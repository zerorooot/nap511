package github.zerorooot.nap511.activity

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.listener.GSYSampleCallBack
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import com.shuyu.gsyvideoplayer.utils.OrientationUtils
import github.zerorooot.nap511.R
import github.zerorooot.nap511.service.VideoService
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tv.danmaku.ijk.media.exo2.Exo2PlayerManager
import kotlin.concurrent.thread


class VideoActivity : AppCompatActivity() {
    private var orientationUtils: OrientationUtils? = null
    private lateinit var videoPlayer: MyGSYVideoPlayer
    private lateinit var videoService: VideoService
    private lateinit var cookie: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)
        val pickCode = intent.getStringExtra("pick_code")!!
        val address = "http://115.com/api/video/m3u8/${pickCode}.m3u8"
        val title = intent.getStringExtra("title")
        cookie = intent.getStringExtra("cookie")!!
        videoService = VideoService.getInstance(cookie)

        videoPlayer = findViewById(R.id.pre_video_player)

        PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)

        //设置旋转
        orientationUtils = OrientationUtils(this, videoPlayer)


        videoPlayer.setUp(address, false, null, mapOf("Cookie" to cookie), title)

        //增加title
        videoPlayer.titleTextView.visibility = View.VISIBLE
        videoPlayer.titleTextView.isSelected = true
        videoPlayer.seekRatio = 10f

        //设置返回键
        videoPlayer.backButton.visibility = View.VISIBLE
        videoPlayer.setOrientationUtils(orientationUtils)


        videoPlayer.fullscreenButton.setOnClickListener {
            orientationUtils!!.resolveByClick()
        }


        //设置返回按键功能
        videoPlayer.backButton.setOnClickListener {
            back()
        }

        videoPlayer.startPlayLogic()

        if (DataStoreUtil.getData(ConfigKeyUtil.AUTO_ROTATE, false)) {
            //设置竖屏
            lifecycleScope.launch {
                val videoInfo = videoService.videoInfo(pickCode)
                val videoHeight = videoInfo.height
                val videoWidth = videoInfo.width
                if (videoHeight > videoWidth) {
                    orientationUtils!!.resolveByClick()
                }
            }
        }


        videoPlayer.setVideoAllCallBack(object : GSYSampleCallBack() {
            override fun onPlayError(url: String?, vararg objects: Any?) {
                super.onPlayError(url, objects)
                Toast.makeText(baseContext, "播放失败~", Toast.LENGTH_SHORT).show()
                finish()
            }
        })

        onBackPressedDispatcher.addCallback(this) {
            back()
        }
    }

    override fun onPause() {
        super.onPause()
        videoPlayer.onVideoPause()
    }

    override fun onResume() {
        super.onResume()
        videoPlayer.onVideoResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateTime()
        GSYVideoManager.releaseAllVideos()
        if (orientationUtils != null) orientationUtils!!.releaseListener()
    }

    private fun updateTime() {
        thread {
            val formBody = FormBody.Builder()
            formBody.add("op", "update")
            formBody.add("pick_code", intent.getStringExtra("pick_code")!!)
            formBody.add("definition", "0")
            formBody.add("category", "1")
            formBody.add("share_id", "0")
            formBody.add("time", (videoPlayer.currentPositionWhenPlaying / 1000).toString())
            val url = "https://115vod.com/webapi/files/history"
            val okHttpClient = OkHttpClient()
            val request: Request = Request.Builder().url(url)
                .addHeader("cookie", cookie)
                .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
                ).post(formBody.build()).build()
            okHttpClient.newCall(request).execute()
        }
    }

    private fun back() {
        updateTime()
        //释放所有
        videoPlayer.setVideoAllCallBack(null);
        finish()
    }


}