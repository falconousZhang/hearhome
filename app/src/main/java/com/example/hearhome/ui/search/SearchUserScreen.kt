package com.example.hearhome.ui.search

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
import androidx.navigation.NavHostController
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.data.local.Friend
import com.example.hearhome.data.local.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchUserScreen(navController: NavHostController, currentUserId: Int) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val userDao = remember { db.userDao() }
    val friendDao = remember { db.friendDao() }

    var keyword by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<User>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                label = { Text("输入用户ID或昵称搜索") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    scope.launch {
                        val users = withContext(Dispatchers.IO) { userDao.searchUsers(keyword) }
                        results = users.filter { it.uid != currentUserId }
                        searched = true
                    }
                }) { Text("搜索") }

                OutlinedButton(onClick = {
                    keyword = ""
                    results = emptyList()
                    searched = false
                }) { Text("清空") }
            }

            Spacer(Modifier.height(16.dp))

            if (searched && results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("未找到该用户", color = MaterialTheme.colorScheme.error)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(results) { user ->
                        UserCard(
                            user = user,
                            onAddFriend = {
                                scope.launch {
                                    val exists = withContext(Dispatchers.IO) {
                                        friendDao.getRelationship(currentUserId, user.uid)
                                    }
                                    if (exists == null) {
                                        withContext(Dispatchers.IO) {
                                            friendDao.insertRequest(
                                                Friend(senderId = currentUserId, receiverId = user.uid)
                                            )
                                        }
                                        snackbarHostState.showSnackbar("好友请求已发送")
                                    } else snackbarHostState.showSnackbar("已是好友或请求中")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: User, onAddFriend: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(user.avatarColor.toColorInt()))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(user.nickname.ifBlank { "(未设置昵称)" }, style = MaterialTheme.typography.titleMedium)
                Text("ID: ${user.uid} | 性别: ${user.gender}", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = onAddFriend) {
                Text("加好友")
            }
        }
    }
}