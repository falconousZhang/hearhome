package com.example.hearhome.ui.space

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.data.local.Space
import com.example.hearhome.data.local.SpaceMember
import com.example.hearhome.data.local.User
import kotlinx.coroutines.launch

/**
 * 空间信息界面
 * 显示空间的详细信息和成员列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceInfoScreen(
    navController: NavController,
    spaceId: Int,
    currentUserId: Int
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)

    var space by remember { mutableStateOf<Space?>(null) }
    var members by remember { mutableStateOf<List<SpaceMember>>(emptyList()) }
    var users by remember { mutableStateOf<Map<Int, User>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(spaceId) {
        scope.launch {
            try {
                // 获取空间信息
                space = db.spaceDao().getSpaceById(spaceId)
                // 获取成员列表
                members = db.spaceDao().getSpaceMembers(spaceId)
                // 获取所有成员的用户信息
                val userIds = members.map { it.userId }
                val userList = userIds.mapNotNull { userId ->
                    db.userDao().getUserById(userId)
                }
                users = userList.associateBy { it.uid }
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("空间信息") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (space == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("空间不存在")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 空间基本信息
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "基本信息",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            
                            InfoRow(label = "空间名称", value = space!!.name)
                            InfoRow(label = "空间类型", value = when (space!!.type) {
                                "couple" -> "双人空间"
                                "family" -> "家庭空间"
                                else -> "未知类型"
                            })
                            InfoRow(label = "邀请码", value = space!!.inviteCode)
                            InfoRow(label = "创建时间", value = 
                                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                    .format(java.util.Date(space!!.createdAt))
                            )
                            InfoRow(label = "成员数量", value = "${members.size}人")
                        }
                    }
                }

                // 成员列表
                item {
                    Text(
                        text = "成员列表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(members) { member ->
                    val user = users[member.userId]
                    if (user != null) {
                        MemberCard(member, user)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MemberCard(member: SpaceMember, user: User) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = user.nickname.ifEmpty { user.email },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = when (member.role) {
                        "admin" -> "管理员"
                        "member" -> "成员"
                        else -> "未知角色"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (member.status != "approved") {
                Badge {
                    Text(
                        when (member.status) {
                            "pending" -> "待审核"
                            "rejected" -> "已拒绝"
                            else -> member.status
                        }
                    )
                }
            }
        }
    }
}
