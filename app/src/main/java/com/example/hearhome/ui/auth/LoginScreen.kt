package com.example.hearhome.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private enum class ResetMode { EMAIL, SECURITY_QUESTION }

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

    var mode by remember { mutableStateOf(ResetMode.EMAIL) }
    var step by remember { mutableStateOf(1) }
    var resetEmail by remember { mutableStateOf("") }
    var emailCode by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirmPwd by remember { mutableStateOf("") }
    var fieldError by remember { mutableStateOf<String?>(null) }

    val question = viewModel.getCurrentResetQuestion()
    val globalError = (authState as? AuthViewModel.AuthState.Error)?.message
    val isLoading = authState is AuthViewModel.AuthState.Loading

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthViewModel.AuthState.EmailCodeSent -> if (state.purpose == AuthViewModel.VerificationPurpose.RESET_PASSWORD) {
                mode = ResetMode.EMAIL
                step = 2
                fieldError = null
            }
            is AuthViewModel.AuthState.EmailCodeVerified -> if (state.purpose == AuthViewModel.VerificationPurpose.RESET_PASSWORD) {
                mode = ResetMode.EMAIL
                step = 3
                fieldError = null
            }
            is AuthViewModel.AuthState.SecurityQuestionRequired -> {
                mode = ResetMode.SECURITY_QUESTION
                step = 2
                fieldError = null
            }
            is AuthViewModel.AuthState.PasswordResetSuccess -> {
                step = 3
            }
            else -> {}
        }
    }

    fun validatePassword(): String? = when {
        newPwd.length < 6 -> "新密码至少 6 位"
        newPwd != confirmPwd -> "两次密码不一致"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("找回密码") },
        text = {
            Column {
                when (mode) {
                    ResetMode.EMAIL -> when (step) {
                        1 -> {
                            OutlinedTextField(
                                value = resetEmail,
                                onValueChange = { resetEmail = it; fieldError = null; viewModel.resetAuthResult() },
                                label = { Text("注册邮箱") },
                                isError = !globalError.isNullOrBlank(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("优先通过邮箱验证码完成身份验证。")
                            fieldError?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        2 -> {
                            OutlinedTextField(
                                value = emailCode,
                                onValueChange = { emailCode = it; fieldError = null },
                                label = { Text("邮箱验证码") },
                                isError = !fieldError.isNullOrBlank(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.resendEmailCode(AuthViewModel.VerificationPurpose.RESET_PASSWORD) }, enabled = !isLoading) {
                                Text("收不到？重发验证码")
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = newPwd,
                                onValueChange = { newPwd = it; fieldError = null },
                                label = { Text("新密码（≥6位）") },
                                visualTransformation = PasswordVisualTransformation(),
                                isError = !fieldError.isNullOrBlank(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = confirmPwd,
                                onValueChange = { confirmPwd = it; fieldError = null },
                                label = { Text("确认新密码") },
                                visualTransformation = PasswordVisualTransformation(),
                                isError = !fieldError.isNullOrBlank(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            fieldError?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    ResetMode.SECURITY_QUESTION -> when (step) {
                        2 -> {
                            Text(
                                text = "邮箱不可用？已切换到密保问题验证，由后端返回的 USE_SECURITY_QUESTION 触发。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(text = "使用密保问题验证：${question ?: ""}")
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = answer,
                                onValueChange = { answer = it; fieldError = null },
                                label = { Text("答案") },
                                isError = !fieldError.isNullOrBlank(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            fieldError?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = newPwd,
                                onValueChange = { newPwd = it; fieldError = null },
                                label = { Text("新密码（≥6位）") },
                                visualTransformation = PasswordVisualTransformation(),
                                isError = !fieldError.isNullOrBlank(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = confirmPwd,
                                onValueChange = { confirmPwd = it; fieldError = null },
                                label = { Text("确认新密码") },
                                visualTransformation = PasswordVisualTransformation(),
                                isError = !fieldError.isNullOrBlank(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            fieldError?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, color = MaterialTheme.colorScheme.error)
                            }
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
                is AuthViewModel.AuthState.PasswordResetSuccess -> TextButton(onClick = onDismiss) { Text("完成") }
                else -> {
                    Button(onClick = {
                        when (mode) {
                            ResetMode.EMAIL -> when (step) {
                                1 -> {
                                    if (resetEmail.isBlank()) {
                                        fieldError = "请输入注册邮箱"
                                    } else {
                                        viewModel.startResetByEmail(resetEmail.trim())
                                    }
                                }
                                2 -> {
                                    if (emailCode.isBlank()) {
                                        fieldError = "请输入验证码"
                                    } else {
                                        viewModel.verifyEmailCode(emailCode.trim())
                                    }
                                }
                                else -> {
                                    fieldError = validatePassword()
                                    if (fieldError == null) viewModel.setNewPassword(newPwd)
                                }
                            }
                            ResetMode.SECURITY_QUESTION -> when (step) {
                                1 -> viewModel.startResetByQuestion(resetEmail.trim())
                                2 -> {
                                    if (answer.isBlank()) {
                                        fieldError = "请输入答案"
                                    } else {
                                        viewModel.verifyAnswer(answer)
                                        step = 3
                                    }
                                }
                                else -> {
                                    fieldError = validatePassword()
                                    if (fieldError == null) viewModel.setNewPassword(newPwd)
                                }
                            }
                        }
                    }) {
                        Text(
                            when (mode) {
                                ResetMode.EMAIL -> when (step) { 1 -> "发送验证码"; 2 -> "验证"; else -> "提交新密码" }
                                ResetMode.SECURITY_QUESTION -> when (step) { 2 -> "验证答案"; else -> "提交新密码" }
                            }
                        )
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
