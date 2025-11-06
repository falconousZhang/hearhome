package com.example.hearhome

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.hearhome.ui.auth.*
import com.example.hearhome.ui.chat.ChatScreen
import com.example.hearhome.ui.friend.FriendRequestsScreen
import com.example.hearhome.ui.home.HomeScreen
import com.example.hearhome.ui.profile.ProfileScreen
import com.example.hearhome.ui.relation.CoupleRequestsScreen
import com.example.hearhome.ui.relation.RelationListScreen
import com.example.hearhome.ui.search.SearchUserScreen
import com.example.hearhome.ui.space.SpaceListScreen
import com.example.hearhome.ui.space.SpaceDetailScreen
import com.example.hearhome.ui.space.PostDetailScreen
import com.example.hearhome.ui.space.FavoritesScreen
import com.example.hearhome.ui.space.SpaceManageScreen
import com.example.hearhome.ui.space.SpaceInfoScreen
import com.example.hearhome.ui.theme.HearHomeTheme
import com.example.hearhome.utils.NotificationHelper

class MainActivity : ComponentActivity() {
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 初始化通知渠道
        NotificationHelper.createNotificationChannels(this)
        
        setContent {
            HearHomeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    AuthNavigation()
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
    val authViewModel: AuthViewModel =
        viewModel(factory = AuthViewModelFactory(userDao))
    val authState by authViewModel.authState.collectAsState()

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
        ) {
            val userId = it.arguments?.getInt("userId") ?: return@composable
            HomeScreen(navController = navController, userId = userId)
        }

        composable(
            route = "profile/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) {
            val userId = it.arguments?.getInt("userId") ?: return@composable
            ProfileScreen(navController, authViewModel, userId)
        }

        composable(
            route = "search/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) {
            val userId = it.arguments?.getInt("userId") ?: return@composable
            SearchUserScreen(navController = navController, currentUserId = userId)
        }

        composable(
            route = "friendRequests/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) {
            val userId = it.arguments?.getInt("userId") ?: return@composable
            FriendRequestsScreen(navController, userId)
        }

        composable(
            route = "relationList/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) {
            val userId = it.arguments?.getInt("userId") ?: return@composable
            RelationListScreen(navController, userId)
        }

        composable(
            route = "coupleRequests/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) {
            val userId = it.arguments?.getInt("userId") ?: return@composable
            CoupleRequestsScreen(navController, userId)
        }

        composable(
            route = "chat/{currentUserId}/{friendUserId}",
            arguments = listOf(
                navArgument("currentUserId") { type = NavType.IntType },
                navArgument("friendUserId") { type = NavType.IntType }
            )
        ) {
            val currentUserId = it.arguments?.getInt("currentUserId") ?: return@composable
            val friendUserId = it.arguments?.getInt("friendUserId") ?: return@composable
            ChatScreen(navController, currentUserId, friendUserId)
        }
        
        // ==================== 空间相关路由 ====================
        
        composable(
            route = "space_list/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) {
            val userId = it.arguments?.getInt("userId") ?: return@composable
            SpaceListScreen(navController, userId)
        }
        
        composable(
            route = "space_detail/{spaceId}/{userId}",
            arguments = listOf(
                navArgument("spaceId") { type = NavType.IntType },
                navArgument("userId") { type = NavType.IntType }
            )
        ) {
            val spaceId = it.arguments?.getInt("spaceId") ?: return@composable
            val userId = it.arguments?.getInt("userId") ?: return@composable
            SpaceDetailScreen(navController, spaceId, userId)
        }
        
        composable(
            route = "post_detail/{postId}/{userId}",
            arguments = listOf(
                navArgument("postId") { type = NavType.IntType },
                navArgument("userId") { type = NavType.IntType }
            )
        ) {
            val postId = it.arguments?.getInt("postId") ?: return@composable
            val userId = it.arguments?.getInt("userId") ?: return@composable
            PostDetailScreen(navController, postId, userId)
        }
        
        composable(
            route = "favorites/{spaceId}/{userId}",
            arguments = listOf(
                navArgument("spaceId") { type = NavType.IntType },
                navArgument("userId") { type = NavType.IntType }
            )
        ) {
            val spaceId = it.arguments?.getInt("spaceId") ?: return@composable
            val userId = it.arguments?.getInt("userId") ?: return@composable
            FavoritesScreen(navController, spaceId, userId)
        }
        
        composable(
            route = "space_manage/{spaceId}/{userId}",
            arguments = listOf(
                navArgument("spaceId") { type = NavType.IntType },
                navArgument("userId") { type = NavType.IntType }
            )
        ) {
            val spaceId = it.arguments?.getInt("spaceId") ?: return@composable
            val userId = it.arguments?.getInt("userId") ?: return@composable
            SpaceManageScreen(navController, spaceId, userId)
        }
        
        composable(
            route = "space_info/{spaceId}/{userId}",
            arguments = listOf(
                navArgument("spaceId") { type = NavType.IntType },
                navArgument("userId") { type = NavType.IntType }
            )
        ) {
            val spaceId = it.arguments?.getInt("spaceId") ?: return@composable
            val userId = it.arguments?.getInt("userId") ?: return@composable
            SpaceInfoScreen(navController, spaceId, userId)
        }

    }
}

private fun handleGlobalNavigation(
    authState: AuthViewModel.AuthState,
    navController: NavHostController
) {
    when (authState) {
        is AuthViewModel.AuthState.Success,
        is AuthViewModel.AuthState.RegisterSuccess -> {
            // ✅ 显式转换后取 user
            val user = when (authState) {
                is AuthViewModel.AuthState.Success -> authState.user
                is AuthViewModel.AuthState.RegisterSuccess -> authState.user
                else -> null
            }
            user?.let {
                navController.navigate(route = "home/${it.uid}") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                }
            }
        }

        is AuthViewModel.AuthState.PasswordUpdateSuccess -> {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }

        is AuthViewModel.AuthState.Idle -> {
            if (navController.currentDestination?.route != "login") {
                navController.navigate("login") {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }

        else -> {}
    }
}
