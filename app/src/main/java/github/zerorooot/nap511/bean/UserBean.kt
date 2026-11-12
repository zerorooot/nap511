package github.zerorooot.nap511.bean

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import github.zerorooot.nap511.R

@Immutable
data class AvatarBean(
    val expire: Long = 1L,
    val expireString: String = "1970-01-01 08:00:00",
    @SerializedName("user_name")
    val userName: String = "Test",
    val face: String = "https://my.115.com/static/2014v1.0/personal/head/80/male/male034.png",
    @SerializedName("user_id")
    val userId: String = ""
)

data class RemainingSpaceBean(
    @SerializedName("all_remain") val remain: SpaceDetails = SpaceDetails(),
    @SerializedName("all_total") val total: SpaceDetails = SpaceDetails(),
    @SerializedName("all_use") val use: SpaceDetails = SpaceDetails(0)
) {
    data class SpaceDetails(
        val size: Long = 1L,
        @SerializedName("size_format") val sizeFormat: String = "0TB"
    )
}

@Immutable
data class SignBean(
    @SerializedName("state") val state: Boolean = false,
    @SerializedName("size") val size: String = "",
    @SerializedName("url") val url: String = "",
    @SerializedName("bt_url") val btUrl: String = "",
    @SerializedName("sign") val sign: String = "",
    @SerializedName("time") val time: Int = -1
)

data class InitUploadBean(
    @SerializedName("object")
    val key: String,
    @SerializedName("accessid")
    val oSSAccessKeyId: String,
    val host: String,
    val policy: String,
    val signature: String,
    val callback: String
)

@Immutable
data class RecycleInfo(
    @SerializedName("count") val count: String = "",
    @SerializedName("state") val state: Boolean = false,
    @SerializedName("error") val error: String = "",
    @SerializedName("offset") val offset: Int = -1,
    @SerializedName("page_size") val pageSize: Int = -1,
    @SerializedName("data") val recycleBeanList: List<RecycleBean> = emptyList()
)

@Immutable
data class RecycleBean(
    @SerializedName("id") val id: String = "",
    @SerializedName("file_name") val fileName: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("file_size") val fileSize: String = "",
    @SerializedName("dtime") val modifiedTime: String = "",
    @SerializedName("status") val status: String = "",
    @SerializedName("cid") val cid: String = "",
    @SerializedName("parent_name") val parentName: String = "",
    @SerializedName("iv") val iv: Int = -1,
    @SerializedName("vdi") val vdi: Int = -1,
    @SerializedName("ico") val ico: String = "",
    @SerializedName("u") val photoThumb: String = "",
    @SerializedName("play_long") val playLong: Float = -1f,
    val isFolder: Boolean = false,
    val modifiedTimeString: String = "",
    val fileSizeString: String = "",
    val fileIco: Int = R.drawable.other
)
