package com.example.hearhome.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.ui.auth.AuthViewModel
import com.example.hearhome.ui.components.AppBottomNavigation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    userId: Int
) {
    val profileViewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModelFactory(AppDatabase.getInstance(LocalContext.current).userDao())
    )
    LaunchedEffect(userId) { profileViewModel.loadUser(userId) }
    val uiState by profileViewModel.uiState.collectAsState()
    val user = uiState.user

    var oldPassword by remember { mutableStateOf("") }
    var securityAnswerForPasswordChange by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var securityQuestion by remember { mutableStateOf("") }
    var securityAnswerForSQ by remember { mutableStateOf("") }
    var passwordForSQ by remember { mutableStateOf("") }

    var userIdVisible by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    var oldPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var passwordForSQVisible by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(user) {
        if (user != null) {
            securityQuestion = user.secQuestion
        }
    }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthViewModel.AuthState.Error -> {
                scope.launch { snackbarHostState.showSnackbar(state.message) }
                authViewModel.onProfileEventConsumed()
            }
            is AuthViewModel.AuthState.UpdateSuccess -> {
                scope.launch { snackbarHostState.showSnackbar("私密问题更新成功！") }
                securityAnswerForSQ = ""
                passwordForSQ = ""
                authViewModel.onProfileEventConsumed()
            }
            is AuthViewModel.AuthState.PasswordUpdateSuccess -> {
                scope.launch { snackbarHostState.showSnackbar("密码修改成功，请重新登录") }
            }
            else -> {}
        }
    }



    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AppBottomNavigation(
                currentRoute = "profile",
                navController = navController,
                userId = userId
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.isLoading) {
                Spacer(modifier = Modifier.weight(1f))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.weight(1f))
            } else if (user != null) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color(user.avatarColor.toColorInt()))
                )
                Spacer(modifier = Modifier.height(24.dp))

                Text("个人信息", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))

                InfoCard(label = "昵称", value = user.nickname)
                InfoCard(label = "性别", value = user.gender)
                InfoCard(label = "邮箱", value = user.email)
                InfoCard(
                    label = "用户ID",
                    value = if (userIdVisible) user.uid.toString() else "********",
                    trailingIcon = {
                        IconButton(onClick = { userIdVisible = !userIdVisible }) {
                            Icon(
                                imageVector = if (userIdVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "切换ID可见性"
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))
                Text("修改密码", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                // ✅ 恢复修改密码的UI
                OutlinedTextField(value = oldPassword, onValueChange = { oldPassword = it }, label = { Text("旧密码") }, visualTransformation = if (oldPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { oldPasswordVisible = !oldPasswordVisible }) { Icon(imageVector = if (oldPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle password visibility") } }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = securityAnswerForPasswordChange, onValueChange = { securityAnswerForPasswordChange = it }, label = { Text("安全问题: ${user.secQuestion}") }, placeholder = { Text("请输入答案") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, label = { Text("新密码") }, visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) { Icon(imageVector = if (newPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle password visibility") } }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("确认新密码") }, visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) { Icon(imageVector = if (confirmPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle password visibility") } }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { authViewModel.updatePassword(email = user.email, oldPassword = oldPassword, securityAnswer = securityAnswerForPasswordChange, newPassword = newPassword, confirmPassword = confirmPassword) }, modifier = Modifier.fillMaxWidth()) { Text("确认修改密码") }

                Spacer(modifier = Modifier.height(32.dp))
                Text("设置私密问题", style = MaterialTheme. typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                // ✅ 恢复设置私密问题的UI
                OutlinedTextField(value = passwordForSQ, onValueChange = { passwordForSQ = it }, label = { Text("当前密码") }, visualTransformation = if (passwordForSQVisible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { passwordForSQVisible = !passwordForSQVisible }) { Icon(imageVector = if (passwordForSQVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle password visibility") } }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = securityQuestion, onValueChange = { securityQuestion = it }, label = { Text("新私密问题") }, placeholder = { Text("例如：我最喜欢的颜色是？") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = securityAnswerForSQ, onValueChange = { securityAnswerForSQ = it }, label = { Text("新答案") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { authViewModel.updateSecurityQuestion(email = user.email, password = passwordForSQ, question = securityQuestion, answer = securityAnswerForSQ) }, modifier = Modifier.fillMaxWidth()) { Text("保存私密问题") }
                
                Spacer(modifier = Modifier.height(32.dp))

                Button(onClick = { showLogoutDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text("退出登录") }

            } else {
                Spacer(modifier = Modifier.weight(1f))
                Text(uiState.error ?: "无法加载用户信息。")
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        
        if (showLogoutDialog) {
            LogoutConfirmationDialog(
                onConfirm = {
                    showLogoutDialog = false
                    authViewModel.logout()
                },
                onDismiss = { showLogoutDialog = false }
            )
        }
    }
}

@Composable
private fun InfoCard(label: String, value: String, trailingIcon: @Composable (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(text = value, style = MaterialTheme.typography.bodyLarge, fontSize = 18.sp)
            }
            trailingIcon?.invoke()
        }
    }
}

@Composable
private fun LogoutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("退出登录") },
        text = { Text("您确定要退出登录吗？") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
