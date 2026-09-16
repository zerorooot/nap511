package github.zerorooot.nap511.bean

import github.zerorooot.nap511.util.subtitle.SubtitleEntry

/**
 * 统一字幕/歌词 UI 状态数据类
 */
data class SubtitleUiState(
    val subtitles: List<SubtitleItem> = emptyList(),
    val selectedSubtitle: SubtitleItem? = null,
    val offsetMs: Long = 0L,
    val isLoading: Boolean = false,
    val isSearchLoading: Boolean = false,
    val searchedSubtitle: Boolean = false,
    val defaultSearchKeyword: String = "",
    val currentLocalSubtitles: List<SubtitleItem> = emptyList(),

    // 音频播放歌词/字幕逐行匹配状态
    val entries: List<SubtitleEntry> = emptyList(),
    val currentText: String = "",
    val currentIndex: Int = -1,

    // 视频字幕样式控制
    val subtitleStyle: SubtitleStyleBean = SubtitleStyleBean()
)
