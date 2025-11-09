package com.example.hearhome.utils

import android.content.Context
import android.net.Uri
import com.example.hearhome.model.AttachmentType
import com.example.hearhome.model.PendingAttachment
import com.example.hearhome.model.ResolvedAttachment

/**
 * 附件文件处理帮助类，负责将选择的附件复制到内部存储并返回可用于数据库的路径
 */
object AttachmentFileHelper {

    /**
     * 将待处理的附件转换为内部可用的文件路径
     */
    fun resolvePendingAttachments(
        context: Context,
        attachments: List<PendingAttachment>
    ): List<ResolvedAttachment> {
        if (attachments.isEmpty()) return emptyList()

        val resolved = mutableListOf<ResolvedAttachment>()
        attachments.forEach { attachment ->
            when (attachment.type) {
                AttachmentType.IMAGE -> {
                    val path = if (attachment.fromContentUri) {
                        ImageUtils.saveImageToInternalStorage(
                            context,
                            Uri.parse(attachment.source)
                        )
                    } else {
                        attachment.source
                    }
                    if (!path.isNullOrBlank()) {
                        resolved += ResolvedAttachment(
                            type = AttachmentType.IMAGE,
                            uri = path
                        )
                    }
                }
                AttachmentType.AUDIO -> {
                    val path = if (attachment.fromContentUri) {
                        // 目前语音均来源于录音，若后续支持文件选择可在此扩展
                        attachment.source
                    } else {
                        attachment.source
                    }
                    if (!path.isNullOrBlank()) {
                        resolved += ResolvedAttachment(
                            type = AttachmentType.AUDIO,
                            uri = path,
                            duration = attachment.duration
                        )
                    }
                }
            }
        }
        return resolved
    }
}
