package com.example.hearhome.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.example.hearhome.data.local.User
import com.example.hearhome.data.local.Friend
import com.example.hearhome.data.remote.ApiService
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.foundation.clickable

/**
 * 空间管理界面
 * 管理员审核加入申请、管理成员
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceManageScreen(
    navController: NavController,
    spaceId: Int,
    currentUserId: Int
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)

    // 使用 key 确保每个空间使用独立的 ViewModel 实例
    val viewModel: SpaceViewModel = viewModel(
        key = "space_manage_$spaceId",
        factory = SpaceViewModelFactory(
            db.spaceDao(),
            db.userDao(),
            db.coupleDao(),
            currentUserId
        )
    )

    val currentSpace by viewModel.currentSpace.collectAsState()
    val spaceMembers by viewModel.spaceMembers.collectAsState()
    val pendingMembers by viewModel.pendingMembers.collectAsState()
    val currentUserRole by viewModel.currentUserRole.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDissolveDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }

    // 情侣空间双方都是owner，都可以管理和解散
    val isCoupleSpace = currentSpace?.type == "couple"
    val isAdmin = currentUserRole == "admin" || currentUserRole == "owner"
    val canDissolve = currentUserRole == "owner"  // 情侣空间和其他空间的owner都可以解散

    LaunchedEffect(spaceId) {
        println("[DEBUG SpaceManageScreen] LaunchedEffect: selecting spaceId=$spaceId")
        viewModel.selectSpace(spaceId)
    }

    // 加载中状态
    if (currentUserRole == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // 权限检查（管理员和所有者可访问）
    if (!isAdmin) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("空间管理") },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, "返回")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "无权限访问",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "只有管理员可以管理空间",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "当前角色: ${currentUserRole ?: "加载中..."}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("空间管理") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 情侣空间不显示移除成员功能
            val canRemoveMembers = !isCoupleSpace


            // 空间信息
            item {
                currentSpace?.let { space ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "空间信息",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("名称: ${space.name}")
                            Text("类型: ${if (space.type == "couple") "情侣空间" else "家族空间"}")
                            Text("邀请码: ${space.inviteCode}")
                            Text("成员数: ${spaceMembers.size}")
                            Text("成员数: ${spaceMembers.size}")

                            Spacer(Modifier.height(8.dp))

                            if (canRemoveMembers) { // 这里你已经有 canRemoveMembers = !isCoupleSpace
                                Button(
                                    onClick = { showInviteDialog = true },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("邀请好友加入空间")
                                }
                            }
//wdz------------
                        }
                    }
                }
            }

            // 打卡设置（仅管理员可见）
            item {
                currentSpace?.let { space ->
                    CheckInSettingsCard(
                        space = space,
                        onUpdateInterval = { intervalSeconds ->
                            scope.launch {
                                try {
                                    db.spaceDao().updateCheckInInterval(spaceId, intervalSeconds)
                                    snackbarHostState.showSnackbar("打卡设置已更新")
                                    viewModel.selectSpace(spaceId) // 刷新空间信息
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("更新失败：${e.message}")
                                }
                            }
                        }
                    )
                }
            }


            // 待审核成员
            if (pendingMembers.isNotEmpty()) {
                println("[DEBUG SpaceManageScreen] Showing ${pendingMembers.size} pending members")
                item {
                    Text(
                        "待审核 (${pendingMembers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(pendingMembers) { memberInfo ->
                    MemberRequestCard(
                        memberInfo = memberInfo,
                        onApprove = {
                            println("[DEBUG SpaceManageScreen] Approve button clicked for member ${memberInfo.member.id}")
                            scope.launch {
                                viewModel.approveMember(memberInfo.member.id)
                            }
                        },
                        onReject = {
                            println("[DEBUG SpaceManageScreen] Reject button clicked for member ${memberInfo.member.id}")
                            scope.launch {
                                viewModel.rejectMember(memberInfo.member.id)
                            }
                        }
                    )
                }

                item { HorizontalDivider() }
            } else {
                println("[DEBUG SpaceManageScreen] No pending members to display")
            }

            // 现有成员
            item {
                Text(
                    "成员列表 (${spaceMembers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(spaceMembers) { memberInfo ->
                MemberCard(
                    memberInfo = memberInfo,
                    currentUserId = currentUserId,
                    canRemove = canRemoveMembers,
                    onRemove = {
                        scope.launch {
                            viewModel.removeMember(memberInfo.member.id)
                        }
                    }
                )
            }

            // 危险操作区域 - 只有owner可以解散
            if (canDissolve) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "危险操作",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = if (isCoupleSpace) {
                                    "解散情侣空间将同时解除情侣关系。该操作无法撤销。"
                                } else {
                                    "解散空间后，成员将无法访问空间内容。该操作无法撤销。"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(
                                onClick = { showDissolveDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("解散空间", color = MaterialTheme.colorScheme.onError)
                            }
                        }
                    }
                }
            }

        }
        if (showInviteDialog) {
            InviteFriendDialog(
                onDismiss = { showInviteDialog = false },
                spaceId = spaceId,
                currentUserId = currentUserId,
                onInvite = { friendUserIds ->
                    scope.launch {
                        viewModel.inviteFriendsToSpace(spaceId, friendUserIds)
                        val message = if (friendUserIds.size == 1) {
                            "已发送邀请/加入请求"
                        } else {
                            "已向 ${friendUserIds.size} 位好友发送邀请"
                        }
                        snackbarHostState.showSnackbar(message)
                        showInviteDialog = false
                    }
                }
            )
        }


    }

    // 解散空间确认对话框
    if (showDissolveDialog) {
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
                        showDissolveDialog = false
                        scope.launch {
                            val result = viewModel.dissolveSpace(spaceId)
                            when (result) {
                                is DissolveSpaceResult.Success -> {
                                    // 解散成功，直接返回到主页
                                    // 使用 popBackStack 清除所有空间相关页面，返回到 home/{userId}
                                    navController.popBackStack(
                                        route = "home/$currentUserId",
                                        inclusive = false
                                    )
                                }
                                is DissolveSpaceResult.Failure -> {
                                    snackbarHostState.showSnackbar(result.message)
                                }
                            }
                        }
                    }
                ) {
                    Text("确认", color = MaterialTheme.colorScheme.error)
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
fun MemberRequestCard(
    memberInfo: SpaceMemberInfo,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(memberInfo.user.avatarColor.toColorInt()))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = memberInfo.user.nickname,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "申请加入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Row {
                IconButton(
                    onClick = onApprove,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.Check,
                        "通过",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onReject,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Close,
                        "拒绝",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun MemberCard(
    memberInfo: SpaceMemberInfo,
    currentUserId: Int,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    val isCurrentUser = memberInfo.user.uid == currentUserId
    val isOwner = memberInfo.member.role == "owner"
    val isAdminOrOwner = memberInfo.member.role == "admin" || isOwner
    val roleText = when (memberInfo.member.role) {
        "owner" -> if (canRemove) "所有者" else "共同所有者"  // 情侣空间显示"共同所有者"
        "admin" -> "管理员"
        else -> "成员"
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(memberInfo.user.avatarColor.toColorInt()))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = memberInfo.user.nickname,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (isCurrentUser) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "(我)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = roleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAdminOrOwner) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
                }
            }

            // 只有非管理员、非所有者且非当前用户才显示移除按钮
            if (canRemove && !isAdminOrOwner && !isCurrentUser) {
                TextButton(onClick = onRemove) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * 打卡设置卡片
 * 允许管理员设置成员动态发布的最大间隔时间
 */
@Composable
fun CheckInSettingsCard(
    space: com.example.hearhome.data.local.Space,
    onUpdateInterval: (Long) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var hours by remember { mutableStateOf("0") }
    var minutes by remember { mutableStateOf("0") }
    var seconds by remember { mutableStateOf("0") }

    // 使用本地状态来跟踪当前间隔，确保UI立即更新
    var localIntervalSeconds by remember(space.id) { mutableStateOf(space.checkInIntervalSeconds) }

    // 当 space 变化时同步本地状态
    LaunchedEffect(space.checkInIntervalSeconds) {
        localIntervalSeconds = space.checkInIntervalSeconds
    }

    // 将当前间隔转换为时分秒（使用本地状态）
    val currentHours = localIntervalSeconds / 3600
    val currentMinutes = (localIntervalSeconds % 3600) / 60
    val currentSeconds = localIntervalSeconds % 60

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "打卡设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            if (localIntervalSeconds > 0) {
                Text(
                    "当前打卡间隔：${currentHours}小时 ${currentMinutes}分钟 ${currentSeconds}秒",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "成员需要在此时间内发布新动态，否则将收到提醒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "未设置打卡间隔",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "设置后，成员将定期收到发布动态的提醒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (localIntervalSeconds > 0) "修改设置" else "设置打卡")
                }

                if (localIntervalSeconds > 0) {
                    OutlinedButton(
                        onClick = {
                            localIntervalSeconds = 0  // 立即更新本地状态
                            onUpdateInterval(0)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("关闭打卡")
                    }
                }
            }
        }
    }

    // 设置对话框
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("设置打卡间隔") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "设置成员发布动态的最大间隔时间",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = hours,
                            onValueChange = {
                                if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                    hours = it
                                }
                            },
                            label = { Text("小时") },
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
                            label = { Text("分钟") },
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
                            label = { Text("秒") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Text(
                        "示例：设置为 24:00:00 表示每天需要发布一次动态",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val h = hours.toLongOrNull() ?: 0
                        val m = minutes.toLongOrNull() ?: 0
                        val s = seconds.toLongOrNull() ?: 0
                        val totalSeconds = h * 3600 + m * 60 + s

                        if (totalSeconds > 0) {
                            localIntervalSeconds = totalSeconds  // 立即更新本地状态
                            onUpdateInterval(totalSeconds)
                            showDialog = false
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
@Composable
fun InviteFriendDialog(
    onDismiss: () -> Unit,
    spaceId: Int,
    currentUserId: Int,
    onInvite: (List<Int>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var friendList by remember { mutableStateOf<List<User>>(emptyList()) }
    var selectedFriendIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 打开对话框时加载好友列表
    LaunchedEffect(Unit) {
        try {
            val friendsResponse = ApiService.getFriends(currentUserId)
            if (friendsResponse.status == HttpStatusCode.OK) {
                val friendRelations = friendsResponse.body<List<Friend>>()
                // 从关系表中拿到对方的 userId，然后查资料
                val users = friendRelations.map { relation ->
                    val friendId =
                        if (relation.senderId == currentUserId) relation.receiverId
                        else relation.senderId

                    async {
                        try {
                            val profileResponse = ApiService.getProfile(friendId)
                            if (profileResponse.status == HttpStatusCode.OK) {
                                profileResponse.body<User>()
                            } else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()

                friendList = users
            } else {
                errorMessage = "加载好友列表失败"
            }
        } catch (e: Exception) {
            errorMessage = "加载好友列表失败: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("邀请好友加入空间") },
        text = {
            when {
                isLoading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                errorMessage != null -> {
                    Text(errorMessage!!)
                }

                friendList.isEmpty() -> {
                    Text("暂无可邀请的好友，请先添加好友。")
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("请选择要邀请的好友：")
                            // 全选/取消全选按钮
                            TextButton(
                                onClick = {
                                    selectedFriendIds = if (selectedFriendIds.size == friendList.size) {
                                        emptySet()
                                    } else {
                                        friendList.map { it.uid }.toSet()
                                    }
                                }
                            ) {
                                Text(
                                    if (selectedFriendIds.size == friendList.size) "取消全选" else "全选",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Text(
                            "已选择 ${selectedFriendIds.size} 人",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        friendList.forEach { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedFriendIds = if (user.uid in selectedFriendIds) {
                                            selectedFriendIds - user.uid
                                        } else {
                                            selectedFriendIds + user.uid
                                        }
                                    }
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = user.uid in selectedFriendIds,
                                    onCheckedChange = { checked ->
                                        selectedFriendIds = if (checked) {
                                            selectedFriendIds + user.uid
                                        } else {
                                            selectedFriendIds - user.uid
                                        }
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(user.nickname.ifBlank { user.email })
                                    Text(
                                        "ID: ${user.uid}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedFriendIds.isNotEmpty()) {
                        onInvite(selectedFriendIds.toList())
                    }
                },
                enabled = !isLoading && selectedFriendIds.isNotEmpty()
            ) {
                Text("邀请 (${selectedFriendIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

