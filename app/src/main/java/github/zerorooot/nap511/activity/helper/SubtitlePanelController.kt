package github.zerorooot.nap511.activity.helper

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.shuyu.gsyvideoplayer.subtitle.GSYSubtitleMime
import com.shuyu.gsyvideoplayer.subtitle.GSYSubtitleSource
import github.zerorooot.nap511.R
import github.zerorooot.nap511.adapter.SubtitleAdapter
import github.zerorooot.nap511.bean.FontFamilyType
import github.zerorooot.nap511.bean.SubtitleItem
import github.zerorooot.nap511.bean.SubtitleStyleBean
import github.zerorooot.nap511.bean.VideoUiState
import github.zerorooot.nap511.player.MyGSYVideoPlayer
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.viewmodel.VideoViewModel

class SubtitlePanelController(
    private val context: Context,
    private val videoPlayer: MyGSYVideoPlayer,
    private val viewModel: VideoViewModel
) {
    val subtitleAdapter: SubtitleAdapter

    private val sizeMap = mapOf(
        R.id.btn_style_size_small to 22f,
        R.id.btn_style_size_medium to 24f,
        R.id.btn_style_size_large to 26f
    )

    private val colorMap = mapOf(
        R.id.btn_style_color_white to Color.WHITE,
        R.id.btn_style_color_yellow to Color.YELLOW,
        R.id.btn_style_color_green to Color.GREEN,
        R.id.btn_style_color_cyan to Color.CYAN
    )

    private val fontMap = mapOf(
        R.id.btn_style_font_default to FontFamilyType.DEFAULT,
        R.id.btn_style_font_sans to FontFamilyType.SANS_SERIF,
        R.id.btn_style_font_serif to FontFamilyType.SERIF,
        R.id.btn_style_font_mono to FontFamilyType.MONOSPACE
    )

    init {
        val uiState = viewModel.uiState.value
        subtitleAdapter = SubtitleAdapter(
            items = uiState.subtitles,
            selectedId = uiState.selectedSubtitle?.id ?: "",
        ) { subtitleItem ->
            applySubtitle(subtitleItem, true)
        }
        initSubtitleControls()
    }

    fun applySubtitle(subtitleItem: SubtitleItem, showToast: Boolean = true) {
        viewModel.selectSubtitle(context.cacheDir, subtitleItem) { srtFile ->
            val source = GSYSubtitleSource.Builder(Uri.fromFile(srtFile).toString())
                .setLabel(subtitleItem.simpleName)
                .setId(subtitleItem.id)
                .setMimeType(GSYSubtitleMime.APPLICATION_SUBRIP)
                .setLanguage("zh")
                .setCharsetName("UTF-8")
                .setOffsetMs(viewModel.uiState.value.subtitleOffsetMs)
                .setDefault(true)
                .build()
            videoPlayer.setSubtitleSource(source)
            videoPlayer.setSubtitleEnabled(true)
//            if (showToast) {
//                App.instance.toast("字幕切换成功")
//            } else {
               // App.instance.toast("自动加载字幕: ${subtitleItem.simpleName}")
//            }
        }
    }

    private fun initSubtitleControls() {
        val etSearch = videoPlayer.findViewById<EditText>(R.id.et_subtitle_search)
        val btnSearch = videoPlayer.findViewById<View>(R.id.btn_subtitle_search)
        val executeSearch = {
            val keyword = etSearch?.text?.toString()?.trim() ?: ""
            if (keyword.isNotEmpty()) {
                viewModel.searchSubtitlesByName(keyword, videoPlayer.duration)
            }
        }
        btnSearch?.setOnClickListener { executeSearch() }
        etSearch?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                executeSearch()
                true
            } else false
        }

        val offsetDeltas = mapOf(
            R.id.btn_offset_minus_1s to -1000L,
            R.id.btn_offset_minus_100ms to -100L,
            R.id.btn_offset_plus_100ms to 100L,
            R.id.btn_offset_plus_1s to 1000L
        )
        offsetDeltas.forEach { (id, delta) ->
            videoPlayer.findViewById<View>(id)
                ?.setOnClickListener { viewModel.addSubtitleOffset(delta) }
        }
        videoPlayer.findViewById<View>(R.id.btn_offset_reset)
            ?.setOnClickListener { viewModel.setSubtitleOffset(0L) }

        sizeMap.forEach { (id, size) ->
            videoPlayer.findViewById<View>(id)?.setOnClickListener {
                viewModel.updateSubtitleStyle(viewModel.uiState.value.subtitleStyle.copy(textSizeSp = size))
            }
        }

        colorMap.forEach { (id, color) ->
            videoPlayer.findViewById<View>(id)?.setOnClickListener {
                viewModel.updateSubtitleStyle(viewModel.uiState.value.subtitleStyle.copy(textColor = color))
            }
        }

        fontMap.forEach { (id, font) ->
            videoPlayer.findViewById<View>(id)?.setOnClickListener {
                viewModel.updateSubtitleStyle(viewModel.uiState.value.subtitleStyle.copy(fontFamily = font))
            }
        }

        videoPlayer.findViewById<View>(R.id.btn_style_bold)?.setOnClickListener {
            val curBold = viewModel.uiState.value.subtitleStyle.isBold
            viewModel.updateSubtitleStyle(viewModel.uiState.value.subtitleStyle.copy(isBold = !curBold))
        }
        videoPlayer.findViewById<View>(R.id.btn_style_bg_translucent)?.setOnClickListener {
            val curBg = viewModel.uiState.value.subtitleStyle.backgroundColor
            val nextBg =
                if (curBg == Color.TRANSPARENT) "#80000000".toColorInt() else Color.TRANSPARENT
            viewModel.updateSubtitleStyle(viewModel.uiState.value.subtitleStyle.copy(backgroundColor = nextBg))
        }

        videoPlayer.findViewById<View>(R.id.btn_save_subtitle)?.setOnClickListener {
            viewModel.saveAndUploadSubtitle(context.cacheDir)
        }

        videoPlayer.findViewById<View>(R.id.btn_remove_subtitle)?.setOnClickListener {
            videoPlayer.setSubtitleEnabled(false)
            viewModel.removeSubtitle()
            subtitleAdapter.updateSelectedId("")
        }
    }

    fun bindState(state: VideoUiState) {
        subtitleAdapter.updateData(
            state.subtitles,
            state.selectedSubtitle?.id ?: ""
        )
        subtitleAdapter.updateSelectedId(state.selectedSubtitle?.id ?: "")

        updateSubtitleEmptyState(state)

        val tvOffsetLabel = videoPlayer.findViewById<TextView>(R.id.tv_subtitle_offset_label)
        tvOffsetLabel?.text = "偏移: ${state.subtitleOffsetMs}ms"
        videoPlayer.setSubtitleOffsetMs(state.subtitleOffsetMs)

        videoPlayer.applySubtitleStyle(state.subtitleStyle)
        updateSubtitleStyleButtonVisuals(state.subtitleStyle)

        val etSearch = videoPlayer.findViewById<EditText>(R.id.et_subtitle_search)
        if (etSearch != null) {
            etSearch.setText(state.defaultSearchKeyword)
        }
    }

    fun updateSubtitleEmptyState(state: VideoUiState = viewModel.uiState.value) {
        val tvEmpty = videoPlayer.findViewById<TextView>(R.id.tv_subtitle_empty) ?: return
        val rvDrawer = videoPlayer.findViewById<RecyclerView>(R.id.rv_drawer) ?: return

        if (videoPlayer.currentDrawerType != MyGSYVideoPlayer.DrawerType.SUBTITLE) {
            tvEmpty.visibility = View.GONE
            rvDrawer.visibility = View.VISIBLE
            return
        }

        val isLoading = state.isSubtitleLoading
        val list = state.subtitles

        if (isLoading) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "正在搜索获取字幕..."
            rvDrawer.visibility = View.GONE
        } else if (list.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = "未找到相关字幕，请尝试换别的关键字重新搜索"
            rvDrawer.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvDrawer.visibility = View.VISIBLE
        }
    }

    private fun updateSubtitleStyleButtonVisuals(styleBean: SubtitleStyleBean) {
        val activeColor = "#42A5F5".toColorInt()
        val defaultColor = Color.WHITE

        sizeMap.forEach { (id, size) ->
            val btn = videoPlayer.findViewById<TextView>(id) ?: return@forEach
            val isSelected = styleBean.textSizeSp == size
            btn.setTextColor(if (isSelected) activeColor else defaultColor)
            btn.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        }

        colorMap.forEach { (id, color) ->
            val btn = videoPlayer.findViewById<TextView>(id) ?: return@forEach
            val isSelected = styleBean.textColor == color
            btn.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            btn.setBackgroundColor(if (isSelected) "#3342A5F5".toColorInt() else Color.TRANSPARENT)
        }

        fontMap.forEach { (id, font) ->
            val btn = videoPlayer.findViewById<TextView>(id) ?: return@forEach
            val isSelected = styleBean.fontFamily == font
            btn.setTextColor(if (isSelected) activeColor else defaultColor)
            btn.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        }

        val btnBold = videoPlayer.findViewById<TextView>(R.id.btn_style_bold)
        btnBold?.setTextColor(if (styleBean.isBold) activeColor else defaultColor)
        btnBold?.setTypeface(null, if (styleBean.isBold) Typeface.BOLD else Typeface.NORMAL)

        val btnBg = videoPlayer.findViewById<TextView>(R.id.btn_style_bg_translucent)
        val hasBg = styleBean.backgroundColor != Color.TRANSPARENT
        btnBg?.setTextColor(if (hasBg) activeColor else defaultColor)
        btnBg?.setTypeface(null, if (hasBg) Typeface.BOLD else Typeface.NORMAL)
    }
}
