package github.zerorooot.nap511.player

import com.shuyu.gsyvideoplayer.GSYVideoBaseManager

/*
不复用GSYVideoManager，因为视频也用了。容易产生冲突
 */
class AudioGSYManager private constructor() : GSYVideoBaseManager() {
    init {
        // 核心修复点：必须调用父类的 init() 来初始化内部 HandlerThread 和 mMediaHandler！
        init()
    }

    override fun getPlayTag(): String {
        // 返回一个唯一的 Tag，避免与视频播放器 Tag 冲突
        return TAG
    }

    /**
     * 抽取统一的相对跳转函数
     */
    fun seekRelative(offsetMs: Long) {
        val current = this.currentPosition
        val duration = this.duration
        val target =
            (current + offsetMs).coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
        this.seekTo(target)
    }

    fun setVolume(volume: Float) {
        try {
            // GSYVideoBaseManager 通常通过此方法获取当前的播放内核
            curPlayerManager?.setVolume(volume, volume)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val TAG = "AudioGSYManager"

        @Volatile
        private var instance: AudioGSYManager? = null

        fun instance(): AudioGSYManager {
            if (instance == null) {
                synchronized(AudioGSYManager::class.java) {
                    if (instance == null) {
                        instance = AudioGSYManager()
                    }
                }
            }
            return instance!!
        }
    }
}