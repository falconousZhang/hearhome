package com.example.hearhome.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.hearhome.data.local.AppDatabase
import kotlinx.coroutines.launch

/**
 * 空间管理界面
 * 管理员审核加入申请、管理成员
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceManageScreen(
    navController: NavController,
    spaceId: Int,
    currentUserId: Int
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    
    val viewModel: SpaceViewModel = viewModel(
        factory = SpaceViewModelFactory(
            db.spaceDao(),
            db.userDao(),
            currentUserId
        )
    )
    
    val currentSpace by viewModel.currentSpace.collectAsState()
    val spaceMembers by viewModel.spaceMembers.collectAsState()
    val pendingMembers by viewModel.pendingMembers.collectAsState()
    val currentUserRole by viewModel.currentUserRole.collectAsState()
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(spaceId) {
        viewModel.selectSpace(spaceId)
    }
    
    // 加载中状态
    if (currentUserRole == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    
    // 权限检查
    if (currentUserRole != "admin") {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("空间管理") },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.Default.ArrowBack, "返回")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "无权限访问",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "只有管理员可以管理空间",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("空间管理") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 空间信息
            item {
                currentSpace?.let { space ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "空间信息",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("名称: ${space.name}")
                            Text("类型: ${if (space.type == "couple") "情侣空间" else "家族空间"}")
                            Text("邀请码: ${space.inviteCode}")
                            Text("成员数: ${spaceMembers.size}")
                        }
                    }
                }
            }
            
            // 待审核成员
            if (pendingMembers.isNotEmpty()) {
                item {
                    Text(
                        "待审核 (${pendingMembers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(pendingMembers) { memberInfo ->
                    MemberRequestCard(
                        memberInfo = memberInfo,
                        onApprove = {
                            scope.launch {
                                viewModel.approveMember(memberInfo.member.id)
                            }
                        },
                        onReject = {
                            scope.launch {
                                viewModel.rejectMember(memberInfo.member.id)
                            }
                        }
                    )
                }
                
                item { HorizontalDivider() }
            }
            
            // 现有成员
            item {
                Text(
                    "成员列表 (${spaceMembers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            items(spaceMembers) { memberInfo ->
                MemberCard(
                    memberInfo = memberInfo,
                    currentUserId = currentUserId,
                    onRemove = {
                        scope.launch {
                            viewModel.removeMember(memberInfo.member.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MemberRequestCard(
    memberInfo: SpaceMemberInfo,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(memberInfo.user.avatarColor.toColorInt()))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = memberInfo.user.nickname,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "申请加入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            
            Row {
                IconButton(
                    onClick = onApprove,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.Check,
                        "通过",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onReject,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Close,
                        "拒绝",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun MemberCard(
    memberInfo: SpaceMemberInfo,
    currentUserId: Int,
    onRemove: () -> Unit
) {
    val isCurrentUser = memberInfo.user.uid == currentUserId
    val isAdmin = memberInfo.member.role == "admin"
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(memberInfo.user.avatarColor.toColorInt()))
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = memberInfo.user.nickname,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (isCurrentUser) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "(我)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = if (isAdmin) "管理员" else "成员",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAdmin) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.outline
                    )
                }
            }
            
            // 只有非管理员且非当前用户才显示移除按钮
            if (!isAdmin && !isCurrentUser) {
                TextButton(onClick = onRemove) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
