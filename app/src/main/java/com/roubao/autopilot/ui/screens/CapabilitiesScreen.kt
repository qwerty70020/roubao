package com.roubao.autopilot.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.annotation.StringRes
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roubao.autopilot.R
import com.roubao.autopilot.tools.ToolManager
import com.roubao.autopilot.ui.theme.BaoziTheme

/**
 * 工具信息（用于展示）
 */
data class ToolInfo(
    val name: String,
    val description: String
)

/**
 * Agent 角色信息
 */
data class AgentInfo(
    val name: String,
    val icon: String,
    @StringRes val roleRes: Int,
    @StringRes val descriptionRes: Int,
    val responsibilityRes: List<Int>
)

/**
 * 预定义的 Agents 列表
 */
val agentsList = listOf(
    AgentInfo(
        name = "Manager",
        icon = "🎯",
        roleRes = R.string.agent_manager_role,
        descriptionRes = R.string.agent_manager_desc,
        responsibilityRes = listOf(
            R.string.agent_manager_r1,
            R.string.agent_manager_r2,
            R.string.agent_manager_r3,
            R.string.agent_manager_r4
        )
    ),
    AgentInfo(
        name = "Executor",
        icon = "⚡",
        roleRes = R.string.agent_executor_role,
        descriptionRes = R.string.agent_executor_desc,
        responsibilityRes = listOf(
            R.string.agent_executor_r1,
            R.string.agent_executor_r2,
            R.string.agent_executor_r3,
            R.string.agent_executor_r4
        )
    ),
    AgentInfo(
        name = "Reflector",
        icon = "🔍",
        roleRes = R.string.agent_reflector_role,
        descriptionRes = R.string.agent_reflector_desc,
        responsibilityRes = listOf(
            R.string.agent_reflector_r1,
            R.string.agent_reflector_r2,
            R.string.agent_reflector_r3,
            R.string.agent_reflector_r4
        )
    ),
    AgentInfo(
        name = "Notetaker",
        icon = "📝",
        roleRes = R.string.agent_notetaker_role,
        descriptionRes = R.string.agent_notetaker_desc,
        responsibilityRes = listOf(
            R.string.agent_notetaker_r1,
            R.string.agent_notetaker_r2,
            R.string.agent_notetaker_r3,
            R.string.agent_notetaker_r4
        )
    )
)

/**
 * 能力展示页面
 *
 * 展示 Agents 和 Tools（只读）
 */
@Composable
fun CapabilitiesScreen() {
    val colors = BaoziTheme.colors

    // 获取 Tools
    val tools = remember {
        if (ToolManager.isInitialized()) {
            ToolManager.getInstance().getAvailableTools().map { tool ->
                ToolInfo(name = tool.name, description = tool.description)
            }
        } else {
            emptyList()
        }
    }

    // 额外的内置工具（不在 ToolManager 中但是系统能力）
    val builtInTools = listOf(
        ToolInfo("screenshot", stringResource(R.string.tool_screenshot_desc)),
        ToolInfo("tap", stringResource(R.string.tool_tap_desc)),
        ToolInfo("swipe", stringResource(R.string.tool_swipe_desc)),
        ToolInfo("type", stringResource(R.string.tool_type_desc)),
        ToolInfo("press_key", stringResource(R.string.tool_press_key_desc))
    )

    val allTools = tools + builtInTools

    // Tab 状态
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.capabilities_tab_agents, agentsList.size),
        stringResource(R.string.capabilities_tab_tools, allTools.size)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // 顶部标题
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.capabilities_title),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
                Text(
                    text = stringResource(R.string.capabilities_subtitle, agentsList.size, allTools.size),
                    fontSize = 14.sp,
                    color = colors.textSecondary
                )
            }
        }

        // Tab 切换
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = colors.background,
            contentColor = colors.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTab == index) colors.primary else colors.textSecondary
                        )
                    }
                )
            }
        }

        // 内容区域
        when (selectedTab) {
            0 -> AgentsListView()
            1 -> ToolsListView(tools = allTools)
        }
    }
}

@Composable
fun AgentsListView() {
    val colors = BaoziTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 架构说明卡片
        item(key = "arch_intro") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.primary.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.capabilities_arch_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.capabilities_arch_desc),
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Manager → Executor → Reflector → Notetaker",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textHint
                    )
                }
            }
        }

        // Agent 列表
        items(agentsList, key = { it.name }) { agent ->
            AgentCard(agent = agent)
        }
    }
}

@Composable
fun AgentCard(agent: AgentInfo) {
    val colors = BaoziTheme.colors
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.backgroundCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Agent 图标
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = agent.icon,
                        fontSize = 28.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = agent.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.secondary.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(agent.roleRes),
                                fontSize = 11.sp,
                                color = colors.secondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(agent.descriptionRes),
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) stringResource(R.string.capabilities_collapse) else stringResource(R.string.capabilities_expand),
                    tint = colors.textHint
                )
            }

            // 展开显示职责列表
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.capabilities_responsibilities),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    agent.responsibilityRes.forEach { responsibilityRes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                fontSize = 14.sp,
                                color = colors.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = stringResource(responsibilityRes),
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolsListView(tools: List<ToolInfo>) {
    if (tools.isEmpty()) {
        EmptyState(message = stringResource(R.string.capabilities_no_tools))
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tools, key = { it.name }) { tool ->
                ToolCard(tool = tool)
            }
        }
    }
}

@Composable
fun ToolCard(tool: ToolInfo) {
    val colors = BaoziTheme.colors

    // 根据工具名获取图标
    val toolIcon = when (tool.name) {
        "search_apps" -> "🔍"
        "open_app" -> "📱"
        "deep_link" -> "🔗"
        "clipboard" -> "📋"
        "shell" -> "💻"
        "http" -> "🌐"
        else -> "🔧"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.backgroundCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 工具图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.secondary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = toolIcon,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tool.description,
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    val colors = BaoziTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.capabilities_empty_emoji),
                fontSize = 64.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                fontSize = 16.sp,
                color = colors.textSecondary
            )
        }
    }
}
