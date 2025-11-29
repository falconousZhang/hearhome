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
import com.example.hearhome.data.local.*
import com.example.hearhome.model.AttachmentType
import com.example.hearhome.model.PendingAttachment
import com.example.hearhome.ui.components.EmojiTextField
import com.example.hearhome.ui.components.attachments.AttachmentAudioList
import com.example.hearhome.ui.components.attachments.AttachmentGallery
import com.example.hearhome.ui.components.attachments.AttachmentSelector
import com.example.hearhome.utils.AttachmentFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * 空间详情界面
 * 顶部新增：纪念日倒计时卡片
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
    val anniversaryDao = remember { db.anniversaryDao() }

    // 空间管理 VM
    val spaceViewModel: SpaceViewModel = viewModel(
        key = "space_detail_$spaceId",
        factory = SpaceViewModelFactory(
            db.spaceDao(),
            db.userDao(),
            db.coupleDao(),
            currentUserId
        )
    )
    // 动态 VM
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
    val isAdmin = currentUserRole == "admin" || currentUserRole == "owner"
    
    // DEBUG: 打印当前角色（用于调试）
    LaunchedEffect(currentUserRole) {
        println("[DEBUG SpaceDetailScreen] currentUserRole=$currentUserRole, isAdmin=$isAdmin")
        println("[DEBUG SpaceDetailScreen] currentSpace=$currentSpace")
        println("[DEBUG SpaceDetailScreen] spaceMembers count=${spaceMembers.size}")
    }

    // 纪念日加载与倒计时刷新
    var anniversaries by remember { mutableStateOf(listOf<Anniversary>()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(spaceId) {
        spaceViewModel.selectSpace(spaceId)
        anniversaries = withContext(Dispatchers.IO) {
            anniversaryDao.listBySpace(spaceId).filter { it.status == "active" }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000) // 每分钟刷新一次
            now = System.currentTimeMillis()
        }
    }

    var showPostDialog by remember { mutableStateOf(false) }
    var showMembersDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

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
                            if (isAdmin) {
                                DropdownMenuItem(
                                    text = { Text("成员管理") },
                                    onClick = {
                                        showMoreMenu = false
                                        navController.navigate("space_manage/$spaceId/$currentUserId")
                                    }
                                )
                            }
                            Divider()
                            DropdownMenuItem(
                                text = { Text("纪念日") },
                                onClick = {
                                    showMoreMenu = false
                                    navController.navigate("anniversary/$spaceId/$currentUserId")
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showPostDialog = true }) {
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
            // 纪念日倒计时卡片
            item {
                AnniversaryCountdownCard(
                    anniversaries = anniversaries,
                    nowMillis = now,
                    onManage = { navController.navigate("anniversary/$spaceId/$currentUserId") }
                )
            }
            // 空间信息（含打卡状态）
            item { 
                currentSpace?.let { space ->
                    SpaceInfoCard(
                        space = space,
                        currentUserId = currentUserId,
                        spaceDao = db.spaceDao()
                    )
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
                        onLike = { scope.launch { postViewModel.toggleLike(postInfo.post.id) } },
                        onComment = {
                            navController.navigate("post_detail/${postInfo.post.id}/$currentUserId")
                        },
                        onDelete = { scope.launch { postViewModel.deletePost(postInfo.post.id) } },
                        onFavorite = { scope.launch { postViewModel.toggleFavorite(postInfo.post.id) } }
                    )
                }
            }
        }
    }

    if (showPostDialog) {
        CreatePostScreen(
            spaceMembers = spaceMembers,
            currentUserId = currentUserId,
            onDismiss = { showPostDialog = false },
            onPost = { content, pendingAttachments, mentionedUserIds, timeoutSeconds ->
                scope.launch {
                    val resolvedAttachments = withContext(Dispatchers.IO) {
                        AttachmentFileHelper.resolvePendingAttachments(context, pendingAttachments)
                    }
                    val postId = postViewModel.createPost(content, resolvedAttachments)
                    if (postId > 0) {
                        // 创建提醒记录
                        if (mentionedUserIds.isNotEmpty() && timeoutSeconds > 0) {
                            val mentions = mentionedUserIds.map { userId ->
                                PostMention(
                                    postId = postId.toInt(),
                                    mentionedUserId = userId,
                                    mentionerUserId = currentUserId,
                                    timeoutSeconds = timeoutSeconds
                                )
                            }
                            db.postMentionDao().insertMentions(mentions)
                        }
                        showPostDialog = false
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
}

/* ------------------- 纪念日倒计时卡片 ------------------- */

@Composable
private fun AnniversaryCountdownCard(
    anniversaries: List<Anniversary>,
    nowMillis: Long,
    onManage: () -> Unit
) {
    val upcoming = remember(anniversaries, nowMillis) {
        anniversaries
            .map { it to nextOccurrenceMillis(it.dateMillis, nowMillis) }
            .sortedBy { it.second }
            .take(3)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ✅ 正确：Row 的子项使用 Modifier.weight(1f)
                Text(
                    "纪念日倒计时",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onManage) { Text("管理纪念日") }
            }

            if (upcoming.isEmpty()) {
                Text(
                    "暂无已生效的纪念日，去“管理纪念日”新建一个吧～",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    upcoming.forEach { (ann, target) ->
                        val remain = max(0, target - nowMillis)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (ann.style) {
                                "ring" -> RingCountdown(remain, ann.name, target)
                                else -> SimpleCountdown(remain, ann.name, target)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleCountdown(
    remainingMillis: Long,
    title: String,
    target: Long,
    modifier: Modifier = Modifier
) {
    val (d, h, m) = splitTime(remainingMillis)

    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "还有 ${d}天 ${h}小时 ${m}分钟",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(target)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


@Composable
private fun RingCountdown(remainingMillis: Long, title: String, target: Long) {
    val totalOneYear = 365L * 24 * 60 * 60 * 1000 // 粗略一年
    val progress = 1f - (remainingMillis.toFloat() / totalOneYear.toFloat()).coerceIn(0f, 1f)

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(progress = progress, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        Spacer(Modifier.width(12.dp))
        SimpleCountdown(remainingMillis, title, target)
    }
}

/* 计算下一次触发时刻（每年同月同日同时间） */
private fun nextOccurrenceMillis(baseMillis: Long, now: Long): Long {
    val base = Calendar.getInstance().apply { timeInMillis = baseMillis }
    val target = Calendar.getInstance().apply { timeInMillis = now }
    target.set(Calendar.MONTH, base.get(Calendar.MONTH))
    target.set(Calendar.DAY_OF_MONTH, base.get(Calendar.DAY_OF_MONTH))
    target.set(Calendar.HOUR_OF_DAY, base.get(Calendar.HOUR_OF_DAY))
    target.set(Calendar.MINUTE, base.get(Calendar.MINUTE))
    target.set(Calendar.SECOND, 0)
    target.set(Calendar.MILLISECOND, 0)
    if (target.timeInMillis <= now) target.add(Calendar.YEAR, 1)
    return target.timeInMillis
}

private fun splitTime(millis: Long): Triple<Long, Long, Long> {
    val minutes = millis / 60_000
    val days = minutes / (60 * 24)
    val hours = (minutes % (60 * 24)) / 60
    val mins = minutes % 60
    return Triple(days, hours, mins)
}

/* ------------------- 成员/动态 等你的现有实现（保持不变） ------------------- */

@Composable
private fun SpaceMembersDialog(
    members: List<SpaceMemberInfo>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
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
fun SpaceInfoCard(
    space: Space,
    currentUserId: Int,
    spaceDao: SpaceDao
) {
    var checkInStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(space.id, space.checkInIntervalSeconds) {
        if (space.checkInIntervalSeconds > 0) {
            checkInStatus = com.example.hearhome.utils.CheckInHelper.getCheckInStatusText(
                space, currentUserId, spaceDao
            )
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(space.coverColor.toColorInt()).copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
            space.description?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            
            // 打卡状态显示
            if (space.checkInIntervalSeconds > 0 && checkInStatus != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (checkInStatus == "需要打卡") 
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            else 
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = if (checkInStatus == "需要打卡")
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "打卡提醒: $checkInStatus",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (checkInStatus == "需要打卡")
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }
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

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                            .background(Color(author?.avatarColor?.toColorInt() ?: 0xFFCCCCCC.toInt()))
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
                if (post.authorId == currentUserId) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // 动态文字内容
            if (post.content.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = post.content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // 图片附件展示
            val legacyImagePaths = remember(post.images) {
                parseLegacyImagePaths(post.images)
            }
            val imageAttachments = postInfo.attachments.filter {
                AttachmentType.fromStorage(it.type) == AttachmentType.IMAGE
            }
            
            // 调试日志：帮助诊断图片加载问题
            if (imageAttachments.isNotEmpty() || legacyImagePaths.isNotEmpty()) {
                android.util.Log.d("PostCard", 
                    "Post ${post.id}: ${imageAttachments.size} attachments, ${legacyImagePaths.size} legacy paths"
                )
                imageAttachments.forEach { att ->
                    android.util.Log.d("PostCard", "  Attachment: ${att.uri}")
                }
                legacyImagePaths.forEach { path ->
                    android.util.Log.d("PostCard", "  Legacy: $path")
                }
            }
            
            val hasImageAttachments = imageAttachments.isNotEmpty() || legacyImagePaths.isNotEmpty()
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
            
            // 提醒状态显示
            PostMentionStatusView(
                postId = post.id,
                authorId = post.authorId,
                currentUserId = currentUserId
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = onLike) {
                    Icon(
                        imageVector = if (postInfo.hasLiked) Icons.Filled.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "点赞",
                        tint = if (postInfo.hasLiked) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(4.dp)); Text("${post.likeCount}")
                }
                TextButton(onClick = onComment) {
                    Icon(Icons.Default.Comment, "评论")
                    Spacer(Modifier.width(4.dp)); Text("${post.commentCount}")
                }
                TextButton(onClick = onFavorite) {
                    Icon(
                        imageVector = if (postInfo.hasFavorited) Icons.Filled.Star else Icons.Default.StarBorder,
                        contentDescription = "收藏",
                        tint = if (postInfo.hasFavorited) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(4.dp)); Text(if (postInfo.hasFavorited) "已收藏" else "收藏")
                }
            }
        }
    }
}

@Composable
fun CreatePostScreen(
    spaceMembers: List<SpaceMemberInfo>,
    currentUserId: Int,
    onDismiss: () -> Unit,
    onPost: (String, List<PendingAttachment>, List<Int>, Long) -> Unit
) {
    var content by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<PendingAttachment>>(emptyList()) }
    var showMentionSettings by remember { mutableStateOf(false) }
    var selectedUserIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf("") }
    
    // 过滤掉当前用户
    val selectableMembers = spaceMembers.filter { it.user.uid != currentUserId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发布动态") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
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
                
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                // @提醒功能入口
                OutlinedButton(
                    onClick = { showMentionSettings = !showMentionSettings },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (selectedUserIds.isEmpty()) "@提醒某人查看" 
                        else "@已选择 ${selectedUserIds.size} 人"
                    )
                }
                
                // 提醒设置展开区域
                if (showMentionSettings) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "选择要提醒的成员",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            
                            // 成员选择列表
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 150.dp)
                            ) {
                                items(selectableMembers.size) { index ->
                                    val member = selectableMembers[index]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedUserIds = if (member.user.uid in selectedUserIds) {
                                                    selectedUserIds - member.user.uid
                                                } else {
                                                    selectedUserIds + member.user.uid
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = member.user.uid in selectedUserIds,
                                            onCheckedChange = null
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(Color(member.user.avatarColor.toColorInt()))
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(member.user.nickname)
                                    }
                                }
                            }
                            
                            if (selectedUserIds.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "设置查看超时时间",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = hours,
                                        onValueChange = { 
                                            if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                                hours = it
                                            }
                                        },
                                        label = { Text("时", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    Text(":")
                                    OutlinedTextField(
                                        value = minutes,
                                        onValueChange = { 
                                            if (it.isEmpty() || (it.all { char -> char.isDigit() } && it.toIntOrNull()?.let { num -> num < 60 } == true)) {
                                                minutes = it
                                            }
                                        },
                                        label = { Text("分", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    Text(":")
                                    OutlinedTextField(
                                        value = seconds,
                                        onValueChange = { 
                                            if (it.isEmpty() || (it.all { char -> char.isDigit() } && it.toIntOrNull()?.let { num -> num < 60 } == true)) {
                                                seconds = it
                                            }
                                        },
                                        label = { Text("秒", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                
                                Text(
                                    "提示：如果被提醒人在此时间内未查看，将收到弹窗提醒",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
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
                    if (content.isNotBlank() || attachments.isNotEmpty()) {
                        val h = hours.toLongOrNull() ?: 0
                        val m = minutes.toLongOrNull() ?: 0
                        val s = seconds.toLongOrNull() ?: 0
                        val timeoutSeconds = h * 3600 + m * 60 + s
                        onPost(content, attachments, selectedUserIds.toList(), timeoutSeconds)
                    }
                },
                enabled = content.isNotBlank() || attachments.isNotEmpty()
            ) { Text("发布") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/* ------------------- 工具 ------------------- */

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
                    if (cleaned.isNotEmpty()) add(cleaned)
                }
            }
        }.getOrElse { parseCommaSeparatedImagePaths(trimmed) }
    } else parseCommaSeparatedImagePaths(trimmed)
}

private fun parseCommaSeparatedImagePaths(value: String): List<String> =
    value.split(",").map { it.trim().trim('"') }.filter { it.isNotEmpty() }

fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        diff < 604_800_000 -> "${diff / 86_400_000}天前"
        else -> SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

/**
 * 动态提醒状态显示组件
 * 显示该动态@提醒了谁，以及查看状态
 */
@Composable
fun PostMentionStatusView(
    postId: Int,
    authorId: Int,
    currentUserId: Int
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    val mentions by db.postMentionDao().getMentionsWithUserInfo(postId).collectAsState(initial = emptyList())
    
    // 只有发布者或被提醒人可以看到提醒状态
    val canViewMentions = currentUserId == authorId || mentions.any { it.mentionedUserId == currentUserId }
    
    if (mentions.isNotEmpty() && canViewMentions) {
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "提醒了 ${mentions.size} 人查看",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                mentions.forEach { mention ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color((mention.avatarColor ?: "#CCCCCC").toColorInt()))
                        )
                        Spacer(Modifier.width(8.dp))
                        
                        Text(
                            text = mention.nickname ?: "未知用户",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // 查看状态
                        if (mention.viewedAt != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF4CAF50)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = formatTimestamp(mention.viewedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        } else {
                            // 显示剩余时间或已超时
                            val timeoutMillis = mention.createdAt + mention.timeoutSeconds * 1000
                            val now = System.currentTimeMillis()
                            
                            if (now < timeoutMillis) {
                                val remainingSeconds = (timeoutMillis - now) / 1000
                                Text(
                                    text = com.example.hearhome.utils.CheckInHelper.formatInterval(remainingSeconds),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = "未查看",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
