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
    private val ACTIVE_COLOR = "#42A5F5".toColorInt()
    private val ACTIVE_BG = "#3342A5F5".toColorInt()

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
            if (showToast) {
                App.instance.toast("字幕切换成功")
            } else {
                App.instance.toast("自动加载字幕: ${subtitleItem.simpleName}")
            }
        }
    }

    private fun initSubtitleControls() {
        // ============ 通用工具 ============

        /** 绑定点击事件，自动处理 findViewById 为 null 的情况 */
        fun onClick(id: Int, block: () -> Unit) {
            videoPlayer.findViewById<View>(id)?.setOnClickListener { block() }
        }

        /** 绑定"基于当前 subtitleStyle 做 copy 变换"的点击 */
        fun bindStyleClick(
            id: Int,
            transform: (SubtitleStyleBean) -> SubtitleStyleBean
        ) {
            onClick(id) {
                val style = viewModel.uiState.value.subtitleStyle
                viewModel.updateSubtitleStyle(transform(style))
            }
        }

        // ============ 搜索 ============

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

        // ============ 偏移 ============

        val offsetDeltas = mapOf(
            R.id.btn_offset_minus_1s to -1000L,
            R.id.btn_offset_minus_100ms to -100L,
            R.id.btn_offset_plus_100ms to 100L,
            R.id.btn_offset_plus_1s to 1000L
        )
        offsetDeltas.forEach { (id, delta) ->
            onClick(id) { viewModel.addSubtitleOffset(delta) }
        }
        onClick(R.id.btn_offset_reset) { viewModel.setSubtitleOffset(0L) }

        // ============ 样式：字号 / 颜色 / 字体 ============

        sizeMap.forEach { (id, size) ->
            bindStyleClick(id) { it.copy(textSizeSp = size) }
        }
        colorMap.forEach { (id, color) ->
            bindStyleClick(id) { it.copy(textColor = color) }
        }
        fontMap.forEach { (id, font) ->
            bindStyleClick(id) { it.copy(fontFamily = font) }
        }

        // ============ 样式：加粗 / 背景 ============

        bindStyleClick(R.id.btn_style_bold) { it.copy(isBold = !it.isBold) }

        bindStyleClick(R.id.btn_style_bg_translucent) {
            it.copy(
                backgroundColor = if (it.backgroundColor == Color.TRANSPARENT)
                    "#80000000".toColorInt() else Color.TRANSPARENT
            )
        }

        // ============ 保存 / 删除 ============

        onClick(R.id.btn_save_subtitle) {
            viewModel.saveAndUploadSubtitle(context.cacheDir)
        }

        onClick(R.id.btn_remove_subtitle) {
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
        // 显示空提示文本
        val tvEmpty = videoPlayer.findViewById<TextView>(R.id.tv_subtitle_empty) ?: return
        // 列表 RecyclerView
        val rvDrawer = videoPlayer.findViewById<RecyclerView>(R.id.rv_drawer) ?: return

        if (videoPlayer.currentDrawerType != MyGSYVideoPlayer.DrawerType.SUBTITLE) {
            tvEmpty.visibility = View.GONE
            rvDrawer.visibility = View.VISIBLE
            return
        }

        val isLoading = state.isSubtitleLoading
        val list = state.subtitles

        val showEmpty = isLoading || list.isEmpty()

        tvEmpty.visibility = if (showEmpty) View.VISIBLE else View.GONE
        rvDrawer.visibility = if (showEmpty) View.GONE else View.VISIBLE

        tvEmpty.text = when {
            isLoading -> "正在搜索获取字幕..."
            list.isEmpty() -> "'${viewModel.uiState.value.defaultSearchKeyword}'未找到相关字幕，请尝试换别的关键字重新搜索"
            else -> tvEmpty.text  // 列表正常时无需改文案
        }
    }

    /**
     * 根据当前的样式状态，刷新所有"样式按钮"的选中视觉。
     * 例如：当前字号是 18sp，那么"18sp"那个按钮就高亮，其余按钮恢复默认。
     *
     * 整体分两类按钮：
     *  1) 成组的按钮（字号/颜色/字体），每组存在一个 Map<按钮id, 该按钮代表的值> 里；
     *  2) 独立的开关按钮（加粗、半透明背景），只有一个按钮，判断条件单独写。
     */
    private fun updateSubtitleStyleButtonVisuals(styleBean: SubtitleStyleBean) {
        // ---- 第一类：成组按钮 ----

        // 字号组：按钮"选中"的条件是 当前样式的字号 == 这个按钮代表的字号
        applyGroupVisual(styleBean, sizeMap) { style, v -> style.textSizeSp == v }

        // 颜色组：选中条件是 当前文字颜色 == 这个按钮代表的颜色
        // 额外传 affectBackground=true，因为颜色按钮的选中效果是"加背景高亮"，
        // 而字号/字体按钮是靠"变文字颜色"来高亮，两者的视觉反馈方式不同。
        applyGroupVisual(
            styleBean,
            colorMap,
            affectTextColor = false, // 颜色组不改文字颜色，保留 XML 原色
            affectBackground = true
        ) { style, v -> style.textColor == v }

        // 字体组：选中条件是 当前字体 == 这个按钮代表的字体
        applyGroupVisual(styleBean, fontMap) { style, v -> style.fontFamily == v }

        // ---- 第二类：独立开关按钮 ----

        // 加粗按钮：当前 isBold 为 true 就高亮
        videoPlayer.findViewById<TextView>(R.id.btn_style_bold)
            ?.applySelectedVisual(styleBean.isBold)

        // 半透明背景按钮：背景色不是"透明"就说明生效了，高亮
        videoPlayer.findViewById<TextView>(R.id.btn_style_bg_translucent)
            ?.applySelectedVisual(styleBean.backgroundColor != Color.TRANSPARENT)
    }

    /**
     * 遍历一组按钮，逐个刷新选中视觉。
     *
     * @param style        当前样式状态，用来和每个按钮代表的值做比较
     * @param map          这一组按钮：key = 按钮的资源 id，value = 该按钮代表的值
     *                     （比如 sizeMap 是 Map<按钮id, 字号>）
     * @param affectBackground 选中时是否用"背景高亮"而不是"文字变色"。
     *                     字号/字体组用默认 false（文字变色），颜色组用 true（加背景）。
     * @param isSelected   判断"某个按钮是否选中"的规则。
     *                     之所以做成参数，是因为不同组判断字段不同：
     *                     字号组比 textSizeSp，颜色组比 textColor，字体组比 fontFamily。
     *                     传进来的是 (当前样式, 按钮代表的值) -> 是否选中。
     *
     * inline + crossinline：让这个 lambda 在调用处内联展开，避免为每次遍历创建函数对象，
     * 属于性能上的小优化，去掉也能正常运行。
     */
    private inline fun <V> applyGroupVisual(
        style: SubtitleStyleBean,
        map: Map<Int, V>,
        affectTextColor: Boolean = true,
        affectBackground: Boolean = false,
        crossinline isSelected: (SubtitleStyleBean, V) -> Boolean
    ) {
        map.forEach { (id, value) ->
            // 找到这个按钮；找不到就跳过（findViewById 可能返回 null）
            videoPlayer.findViewById<TextView>(id)?.applySelectedVisual(
                // 用传进来的规则，算出这个按钮当前该不该高亮
                isSelected = isSelected(style, value),
                affectTextColor = affectTextColor,
                // 把"是否用背景高亮"透传给下面的视觉函数
                affectBackground = affectBackground
            )
        }
    }

    /**
     * 给一个 TextView 应用"选中 / 未选中"的视觉。
     * 这是所有样式按钮最终统一走的地方，视觉规则集中在这里，改一处全局生效。
     *
     * @param isSelected       是否选中
     * @param affectTextColor  选中时是否改变文字颜色（默认 true）
     *                         - 字号/字体/加粗/背景按钮：true，靠文字变色 + 加粗表示选中
     *                         - 颜色按钮：false，因为它本身就是"选颜色"，
     *                           再改文字颜色会让人困惑，所以它只用背景高亮
     * @param affectBackground 选中时是否改变背景色（默认 false）
     *                         - 颜色按钮：true
     *                         - 其余：false
     */
    private fun TextView.applySelectedVisual(
        isSelected: Boolean,
        affectTextColor: Boolean = true,
        affectBackground: Boolean = false
    ) {
        // 文字颜色：选中用主题蓝，未选中用白色（仅在允许改文字色时执行）
        if (affectTextColor) {
            setTextColor(if (isSelected) ACTIVE_COLOR else Color.WHITE)
        }

        // 字重：选中加粗，未选中常规。所有按钮都适用，所以不加开关
        setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)

        // 背景色：选中用半透明蓝，未选中透明（仅在允许改背景时执行）
        if (affectBackground) {
            if (isSelected) {
                setBackgroundColor(ACTIVE_BG)
            } else {
                // 恢复 XML 中定义的原本 Drawable 背景，而不是设为透明 ColorDrawable
                setBackgroundResource(R.drawable.bg_control_pill)
            }
        }
    }
}
