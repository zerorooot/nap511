package github.zerorooot.nap511.bean

/**
 * 视频播放器 UI 状态数据类
 */
data class VideoUiState(
    val videoInfo: VideoInfoBean? = null,
    val fileBeanIndex: Int = -1,
    val hasPrev: Boolean = false,
    val hasNext: Boolean = false,
    val subtitle: SubtitleUiState = SubtitleUiState()
) {
    // 兼容快捷属性访问，映射至字幕独立 UI 状态
    val searchedSubtitle: Boolean get() = subtitle.searchedSubtitle
    val subtitles: List<SubtitleItem> get() = subtitle.subtitles
    val selectedSubtitle: SubtitleItem? get() = subtitle.selectedSubtitle
    val subtitleOffsetMs: Long get() = subtitle.offsetMs
    val subtitleStyle: SubtitleStyleBean get() = subtitle.subtitleStyle
    val isSubtitleLoading: Boolean get() = subtitle.isLoading || subtitle.isSearchLoading
    val defaultSearchKeyword: String get() = subtitle.defaultSearchKeyword
}
