package com.example.hearhome.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
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
import com.example.hearhome.data.local.Space
import com.example.hearhome.ui.components.AppBottomNavigation
import kotlinx.coroutines.launch

/**
 * 空间列表界面
 * 显示用户加入的所有空间
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceListScreen(
    navController: NavController,
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
    
    val mySpaces by viewModel.mySpaces.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 对话框状态
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("我的空间") },
                actions = {
                    IconButton(onClick = { showJoinDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "加入空间"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true }
            ) {
                Icon(Icons.Default.Add, "创建空间")
            }
        },
        bottomBar = {
            AppBottomNavigation(
                navController = navController,
                userId = currentUserId
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (mySpaces.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "暂无空间",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "点击右上角加入或创建空间",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                // 空间列表
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(mySpaces) { space ->
                        SpaceCard(
                            space = space,
                            onClick = {
                                navController.navigate("space_detail/${space.id}/$currentUserId")
                            }
                        )
                    }
                }
            }
        }
    }
    
    // 创建空间对话框
    if (showCreateDialog) {
        CreateSpaceDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, type, description ->
                scope.launch {
                    val success = viewModel.createSpace(name, type, description) != null
                    if (success) {
                        showCreateDialog = false
                    }
                }
            }
        )
    }
    
    // 加入空间对话框
    if (showJoinDialog) {
        JoinSpaceDialog(
            onDismiss = { showJoinDialog = false },
            onJoin = { inviteCode ->
                scope.launch {
                    when (val result = viewModel.joinSpaceByCode(inviteCode)) {
                        is JoinSpaceResult.Joined -> {
                            showJoinDialog = false
                            val message = result.message ?: "成功加入「${result.space.name}」"
                            snackbarHostState.showSnackbar(message)
                        }
                        is JoinSpaceResult.RequestPending -> {
                            showJoinDialog = false
                            snackbarHostState.showSnackbar(result.message)
                        }
                        is JoinSpaceResult.Failure -> {
                            snackbarHostState.showSnackbar(result.message)
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun SpaceCard(
    space: Space,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 空间图标
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(space.coverColor.toColorInt())),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            // 空间信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = space.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (space.type) {
                        "couple" -> "情侣空间"
                        "family" -> "家族空间"
                        else -> "未知类型"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (space.description != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = space.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun CreateSpaceDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("family") }
    var description by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建空间") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("空间名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(8.dp))
                
                // 空间类型选择
                Text("空间类型:", style = MaterialTheme.typography.bodySmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = type == "couple",
                        onClick = { type = "couple" },
                        label = { Text("情侣空间") }
                    )
                    FilterChip(
                        selected = type == "family",
                        onClick = { type = "family" },
                        label = { Text("家族空间") }
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("空间描述(可选)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(
                            name, 
                            type, 
                            description.ifBlank { null }
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun JoinSpaceDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var inviteCode by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入空间") },
        text = {
            Column {
                Text(
                    "请输入6位邀请码",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { 
                        if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                            inviteCode = it
                        }
                    },
                    label = { Text("邀请码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (inviteCode.length == 6) {
                        onJoin(inviteCode)
                    }
                },
                enabled = inviteCode.length == 6
            ) {
                Text("加入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
