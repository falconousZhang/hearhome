package com.example.hearhome.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.ui.components.AppBottomNavigation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    userId: Int
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(db.friendDao())
    )

    val friendsWithUsers by viewModel.friendsWithUsers.collectAsState()

    LaunchedEffect(userId) {
        viewModel.loadFriends(userId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("聊天") },
                actions = {
                    IconButton(onClick = { navController.navigate("search/$userId") }) {
                        Icon(Icons.Filled.Search, contentDescription = "查找用户")
                    }
                }
            )
        },
        bottomBar = {
            AppBottomNavigation(
                currentRoute = "home",
                navController = navController,
                userId = userId
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (friendsWithUsers.isEmpty()) {
                item {
                    Text("您还没有好友，快去查找用户并添加吧！", modifier = Modifier.padding(16.dp))
                }
            } else {
                items(friendsWithUsers) { friendWithUser ->
                    val friendUser = if (friendWithUser.friend.senderId == userId) {
                        friendWithUser.receiver
                    } else {
                        friendWithUser.sender
                    }

                    ListItem(
                        headlineContent = { Text(friendUser.nickname) },
                        supportingContent = { Text("在线状态") },
                        modifier = Modifier.clickable { 
                            navController.navigate("chat/$userId/${friendUser.uid}") 
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}