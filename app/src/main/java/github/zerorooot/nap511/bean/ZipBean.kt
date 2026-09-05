package github.zerorooot.nap511.bean

import com.google.gson.annotations.SerializedName
import github.zerorooot.nap511.R

sealed interface ZipStatus {
    /** 正常未加密的压缩包，可以直接预览 */
    object Normal : ZipStatus

    /** 正在加载中，需等待 */
    data class Loading(val progress: Int) : ZipStatus

    /** 加密压缩包，需要提示用户输入密码 */
    object Encrypted : ZipStatus

    /** 不支持预览（如文件超大）或接口、网络等其它错误 */
    data class UnsupportedOrError(val message: String) : ZipStatus
}

/**
 * 异常，正在进行云解压
 */
class DecompressionLoadingException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

data class ExtractResponse(
    @SerializedName("data")
    val data: ExtractData
) : BaseResponse()

data class ExtractData(
    @SerializedName("extract_status")
    val extractStatus: ExtractStatus
)

data class ExtractStatus(
    @SerializedName("unzip_status")
    val unzipStatus: Int = -1,
    val progress: Int = -1
)

data class EncryptionDataResponse(
    @SerializedName("data")
    val data: EncryptionData
) : BaseResponse()

data class EncryptionData(
    @SerializedName("unzip_status")
    val unzipStatus: Int = -1
)

data class ProcessDataResponse(
    @SerializedName("data")
    val data: ProcessData
) : BaseResponse()

data class ProcessData(
    @SerializedName("extract_id")
    val extractId: String,
    @SerializedName("to_pid")
    val toPid: String,
    val percent: Int
)

data class ZipBeanList(
    var list: ArrayList<ZipBean> = arrayListOf(),
    @SerializedName("has_file")
    var hasFile: Boolean = false,
    @SerializedName("next_marker")
    var nextMarker: String = "",
    var pathString: String = ""
)

data class ZipBean(
    @SerializedName("file_name")
    var fileName: String = "",
    @SerializedName("ico")
    var icoString: String = "",
    var fileIco: Int = R.drawable.other,
    var size: Long = 0,
    var sizeString: String = "",
    /**
     * 1 is file, 0 is folder
     */
    @SerializedName("file_category")
    var fileCategory: Int = 1,
    var time: String = "",
    var timeString: String = ""
)
