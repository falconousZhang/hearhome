package com.example.hearhome.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.hearhome.R

/**
 * 通知工具类
 * 处理应用内的通知推送
 */
object NotificationHelper {
    
    // 通知渠道ID
    private const val CHANNEL_ID_DEFAULT = "hearhome_default"
    private const val CHANNEL_ID_COMMENT = "hearhome_comment"
    private const val CHANNEL_ID_LIKE = "hearhome_like"
    private const val CHANNEL_ID_SPACE = "hearhome_space"
    
    // 通知渠道名称
    private const val CHANNEL_NAME_DEFAULT = "默认通知"
    private const val CHANNEL_NAME_COMMENT = "评论通知"
    private const val CHANNEL_NAME_LIKE = "点赞通知"
    private const val CHANNEL_NAME_SPACE = "空间通知"
    
    /**
     * 创建通知渠道（Android 8.0+需要）
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 创建默认通知渠道
            val defaultChannel = NotificationChannel(
                CHANNEL_ID_DEFAULT,
                CHANNEL_NAME_DEFAULT,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "应用默认通知"
            }
            
            // 创建评论通知渠道
            val commentChannel = NotificationChannel(
                CHANNEL_ID_COMMENT,
                CHANNEL_NAME_COMMENT,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "收到新评论时的通知"
            }
            
            // 创建点赞通知渠道
            val likeChannel = NotificationChannel(
                CHANNEL_ID_LIKE,
                CHANNEL_NAME_LIKE,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "收到新点赞时的通知"
            }
            
            // 创建空间通知渠道
            val spaceChannel = NotificationChannel(
                CHANNEL_ID_SPACE,
                CHANNEL_NAME_SPACE,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "空间相关通知（加入申请、新动态等）"
            }
            
            notificationManager.createNotificationChannel(defaultChannel)
            notificationManager.createNotificationChannel(commentChannel)
            notificationManager.createNotificationChannel(likeChannel)
            notificationManager.createNotificationChannel(spaceChannel)
        }
    }
    
    /**
     * 发送评论通知
     */
    fun sendCommentNotification(
        context: Context,
        notificationId: Int,
        userName: String,
        content: String,
        postId: Int? = null
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_COMMENT)
            .setSmallIcon(android.R.drawable.ic_dialog_email) // 替换为应用图标
            .setContentTitle("$userName 评论了你")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        
        // 如果提供了postId，可以添加点击跳转到动态详情的intent
        // postId?.let {
        //     val intent = Intent(context, MainActivity::class.java).apply {
        //         putExtra("postId", it)
        //     }
        //     val pendingIntent = PendingIntent.getActivity(
        //         context, 0, intent,
        //         PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        //     )
        //     builder.setContentIntent(pendingIntent)
        // }
        
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                // 缺少通知权限
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 发送点赞通知
     */
    fun sendLikeNotification(
        context: Context,
        notificationId: Int,
        userName: String,
        contentPreview: String
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_LIKE)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("$userName 点赞了你的动态")
            .setContentText(contentPreview)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 发送空间加入申请通知
     */
    fun sendSpaceJoinRequestNotification(
        context: Context,
        notificationId: Int,
        userName: String,
        spaceName: String
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_SPACE)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("新的加入申请")
            .setContentText("$userName 申请加入空间「$spaceName」")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 发送新动态通知
     */
    fun sendNewPostNotification(
        context: Context,
        notificationId: Int,
        userName: String,
        spaceName: String,
        contentPreview: String
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_SPACE)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("$spaceName 有新动态")
            .setContentText("$userName: $contentPreview")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 发送自定义通知
     */
    fun sendCustomNotification(
        context: Context,
        notificationId: Int,
        title: String,
        content: String,
        channelId: String = CHANNEL_ID_DEFAULT
    ) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        
        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 取消通知
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
    
    /**
     * 取消所有通知
     */
    fun cancelAllNotifications(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
