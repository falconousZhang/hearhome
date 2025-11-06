package com.example.hearhome.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 应用主数据库
 * 管理用户表、好友表、情侣表、消息表以及空间相关表
 */
@Database(
    entities = [
        User::class, 
        Friend::class, 
        Couple::class, 
        Message::class,
        Space::class,
        SpaceMember::class,
        SpacePost::class,
        PostLike::class,
        PostComment::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    // 各 DAO 接口
    abstract fun userDao(): UserDao
    abstract fun friendDao(): FriendDao
    abstract fun coupleDao(): CoupleDao
    abstract fun messageDao(): MessageDao
    abstract fun spaceDao(): SpaceDao
    abstract fun spacePostDao(): SpacePostDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * -------------------------------
         * 数据库迁移逻辑（防止升级后数据丢失）
         * -------------------------------
         */

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN secQuestion TEXT NOT NULL DEFAULT ''"); db.execSQL(
                    "ALTER TABLE users ADD COLUMN secAnswerHash TEXT NOT NULL DEFAULT ''"
                )
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN nickname TEXT NOT NULL DEFAULT ''"); db.execSQL(
                    "ALTER TABLE users ADD COLUMN relationshipStatus TEXT NOT NULL DEFAULT 'single'"
                ); db.execSQL("ALTER TABLE users ADD COLUMN partnerId INTEGER")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS friends (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, senderId INTEGER NOT NULL, receiverId INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'pending', createdAt INTEGER NOT NULL)")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS couples (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, requesterId INTEGER NOT NULL, partnerId INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'pending', createdAt INTEGER NOT NULL)")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE couples ADD COLUMN requesterRemark TEXT"); db.execSQL("ALTER TABLE couples ADD COLUMN partnerRemark TEXT")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, senderId INTEGER NOT NULL, receiverId INTEGER NOT NULL, content TEXT NOT NULL, timestamp INTEGER NOT NULL, isRead INTEGER NOT NULL DEFAULT 0)")
            }
        }
        
        /**
         * 迁移 7 -> 8: 添加空间相关表
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建空间表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS spaces (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        description TEXT,
                        creatorId INTEGER NOT NULL,
                        inviteCode TEXT NOT NULL,
                        coverColor TEXT NOT NULL DEFAULT '#FF9800',
                        createdAt INTEGER NOT NULL,
                        status TEXT NOT NULL DEFAULT 'active'
                    )
                """.trimIndent())
                
                // 创建空间成员表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS space_members (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        spaceId INTEGER NOT NULL,
                        userId INTEGER NOT NULL,
                        role TEXT NOT NULL DEFAULT 'member',
                        nickname TEXT,
                        joinedAt INTEGER NOT NULL,
                        status TEXT NOT NULL DEFAULT 'active'
                    )
                """.trimIndent())
                
                // 创建空间动态表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS space_posts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        spaceId INTEGER NOT NULL,
                        authorId INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        images TEXT,
                        location TEXT,
                        timestamp INTEGER NOT NULL,
                        likeCount INTEGER NOT NULL DEFAULT 0,
                        commentCount INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'normal'
                    )
                """.trimIndent())
                
                // 创建动态点赞表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS post_likes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        postId INTEGER NOT NULL,
                        userId INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
                
                // 创建动态评论表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS post_comments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        postId INTEGER NOT NULL,
                        authorId INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        replyToUserId INTEGER,
                        timestamp INTEGER NOT NULL,
                        status TEXT NOT NULL DEFAULT 'normal'
                    )
                """.trimIndent())
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
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
