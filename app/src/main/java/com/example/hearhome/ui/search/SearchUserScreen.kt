package com.example.hearhome.ui.search

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import kotlinx.coroutines.delay
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
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var feedbackIsError by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let {
            feedbackMessage = it
            feedbackIsError = false
            friendViewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.actionError) {
        uiState.actionError?.let {
            feedbackMessage = it
            feedbackIsError = true
            friendViewModel.clearMessages()
        }
    }

    LaunchedEffect(coupleState.successMessage) {
        coupleState.successMessage?.let {
            feedbackMessage = it
            feedbackIsError = false
            coupleViewModel.clearMessages()
        }
    }

    LaunchedEffect(coupleState.error) {
        coupleState.error?.let {
            feedbackMessage = it
            feedbackIsError = true
            coupleViewModel.clearMessages()
        }
    }
    LaunchedEffect(feedbackMessage) {
        val message = feedbackMessage ?: return@LaunchedEffect
        delay(3500)
        if (feedbackMessage == message) {
            feedbackMessage = null
        }
    }

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
        }
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

            feedbackMessage?.let { message ->
                val containerColor = if (feedbackIsError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                val contentColor = if (feedbackIsError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer

                Surface(
                    color = containerColor,
                    contentColor = contentColor,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { feedbackMessage = null }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭提示")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val targetUser = uiState.searchedUser?.takeIf { it.uid != currentUserId }
                val searchMessage = when {
                    uiState.searchCompleted && uiState.searchedUser == null -> "未找到该用户"
                    uiState.searchedUser?.uid == currentUserId -> "不能添加自己为好友"
                    else -> null
                }

                uiState.error?.let { errorMessage ->
                    Text(
                        text = "搜索出错: $errorMessage",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                } ?: run {
                    if (searchMessage != null) {
                        Text(
                            text = searchMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        )
                    }

                    if (targetUser != null) {
                        Spacer(Modifier.height(16.dp))
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                UserCard(
                                    user = targetUser,
                                    onAddFriend = {
                                        friendViewModel.sendFriendRequest(currentUserId, targetUser.uid)
                                    },
                                    onSendCoupleRequest = {
                                        coupleViewModel.sendCoupleRequest(currentUserId, targetUser.uid)
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
