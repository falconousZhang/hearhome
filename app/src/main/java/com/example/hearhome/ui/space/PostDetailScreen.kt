package com.example.hearhome.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.example.hearhome.ui.components.AudioPlayer
import com.example.hearhome.ui.components.AudioRecorder
import com.example.hearhome.ui.components.EmojiTextField
import com.example.hearhome.utils.TestUtils
import kotlinx.coroutines.launch

/**
 * 动态详情界面
 * 显示动态及其评论
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    navController: NavController,
    postId: Int,
    currentUserId: Int
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    
    // 需要先获取post才能知道spaceId
    var spaceId by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(postId) {
        val post = db.spacePostDao().getPostById(postId)
        if (post != null) {
            spaceId = post.spaceId
        }
    }
    
    if (spaceId == 0) {
        // 加载中
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    // 使用key让ViewModel在spaceId变化时重新创建
    val viewModel: SpacePostViewModel = viewModel(
        key = "post_detail_$spaceId",
        factory = SpacePostViewModelFactory(
            db.spacePostDao(),
            db.userDao(),
            db.postFavoriteDao(),
            spaceId,
            currentUserId,
            context
        )
    )
    
    val selectedPost by viewModel.selectedPost.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val scope = rememberCoroutineScope()
    
    var commentText by remember { mutableStateOf("") }
    var replyToUser by remember { mutableStateOf<CommentInfo?>(null) }
    var showAudioRecorder by remember { mutableStateOf(false) }
    var audioPath by remember { mutableStateOf<String?>(null) }
    var audioDuration by remember { mutableLongStateOf(0L) }
    
    LaunchedEffect(postId) {
        viewModel.selectPost(postId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("动态详情") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 动态内容区域
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 动态卡片
                item {
                    selectedPost?.let { postInfo ->
                        PostCard(
                            postInfo = postInfo,
                            currentUserId = currentUserId,
                            onLike = {
                                scope.launch {
                                    viewModel.toggleLike(postInfo.post.id)
                                }
                            },
                            onComment = { },
                            onDelete = {
                                scope.launch {
                                    viewModel.deletePost(postInfo.post.id)
                                    navController.navigateUp()
                                }
                            },
                            onFavorite = {
                                scope.launch {
                                    viewModel.toggleFavorite(postInfo.post.id)
                                }
                            }
                        )
                    }
                }
                
                // 评论分隔
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "评论 (${comments.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // 评论列表
                if (comments.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "暂无评论",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    items(comments) { commentInfo ->
                        CommentItem(
                            commentInfo = commentInfo,
                            currentUserId = currentUserId,
                            onReply = {
                                replyToUser = commentInfo
                            },
                            onDelete = {
                                scope.launch {
                                    viewModel.deleteComment(
                                        commentInfo.comment.id,
                                        postId
                                    )
                                }
                            }
                        )
                    }
                }
            }
            
            // 评论输入框
            Surface(
                shadowElevation = 8.dp,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // 显示已录制的语音
                    if (audioPath != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AudioPlayer(
                                audioPath = audioPath!!,
                                duration = audioDuration,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    audioPath = null
                                    audioDuration = 0L
                                }
                            ) {
                                Icon(Icons.Default.Close, "删除语音")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 显示回复目标
                        if (replyToUser != null) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "回复 @${replyToUser?.author?.nickname}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                EmojiTextField(
                                    value = commentText,
                                    onValueChange = { commentText = it },
                                    label = "回复内容",
                                    placeholder = "说点什么...",
                                    maxLines = 3,
                                    minHeight = 60
                                )
                            }
                            IconButton(onClick = { replyToUser = null }) {
                                Icon(Icons.Default.Close, "取消回复")
                            }
                        } else {
                            EmojiTextField(
                                value = commentText,
                                onValueChange = { commentText = it },
                                modifier = Modifier.weight(1f),
                                label = "评论",
                                placeholder = "说点什么...",
                                maxLines = 3,
                                minHeight = 60
                            )
                        }
                        
                        Spacer(Modifier.width(8.dp))
                        
                        // 语音按钮
                        IconButton(
                            onClick = { showAudioRecorder = true }
                        ) {
                            Icon(Icons.Default.Mic, "语音消息")
                        }
                        
                        // 测试按钮（仅模拟器显示）
                        if (TestUtils.isEmulator()) {
                            IconButton(
                                onClick = {
                                    val mockPath = TestUtils.createMockAudioFile(context)
                                    if (mockPath != null) {
                                        audioPath = mockPath
                                        audioDuration = TestUtils.getMockAudioDuration()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Science, "模拟语音", tint = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                        
                        // 发送按钮
                        Button(
                            onClick = {
                                if (commentText.isNotBlank() || audioPath != null) {
                                    scope.launch {
                                        viewModel.addComment(
                                            postId = postId,
                                            content = commentText.ifBlank { "[语音消息]" },
                                            replyToUserId = replyToUser?.author?.uid,
                                            audioPath = audioPath,
                                            audioDuration = audioDuration
                                        )
                                        commentText = ""
                                        audioPath = null
                                        audioDuration = 0L
                                        replyToUser = null
                                    }
                                }
                            },
                            enabled = commentText.isNotBlank() || audioPath != null
                        ) {
                            Text("发送")
                        }
                    }
                }
            }
        }
        
        // 语音录制对话框
        if (showAudioRecorder) {
            AudioRecorder(
                onAudioRecorded = { path, duration ->
                    audioPath = path
                    audioDuration = duration
                    showAudioRecorder = false
                },
                onDismiss = {
                    showAudioRecorder = false
                }
            )
        }
    }
}

@Composable
fun CommentItem(
    commentInfo: CommentInfo,
    currentUserId: Int,
    onReply: () -> Unit,
    onDelete: () -> Unit
) {
    val comment = commentInfo.comment
    val author = commentInfo.author
    val replyToUser = commentInfo.replyToUser
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(author.avatarColor.toColorInt()))
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = author.nickname,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatTimestamp(comment.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                Row {
                    IconButton(onClick = onReply) {
                        Icon(
                            Icons.Default.Reply,
                            "回复",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (comment.authorId == currentUserId) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                "删除",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // 显示语音消息
            if (comment.audioPath != null && comment.audioDuration != null) {
                AudioPlayer(
                    audioPath = comment.audioPath!!,
                    duration = comment.audioDuration!!,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
            }
            
            // 显示回复对象
            if (replyToUser != null) {
                Text(
                    text = "回复 @${replyToUser.nickname}: ${comment.content}",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = comment.content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
