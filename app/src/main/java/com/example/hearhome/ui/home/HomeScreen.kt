package com.example.hearhome.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.hearhome.data.remote.ApiService
import com.example.hearhome.ui.components.AppBottomNavigation
import com.example.hearhome.ui.components.CheckInReminderDialog
import com.example.hearhome.ui.components.MentionTimeoutDialog
import com.example.hearhome.ui.friend.FriendViewModel
import com.example.hearhome.ui.friend.FriendViewModelFactory
import com.example.hearhome.utils.MentionReminderChecker
import com.example.hearhome.utils.MentionWithPost
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    userId: Int
) {
    val context = LocalContext.current
    val friendViewModel: FriendViewModel = viewModel(factory = FriendViewModelFactory(ApiService))
    val uiState by friendViewModel.uiState.collectAsState()
    val friends = uiState.friends

    val scope = rememberCoroutineScope()
    
    // 控制提醒弹窗的显示
    var showMentionDialog by remember { mutableStateOf(true) }
    var showCheckInDialog by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        friendViewModel.getFriends(userId)
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
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (friends.isEmpty()) {
                    item {
                        Text("您还没有好友，快去查找用户并添加吧！", modifier = Modifier.padding(16.dp))
                    }
                } else {
                    items(friends) { friend ->
                        ListItem(
                            headlineContent = { Text(friend.user.nickname) },
                            supportingContent = { Text("在线状态") },
                            modifier = Modifier.clickable { 
                                navController.navigate("chat/$userId/${friend.user.uid}") 
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    
    // @提醒超时弹窗
    if (showMentionDialog) {
        MentionTimeoutDialog(
            userId = userId,
            navController = navController,
            onDismiss = { showMentionDialog = false }
        )
    }
    
    // 打卡提醒弹窗
    if (showCheckInDialog) {
        CheckInReminderDialog(
            userId = userId,
            navController = navController,
            onDismiss = { showCheckInDialog = false }
        )
    }
}