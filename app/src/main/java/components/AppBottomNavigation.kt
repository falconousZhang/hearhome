package com.example.hearhome.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController

/**
 * 共享的底部导航栏
 * @param currentRoute "home" 或 "profile"，用于高亮当前标签
 * @param navController 用于导航
 * @param userId 需要传递给导航路由
 */
@Composable
fun AppBottomNavigation(
    currentRoute: String,
    navController: NavController,
    userId: Int
) {
    NavigationBar {
        // 1. 主页 标签
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Home, contentDescription = "主页") },
            label = { Text("主页") },
            selected = currentRoute == "home",
            onClick = {
                if (currentRoute != "home") {
                    // 如果当前在个人中心页，点击“主页”则返回
                    navController.popBackStack()
                }
            }
        )

        // 2. 个人中心 标签
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Person, contentDescription = "个人中心") },
            label = { Text("个人中心") },
            selected = currentRoute == "profile",
            onClick = {
                if (currentRoute != "profile") {
                    // 如果当前在主页，点击“个人中心”则导航
                    navController.navigate("profile/$userId")
                }
            }
        )
    }
}