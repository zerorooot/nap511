package github.zerorooot.nap511.bean

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Immutable
data class OfflineListCount(
    @SerializedName("total_count") val totalCount: Int = 0,
    @SerializedName("failed_count") val failedCount: Int = 0,
    @SerializedName("finished_count") val finishedCount: Int = 0,
    @SerializedName("downloading_count") val downloadingCount: Int = 0,
    @SerializedName("state") val state: Boolean = false,
    @SerializedName("errtype") val errtype: String = "",
    @SerializedName("errcode") val errcode: Int = 0
)

@Immutable
data class OfflineInfo(
    @SerializedName("page") val page: Int = -1,
    @SerializedName("page_count") val pageCount: Int = -1,
    @SerializedName("page_row") val pageRow: Int = -1,
    @SerializedName("count") val count: Int = -1,
    @SerializedName("quota") val quota: Int = -1,
    @SerializedName("total") val total: Int = -1,
    @SerializedName("tasks") val tasks: List<OfflineTask> = emptyList(),
    @SerializedName("state") val state: Boolean = false,
    val isLoadedComplete: Boolean = false
)

enum class OfflineTaskType(val stat: Int) {
    DownloadingList(12),
    FailedList(9),
    CompletedList(11)
}

@Immutable
@Serializable
data class OfflineTask(
    @SerializedName("info_hash") val infoHash: String = "",
    @SerializedName("add_time") val addTime: Long = -1,
    @SerializedName("percentDone") val percentDone: Double = 0.0,
    @SerializedName("size") val size: Long = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("file_id") val fileId: String = "",
    @SerializedName("delete_file_id") val deleteFileId: String = "",
    @SerializedName("status") val status: Int = -1,
    @SerializedName("url") val url: String = "",
    @SerializedName("del_path") val delPath: String = "",
    @SerializedName("wp_path_id") val wpPathId: String = "",
    @SerializedName("can_appeal") val canAppeal: Int = -1,
    val sizeString: String = "",
    val timeString: String = "",
    val percentString: String = ""
)
