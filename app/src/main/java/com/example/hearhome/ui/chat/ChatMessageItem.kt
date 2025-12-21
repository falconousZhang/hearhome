package com.example.hearhome.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.hearhome.data.local.Message
import com.example.hearhome.ui.components.AudioPlayer
import android.content.Intent
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.*

private data class LocationPayload(val lat: Double, val lon: Double, val name: String)

private fun parseLocationContent(content: String?): LocationPayload? {
    if (content.isNullOrBlank()) return null
    if (!content.startsWith("LOCATION:")) return null
    val body = content.removePrefix("LOCATION:")
    val parts = body.split("|")
    if (parts.isEmpty()) return null
    val coords = parts[0].split(",")
    if (coords.size != 2) return null
    val lat = coords[0].toDoubleOrNull() ?: return null
    val lon = coords[1].toDoubleOrNull() ?: return null
    val name = if (parts.size > 1) parts[1] else "位置"
    return LocationPayload(lat, lon, name)
}

@Composable
fun ChatMessageItem(
    message: Message,
    currentUserId: Int,
    onImageClick: (String) -> Unit
) {
    val isMyMessage = message.senderId == currentUserId
    val backgroundColor =
        if (isMyMessage) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.secondaryContainer

    val horizontalArrangement =
        if (isMyMessage) Arrangement.End else Arrangement.Start

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

                val locationPayload = parseLocationContent(message.content)
                val context = LocalContext.current

                // ✅ 显示位置消息
                if (locationPayload != null) {
                    Text(text = locationPayload.name, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "点击查看地图", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable {
                        val url = "https://uri.amap.com/marker?position=${locationPayload.lon},${locationPayload.lat}&name=${Uri.encode(locationPayload.name)}"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    })
                    Spacer(modifier = Modifier.height(4.dp))
                } else if (!message.content.isNullOrBlank()) {
                    Text(text = message.content!!)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // ✅ 显示图片消息
                if (!message.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = "Image Message",
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onImageClick(message.imageUrl!!) },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // ✅ 显示语音消息
                if (!message.audioUrl.isNullOrBlank() && message.audioDuration != null) {
                    AudioPlayer(
                        audioPath = message.audioUrl!!,
                        duration = message.audioDuration,
                        modifier = Modifier.width(160.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // 时间戳
                Text(
                    text = formatTimestamp(message.timestamp),
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
