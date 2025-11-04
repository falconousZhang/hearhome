package com.example.hearhome.ui.relation

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
import com.example.hearhome.data.local.Couple
import com.example.hearhome.data.local.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CoupleRequestInfo(val request: Couple, val requester: User)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupleRequestsScreen(
    navController: NavController,
    currentUserId: Int
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val coupleDao = db.coupleDao()
    val userDao = db.userDao()

    var requests by remember { mutableStateOf<List<CoupleRequestInfo>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun refreshRequests() {
        scope.launch {
            requests = withContext(Dispatchers.IO) {
                coupleDao.getPendingRequests(currentUserId).mapNotNull { req ->
                    userDao.getUserById(req.requesterId)?.let { requester ->
                        CoupleRequestInfo(req, requester)
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
            if (requests.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("暂无新的情侣申请", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(requests, key = { it.request.id }) { info ->
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
                                    Button(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                coupleDao.acceptRequest(info.request.id)
                                                // 更新双方状态
                                                val requesterId = info.requester.uid
                                                userDao.updateRelationshipStatus(currentUserId, "in_relationship", requesterId)
                                                userDao.updateRelationshipStatus(requesterId, "in_relationship", currentUserId)
                                            }
                                            Toast.makeText(context, "已接受情侣申请", Toast.LENGTH_SHORT).show()
                                            refreshRequests()
                                        }
                                    }) { Text("同意") }

                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { coupleDao.rejectRequest(info.request.id) }
                                            Toast.makeText(context, "已拒绝情侣申请", Toast.LENGTH_SHORT).show()
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