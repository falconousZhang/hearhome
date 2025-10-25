package com.example.hearhome.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.hearhome.ui.components.AppBottomNavigation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    userId: Int // 我们将从导航中获取用户ID
) {
    Scaffold(
        bottomBar = {
            AppBottomNavigation(
                currentRoute = "home", // 告诉导航栏当前是“主页”
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
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "欢迎！你已登录。",
                style = MaterialTheme.typography.headlineSmall
            )

        }
    }
}