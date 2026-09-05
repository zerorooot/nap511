package github.zerorooot.nap511.bean

import com.google.gson.annotations.SerializedName

data class MusicBean(
    val state: Boolean = false,
    val url: String = "",
    @SerializedName("audio_url")
    val audioUrl: String = ""
)

data class VideoInfoBean(
    @SerializedName("thumb_url") var thumbUrl: String = "",
    @SerializedName("height") var height: Int = 0,
    @SerializedName("width") var width: Int = 0,
    @SerializedName("video_url") var videoUrl: String = "",
    @SerializedName("play_long") var playLong: String = "",
    @SerializedName("pick_code") var pickCode: String = "",
    @SerializedName("file_name") var fileName: String = "",
    @SerializedName("file_size") var fileSize: String = "",
    @SerializedName("parent_id") var parentId: String = "",
    @SerializedName("file_id") var fileId: String = "",
    @SerializedName("is_mark") var isMark: String = "",
    @SerializedName("sha1") var sha1: String = "",
    @SerializedName("user_def") var userDef: String = "",
    @SerializedName("user_rotate") var userRotate: Int = 0,
    @SerializedName("user_turn") var userTurn: Int = 0,
    @SerializedName("origin_file_url") var originFileUrl: String = "",
    var index: Int = -1,
    var isAutoRotate: Boolean = false,
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

data class ImageDate(
    @SerializedName("state") var state: Boolean = false,
    @SerializedName("data") var imageBean: ImageBean = ImageBean()
)

data class ImageBean(
    @SerializedName("url") var url: String = "",
    @SerializedName("origin_url") var originUrl: String = "",
    @SerializedName("source_url") var sourceUrl: String = "",
    @SerializedName("file_name") var fileName: String = "",
    @SerializedName("file_sha1") var fileSha1: String = "",
    @SerializedName("pick_code") var pickCode: String = "",
)
