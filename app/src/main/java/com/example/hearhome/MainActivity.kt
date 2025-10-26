package com.example.hearhome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box // [已添加] 导入 Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding // [已添加] 导入 padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.data.local.User
import com.example.hearhome.ui.auth.AuthViewModel
import com.example.hearhome.ui.auth.AuthViewModelFactory // [已修复] 添加导入
import com.example.hearhome.ui.auth.LoginScreen
import com.example.hearhome.ui.auth.RegistrationScreen
import com.example.hearhome.ui.home.HomeScreen
import com.example.hearhome.ui.profile.ProfileScreen // [已修复] 移除重复导入
import com.example.hearhome.ui.theme.HearHomeTheme
// [已修复] 删除了重复的 ProfileScreen 导入

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HearHomeTheme {
                // [已修复] innerPadding 已被使用
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AuthNavigation()
                    }
                }
            }
        }
    }
}

@Composable
fun AuthNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    // [已修复] 使用 getInstance
    val userDao = AppDatabase.getInstance(context).userDao()

    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(userDao))
    val authState by authViewModel.authState.collectAsState()

    // --- [合并后] 的导航逻辑 ---
    LaunchedEffect(authState) {
        when (val state = authState) {
            // [修改] 登录成功
            is AuthViewModel.AuthState.Success -> {
                navController.navigate("home/${state.user.uid}") {
                    popUpTo(navController.graph.startDestinationId) {
                        inclusive = true
                    }
                }
            }
            // [修改] 注册成功
            is AuthViewModel.AuthState.RegisterSuccess -> {
                navController.navigate("home/${state.user.uid}") {
                    popUpTo(navController.graph.startDestinationId) {
                        inclusive = true
                    }
                }
            }
            // [修改] 退出登录或初始状态
            is AuthViewModel.AuthState.Idle -> {
                navController.navigate("login") {
                    popUpTo(navController.graph.id) {
                        inclusive = true
                    }
                }
            }
            // 其他状态 (Error, Loading, PasswordReset...) 在各自的屏幕上处理，
            // 导航器不需要全局响应。
            else -> { /* Do nothing */ }
        }
    }

    // --- (我们现有的 NavHost，无需改动) ---
    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                viewModel = authViewModel,
                // [已修复] 语法错误
                onNavigateToRegister = {
                    // Opening a new screen
                    authViewModel.resetAuthState() // 重置状态
                    navController.navigate("register")
                }
            )
        }
        composable("register") {
            RegistrationScreen(
                viewModel = authViewModel,
                onNavigateToLogin = {
                    authViewModel.resetAuthState() // 重置状态
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "home/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId")
            if (userId != null) {
                HomeScreen(navController = navController, userId = userId)
            } else {
                navController.navigate("login") { popUpTo(0) }
            }
        }

        composable(
            route = "profile/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId")
            if (userId != null) {
                ProfileScreen(
                    navController = navController,
                    authViewModel = authViewModel,
                    userId = userId
                )
            } else {
                navController.navigate("login") { popUpTo(0) }
            }
        }
    }
}