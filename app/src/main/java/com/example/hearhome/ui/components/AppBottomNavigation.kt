package com.example.hearhome.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Group

@Composable
fun AppBottomNavigation(
    currentRoute: String,
    navController: NavController,
    userId: Int
) {
    NavigationBar {
        // 🏠 主页
        NavigationBarItem(
            selected = currentRoute == "home",
            onClick = { navController.navigate("home/$userId") },
            icon = { Icon(Icons.Default.Home, contentDescription = "主页") },
            label = { Text("主页") }
        )

        // 🤝 好友
        NavigationBarItem(
            selected = currentRoute == "friend",
            onClick = { navController.navigate("relationList/$userId") },
            icon = { Icon(Icons.Default.Group, contentDescription = "好友") },
            label = { Text("好友") }
        )

        // 👤 个人中心
        NavigationBarItem(
            selected = currentRoute == "profile",
            onClick = { navController.navigate("profile/$userId") },
            icon = { Icon(Icons.Default.Person, contentDescription = "个人中心") },
            label = { Text("个人中心") }
        )
    }
}
