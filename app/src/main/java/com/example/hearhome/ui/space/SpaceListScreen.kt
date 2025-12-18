package com.example.hearhome.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
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
import com.example.hearhome.data.local.Friend
import com.example.hearhome.data.local.Space
import com.example.hearhome.data.local.User
import com.example.hearhome.data.remote.ApiService
import com.example.hearhome.ui.components.AppBottomNavigation
import com.example.hearhome.utils.CheckInHelper
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 空间列表界面
 * 显示用户加入的所有空间
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceListScreen(
    navController: NavController,
    currentUserId: Int
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    
    val viewModel: SpaceViewModel = viewModel(
        factory = SpaceViewModelFactory(
            db.spaceDao(),
            db.userDao(),
            db.coupleDao(),
            currentUserId
        )
    )
    
    val mySpaces by viewModel.mySpaces.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var coupleCandidates by remember { mutableStateOf<List<User>>(emptyList()) }
    var partnerUser by remember { mutableStateOf<User?>(null) }
    var currentUser by remember { mutableStateOf<User?>(null) }
    var coupleCandidatesLoading by remember { mutableStateOf(false) }

    // 对话框状态
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinErrorMessage by remember { mutableStateOf<String?>(null) }

    // 从 API 加载情侣候选人（好友列表和当前用户信息）
    LaunchedEffect(showCreateDialog) {
        if (showCreateDialog) {
            coupleCandidatesLoading = true
            try {
                withContext(Dispatchers.IO) {
                    // 1. 从 API 获取当前用户信息
                    val userResponse = ApiService.getProfile(currentUserId)
                    val user = if (userResponse.status == HttpStatusCode.OK) {
                        userResponse.body<User>()
                    } else null

                    println("[DEBUG SpaceListScreen] Current user: uid=${user?.uid}, partnerId=${user?.partnerId}, relationshipStatus=${user?.relationshipStatus}")

                    // 2. 从 API 获取好友列表
                    val friendsResponse = ApiService.getFriends(currentUserId)
                    val friendRelations = if (friendsResponse.status == HttpStatusCode.OK) {
                        friendsResponse.body<List<Friend>>()
                    } else emptyList()

                    println("[DEBUG SpaceListScreen] Got ${friendRelations.size} friend relations from API")

                    // 3. 获取每个好友的详细信息
                    val friendUsers = friendRelations.map { relation ->
                        val friendId = if (relation.senderId == currentUserId) relation.receiverId else relation.senderId
                        async {
                            try {
                                val profileResponse = ApiService.getProfile(friendId)
                                if (profileResponse.status == HttpStatusCode.OK) {
                                    profileResponse.body<User>()
                                } else null
                            } catch (e: Exception) {
                                println("[ERROR SpaceListScreen] Failed to get profile for user $friendId: ${e.message}")
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()

                    println("[DEBUG SpaceListScreen] Got ${friendUsers.size} friend user profiles")
                    friendUsers.forEach { u ->
                        println("[DEBUG SpaceListScreen] Friend: uid=${u.uid}, nickname=${u.nickname}, relationshipStatus=${u.relationshipStatus}, partnerId=${u.partnerId}")
                    }

                    // 4. 筛选情侣候选人：单身或已与当前用户是情侣
                    val candidates = friendUsers
                        .filter { it.uid != currentUserId }
                        .filter { it.relationshipStatus != "in_relationship" || it.partnerId == currentUserId }

                    println("[DEBUG SpaceListScreen] Filtered candidates: ${candidates.size}")

                    // 5. 确定当前情侣（如果有）
                    val partner = if (user?.partnerId != null && user.partnerId != 0 && user.partnerId != currentUserId) {
                        // 先从好友列表中找
                        candidates.firstOrNull { it.uid == user.partnerId }
                            ?: friendUsers.firstOrNull { it.uid == user.partnerId }
                            ?: run {
                                // 如果不在好友列表中，单独获取
                                try {
                                    val partnerResponse = ApiService.getProfile(user.partnerId)
                                    if (partnerResponse.status == HttpStatusCode.OK) {
                                        partnerResponse.body<User>()
                                    } else null
                                } catch (e: Exception) {
                                    println("[ERROR SpaceListScreen] Failed to get partner profile: ${e.message}")
                                    null
                                }
                            }
                    } else null

                    println("[DEBUG SpaceListScreen] Partner: ${partner?.uid}, ${partner?.nickname}")

                    currentUser = user
                    coupleCandidates = candidates
                    partnerUser = partner
                }
            } catch (e: Exception) {
                println("[ERROR SpaceListScreen] Failed to load couple candidates: ${e.message}")
                e.printStackTrace()
            } finally {
                coupleCandidatesLoading = false
            }
        }
    }
    
    // 获取总的待处理提醒数量
    val pendingMentionCount by db.postMentionDao()
        .getPendingMentionCountFlow(currentUserId)
        .collectAsState(initial = 0)

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("我的空间") },
                actions = {
                    // 一键标记全部已读按钮
                    if (pendingMentionCount > 0) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        db.postMentionDao().markAllAsViewed(
                                            currentUserId,
                                            System.currentTimeMillis()
                                        )
                                    }
                                    snackbarHostState.showSnackbar("已将所有提醒标记为已读")
                                }
                            }
                        ) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text(if (pendingMentionCount <= 99) pendingMentionCount.toString() else "99+")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "一键全部已读"
                                )
                            }
                        }
                    }
                    TextButton(onClick = {
                        joinErrorMessage = null
                        showJoinDialog = true
                    }) {
                        Text("加入空间")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = {},
                text = { Text("创建空间") }
            )
        },
        bottomBar = {
            AppBottomNavigation(
                navController = navController,
                userId = currentUserId
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (mySpaces.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "暂无空间",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "点击右上角加入或创建空间",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                // 空间列表
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(mySpaces) { space ->
                        SpaceCardWithBadge(
                            space = space,
                            currentUserId = currentUserId,
                            onClick = {
                                navController.navigate("space_detail/${space.id}/$currentUserId")
                            }
                        )
                    }
                }
            }
        }
    }
    
    // 创建空间对话框
    if (showCreateDialog) {
        val hasActiveCoupleSpace = mySpaces.any { it.type == "couple" }
        CreateSpaceDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, type, description, partnerId ->
                scope.launch {
                    when (val result = viewModel.createSpace(name, type, description, partnerId)) {
                        is CreateSpaceResult.Success -> {
                            showCreateDialog = false
                            val message = result.message ?: "空间创建成功"
                            snackbarHostState.showSnackbar(message)
                        }
                        is CreateSpaceResult.RequestSent -> {
                            showCreateDialog = false
                            snackbarHostState.showSnackbar(result.message)
                        }
                        is CreateSpaceResult.Failure -> {
                            snackbarHostState.showSnackbar(result.message)
                        }
                    }
                }
            },
            currentUser = currentUser,
            coupleCandidates = coupleCandidates,
            partnerUser = partnerUser,
            hasActiveCoupleSpace = hasActiveCoupleSpace,
            isLoadingCoupleCandidates = coupleCandidatesLoading
        )
    }
    
    // 加入空间对话框
    if (showJoinDialog) {
        JoinSpaceDialog(
            onDismiss = {
                joinErrorMessage = null
                showJoinDialog = false
            },
            onJoin = { inviteCode ->
                scope.launch {
                    when (val result = viewModel.joinSpaceByCode(inviteCode)) {
                        is JoinSpaceResult.Joined -> {
                            joinErrorMessage = null
                            showJoinDialog = false
                            val message = result.message ?: "成功加入「${result.space.name}」"
                            snackbarHostState.showSnackbar(message)
                        }
                        is JoinSpaceResult.RequestPending -> {
                            joinErrorMessage = null
                            showJoinDialog = false
                            snackbarHostState.showSnackbar(result.message)
                        }
                        is JoinSpaceResult.Failure -> {
                            joinErrorMessage = result.message
                        }
                    }
                }
            },
            errorMessage = joinErrorMessage,
            onInputEdited = { joinErrorMessage = null }
        )
    }
}

/**
 * 带未读提醒计数和打卡状态的空间卡片
 */
@Composable
fun SpaceCardWithBadge(
    space: Space,
    currentUserId: Int,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    val scope = rememberCoroutineScope()
    
    // 获取该空间的待处理提醒数量
    val pendingCount by db.postMentionDao()
        .getPendingMentionCountBySpaceFlow(currentUserId, space.id)
        .collectAsState(initial = 0)
    
    // 打卡状态
    var checkInStatus by remember { mutableStateOf<CheckInStatus?>(null) }
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // 加载并定时刷新打卡状态
    LaunchedEffect(space.id, space.checkInIntervalSeconds) {
        if (space.checkInIntervalSeconds > 0) {
            while (true) {
                val status = withContext(Dispatchers.IO) {
                    val remaining = CheckInHelper.getRemainingTime(space, currentUserId, db.spaceDao())
                    val needsCheckIn = CheckInHelper.needsCheckIn(space, currentUserId, db.spaceDao())
                    CheckInStatus(
                        hasCheckIn = space.checkInIntervalSeconds > 0,
                        needsCheckIn = needsCheckIn,
                        remainingSeconds = remaining
                    )
                }
                checkInStatus = status
                currentTime = System.currentTimeMillis()
                delay(1000) // 每秒更新
            }
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 空间图标（带Badge）
                BadgedBox(
                    badge = {
                        if (pendingCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error
                            ) {
                                Text(if (pendingCount <= 99) pendingCount.toString() else "99+")
                            }
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(space.coverColor.toColorInt())),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                Spacer(Modifier.width(16.dp))
                
                // 空间信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = space.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (space.type) {
                                "couple" -> "情侣空间"
                                "family" -> "家族空间"
                                else -> "未知类型"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        if (pendingCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "· $pendingCount 条未读提醒",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (space.description != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = space.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
            
            // 打卡状态显示
            checkInStatus?.let { status ->
                if (status.hasCheckIn) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    CheckInStatusBar(status = status)
                }
            }
        }
    }
}

/**
 * 打卡状态
 */
data class CheckInStatus(
    val hasCheckIn: Boolean,      // 是否开启打卡
    val needsCheckIn: Boolean,    // 是否需要打卡（已超时）
    val remainingSeconds: Long    // 剩余秒数（-1表示未设置，0表示已超时）
)

/**
 * 打卡状态栏
 */
@Composable
fun CheckInStatusBar(status: CheckInStatus) {
    val isOverdue = status.needsCheckIn || status.remainingSeconds <= 0
    val backgroundColor = if (isOverdue) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    }
    val contentColor = if (isOverdue) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isOverdue) Icons.Default.Warning else Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (isOverdue) "需要打卡" else "打卡倒计时",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
        }
        
        Text(
            text = if (isOverdue) {
                "已超时，请发布动态"
            } else {
                formatCheckInCountdown(status.remainingSeconds)
            },
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * 格式化打卡倒计时 (hh:mm:ss)
 */
private fun formatCheckInCountdown(seconds: Long): String {
    if (seconds <= 0) return "00:00:00"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}

@Composable
fun SpaceCard(
    space: Space,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 空间图标
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(space.coverColor.toColorInt())),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            // 空间信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = space.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (space.type) {
                        "couple" -> "情侣空间"
                        "family" -> "家族空间"
                        else -> "未知类型"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (space.description != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = space.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun CreateSpaceDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String?, Int?) -> Unit,
    currentUser: User?,
    coupleCandidates: List<User>,
    partnerUser: User?,
    hasActiveCoupleSpace: Boolean,
    isLoadingCoupleCandidates: Boolean
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("family") }
    var description by remember { mutableStateOf("") }
    var selectedPartnerId by remember { mutableStateOf<Int?>(partnerUser?.uid) }

    val candidateList = remember(coupleCandidates, partnerUser, currentUser?.uid) {
        buildList {
            partnerUser?.let { partner ->
                if (partner.uid != currentUser?.uid) {
                    add(partner)
                }
            }
            coupleCandidates.forEach { candidate ->
                if (candidate.uid != currentUser?.uid && candidate.uid != partnerUser?.uid) {
                    add(candidate)
                }
            }
        }
    }

    LaunchedEffect(type, partnerUser?.uid) {
        if (type == "couple" && partnerUser != null) {
            selectedPartnerId = partnerUser.uid
        } else if (type != "couple") {
            selectedPartnerId = null
        }
    }

    LaunchedEffect(type, candidateList) {
        if (type == "couple" && candidateList.isEmpty()) {
            selectedPartnerId = null
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建空间") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("空间名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(8.dp))
                
                // 空间类型选择
                Text("空间类型:", style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = type == "couple",
                        onClick = { type = "couple" },
                        label = { Text("情侣空间") }
                    )
                    FilterChip(
                        selected = type == "family",
                        onClick = { type = "family" },
                        label = { Text("家族空间") }
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("空间描述(可选)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                if (type == "couple") {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "请选择邀请的情侣成员(仅限一人)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))

                    if (hasActiveCoupleSpace) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("您已经拥有情侣空间，无法再次创建") }
                        )
                    } else if (isLoadingCoupleCandidates) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (candidateList.isEmpty()) {
                        Text(
                            "暂无可邀请的成员，请先添加好友或建立情侣关系。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            candidateList.forEach { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable { selectedPartnerId = candidate.uid }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedPartnerId == candidate.uid,
                                        onClick = { selectedPartnerId = candidate.uid }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(candidate.nickname.ifBlank { candidate.email })
                                        if (candidate.relationshipStatus == "in_relationship" && candidate.partnerId == currentUser?.uid) {
                                            Text(
                                                "当前已与您是情侣关系",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else if (candidate.relationshipStatus == "in_relationship") {
                                            Text(
                                                "对方正在情侣关系中",
                                                style = MaterialTheme.typography.bodySmall,
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
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(
                            name, 
                            type, 
                            description.ifBlank { null },
                            if (type == "couple") selectedPartnerId else null
                        )
                    }
                },
                enabled = name.isNotBlank() && (
                    if (type == "couple") {
                        !hasActiveCoupleSpace && !isLoadingCoupleCandidates && selectedPartnerId != null
                    } else true
                )
            ) {
                Text("创建")
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
fun JoinSpaceDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit,
    errorMessage: String?,
    onInputEdited: () -> Unit
) {
    var inviteCode by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入空间") },
        text = {
            Column {
                Text(
                    "请输入6位邀请码",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { raw ->
                        val sanitized = raw.filter { it.isDigit() }.take(6)
                        if (sanitized != inviteCode) {
                            inviteCode = sanitized
                            onInputEdited()
                        }
                    },
                    label = { Text("邀请码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (inviteCode.length == 6) {
                        onJoin(inviteCode)
                    }
                },
                enabled = inviteCode.length == 6
            ) {
                Text("加入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
