package com.roubao.autopilot.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.annotation.StringRes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roubao.autopilot.R
import com.roubao.autopilot.agent.AgentState
import com.roubao.autopilot.ui.theme.BaoziTheme
import com.roubao.autopilot.ui.theme.Primary
import com.roubao.autopilot.ui.theme.Secondary

/**
 * 预设命令
 */
data class PresetCommand(
    val icon: String,
    @StringRes val titleRes: Int,
    @StringRes val commandRes: Int
)

val presetCommands = listOf(
    PresetCommand("🍔", R.string.preset_burger, R.string.preset_burger_cmd),
    PresetCommand("📕", R.string.preset_weibo, R.string.preset_weibo_cmd),
    PresetCommand("📺", R.string.preset_bilibili, R.string.preset_bilibili_cmd),
    PresetCommand("🛒", R.string.preset_takeout, R.string.preset_takeout_cmd),
    PresetCommand("🎵", R.string.preset_music, R.string.preset_music_cmd),
    PresetCommand("💬", R.string.preset_message, R.string.preset_message_cmd)
)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    agentState: AgentState?,
    logs: List<String>,
    onExecute: (String) -> Unit,
    onStop: () -> Unit,
    shizukuAvailable: Boolean,
    currentModel: String = "",
    onRefreshShizuku: () -> Unit = {},
    onShizukuRequired: () -> Unit = {},
    isExecuting: Boolean = false
) {
    val colors = BaoziTheme.colors
    var inputText by remember { mutableStateOf("") }
    // 使用 isExecuting 或 agentState?.isRunning 来判断是否运行中
    val isRunning = isExecuting || agentState?.isRunning == true
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 记录上一次的运行状态，用于检测任务结束
    var wasRunning by remember { mutableStateOf(false) }

    // 任务结束时清空输入框
    LaunchedEffect(isRunning) {
        if (wasRunning && !isRunning) {
            // 从运行中变为未运行，说明任务结束
            inputText = ""
        }
        wasRunning = isRunning
    }

    // 自动滚动到底部
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        // 顶部标题
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.tab_home),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                    Text(
                        text = if (shizukuAvailable) stringResource(R.string.home_ready) else stringResource(R.string.home_connect_shizuku),
                        fontSize = 14.sp,
                        color = if (shizukuAvailable) colors.textSecondary else colors.error
                    )
                }

                // 未连接时显示刷新按钮
                if (!shizukuAvailable) {
                    IconButton(
                        onClick = onRefreshShizuku,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.backgroundCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.home_refresh_shizuku),
                            tint = colors.primary
                        )
                    }
                }
            }
        }

        // 内容区域
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isRunning || logs.isNotEmpty()) {
                // 执行中或有日志时显示日志
                ExecutionLogView(
                    logs = logs,
                    isRunning = isRunning,
                    currentStep = agentState?.currentStep ?: 0,
                    currentModel = currentModel,
                    listState = listState,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 空闲时显示预设命令
                PresetCommandsView(
                    onCommandClick = { command ->
                        if (shizukuAvailable) {
                            inputText = command
                        } else {
                            onShizukuRequired()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 底部输入区域
        InputArea(
            inputText = inputText,
            onInputChange = { inputText = it },
            onExecute = {
                if (inputText.isNotBlank()) {
                    // 收起键盘并清除焦点
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onExecute(inputText)
                }
            },
            onStop = {
                // 停止任务并清空输入框
                inputText = ""
                onStop()
            },
            isRunning = isRunning,
            enabled = shizukuAvailable,
            onInputClick = {
                if (!shizukuAvailable) {
                    onShizukuRequired()
                }
            }
        )
    }
}

@Composable
fun PresetCommandsView(
    onCommandClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BaoziTheme.colors
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.home_try_these),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        presetCommands.chunked(2).forEach { rowCommands ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowCommands.forEach { preset ->
                    val command = stringResource(preset.commandRes)
                    PresetCommandCard(
                        preset = preset,
                        onClick = { onCommandClick(command) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 如果是奇数，补一个空白
                if (rowCommands.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun PresetCommandCard(
    preset: PresetCommand,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = BaoziTheme.colors
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.backgroundCard
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = preset.icon,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(preset.titleRes),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
                Text(
                    text = stringResource(preset.commandRes),
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ExecutionLogView(
    logs: List<String>,
    isRunning: Boolean,
    currentStep: Int,
    currentModel: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 执行状态指示器
        if (isRunning) {
            ExecutingIndicator(currentStep = currentStep, currentModel = currentModel)
        }

        // 日志列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(logs) { log ->
                LogItem(log = log)
            }
        }
    }
}

@Composable
fun ExecutingIndicator(currentStep: Int, currentModel: String = "") {
    val colors = BaoziTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "executing")
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.backgroundCard)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 动画圆点
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(Primary, Secondary, Primary)
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.home_executing, currentStep),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.primary
                        )
                    }
                    // 显示当前模型
                    if (currentModel.isNotEmpty()) {
                        Text(
                            text = currentModel,
                            fontSize = 11.sp,
                            color = colors.textHint,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 进度条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.backgroundInput)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Primary, Secondary)
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun LogItem(log: String) {
    val colors = BaoziTheme.colors
    val logColor = when {
        log.contains("❌") -> colors.error
        log.contains("✅") -> colors.success
        log.contains("📋") || log.contains("🎬") -> colors.secondary
        log.contains("Step") || log.contains("=====") -> colors.primary
        log.contains("⛔") -> colors.error
        else -> colors.textSecondary
    }

    Text(
        text = log,
        fontSize = 12.sp,
        color = logColor,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
fun InputArea(
    inputText: String,
    onInputChange: (String) -> Unit,
    onExecute: () -> Unit,
    onStop: () -> Unit,
    isRunning: Boolean,
    enabled: Boolean,
    onInputClick: () -> Unit = {}
) {
    val colors = BaoziTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.backgroundCard,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isRunning) Arrangement.Center else Arrangement.Start
        ) {
            if (isRunning) {
                // 运行中只显示停止按钮
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.error
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.home_stop),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_stop_execution),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // 非运行状态显示输入框和发送按钮
                // 输入框
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.backgroundInput)
                        .then(
                            if (!enabled) {
                                Modifier.clickable { onInputClick() }
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    if (enabled) {
                        // Shizuku 已连接，显示可编辑的输入框
                        BasicTextField(
                            value = inputText,
                            onValueChange = onInputChange,
                            textStyle = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(colors.primary),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (inputText.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.home_input_placeholder),
                                            color = colors.textHint,
                                            fontSize = 15.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    } else {
                        // Shizuku 未连接，显示提示文字
                        Text(
                            text = stringResource(R.string.home_connect_shizuku),
                            color = colors.textHint,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 发送按钮
                IconButton(
                    onClick = onExecute,
                    enabled = enabled && inputText.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputText.isNotBlank() && enabled) colors.primary
                            else colors.backgroundInput
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = stringResource(R.string.home_send),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
