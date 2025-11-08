package com.example.hearhome.ui.components.attachments

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.example.hearhome.model.AttachmentType
import com.example.hearhome.model.PendingAttachment
import com.example.hearhome.ui.components.AudioPlayer
import com.example.hearhome.ui.components.AudioRecorder
import com.example.hearhome.utils.AudioUtils
import com.example.hearhome.utils.TestUtils
import java.io.File

/**
 * 通用附件选择器，支持图片和语音，并提供预览和删除能力
 */
@Composable
fun AttachmentSelector(
    attachments: List<PendingAttachment>,
    onAttachmentsChange: (List<PendingAttachment>) -> Unit,
    modifier: Modifier = Modifier,
    allowImage: Boolean = true,
    allowAudio: Boolean = true,
    enableTestTools: Boolean = false,
    maxImageCount: Int = 9
) {
    val context = LocalContext.current
    var showAudioRecorder by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val currentImageCount = attachments.count { it.type == AttachmentType.IMAGE }
            val availableSlots = (maxImageCount - currentImageCount).coerceAtLeast(0)
            val picked = if (availableSlots > 0) uris.take(availableSlots) else emptyList()
            if (picked.isNotEmpty()) {
                val newAttachments = picked.map {
                    PendingAttachment(
                        type = AttachmentType.IMAGE,
                        source = it.toString(),
                        fromContentUri = true
                    )
                }
                onAttachmentsChange(attachments + newAttachments)
            }
        }
    }

    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allowImage) {
                OutlinedButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("图片")
                }
            }

            if (allowAudio) {
                OutlinedButton(onClick = { showAudioRecorder = true }) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("语音")
                }
            }

            if (enableTestTools && TestUtils.isEmulator()) {
                if (allowImage) {
                    OutlinedButton(onClick = {
                        val mockImage = TestUtils.createMockImageFile(context)
                        if (mockImage != null) {
                            onAttachmentsChange(
                                attachments + PendingAttachment(
                                    type = AttachmentType.IMAGE,
                                    source = mockImage.absolutePath,
                                    fromContentUri = false
                                )
                            )
                        }
                    }) {
                        Icon(Icons.Outlined.Science, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("测试图")
                    }
                }

                if (allowAudio) {
                    OutlinedButton(onClick = {
                        val mockAudio = TestUtils.createMockAudioFile(context)
                        if (mockAudio != null) {
                            onAttachmentsChange(
                                attachments + PendingAttachment(
                                    type = AttachmentType.AUDIO,
                                    source = mockAudio,
                                    duration = TestUtils.getMockAudioDuration(),
                                    fromContentUri = false
                                )
                            )
                        }
                    }) {
                        Icon(Icons.Outlined.Science, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("测试音")
                    }
                }
            }
        }

        val imageAttachments = attachments.filter { it.type == AttachmentType.IMAGE }
        if (imageAttachments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(imageAttachments, key = { it.id }) { attachment ->
                    Box {
                        val painterData = if (attachment.fromContentUri) {
                            Uri.parse(attachment.source)
                        } else {
                            File(attachment.source)
                        }
                        Image(
                            painter = rememberImagePainter(data = painterData),
                            contentDescription = null,
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = {
                                onAttachmentsChange(attachments.filterNot { it.id == attachment.id })
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "删除",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        val audioAttachments = attachments.filter { it.type == AttachmentType.AUDIO }
        if (audioAttachments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                audioAttachments.forEach { attachment ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AudioPlayer(
                            audioPath = attachment.source,
                            duration = attachment.duration ?: AudioUtils.getAudioDuration(attachment.source),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            onAttachmentsChange(attachments.filterNot { it.id == attachment.id })
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "删除语音")
                        }
                    }
                }
            }
        }
    }

    if (showAudioRecorder) {
        AudioRecorder(
            onAudioRecorded = { path, duration ->
                onAttachmentsChange(
                    attachments + PendingAttachment(
                        type = AttachmentType.AUDIO,
                        source = path,
                        duration = duration,
                        fromContentUri = false
                    )
                )
                showAudioRecorder = false
            },
            onDismiss = { showAudioRecorder = false }
        )
    }
}
