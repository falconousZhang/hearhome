package com.example.hearhome.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.hearhome.pet.PetViewModel
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
    // 宠物 VM
    val petViewModel: PetViewModel = viewModel(
        key = "pet_$spaceId"
    )

    // 启动前端轮询刷新，避免改后端接口
    LaunchedEffect(spaceId) {
        postViewModel.startAutoRefresh()
    }

    val currentSpace by spaceViewModel.currentSpace.collectAsState()
    val posts by postViewModel.posts.collectAsState()
    val currentUserRole by spaceViewModel.currentUserRole.collectAsState()
    val spaceMembers by spaceViewModel.spaceMembers.collectAsState()
    val scope = rememberCoroutineScope()
    val isAdmin = currentUserRole == "admin" || currentUserRole == "owner"

    // 用户待处理的@提醒的postId列表（用于置顶）
    var pendingMentionPostIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // 加载用户在该空间的待处理@提醒
    LaunchedEffect(spaceId, currentUserId) {
        withContext(Dispatchers.IO) {
            try {
                val pendingMentions = db.postMentionDao().getPendingMentions(currentUserId)
                // 获取该空间的帖子ID
                val spacePostIds = posts.map { it.post.id }.toSet()
                pendingMentionPostIds = pendingMentions
                    .filter { it.postId in spacePostIds || true } // 先加载所有，后续筛选
                    .map { it.postId }
                    .toSet()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 对帖子进行排序：被@且pending的帖子置顶
    val sortedPosts = remember(posts, pendingMentionPostIds) {
        posts.sortedWith(compareBy(
            // 先按是否有pending @提醒排序（有的排前面）
            { if (it.post.id in pendingMentionPostIds) 0 else 1 },
            // 再按时间排序（新的排前面）
            { -it.post.timestamp }
        ))
    }

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
    
    // 打卡成功提示
    var showCheckInSuccess by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 打卡状态刷新触发器
    var checkInRefreshTrigger by remember { mutableStateOf(0L) }
    
    // 显示打卡成功提示
    LaunchedEffect(showCheckInSuccess) {
        if (showCheckInSuccess) {
            snackbarHostState.showSnackbar(
                message = "🎉 打卡成功！继续保持哦~",
                duration = SnackbarDuration.Short
            )
            showCheckInSuccess = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                    text = { Text("空间管理") },
                                    onClick = {
                                        showMoreMenu = false
                                        navController.navigate("space_manage/$spaceId/$currentUserId")
                                    }
                                )
                            }
                            // ... 前面已有的菜单------------------------wdz

                            if (currentUserRole != "owner") {
                                DropdownMenuItem(
                                    text = { Text("退出空间") },
                                    onClick = {
                                        showMoreMenu = false
                                        // 这里直接调用 ViewModel 的 leaveSpace
                                        spaceViewModel.leaveSpace(spaceId)

                                        // 退出成功后，SpaceViewModel 会 refreshMySpaces()
                                        // 这边可以直接导航回主页
                                        navController.popBackStack(
                                            route = "home/$currentUserId",
                                            inclusive = false
                                        )
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
// ... ------------------------wdz
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
            
            // 宠物卡片
            item {
                SpacePetCard(
                    spaceId = spaceId,
                    petViewModel = petViewModel
                )
            }
            
            // 空间信息（含打卡状态）
            item {
                currentSpace?.let { space ->
                    SpaceInfoCard(
                        space = space,
                        currentUserId = currentUserId,
                        spaceDao = db.spaceDao(),
                        refreshTrigger = checkInRefreshTrigger
                    )
                }
            }

            // 打卡统计卡片（仅当启用了打卡功能时显示）
            if (currentSpace?.checkInIntervalSeconds ?: 0 > 0) {
                item {
                    CheckInStatsCard(
                        spaceId = spaceId,
                        currentUserId = currentUserId,
                        spacePostDao = db.spacePostDao(),
                        userDao = db.userDao(),
                        refreshTrigger = checkInRefreshTrigger
                    )
                }
            }

            // 动态列表
            if (sortedPosts.isEmpty()) {
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
                items(sortedPosts) { postInfo ->
                    val isPendingMention = postInfo.post.id in pendingMentionPostIds
                    PostCard(
                        postInfo = postInfo,
                        currentUserId = currentUserId,
                        isPendingMention = isPendingMention, // 传递高亮标记
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
                    // 检查发帖前是否需要打卡
                    val neededCheckIn = currentSpace?.let { space ->
                        if (space.checkInIntervalSeconds > 0) {
                            withContext(Dispatchers.IO) {
                                com.example.hearhome.utils.CheckInHelper.needsCheckIn(
                                    space, currentUserId, db.spaceDao()
                                )
                            }
                        } else false
                    } ?: false
                    
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
                        
                        // 触发打卡状态刷新
                        checkInRefreshTrigger = System.currentTimeMillis()
                        
                        // 如果之前需要打卡，现在发帖成功，显示打卡成功提示
                        if (neededCheckIn && currentSpace?.checkInIntervalSeconds ?: 0 > 0) {
                            showCheckInSuccess = true
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
    spaceDao: SpaceDao,
    refreshTrigger: Long = 0L  // 刷新触发器，当值改变时立即刷新
) {
    var checkInStatus by remember { mutableStateOf<String?>(null) }
    var remainingSeconds by remember { mutableStateOf(-1L) }
    var needsCheckIn by remember { mutableStateOf(false) }

    // 每秒更新打卡状态，同时监听刷新触发器
    LaunchedEffect(space.id, space.checkInIntervalSeconds, refreshTrigger) {
        if (space.checkInIntervalSeconds > 0) {
            // 立即执行一次刷新
            withContext(Dispatchers.IO) {
                needsCheckIn = com.example.hearhome.utils.CheckInHelper.needsCheckIn(
                    space, currentUserId, spaceDao
                )
                remainingSeconds = com.example.hearhome.utils.CheckInHelper.getRemainingTime(
                    space, currentUserId, spaceDao
                )
                checkInStatus = com.example.hearhome.utils.CheckInHelper.getCheckInStatusText(
                    space, currentUserId, spaceDao
                )
            }
            // 然后每秒更新
            while (true) {
                delay(1000)
                withContext(Dispatchers.IO) {
                    needsCheckIn = com.example.hearhome.utils.CheckInHelper.needsCheckIn(
                        space, currentUserId, spaceDao
                    )
                    remainingSeconds = com.example.hearhome.utils.CheckInHelper.getRemainingTime(
                        space, currentUserId, spaceDao
                    )
                    checkInStatus = com.example.hearhome.utils.CheckInHelper.getCheckInStatusText(
                        space, currentUserId, spaceDao
                    )
                }
            }
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

            // 打卡状态显示（实时倒计时）
            if (space.checkInIntervalSeconds > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (needsCheckIn)
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = if (needsCheckIn) Icons.Default.Warning else Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (needsCheckIn)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))

                    if (needsCheckIn) {
                        Text(
                            text = "需要打卡！请发布动态",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "打卡倒计时: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = formatCheckInCountdownDetail(remainingSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (remainingSeconds < 3600) // 小于1小时高亮
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary,
                            fontWeight = if (remainingSeconds < 3600) FontWeight.Bold else FontWeight.Normal
                        )
                    }
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
    isPendingMention: Boolean = false, // 是否有待处理的@提醒（用于高亮）
    onLike: () -> Unit,
    onComment: () -> Unit,
    onDelete: () -> Unit,
    onFavorite: () -> Unit = {}
) {
    val post = postInfo.post
    val author = postInfo.author

    // 高亮边框颜色
    val borderModifier = if (isPendingMention) {
        Modifier.border(
            width = 2.dp,
            color = MaterialTheme.colorScheme.error,
            shape = MaterialTheme.shapes.medium
        )
    } else {
        Modifier
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier),
        colors = if (isPendingMention) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 如果是被@的帖子，显示置顶标签
            if (isPendingMention) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onError
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "@了你",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

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
            val imageAttachments = postInfo.attachments.filter {
                AttachmentType.fromStorage(it.type) == AttachmentType.IMAGE
            }
            val legacyImagePaths = remember(post.images, imageAttachments) {
                // 新数据已经转成附件，不再重复用 legacy 字段，避免双份展示
                if (imageAttachments.isEmpty()) parseLegacyImagePaths(post.images) else emptyList()
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
                    .heightIn(min = 320.dp, max = 600.dp)
                    .verticalScroll(rememberScrollState())
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
                                        modifier = Modifier
                                            .weight(1f)
                                            .widthIn(min = 72.dp),
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
                                        modifier = Modifier
                                            .weight(1f)
                                            .widthIn(min = 72.dp),
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
                                        modifier = Modifier
                                            .weight(1f)
                                            .widthIn(min = 72.dp),
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
 * 倒计时以 hh:mm:ss 格式每秒更新
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

    // 管理对话框状态
    var showManageDialog by remember { mutableStateOf(false) }
    // 触发刷新的计数器
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // 当前时间状态，每秒更新一次
    // 使用 derivedStateOf 确保始终使用最新时间
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // 每秒更新当前时间（始终启用以确保倒计时准确）
    val hasUnviewedMentions = mentions.any { it.viewedAt == null && it.status == "pending" }
    LaunchedEffect(hasUnviewedMentions, refreshTrigger) {
        // 首次加载或刷新时立即更新时间
        currentTime = System.currentTimeMillis()

        if (hasUnviewedMentions) {
            while (true) {
                delay(1000) // 每秒更新
                currentTime = System.currentTimeMillis()
            }
        }
    }

    // 只有发布者或被提醒人可以看到提醒状态
    val canViewMentions = currentUserId == authorId || mentions.any { it.mentionedUserId == currentUserId }
    val isCurrentUserMentioned = mentions.any { it.mentionedUserId == currentUserId }
    val currentUserMention = mentions.find { it.mentionedUserId == currentUserId }
    val isAuthor = currentUserId == authorId

    // 显示管理对话框
    if (showManageDialog && isAuthor) {
        com.example.hearhome.ui.components.MentionManageDialog(
            postId = postId,
            currentUserId = currentUserId,
            onDismiss = { showManageDialog = false },
            onMentionsChanged = {
                // 提醒已更改，触发刷新并立即更新时间
                refreshTrigger++
                currentTime = System.currentTimeMillis()
            }
        )
    }

    if (mentions.isNotEmpty() && canViewMentions) {
        Spacer(Modifier.height(8.dp))

        // 如果当前用户被@，使用高亮显示
        val containerColor = if (isCurrentUserMentioned && currentUserMention?.status == "pending") {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) // 高亮
        } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isCurrentUserMentioned) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(4.dp))

                        // 优化文案：如果当前用户被@，显示"提醒了你和其他x人"
                        val mentionText = if (isCurrentUserMentioned) {
                            val otherCount = mentions.size - 1
                            if (otherCount > 0) {
                                "提醒了你和其他 $otherCount 人查看"
                            } else {
                                "提醒了你查看"
                            }
                        } else {
                            "提醒了 ${mentions.size} 人查看"
                        }

                        Text(
                            text = mentionText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrentUserMentioned) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.secondary
                        )
                    }

                    // 作者才显示管理按钮
                    if (isAuthor && mentions.any { it.status == "pending" }) {
                        IconButton(
                            onClick = { showManageDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "管理@提醒",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
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
                        when (mention.status) {
                            "viewed" -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(0xFF4CAF50)
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = "已读 ${mention.viewedAt?.let { formatTimestamp(it) } ?: ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                            }
                            "ignored" -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.DoNotDisturb,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = "已忽略",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            else -> {
                                // pending 或其他状态，显示剩余时间或已超时（使用 hh:mm:ss 格式）
                                val timeoutMillis = mention.createdAt + mention.timeoutSeconds * 1000

                                if (currentTime < timeoutMillis) {
                                    val remainingSeconds = (timeoutMillis - currentTime) / 1000
                                    Text(
                                        text = formatCountdownHHMMSS(remainingSeconds),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (remainingSeconds < 60) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.outline,
                                        fontWeight = if (remainingSeconds < 60) FontWeight.Bold else FontWeight.Normal
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
                                            text = "已超时",
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
}

/**
 * 格式化倒计时为 hh:mm:ss 格式
 */
private fun formatCountdownHHMMSS(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

/**
 * 格式化打卡倒计时（带更详细的时间显示）
 */
private fun formatCheckInCountdownDetail(seconds: Long): String {
    if (seconds <= 0) return "00:00:00"

    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (days > 0) {
        String.format("%d天 %02d:%02d:%02d", days, hours, minutes, secs)
    } else {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    }
}

/**
 * 打卡统计卡片组件
 * 显示当前用户的打卡统计信息和最近打卡历史
 */
@Composable
fun CheckInStatsCard(
    spaceId: Int,
    currentUserId: Int,
    spacePostDao: SpacePostDao,
    userDao: UserDao,
    refreshTrigger: Long = 0L  // 刷新触发器
) {
    var myPostCount by remember { mutableStateOf(0) }
    var recentPosts by remember { mutableStateOf<List<SpacePost>>(emptyList()) }
    var allStats by remember { mutableStateOf<List<com.example.hearhome.data.local.CheckInStat>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showHistory by remember { mutableStateOf(false) }

    // 获取当前月份的起止时间
    val calendar = Calendar.getInstance()
    val currentMonthStart = calendar.apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val currentMonthEnd = calendar.apply {
        add(Calendar.MONTH, 1)
        add(Calendar.MILLISECOND, -1)
    }.timeInMillis

    var monthlyPostCount by remember { mutableStateOf(0) }

    // 监听刷新触发器，当发帖后立即刷新统计数据
    LaunchedEffect(spaceId, refreshTrigger) {
        withContext(Dispatchers.IO) {
            myPostCount = spacePostDao.getPostCountByUser(spaceId, currentUserId)
            recentPosts = spacePostDao.getRecentPostsByUser(spaceId, currentUserId, 5)
            allStats = spacePostDao.getCheckInStatsBySpace(spaceId)
            monthlyPostCount = spacePostDao.getPostCountByUserInPeriod(
                spaceId, currentUserId, currentMonthStart, currentMonthEnd
            )
            isLoading = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "打卡统计",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = { showHistory = !showHistory }) {
                    Text(if (showHistory) "收起" else "查看历史")
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                Spacer(Modifier.height(8.dp))

                // 统计概览
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        label = "总打卡",
                        value = myPostCount.toString(),
                        icon = Icons.Default.Done
                    )
                    StatItem(
                        label = "本月打卡",
                        value = monthlyPostCount.toString(),
                        icon = Icons.Default.CalendarMonth
                    )
                    StatItem(
                        label = "空间排名",
                        value = run {
                            val rank = allStats.indexOfFirst { it.authorId == currentUserId } + 1
                            if (rank > 0) "#$rank" else "-"
                        },
                        icon = Icons.Default.EmojiEvents
                    )
                }

                // 展开历史记录
                if (showHistory) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "最近打卡记录",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (recentPosts.isEmpty()) {
                        Text(
                            text = "暂无打卡记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        recentPosts.forEach { post ->
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = post.content.take(30) + if (post.content.length > 30) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatTimestamp(post.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    // 空间成员打卡排行
                    if (allStats.size > 1) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "成员打卡排行",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        allStats.take(5).forEachIndexed { index, stat ->
                            var userName by remember { mutableStateOf("加载中...") }

                            LaunchedEffect(stat.authorId) {
                                withContext(Dispatchers.IO) {
                                    val user = userDao.getUserById(stat.authorId)
                                    userName = user?.nickname ?: "用户${stat.authorId}"
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "#${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (index) {
                                            0 -> Color(0xFFFFD700) // 金
                                            1 -> Color(0xFFC0C0C0) // 银
                                            2 -> Color(0xFFCD7F32) // 铜
                                            else -> MaterialTheme.colorScheme.outline
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = userName + if (stat.authorId == currentUserId) " (我)" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (stat.authorId == currentUserId) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                Text(
                                    text = "${stat.count}次",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 统计项组件
 */
@Composable
private fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
