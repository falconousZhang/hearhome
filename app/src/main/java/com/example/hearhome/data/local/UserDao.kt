package com.example.hearhome.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow


@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: User):Long

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE uid = :userId LIMIT 1")
    fun findById(userId: Int): Flow<User?>

    @Query("UPDATE users SET password = :newPassword WHERE email = :email")
    suspend fun updatePasswordByEmail(email: String, newPassword: String): Int

    @Query("UPDATE users SET secQuestion = :q, secAnswerHash = :aHash WHERE email = :email")
    suspend fun updateSecurityQAByEmail(email: String, q: String, aHash: String): Int

    @Query("SELECT secQuestion AS question, secAnswerHash AS answerHash FROM users WHERE email = :email LIMIT 1")
    suspend fun getSecurityQA(email: String): SecurityQA?

    data class SecurityQA(
        val question: String,
        val answerHash: String
    )
}
