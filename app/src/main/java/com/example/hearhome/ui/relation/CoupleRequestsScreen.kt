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
    val coupleViewModel: CoupleViewModel = viewModel(factory = CoupleViewModelFactory(ApiService))
    val uiState by coupleViewModel.uiState.collectAsState()
    
    // 加载待处理请求
    LaunchedEffect(currentUserId) {
        coupleViewModel.getCoupleRequests(currentUserId)
    }
    
    // 监听成功消息
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            coupleViewModel.clearMessages()
        }
    }
    
    // 监听错误消息
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, "错误: $it", Toast.LENGTH_SHORT).show()
            coupleViewModel.clearMessages()
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
        Column(
            Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        contentAlignment = Alignment.Center, 
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.coupleRequests.isEmpty() -> {
                    Box(
                        contentAlignment = Alignment.Center, 
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            "暂无新的情侣申请", 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(
                            items = uiState.coupleRequests, 
                            key = { it.request.id }
                        ) { requestInfo ->
                            CoupleRequestCard(
                                requester = requestInfo.requester,
                                onAccept = {
                                    coupleViewModel.acceptCoupleRequest(
                                        requestInfo.request.id,
                                        currentUserId
                                    )
                                },
                                onReject = {
                                    coupleViewModel.rejectCoupleRequest(
                                        requestInfo.request.id,
                                        currentUserId
                                    )
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
    requester: com.example.hearhome.data.local.User,
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
