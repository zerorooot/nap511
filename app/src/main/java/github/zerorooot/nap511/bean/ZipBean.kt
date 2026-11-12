package github.zerorooot.nap511.bean

import androidx.compose.runtime.Immutable
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import github.zerorooot.nap511.R
import java.lang.reflect.Type

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
class ExtractDataDeserializer : JsonDeserializer<ExtractData?> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ExtractData? {
        return if (json.isJsonObject) {
            context.deserialize(json, ExtractData::class.java)
        } else {
            null // 如果是 [] 空数组或其他非法类型，直接返回 null
        }
    }
}

class EncryptionDataDeserializer : JsonDeserializer<EncryptionData?> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): EncryptionData? {
        return if (json.isJsonObject) {
            context.deserialize(json, EncryptionData::class.java)
        } else {
            null // 如果服务端返回 [] 空数组，安全降级为 null
        }
    }
}

class ProcessDataDeserializer : JsonDeserializer<ProcessData?> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ProcessData? {
        return if (json.isJsonObject) {
            context.deserialize(json, ProcessData::class.java)
        } else {
            null // 如果服务端返回 [] 空数组，安全降级为 null
        }
    }
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
    @JsonAdapter(ExtractDataDeserializer::class)
    val data: ExtractData? = null
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
    @JsonAdapter(EncryptionDataDeserializer::class)
    val data: EncryptionData? = null
) : BaseResponse()

data class EncryptionData(
    @SerializedName("unzip_status")
    val unzipStatus: Int = -1
)

data class ProcessDataResponse(
    @SerializedName("data")
    @JsonAdapter(ProcessDataDeserializer::class)
    val data: ProcessData? = null
) : BaseResponse()

data class ProcessData(
    @SerializedName("extract_id")
    val extractId: String,
    @SerializedName("to_pid")
    val toPid: String,
    val percent: Int
)

@Immutable
data class ZipBeanList(
    val list: List<ZipBean> = emptyList(),
    @SerializedName("has_file")
    val hasFile: Boolean = false,
    @SerializedName("next_marker")
    val nextMarker: String = "",
    val pathString: String = ""
)

@Immutable
data class ZipBean(
    @SerializedName("file_name")
    val fileName: String = "",
    @SerializedName("ico")
    val icoString: String = "",
    val fileIco: Int = R.drawable.other,
    val size: Long = 0,
    val sizeString: String = "",
    /**
     * 1 is file, 0 is folder
     */
    @SerializedName("file_category")
    val fileCategory: Int = 1,
    val time: String = "",
    val timeString: String = ""
)
