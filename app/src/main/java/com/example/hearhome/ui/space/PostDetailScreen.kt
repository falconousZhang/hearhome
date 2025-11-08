package com.example.hearhome.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.hearhome.model.AttachmentType
import com.example.hearhome.model.PendingAttachment
import com.example.hearhome.ui.components.AudioPlayer
import com.example.hearhome.ui.components.EmojiTextField
import com.example.hearhome.ui.components.attachments.AttachmentSelector
import com.example.hearhome.ui.components.attachments.AttachmentAudioList
import com.example.hearhome.ui.components.attachments.AttachmentGallery
import com.example.hearhome.utils.AttachmentFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            db.mediaAttachmentDao(),
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
    var attachments by remember { mutableStateOf<List<PendingAttachment>>(emptyList()) }
    
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
                    if (replyToUser != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "回复 @${replyToUser?.author?.nickname}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = { replyToUser = null }) {
                                Icon(Icons.Default.Close, contentDescription = "取消回复")
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    EmojiTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        label = if (replyToUser != null) "回复内容" else "评论",
                        placeholder = "说点什么...",
                        maxLines = 4,
                        minHeight = 80
                    )

                    Spacer(Modifier.height(8.dp))

                    AttachmentSelector(
                        attachments = attachments,
                        onAttachmentsChange = { attachments = it },
                        enableTestTools = true
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                if (commentText.isNotBlank() || attachments.isNotEmpty()) {
                                    scope.launch {
                                        val resolved = withContext(Dispatchers.IO) {
                                            AttachmentFileHelper.resolvePendingAttachments(
                                                context,
                                                attachments
                                            )
                                        }
                                        val fallbackContent = when {
                                            resolved.any { it.type == AttachmentType.AUDIO } -> "[语音消息]"
                                            resolved.any { it.type == AttachmentType.IMAGE } -> "[图片]"
                                            else -> "[附件]"
                                        }
                                        val sendContent = commentText.ifBlank { fallbackContent }
                                        val success = viewModel.addComment(
                                            postId = postId,
                                            content = sendContent,
                                            replyToUserId = replyToUser?.author?.uid,
                                            attachments = resolved
                                        )
                                        if (success) {
                                            commentText = ""
                                            attachments = emptyList()
                                            replyToUser = null
                                        }
                                    }
                                }
                            },
                            enabled = commentText.isNotBlank() || attachments.isNotEmpty()
                        ) {
                            Text("发送")
                        }
                    }
                }
            }
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
            
            val hasAudioAttachments = commentInfo.attachments.any {
                AttachmentType.fromStorage(it.type) == AttachmentType.AUDIO
            }
            if (hasAudioAttachments) {
                AttachmentAudioList(
                    attachments = commentInfo.attachments,
                    audioItemModifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
            } else if (comment.audioPath != null && comment.audioDuration != null) {
                // 兼容旧数据
                AudioPlayer(
                    audioPath = comment.audioPath!!,
                    duration = comment.audioDuration!!,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
            }

            val hasImageAttachments = commentInfo.attachments.any {
                AttachmentType.fromStorage(it.type) == AttachmentType.IMAGE
            }
            if (hasImageAttachments) {
                Spacer(Modifier.height(4.dp))
                AttachmentGallery(
                    attachments = commentInfo.attachments,
                    imageWidth = 80.dp,
                    imageHeight = 80.dp,
                    cornerRadius = 8.dp
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
