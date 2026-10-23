package github.zerorooot.nap511.bean

import android.os.Parcelable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.gson.annotations.SerializedName
import github.zerorooot.nap511.R
import kotlinx.parcelize.Parcelize
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

enum class ForceOpenType(
    val label: String,
    val icon: ImageVector
) {
    VIDEO("视频", Icons.Default.VideoFile),
    AUDIO("音频", Icons.Default.AudioFile),
    IMAGE("图像", Icons.Default.Image),
    TEXT("文本", Icons.Default.Description),
    WEB("网页", Icons.Default.Language),
    ARCHIVE("压缩", Icons.Default.FolderZip),
    TORRENT("种子", Icons.AutoMirrored.Filled.InsertDriveFile)
}

data class FilesBean(
    @SerializedName("data") var fileBeanList: ArrayList<FileBean>,
    var cid: String,
    var count: Int,
    var order: String,
    var path: List<PathBean>,
    /**
     * is_asc = 1：升序（正序，从小到大 / A-Z / 从旧到新）。
     *
     * is_asc = 0：降序（倒序，从大到小 / Z-A / 从新到旧）。
     */
    @SerializedName("is_asc") val isAsc: Int,
)

object OrderEnum {
    const val create = "user_ptime"
    const val type = "file_type"
    const val name = "file_name"
    const val size = "file_size"
    const val update = "user_utime"
    const val open = "user_otime"
}

enum class OrderType(val rawValue: String, val label: String) {
    NAME(OrderEnum.name, "文件名称"),
    SIZE(OrderEnum.size, "文件大小"),
    TYPE(OrderEnum.type, "文件种类"),
    CREATE(OrderEnum.create, "创建时间"),
    UPDATE(OrderEnum.update, "修改时间"),
    OPEN(OrderEnum.open, "打开时间");

    companion object {
        fun fromRaw(raw: String): OrderType = entries.find { it.rawValue == raw } ?: NAME
        fun fromLabel(label: String): OrderType = entries.find { it.label == label } ?: NAME
    }
}

data class OrderBean(
    var type: String = OrderEnum.name,
    var asc: Int = 1
) {
    val isAscending: Boolean get() = asc == 1
    val orderType: OrderType get() = OrderType.fromRaw(type)

    override fun toString(): String {
        val arrow = if (isAscending) "⬆️" else "⬇️"
        return "${orderType.label}$arrow"
    }

    companion object {
        // 从 FilesBean 快捷创建
        fun from(files: FilesBean) = OrderBean(type = files.order, asc = files.isAsc)

        // 从弹窗选中的 UI 文本（如 "文件名称⬆️"）安全解析成 OrderBean
        fun parseFromUiString(uiText: String): OrderBean {
            val isAsc = if (uiText.endsWith("⬆️")) 1 else 0
            val label = uiText.removeSuffix("⬆️").removeSuffix("⬇️")
            return OrderBean(type = OrderType.fromLabel(label).rawValue, asc = isAsc)
        }
    }
}

data class QuotaBean(var count: Int, var surplus: Int)
data class PathBean(var cid: String, var name: String, var pid: String)

data class LocationBean(var firstVisibleItemIndex: Int, var firstVisibleItemScrollOffset: Int)

// 告诉 Compose 编译器此类是稳定的，无需重复绘制未变动的 Item
@Immutable
@Parcelize
data class FileBean(
    @SerializedName("cid") val categoryId: String = "",
    @SerializedName("ico") val icoString: String = "",
    @SerializedName("aid") val areaId: String = "",
    @SerializedName("pid") val parentId: String = "",
    @SerializedName("n") val name: String = "",
    @SerializedName("fid") val fileId: String = "",
    @SerializedName("pc") val pickCode: String = "",

    @SerializedName("t") val time: String = "",
    /**
     * Time Edit / user_utime (mtime),文件修改时间 (Modification Time),
     * 代表文件内容的最后修改时间。在 115 API 排序参数中对应 user_utime。注意它与 t 的时间完全一致。
     */
    @SerializedName("te") val modifiedTime: String = "",
    /**
     * Time Push / user_ptime (ctime),创建 / 上传时间 (Creation/Push Time)
     * 代表文件被上传或转存至网盘的时间。在 115 API 排序参数中对应 user_ptime。当前与 te 相同，表示文件上传后未被修改过。
     */
    @SerializedName("tp") val createTime: String = "",
    /**
     *Time Open / user_otime (atime),最后打开 / 访问时间 (Open/Access Time)
     * 代表用户在网盘中最后一次预览、播放或下载该文件的时间。在 115 API 排序参数中对应 user_otime。
     */
    @SerializedName("to") val openTime: String = "",

    /**
     *Time Update / utime,元数据更新时间 (Metadata Update Time)
     * 代表该文件在云端的元数据属性（如重命名、移动目录位置、修改标签或云端同步）最后一次发生变动的时间。
     */
    @SerializedName("tu") val metadataUpdateTime: String = "",

    @SerializedName("iv") val isVideo: Int = 0,
    @SerializedName("u") val photoThumb: String = "",
    @SerializedName("fuuid") val uuid: Long = 0,
    @SerializedName("sha") val sha1: String = "",
    @SerializedName("s") val size: String = "0",
    @SerializedName("current_time") val currentPlayTime: Int = 0,
    @SerializedName("play_long") val playLong: Double = 0.00,
    val isFolder: Boolean = false,
    val modifiedTimeString: String = "",
    val createTimeString: String = "",
    val timeString: String = "",
    /**
     * 视频时间格式化后
     */
    val playLongString: String = "",
    /**
     * 播放了百分之几
     */
    val playLongRatio: String = "",
    val sizeString: String = "",
    val isSelect: Boolean = false,
    val fileIco: Int = R.drawable.other
) : Parcelable

data class FileInfo(
    @SerializedName("count") var count: String = "",
    @SerializedName("size") var size: String = "",
    @SerializedName("folder_count") var folderCount: String = "",
    @SerializedName("ptime") var createTime: String = "",
    @SerializedName("utime") var changeTime: String = "",
    @SerializedName("play_long") var playLong: Int = 0,
    @SerializedName("file_name") var fileName: String = "",
    @SerializedName("pick_code") var pickCode: String = "",
    @SerializedName("sha1") var sha1: String = "",
    @SerializedName("open_time") var openTime: Int = 0,
    @SerializedName("desc") var desc: String = "",
    @SerializedName("file_category") var fileCategory: String = "",
    var paths: List<PathsBean> = emptyList()
)

data class InfoSection(
    val title: String,
    val items: List<InfoItem>
)

data class InfoItem(
    val label: String,
    val value: String,
    val customContent: (@Composable () -> Unit)? = null
)

data class PathsBean(
    @SerializedName("file_id") var fileId: String = "",
    @SerializedName("file_name") var fileName: String = ""
)

data class CreateFolderMessage(
    @SerializedName("state") var state: Boolean = false,
    @SerializedName("error") var error: String = "",
    @SerializedName("errno") var errno: String = "",
    @SerializedName("aid") var aid: Int = 0,
    @SerializedName("cid") var cid: String = "",
    @SerializedName("cname") var cname: String = "",
    @SerializedName("file_id") var fileId: String = "",
    @SerializedName("file_name") var fileName: String = ""
)

data class RenameBean(var fid: String, var newName: String) {
    fun toRequestBody(): RequestBody {
        return toString().toRequestBody("application/x-www-form-urlencoded".toMediaType())
    }

    override fun toString(): String {
        return "files_new_name[$fid]=$newName"
    }
}

data class CategoryDetailResponse(
    @SerializedName("state") val state: Boolean = false,
    @SerializedName("file_name") val fileName: String = "",
    @SerializedName("size") val size: String = "",
    @SerializedName("file_category") val fileCategory: String = "",
    @SerializedName("ctime") val ctime: String = "",
    @SerializedName("utime") val utime: String = "",
    @SerializedName("paths") val paths: List<CategoryPathItem> = emptyList()
)

data class CategoryPathItem(
    @SerializedName("file_id") val fileId: String = "0",
    @SerializedName("file_name") val fileName: String = ""
)
