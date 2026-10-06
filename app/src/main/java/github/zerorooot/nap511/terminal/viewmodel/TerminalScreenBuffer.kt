package github.zerorooot.nap511.terminal.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * 终端屏幕输出与回滚缓冲管理器 (Terminal Screen Buffer)
 *
 * 职责：
 * 1. 高内聚管理终端展示行列表 (`SnapshotStateList<TerminalLine>`)，供 Compose 响应式渲染。
 * 2. 封装最大回滚行数 (Scrollback Limit) 的截断与内存膨胀保护。
 * 3. 支持批量行追加，降低 Compose 重组频率，保证海量输出时的流畅度。
 * 4. 提供清屏与屏幕全文提取功能。
 *
 * 设计定位：
 * 纯数据与缓冲模型，与 Android ViewModel 生命周期及特定协程环境解耦，可在任何控制台 UI 或日志测试中高复用。
 */
class TerminalScreenBuffer(
    val maxScrollbackLines: Int = DEFAULT_MAX_SCROLLBACK_LINES
) {
    companion object {
        /**
         * 默认终端屏幕输出最大保留行数上限 (Scrollback Limit)
         * 避免长时间运行或海量输出导致内存暴涨与掉帧
         */
        const val DEFAULT_MAX_SCROLLBACK_LINES = 2000
    }

    /**
     * 响应式终端行状态列表，由 Compose Snapshot 系统直接观察
     */
    val lines: SnapshotStateList<TerminalLine> = mutableStateListOf()

    val size: Int get() = lines.size

    val isEmpty: Boolean get() = lines.isEmpty()

    /**
     * 安全向终端输出追加单行，带最大回滚行数截断保护，防止长期运行导致内存膨胀
     */
    fun appendLine(line: TerminalLine) {
        if (lines.size >= maxScrollbackLines) {
            val removeCount = (lines.size - maxScrollbackLines + 1).coerceAtLeast(1)
            lines.subList(0, removeCount.coerceAtMost(lines.size)).clear()
        }
        lines.add(line)
    }

    /**
     * 批量追加终端输出，降低 Compose 重组频率，保证海量输出流畅度
     */
    fun appendLines(newLines: List<TerminalLine>) {
        if (newLines.isEmpty()) return
        val effectiveNewLines = if (newLines.size > maxScrollbackLines) {
            newLines.takeLast(maxScrollbackLines)
        } else {
            newLines
        }
        val total = lines.size + effectiveNewLines.size
        if (total > maxScrollbackLines) {
            val removeCount = (total - maxScrollbackLines).coerceAtLeast(1)
            lines.subList(0, removeCount.coerceAtMost(lines.size)).clear()
        }
        lines.addAll(effectiveNewLines)
    }

    /**
     * 清空屏幕所有内容
     */
    fun clear() {
        lines.clear()
    }

    /**
     * 提取当前屏幕上所有行的纯文本（以换行符连接），用于快捷复制等场景
     */
    fun getAllText(): String {
        return lines.joinToString("\n") { it.text }
    }
}
