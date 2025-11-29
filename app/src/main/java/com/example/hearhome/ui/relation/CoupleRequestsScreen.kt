package com.example.hearhome.ui.relation

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
import androidx.navigation.NavController
import com.example.hearhome.data.local.User
import com.example.hearhome.data.remote.ApiService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleRequestsScreen(
    navController: NavController,
    currentUserId: Int
) {
    val context = LocalContext.current
    // 保留您在 yangtze 分支引入的 ViewModel
    val viewModel: CoupleRequestsViewModel = viewModel(
        factory = CoupleRequestsViewModelFactory(ApiService, currentUserId)
    )
    val uiState by viewModel.uiState.collectAsState()

    // 采纳“上线测试”分支的方案，使用 LaunchedEffect 处理 Toast 提示，代码更简洁
    // 注意：您可能需要在 CoupleRequestsViewModel 和其 UiState 中添加 successMessage/error 状态，以及一个 clearMessages 方法
    uiState.successMessage?.let {
        LaunchedEffect(it) {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    uiState.error?.let {
        LaunchedEffect(it) {
            Toast.makeText(context, "错误: $it", Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("情侣申请") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.requests.isEmpty() -> {
                    Text(
                        text = "暂无新的情侣申请",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 假设您的 uiState.requests 中的对象包含 requestId 和 requester
                        items(uiState.requests, key = { it.requestId }) { info ->
                            // 使用“上线测试”分支中更纯粹的无状态 Card 组件
                            CoupleRequestCard(
                                requester = info.requester,
                                onAccept = {
                                    // 在此处调用 ViewModel 的方法
                                    viewModel.acceptRequest(info.requestId)
                                },
                                onReject = {
                                    viewModel.rejectRequest(info.requestId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoupleRequestCard(
    requester: User,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(requester.avatarColor.toColorInt()))
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    requester.nickname.ifBlank { "(未设置昵称)" },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "ID: ${requester.uid} | 性别: ${requester.gender}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.width(80.dp)
                ) {
                    Text("同意")
                }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.width(80.dp)
                ) {
                    Text("拒绝")
                }
            }
        }
    }
}
