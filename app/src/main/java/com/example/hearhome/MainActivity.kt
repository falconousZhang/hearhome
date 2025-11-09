package com.example.hearhome

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
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
import com.example.hearhome.data.local.AnniversaryScreen
import com.example.hearhome.ui.auth.AuthViewModel
import com.example.hearhome.ui.auth.AuthViewModelFactory
import com.example.hearhome.ui.auth.LoginScreen
import com.example.hearhome.ui.auth.RegistrationScreen
import com.example.hearhome.ui.chat.ChatScreen
import com.example.hearhome.ui.friend.FriendRequestsScreen
import com.example.hearhome.ui.home.HomeScreen
import com.example.hearhome.ui.profile.ProfileScreen
import com.example.hearhome.ui.relation.CoupleRequestsScreen
import com.example.hearhome.ui.relation.RelationListScreen
import com.example.hearhome.ui.search.SearchUserScreen
import com.example.hearhome.ui.space.*
import com.example.hearhome.ui.theme.HearHomeTheme
import com.example.hearhome.utils.NotificationHelper

class MainActivity : ComponentActivity() {
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 初始化通知渠道
        NotificationHelper.createNotificationChannels(this)

        // 从通知携带的深链参数（可为空）
        val navigateTarget = intent?.getStringExtra("navigate")
        val spaceIdFromNoti = intent?.getIntExtra("spaceId", -1) ?: -1

        setContent {
            HearHomeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    AuthNavigation(
                        navigateTarget = navigateTarget,
                        navigateSpaceId = if (spaceIdFromNoti > 0) spaceIdFromNoti else null
                    )
                }
            }
        }
    }
}

@Composable
fun AuthNavigation(
    navigateTarget: String? = null,
    navigateSpaceId: Int? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val userDao = AppDatabase.getInstance(context).userDao()
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModelFactory(userDao))
    val authState by authViewModel.authState.collectAsState()

    // 将通知深链缓存起来，等用户登录成功后再导航
    var pendingDeepLink by remember {
        mutableStateOf<Pair<String, Int>?>(if (navigateTarget == "anniversary" && navigateSpaceId != null) {
            "anniversary" to navigateSpaceId
        } else null)
    }

    LaunchedEffect(authState) {
        handleGlobalNavigation(authState, navController)
    }

    // 登录/注册成功后，如果存在深链则跳入纪念日页面
    LaunchedEffect(authState, pendingDeepLink) {
        val user = when (authState) {
            is AuthViewModel.AuthState.Success -> (authState as AuthViewModel.AuthState.Success).user
            is AuthViewModel.AuthState.RegisterSuccess -> (authState as AuthViewModel.AuthState.RegisterSuccess).user
            else -> null
        }
        val deep = pendingDeepLink
        if (user != null && deep != null) {
            val (target, spaceId) = deep
            if (target == "anniversary") {
                navController.navigate("anniversary/$spaceId/${user.uid}")
                pendingDeepLink = null
            }
        }
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

        // ==================== 纪念日路由 ====================
        composable(
            route = "anniversary/{spaceId}/{userId}",
            arguments = listOf(
                navArgument("spaceId") { type = NavType.IntType },
                navArgument("userId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val spaceId = backStackEntry.arguments?.getInt("spaceId") ?: return@composable
            val userId = backStackEntry.arguments?.getInt("userId") ?: return@composable

            AnniversaryScreen(
                spaceId = spaceId,
                currentUserId = userId,
                partnerUserId = userId, // 如需真实伴侣ID，可在 Screen 内查询
                onBack = { navController.popBackStack() }
            )
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
