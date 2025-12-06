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
    
    // @提醒相关状态
    var hasPendingMention by remember { mutableStateOf(false) }
    var mentionResponded by remember { mutableStateOf(false) }
    var mentionExpired by remember { mutableStateOf(false) }  // 是否已超时
    var mentionStatus by remember { mutableStateOf<String?>(null) }  // 提醒状态

    LaunchedEffect(postId) {
        viewModel.selectPost(postId)
        
        // 检查用户是否有待处理的@提醒以及是否已超时
        withContext(Dispatchers.IO) {
            try {
                val mention = db.postMentionDao().getMentionForPost(postId, currentUserId)
                if (mention != null) {
                    mentionStatus = mention.status
                    val timeoutMillis = mention.createdAt + mention.timeoutSeconds * 1000
                    val now = System.currentTimeMillis()
                    
                    when (mention.status) {
                        "pending" -> {
                            // 检查是否已超时
                            if (now >= timeoutMillis) {
                                // 已超时但状态还是pending，标记为expired
                                db.postMentionDao().markAsExpired(mention.id)
                                mentionExpired = true
                                hasPendingMention = false
                            } else {
                                hasPendingMention = true
                                mentionExpired = false
                            }
                        }
                        "expired" -> {
                            mentionExpired = true
                            hasPendingMention = false
                        }
                        "viewed", "ignored" -> {
                            mentionResponded = true
                            hasPendingMention = false
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // 处理已读操作
    val handleMarkAsViewed: () -> Unit = {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // 再次检查是否已超时（防止在用户操作期间超时）
                    val mention = db.postMentionDao().getMentionForPost(postId, currentUserId)
                    if (mention != null && mention.status == "pending") {
                        val timeoutMillis = mention.createdAt + mention.timeoutSeconds * 1000
                        if (System.currentTimeMillis() < timeoutMillis) {
                            db.postMentionDao().markAsViewed(
                                postId = postId,
                                userId = currentUserId,
                                viewedTime = System.currentTimeMillis()
                            )
                            hasPendingMention = false
                            mentionResponded = true
                        } else {
                            // 已超时，标记为expired
                            db.postMentionDao().markAsExpired(mention.id)
                            mentionExpired = true
                            hasPendingMention = false
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        Unit
    }
    
    // 处理忽略操作
    val handleMarkAsIgnored: () -> Unit = {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    db.postMentionDao().markAsIgnored(
                        postId = postId,
                        userId = currentUserId,
                        ignoredTime = System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            hasPendingMention = false
            mentionResponded = true
        }
        Unit
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
            // @提醒响应提示卡片（仅在有待处理提醒且未超时时显示）
            if (hasPendingMention && !mentionExpired) {
                MentionResponseCard(
                    onMarkAsViewed = handleMarkAsViewed,
                    onMarkAsIgnored = handleMarkAsIgnored
                )
            }
            
            // 超时提示卡片（在超时后显示）
            if (mentionExpired) {
                MentionExpiredCard()
            }
            
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

/**
 * @提醒响应卡片
 * 显示"已读"和"忽略"按钮，让用户选择如何响应@提醒
 */
@Composable
fun MentionResponseCard(
    onMarkAsViewed: () -> Unit,
    onMarkAsIgnored: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "有人@了你，请查看这条动态",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                text = "请选择如何响应这个提醒：",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 已读按钮（接受打卡）
                Button(
                    onClick = onMarkAsViewed,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("已读")
                }
                
                // 忽略按钮（拒绝打卡）
                OutlinedButton(
                    onClick = onMarkAsIgnored,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.outline
                    )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("忽略")
                }
            }
        }
    }
}

/**
 * 提醒已超时卡片
 * 当用户的@提醒已超时时显示，告知用户无法再进行"已读"操作
 */
@Composable
fun MentionExpiredCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "@提醒已超时",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                text = "你没有在规定时间内响应这个提醒，已无法标记为已读。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
            )
        }
    }
}
