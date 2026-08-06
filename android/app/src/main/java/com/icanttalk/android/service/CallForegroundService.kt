package com.icanttalk.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.icanttalk.android.MainActivity
import com.icanttalk.android.R

class CallForegroundService : Service() {
    private var callActive = false
    private var cameraActive = false
    private var screenActive = false
    private var roomName = "Voice room"

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CALL -> {
                callActive = true
                roomName = intent.getStringExtra(EXTRA_ROOM).orEmpty().ifBlank { "Voice room" }
            }
            ACTION_SET_CAMERA -> cameraActive = intent.getBooleanExtra(EXTRA_ENABLED, false)
            ACTION_SET_SCREEN -> screenActive = intent.getBooleanExtra(EXTRA_ENABLED, false)
            ACTION_STOP -> {
                callActive = false
                cameraActive = false
                screenActive = false
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (callActive) {
            updateForegroundNotification()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun updateForegroundNotification() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var value = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (cameraActive) value = value or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            if (screenActive) value = value or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            value
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            type,
        )
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val status = buildList {
            add(roomName)
            if (cameraActive) add("Camera")
            if (screenActive) add("Screen sharing")
        }.joinToString(" • ")

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.call_notification_title))
            .setContentText(status)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.call_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps iCANTtalk voice, camera, and screen sharing active."
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "icanttalk_active_call"
        private const val NOTIFICATION_ID = 4101
        private const val ACTION_START_CALL = "com.icanttalk.android.START_CALL"
        private const val ACTION_SET_CAMERA = "com.icanttalk.android.SET_CAMERA"
        private const val ACTION_SET_SCREEN = "com.icanttalk.android.SET_SCREEN"
        private const val ACTION_STOP = "com.icanttalk.android.STOP_CALL"
        private const val EXTRA_ROOM = "room_name"
        private const val EXTRA_ENABLED = "enabled"

        fun startCall(context: Context, roomName: String) {
            start(context, Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_START_CALL
                putExtra(EXTRA_ROOM, roomName)
            })
        }

        fun setCamera(context: Context, enabled: Boolean) {
            start(context, Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_SET_CAMERA
                putExtra(EXTRA_ENABLED, enabled)
            })
        }

        fun setScreen(context: Context, enabled: Boolean) {
            start(context, Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_SET_SCREEN
                putExtra(EXTRA_ENABLED, enabled)
            })
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }

        private fun start(context: Context, intent: Intent) {
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
