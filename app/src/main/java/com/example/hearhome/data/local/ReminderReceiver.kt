package com.example.hearhome.utils

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.hearhome.R
import com.example.hearhome.data.local.Anniversary
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.data.local.SpacePost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 到点触发：
 * 1) 可选发送系统通知（有权限才发）
 * 2) 向 space_posts 插入 status="system" 的提醒动态（任何成员可删除）
 * 3) 自动排到下一年同一时刻
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ANNIVERSARY = "com.example.hearhome.action.ANNIVERSARY"
        const val CHANNEL_ID = "anniversary_channel" // 与你的 NotificationHelper 保持一致
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ANNIVERSARY) return

        val annId = intent.getIntExtra("anniversaryId", -1)
        val spaceId = intent.getIntExtra("spaceId", -1)
        val month = intent.getIntExtra("month", 0)
        val day = intent.getIntExtra("day", 1)
        val hour = intent.getIntExtra("hour", 9)
        val minute = intent.getIntExtra("minute", 0)
        if (annId <= 0 || spaceId <= 0) return

        // 1) 系统通知（可选，不影响空间插入）
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(
                10000 + annId,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("今天是你们的纪念日")
                    .setContentText("点开空间看看吧，一起留下足迹～")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
            )
        }

        // 2) 往空间发“系统提醒”帖子
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            val ann: Anniversary? = db.anniversaryDao().getById(annId)
            if (ann != null && ann.status == "active") {
                val timeText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(Date(ann.dateMillis))
                val content = "🎉 今天是『${ann.name}』（$timeText）\n一起纪念这一天吧！"

                db.spacePostDao().insert(
                    SpacePost(
                        id = 0,
                        spaceId = ann.spaceId,
                        authorId = 0,              // 系统
                        content = content,
                        images = null,
                        location = "系统提醒",
                        timestamp = System.currentTimeMillis(),
                        likeCount = 0,
                        commentCount = 0,
                        status = "system"          // 标记为系统提醒，前端放开删除
                    )
                )
            }
        }

        // 3) 自动续排到下一年
        AnniversaryReminder.scheduleNextYear(context, annId, spaceId, month, day, hour, minute)
    }
}
