package com.example.hearhome.ui.space

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.rememberImagePainter
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.ui.components.EmojiTextField
import com.example.hearhome.utils.ImageUtils
import com.example.hearhome.utils.TestUtils
import kotlinx.coroutines.launch
import java.io.File
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
            currentUserId
        )
    )
    
    // 空间动态ViewModel
    val postViewModel: SpacePostViewModel = viewModel(
        factory = SpacePostViewModelFactory(
            db.spacePostDao(),
            db.userDao(),
            db.postFavoriteDao(),
            spaceId,
            currentUserId,
            context
        )
    )
    
    val currentSpace by spaceViewModel.currentSpace.collectAsState()
    val posts by postViewModel.posts.collectAsState()
    val currentUserRole by spaceViewModel.currentUserRole.collectAsState()
    val scope = rememberCoroutineScope()
    
    // 判断是否有管理权限（管理员或所有者）
    val isAdmin = currentUserRole == "admin" || currentUserRole == "owner"
    
    // 状态管理
    var showPostDialog by remember { mutableStateOf(false) }
    var showMembersDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    
    // 加载空间信息和用户角色
    LaunchedEffect(spaceId) {
        spaceViewModel.selectSpace(spaceId)
    }
    
    Scaffold(
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
            onPost = { content, imageUris ->
                scope.launch {
                    // 先保存图片到内部存储
                    val savedImagePaths = ImageUtils.saveImagesToInternalStorage(
                        context, 
                        imageUris.map { Uri.parse(it) }
                    )
                    
                    // 发布动态
                    val success = postViewModel.createPost(content, savedImagePaths)
                    if (success) {
                        showPostDialog = false
                        postViewModel.loadPosts() // Manually refresh posts
                    }
                }
            }
        )
    }
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

            // 图片显示
            if (!post.images.isNullOrEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(post.images.split(",").filter { it.isNotBlank() }) { imagePath ->
                        // 从文件路径加载图片
                        val imageFile = File(imagePath)
                        if (imageFile.exists()) {
                            Image(
                                painter = rememberImagePainter(data = imageFile),
                                contentDescription = null,
                                modifier = Modifier
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
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
    onPost: (String, List<String>) -> Unit
) {
    val context = LocalContext.current
    var content by remember { mutableStateOf("") }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        imageUris = uris
    }

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
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(Icons.Default.AddPhotoAlternate, "添加图片")
                        Spacer(Modifier.width(4.dp))
                        Text("添加图片")
                    }
                    
                    // 测试按钮（仅模拟器显示）
                    if (TestUtils.isEmulator()) {
                        OutlinedButton(
                            onClick = {
                                val mockImage = TestUtils.createMockImageFile(context)
                                if (mockImage != null) {
                                    imageUris = imageUris + Uri.fromFile(mockImage)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Science, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("生成测试图")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(imageUris) { uri ->
                        Box {
                            Image(
                                painter = rememberImagePainter(uri),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            // 删除按钮
                            IconButton(
                                onClick = {
                                    imageUris = imageUris.filter { it != uri }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "删除",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isNotBlank() || imageUris.isNotEmpty()) {
                        onPost(content, imageUris.map { it.toString() })
                    }
                },
                enabled = content.isNotBlank() || imageUris.isNotEmpty()
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
