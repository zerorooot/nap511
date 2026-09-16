package github.zerorooot.nap511.bean

import com.google.gson.annotations.SerializedName

/**
 * 迅雷字幕 API 响应根结构
 */
data class XunleiSubtitleResponse(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("result") val result: String? = null,
    @SerializedName("data") val data: List<XunleiSubtitleItem>? = null
)

/**
 * 迅雷字幕 API 返回的单条字幕数据
 */
data class XunleiSubtitleItem(
    @SerializedName("gcid") val gcid: String? = null,
    @SerializedName("cid") val cid: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("ext") val ext: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("simple_name") val simpleName: String? = null,
    @SerializedName("duration") val duration: Long = 0L, // 时长（毫秒）
    @SerializedName("languages") val languages: List<String>? = null,
    @SerializedName("source") val source: Int? = null,
    @SerializedName("score") val score: Int? = null,
    @SerializedName("fingerprintf_score") val fingerprintScore: Int? = null,
    @SerializedName("mt") val mt: Int? = null
)

/**
 * 字幕来源类型枚举
 */
enum class SubtitleSourceType(val label: String) {
    XUNLEI("迅雷"),
    ONE_ONE_FIVE("115网盘")
}

/**
 * 统一字幕数据模型，用于在界面展示与进行排序、下载与播放器绑定
 */
data class SubtitleItem(
    val id: String,                     // 唯一标识符（如 xunlei_gcid 或 115_pickcode）
    val name: String,                   // 原始文件名
    val simpleName: String,             // 显示名称
    val sourceType: SubtitleSourceType, // 来源类型（迅雷 / 115网盘）
    val url: String = "",               // 迅雷字幕静态下载 URL
    val pickCode: String = "",          // 115 文件 pickCode，用于动态换取下载 URL
    val fileId: String = "",            // 115 文件 fileId
    val ext: String = "srt",            // 文件后缀格式 (srt, vtt, ass, ssa, sub)
    val durationMs: Long = 0L,          // 字幕时长（毫秒）
    val languages: List<String> = emptyList() // 语言列表
)
