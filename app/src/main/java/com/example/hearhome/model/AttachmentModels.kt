package com.example.hearhome.model

import java.util.UUID

/**
 * 通用的媒体附件类型
 */
enum class AttachmentType {
    IMAGE,
    AUDIO;

    companion object {
        fun fromStorage(value: String): AttachmentType = try {
            valueOf(value)
        } catch (_: IllegalArgumentException) {
            IMAGE
        }
    }
}

/**
 * 待持久化的附件信息，包括来源和元数据
 */
data class PendingAttachment(
    val id: String = UUID.randomUUID().toString(),
    val type: AttachmentType,
    val source: String,
    val duration: Long? = null,
    val fromContentUri: Boolean = false
)

/**
 * 已经转换为应用内部存储的附件
 */
data class ResolvedAttachment(
    val type: AttachmentType,
    val uri: String,
    val duration: Long? = null
)
