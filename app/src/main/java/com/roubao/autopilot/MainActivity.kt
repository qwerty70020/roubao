package com.roubao.autopilot

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import android.net.Uri
import android.provider.Settings
import com.roubao.autopilot.agent.MobileAgent
import com.roubao.autopilot.controller.DeviceController
import com.roubao.autopilot.data.*
import com.roubao.autopilot.ui.screens.*
import com.roubao.autopilot.ui.theme.*
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.roubao.autopilot.vlm.GUIOwlClient
import com.roubao.autopilot.vlm.MAIUIClient
import com.roubao.autopilot.vlm.VLMClient
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import android.util.Log

private const val TAG = "MainActivity"

sealed class Screen(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    object Home : Screen("home", R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home)
    object Capabilities : Screen("capabilities", R.string.capabilities_title, Icons.Outlined.Star, Icons.Filled.Star)
    object History : Screen("history", R.string.tab_history, Icons.Outlined.List, Icons.Filled.List)
    object Settings : Screen("settings", R.string.tab_settings, Icons.Outlined.Settings, Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {

    private lateinit var deviceController: DeviceController
    private lateinit var settingsManager: SettingsManager
    private lateinit var executionRepository: ExecutionRepository

    private val mobileAgent = mutableStateOf<MobileAgent?>(null)
    private var shizukuAvailable = mutableStateOf(false)

    // 当前执行的协程 Job（用于停止任务）
    private var currentExecutionJob: kotlinx.coroutines.Job? = null

    // 执行记录列表
    private val executionRecords = mutableStateOf<List<ExecutionRecord>>(emptyList())

    // 是否正在执行（点击发送后立即为 true）
    private val isExecuting = mutableStateOf(false)

    // 当前执行的记录 ID（用于停止后跳转）
    private val currentRecordId = mutableStateOf<String?>(null)

    // 是否需要跳转到记录详情（悬浮窗停止后触发）
    private val shouldNavigateToRecord = mutableStateOf(false)

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        shizukuAvailable.value = true
        if (checkShizukuPermission()) {
            Log.d(TAG, "Shizuku permission granted, binding service")
            deviceController.bindService()
        } else {
            Log.d(TAG, "Shizuku permission not granted")
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
        shizukuAvailable.value = false
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        Log.d(TAG, "Shizuku permission result: $grantResult")
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            deviceController.bindService()
            Toast.makeText(this, getString(R.string.toast_shizuku_granted), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // 设置边到边显示，深色状态栏和导航栏
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        deviceController = DeviceController(this)
        deviceController.setCacheDir(cacheDir)
        settingsManager = SettingsManager(this)
        executionRepository = ExecutionRepository(this)

        // 加载执行记录
        lifecycleScope.launch {
            executionRecords.value = executionRepository.getAllRecords()
        }

        // 添加 Shizuku 监听器
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        // 检查 Shizuku 状态
        checkAndUpdateShizukuStatus()

        setContent {
            val settings by settingsManager.settings.collectAsState()
            BaoziTheme(themeMode = settings.themeMode) {
                val colors = BaoziTheme.colors
                // 动态更新系统栏颜色
                SideEffect {
                    val window = this@MainActivity.window
                    window.statusBarColor = colors.background.toArgb()
                    window.navigationBarColor = colors.backgroundCard.toArgb()
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !colors.isDark
                        isAppearanceLightNavigationBars = !colors.isDark
                    }
                }

                // 首次启动显示引导画面
                if (!settings.hasSeenOnboarding) {
                    OnboardingScreen(
                        onComplete = {
                            settingsManager.setOnboardingSeen()
                        }
                    )
                } else {
                    MainApp()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainApp() {
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
        var selectedRecord by remember { mutableStateOf<ExecutionRecord?>(null) }
        var showShizukuHelpDialog by remember { mutableStateOf(false) }
        var hasShownShizukuHelp by remember { mutableStateOf(false) }

        val settings by settingsManager.settings.collectAsState()
        val colors = BaoziTheme.colors
        val agent = mobileAgent.value
        val agentState by agent?.state?.collectAsState() ?: remember { mutableStateOf(null) }
        val logs by agent?.logs?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
        val records by remember { executionRecords }
        val isShizukuAvailable = shizukuAvailable.value && checkShizukuPermission()
        val executing by remember { isExecuting }
        val navigateToRecord by remember { shouldNavigateToRecord }
        val recordId by remember { currentRecordId }

        // 监听跳转事件
        LaunchedEffect(navigateToRecord, recordId) {
            if (navigateToRecord && recordId != null) {
                // 找到对应的记录并跳转
                val record = records.find { it.id == recordId }
                if (record != null) {
                    selectedRecord = record
                    currentScreen = Screen.History
                }
                shouldNavigateToRecord.value = false
            }
        }

        // 首次进入且 Shizuku 未连接时，显示帮助引导（只显示一次）
        LaunchedEffect(Unit) {
            if (!isShizukuAvailable && settings.hasSeenOnboarding && !hasShownShizukuHelp) {
                hasShownShizukuHelp = true
                showShizukuHelpDialog = true
            }
        }

        Scaffold(
            modifier = Modifier.background(colors.background),
            containerColor = colors.background,
            bottomBar = {
                if (selectedRecord == null) {
                    NavigationBar(
                        containerColor = colors.background,
                        contentColor = colors.textPrimary,
                        tonalElevation = 0.dp
                    ) {
                        listOf(Screen.Home, Screen.Capabilities, Screen.History, Screen.Settings).forEach { screen ->
                            val selected = currentScreen == screen
                            val screenTitle = stringResource(screen.titleRes)
                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.icon,
                                        contentDescription = screenTitle
                                    )
                                },
                                label = { Text(screenTitle) },
                                selected = selected,
                                onClick = { currentScreen = screen },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = if (colors.isDark) colors.textPrimary else Color.White,
                                    selectedTextColor = colors.primary,
                                    unselectedIconColor = colors.textSecondary,
                                    unselectedTextColor = colors.textSecondary,
                                    indicatorColor = colors.primary
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 处理系统返回手势
                BackHandler(enabled = selectedRecord != null) {
                    selectedRecord = null
                }

                // 详情页优先显示
                if (selectedRecord != null) {
                    HistoryDetailScreen(
                        record = selectedRecord!!,
                        onBack = { selectedRecord = null }
                    )
                } else {
                    // 主页面切换
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "screen"
                    ) { screen ->
                        when (screen) {
                            Screen.Home -> {
                                // 每次进入首页都检测 Shizuku 状态
                                LaunchedEffect(Unit) {
                                    checkAndUpdateShizukuStatus()
                                }
                                HomeScreen(
                                    agentState = agentState,
                                    logs = logs,
                                    onExecute = { instruction ->
                                        runAgent(
                                            instruction = instruction,
                                            apiKey = settings.apiKey,
                                            baseUrl = settings.baseUrl,
                                            model = settings.model,
                                            maxSteps = settings.maxSteps,
                                            isGUIAgent = settings.currentProvider.isGUIAgent,
                                            providerId = settings.currentProviderId
                                        )
                                    },
                                    onStop = {
                                        mobileAgent.value?.stop()
                                    },
                                    shizukuAvailable = isShizukuAvailable,
                                    currentModel = settings.model,
                                    onRefreshShizuku = { refreshShizukuStatus() },
                                    onShizukuRequired = { showShizukuHelpDialog = true },
                                    isExecuting = executing
                                )
                            }
                            Screen.Capabilities -> CapabilitiesScreen()
                            Screen.History -> HistoryScreen(
                                records = records,
                                onRecordClick = { record -> selectedRecord = record },
                                onDeleteRecord = { id -> deleteRecord(id) }
                            )
                            Screen.Settings -> SettingsScreen(
                                settings = settings,
                                onUpdateApiKey = { settingsManager.updateApiKey(it) },
                                onUpdateBaseUrl = { settingsManager.updateBaseUrl(it) },
                                onUpdateModel = { settingsManager.updateModel(it) },
                                onUpdateCachedModels = { settingsManager.updateCachedModels(it) },
                                onUpdateThemeMode = { settingsManager.updateThemeMode(it) },
                                onUpdateMaxSteps = { settingsManager.updateMaxSteps(it) },
                                onUpdateCloudCrashReport = { enabled ->
                                    settingsManager.updateCloudCrashReportEnabled(enabled)
                                    App.getInstance().updateCloudCrashReportEnabled(enabled)
                                },
                                onUpdateRootModeEnabled = { settingsManager.updateRootModeEnabled(it) },
                                onUpdateSuCommandEnabled = { settingsManager.updateSuCommandEnabled(it) },
                                onSelectProvider = { settingsManager.selectProvider(it) },
                                shizukuAvailable = isShizukuAvailable,
                                shizukuPrivilegeLevel = if (isShizukuAvailable) {
                                    when (deviceController.getShizukuPrivilegeLevel()) {
                                        DeviceController.ShizukuPrivilegeLevel.ROOT -> "ROOT"
                                        DeviceController.ShizukuPrivilegeLevel.ADB -> "ADB"
                                        else -> "NONE"
                                    }
                                } else "NONE",
                                onFetchModels = { onSuccess, onError ->
                                    lifecycleScope.launch {
                                        val result = VLMClient.fetchModels(settings.baseUrl, settings.apiKey)
                                        result.onSuccess { models ->
                                            onSuccess(models)
                                        }.onFailure { error ->
                                            onError(error.message ?: "未知错误")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Shizuku 帮助对话框
        if (showShizukuHelpDialog) {
            ShizukuHelpDialog(onDismiss = { showShizukuHelpDialog = false })
        }
    }

    private fun deleteRecord(id: String) {
        lifecycleScope.launch {
            executionRepository.deleteRecord(id)
            executionRecords.value = executionRepository.getAllRecords()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        deviceController.unbindService()
    }

    private fun checkShizukuPermission(): Boolean {
        return try {
            val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "checkShizukuPermission: $granted")
            granted
        } catch (e: Exception) {
            Log.e(TAG, "checkShizukuPermission error", e)
            false
        }
    }

    private fun checkAndUpdateShizukuStatus() {
        Log.d(TAG, "checkAndUpdateShizukuStatus called")
        try {
            val binderAlive = Shizuku.pingBinder()
            Log.d(TAG, "Shizuku pingBinder: $binderAlive")

            if (binderAlive) {
                shizukuAvailable.value = true
                val hasPermission = checkShizukuPermission()
                Log.d(TAG, "Shizuku hasPermission: $hasPermission")

                if (hasPermission) {
                    Log.d(TAG, "Binding Shizuku service")
                    deviceController.bindService()
                } else {
                    Log.d(TAG, "Requesting Shizuku permission")
                    requestShizukuPermission()
                }
            } else {
                Log.d(TAG, "Shizuku binder not alive")
                shizukuAvailable.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkAndUpdateShizukuStatus error", e)
            shizukuAvailable.value = false
        }
    }

    private fun refreshShizukuStatus() {
        Log.d(TAG, "refreshShizukuStatus called by user")
        Toast.makeText(this, getString(R.string.toast_checking_shizuku), Toast.LENGTH_SHORT).show()
        checkAndUpdateShizukuStatus()

        if (shizukuAvailable.value && checkShizukuPermission()) {
            Toast.makeText(this, getString(R.string.shizuku_connected), Toast.LENGTH_SHORT).show()
        } else if (shizukuAvailable.value) {
            Toast.makeText(this, getString(R.string.toast_authorize_shizuku_popup), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.toast_start_shizuku), Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, getString(R.string.toast_start_shizuku), Toast.LENGTH_SHORT).show()
                return
            }

            if (Shizuku.isPreV11()) {
                Toast.makeText(this, getString(R.string.toast_shizuku_old), Toast.LENGTH_SHORT).show()
                return
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.toast_shizuku_granted), Toast.LENGTH_SHORT).show()
                shizukuAvailable.value = true
                deviceController.bindService()
                return
            }

            Shizuku.requestPermission(0)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_start_shizuku), Toast.LENGTH_SHORT).show()
        }
    }

    private fun runAgent(
        instruction: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        maxSteps: Int,
        isGUIAgent: Boolean = false,
        providerId: String = ""
    ) {
        if (instruction.isBlank()) {
            Toast.makeText(this, getString(R.string.toast_enter_instruction), Toast.LENGTH_SHORT).show()
            return
        }
        // MAI-UI 本地部署不需要 API Key
        val requiresApiKey = providerId != "mai_ui"
        if (requiresApiKey && apiKey.isBlank()) {
            Toast.makeText(this, getString(R.string.toast_enter_api_key), Toast.LENGTH_SHORT).show()
            return
        }

        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.toast_grant_overlay), Toast.LENGTH_LONG).show()
            val intent = android.content.Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        // 立即设置执行状态为 true，显示停止按钮
        isExecuting.value = true

        // 根据服务商类型创建相应的客户端
        if (isGUIAgent) {
            // GUI-Owl 模式
            val guiOwlClient = GUIOwlClient(
                apiKey = apiKey,
                model = model.ifBlank { "pre-gui_owl_7b" }
            )
            mobileAgent.value = MobileAgent(
                vlmClient = null,
                controller = deviceController,
                context = this,
                guiOwlClient = guiOwlClient
            )
        } else if (providerId == "mai_ui") {
            // MAI-UI 模式
            val maiuiClient = MAIUIClient(
                baseUrl = baseUrl.ifBlank { "http://localhost:8000/v1" },
                model = model.ifBlank { "MAI-UI-2B" }
            )
            mobileAgent.value = MobileAgent(
                vlmClient = null,
                controller = deviceController,
                context = this,
                maiuiClient = maiuiClient
            )
        } else {
            // OpenAI 兼容模式 (阿里云 Qwen-VL, OpenAI, OpenRouter 等)
            val vlmClient = VLMClient(
                apiKey = apiKey,
                baseUrl = baseUrl.ifBlank { "https://dashscope.aliyuncs.com/compatible-mode/v1" },
                model = model.ifBlank { "qwen3-vl-plus" }
            )
            mobileAgent.value = MobileAgent(vlmClient, deviceController, this)
        }

        // 设置停止回调，用于取消协程
        mobileAgent.value?.onStopRequested = {
            currentExecutionJob?.cancel()
            currentExecutionJob = null
        }

        // 创建执行记录
        val record = ExecutionRecord(
            title = generateTitle(instruction),
            instruction = instruction,
            startTime = System.currentTimeMillis(),
            status = ExecutionStatus.RUNNING
        )

        // 保存当前记录 ID，用于停止后跳转
        currentRecordId.value = record.id

        // 取消之前的任务（如果有）
        currentExecutionJob?.cancel()

        currentExecutionJob = lifecycleScope.launch {
            // 保存初始记录
            executionRepository.saveRecord(record)
            executionRecords.value = executionRepository.getAllRecords()

            try {
                val result = mobileAgent.value!!.runInstruction(instruction, maxSteps)

                // 更新记录状态
                val agentState = mobileAgent.value?.state?.value
                val steps = agentState?.executionSteps ?: emptyList()
                val currentLogs = mobileAgent.value?.logs?.value ?: emptyList()

                val updatedRecord = record.copy(
                    endTime = System.currentTimeMillis(),
                    status = if (result.success) ExecutionStatus.COMPLETED else ExecutionStatus.FAILED,
                    steps = steps,
                    logs = currentLogs,
                    resultMessage = result.message
                )
                executionRepository.saveRecord(updatedRecord)
                executionRecords.value = executionRepository.getAllRecords()

                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()

                // 重置执行状态
                isExecuting.value = false

                // 延迟3秒后清空日志，恢复默认状态
                kotlinx.coroutines.delay(3000)
                mobileAgent.value?.clearLogs()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户取消任务 - 使用 NonCancellable 确保清理操作完成
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    val agentState = mobileAgent.value?.state?.value
                    val steps = agentState?.executionSteps ?: emptyList()
                    val currentLogs = mobileAgent.value?.logs?.value ?: emptyList()

                    println("[MainActivity] 取消任务 - steps: ${steps.size}, logs: ${currentLogs.size}")

                    val updatedRecord = record.copy(
                        endTime = System.currentTimeMillis(),
                        status = ExecutionStatus.STOPPED,
                        steps = steps,
                        logs = currentLogs,
                        resultMessage = getString(R.string.status_cancelled)
                    )
                    executionRepository.saveRecord(updatedRecord)
                    executionRecords.value = executionRepository.getAllRecords()

                    // 重置执行状态
                    isExecuting.value = false

                    Toast.makeText(this@MainActivity, getString(R.string.toast_task_stopped), Toast.LENGTH_SHORT).show()
                    mobileAgent.value?.clearLogs()

                    // 触发跳转到记录详情页
                    shouldNavigateToRecord.value = true
                }
            } catch (e: Exception) {
                // 更新失败记录
                val currentLogs = mobileAgent.value?.logs?.value ?: emptyList()
                val updatedRecord = record.copy(
                    endTime = System.currentTimeMillis(),
                    status = ExecutionStatus.FAILED,
                    logs = currentLogs,
                    resultMessage = getString(R.string.toast_error, e.message ?: "")
                )
                executionRepository.saveRecord(updatedRecord)
                executionRecords.value = executionRepository.getAllRecords()

                // 重置执行状态
                isExecuting.value = false

                Toast.makeText(this@MainActivity, getString(R.string.toast_error, e.message ?: ""), Toast.LENGTH_LONG).show()

                // 延迟3秒后清空日志，恢复默认状态
                kotlinx.coroutines.delay(3000)
                mobileAgent.value?.clearLogs()
            }
        }
    }

    private fun generateTitle(instruction: String): String {
        // 生成简短标题（按关键词匹配，兼容中/英/乌）
        val keywords = listOf(
            listOf("打开", "open", "відкр") to R.string.title_open_app,
            listOf("点餐", "order", "замов") to R.string.title_order,
            listOf("发", "send", "надісл", "відправ") to R.string.title_send_message,
            listOf("看", "browse", "перегля") to R.string.title_browse,
            listOf("搜", "search", "шук") to R.string.title_search,
            listOf("设置", "settings", "налашт") to R.string.title_settings,
            listOf("播放", "play", "відтвор") to R.string.title_play_media
        )
        val lower = instruction.lowercase()
        for ((keys, titleRes) in keywords) {
            if (keys.any { lower.contains(it) }) {
                return getString(titleRes)
            }
        }
        return if (instruction.length > 10) instruction.take(10) + "..." else instruction
    }
}
