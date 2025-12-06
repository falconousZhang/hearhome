package com.example.hearhome.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.utils.MentionReminderChecker
import com.example.hearhome.utils.MentionWithPost
import com.example.hearhome.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全局@提醒超时弹窗组件
 * 当用户有超时未查看的@提醒时显示弹窗
 * 应该在主界面（如HomeScreen）或空间列表界面调用此组件
 */
@Composable
fun MentionTimeoutDialog(
    userId: Int,
    navController: NavController,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    var expiredMentions by remember { mutableStateOf<List<MentionWithPost>>(emptyList()) }
    var currentMention by remember { mutableStateOf<MentionWithPost?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    // 已处理的提醒ID集合（避免重复弹窗）
    var processedMentionIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    
    // 定期检查超时提醒
    // 改进：启动时立即检查，之后每3秒检查一次（更快响应）
    LaunchedEffect(userId) {
        // 立即进行第一次检查（无需等待）
        var firstCheck = true
        
        while (true) {
            if (!firstCheck) {
                delay(3_000) // 3秒间隔，更快响应超时
            }
            firstCheck = false
            
            val mentions = MentionReminderChecker.checkExpiredMentions(context, userId)
            
            // 过滤出尚未处理过的超时提醒
            val notifiableMentions = mentions.filter { mention ->
                mention.mention.id !in processedMentionIds &&
                (mention.mention.lastNotifiedAt == null ||
                 (System.currentTimeMillis() - mention.mention.lastNotifiedAt) > 2 * 60 * 1000) // 2分钟
            }
            
            if (notifiableMentions.isNotEmpty()) {
                expiredMentions = notifiableMentions
                currentMention = notifiableMentions.firstOrNull()
                showDialog = true
                
                // 发送系统通知并记录已处理的ID
                notifiableMentions.forEach { mention ->
                    NotificationHelper.sendMentionTimeoutNotification(
                        context = context,
                        notificationId = mention.mention.id,
                        mentionerName = mention.mentionerName,
                        contentPreview = mention.postContent.take(20),
                        postId = mention.mention.postId
                    )
                    
                    // 更新最后通知时间并标记为已过期
                    MentionReminderChecker.markAsNotified(context, mention.mention.id)
                    MentionReminderChecker.markAsExpired(context, mention.mention.id)
                    
                    // 记录已处理
                    processedMentionIds = processedMentionIds + mention.mention.id
                }
            }
        }
    }
    
    // 显示弹窗
    if (showDialog && currentMention != null) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                onDismiss()
            },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "@提醒已超时",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${currentMention!!.mentionerName} @了你查看一条动态，但你还没有响应。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                "动态内容：",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (currentMention!!.postContent.length > 100) {
                                    currentMention!!.postContent.take(100) + "..."
                                } else {
                                    currentMention!!.postContent
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    
                    if (expiredMentions.size > 1) {
                        Text(
                            "还有 ${expiredMentions.size - 1} 条超时提醒待处理",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog = false
                        // 跳转到动态详情页
                        navController.navigate("post_detail/${currentMention!!.mention.postId}/$userId")
                    }
                ) {
                    Text("立即查看")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // 移除当前提醒，显示下一个（如果有）
                        val remainingMentions = expiredMentions.drop(1)
                        if (remainingMentions.isNotEmpty()) {
                            expiredMentions = remainingMentions
                            currentMention = remainingMentions.firstOrNull()
                        } else {
                            showDialog = false
                            onDismiss()
                        }
                    }
                ) {
                    Text("稍后处理")
                }
            }
        )
    }
}

/**
 * 打卡提醒弹窗组件
 * 当用户有未打卡的空间时显示弹窗
 */
@Composable
fun CheckInReminderDialog(
    userId: Int,
    navController: NavController,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    
    var needsCheckInSpaces by remember { mutableStateOf<List<CheckInSpaceInfo>>(emptyList()) }
    var currentSpace by remember { mutableStateOf<CheckInSpaceInfo?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    // 记录已通知过的空间ID和通知时间，避免频繁弹窗
    var notifiedSpaces by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    
    // 检查需要打卡的空间（每10秒检查一次）
    LaunchedEffect(userId) {
        while (true) {
            val currentTime = System.currentTimeMillis()
            val spaces = withContext(Dispatchers.IO) {
                val spaceDao = db.spaceDao()
                val members = spaceDao.getSpaceMembersByUserId(userId)
                    .filter { it.status == "active" }
                
                members.mapNotNull { member ->
                    val space = spaceDao.getSpaceById(member.spaceId)
                    if (space != null && space.checkInIntervalSeconds > 0) {
                        val needsCheckIn = com.example.hearhome.utils.CheckInHelper.needsCheckIn(
                            space, userId, spaceDao
                        )
                        if (needsCheckIn) {
                            val lastPost = spaceDao.getLastPostTimeByUser(space.id, userId)
                            val overdueSeconds = if (lastPost != null) {
                                (currentTime - lastPost) / 1000 - space.checkInIntervalSeconds
                            } else {
                                // 从未发布过，超时时间等于间隔时间
                                space.checkInIntervalSeconds
                            }
                            CheckInSpaceInfo(
                                spaceId = space.id,
                                spaceName = space.name,
                                intervalSeconds = space.checkInIntervalSeconds,
                                overdueSeconds = if (overdueSeconds > 0) overdueSeconds else 0
                            )
                        } else null
                    } else null
                }
            }
            
            if (spaces.isNotEmpty()) {
                // 过滤出未通知过或距离上次通知超过5分钟的空间
                val newSpacesToNotify = spaces.filter { spaceInfo ->
                    val lastNotified = notifiedSpaces[spaceInfo.spaceId]
                    lastNotified == null || (currentTime - lastNotified) > 5 * 60 * 1000
                }
                
                if (newSpacesToNotify.isNotEmpty()) {
                    needsCheckInSpaces = spaces
                    currentSpace = newSpacesToNotify.firstOrNull()
                    showDialog = true
                    
                    // 发送系统通知
                    newSpacesToNotify.forEach { spaceInfo ->
                        NotificationHelper.sendCheckInReminderNotification(
                            context = context,
                            notificationId = spaceInfo.spaceId + 10000,
                            spaceName = spaceInfo.spaceName,
                            intervalText = com.example.hearhome.utils.CheckInHelper.formatInterval(
                                spaceInfo.intervalSeconds
                            )
                        )
                        // 记录通知时间
                        notifiedSpaces = notifiedSpaces + (spaceInfo.spaceId to currentTime)
                    }
                }
            }
            
            delay(10_000) // 10秒后再次检查
        }
    }
    
    // 显示弹窗
    if (showDialog && currentSpace != null) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                onDismiss()
            },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "打卡提醒",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "「${currentSpace!!.spaceName}」需要你发布动态！",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Text(
                        "打卡间隔：${com.example.hearhome.utils.CheckInHelper.formatInterval(currentSpace!!.intervalSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    
                    if (currentSpace!!.overdueSeconds > 0) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "已超时：${com.example.hearhome.utils.CheckInHelper.formatInterval(currentSpace!!.overdueSeconds)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    if (needsCheckInSpaces.size > 1) {
                        Text(
                            "还有 ${needsCheckInSpaces.size - 1} 个空间需要打卡",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDialog = false
                        // 跳转到空间详情页
                        navController.navigate("space_detail/${currentSpace!!.spaceId}/$userId")
                    }
                ) {
                    Text("去发布动态")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // 移除当前空间，显示下一个（如果有）
                        val remainingSpaces = needsCheckInSpaces.drop(1)
                        if (remainingSpaces.isNotEmpty()) {
                            needsCheckInSpaces = remainingSpaces
                            currentSpace = remainingSpaces.firstOrNull()
                        } else {
                            showDialog = false
                            onDismiss()
                        }
                    }
                ) {
                    Text("稍后处理")
                }
            }
        )
    }
}

/**
 * 需要打卡的空间信息
 */
data class CheckInSpaceInfo(
    val spaceId: Int,
    val spaceName: String,
    val intervalSeconds: Long,
    val overdueSeconds: Long = 0
)

/**
 * @提醒管理对话框
 * 允许动态作者查看、修改、删除已发送的@提醒
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentionManageDialog(
    postId: Int,
    currentUserId: Int,
    onDismiss: () -> Unit,
    onMentionsChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    val scope = rememberCoroutineScope()
    
    var mentions by remember { mutableStateOf<List<com.example.hearhome.data.local.PostMentionWithUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var editingMention by remember { mutableStateOf<com.example.hearhome.data.local.PostMentionWithUser?>(null) }
    var newTimeoutHours by remember { mutableStateOf("0") }
    var newTimeoutMinutes by remember { mutableStateOf("0") }
    var newTimeoutSeconds by remember { mutableStateOf("0") }
    
    // 加载提醒列表
    LaunchedEffect(postId) {
        withContext(Dispatchers.IO) {
            mentions = db.postMentionDao().getMentionsByPost(postId)
            isLoading = false
        }
    }
    
    if (editingMention != null) {
        // 编辑倒计时对话框
        AlertDialog(
            onDismissRequest = { editingMention = null },
            title = { Text("修改倒计时") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "修改 ${editingMention!!.nickname ?: "用户"} 的@提醒倒计时",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTimeoutHours,
                            onValueChange = { 
                                if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                    newTimeoutHours = it
                                }
                            },
                            label = { Text("时") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Text(":")
                        OutlinedTextField(
                            value = newTimeoutMinutes,
                            onValueChange = { 
                                if (it.isEmpty() || (it.all { char -> char.isDigit() } && it.toIntOrNull()?.let { num -> num < 60 } == true)) {
                                    newTimeoutMinutes = it
                                }
                            },
                            label = { Text("分") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Text(":")
                        OutlinedTextField(
                            value = newTimeoutSeconds,
                            onValueChange = { 
                                if (it.isEmpty() || (it.all { char -> char.isDigit() } && it.toIntOrNull()?.let { num -> num < 60 } == true)) {
                                    newTimeoutSeconds = it
                                }
                            },
                            label = { Text("秒") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    
                    // 计算剩余时间
                    val remainingMillis = editingMention!!.createdAt + editingMention!!.timeoutSeconds * 1000 - System.currentTimeMillis()
                    if (remainingMillis > 0 && editingMention!!.status == "pending") {
                        Text(
                            "当前剩余：${formatCountdown(remainingMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val h = newTimeoutHours.toLongOrNull() ?: 0
                        val m = newTimeoutMinutes.toLongOrNull() ?: 0
                        val s = newTimeoutSeconds.toLongOrNull() ?: 0
                        val totalSeconds = h * 3600 + m * 60 + s
                        
                        if (totalSeconds > 0) {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    // 同时更新 createdAt 为当前时间，使新倒计时从现在开始
                                    val now = System.currentTimeMillis()
                                    db.postMentionDao().updateMentionTimeout(editingMention!!.id, totalSeconds, now)
                                    mentions = db.postMentionDao().getMentionsByPost(postId)
                                }
                                editingMention = null
                                onMentionsChanged()
                            }
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMention = null }) {
                    Text("取消")
                }
            }
        )
    } else {
        // 主管理对话框
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("管理@提醒")
                }
            },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (mentions.isEmpty()) {
                        Text(
                            "暂无@提醒",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        mentions.forEach { mention ->
                            MentionManageItem(
                                mention = mention,
                                onEdit = {
                                    // 初始化编辑框值
                                    val currentSeconds = mention.timeoutSeconds
                                    newTimeoutHours = (currentSeconds / 3600).toString()
                                    newTimeoutMinutes = ((currentSeconds % 3600) / 60).toString()
                                    newTimeoutSeconds = (currentSeconds % 60).toString()
                                    editingMention = mention
                                },
                                onDelete = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            db.postMentionDao().deleteMention(mention.id)
                                            mentions = db.postMentionDao().getMentionsByPost(postId)
                                        }
                                        onMentionsChanged()
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        )
    }
}

/**
 * 单条@提醒管理项
 */
@Composable
private fun MentionManageItem(
    mention: com.example.hearhome.data.local.PostMentionWithUser,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val now = System.currentTimeMillis()
    val timeoutMillis = mention.createdAt + mention.timeoutSeconds * 1000
    val remainingMillis = timeoutMillis - now
    val isExpired = remainingMillis <= 0 || mention.status != "pending"
    
    val statusText = when (mention.status) {
        "viewed" -> "已查看"
        "ignored" -> "已忽略"
        "expired" -> "已超时"
        else -> if (isExpired) "已超时" else "进行中"
    }
    
    val statusColor = when (mention.status) {
        "viewed" -> MaterialTheme.colorScheme.primary
        "ignored" -> MaterialTheme.colorScheme.outline
        "expired" -> MaterialTheme.colorScheme.error
        else -> if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpired) 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        mention.nickname ?: "未知用户",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )
                        if (!isExpired && mention.status == "pending") {
                            Text(
                                "剩余 ${formatCountdown(remainingMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                // 仅对进行中的提醒显示操作按钮
                if (mention.status == "pending" && !isExpired) {
                    Row {
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Edit,
                                contentDescription = "修改",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 格式化倒计时显示
 */
private fun formatCountdown(millis: Long): String {
    if (millis <= 0) return "00:00:00"
    
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}
