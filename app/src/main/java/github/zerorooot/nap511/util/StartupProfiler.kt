package github.zerorooot.nap511.util

import android.os.Process
import android.os.SystemClock
import github.zerorooot.nap511.BuildConfig

/**
 * 启动全流程耗时监控与分析工具 (零外部依赖，使用 kotlin.io.println 直出控制台)
 * 仅在 BuildConfig.DEBUG 模式下启用并输出，避免生产环境产生多余性能损耗。
 */
object StartupProfiler {
    private const val PREFIX = "[Startup]"

    /**
     * 控制打点输出开关，默认仅在 DEBUG 模式下启用
     */
    internal var isEnabled: Boolean = BuildConfig.DEBUG

    private val processStartTime: Long by lazy {
        Process.getStartUptimeMillis()
    }

    private var lastTime: Long = processStartTime
    private val records = mutableListOf<StepRecord>()

    data class StepRecord(
        val name: String,
        val costMs: Long,
        val totalMs: Long,
        val extra: String = ""
    )

    /**
     * 记录启动阶段打点
     * @param name 当前阶段名称
     * @param extra 额外备注信息（可选）
     */
    @Synchronized
    fun step(name: String, extra: String = "") {
        if (!isEnabled) return
        val now = SystemClock.uptimeMillis()
        val cost = now - lastTime
        val total = now - processStartTime
        lastTime = now
        val record = StepRecord(name, cost, total, extra)
        records.add(record)
        val extraStr = if (extra.isNotEmpty()) " [$extra]" else ""
        println(
            "$PREFIX ⏱️ [+${cost.toString().padStart(4)} ms | Σ${
                total.toString().padStart(5)
            } ms] -> $name$extraStr"
        )
    }

    /**
     * 结束启动打点并格式化输出完整的“启动耗时汇总看板”报表
     */
    @Synchronized
    fun finish(label: String = "Startup Completed") {
        if (!isEnabled) return
        step(label)
        val sb = StringBuilder()
        sb.append("\n$PREFIX ==================== 🚀 启动耗时看板 🚀 ====================\n")
        records.forEach { r ->
            val extraStr = if (r.extra.isNotEmpty()) " [${r.extra}]" else ""
            sb.append(
                "$PREFIX  • ${r.name.padEnd(42)} : 耗时 ${
                    r.costMs.toString().padStart(4)
                } ms (累计 ${r.totalMs.toString().padStart(5)} ms)$extraStr\n"
            )
        }
        val grandTotal = SystemClock.uptimeMillis() - processStartTime
        sb.append("$PREFIX ==================== 🏁 总计耗时: $grandTotal ms ====================\n")
        println(sb.toString())
    }

    /**
     * 重置打点记录（供测试使用）
     */
    @Synchronized
    internal fun resetForTesting() {
        records.clear()
        lastTime = processStartTime
    }

    /**
     * 获取打点记录副本（供测试断言使用）
     */
    @Synchronized
    internal fun getRecordsForTesting(): List<StepRecord> = records.toList()
}
