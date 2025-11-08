package com.example.hearhome.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.model.PendingAttachment
import com.example.hearhome.ui.components.EmojiTextField
import com.example.hearhome.ui.components.attachments.AttachmentSelector
import com.example.hearhome.utils.AttachmentFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    currentUserId: Int,
    friendUserId: Int
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(
            db.messageDao(),
            db.mediaAttachmentDao(),
            currentUserId,
            friendUserId
        )
    )

    val messages by viewModel.messages.collectAsState()
    var messageText by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<PendingAttachment>>(emptyList()) }
    var friendNickname by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(friendUserId) {
        val friend = db.userDao().getUserById(friendUserId)
        friendNickname = friend?.nickname ?: "用户"
    }

    // 当消息列表变化时，自动滚动到底部
    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(index = messages.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("与 $friendNickname 聊天中") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 6.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    EmojiTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        label = "消息",
                        placeholder = "输入消息...",
                        maxLines = 4,
                        minHeight = 70
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    AttachmentSelector(
                        attachments = attachments,
                        onAttachmentsChange = { attachments = it },
                        enableTestTools = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (messageText.isNotBlank() || attachments.isNotEmpty()) {
                                    coroutineScope.launch {
                                        val resolved = withContext(Dispatchers.IO) {
                                            AttachmentFileHelper.resolvePendingAttachments(
                                                context = context,
                                                attachments = attachments
                                            )
                                        }
                                        val trimmed = messageText.trim()
                                        viewModel.sendMessage(trimmed, resolved)
                                        messageText = ""
                                        attachments = emptyList()
                                    }
                                }
                            },
                            enabled = messageText.isNotBlank() || attachments.isNotEmpty()
                        ) {
                            Text("发送")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(messages) { message ->
                ChatMessageItem(message = message, currentUserId = currentUserId)
            }
        }
    }
}
