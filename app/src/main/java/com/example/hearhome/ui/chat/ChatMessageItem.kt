package com.example.hearhome.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hearhome.data.local.Message
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatMessageItem(
    message: Message,
    currentUserId: Int
) {
    val isMyMessage = message.senderId == currentUserId
    val backgroundColor = if (isMyMessage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start

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
                if (message.content.isNotBlank()) {
                    Text(text = message.content)
                    Spacer(modifier = Modifier.height(4.dp))
                }

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
