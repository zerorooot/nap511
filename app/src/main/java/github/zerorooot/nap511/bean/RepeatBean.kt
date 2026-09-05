package github.zerorooot.nap511.bean

import com.google.gson.annotations.SerializedName
import github.zerorooot.nap511.R

data class RepeatStatusResponse(
    @SerializedName("data") val data: RepeatStatusData? = null
) : BaseResponse()

data class RepeatStatusData(
    @SerializedName("group_count") val groupCount: String = "0",
    @SerializedName("file_count") val fileCount: String = "0",
    @SerializedName("file_size") val fileSize: String = "0"
)

data class RepeatListResponse(
    @SerializedName("data") val data: List<RepeatFileItem> = emptyList(),
    @SerializedName("s") val offset: Int = 0,
    @SerializedName("l") val limit: String = "100",
    @SerializedName("count") val count: String = "0"
) : BaseResponse()

data class RepeatFileItem(
    @SerializedName("sha1") val sha1: String = "",
    @SerializedName("file_id") val fileId: String = "",
    @SerializedName("file_name") val fileName: String = "",
    @SerializedName("ico") val ico: String = "",
    @SerializedName("file_size") val fileSize: String = "",
    @SerializedName("user_utime") val userUtime: String = "",
    @SerializedName("user_utime_str") val userUtimeStr: String = "",
    @SerializedName("parent_id") val parentId: String = "",
    @SerializedName("path") val path: String = "",
    var fileIco: Int = R.drawable.other,
    var fileSizeString: String = ""
)
