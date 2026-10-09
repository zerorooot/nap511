package github.zerorooot.nap511.worker

import com.google.gson.annotations.SerializedName
import github.zerorooot.nap511.bean.FileBean
import java.util.UUID

data class UnzipTaskItem(
    @SerializedName("taskId")
    val taskId: String = UUID.randomUUID().toString(),
    @SerializedName("fileBean")
    val fileBean: FileBean,
    @SerializedName("targetCid")
    val targetCid: String,
    @SerializedName("password")
    val password: String? = null,
    @SerializedName("errorCid")
    val errorCid: String? = null,
    @SerializedName("createdAt")
    val createdAt: Long = System.currentTimeMillis()
)
