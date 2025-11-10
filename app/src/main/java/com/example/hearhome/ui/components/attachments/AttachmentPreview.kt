package com.example.hearhome.ui.components.attachments

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.hearhome.data.local.MediaAttachment
import com.example.hearhome.model.AttachmentType
import com.example.hearhome.ui.components.AudioPlayer
import com.example.hearhome.utils.AudioUtils
import java.io.File

/**
 * 通用附件展示组件，支持图片与语音附件。
 * 该组件用于将空间动态、评论与聊天模块的附件展示逻辑解耦。
 */
@Composable
fun AttachmentGallery(
    attachments: List<MediaAttachment>,
    modifier: Modifier = Modifier,
    legacyImagePaths: List<String> = emptyList(),
    imageWidth: Dp? = null,
    imageHeight: Dp = 150.dp,
    cornerRadius: Dp = 8.dp,
    imageSpacing: Dp = 8.dp
) {
    val imageAttachments = attachments.filter {
        AttachmentType.fromStorage(it.type) == AttachmentType.IMAGE
    }
    val legacyPaths = legacyImagePaths.filter { it.isNotBlank() }
    if (imageAttachments.isEmpty() && legacyPaths.isEmpty()) {
        return
    }
    
    var selectedImageData by remember { mutableStateOf<Any?>(null) }

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(imageSpacing)
    ) {
        items(imageAttachments, key = { it.id }) { attachment ->
            val imageData = resolveCoilData(attachment.uri)
            AttachmentImageItem(
                data = imageData,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                cornerRadius = cornerRadius,
                onClick = { selectedImageData = imageData }
            )
        }
        if (legacyPaths.isNotEmpty()) {
            items(legacyPaths) { path ->
                val imageData = resolveCoilData(path)
                AttachmentImageItem(
                    data = imageData,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    cornerRadius = cornerRadius,
                    onClick = { selectedImageData = imageData }
                )
            }
        }
    }
    
    // 全屏图片查看器
    if (selectedImageData != null) {
        ImageViewerDialog(
            imageData = selectedImageData!!,
            onDismiss = { selectedImageData = null }
        )
    }
}

@Composable
fun AttachmentAudioList(
    attachments: List<MediaAttachment>,
    modifier: Modifier = Modifier,
    audioSpacing: Dp = 8.dp,
    audioItemModifier: Modifier = Modifier
) {
    val audioAttachments = attachments.filter {
        AttachmentType.fromStorage(it.type) == AttachmentType.AUDIO
    }
    if (audioAttachments.isEmpty()) {
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(audioSpacing)
    ) {
        audioAttachments.forEach { attachment ->
            val duration = attachment.duration ?: AudioUtils.getAudioDuration(attachment.uri)
            AudioPlayer(
                audioPath = attachment.uri,
                duration = duration,
                modifier = audioItemModifier
            )
        }
    }
}

@Composable
private fun AttachmentImageItem(
    data: Any,
    imageWidth: Dp?,
    imageHeight: Dp,
    cornerRadius: Dp,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val modifier = if (imageWidth != null) {
        Modifier
            .size(width = imageWidth, height = imageHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick)
    } else {
        Modifier
            .height(imageHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick)
    }
    
    // 调试日志
    Log.d("AttachmentImage", "Loading image: $data (type: ${data::class.simpleName})")
    
    // 使用 SubcomposeAsyncImage 支持自定义 Composable 内容
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(data)
            .crossfade(true)
            .listener(
                onStart = { 
                    Log.d("AttachmentImage", "Start loading: $data")
                },
                onSuccess = { _, _ -> 
                    Log.d("AttachmentImage", "Success loading: $data")
                },
                onError = { _, result -> 
                    Log.e("AttachmentImage", "Error loading: $data", result.throwable)
                }
            )
            .build(),
        contentDescription = "图片",
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = {
            // 加载占位符
            Box(
                modifier = Modifier.fillMaxSize().background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        },
        error = {
            // 错误占位符
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                Text("❌", fontSize = 24.sp, color = Color.White)
            }
        }
    )
}

/**
 * 全屏图片查看器对话框
 * 支持缩放、拖动手势
 */
@Composable
private fun ImageViewerDialog(
    imageData: Any,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 图片缩放和拖动状态
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            
            val state = rememberTransformableState { zoomChange, offsetChange, _ ->
                scale = (scale * zoomChange).coerceIn(0.5f, 5f)
                offset += offsetChange
            }
            
            // 可缩放拖动的图片
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageData)
                    .crossfade(true)
                    .build(),
                contentDescription = "全屏图片",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state = state),
                contentScale = ContentScale.Fit,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            )
            
            // 关闭按钮
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

private fun resolveCoilData(path: String): Any {
    return when {
        path.startsWith("content://") || path.startsWith("file://") -> Uri.parse(path)
        path.startsWith("/") -> File(path)
        else -> path
    }
}
