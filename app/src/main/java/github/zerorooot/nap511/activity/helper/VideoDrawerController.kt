package github.zerorooot.nap511.activity.helper

import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import github.zerorooot.nap511.R
import github.zerorooot.nap511.adapter.VideoOptionAdapter
import github.zerorooot.nap511.player.MyGSYVideoPlayer
import github.zerorooot.nap511.viewmodel.VideoViewModel

class VideoDrawerController(
    private val context: Context,
    private val videoPlayer: MyGSYVideoPlayer,
    private val viewModel: VideoViewModel,
    private val subtitlePanelController: SubtitlePanelController
) {
    private val episodeAdapter: VideoOptionAdapter
    private val speedAdapter: VideoOptionAdapter
    private val scaleAdapter: VideoOptionAdapter

    init {
        val rvDrawer = videoPlayer.findViewById<RecyclerView>(R.id.rv_drawer)
        if (rvDrawer != null) {
            rvDrawer.layoutManager = LinearLayoutManager(context)
        }

        // 1. 选集适配器
        val episodeTitles = viewModel.videoList.mapIndexed { index, item ->
            val displayIndex = index + 1
            if (item.name.isNotEmpty()) {
                "P$displayIndex  ${item.name}"
            } else {
                "第 $displayIndex 集"
            }
        }
        episodeAdapter = VideoOptionAdapter(
            options = episodeTitles,
            selectedIndex = viewModel.uiState.value.fileBeanIndex
        ) { index, _ ->
            val isPortrait =
                context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            viewModel.playVideoAtIndex(
                index,
                isPortrait,
                videoPlayer.currentPositionWhenPlaying
            )
            videoPlayer.hideAllDrawers()
        }

        // 2. 倍速适配器
        val speedValues =
            floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 3.0f)
        val speedTitles = listOf(
            "0.5X", "0.75X", "1.0X", "1.25X", "1.5X", "1.75X", "2.0X", "2.25X", "2.5X", "3.0X"
        )
        val defaultSpeedIndex = 2
        speedAdapter = VideoOptionAdapter(speedTitles, defaultSpeedIndex) { index, _ ->
            val speedVal = speedValues[index]
            videoPlayer.currentPlayer.setSpeed(speedVal, true)
            videoPlayer.setSpeedText(if (speedVal == 1.0f) "倍速" else "${speedVal}X")
            videoPlayer.hideAllDrawers()
        }

        // 3. 画面比例适配器
        val scaleTypes = intArrayOf(0, 1, 2, 3, 4)
        val scaleTitles = listOf("默认", "16:9", "4:3", "全屏", "拉伸")
        val defaultScaleIndex = 0
        scaleAdapter = VideoOptionAdapter(scaleTitles, defaultScaleIndex) { index, _ ->
            videoPlayer.setAspectScale(scaleTypes[index])
            videoPlayer.hideAllDrawers()
        }

        // 监听抽屉打开事件
        if (rvDrawer != null) {
            val layoutSubtitlePanel = videoPlayer.findViewById<View>(R.id.layout_subtitle_panel)
            videoPlayer.setOnDrawerOpenListener { type ->
                val rvParams = rvDrawer.layoutParams as? FrameLayout.LayoutParams
                if (type == MyGSYVideoPlayer.DrawerType.SUBTITLE) {
                    rvParams?.gravity = Gravity.TOP
                } else {
                    rvParams?.gravity = Gravity.CENTER
                }
                rvDrawer.layoutParams = rvParams
                layoutSubtitlePanel?.visibility = View.GONE

                // 切换抽屉时统一更新/重置空状态与列表控件显隐
                subtitlePanelController.updateSubtitleEmptyState()

                when (type) {
                    MyGSYVideoPlayer.DrawerType.EPISODE -> {
                        rvDrawer.adapter = episodeAdapter
                        val currentIndex = viewModel.uiState.value.fileBeanIndex
                        if (currentIndex >= 0) {
                            // 将当前行提前 3 行显示，使播放行接近中央
                            rvDrawer.scrollToPosition((currentIndex - 3).coerceAtLeast(0))
                        }
                    }

                    MyGSYVideoPlayer.DrawerType.SPEED -> {
                        rvDrawer.adapter = speedAdapter
                    }

                    MyGSYVideoPlayer.DrawerType.SCALE -> {
                        rvDrawer.adapter = scaleAdapter
                    }

                    MyGSYVideoPlayer.DrawerType.SUBTITLE -> {
                        layoutSubtitlePanel?.visibility = View.VISIBLE
                        rvDrawer.adapter = subtitlePanelController.subtitleAdapter
                        if (!viewModel.uiState.value.searchedSubtitle) {
                            viewModel.loadSubtitles(videoPlayer.duration)
                        }
                        val etSearch = videoPlayer.findViewById<EditText>(R.id.et_subtitle_search)
                        if (etSearch != null && etSearch.text.isNullOrEmpty()) {
                            etSearch.setText(viewModel.uiState.value.defaultSearchKeyword)
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    // 更新选中的视频集数时
    fun updateEpisodeSelectedIndex(fileBeanIndex: Int) {
        episodeAdapter.updateSelectedIndex(fileBeanIndex)
        if (fileBeanIndex >= 0 && videoPlayer.currentDrawerType == MyGSYVideoPlayer.DrawerType.EPISODE) {
            // 将当前行提前 3 行显示，使播放行接近中央
            videoPlayer.findViewById<RecyclerView>(R.id.rv_drawer)
                ?.scrollToPosition((fileBeanIndex - 3).coerceAtLeast(0))
        }
    }
}
