package com.example.hearhome.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 应用主数据库
 * 管理用户表、好友表、情侣表以及数据库升级
 */
@Database(
    entities = [User::class, Friend::class, Couple::class, Message::class],
    version = 8,  // 升级到版本 8，添加 gender 和 avatarColor 字段
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    // 各 DAO 接口
    abstract fun userDao(): UserDao
    abstract fun friendDao(): FriendDao
    abstract fun coupleDao(): CoupleDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * -------------------------------
         * 数据库迁移逻辑（防止升级后数据丢失）
         * -------------------------------
         */

        private val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE users ADD COLUMN secQuestion TEXT NOT NULL DEFAULT ''"); db.execSQL("ALTER TABLE users ADD COLUMN secAnswerHash TEXT NOT NULL DEFAULT ''") } }
        private val MIGRATION_2_3 = object : Migration(2, 3) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE users ADD COLUMN nickname TEXT NOT NULL DEFAULT ''"); db.execSQL("ALTER TABLE users ADD COLUMN relationshipStatus TEXT NOT NULL DEFAULT 'single'"); db.execSQL("ALTER TABLE users ADD COLUMN partnerId INTEGER") } }
        private val MIGRATION_3_4 = object : Migration(3, 4) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS friends (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, senderId INTEGER NOT NULL, receiverId INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'pending', createdAt INTEGER NOT NULL)") } }
        private val MIGRATION_4_5 = object : Migration(4, 5) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS couples (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, requesterId INTEGER NOT NULL, partnerId INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'pending', createdAt INTEGER NOT NULL)") } }
        private val MIGRATION_5_6 = object : Migration(5, 6) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE couples ADD COLUMN requesterRemark TEXT"); db.execSQL("ALTER TABLE couples ADD COLUMN partnerRemark TEXT") } }
        private val MIGRATION_6_7 = object : Migration(6, 7) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, senderId INTEGER NOT NULL, receiverId INTEGER NOT NULL, content TEXT NOT NULL, timestamp INTEGER NOT NULL, isRead INTEGER NOT NULL DEFAULT 0)") } }
        private val MIGRATION_7_8 = object : Migration(7, 8) { 
            override fun migrate(db: SupportSQLiteDatabase) {
                // 添加缺失的 gender 和 avatarColor 列（如果已存在会报错，但不影响）
                try {
                    db.execSQL("ALTER TABLE users ADD COLUMN gender TEXT NOT NULL DEFAULT 'Not specified'")
                } catch (e: Exception) {
                    // 列可能已存在，忽略错误
                }
                try {
                    db.execSQL("ALTER TABLE users ADD COLUMN avatarColor TEXT NOT NULL DEFAULT '#CCCCCC'")
                } catch (e: Exception) {
                    // 列可能已存在，忽略错误
                }
            } 
        }


        /**
         * 获取数据库单例
         */
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app.db" // 数据库文件名
                )
                    // 注册所有迁移
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8
                    )
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
