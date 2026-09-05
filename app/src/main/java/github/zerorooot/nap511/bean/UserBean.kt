package github.zerorooot.nap511.bean

import com.google.gson.annotations.SerializedName
import github.zerorooot.nap511.R

data class AvatarBean(
    var expire: Long = 1L,
    var expireString: String = "1970-01-01 08:00:00",
    @SerializedName("user_name")
    var userName: String = "Test",
    var face: String = "https://my.115.com/static/2014v1.0/personal/head/80/male/male034.png",
    @SerializedName("user_id")
    var userId: String = "0"
)

data class RemainingSpaceBean(
    @SerializedName("all_remain") val remain: SpaceDetails = SpaceDetails(),
    @SerializedName("all_total") val total: SpaceDetails = SpaceDetails(),
    @SerializedName("all_use") val use: SpaceDetails = SpaceDetails()
) {
    data class SpaceDetails(
        val size: Long = 1L,
        @SerializedName("size_format") val sizeFormat: String = "0TB"
    )
}

data class SignBean(
    @SerializedName("state") var state: Boolean = false,
    @SerializedName("size") var size: String = "",
    @SerializedName("url") var url: String = "",
    @SerializedName("bt_url") var btUrl: String = "",
    @SerializedName("sign") var sign: String = "",
    @SerializedName("time") var time: Int = -1
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

data class RecycleInfo(
    @SerializedName("count") var count: String = "",
    @SerializedName("state") var state: Boolean = false,
    @SerializedName("error") var error: String = "",
    @SerializedName("offset") var offset: Int = -1,
    @SerializedName("page_size") var pageSize: Int = -1,
    @SerializedName("data") var recycleBeanList: ArrayList<RecycleBean> = arrayListOf()
)

data class RecycleBean(
    @SerializedName("id") var id: String = "",
    @SerializedName("file_name") var fileName: String = "",
    @SerializedName("type") var type: String = "",
    @SerializedName("file_size") var fileSize: String = "",
    @SerializedName("dtime") var modifiedTime: String = "",
    @SerializedName("status") var status: String = "",
    @SerializedName("cid") var cid: String = "",
    @SerializedName("parent_name") var parentName: String = "",
    @SerializedName("iv") var iv: Int = -1,
    @SerializedName("vdi") var vdi: Int = -1,
    @SerializedName("ico") var ico: String = "",
    @SerializedName("u") var photoThumb: String = "",
    @SerializedName("play_long") var playLong: Float = -1f,
    var isFolder: Boolean = false,
    var modifiedTimeString: String = "",
    var fileSizeString: String = "",
    var fileIco: Int = R.drawable.other
)
