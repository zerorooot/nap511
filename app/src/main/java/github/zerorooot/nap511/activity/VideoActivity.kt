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
import tv.danmaku.ijk.media.exo2.Exo2PlayerManager


class VideoActivity : AppCompatActivity() {
    private var orientationUtils: OrientationUtils? = null
    private lateinit var videoPlayer: MyGSYVideoPlayer

    private lateinit var videoService: VideoService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)
        val pickCode = intent.getStringExtra("pick_code")!!
        val address = "http://115.com/api/video/m3u8/${pickCode}.m3u8"
        val title = intent.getStringExtra("title")
        val cookie = intent.getStringExtra("cookie")!!
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

        if (DataStoreUtil.getData(ConfigKeyUtil.AUTO_ROTATE, false)){
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

        onBackPressedDispatcher.addCallback(this){
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
        lifecycleScope.launch {
            val map = hashMapOf<String, String>()
            map["op"] = "update"
            map["pick_code"] = intent.getStringExtra("pick_code")!!
            map["definition"] = "0"
            map["category"] = "1"
            map["share_id"] = "0"
            map["time"] = (videoPlayer.currentPositionWhenPlaying / 1000).toString()
            videoService.history(map)
        }
    }

    private fun back() {
        updateTime()
        //释放所有
        videoPlayer.setVideoAllCallBack(null);
        finish()
    }


}