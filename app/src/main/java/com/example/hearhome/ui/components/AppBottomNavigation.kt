package com.example.hearhome.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Photo

/**
 * 应用底部导航栏
 * 支持主页、好友、空间、个人中心四个主要功能入口
 */
@Composable
fun AppBottomNavigation(
    currentRoute: String = "home",
    navController: NavController,
    userId: Int
) {
    NavigationBar {
        // 🏠 主页
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate("home/$userId") },
            icon = { Icon(Icons.Default.Home, contentDescription = "主页") },
            label = { Text("主页") }
        )

        // 🤝 好友
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate("relationList/$userId") },
            icon = { Icon(Icons.Default.Group, contentDescription = "好友") },
            label = { Text("好友") }
        )
        
        // � 空间
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate("space_list/$userId") },
            icon = { Icon(Icons.Default.Photo, contentDescription = "空间") },
            label = { Text("空间") }
        )

        // 👤 个人中心
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate("profile/$userId") },
            icon = { Icon(Icons.Default.Person, contentDescription = "个人中心") },
            label = { Text("个人中心") }
        )
    }
}
