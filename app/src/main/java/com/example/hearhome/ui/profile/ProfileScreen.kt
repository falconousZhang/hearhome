package com.example.hearhome.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf // <-- [新] 导入
import androidx.compose.runtime.remember // <-- [新] 导入
import androidx.compose.runtime.setValue // <-- [新] 导入
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.ui.auth.AuthViewModel
import com.example.hearhome.ui.components.AppBottomNavigation // <-- [新] 导入

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    userId: Int
) {
    // ... ViewModel 和 State 的设置保持不变 ...
    val profileViewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModelFactory(
            AppDatabase.getInstance(navController.context).userDao()
        )
    )
    LaunchedEffect(userId) { profileViewModel.loadUser(userId) }
    val uiState by profileViewModel.uiState.collectAsState()
    val user = uiState.user

    // --- [新] 用于控制退出登录确认框的状态 (Request 2) ---
    var showLogoutDialog by remember { mutableStateOf(false) }

    // --- [新] 如果 showLogoutDialog 为 true，则显示弹窗 (Request 2) ---
    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onConfirm = {
                authViewModel.logout()
                showLogoutDialog = false
            },
            onDismiss = {
                showLogoutDialog = false
            }
        )
    }

    Scaffold(
        // [修改] 移除 TopAppBar

        // --- [新] 添加底部导航栏 (Request 1) ---
        bottomBar = {
            AppBottomNavigation(
                currentRoute = "profile", // 告诉导航栏当前是“个人中心”
                navController = navController,
                userId = userId
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ... 用户信息区域 (InfoCard 等) 保持不变 ...
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (user != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "个人信息",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoCard(label = "邮箱", value = user.email)
                    InfoCard(label = "用户ID", value = user.uid.toString())
                }
            } else {
                Text("无法加载用户信息。")
            }

            // --- [修改] 退出登录按钮的行为 (Request 2) ---
            Button(
                onClick = {
                    // 点击按钮时，显示确认框，而不是直接退出
                    showLogoutDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text("退出登录")
            }
        }
    }
}

// ... InfoCard(...) Composable 保持不变 ...
@Composable
private fun InfoCard(label: String, value: String) {
    // (代码与之前相同)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 18.sp
            )
        }
    }
}

// --- [新] 退出登录确认弹窗 (Request 2) ---
@Composable
private fun LogoutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("退出登录") },
        text = { Text("您确定要退出登录吗？") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}