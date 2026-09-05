package github.zerorooot.nap511.bean

import com.google.gson.annotations.SerializedName

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
    val wanted: Int = -1,
)
