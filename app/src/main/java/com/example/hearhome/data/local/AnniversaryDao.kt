package com.example.hearhome.data.local

import androidx.room.*

@Dao
interface AnniversaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(anniversary: Anniversary): Long

    @Update
    suspend fun update(anniversary: Anniversary)

    @Delete
    suspend fun delete(anniversary: Anniversary)

    @Query("SELECT * FROM anniversaries WHERE spaceId = :spaceId ORDER BY dateMillis ASC")
    suspend fun listBySpace(spaceId: Int): List<Anniversary>

    @Query("SELECT * FROM anniversaries WHERE spaceId = :spaceId AND status = 'active' ORDER BY dateMillis ASC")
    suspend fun listActive(spaceId: Int): List<Anniversary>

    @Query("UPDATE anniversaries SET status = 'active' WHERE id = :id")
    suspend fun confirm(id: Int)

    @Query("SELECT * FROM anniversaries WHERE id = :id")
    suspend fun getById(id: Int): Anniversary?
}
