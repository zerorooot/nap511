package github.zerorooot.nap511.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import github.zerorooot.nap511.R
import github.zerorooot.nap511.player.AudioGSYManager

/**
 */
class AudioService : Service() {
    private lateinit var mediaSession: MediaSessionCompat
    private val videoManger: AudioGSYManager = AudioGSYManager.instance()
    /**
     * 抽取统一的相对跳转函数
     */
    private fun seekRelative(offsetMs: Long) {
        val current = videoManger.currentPosition
        val duration = videoManger.duration
        val target = (current + offsetMs).coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
        videoManger.seekTo(target)
        updateMediaState()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "AudioService").apply {
            // 监听系统通知栏/锁屏界面的交互回调
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    videoManger.start()
                    updateMediaState()
                }

                override fun onPause() {
                    videoManger.pause()
                    updateMediaState()
                }

                // 解决问题 2：响应系统通知栏拖拽进度条
                override fun onSeekTo(pos: Long) {
                    videoManger.seekTo(pos)
                    updateMediaState()
                }

                // 响应快退 15 秒
                override fun onRewind() = seekRelative(-SEEK_STEP_MS)

                // 响应快进 15 秒
                override fun onFastForward() = seekRelative(SEEK_STEP_MS)

                // 重写上一首/下一首回调，将其映射为快退/快进
                override fun onSkipToPrevious() = onRewind()
                override fun onSkipToNext() = onFastForward()
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val title = intent?.getStringExtra("title") ?: "正在播放"

        when (action) {
            ACTION_PLAY -> {
                videoManger.start()
            }

            ACTION_PAUSE -> {
                videoManger.pause()
            }

            ACTION_REWIND -> {
                seekRelative(-SEEK_STEP_MS)
            }


            ACTION_FAST_FORWARD -> {
                seekRelative(SEEK_STEP_MS)
            }

            ACTION_UPDATE_STATE -> {
                // 仅更新状态（如定时轮询进度）
                updateMediaState(title)
                return START_STICKY
            }

            ACTION_STOP -> {
                videoManger.releaseMediaPlayer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        updateMediaState(title)
        startForeground(NOTIFICATION_ID, buildNotification(title))
        return START_STICKY
    }

    /**
     * 核心关键：同步系统 MediaSession 的状态与元数据
     * 解锁通知栏进度条拖拽，并修复按钮置灰问题
     */
    private fun updateMediaState(title: String? = null) {
        val isPlaying = videoManger.isPlaying
        val currentPos = videoManger.currentPosition
        val duration = videoManger.duration

        // 1. 设置 PlaybackState：显式声明支持 SEEK_TO、PLAY、PAUSE、REWIND、FAST_FORWARD
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SEEK_TO or          // 允许拖拽进度条
                        PlaybackStateCompat.ACTION_REWIND or           // 允许快退
                        PlaybackStateCompat.ACTION_FAST_FORWARD or     // 允许快进
                        // 显式开启上一首/下一首 Action，使系统播控控件按钮取消置灰
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                currentPos,
                1.0f // 系统利用此播放速率在通知栏平滑走进度
            )
        mediaSession.setPlaybackState(stateBuilder.build())

        // 2. 设置 Metadata：告知系统音频总时长，以便系统渲染进度条的最大范围
        if (duration > 0 || title != null) {
            val metadataBuilder = MediaMetadataCompat.Builder()
            if (title != null) {
                metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            }
            if (duration > 0) {
                metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            }
            mediaSession.setMetadata(metadataBuilder.build())
        }
    }

    private fun buildNotification(title: String): Notification {
        val isPlaying = videoManger.isPlaying

        // 绑定各个按钮的 PendingIntent
        val rewindIntent = PendingIntent.getService(
            this, 1, Intent(this, AudioService::class.java).apply { action = ACTION_REWIND },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, AudioService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ffIntent = PendingIntent.getService(
            this, 3, Intent(this, AudioService::class.java).apply { action = ACTION_FAST_FORWARD },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.outline_ear_sound_24)
            .setContentTitle(title)
            .setContentText("nap511 音乐播放器")
            .setOngoing(true)
            // 按严格顺序添加按钮：[0: 快退], [1: 播放/暂停], [2: 快进]
            .addAction(
                R.drawable.outline_arrow_back_ios_24,
                "后退15秒",
                rewindIntent
            )          // Index 0
            .addAction(
                if (isPlaying) R.drawable.outline_autopause_24 else R.drawable.outline_autoplay_24,
                if (isPlaying) "暂停" else "播放",
                playPauseIntent
            )                                                                               // Index 1
            .addAction(
                R.drawable.outline_arrow_forward_ios_24,
                "快进15秒",
                ffIntent
            )            // Index 2
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    // 解决问题 1：指定折叠/小视图下显示的按钮索引（0:快退, 1:播放/暂停, 2:快进）
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "音乐播放控制",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        // 1. 统一定义跳转步长（15秒）
        private const val SEEK_STEP_MS = 15000L
        const val CHANNEL_ID = "audio_play_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_REWIND = "ACTION_REWIND"
        const val ACTION_FAST_FORWARD = "ACTION_FAST_FORWARD"
        const val ACTION_UPDATE_STATE = "ACTION_UPDATE_STATE"
        const val ACTION_STOP = "ACTION_STOP"
    }
}