package github.zerorooot.nap511.bean

import android.os.Parcelable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
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
    ARCHIVE("压缩", Icons.Default.FolderZip),
    TORRENT("种子", Icons.AutoMirrored.Filled.InsertDriveFile)
}

data class FilesBean(
    @SerializedName("data") var fileBeanList: ArrayList<FileBean>,
    var cid: String,
    var count: Int,
    var order: String,
    var path: List<PathBean>
)

object OrderEnum {
    const val change = "user_ptime"
    const val type = "file_type"
    const val name = "file_name"
    const val size = "file_size"
}

data class OrderBean(var type: String = OrderEnum.name, var asc: Int = 1) {
    override fun toString(): String {
        val order = if (asc == 1) "⬆️" else "⬇️"
        val name = when (type) {
            OrderEnum.name -> "文件名称"
            OrderEnum.change -> "更改时间"
            OrderEnum.type -> "文件种类"
            OrderEnum.size -> "文件大小"
            else -> "文件名称"
        }
        return "$name$order"
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
    @SerializedName("te") val updateTime: String = "",
    @SerializedName("tp") val createTime: String = "",
    @SerializedName("t") val modifiedTime: String = "",
    @SerializedName("iv") val isVideo: Int = 0,
    @SerializedName("u") val photoThumb: String = "",
    @SerializedName("fuuid") val uuid: Long = 0,
    @SerializedName("sha") val sha1: String = "",
    @SerializedName("s") val size: String = "0",
    @SerializedName("current_time") val currentPlayTime: Int = 0,
    @SerializedName("play_long") val playLong: Double = 0.00,
    val isFolder: Boolean = false,
    val updateTimeString: String = "",
    val createTimeString: String = "",
    val playLongString: String = "",
    val modifiedTimeString: String = "",
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
