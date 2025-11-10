package com.example.hearhome.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * 纪念日闹钟工具：支持精确到分钟的触发，并且在触发后自动排到下一年同一时刻。
 */
object AnniversaryReminder {

    /** 安排“今年或明年的最近一次”精确提醒（到分钟）。 */
    fun scheduleYearlyExact(
        context: Context,
        anniversaryId: Int,
        spaceId: Int,
        month: Int,   // Calendar.JANUARY..DECEMBER
        day: Int,
        hour: Int,
        minute: Int
    ) {
        val triggerAt = nextTriggerMillis(month, day, hour, minute)
        setExactAt(context, anniversaryId, spaceId, month, day, hour, minute, triggerAt)
    }

    /** Receiver 触发后，排下一年同一时刻。 */
    fun scheduleNextYear(
        context: Context,
        anniversaryId: Int,
        spaceId: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ) {
        val next = Calendar.getInstance().apply {
            add(Calendar.YEAR, 1)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        setExactAt(context, anniversaryId, spaceId, month, day, hour, minute, next)
    }

    /** 在指定毫秒时间精确触发（兼容 Doze）。 */
    private fun setExactAt(
        context: Context,
        anniversaryId: Int,
        spaceId: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        triggerAtMillis: Long
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context, anniversaryId, spaceId, month, day, hour, minute)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun nextTriggerMillis(month: Int, day: Int, hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            set(Calendar.YEAR, now.get(Calendar.YEAR))
        }
        if (target.timeInMillis <= now.timeInMillis) target.add(Calendar.YEAR, 1)
        return target.timeInMillis
    }

    private fun buildPendingIntent(
        context: Context,
        anniversaryId: Int,
        spaceId: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_ANNIVERSARY
            putExtra("anniversaryId", anniversaryId)
            putExtra("spaceId", spaceId)
            putExtra("month", month)
            putExtra("day", day)
            putExtra("hour", hour)
            putExtra("minute", minute)
        }
        return PendingIntent.getBroadcast(
            context,
            anniversaryId, // requestCode：按纪念日稳定
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
