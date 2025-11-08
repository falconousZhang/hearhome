package com.example.hearhome.ui.components.attachments

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
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

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(imageSpacing)
    ) {
        items(imageAttachments, key = { it.id }) { attachment ->
            AttachmentImageItem(
                data = resolveCoilData(attachment.uri),
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                cornerRadius = cornerRadius
            )
        }
        if (legacyPaths.isNotEmpty()) {
            items(legacyPaths) { path ->
                AttachmentImageItem(
                    data = resolveCoilData(path),
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    cornerRadius = cornerRadius
                )
            }
        }
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
    cornerRadius: Dp
) {
    val modifier = if (imageWidth != null) {
        Modifier
            .size(width = imageWidth, height = imageHeight)
            .clip(RoundedCornerShape(cornerRadius))
    } else {
        Modifier
            .height(imageHeight)
            .clip(RoundedCornerShape(cornerRadius))
    }
    Image(
        painter = rememberImagePainter(data = data),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
    if (imageWidth == null) {
        Spacer(modifier = Modifier)
    }
}

private fun resolveCoilData(path: String): Any {
    return when {
        path.startsWith("content://") || path.startsWith("file://") -> Uri.parse(path)
        path.startsWith("/") -> File(path)
        else -> path
    }
}
