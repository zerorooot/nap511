package github.zerorooot.nap511.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

data class MusicBean(
    val state: Boolean = false,
    val url: String = "",
    @SerializedName("audio_url")
    val audioUrl: String = ""
)

data class VideoBean(
    val currentDuration: Int = -1,
//    val fileBeanIndex: Int = -1,
    val pickCode: String,
    val isStar: Boolean = false,
    val fileId: String = "",
    //playNextVideo中需要用到
    val name: String = "",
    val time: String = ""
)

/**
 * 视频星标操作包装类，记录文件 ID 与目标加星状态
 */
data class VideoStarAction(
    val fid: String,
    val star: Boolean
)

/**
 * 启动视频播放的事件包装类
 * @param videoInfo 视频基础信息
 * @param videoAttribute 视频属性配置
 * @param videoList 视频列表
 */
data class LaunchVideoParams(
    val videoInfo: VideoInfoBean,
    val videoAttribute: VideoAttributeBean,
    val localSubtitleItem: List<SubtitleItem>,
    val videoList: List<VideoBean>,
    val categoryId: String
)

data class VideoAttributeBean(
    val isAutoRotate: Boolean = false,
    val videoLinkMode: Boolean = false,
    val autoJumpRetry: Boolean = true,
    val hideLoading: Boolean = false,
    val positionAfterAt: Boolean = false,
    val automaticSubtitle: Boolean = false
)

@Immutable
data class VideoInfoBean(
    @SerializedName("thumb_url") val thumbUrl: String = "",
    @SerializedName("height") val height: Int = 0,
    @SerializedName("width") val width: Int = 0,
    @SerializedName("video_url") val videoUrl: String = "",
    @SerializedName("play_long") val playLong: String = "",
    @SerializedName("pick_code") val pickCode: String = "",
    @SerializedName("file_name") val fileName: String = "",
    @SerializedName("file_size") val fileSize: String = "",
    @SerializedName("parent_id") val parentId: String = "",
    @SerializedName("file_id") val fileId: String = "",
    @SerializedName("is_mark") val isMark: String = "",
    @SerializedName("sha1") val sha1: String = "",
    @SerializedName("user_def") val userDef: String = "",
    @SerializedName("user_rotate") val userRotate: Int = 0,
    @SerializedName("user_turn") val userTurn: Int = 0,
    @SerializedName("origin_file_url") val originFileUrl: String = "",
    val index: Int = -1,

    @SerializedName("download_url")
    private val rawDownloadUrl: Any? = null
) {
    val downloadUrl: String
        get() = when (rawDownloadUrl) {
            is String -> rawDownloadUrl
            is List<*> -> ""
            else -> ""
        }
}

/**
 * 结合 115 云端返回的 userRotate 计算视觉宽高
 * 若旋转 90° 或 270°，交换物理宽高
 */
fun VideoInfoBean.getVisualDimensions(): Pair<Int, Int> {
    val isSwapped = (userRotate % 180 != 0)
    val visualWidth = if (isSwapped) height else width
    val visualHeight = if (isSwapped) width else height
    return visualWidth to visualHeight
}

@Immutable
data class ImageDate(
    @SerializedName("state") val state: Boolean = false,
    @SerializedName("data") val imageBean: ImageBean = ImageBean()
)

@Immutable
data class ImageBean(
    @SerializedName("url") val url: String = "",
    @SerializedName("origin_url") val originUrl: String = "",
    @SerializedName("source_url") val sourceUrl: String = "",
    @SerializedName("file_name") val fileName: String = "",
    @SerializedName("file_sha1") val fileSha1: String = "",
    @SerializedName("pick_code") val pickCode: String = "",
)

data class UploadBean(
    @SerializedName("aid") val areaId: String = "",
    @SerializedName("cid") val categoryId: String = "",
    @SerializedName("file_name") val name: String = "",
    @SerializedName("file_ptime") val createTime: String = "",
    @SerializedName("file_id") val fileId: String = "",
    @SerializedName("file_size") val size: String = "0",
    @SerializedName("pick_code") val pickCode: String = "",
    @SerializedName("sha1") val sha1: String = "",
    @SerializedName("is_video") val isVideo: Int = 0,
)