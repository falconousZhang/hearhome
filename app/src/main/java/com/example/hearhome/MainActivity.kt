package com.example.hearhome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.ui.auth.AuthViewModel
import com.example.hearhome.ui.auth.AuthViewModelFactory
import com.example.hearhome.ui.auth.LoginScreen
import com.example.hearhome.ui.auth.RegistrationScreen
import com.example.hearhome.ui.home.HomeScreen
import com.example.hearhome.ui.profile.ProfileScreen
import com.example.hearhome.ui.theme.HearHomeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HearHomeTheme {
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
    val userDao = AppDatabase.getInstance(context).userDao()

    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(userDao))
    val authState by authViewModel.authState.collectAsState()

    // [修改] 全局导航只处理需要跨页面的状态
    LaunchedEffect(authState) {
        handleGlobalNavigation(authState, navController)
    }

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = {
                    authViewModel.resetAuthResult()
                    navController.navigate("register")
                }
            )
        }
        composable("register") {
            RegistrationScreen(
                viewModel = authViewModel,
                onNavigateToLogin = {
                    authViewModel.resetAuthResult()
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
                // 如果没有userId，强制返回登录页
                navController.navigate("login") { popUpTo(navController.graph.id) { inclusive = true } }
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
                navController.navigate("login") { popUpTo(navController.graph.id) { inclusive = true } }
            }
        }
    }
}

// [新] 将导航逻辑提取到一个独立的函数中，更清晰
private fun handleGlobalNavigation(authState: AuthViewModel.AuthState, navController: NavHostController) {
    when (authState) {
        is AuthViewModel.AuthState.Success -> {
            navController.navigate("home/${authState.user.uid}") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        }
        is AuthViewModel.AuthState.RegisterSuccess -> {
            navController.navigate("home/${authState.user.uid}") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        }
        is AuthViewModel.AuthState.PasswordUpdateSuccess -> {
             // 密码修改成功，也返回登录页
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
        is AuthViewModel.AuthState.Idle -> {
            // 当登出或会话过期时返回登录页
            if (navController.currentDestination?.route != "login") {
                 navController.navigate("login") {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }
        // 其他状态（如Error, Loading, AwaitingInput）由各自的屏幕自己处理，不在这里进行全局导航
        else -> { /* Do Nothing */ }
    }
}
