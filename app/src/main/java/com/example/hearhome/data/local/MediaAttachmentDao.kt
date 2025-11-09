package com.example.hearhome.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 通用媒体附件 DAO
 */
@Dao
interface MediaAttachmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: MediaAttachment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<MediaAttachment>)

    @Query(
        "SELECT * FROM media_attachments WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt ASC"
    )
    fun observeAttachments(ownerType: String, ownerId: Int): Flow<List<MediaAttachment>>

    @Query(
        "SELECT * FROM media_attachments WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt ASC"
    )
    suspend fun getAttachments(ownerType: String, ownerId: Int): List<MediaAttachment>

    @Query(
        "SELECT * FROM media_attachments WHERE ownerType = :ownerType AND ownerId IN (:ownerIds)"
    )
    suspend fun getAttachmentsForOwners(ownerType: String, ownerIds: List<Int>): List<MediaAttachment>

    @Query(
        "SELECT * FROM media_attachments WHERE ownerType = :ownerType AND ownerId IN (:ownerIds)"
    )
    fun observeAttachmentsForOwners(ownerType: String, ownerIds: List<Int>): Flow<List<MediaAttachment>>

    @Query(
        "DELETE FROM media_attachments WHERE ownerType = :ownerType AND ownerId = :ownerId"
    )
    suspend fun deleteAttachments(ownerType: String, ownerId: Int)
}
