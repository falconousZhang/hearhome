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
 * v13：空间表新增 checkInIntervalSeconds 字段（打卡间隔）
 *      + 新增 post_mentions 表（动态提醒功能）
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
        PostComment::class,
        PostFavorite::class,
        MediaAttachment::class,
        Anniversary::class, // v12 新增
        PostMention::class  // v13 新增
    ],
    version = 13,
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
    abstract fun postFavoriteDao(): PostFavoriteDao
    abstract fun mediaAttachmentDao(): MediaAttachmentDao
    abstract fun anniversaryDao(): AnniversaryDao // v12 新增
    abstract fun postMentionDao(): PostMentionDao // v13 新增

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN secQuestion TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE users ADD COLUMN secAnswerHash TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN nickname TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE users ADD COLUMN relationshipStatus TEXT NOT NULL DEFAULT 'single'")
                db.execSQL("ALTER TABLE users ADD COLUMN partnerId INTEGER")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS friends (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "senderId INTEGER NOT NULL, receiverId INTEGER NOT NULL, " +
                            "status TEXT NOT NULL DEFAULT 'pending', createdAt INTEGER NOT NULL)"
                )
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS couples (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "requesterId INTEGER NOT NULL, partnerId INTEGER NOT NULL, " +
                            "status TEXT NOT NULL DEFAULT 'pending', createdAt INTEGER NOT NULL)"
                )
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE couples ADD COLUMN requesterRemark TEXT")
                db.execSQL("ALTER TABLE couples ADD COLUMN partnerRemark TEXT")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS messages (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "senderId INTEGER NOT NULL, receiverId INTEGER NOT NULL, " +
                            "content TEXT NOT NULL, timestamp INTEGER NOT NULL, " +
                            "isRead INTEGER NOT NULL DEFAULT 0)"
                )
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
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
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS space_members (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        spaceId INTEGER NOT NULL,
                        userId INTEGER NOT NULL,
                        role TEXT NOT NULL DEFAULT 'member',
                        nickname TEXT,
                        joinedAt INTEGER NOT NULL,
                        status TEXT NOT NULL DEFAULT 'active'
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
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
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS post_likes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        postId INTEGER NOT NULL,
                        userId INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS post_comments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        postId INTEGER NOT NULL,
                        authorId INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        replyToUserId INTEGER,
                        timestamp INTEGER NOT NULL,
                        status TEXT NOT NULL DEFAULT 'normal'
                    )
                    """.trimIndent()
                )
            }
        }
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS post_favorites (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        userId INTEGER NOT NULL,
                        postId INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL,
                        note TEXT
                    )
                    """.trimIndent()
                )
            }
        }
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE post_comments ADD COLUMN audioPath TEXT")
                db.execSQL("ALTER TABLE post_comments ADD COLUMN audioDuration INTEGER")
            }
        }
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS media_attachments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ownerType TEXT NOT NULL,
                        ownerId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        uri TEXT NOT NULL,
                        duration INTEGER,
                        extra TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_media_attachments_ownerType_ownerId " +
                            "ON media_attachments(ownerType, ownerId)"
                )
            }
        }

        // 11 -> 12：创建纪念日表（含默认值 & 索引）
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS anniversaries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        spaceId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        dateMillis INTEGER NOT NULL,
                        style TEXT NOT NULL,
                        creatorUserId INTEGER NOT NULL,
                        status TEXT NOT NULL DEFAULT 'pending',
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_anniversaries_spaceId ON anniversaries(spaceId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_anniversaries_spaceId_status ON anniversaries(spaceId, status)"
                )
            }
        }

        // 12 -> 13：空间表新增打卡间隔字段 + 创建动态提醒表
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 空间表新增打卡间隔字段
                db.execSQL(
                    "ALTER TABLE spaces ADD COLUMN checkInIntervalSeconds INTEGER NOT NULL DEFAULT 0"
                )
                
                // 2. 创建动态提醒表
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS post_mentions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        postId INTEGER NOT NULL,
                        mentionedUserId INTEGER NOT NULL,
                        mentionerUserId INTEGER NOT NULL,
                        timeoutSeconds INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        viewedAt INTEGER,
                        lastNotifiedAt INTEGER,
                        status TEXT NOT NULL DEFAULT 'pending'
                    )
                    """.trimIndent()
                )
                // 创建索引提高查询性能
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_post_mentions_postId ON post_mentions(postId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_post_mentions_mentionedUserId ON post_mentions(mentionedUserId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_post_mentions_status ON post_mentions(mentionedUserId, status)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13
                    )
                    .fallbackToDestructiveMigration()
                    // 如需强制清库调试可打开：.fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
