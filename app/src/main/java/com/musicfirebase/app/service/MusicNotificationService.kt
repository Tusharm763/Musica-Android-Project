package com.musicfirebase.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.musicfirebase.app.PlayerActivity
import com.musicfirebase.app.R
import com.musicfirebase.app.model.Song
import androidx.core.graphics.toColorInt
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

class MusicNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "MusicNotificationService"
        const val ACTION_PLAY_PAUSE = "action_play_pause"
        const val ACTION_PREVIOUS = "action_previous"
        const val ACTION_NEXT = "action_next"
        const val ACTION_STOP = "action_stop"
    }

    private val binder = MusicBinder()
    private var notificationManager: NotificationManager? = null
    private var mediaSession: MediaSessionCompat? = null
    private var currentSong: Song? = null
    private var isPlaying = false
    private var currentPosition = 0L
    private var duration = 0L
    private var defaultAlbumArt: Bitmap? = null

    inner class MusicBinder : Binder() {
        fun getService(): MusicNotificationService = this@MusicNotificationService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializeMediaSession()
        initializeDefaultAlbumArt()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                sendBroadcast(Intent("com.musicfirebase.app.PLAY_PAUSE"))
            }
            ACTION_PREVIOUS -> {
                sendBroadcast(Intent("com.musicfirebase.app.PREVIOUS"))
            }
            ACTION_NEXT -> {
                sendBroadcast(Intent("com.musicfirebase.app.NEXT"))
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music player controls"
                setShowBadge(false)
                setSound(null, null)
            }
            notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicService")
        mediaSession?.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                sendBroadcast(Intent("com.musicfirebase.app.PLAY_PAUSE"))
            }

            override fun onPause() {
                sendBroadcast(Intent("com.musicfirebase.app.PLAY_PAUSE"))
            }

            override fun onSkipToNext() {
                sendBroadcast(Intent("com.musicfirebase.app.NEXT"))
            }

            override fun onSkipToPrevious() {
                sendBroadcast(Intent("com.musicfirebase.app.PREVIOUS"))
            }
        })
        mediaSession?.isActive = true
    }

    private fun initializeDefaultAlbumArt() {
        try {
            defaultAlbumArt = BitmapFactory.decodeResource(resources, R.drawable.ic_music_note)
            if (defaultAlbumArt == null) {
                Log.w(TAG, "Failed to decode default album art resource, creating fallback")
                defaultAlbumArt = createFallbackAlbumArt()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while loading default album art", e)
            defaultAlbumArt = createFallbackAlbumArt()
        }
    }

    private fun createFallbackAlbumArt(): Bitmap {
        val size = 128
//        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)

        val gradientPaint = Paint().apply {
            isAntiAlias = true
            shader = android.graphics.LinearGradient(
                0f, 0f, size.toFloat(), 0f,
                intArrayOf(
                    "#9C27B0".toColorInt(),
                    "#7B1FA2".toColorInt()
                ),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
        }

        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), gradientPaint)

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val centerX = size / 2f
        val centerY = size / 2f
        val radius = size / 6f

        canvas.drawCircle(centerX - radius, centerY + radius, radius, paint)

        paint.strokeWidth = 8f
        canvas.drawLine(
            centerX,
            centerY + radius,
            centerX,
            centerY - radius * 2,
            paint
        )

        return bitmap
    }

    private fun getCustomAlbumArt(): Bitmap? {
        val size = 128
//        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)

        try {
            val gradientPaint = Paint().apply {
                isAntiAlias = true
                shader = android.graphics.LinearGradient(
                    0f, 0f, size.toFloat(), 0f,
                    intArrayOf(
                        "#9C27B0".toColorInt(),
                        "#7B1FA2".toColorInt()
                    ),
                    null,
                    android.graphics.Shader.TileMode.CLAMP
                )
            }

            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), gradientPaint)

            val originalIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_music_note)
            if (originalIcon != null) {
                val whitePaint = Paint().apply {
                    isAntiAlias = true
                    colorFilter = android.graphics.PorterDuffColorFilter(
                        Color.WHITE,
                        android.graphics.PorterDuff.Mode.SRC_IN
                    )
                }

                // Scale and center the icon
                val iconSize = size * 0.6f
                val left = (size - iconSize) / 2
                val top = (size - iconSize) / 2
//                val scaledIcon = Bitmap.createScaledBitmap(
//                    originalIcon,
//                    iconSize.toInt(),
//                    iconSize.toInt(),
//                    true
//                )
                val scaledIcon = originalIcon.scale(iconSize.toInt(), iconSize.toInt())
                canvas.drawBitmap(scaledIcon, left, top, whitePaint)

                scaledIcon.recycle()
                originalIcon.recycle()
            } else {
                drawSimpleWhiteMusicNote(canvas, size)
            }

            return bitmap

        } catch (e: Exception) {
            Log.e(TAG, "Exception creating custom album art", e)
            return createFallbackAlbumArt()
        }
    }

    private fun drawSimpleWhiteMusicNote(canvas: Canvas, size: Int) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val centerX = size / 2f
        val centerY = size / 2f
        val radius = size / 6f

        canvas.drawCircle(centerX - radius, centerY + radius, radius, paint)

        paint.strokeWidth = 8f
        canvas.drawLine(
            centerX,
            centerY + radius,
            centerX,
            centerY - radius * 2,
            paint
        )
    }

    fun updateNotification(song: Song?, isPlaying: Boolean, currentPos: Long, totalDuration: Long) {
        this.currentSong = song
        this.isPlaying = isPlaying
        this.currentPosition = currentPos
        this.duration = totalDuration

        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }

    private fun createNotification(): Notification {
        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val playPauseIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MusicNotificationService::class.java).apply {
                action = ACTION_PLAY_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MusicNotificationService::class.java).apply {
                action = ACTION_PREVIOUS
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this, 2,
            Intent(this, MusicNotificationService::class.java).apply {
                action = ACTION_NEXT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = PendingIntent.getActivity(
            this, 3,
            Intent(this, PlayerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val currentTimeStr = formatTime(currentPosition)
        val totalTimeStr = formatTime(duration)
        val progressText = "$currentTimeStr / $totalTimeStr"

        val albumArt = getCustomAlbumArt()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentSong?.name ?: "Unknown Song")
            .setContentText(progressText)
            .setSubText(if (isPlaying) "Playing" else "Paused")
            .setSmallIcon(R.drawable.ic_music_note)
            .apply {
                albumArt?.let { setLargeIcon(it) }
            }
            .setContentIntent(contentIntent)
            .setDeleteIntent(
                PendingIntent.getService(
                    this, 4,
                    Intent(this, MusicNotificationService::class.java).apply {
                        action = ACTION_STOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .setOngoing(isPlaying)
            .setColor(getColor(android.R.color.white))
            .addAction(R.drawable.ic_skip_previous, "Previous", previousIntent)
            .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
            .addAction(R.drawable.ic_skip_next, "Next", nextIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession?.sessionToken)
            )
            .build()
    }

    private fun getDefaultAlbumArt(): Bitmap? {
        return getCustomAlbumArt()
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()

        defaultAlbumArt?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
        defaultAlbumArt = null
        stopForeground(true)
    }
}