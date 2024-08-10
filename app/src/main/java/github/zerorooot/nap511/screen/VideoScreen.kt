package github.zerorooot.nap511.screen

import android.app.Activity
import android.graphics.PorterDuff
import android.location.LocationManager
import android.view.View
import android.widget.RatingBar
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.shuyu.gsyvideoplayer.GSYVideoManager
import com.shuyu.gsyvideoplayer.listener.GSYSampleCallBack
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import com.shuyu.gsyvideoplayer.utils.OrientationUtils
import github.zerorooot.nap511.MainActivity
import github.zerorooot.nap511.R
import github.zerorooot.nap511.activity.MyGSYVideoPlayer
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.service.VideoService
import github.zerorooot.nap511.viewmodel.FileViewModel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tv.danmaku.ijk.media.exo2.Exo2PlayerManager

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun MyVideoScreen(
    cookie: String,
    fileBean: FileBean,
    selectedItem: MutableState<String>
) {
    val videoService = VideoService.getInstance(cookie)
    val pickCode = fileBean.pickCode
    val address = "http://115.com/api/video/m3u8/${pickCode}.m3u8"
    val title = fileBean.name
    val context = LocalContext.current
    val activity = context as MainActivity


    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {

            MyGSYVideoPlayer(context).apply {
                PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)
                //设置旋转
                val orientationUtils = OrientationUtils(activity, this)

                setUp(address, false, null, mapOf("Cookie" to cookie), title)

                //增加title
                titleTextView.visibility = View.VISIBLE
                titleTextView.isSelected = true
                seekRatio = 10f

                //设置返回键
                backButton.visibility = View.VISIBLE
                setOrientationUtils(orientationUtils)
                //默认横屏
                orientationUtils.resolveByClick()
                fullscreenButton.setOnClickListener {
                    orientationUtils.resolveByClick()
                }
                val back = {
                    setVideoAllCallBack(null);
                    GSYVideoManager.releaseAllVideos()
                    orientationUtils.releaseListener()
                    GlobalScope.launch {
                        val map = hashMapOf<String, String>()
                        map["op"] = "update"
                        map["pick_code"] = pickCode
                        map["definition"] = "0"
                        map["category"] = "1"
                        map["share_id"] = "0"
                        map["time"] = (currentPositionWhenPlaying / 1000).toString()
                        videoService.history(map)
                    }
                }

//              onBackPressedDispatcher.addCallback(this){
//                    back()
//                }

                //设置返回按键功能
                backButton.setOnClickListener {
                    back()
                    activity.onBackPressedDispatcher.onBackPressed()
                }
               

                //设置竖屏
                GlobalScope.launch {
                    val videoInfo = videoService.videoInfo(pickCode)
                    val videoHeight = videoInfo.height
                    val videoWidth = videoInfo.width
                    if (videoHeight > videoWidth) {
                        orientationUtils.resolveByClick()
                    }
                }

                setVideoAllCallBack(object : GSYSampleCallBack() {
                    override fun onPlayError(url: String?, vararg objects: Any?) {
                        super.onPlayError(url, objects)
                        Toast.makeText(context, "播放失败~", Toast.LENGTH_SHORT).show()
                        back()
                        activity.onBackPressedDispatcher.onBackPressed()
                    }
                })
            }
        },
        update = {
            it.startPlayLogic()
        }
    )

}