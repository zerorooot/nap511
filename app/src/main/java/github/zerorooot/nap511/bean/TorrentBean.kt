package github.zerorooot.nap511.bean

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

@Immutable
data class TorrentFileBean(
    val state: Boolean = false,
    val errno: Long = 0,
    @SerializedName("error_msg")
    val errorMessage: String = "",
    val errtype: String = "suc",
    val errcode: Long = 0,
    @SerializedName("file_size")
    val fileSize: Long = 0,
    val fileSizeString: String = "",
    @SerializedName("torrent_name")
    val torrentName: String = "",
    @SerializedName("file_count")
    val fileCount: Int = 0,
    @SerializedName("info_hash")
    val infoHash: String = "",
    @SerializedName("torrent_filelist_web")
    val torrentFileListWeb: List<TorrentFileListWeb> = emptyList(),
)

@Immutable
data class TorrentFileListWeb(
    val size: Long = 0,
    val sizeString: String = "",
    val path: String = "",
    val wanted: Int = -1,
)
