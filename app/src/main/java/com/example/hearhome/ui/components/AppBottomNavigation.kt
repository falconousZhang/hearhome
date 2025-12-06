package com.example.hearhome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Photo
import com.example.hearhome.data.local.AppDatabase

/**
 * 应用底部导航栏
 * 支持主页、好友、空间、个人中心四个主要功能入口
 * 空间图标上会显示待处理@提醒的小红点
 */
@Composable
fun AppBottomNavigation(
    currentRoute: String = "home",
    navController: NavController,
    userId: Int
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    
    // 获取待处理的@提醒数量
    val pendingMentionCount by db.postMentionDao()
        .getPendingMentionCountFlow(userId)
        .collectAsState(initial = 0)
    
    NavigationBar {
        // 主页
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate("home/$userId") },
            icon = { Icon(Icons.Default.Home, contentDescription = "主页") },
            label = { Text("主页") }
        )

        // 好友
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate("relationList/$userId") },
            icon = { Icon(Icons.Default.Group, contentDescription = "好友") },
            label = { Text("好友") }
        )
        
        // 空间（带小红点）
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate("space_list/$userId") },
            icon = { 
                BadgedBox(
                    badge = {
                        if (pendingMentionCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error
                            ) {
                                if (pendingMentionCount <= 99) {
                                    Text(pendingMentionCount.toString())
                                } else {
                                    Text("99+")
                                }
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Photo, contentDescription = "空间")
                }
            },
            label = { Text("空间") }
        )

        // 个人中心
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate("profile/$userId") },
            icon = { Icon(Icons.Default.Person, contentDescription = "个人中心") },
            label = { Text("个人中心") }
        )
    }
}
