package com.example.hearhome.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [User::class],
    version = 2,             // 数据库版本升级到 2
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // [新] 数据库迁移：从版本 1 升级到 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 为 'users' 表添加安全问题和答案哈希列
                db.execSQL("ALTER TABLE users ADD COLUMN secQuestion TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE users ADD COLUMN secAnswerHash TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app.db" // 你的数据库名称
                )
                    .addMigrations(MIGRATION_1_2) // [新] 添加迁移
                    .build()
                    .also { INSTANCE = it }
            }
    }
}