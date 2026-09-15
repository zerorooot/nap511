package github.zerorooot.nap511.bean

/**
 * 视频播放器 UI 状态数据类
 */
data class VideoUiState(
    val videoInfo: VideoInfoBean? = null,
    val fileBeanIndex: Int = -1,
    val hasPrev: Boolean = false,
    val hasNext: Boolean = false,
    val subtitles: List<SubtitleItem> = emptyList(),
    val selectedSubtitle: SubtitleItem? = null,
    val subtitleOffsetMs: Long = 0L,
    val subtitleStyle: SubtitleStyleBean = SubtitleStyleBean(),
    val isSubtitleLoading: Boolean = false,
    val defaultSearchKeyword: String = ""
)
