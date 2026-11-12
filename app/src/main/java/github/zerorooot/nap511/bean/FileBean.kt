package github.zerorooot.nap511.bean

import android.os.Parcelable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
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

@Immutable
data class FilesBean(
    @SerializedName("data") val fileBeanList: List<FileBean> = emptyList(),
    val cid: String = "",
    val count: Int = 0,
    val order: String = "",
    val path: List<PathBean> = emptyList(),
    /**
     * is_asc = 1：升序（正序，从小到大 / A-Z / 从旧到新）。
     *
     * is_asc = 0：降序（倒序，从大到小 / Z-A / 从新到旧）。
     */
    @SerializedName("is_asc") val isAsc: Int = 1,
    /**
     * fc_mix = 0：文件夹置顶（默认行为）。
     * fc_mix = 1：文件与文件夹混排。
     */
    @SerializedName("fc_mix") val fcMix: Int = 0,
) {
    /** 当前缓存是否已完整拉取全部条目 */
    val isComplete: Boolean
        get() = count <= 0 || fileBeanList.size >= count

    /**
     * 三位一体缓存匹配门禁：
     * 1. 完备性：必须是全量拉取完成的缓存
     * 2. 排序一致性：排序字段与升降序必须与预期一致
     * 3. 混排模式一致性：fcMix 必须与当前设置一致
     */
    fun isFullyValid(expectedOrder: OrderBean, expectedFcMix: Int): Boolean {
        return isComplete &&
                order == expectedOrder.type &&
                isAsc == expectedOrder.asc &&
                fcMix == expectedFcMix
    }
}

object OrderEnum {
    const val create = "user_ptime"
    const val type = "file_type"
    const val name = "file_name"
    const val size = "file_size"
    const val update = "user_utime"
    const val open = "user_otime"
    const val star = "star_time"

    // 本地自定义排序标识（仅在客户端内存排序，不同步至 115 服务端）
    const val videoDuration = "video_duration"
    const val starFile = "star_file"
}

enum class OrderType(
    val rawValue: String,
    val label: String,
    val isShow: Boolean = true,
    val isLocalOnly: Boolean = false
) {
    NAME(OrderEnum.name, "文件名称"),
    SIZE(OrderEnum.size, "文件大小"),
    TYPE(OrderEnum.type, "文件种类"),
    UPDATE(OrderEnum.update, "修改时间"),
    CREATE(OrderEnum.create, "创建时间"),
    OPEN(OrderEnum.open, "打开时间"),
    STAR(OrderEnum.star, "星标时间", false),

    // 本地自定义排序项
    VIDEO_DURATION(OrderEnum.videoDuration, "视频时长", isShow = true, isLocalOnly = true),
    STAR_FILE(OrderEnum.starFile, "标星文件", isShow = true, isLocalOnly = true);

    val icon: ImageVector
        get() = when (this) {
            NAME -> Icons.Default.SortByAlpha
            SIZE -> Icons.Default.Storage
            TYPE -> Icons.Default.Category
            UPDATE -> Icons.Default.AccessTime
            CREATE -> Icons.Default.CalendarToday
            OPEN -> Icons.Default.History
            STAR -> Icons.Default.StarOutline
            VIDEO_DURATION -> Icons.Outlined.Schedule
            STAR_FILE -> Icons.Outlined.StarOutline
        }

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

data class QuotaBean(val count: Int, val surplus: Int)
data class PathBean(val cid: String, val name: String, val pid: String)

data class LocationBean(val firstVisibleItemIndex: Int, val firstVisibleItemScrollOffset: Int)

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
    @SerializedName("m") val star: Int = -1,
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
    /**
     * 文件/文件夹是否置顶，1 为置顶，0 为不置顶
     */
    @SerializedName("is_top") val isTop: Int = 0,
    val isPinned: Boolean = false,
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
    val isStar: Boolean = false,
    val sizeString: String = "",
    // 标记为 @Transient，避免 Gson 保存到磁盘 json 中
    @Transient val fileIco: Int = R.drawable.other
) : Parcelable

/**
 * 文件唯一业务标识（兼容常规文件、文件夹与离线预览条目）
 */
val FileBean.uniqueId: String
    get() = fileId.ifEmpty { categoryId.ifEmpty { pickCode } }

@Immutable
data class FileInfo(
    @SerializedName("count") val count: String = "",
    @SerializedName("size") val size: String = "",
    @SerializedName("folder_count") val folderCount: String = "",
    @SerializedName("ptime") val createTime: String = "",
    @SerializedName("utime") val changeTime: String = "",
    @SerializedName("play_long") val playLong: Int = 0,
    @SerializedName("file_name") val fileName: String = "",
    @SerializedName("pick_code") val pickCode: String = "",
    @SerializedName("sha1") val sha1: String = "",
    @SerializedName("open_time") val openTime: Int = 0,
    @SerializedName("desc") val desc: String = "",
    @SerializedName("file_category") val fileCategory: String = "",
    val paths: List<PathsBean> = emptyList()
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
