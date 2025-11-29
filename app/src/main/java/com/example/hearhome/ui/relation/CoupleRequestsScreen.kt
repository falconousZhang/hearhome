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
import com.example.hearhome.data.remote.ApiService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleRequestsScreen(
    navController: NavController,
    currentUserId: Int
) {
    val context = LocalContext.current
    val viewModel: CoupleRequestsViewModel = viewModel(
        factory = CoupleRequestsViewModelFactory(ApiService, currentUserId)
    )
    val uiState by viewModel.uiState.collectAsState()

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
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(
                    text = "加载失败: ${uiState.error}",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error
                )
            } else if (uiState.requests.isEmpty()) {
                Text(
                    text = "暂无新的情侣申请",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.requests, key = { it.requestId }) { info ->
                        CoupleRequestCard(info = info, viewModel = viewModel) { result ->
                            result.onSuccess {
                                Toast.makeText(context, "操作成功", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "操作失败: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoupleRequestCard(
    info: CoupleRequestInfo,
    viewModel: CoupleRequestsViewModel, // Pass the ViewModel down
    onAction: (Result<Unit>) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(info.requester.avatarColor.toColorInt()))
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(info.requester.nickname.ifBlank { "(未设置昵称)" }, style = MaterialTheme.typography.titleMedium)
                Text("ID: ${info.requester.uid} | 性别: ${info.requester.gender}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Button(onClick = { viewModel.acceptRequest(info, onAction) }) {
                    Text("同意")
                }
                OutlinedButton(onClick = { viewModel.rejectRequest(info.requestId, onAction) }) {
                    Text("拒绝")
                }
            }
        }
    }
}
