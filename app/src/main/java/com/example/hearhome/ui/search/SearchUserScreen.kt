package com.example.hearhome.ui.search

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.hearhome.data.local.User
import com.example.hearhome.data.remote.ApiService
import com.example.hearhome.ui.friend.FriendViewModel
import com.example.hearhome.ui.friend.FriendViewModelFactory
import com.example.hearhome.ui.relation.CoupleViewModel
import com.example.hearhome.ui.relation.CoupleViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchUserScreen(navController: NavHostController, currentUserId: Int) {
    val context = LocalContext.current
    val friendViewModel: FriendViewModel = viewModel(factory = FriendViewModelFactory(ApiService))
    val coupleViewModel: CoupleViewModel = viewModel(factory = CoupleViewModelFactory(ApiService))
    val uiState by friendViewModel.uiState.collectAsState()
    val coupleState by coupleViewModel.uiState.collectAsState()

    var keyword by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 监听情侣请求的成功/失败消息
    LaunchedEffect(coupleState.successMessage) {
        coupleState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            coupleViewModel.clearMessages()
        }
    }
    
    LaunchedEffect(coupleState.error) {
        coupleState.error?.let {
            snackbarHostState.showSnackbar("错误: $it")
            coupleViewModel.clearMessages()
        }
    }

    // 当屏幕被销毁时，清除搜索结果
    DisposableEffect(Unit) {
        onDispose {
            friendViewModel.clearSearch()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("查找用户") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("输入用户ID进行搜索") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val id = keyword.toIntOrNull()
                    if (id != null) {
                        friendViewModel.searchUserById(id)
                    } else {
                        Toast.makeText(context, "请输入有效的用户ID", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("搜索") }

                OutlinedButton(onClick = {
                    keyword = ""
                    friendViewModel.clearSearch()
                }) { Text("清空") }
            }

            Spacer(Modifier.height(16.dp))

            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.searchCompleted && uiState.searchedUser == null -> {
                     Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("未找到该用户", color = MaterialTheme.colorScheme.error)
                    }
                }
                uiState.searchedUser != null -> {
                    if (uiState.searchedUser!!.uid == currentUserId) {
                         Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("不能添加自己为好友")
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item { 
                                UserCard(
                                    user = uiState.searchedUser!!,
                                    onAddFriend = {
                                        friendViewModel.sendFriendRequest(currentUserId, uiState.searchedUser!!.uid)
                                        Toast.makeText(context, "好友请求已发送", Toast.LENGTH_SHORT).show()
                                    },
                                    onSendCoupleRequest = {
                                        coupleViewModel.sendCoupleRequest(currentUserId, uiState.searchedUser!!.uid)
                                    }
                                )
                            }
                        }
                    }
                }
                 uiState.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("搜索出错: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(
    user: User, 
    onAddFriend: () -> Unit,
    onSendCoupleRequest: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color(user.avatarColor.toColorInt()))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        user.nickname.ifBlank { "(未设置昵称)" }, 
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "ID: ${user.uid} | 性别: ${user.gender}", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (user.relationshipStatus == "in_relationship") {
                        Text(
                            "已有情侣",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAddFriend,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("加好友")
                }
                
                Button(
                    onClick = onSendCoupleRequest,
                    modifier = Modifier.weight(1f),
                    enabled = user.relationshipStatus != "in_relationship"
                ) {
                    Text("发送情侣请求")
                }
            }
        }
    }
}