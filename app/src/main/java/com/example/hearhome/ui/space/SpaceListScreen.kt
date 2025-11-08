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
import com.example.hearhome.data.local.User
import com.example.hearhome.ui.components.AppBottomNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            db.coupleDao(),
            currentUserId
        )
    )
    
    val mySpaces by viewModel.mySpaces.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val friendDao = remember { db.friendDao() }
    val userDao = remember { db.userDao() }

    var coupleCandidates by remember { mutableStateOf<List<User>>(emptyList()) }
    var partnerUser by remember { mutableStateOf<User?>(null) }
    var currentUser by remember { mutableStateOf<User?>(null) }
    var coupleCandidatesLoading by remember { mutableStateOf(false) }
    
    // 对话框状态
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(showCreateDialog) {
        if (showCreateDialog) {
            coupleCandidatesLoading = true
            val user = withContext(Dispatchers.IO) { userDao.getUserById(currentUserId) }
            val friends = withContext(Dispatchers.IO) { friendDao.getAcceptedFriendsWithUsers(currentUserId) }
            val candidates = friends.mapNotNull { info ->
                when (info.friend.senderId) {
                    currentUserId -> info.receiver
                    else -> if (info.friend.receiverId == currentUserId) info.sender else null
                }
            }
                .filter { it.uid != currentUserId }
                .filter { it.relationshipStatus != "in_relationship" || it.partnerId == currentUserId }
            val uniqueCandidates = candidates.associateBy { it.uid }.values.toList()

            val partner = if (user?.partnerId != null && user.partnerId != currentUserId) {
                uniqueCandidates.firstOrNull { it.uid == user.partnerId } ?: withContext(Dispatchers.IO) {
                    userDao.getUserById(user.partnerId)
                }
            } else {
                null
            }

            currentUser = user
            coupleCandidates = uniqueCandidates
            partnerUser = partner
            coupleCandidatesLoading = false
        }
    }

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
        val hasActiveCoupleSpace = mySpaces.any { it.type == "couple" }
        CreateSpaceDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, type, description, partnerId ->
                scope.launch {
                    when (val result = viewModel.createSpace(name, type, description, partnerId)) {
                        is CreateSpaceResult.Success -> {
                            showCreateDialog = false
                            val message = result.message ?: "空间创建成功"
                            snackbarHostState.showSnackbar(message)
                        }
                        is CreateSpaceResult.RequestSent -> {
                            showCreateDialog = false
                            snackbarHostState.showSnackbar(result.message)
                        }
                        is CreateSpaceResult.Failure -> {
                            snackbarHostState.showSnackbar(result.message)
                        }
                    }
                }
            },
            currentUser = currentUser,
            coupleCandidates = coupleCandidates,
            partnerUser = partnerUser,
            hasActiveCoupleSpace = hasActiveCoupleSpace,
            isLoadingCoupleCandidates = coupleCandidatesLoading
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
    onCreate: (String, String, String?, Int?) -> Unit,
    currentUser: User?,
    coupleCandidates: List<User>,
    partnerUser: User?,
    hasActiveCoupleSpace: Boolean,
    isLoadingCoupleCandidates: Boolean
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("family") }
    var description by remember { mutableStateOf("") }
    var selectedPartnerId by remember { mutableStateOf<Int?>(partnerUser?.uid) }

    val candidateList = remember(coupleCandidates, partnerUser, currentUser?.uid) {
        buildList {
            partnerUser?.let { partner ->
                if (partner.uid != currentUser?.uid) {
                    add(partner)
                }
            }
            coupleCandidates.forEach { candidate ->
                if (candidate.uid != currentUser?.uid && candidate.uid != partnerUser?.uid) {
                    add(candidate)
                }
            }
        }
    }

    LaunchedEffect(type, partnerUser?.uid) {
        if (type == "couple" && partnerUser != null) {
            selectedPartnerId = partnerUser.uid
        } else if (type != "couple") {
            selectedPartnerId = null
        }
    }

    LaunchedEffect(type, candidateList) {
        if (type == "couple" && candidateList.isEmpty()) {
            selectedPartnerId = null
        }
    }
    
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

                if (type == "couple") {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "请选择邀请的情侣成员(仅限一人)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))

                    if (hasActiveCoupleSpace) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("您已经拥有情侣空间，无法再次创建") }
                        )
                    } else if (isLoadingCoupleCandidates) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (candidateList.isEmpty()) {
                        Text(
                            "暂无可邀请的成员，请先添加好友或建立情侣关系。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            candidateList.forEach { candidate ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable { selectedPartnerId = candidate.uid }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedPartnerId == candidate.uid,
                                        onClick = { selectedPartnerId = candidate.uid }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(candidate.nickname.ifBlank { candidate.email })
                                        if (candidate.relationshipStatus == "in_relationship" && candidate.partnerId == currentUser?.uid) {
                                            Text(
                                                "当前已与您是情侣关系",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else if (candidate.relationshipStatus == "in_relationship") {
                                            Text(
                                                "对方正在情侣关系中",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(
                            name, 
                            type, 
                            description.ifBlank { null },
                            if (type == "couple") selectedPartnerId else null
                        )
                    }
                },
                enabled = name.isNotBlank() && (
                    if (type == "couple") {
                        !hasActiveCoupleSpace && !isLoadingCoupleCandidates && selectedPartnerId != null
                    } else true
                )
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
