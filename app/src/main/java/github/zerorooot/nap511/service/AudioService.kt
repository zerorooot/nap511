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
import github.zerorooot.nap511.util.AudioEvent
import github.zerorooot.nap511.util.AudioEventBus

/**
 */
class AudioService : Service() {
    private lateinit var mediaSession: MediaSessionCompat
    private val videoManger: AudioGSYManager = AudioGSYManager.instance()


    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "AudioService").apply {
            // 监听系统通知栏/锁屏界面的交互回调
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    videoManger.start()
                    updateMediaState(notifyUi = true)
                }

                override fun onPause() {
                    videoManger.pause()
                    updateMediaState(notifyUi = true)
                }

                // 解决问题 2：响应系统通知栏拖拽进度条
                override fun onSeekTo(pos: Long) {
                    videoManger.seekTo(pos)
                    updateMediaState(notifyUi = true)
                }

                // 响应快退 15 秒
                override fun onRewind() {
                    videoManger.seekRelative(-SEEK_STEP_MS)
                    updateMediaState(notifyUi = true)
                }

                // 响应快进 15 秒
                override fun onFastForward() {
                    videoManger.seekRelative(SEEK_STEP_MS)
                    updateMediaState(notifyUi = true)
                }

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
                videoManger.seekRelative(-SEEK_STEP_MS)
            }


            ACTION_FAST_FORWARD -> {
                videoManger.seekRelative(SEEK_STEP_MS)
            }

            ACTION_UPDATE_STATE -> {
                // 来自 ViewModel 定时器轮询：仅更新通知栏 MediaSession，不反向通知 UI
                updateMediaState(title, notifyUi = false)
                return START_STICKY
            }

            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                AudioEventBus.sendEvent(AudioEvent.Stop)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        updateMediaState(title, notifyUi = true)
        startForeground(NOTIFICATION_ID, buildNotification(title))
        return START_STICKY
    }

    /**
     * 核心关键：同步系统 MediaSession 的状态与元数据
     * 解锁通知栏进度条拖拽，并修复按钮置灰问题
     */
    private fun updateMediaState(title: String? = null, notifyUi: Boolean) {
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
        if (notifyUi) {
            AudioEventBus.sendEvent(AudioEvent.SyncState)
        }
    }

    private fun buildNotification(title: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("nap511 音乐播放器")
            .setOngoing(true)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
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