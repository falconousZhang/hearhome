package com.example.hearhome.ui.friend

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.hearhome.data.remote.ApiService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendRequestsScreen(
    navController: NavController,
    currentUserId: Int
) {
    val context = LocalContext.current
    val friendViewModel: FriendViewModel = viewModel(factory = FriendViewModelFactory(ApiService))
    val uiState by friendViewModel.uiState.collectAsState()
    var lastErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUserId) {
        friendViewModel.getFriendRequests(currentUserId)
    }
    
    // 错误提示节流，避免按钮短时间内 Toast 频繁弹出
    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMessage ->
            if (errorMessage != lastErrorMessage) {
                Toast.makeText(context, "错误: $errorMessage", Toast.LENGTH_LONG).show()
                lastErrorMessage = errorMessage
            }
            friendViewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("好友申请") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            if (uiState.isLoading) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator()
                }
            } else if (uiState.friendRequests.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("暂无新的好友申请", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {                    
                    items(uiState.friendRequests, key = { it.request.id }) { requestWithSender ->
                        val sender = requestWithSender.sender
                        val request = requestWithSender.request
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(Color(sender.avatarColor.toColorInt())))
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(sender.nickname.ifBlank { "(未设置昵称)" }, style = MaterialTheme.typography.titleMedium)
                                    Text("ID: ${sender.uid} | 性别: ${sender.gender}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Button(onClick = {
                                        lastErrorMessage = null
                                        friendViewModel.acceptFriendRequest(request.id, currentUserId)
                                        Toast.makeText(context, "已接受好友请求", Toast.LENGTH_SHORT).show()
                                    }) { Text("同意") }

                                    OutlinedButton(onClick = {
                                        lastErrorMessage = null
                                        friendViewModel.rejectFriendRequest(request.id, currentUserId)
                                        Toast.makeText(context, "已拒绝好友请求", Toast.LENGTH_SHORT).show()
                                    }) { Text("拒绝") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
