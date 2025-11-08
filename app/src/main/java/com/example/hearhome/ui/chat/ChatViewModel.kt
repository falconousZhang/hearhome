package com.example.hearhome.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.AttachmentOwnerType
import com.example.hearhome.data.local.MediaAttachment
import com.example.hearhome.data.local.MediaAttachmentDao
import com.example.hearhome.data.local.Message
import com.example.hearhome.data.local.MessageDao
import com.example.hearhome.model.AttachmentType
import com.example.hearhome.model.ResolvedAttachment
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val messageDao: MessageDao,
    private val mediaAttachmentDao: MediaAttachmentDao,
    private val currentUserId: Int,
    private val friendUserId: Int
) : ViewModel() {

    val messages: StateFlow<List<MessageWithAttachments>> = 
        messageDao.getMessagesBetweenUsers(currentUserId, friendUserId)
            .flatMapLatest { messageList ->
                if (messageList.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    mediaAttachmentDao
                        .observeAttachmentsForOwners(
                            AttachmentOwnerType.CHAT_MESSAGE,
                            messageList.map { it.id }
                        )
                        .mapLatest { attachments ->
                            val attachmentsMap = attachments.groupBy { it.ownerId }
                            messageList.map { message ->
                                MessageWithAttachments(
                                    message = message,
                                    attachments = attachmentsMap[message.id].orEmpty()
                                )
                            }
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun sendMessage(content: String, attachments: List<ResolvedAttachment> = emptyList()) {
        viewModelScope.launch {
            val fallbackContent = when {
                attachments.any { it.type == AttachmentType.AUDIO } -> "[语音消息]"
                attachments.any { it.type == AttachmentType.IMAGE } -> "[图片]"
                else -> ""
            }
            val messageContent = if (content.isNotBlank()) content else fallbackContent
            val message = Message(
                senderId = currentUserId,
                receiverId = friendUserId,
                content = messageContent,
                timestamp = System.currentTimeMillis(),
                isRead = false
            )
            val messageId = messageDao.insertMessage(message).toInt()

            if (messageId > 0 && attachments.isNotEmpty()) {
                val entities = attachments.map {
                    MediaAttachment(
                        ownerType = AttachmentOwnerType.CHAT_MESSAGE,
                        ownerId = messageId,
                        type = it.type.name,
                        uri = it.uri,
                        duration = it.duration
                    )
                }
                mediaAttachmentDao.insertAttachments(entities)
            }
        }
    }
}

data class MessageWithAttachments(
    val message: Message,
    val attachments: List<MediaAttachment>
)