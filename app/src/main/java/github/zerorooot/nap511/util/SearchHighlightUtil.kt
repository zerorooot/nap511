package github.zerorooot.nap511.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * 通用搜索高亮与匹配工具类 (SearchHighlightUtil)
 *
 * 为应用内多处具有列表搜索高亮需求的场景（如 [TxtReaderScreen] 文本阅读器、[LogScreen] 日志查看器）
 * 提供统一的匹配计算算法模型与高亮 AnnotatedString 构建函数。
 */

/**
 * 通用文本搜索匹配项位置模型
 *
 * @property globalIndex 全局匹配项序号（从 0 开始计数，用于 "1/15" 导航和当前激活项判断）
 * @property itemIndex 匹配项所属的数据项索引（如段落 index、日志行 index）
 * @property startChar 关键字在当前数据项原始文本中的起始字符下标
 * @property length 匹配关键字的字符长度
 */
data class TextSearchMatch(
    val globalIndex: Int,
    val itemIndex: Int,
    val startChar: Int,
    val length: Int
) {
    /** 兼容 TxtReaderScreen 段落索引命名 */
    val paragraphIndex: Int get() = itemIndex

    /** 兼容 LogScreen 日志行索引命名 */
    val logIndex: Int get() = itemIndex

    /** 兼容原始绝对下标命名 */
    val startCharInRaw: Int get() = startChar
}

/** 统一类型别名，确保向后兼容 */
typealias SearchMatch = TextSearchMatch
typealias LogSearchMatch = TextSearchMatch

/**
 * 针对任意文本集合批量执行大小写无关的关键字搜索，计算所有全局排序的匹配项列表
 *
 * @param items 待搜索的数据集合（如段落列表、日志对象列表）
 * @param searchQuery 用户输入的搜索关键字
 * @param textSelector 从元素中提取目标纯文本的选择器
 * @return 匹配项列表
 */
fun <T> findSearchMatches(
    items: List<T>,
    searchQuery: String,
    textSelector: (T) -> String = { it.toString() }
): List<TextSearchMatch> {
    if (searchQuery.isBlank() || items.isEmpty()) return emptyList()

    val results = mutableListOf<TextSearchMatch>()
    var globalIdx = 0

    items.forEachIndexed { itemIdx, item ->
        val text = textSelector(item)
        var startIndex = 0
        while (startIndex < text.length) {
            val foundIndex = text.indexOf(searchQuery, startIndex, ignoreCase = true)
            if (foundIndex == -1) break
            results.add(
                TextSearchMatch(
                    globalIndex = globalIdx++,
                    itemIndex = itemIdx,
                    startChar = foundIndex,
                    length = searchQuery.length
                )
            )
            startIndex = foundIndex + searchQuery.length
        }
    }
    return results
}

/**
 * 根据搜索关键字构建带有背景高亮样式的 [AnnotatedString]
 *
 * @param text 待绘制的目标纯文本
 * @param searchQuery 搜索关键字
 * @param matches 属于当前项的匹配项列表
 * @param textStartInRaw 若当前 text 仅为子串，传入其在 raw 原始文本中的起始偏移（默认为 0）
 * @param currentMatchIndex 当前全局激活的匹配项序号
 * @param activeHighlightColor 当前焦点匹配项背景色（默认明亮橙色）
 * @param inactiveHighlightColor 普通匹配项背景色（默认浅黄色）
 * @param textColor 高亮区域文字前景色（默认黑色）
 */
fun buildSearchHighlightedText(
    text: String,
    searchQuery: String,
    matches: List<TextSearchMatch> = emptyList(),
    textStartInRaw: Int = 0,
    currentMatchIndex: Int = -1,
    activeHighlightColor: Color = Color(0xFFFF9800),
    inactiveHighlightColor: Color = Color(0xFFFFE082),
    textColor: Color = Color.Black
): AnnotatedString {
    if (searchQuery.isBlank() || text.isEmpty()) {
        return AnnotatedString(text)
    }

    return buildAnnotatedString {
        append(text)
        if (matches.isNotEmpty()) {
            matches.forEach { match ->
                val startInText = match.startChar - textStartInRaw
                val endInText = startInText + match.length
                if (startInText >= 0 && endInText <= text.length) {
                    val isActive = (match.globalIndex == currentMatchIndex)
                    addStyle(
                        style = SpanStyle(
                            background = if (isActive) activeHighlightColor else inactiveHighlightColor,
                            color = textColor
                        ),
                        start = startInText,
                        end = endInText
                    )
                }
            }
        } else {
            // 当未提供精准 matches 集合时的平滑回退：在 text 内部就地搜索高亮
            var startIndex = 0
            while (startIndex < text.length) {
                val foundIndex = text.indexOf(searchQuery, startIndex, ignoreCase = true)
                if (foundIndex == -1) break
                addStyle(
                    style = SpanStyle(
                        background = inactiveHighlightColor,
                        color = textColor
                    ),
                    start = foundIndex,
                    end = foundIndex + searchQuery.length
                )
                startIndex = foundIndex + searchQuery.length
            }
        }
    }
}
