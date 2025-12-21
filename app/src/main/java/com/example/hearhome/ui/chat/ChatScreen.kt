package com.example.hearhome.ui.chat

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.hearhome.data.local.Message
import com.example.hearhome.data.remote.ApiService
import com.example.hearhome.ui.components.AudioRecorder
import com.example.hearhome.ui.components.EmojiTextField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    currentUserId: Int,
    friendUserId: Int
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModelFactory(ApiService, application))
    val uiState by viewModel.uiState.collectAsState()
    val messages = uiState.messages
    val friendName = uiState.friendUser?.nickname ?: "好友"

    var messageText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var lastErrorMessage by remember { mutableStateOf<String?>(null) }
    var showAudioRecorder by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val locationPermissions = remember {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> selectedImageUri = uri }
    )

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                lastErrorMessage = null
                viewModel.sendLocation(currentUserId, friendUserId)
            } else {
                Toast.makeText(context, "需要定位权限才能发送位置", Toast.LENGTH_SHORT).show()
            }
        }
    )

    DisposableEffect(currentUserId, friendUserId) {
        viewModel.startPolling(currentUserId, friendUserId)
        viewModel.loadFriendDetails(friendUserId)
        onDispose { /* Stop polling if needed */ }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(index = messages.size - 1)
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMessage ->
            if (errorMessage != lastErrorMessage) {
                Toast.makeText(context, "错误: $errorMessage", Toast.LENGTH_SHORT).show()
                lastErrorMessage = errorMessage
            }
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("与 $friendName 聊天") },
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    selectedImageUri?.let { uri ->
                        Box(modifier = Modifier.padding(bottom = 8.dp).align(Alignment.Start)) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Selected image preview",
                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(
                                onClick = { selectedImageUri = null },
                                modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove image", tint = Color.White, modifier = Modifier.padding(2.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        IconButton(onClick = { photoPickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Select Photo")
                        }
                        IconButton(onClick = {
                            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (hasFine || hasCoarse) {
                                lastErrorMessage = null
                                viewModel.sendLocation(currentUserId, friendUserId)
                            } else {
                                locationPermissionLauncher.launch(locationPermissions)
                            }
                        }) {
                            Icon(Icons.Default.Place, contentDescription = "Send Location")
                        }
                        IconButton(onClick = { showAudioRecorder = !showAudioRecorder }) {
                            Icon(Icons.Default.Mic, contentDescription = "Record Audio", tint = if (showAudioRecorder) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }
                        
                        if (showAudioRecorder) {
                            Box(modifier = Modifier.weight(1f)) {
                                AudioRecorder(
                                    onAudioRecorded = { path, duration ->
                                        viewModel.sendMessage(currentUserId, friendUserId, null, null, path, duration)
                                        showAudioRecorder = false
                                    },
                                    onDismiss = { showAudioRecorder = false }
                                )
                            }
                        } else {
                            EmojiTextField(
                                modifier = Modifier.weight(1f),
                                value = messageText,
                                onValueChange = { messageText = it },
                                label = "消息",
                                placeholder = "输入消息...",
                                maxLines = 4,
                                minHeight = 50
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    lastErrorMessage = null
                                    viewModel.sendMessage(currentUserId, friendUserId, messageText, selectedImageUri)
                                    messageText = ""
                                    selectedImageUri = null
                                },
                                enabled = messageText.isNotBlank() || selectedImageUri != null
                            ) {
                                Text("发送")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading && messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages) { message ->
                    ChatMessageItem(
                        message = message,
                        currentUserId = currentUserId,
                        onImageClick = { imageUrl ->
                            fullScreenImageUrl = imageUrl
                        }
                    )
                }
            }
        }

        fullScreenImageUrl?.let { imageUrl ->
            Dialog(
                onDismissRequest = { fullScreenImageUrl = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { fullScreenImageUrl = null },
                    contentAlignment = Alignment.Center
                ) {
                    ZoomableImageView(
                        model = imageUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
