package github.zerorooot.nap511.terminal.commands.util

/**
 * 表格列对齐方式
 */
enum class TableAlignment {
    LEFT,
    RIGHT
}

/**
 * 表格列配置
 *
 * @property header 列标题
 * @property align 该列内容的对齐方式
 * @property minWidth 最小显示宽度
 */
data class TableColumn(
    val header: String,
    val align: TableAlignment = TableAlignment.LEFT,
    val minWidth: Int = 0
)

/**
 * 自适应 CJK（中日韩）全角字符对齐的终端表格格式化器
 *
 * 解决常见 `%10s` 格式化中因中文字符在终端等宽字体中占据 2 个字符宽度而导致的排版锯齿错位问题。
 */
class TableFormatter(
    private val columns: List<TableColumn>,
    private val columnSpacing: Int = 2
) {
    companion object {
        /**
         * 计算字符串在终端中的 East Asian 视觉显示宽度
         * 半角字符（英文、数字、标准符号）计 1，全角字符（中文、日文、全角标点）计 2
         */
        fun measureEastAsianWidth(text: String): Int {
            var width = 0
            for (ch in text) {
                width += if (isEastAsianWide(ch)) 2 else 1
            }
            return width
        }

        /**
         * 判断字符是否属于东亚全角字符范畴
         */
        private fun isEastAsianWide(ch: Char): Boolean {
            val code = ch.code
            return (code in 0x1100..0x115F) || // Hangul Jamo
                    (code in 0x2E80..0xA4CF && code != 0x303F) || // CJK Radicals, CJK Unified Ideographs, Yi
                    (code in 0xAC00..0xD7A3) || // Hangul Syllables
                    (code in 0xF900..0xFAFF) || // CJK Compatibility Ideographs
                    (code in 0xFE30..0xFE6F) || // CJK Compatibility Forms
                    (code in 0xFF01..0xFF60) || // Fullwidth ASCII variants
                    (code in 0xFFE0..0xFFE6)    // Fullwidth symbols
        }

        /**
         * 填充单个单元格，使其视觉显宽严格等于 [targetWidth]
         */
        fun padCell(value: String, targetWidth: Int, align: TableAlignment): String {
            val currentWidth = measureEastAsianWidth(value)
            val paddingNeeded = (targetWidth - currentWidth).coerceAtLeast(0)
            if (paddingNeeded == 0) return value
            val spaces = " ".repeat(paddingNeeded)
            return when (align) {
                TableAlignment.LEFT -> value + spaces
                TableAlignment.RIGHT -> spaces + value
            }
        }
    }

    /**
     * 流式表格构建器 (Builder)
     */
    class Builder {
        private val columns = mutableListOf<TableColumn>()
        private val rows = mutableListOf<List<String>>()
        private var spacing = 2

        fun addColumn(
            header: String,
            align: TableAlignment = TableAlignment.LEFT,
            minWidth: Int = 0
        ): Builder {
            columns.add(TableColumn(header, align, minWidth))
            return this
        }

        fun setColumnSpacing(spacing: Int): Builder {
            this.spacing = spacing
            return this
        }

        fun addRow(vararg values: String): Builder {
            rows.add(values.toList())
            return this
        }

        fun addRow(values: List<String>): Builder {
            rows.add(values)
            return this
        }

        /**
         * 自适应计算各列最大宽度并生成格式化好的行字符串列表
         */
        fun build(): List<String> {
            if (columns.isEmpty()) return emptyList()

            // 1. 计算每列实际所需的最大宽度（max(列头宽度, 所有行对应单元格宽度, minWidth)）
            val colWidths = IntArray(columns.size) { i ->
                val col = columns[i]
                var maxW = measureEastAsianWidth(col.header).coerceAtLeast(col.minWidth)
                for (row in rows) {
                    val cellVal = row.getOrNull(i) ?: ""
                    val w = measureEastAsianWidth(cellVal)
                    if (w > maxW) {
                        maxW = w
                    }
                }
                maxW
            }

            val separator = " ".repeat(spacing)
            val result = mutableListOf<String>()

            // 2. 渲染表头（若所有列头均为空串则略过表头渲染）
            val hasHeader = columns.any { it.header.isNotBlank() }
            if (hasHeader) {
                val headerLine = columns.mapIndexed { i, col ->
                    padCell(col.header, colWidths[i], col.align)
                }.joinToString(separator).trimEnd()
                result.add(headerLine)
            }

            // 3. 渲染数据行
            for (row in rows) {
                val rowLine = columns.mapIndexed { i, col ->
                    val cellVal = row.getOrNull(i) ?: ""
                    padCell(cellVal, colWidths[i], col.align)
                }.joinToString(separator).trimEnd()
                result.add(rowLine)
            }

            return result
        }
    }
}
