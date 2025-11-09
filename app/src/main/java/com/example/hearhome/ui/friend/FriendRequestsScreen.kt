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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.navigation.NavController
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.data.local.Friend
import com.example.hearhome.data.local.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 用于在UI层合并申请信息和申请人信息
data class RequestInfo(val request: Friend, val sender: User)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendRequestsScreen(
    navController: NavController,
    currentUserId: Int
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val friendDao = db.friendDao()
    val userDao = db.userDao()

    var requests by remember { mutableStateOf<List<RequestInfo>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun refreshRequests() {
        scope.launch {
            requests = withContext(Dispatchers.IO) {
                friendDao.getPendingRequests(currentUserId).mapNotNull { req ->
                    userDao.getUserById(req.senderId)?.let { sender ->
                        RequestInfo(req, sender)
                    }
                }
            }
        }
    }

    LaunchedEffect(currentUserId) {
        refreshRequests()
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
            if (requests.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("暂无新的好友申请", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {                    
                    items(requests, key = { it.request.id }) { info ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(Color(info.sender.avatarColor.toColorInt())))
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(info.sender.nickname.ifBlank { "(未设置昵称)" }, style = MaterialTheme.typography.titleMedium)
                                    Text("ID: ${info.sender.uid} | 性别: ${info.sender.gender}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Button(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { friendDao.acceptRequest(info.request.id) }
                                            Toast.makeText(context, "已接受好友请求", Toast.LENGTH_SHORT).show()
                                            refreshRequests()
                                        }
                                    }) { Text("同意") }

                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { friendDao.rejectRequest(info.request.id) }
                                            Toast.makeText(context, "已拒绝好友请求", Toast.LENGTH_SHORT).show()
                                            refreshRequests()
                                        }
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