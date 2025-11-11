package com.example.hearhome.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.ui.components.AppBottomNavigation
import com.example.hearhome.utils.MentionReminderChecker
import com.example.hearhome.utils.MentionWithPost
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    userId: Int
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(db.friendDao())
    )

    val friendsWithUsers by viewModel.friendsWithUsers.collectAsState()
    val scope = rememberCoroutineScope()
    
    // 超时提醒状态
    var expiredMentions by remember { mutableStateOf<List<MentionWithPost>>(emptyList()) }
    var showReminderDialog by remember { mutableStateOf(false) }
    
    // 检查提醒的函数
    fun checkReminders() {
        scope.launch {
            val mentions = MentionReminderChecker.checkExpiredMentions(context, userId)
            if (mentions.isNotEmpty()) {
                expiredMentions = mentions
                showReminderDialog = true
            }
        }
    }

    LaunchedEffect(userId) {
        viewModel.loadFriends(userId)
        // 初次进入时检查提醒
        checkReminders()
    }
    
    // 监听应用前后台切换
    DisposableEffect(Unit) {
        val listener = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                // 应用从后台恢复到前台时，重新检查提醒
                checkReminders()
            }
        }
        
        val lifecycle = (context as? androidx.activity.ComponentActivity)?.lifecycle
        lifecycle?.addObserver(listener)
        
        onDispose {
            lifecycle?.removeObserver(listener)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("聊天") },
                actions = {
                    IconButton(onClick = { navController.navigate("search/$userId") }) {
                        Icon(Icons.Filled.Search, contentDescription = "查找用户")
                    }
                }
            )
        },
        bottomBar = {
            AppBottomNavigation(
                currentRoute = "home",
                navController = navController,
                userId = userId
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (friendsWithUsers.isEmpty()) {
                item {
                    Text("您还没有好友，快去查找用户并添加吧！", modifier = Modifier.padding(16.dp))
                }
            } else {
                items(friendsWithUsers) { friendWithUser ->
                    val friendUser = if (friendWithUser.friend.senderId == userId) {
                        friendWithUser.receiver
                    } else {
                        friendWithUser.sender
                    }

                    ListItem(
                        headlineContent = { Text(friendUser.nickname) },
                        supportingContent = { Text("在线状态") },
                        modifier = Modifier.clickable { 
                            navController.navigate("chat/$userId/${friendUser.uid}") 
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    
    // 超时提醒对话框
    if (showReminderDialog && expiredMentions.isNotEmpty()) {
        ExpiredMentionDialog(
            mentions = expiredMentions,
            onDismiss = { showReminderDialog = false },
            onViewPost = { postId ->
                scope.launch {
                    // 标记为已通知
                    expiredMentions.forEach { mention ->
                        if (mention.mention.postId == postId) {
                            MentionReminderChecker.markAsNotified(context, mention.mention.id)
                        }
                    }
                }
                showReminderDialog = false
                navController.navigate("post_detail/$postId/$userId")
            },
            onViewAll = {
                showReminderDialog = false
                // 可选：导航到一个查看所有超时提醒的页面
            }
        )
    }
}

/**
 * 超时提醒对话框
 */
@Composable
fun ExpiredMentionDialog(
    mentions: List<MentionWithPost>,
    onDismiss: () -> Unit,
    onViewPost: (Int) -> Unit,
    onViewAll: () -> Unit
) {
    val firstMention = mentions.first()
    val hasMore = mentions.size > 1
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (hasMore) "有 ${mentions.size} 条提醒未查看" else "查看提醒",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 显示第一条提醒的详情
                Text(
                    text = "${firstMention.mentionerName} 提醒你查看动态：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = firstMention.postContent.take(50) + 
                                   if (firstMention.postContent.length > 50) "..." else "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Spacer(Modifier.height(8.dp))
                        
                        val overdueTime = System.currentTimeMillis() - 
                                        (firstMention.mention.createdAt + firstMention.mention.timeoutSeconds * 1000)
                        val overdueMinutes = (overdueTime / 60000).toInt()
                        
                        Text(
                            text = "已超时：${if (overdueMinutes < 60) "${overdueMinutes}分钟" else "${overdueMinutes / 60}小时"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                if (hasMore) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "还有 ${mentions.size - 1} 条提醒...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onViewPost(firstMention.mention.postId) }) {
                Text("立即查看")
            }
        },
        dismissButton = {
            if (hasMore) {
                TextButton(onClick = onViewAll) {
                    Text("查看全部")
                }
            }
            TextButton(onClick = onDismiss) {
                Text("稍后查看")
            }
        }
    )
}