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
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import github.zerorooot.nap511.R
import kotlinx.parcelize.Parcelize
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

data class Base115Response<T>(
    val state: Boolean = false,
    val data: T? = null
)

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

// 统一定义接口返回的包装结构
open class BaseResponse(
    @SerializedName("state") val state: Boolean = false,
    @SerializedName("msg", alternate = ["message"]) val message: String = "",
    @SerializedName("msg_code") val msgCode: Int = 0
) {
    override fun toString(): String {
        return Gson().toJson(this)
    }
}


data class MusicBean(
    val state: Boolean = false,
    val url: String = "",
    @SerializedName("audio_url")
    val audioUrl: String = ""
)

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

// {"state":true,"message":"","code":"","data":{"unzip_status":4}}
data class EncryptionDataResponse(
    @SerializedName("data")
    val data: EncryptionData
) : BaseResponse()

data class EncryptionData(
    @SerializedName("unzip_status")
    val unzipStatus: Int = -1
)

// {"state":true,"message":"","code":"","data":{"extract_id":1231231}}
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

data class SignBean(
    @SerializedName("state") var state: Boolean = false,
//    @SerializedName("data") var data: Int = -1,
    @SerializedName("size") var size: String = "",
    @SerializedName("url") var url: String = "",
    @SerializedName("bt_url") var btUrl: String = "",
//    @SerializedName("limit") var limit: Int = -1,
    @SerializedName("sign") var sign: String = "",
    @SerializedName("time") var time: Int = -1
)

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
//    @SerializedName("vdi") val videoDefinition: Int = 0,
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

//data class FileBeanDownload(var downloadUrl: String,val name: String)

/**
 * {
"thumb_url": "http://static.115.com/video/xxxxx.jpg",
"height": "720",
"width": "1280",
"video_url": "http://115.com/api/video/m3u8/xxxxx.m3u8",
"play_long": "2434",
"pick_code": "xxxxx",
"file_name": "xxxx.mp4",
"file_size": "500225856",
"parent_id": "xxx",
"file_id": "xxxx",
"is_mark": "0",
"sha1": "xxxxx",
"user_def": "3000000",
"user_rotate": 0,
"user_turn": 0
}
 */
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
){
    // 外部直接调用的属性
    val downloadUrl: String
        get() = when (rawDownloadUrl) {
            is String -> rawDownloadUrl
            // 当返回 [] 时，Gson 会解析为 ArrayList
            is List<*> -> ""
            else -> ""
        }
}

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

// 1. 定义专属的 Item 数据类，并给 onClick 赋默认值 {} (空操作)
data class InfoItem(
    val label: String,
    val value: String,
    val customContent: (@Composable () -> Unit)? = null // 新增：自定义内容槽位
)

data class PathsBean(
    @SerializedName("file_id") var fileId: String = "",
    @SerializedName("file_name") var fileName: String = ""
)

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

data class BaseReturnMessage(
    @SerializedName("state") var state: Boolean = false,
    @SerializedName("error") var error: String = "",
    @SerializedName("errno") var errno: String = "",
    var message: String = "",
    @SerializedName("error_msg") var errorMsg: String = ""
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

data class OfflineListCount(
    @SerializedName("total_count") val totalCount: Int = 0,
    @SerializedName("failed_count") val failedCount: Int = 0,
    @SerializedName("finished_count") val finishedCount: Int = 0,
    @SerializedName("downloading_count") val downloadingCount: Int = 0,
    @SerializedName("state") val state: Boolean = false,
    @SerializedName("errtype") val errtype: String = "",
    @SerializedName("errcode") val errcode: Int = 0
)

data class OfflineInfo(
    @SerializedName("page") var page: Int = -1,
    @SerializedName("page_count") var pageCount: Int = -1,
    @SerializedName("page_row") var pageRow: Int = -1,
    @SerializedName("count") var count: Int = -1,
    @SerializedName("quota") var quota: Int = -1,
    @SerializedName("total") var total: Int = -1,
    @SerializedName("tasks") private var _tasks: ArrayList<OfflineTask>? = null,
    @SerializedName("state") var state: Boolean = false,
    var isLoadedComplete: Boolean = false
//    @SerializedName("errtype") var errtype: String = ""
) {
    // 对外暴露的 tasks 变量：如果 _tasks 为 null，自动初始化并返回一个空的 ArrayList
    var tasks: ArrayList<OfflineTask>
        get() = _tasks ?: arrayListOf<OfflineTask>().also { _tasks = it }
        set(value) {
            _tasks = value
        }
}

enum class OfflineTaskType(val stat: Int) {
    DownloadingList(12),
    FailedList(9),
    CompletedList(11)
}

data class OfflineTask(
    @SerializedName("info_hash") var infoHash: String = "",
    @SerializedName("add_time") var addTime: Long = -1,
    @SerializedName("percentDone") var percentDone: Double = 0.0,
    @SerializedName("size") var size: Long = -1,
    @SerializedName("name") var name: String = "",
    @SerializedName("file_id") var fileId: String = "",
    @SerializedName("delete_file_id") var deleteFileId: String = "",
    @SerializedName("status") var status: Int = -1,
    @SerializedName("url") var url: String = "",
    @SerializedName("del_path") var delPath: String = "",
    @SerializedName("wp_path_id") var wpPathId: String = "",
    @SerializedName("can_appeal") var canAppeal: Int = -1,
    var sizeString: String = "",
    var timeString: String = "",
    var percentString: String = ""
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

/**
 * {
 *      "state": true,
 *      "errno": 0,
 *      "errtype": "suc",
 *      "errcode": 0,
 *      "file_size": 70966705837,
 *      "torrent_name": "name",
 *      "file_count": 28,
 *      "info_hash": "hash",
 *      "torrent_filelist_web": [
 *           {
 *                "size": 3902418,
 *                "path": "预览图/2021_04_24_07_37_IMG_1379.JPG",
 *                "wanted": 1
 *           }
 *      ]
 * }
 */
data class TorrentFileBean(
    var state: Boolean = false,
    var errno: Long = 0,
    @SerializedName("error_msg")
    var errorMessage: String = "种子文件解析失败",
    var errtype: String = "suc",
    var errcode: Long = 0,
    @SerializedName("file_size")
    var fileSize: Long = 0,
    var fileSizeString: String = "",
    @SerializedName("torrent_name")
    var torrentName: String = "",
    @SerializedName("file_count")
    var fileCount: Int = 0,
    @SerializedName("info_hash")
    var infoHash: String = "",
    @SerializedName("torrent_filelist_web")
    var torrentFileListWeb: ArrayList<TorrentFileListWeb> = arrayListOf(),
)

data class TorrentFileListWeb(
    var size: Long = 0,
    var sizeString: String = "",
    var path: String = "",
    /**
     * 1是选中的
     *
     * 0的未选中的
     *
     * -1是_____padding_file_0_如果您看到此文件，请升级到BitComet(比特彗星)0.85或以上版本____
     */
    val wanted: Int = -1,
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


//----------------文件查重--------------------------
//{"state":false,"msg":"","msg_code":0,"data":{"group_count":0,"file_count":0,"file_size":0}}
//{"state":true,"msg":"","msg_code":0,"data":{"group_count":"766","file_count":"1632","file_size":"216379446654"}}
// 1. 重复文件状态响应
data class RepeatStatusResponse(
    @SerializedName("data") val data: RepeatStatusData? = null
) : BaseResponse()

data class RepeatStatusData(
    // 兼容 API 返回字符串或数值类型
    @SerializedName("group_count") val groupCount: String = "0",
    @SerializedName("file_count") val fileCount: String = "0",
    @SerializedName("file_size") val fileSize: String = "0"
)

// 2. 重复文件列表响应
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

// 分类/文件详情响应
data class CategoryDetailResponse(
    @SerializedName("state") val state: Boolean = false,
    @SerializedName("file_name") val fileName: String = "",
    @SerializedName("size") val size: String = "",
    @SerializedName("file_category") val fileCategory: String = "",
    @SerializedName("ctime") val ctime: String = "", // 创建时间戳
    @SerializedName("utime") val utime: String = "", // 修改时间戳
    @SerializedName("paths") val paths: List<CategoryPathItem> = emptyList()
)

data class CategoryPathItem(
    @SerializedName("file_id") val fileId: String = "0",
    @SerializedName("file_name") val fileName: String = ""
)