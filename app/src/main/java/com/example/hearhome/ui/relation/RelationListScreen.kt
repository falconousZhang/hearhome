package com.example.hearhome.ui.relation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.data.local.Couple
import com.example.hearhome.data.local.Friend
import com.example.hearhome.data.local.User
import com.example.hearhome.ui.components.AppBottomNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 用于在UI层合并信息和实体
data class FriendInfo(val user: User, val friendEntity: Friend)
data class CoupleInfo(val user: User, val coupleEntity: Couple)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationListScreen(
    navController: NavController,
    currentUserId: Int
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val userDao = db.userDao()
    val friendDao = db.friendDao()
    val coupleDao = db.coupleDao()

    val currentUser by userDao.findById(currentUserId).collectAsState(initial = null)

    var friends by remember { mutableStateOf<List<FriendInfo>>(emptyList()) }
    var coupleInfo by remember { mutableStateOf<CoupleInfo?>(null) }
    var pendingFriendRequestCount by remember { mutableIntStateOf(0) } // 好友申请数
    var pendingCoupleRequestCount by remember { mutableIntStateOf(0) } // 情侣申请数
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- 对话框状态管理 ---
    var friendToDelete by remember { mutableStateOf<FriendInfo?>(null) }
    var friendToRemark by remember { mutableStateOf<FriendInfo?>(null) }
    var friendToShowDetails by remember { mutableStateOf<FriendInfo?>(null) }
    var showPartnerSelectionDialog by remember { mutableStateOf(false) }
    var showDeleteCoupleDialog by remember { mutableStateOf(false) }

    // -- 数据刷新与辅助函数 --
    fun refreshData() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val friendEntities = friendDao.getFriendsOf(currentUserId)
                friends = friendEntities.mapNotNull { friend ->
                    val friendId = if (friend.senderId == currentUserId) friend.receiverId else friend.senderId
                    userDao.getUserById(friendId)?.let { user -> FriendInfo(user, friend) }
                }
                val coupleEntity = coupleDao.getCurrentCouple(currentUserId)
                coupleInfo = coupleEntity?.let {
                    val partnerId = if (it.requesterId == currentUserId) it.partnerId else it.requesterId
                    userDao.getUserById(partnerId)?.let { user -> CoupleInfo(user, it) }
                }
                pendingFriendRequestCount = friendDao.getPendingRequestsCount(currentUserId)
                pendingCoupleRequestCount = coupleDao.getPendingRequests(currentUserId).size // 获取情侣申请数
            }
        }
    }

    fun getMyRemarkForFriend(info: FriendInfo) = if (info.friendEntity.senderId == currentUserId) info.friendEntity.senderRemark else info.friendEntity.receiverRemark
    fun getDisplayName(info: FriendInfo) = getMyRemarkForFriend(info) ?: info.user.nickname.ifBlank { "(未设置昵称)" }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val potentialPartners = remember(friends, currentUser) {
        val userGender = currentUser?.gender
        if (userGender == "男" || userGender == "女") {
            val oppositeGender = if (userGender == "男") "女" else "男"
            friends.filter { it.user.gender == oppositeGender }
        } else {
            emptyList()
        }
    }

    // --- 对话框定义 ---
    // 解除情侣关系对话框
    if (showDeleteCoupleDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCoupleDialog = false },
            title = { Text("确认解除关系") },
            text = { Text("确定要解除情侣关系吗？此操作不可逆。") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                coupleDao.deleteRelationship(currentUserId)
                            }
                            showDeleteCoupleDialog = false
                            snackbarHostState.showSnackbar("已解除情侣关系")
                            refreshData()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("确认解除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteCoupleDialog = false }) { Text("取消") } }
        )
    }


    // 删除好友对话框
    friendToDelete?.let { info ->
        AlertDialog(
            onDismissRequest = { friendToDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除好友 ${getDisplayName(info)} 吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                friendDao.deleteFriend(currentUserId, info.user.uid)
                            }
                            friendToDelete = null
                            snackbarHostState.showSnackbar("好友已删除")
                            refreshData()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { friendToDelete = null }) { Text("取消") } }
        )
    }

    // 修改备注对话框
    friendToRemark?.let { info ->
        var newRemark by remember(info) { mutableStateOf(getMyRemarkForFriend(info) ?: "") }
        AlertDialog(
            onDismissRequest = { friendToRemark = null },
            title = { Text("修改备注") },
            text = {
                OutlinedTextField(
                    value = newRemark,
                    onValueChange = { newRemark = it },
                    label = { Text("为 ${info.user.nickname} 设置备注") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val updatedFriendEntity = if (info.friendEntity.senderId == currentUserId) {
                            info.friendEntity.copy(senderRemark = newRemark.ifBlank { null })
                        } else {
                            info.friendEntity.copy(receiverRemark = newRemark.ifBlank { null })
                        }

                        withContext(Dispatchers.IO) {
                            friendDao.updateFriend(updatedFriendEntity)
                        }
                        friendToRemark = null
                        snackbarHostState.showSnackbar("备注已更新")
                        refreshData()
                    }
                }) { Text( "保存") }
            },
            dismissButton = { TextButton(onClick = { friendToRemark = null }) { Text("取消") } }
        )
    }


    // 好友详情对话框
    friendToShowDetails?.let { info ->
        AlertDialog(
            onDismissRequest = { friendToShowDetails = null },
            title = { Text("好友详情") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(info.user.avatarColor.toColorInt()))
                            .align(Alignment.CenterHorizontally)
                    )
                    getMyRemarkForFriend(info)?.let {
                        Text("备注: $it", style = MaterialTheme.typography.bodyLarge)
                    }
                    Text("昵称: ${info.user.nickname.ifBlank { "(未设置)" }}", style = MaterialTheme.typography.bodyLarge)
                    Text("性别: ${info.user.gender}", style = MaterialTheme.typography.bodyLarge)
                    Text("ID: ${info.user.uid}", style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = {
                Button(onClick = { friendToShowDetails = null }) {
                    Text("关闭")
                }
            }
        )
    }

    // 选择情侣对象对话框
    if (showPartnerSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showPartnerSelectionDialog = false },
            title = { Text("选择一个对象") },
            text = {
                if (potentialPartners.isEmpty()) {
                    Text("没有可供选择的异性好友。")
                } else {
                    LazyColumn {
                        items(items = potentialPartners, key = { it.user.uid }) { info ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            val newCoupleRequest = Couple(
                                                requesterId = currentUserId,
                                                partnerId = info.user.uid,
                                                status = "pending"
                                            )
                                            withContext(Dispatchers.IO) {
                                                coupleDao.insertRequest(newCoupleRequest)
                                            }
                                            showPartnerSelectionDialog = false
                                            snackbarHostState.showSnackbar("已向 ${getDisplayName(info)} 发送情侣申请。")
                                            refreshData()
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(info.user.avatarColor.toColorInt()))
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "${getDisplayName(info)} (${info.user.email})",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPartnerSelectionDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // --- UI 界面 ---
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("好友 / 情侣列表") },
                actions = {
                    IconButton(onClick = { navController.navigate("friendRequests/${currentUserId}") }) {
                        BadgedBox(
                            badge = {
                                if (pendingFriendRequestCount > 0) {
                                    Badge { Text(pendingFriendRequestCount.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "好友申请"
                            )
                        }
                    }
                    IconButton(onClick = { navController.navigate("coupleRequests/${currentUserId}") }) {
                        BadgedBox(
                            badge = {
                                if (pendingCoupleRequestCount > 0) {
                                    Badge { Text(pendingCoupleRequestCount.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "情侣申请"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = { AppBottomNavigation(currentRoute = "relation", navController = navController, userId = currentUserId) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Text("我的情侣：", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val currentCoupleInfo = coupleInfo
            if (currentCoupleInfo != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(currentCoupleInfo.user.avatarColor.toColorInt()))
                        )
                        Spacer(Modifier.width(12.dp))
                        val friendEntry = friends.find { it.user.uid == currentCoupleInfo.user.uid }
                        val displayName = friendEntry?.let { getMyRemarkForFriend(it) } ?: currentCoupleInfo.user.nickname.ifBlank { "(未设置昵称)" }
                        Text(displayName)
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多操作",
                            modifier = Modifier.clickable { showMenu = true }
                        )
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("解除关系", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showDeleteCoupleDialog = true
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("暂无情侣", style = MaterialTheme.typography.bodyLarge)
                    Button(
                        onClick = { showPartnerSelectionDialog = true },
                        enabled = true
                    ) {
                        Text("申请绑定")
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("我的好友：", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (friends.isEmpty()) {
                Text("暂无好友", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = friends, key = { it.friendEntity.id }) { info ->
                        var showMenu by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { friendToShowDetails = info }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(info.user.avatarColor.toColorInt()))
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = getDisplayName(info),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Box {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "更多操作",
                                    modifier = Modifier.clickable { showMenu = true }
                                )
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("修改备注") },
                                        onClick = {
                                            friendToRemark = info
                                            showMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除好友", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            friendToDelete = info
                                            showMenu = false
                                        }
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
