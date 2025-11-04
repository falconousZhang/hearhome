package com.example.hearhome.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.hearhome.ui.components.AppBottomNavigation

@Composable
fun HomeScreen(
    navController: NavController,
    userId: Int
) {
    Scaffold(
        bottomBar = {
            AppBottomNavigation(
                currentRoute = "home",
                navController = navController,
                userId = userId
            )
        }
    ) { innerPadding ->
        // ✅ 页面主体：只有一个“查找用户”按钮
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = { navController.navigate("search/$userId") },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(50.dp)
            ) {
                Text("查找用户")
            }
        }
    }
}
