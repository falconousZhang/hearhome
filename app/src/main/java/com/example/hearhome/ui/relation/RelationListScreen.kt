package com.example.hearhome.ui.relation

import android.widget.Toast
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.hearhome.data.remote.ApiService
import com.example.hearhome.ui.components.AppBottomNavigation
import com.example.hearhome.ui.friend.FriendViewModel
import com.example.hearhome.ui.friend.FriendViewModelFactory
import com.example.hearhome.ui.friend.FriendWithRelation
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.FavoriteBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationListScreen(
    navController: NavController,
    currentUserId: Int
) {
    val context = LocalContext.current
    val friendViewModel: FriendViewModel = viewModel(factory = FriendViewModelFactory(ApiService))
    val coupleViewModel: CoupleViewModel = viewModel(factory = CoupleViewModelFactory(ApiService))
    val uiState by friendViewModel.uiState.collectAsState()
    val coupleState by coupleViewModel.uiState.collectAsState()
    val friends = uiState.friends
    val pendingFriendRequestCount = uiState.friendRequests.size
    var lastErrorMessage by remember { mutableStateOf<String?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }

    var friendToDelete by remember { mutableStateOf<FriendWithRelation?>(null) }
    var showBreakupDialog by remember { mutableStateOf(false) }
    var friendToSendCoupleRequest by remember { mutableStateOf<FriendWithRelation?>(null) }

    // --- Data Refresh ---
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                friendViewModel.getFriends(currentUserId)
                friendViewModel.getFriendRequests(currentUserId)
                coupleViewModel.getMyCouple(currentUserId)
                coupleViewModel.getCoupleRequests(currentUserId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 错误提示节流，避免 Toast 被频繁触发
    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMessage ->
            if (errorMessage != lastErrorMessage) {
                Toast.makeText(context, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
                lastErrorMessage = errorMessage
            }
            friendViewModel.clearMessages()
        }
    }

    // 监听情侣请求的成功/失败消息
    // 情侣请求成功提示保持即时反馈
    LaunchedEffect(coupleState.successMessage) {
        coupleState.successMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            coupleViewModel.clearMessages()
        }
    }
    LaunchedEffect(coupleState.error) {
        coupleState.error?.let { errorMessage ->
            if (errorMessage != lastErrorMessage) {
                Toast.makeText(context, "错误: $errorMessage", Toast.LENGTH_SHORT).show()
                lastErrorMessage = errorMessage
            }
            coupleViewModel.clearMessages()
        }
    }

    // --- Dialogs ---
    friendToDelete?.let { friendInfo ->
        AlertDialog(
            onDismissRequest = { friendToDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除好友 ${friendInfo.user.nickname} 吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        lastErrorMessage = null
                        friendViewModel.deleteFriend(friendInfo.relation.id, currentUserId)
                        friendToDelete = null
                        Toast.makeText(context, "好友已删除", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { friendToDelete = null }) { Text("取消") } }
        )
    }
    
    // 解除情侣关系对话框
    if (showBreakupDialog) {
        AlertDialog(
            onDismissRequest = { showBreakupDialog = false },
            title = { Text("确认解除情侣关系") },
            text = { Text("确定要解除情侣关系吗？此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        lastErrorMessage = null
                        coupleViewModel.breakupCouple(currentUserId)
                        showBreakupDialog = false
                        Toast.makeText(context, "已解除情侣关系", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("解除") }
            },
            dismissButton = {
                TextButton(onClick = { showBreakupDialog = false }) { Text("取消") }
            }
        )
    }

    // 发送情侣请求对话框
    friendToSendCoupleRequest?.let { friendInfo ->
        AlertDialog(
            onDismissRequest = { friendToSendCoupleRequest = null },
            title = { Text("发送情侣请求") },
            text = { Text("确定要向 ${friendInfo.user.nickname.ifBlank { "用户${friendInfo.user.uid}" }} 发送情侣请求吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        lastErrorMessage = null
                        coupleViewModel.sendCoupleRequest(currentUserId, friendInfo.user.uid)
                        friendToSendCoupleRequest = null
                    }
                ) { Text("发送") }
            },
            dismissButton = {
                TextButton(onClick = { friendToSendCoupleRequest = null }) { Text("取消") }
            }
        )
    }

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
                            Icon(Icons.Default.PersonAdd, "��������")
                        }
                    }
                    IconButton(onClick = { navController.navigate("coupleRequests/${currentUserId}") }) {
                        BadgedBox(
                            badge = { 
                                if (coupleState.requestCount > 0) {
                                    Badge { Text(coupleState.requestCount.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Favorite, "情侣申请")
                        }
                    }
                }
            )
        },
        bottomBar = { AppBottomNavigation("relation", navController, currentUserId) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            // --- Couple Section ---
            Text("我的情侣：", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            
            if (coupleState.myCouple != null) {
                val partner = coupleState.myCouple!!.partner
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(Color(partner.avatarColor.toColorInt()))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = partner.nickname.ifBlank { "(未设置昵称)" },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "ID: ${partner.uid}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { showBreakupDialog = true }) {
                            Icon(
                                Icons.Default.HeartBroken, 
                                contentDescription = "解除关系",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            } else {
                Text(
                    "暂无情侣", 
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // --- Friends Section ---
            Text("我的好友：", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (friends.isEmpty()) {
                Text("暂无好友", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = friends, key = { it.relation.id }) { friendInfo ->
                        var showMenu by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(friendInfo.user.avatarColor.toColorInt()))
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = friendInfo.user.nickname.ifBlank { "(未设置昵称)" },
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
                                    // 只有当前用户没有情侣时，才显示发送情侣请求选项
                                    if (coupleState.myCouple == null) {
                                        DropdownMenuItem(
                                            text = { Text("发送情侣请求") },
                                            onClick = {
                                                friendToSendCoupleRequest = friendInfo
                                                showMenu = false
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.FavoriteBorder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("修改备注 (暂未实现)") },
                                        onClick = { showMenu = false },
                                        enabled = false
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除好友", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            friendToDelete = friendInfo
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
