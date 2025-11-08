package com.example.hearhome.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.hearhome.ui.components.EmojiTextField
import com.example.hearhome.ui.components.attachments.AttachmentSelector
import com.example.hearhome.ui.components.attachments.AttachmentAudioList
import com.example.hearhome.ui.components.attachments.AttachmentGallery
import com.example.hearhome.utils.AttachmentFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

/**
 * 空间详情界面
 * 显示空间动态、成员等信息
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceDetailScreen(
    navController: NavController,
    spaceId: Int,
    currentUserId: Int
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    
    // 空间管理ViewModel - 使用 key 确保每个空间使用独立的实例
    val spaceViewModel: SpaceViewModel = viewModel(
        key = "space_detail_$spaceId",
        factory = SpaceViewModelFactory(
            db.spaceDao(),
            db.userDao(),
            db.coupleDao(),
            currentUserId
        )
    )
    
    // 空间动态ViewModel
    val postViewModel: SpacePostViewModel = viewModel(
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
    
    val currentSpace by spaceViewModel.currentSpace.collectAsState()
    val posts by postViewModel.posts.collectAsState()
    val currentUserRole by spaceViewModel.currentUserRole.collectAsState()
    val spaceMembers by spaceViewModel.spaceMembers.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 判断是否有管理权限（管理员或所有者）
    val isAdmin = currentUserRole == "admin" || currentUserRole == "owner"
    
    // 状态管理
    var showPostDialog by remember { mutableStateOf(false) }
    var showMembersDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showDissolveDialog by remember { mutableStateOf(false) }
    
    // 加载空间信息和用户角色
    LaunchedEffect(spaceId) {
        spaceViewModel.selectSpace(spaceId)
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(currentSpace?.name ?: "空间") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showMembersDialog = true }) {
                        Icon(Icons.Default.Group, "成员")
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, "更多")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, null, Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("我的收藏")
                                    }
                                },
                                onClick = {
                                    showMoreMenu = false
                                    navController.navigate("favorites/$spaceId/$currentUserId")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("空间信息") },
                                onClick = {
                                    showMoreMenu = false
                                    navController.navigate("space_info/$spaceId/$currentUserId")
                                }
                            )
                            // 显示成员管理选项（管理员和所有者可见）
                            // 只要 currentUserRole 是 admin 或 owner 就显示
                            if (currentUserRole == "admin" || currentUserRole == "owner") {
                                DropdownMenuItem(
                                    text = { Text("成员管理") },
                                    onClick = {
                                        showMoreMenu = false
                                        navController.navigate("space_manage/$spaceId/$currentUserId")
                                    }
                                )
                            }
                            if (currentUserRole == "owner") {
                                DropdownMenuItem(
                                    text = { Text("解散空间") },
                                    onClick = {
                                        showMoreMenu = false
                                        showDissolveDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPostDialog = true }
            ) {
                Icon(Icons.Default.Edit, "发布动态")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 空间信息卡片
            item {
                currentSpace?.let { space ->
                    SpaceInfoCard(space)
                }
            }
            
            // 动态列表
            if (posts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无动态,快来发布第一条吧!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                items(posts) { postInfo ->
                    PostCard(
                        postInfo = postInfo,
                        currentUserId = currentUserId,
                        onLike = {
                            scope.launch {
                                postViewModel.toggleLike(postInfo.post.id)
                            }
                        },
                        onComment = {
                            navController.navigate("post_detail/${postInfo.post.id}/$currentUserId")
                        },
                        onDelete = {
                            scope.launch {
                                postViewModel.deletePost(postInfo.post.id)
                            }
                        },
                        onFavorite = {
                            scope.launch {
                                postViewModel.toggleFavorite(postInfo.post.id)
                            }
                        }
                    )
                }
            }
        }
    }
    
    // 发布动态对话框
    if (showPostDialog) {
        CreatePostScreen(
            onDismiss = { showPostDialog = false },
            onPost = { content, pendingAttachments ->
                scope.launch {
                    val resolvedAttachments = withContext(Dispatchers.IO) {
                        AttachmentFileHelper.resolvePendingAttachments(
                            context,
                            pendingAttachments
                        )
                    }
                    // 发布动态
                    val success = postViewModel.createPost(content, resolvedAttachments)
                    if (success) {
                        showPostDialog = false
                        // 响应式Flow会自动更新，无需手动刷新
                    }
                }
            }
        )
    }

    if (showMembersDialog) {
        SpaceMembersDialog(
            members = spaceMembers,
            onDismiss = { showMembersDialog = false }
        )
    }

    if (showDissolveDialog) {
        val isCoupleSpace = currentSpace?.type == "couple"
        AlertDialog(
            onDismissRequest = { showDissolveDialog = false },
            title = { Text("确认解散空间") },
            text = {
                Text(
                    if (isCoupleSpace) "解散情侣空间将同时解除情侣关系，确认继续吗？"
                    else "空间解散后成员将无法访问历史内容，确认继续吗？"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val result = spaceViewModel.dissolveSpace(spaceId)
                            showDissolveDialog = false
                            when (result) {
                                is DissolveSpaceResult.Success -> {
                                    snackbarHostState.showSnackbar(result.message)
                                    navController.navigateUp()
                                }
                                is DissolveSpaceResult.Failure -> {
                                    snackbarHostState.showSnackbar(result.message)
                                }
                            }
                        }
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDissolveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SpaceMembersDialog(
    members: List<SpaceMemberInfo>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        title = { Text("空间成员") },
        text = {
            if (members.isEmpty()) {
                Text("暂无成员")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    members.forEach { memberInfo ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(memberInfo.user.avatarColor.toColorInt()))
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(memberInfo.user.nickname.ifBlank { "未设置昵称" })
                                    Text(
                                        "ID: ${memberInfo.user.uid}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Text(
                                when (memberInfo.member.role) {
                                    "owner" -> "所有者"
                                    "admin" -> "管理员"
                                    else -> "成员"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun SpaceInfoCard(space: com.example.hearhome.data.local.Space) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(space.coverColor.toColorInt()).copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = Color(space.coverColor.toColorInt())
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (space.type) {
                        "couple" -> "情侣空间"
                        "family" -> "家族空间"
                        else -> "空间"
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }
            
            if (space.description != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = space.description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "邀请码: ${space.inviteCode}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PostCard(
    postInfo: PostWithAuthorInfo,
    currentUserId: Int,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onDelete: () -> Unit,
    onFavorite: () -> Unit = {}
) {
    val post = postInfo.post
    val author = postInfo.author
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 作者信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Color(author?.avatarColor?.toColorInt() ?: 0xFFCCCCCC.toInt())
                            )
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = author?.nickname ?: "未知用户",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatTimestamp(post.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                // 删除按钮(仅作者可见)
                if (post.authorId == currentUserId) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // 动态内容
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyMedium
            )

            val legacyImagePaths = remember(post.images) {
                parseLegacyImagePaths(post.images)
            }
            val hasImageAttachments = postInfo.attachments.any {
                AttachmentType.fromStorage(it.type) == AttachmentType.IMAGE
            } || legacyImagePaths.isNotEmpty()
            if (hasImageAttachments) {
                Spacer(Modifier.height(12.dp))
                AttachmentGallery(
                    attachments = postInfo.attachments,
                    legacyImagePaths = legacyImagePaths,
                    imageHeight = 150.dp,
                    cornerRadius = 8.dp
                )
            }

            val hasAudioAttachments = postInfo.attachments.any {
                AttachmentType.fromStorage(it.type) == AttachmentType.AUDIO
            }
            if (hasAudioAttachments) {
                Spacer(Modifier.height(12.dp))
                AttachmentAudioList(
                    attachments = postInfo.attachments,
                    audioItemModifier = Modifier.fillMaxWidth()
                )
            }

            // 定位信息
            if (post.location != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = post.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 点赞
                TextButton(onClick = onLike) {
                    Icon(
                        imageVector = if (postInfo.hasLiked) Icons.Filled.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "点赞",
                        tint = if (postInfo.hasLiked) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${post.likeCount}")
                }
                
                // 评论
                TextButton(onClick = onComment) {
                    Icon(Icons.Default.Comment, "评论")
                    Spacer(Modifier.width(4.dp))
                    Text("${post.commentCount}")
                }
                
                // 收藏
                TextButton(onClick = onFavorite) {
                    Icon(
                        imageVector = if (postInfo.hasFavorited) Icons.Filled.Star else Icons.Default.StarBorder,
                        contentDescription = "收藏",
                        tint = if (postInfo.hasFavorited) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (postInfo.hasFavorited) "已收藏" else "收藏")
                }
            }
        }
    }
}

@Composable
fun CreatePostScreen(
    onDismiss: () -> Unit,
    onPost: (String, List<PendingAttachment>) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<PendingAttachment>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发布动态") },
        text = {
            Column {
                EmojiTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = "说点什么...",
                    placeholder = "分享你的心情~",
                    maxLines = 8,
                    minHeight = 150
                )
                Spacer(modifier = Modifier.height(8.dp))
                AttachmentSelector(
                    attachments = attachments,
                    onAttachmentsChange = { attachments = it },
                    enableTestTools = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isNotBlank() || attachments.isNotEmpty()) {
                        onPost(content, attachments)
                    }
                },
                enabled = content.isNotBlank() || attachments.isNotEmpty()
            ) {
                Text("发布")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun CreatePostDialog(
    onDismiss: () -> Unit,
    onPost: (String) -> Unit
) {
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发布动态") },
        text = {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("说点什么...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 10
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isNotBlank()) {
                        onPost(content)
                    }
                },
                enabled = content.isNotBlank()
            ) {
                Text("发布")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun parseLegacyImagePaths(images: String?): List<String> {
    if (images.isNullOrBlank()) return emptyList()
    val trimmed = images.trim()
    return if (trimmed.startsWith("[")) {
        runCatching {
            val jsonArray = JSONArray(trimmed)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val raw = jsonArray.optString(index)
                    val cleaned = raw.trim().trim('"')
                    if (cleaned.isNotEmpty()) {
                        add(cleaned)
                    }
                }
            }
        }.getOrElse { parseCommaSeparatedImagePaths(trimmed) }
    } else {
        parseCommaSeparatedImagePaths(trimmed)
    }
}

private fun parseCommaSeparatedImagePaths(value: String): List<String> =
    value.split(",")
        .map { it.trim().trim('"') }
        .filter { it.isNotEmpty() }

/**
 * 格式化时间戳
 */
fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        diff < 604800_000 -> "${diff / 86400_000}天前"
        else -> {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
