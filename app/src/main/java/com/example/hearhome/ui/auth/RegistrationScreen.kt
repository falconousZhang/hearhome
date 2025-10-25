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
fun RegistrationScreen(
    viewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var secQuestion by remember { mutableStateOf("") }
    var secAnswer by remember { mutableStateOf("") }

    val errorMessage = (authState as? AuthViewModel.AuthState.Error)?.message

    Column(
        Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState()) // 使其可滚动
    ) {
        Text("创建账号", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("邮箱") }, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("密码（≥6位）") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Text("设置个性化问题（用于找回密码）", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = secQuestion,
            onValueChange = { secQuestion = it },
            label = { Text("你的问题（例如：我第一只宠物的名字？）") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = secAnswer,
            onValueChange = { secAnswer = it },
            label = { Text("答案（区分大小写）") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                viewModel.register(
                    email.trim(), password, secQuestion.trim(), secAnswer.trim()
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = authState !is AuthViewModel.AuthState.Loading
        ) {
            Text(if (authState is AuthViewModel.AuthState.Loading) "注册中…" else "注册")
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
        TextButton(onClick = onNavigateToLogin) { Text("已有账号？去登录") }
    }

    // LaunchedEffect 已被移至 MainActivity/AuthNavigation 中
}