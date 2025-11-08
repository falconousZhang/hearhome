package com.example.hearhome.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberImagePainter
import com.example.hearhome.model.AttachmentType
import com.example.hearhome.ui.components.AudioPlayer
import com.example.hearhome.utils.AudioUtils
import java.text.SimpleDateFormat
import java.util.*
import java.io.File

@Composable
fun ChatMessageItem(
    message: MessageWithAttachments,
    currentUserId: Int
) {
    val messageEntity = message.message
    val isMyMessage = messageEntity.senderId == currentUserId
    val alignment = if (isMyMessage) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (isMyMessage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start

    val imageAttachments = message.attachments.filter {
        AttachmentType.fromStorage(it.type) == AttachmentType.IMAGE
    }
    val audioAttachments = message.attachments.filter {
        AttachmentType.fromStorage(it.type) == AttachmentType.AUDIO
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = horizontalArrangement
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            Column {
                if (messageEntity.content.isNotBlank()) {
                    Text(text = messageEntity.content)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (imageAttachments.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(imageAttachments, key = { it.id }) { attachment ->
                            val imageFile = File(attachment.uri)
                            if (imageFile.exists()) {
                                Image(
                                    painter = rememberImagePainter(imageFile),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(width = 140.dp, height = 120.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (audioAttachments.isNotEmpty()) {
                    audioAttachments.forEach { attachment ->
                        AudioPlayer(
                            audioPath = attachment.uri,
                            duration = attachment.duration ?: AudioUtils.getAudioDuration(attachment.uri),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                Text(
                    text = formatTimestamp(messageEntity.timestamp),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
