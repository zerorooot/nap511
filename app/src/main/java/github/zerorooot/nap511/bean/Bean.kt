package github.zerorooot.nap511.bean

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import github.zerorooot.nap511.R
import kotlinx.parcelize.Parcelize
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody


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
    var allRemain: Long = 1L,
    var allRemainString: String = "1T",
    var allTotal: Long = 1L,
    var allTotalString: String = "1T",
    var allUse: Long = 1L,
    var allUseString: String = "1T",
)


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

@Parcelize
data class FileBean(
    @SerializedName("cid") var categoryId: String = "",
    @SerializedName("ico") var icoString: String = "",
    @SerializedName("aid") var areaId: String = "",
    @SerializedName("pid") var parentId: String = "",
    @SerializedName("n") var name: String = "",
    @SerializedName("fid") var fileId: String = "",
    @SerializedName("pc") var pickCode: String = "",
    @SerializedName("te") var updateTime: String = "",
    @SerializedName("tp") var createTime: String = "",
    @SerializedName("t") var modifiedTime: String = "",
    @SerializedName("iv") var isVideo: Int = 0,
    @SerializedName("u") var photoThumb: String = "",
//    @SerializedName("vdi") var videoDefinition: Int = 0,
    @SerializedName("fuuid") var uuid: Long = 0,
    @SerializedName("sha") var sha1: String = "",
    @SerializedName("s") var size: String = "0",
    @SerializedName("current_time") var currentPlayTime: Int = 0,
    @SerializedName("play_long") var playLong: Double = 0.00,
    var isFolder: Boolean = false,
    var updateTimeString: String = "",
    var createTimeString: String = "",
    var playLongString: String = "",
    var modifiedTimeString: String = "",
    var sizeString: String = "",
    var isSelect: Boolean = false,
    var fileIco: Int = R.drawable.other
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
    @SerializedName("user_turn") var userTurn: Int = 0
)

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
    @SerializedName("file_category") var fileCategory: String = ""
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

data class OfflineInfo(
    @SerializedName("page") var page: Int = -1,
    @SerializedName("page_count") var pageCount: Int = -1,
    @SerializedName("page_row") var pageRow: Int = -1,
    @SerializedName("count") var count: Int = -1,
    @SerializedName("quota") var quota: Int = -1,
    @SerializedName("total") var total: Int = -1,
    @SerializedName("tasks") var tasks: ArrayList<OfflineTask> = arrayListOf(),
    @SerializedName("state") var state: Boolean = false,
//    @SerializedName("errtype") var errtype: String = ""
)

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
    var errorMessage: String = "",
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
