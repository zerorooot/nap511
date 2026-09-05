package github.zerorooot.nap511.bean

import com.google.gson.annotations.SerializedName

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
) {
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
