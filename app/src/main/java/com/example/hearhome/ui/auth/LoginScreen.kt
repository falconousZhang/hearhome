package com.example.hearhome.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
) {
    val authState by viewModel.authState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }

    val errorMessage = (authState as? AuthViewModel.AuthState.Error)?.message

    Column(
        Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("欢迎回来", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("邮箱") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.login(email.trim(), password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = authState !is AuthViewModel.AuthState.Loading
        ) {
            Text(if (authState is AuthViewModel.AuthState.Loading) "登录中…" else "登录")
        }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onNavigateToRegister) { Text("没有账号？去注册") }
            TextButton(onClick = { showResetDialog = true }) { Text("忘记密码？") }
        }
    }

    if (showResetDialog) {
        ForgotPasswordDialog(
            viewModel = viewModel,
            onDismiss = {
                showResetDialog = false
                // [修复] 调用正确的函数名
                viewModel.resetAuthResult()
            }
        )
    }

    // LaunchedEffect 已被移至 MainActivity/AuthNavigation 中
}

// [新] 忘记密码对话框 (来自新文件)
@Composable
private fun ForgotPasswordDialog(
    viewModel: AuthViewModel,
    onDismiss: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()

    var step by remember { mutableStateOf(1) }
    var resetEmail by remember { mutableStateOf("") }
    val question = viewModel.getCurrentResetQuestion()
    var answer by remember { mutableStateOf("") }
    var answerError by remember { mutableStateOf<String?>(null) }
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var pwdError by remember { mutableStateOf<String?>(null) }

    val globalError = (authState as? AuthViewModel.AuthState.Error)?.message

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("找回密码") },
        text = {
            Column {
                when (step) {
                    1 -> {
                        OutlinedTextField(
                            value = resetEmail,
                            onValueChange = { resetEmail = it; viewModel.resetAuthResult() },
                            label = { Text("注册邮箱") },
                            isError = globalError != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("输入邮箱以获取你设置的个性化问题。")
                    }
                    2 -> {
                        Text(text = "安全问题：${question ?: ""}")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { answer = it; answerError = null },
                            label = { Text("答案") },
                            isError = answerError != null || globalError != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!answerError.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(answerError!!, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    3 -> {
                        OutlinedTextField(
                            value = newPwd,
                            onValueChange = { newPwd = it; pwdError = null },
                            label = { Text("新密码（≥6位）") },
                            visualTransformation = PasswordVisualTransformation(),
                            isError = pwdError != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmPwd,
                            onValueChange = { confirmPwd = it; pwdError = null },
                            label = { Text("确认新密码") },
                            visualTransformation = PasswordVisualTransformation(),
                            isError = pwdError != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!pwdError.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(pwdError!!, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (!globalError.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(globalError, color = MaterialTheme.colorScheme.error)
                }

                if (authState is AuthViewModel.AuthState.PasswordResetSuccess) {
                    Spacer(Modifier.height(8.dp))
                    Text("密码重置成功！请关闭对话框重新登录。", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            when (authState) {
                is AuthViewModel.AuthState.Loading -> CircularProgressIndicator()
                is AuthViewModel.AuthState.PasswordResetSuccess -> {
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
                else -> {
                    Button(onClick = {
                        when (step) {
                            1 -> viewModel.startResetByQuestion(resetEmail.trim())
                            2 -> {
                                val ok = viewModel.verifyAnswer(answer)
                                if (ok) step = 3 else answerError = "答案不正确"
                            }
                            3 -> {
                                pwdError = when {
                                    newPwd.length < 6 -> "新密码至少 6 位"
                                    newPwd != confirmPwd -> "两次密码不一致"
                                    else -> null
                                }
                                if (pwdError == null) viewModel.setNewPassword(newPwd)
                            }
                        }
                    }) { Text(when (step) { 1 -> "下一步"; 2 -> "验证答案"; else -> "提交" }) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    LaunchedEffect(authState) {
        if (authState is AuthViewModel.AuthState.PasswordResetReady) step = 2
    }
}
