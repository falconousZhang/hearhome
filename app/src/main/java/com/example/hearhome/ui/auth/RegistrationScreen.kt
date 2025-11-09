package com.example.hearhome.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
    var nickname by remember { mutableStateOf("") }
    var secQuestion by remember { mutableStateOf("") }
    var secAnswer by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("男") } // 新增性别状态，默认为“男”

    val errorMessage = (authState as? AuthViewModel.AuthState.Error)?.message

    Column(
        Modifier
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("创建账号", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(value = nickname, onValueChange = { nickname = it }, label = { Text("昵称（必填）") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("邮箱") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("密码（≥6位）") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

        // -- 性别选择 --
        Spacer(Modifier.height(12.dp))
        Text("性别", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = gender == "男", onClick = { gender = "男" })
            Text("男", Modifier.padding(start = 4.dp).clickable { gender = "男" })
            Spacer(Modifier.width(16.dp))
            RadioButton(selected = gender == "女", onClick = { gender = "女" })
            Text("女", Modifier.padding(start = 4.dp).clickable { gender = "女" })
        }

        Spacer(Modifier.height(16.dp))
        Text("设置个性化问题（用于找回密码）", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(value = secQuestion, onValueChange = { secQuestion = it }, label = { Text("你的问题（例如：我第一只宠物的名字？）") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = secAnswer, onValueChange = { secAnswer = it }, label = { Text("答案（区分大小写）") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.register(
                    email.trim(),
                    password.trim(),
                    secQuestion.trim(),
                    secAnswer.trim(),
                    nickname.trim(),
                    gender // 传递性别参数
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = authState !is AuthViewModel.AuthState.Loading
        ) {
            Text(if (authState is AuthViewModel.AuthState.Loading) "注册中…" else "注册")
        }

        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onNavigateToLogin) { Text("已有账号？去登录") }
    }
}
