package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.ast.PositionalArgumentNode

/**
 * 阶梯式多级通配符展开器 (Multi-Level Glob Expander)
 *
 * 遵循 POSIX.1-2017 路径通配展开规范与网盘安全防御机制：
 * 1. 字符级掩码切片（Mask Slicing）：将路径按 '/' 分段时同步切片每段的 quoteMask，保证子路径中的转义与引号状态绝对不失真；
 * 2. 缓存优先策略（Cache-First）：下潜展开过程中优先复用本地目录缓存，降低 HTTP API 请求压力；
 * 3. 安全熔断与告警（Safety Throttling）：常量 [MAX_WILDCARD_FANOUT_DIRS] 限制单层通配最大允许下潜的目录数，超出时告警并截断；
 * 4. 绝对路径与相对路径全兼容：统一处理根目录 "/"（CID "0"）与当前工作目录；
 * 5. POSIX Nomatch Fallback：当没有任何项匹配时，严格保留原始字面量，交由下游命令输出标准报错。
 */
object MultiLevelGlobExpander {

    /** 单层通配最大允许下潜的子目录数量上限 */
    const val MAX_WILDCARD_FANOUT_DIRS = 3

    /**
     * 对位置参数执行阶梯式多级通配符展开
     *
     * @param ctx 终端上下文
     * @param node 位置参数语法节点（包含文本与字符级受保护掩码）
     * @param onWarning 触发熔断保护时的告警回调
     * @return 展开后的路径或文件名列表
     */
    suspend fun expand(
        ctx: TerminalContext,
        node: PositionalArgumentNode,
        onWarning: suspend (String) -> Unit = {}
    ): List<String> {
        if (!node.hasUnquotedWildcards) {
            return listOf(node.text)
        }

        val isAbsolute = node.text.startsWith("/")
        val segmentsWithMask = sliceSegmentsWithMask(node.text, node.quoteMask)

        // 初始路径集合：绝对路径从 "/" 开始，相对路径从 "" 开始
        var currentPaths = listOf(if (isAbsolute) "/" else "")

        for ((segIndex, seg) in segmentsWithMask.withIndex()) {
            // 绝对路径的首个前导空段跳过
            if (isAbsolute && segIndex == 0 && seg.text.isEmpty()) {
                continue
            }

            val isLastSegment = (segIndex == segmentsWithMask.lastIndex)
            val nextPaths = mutableListOf<String>()

            for (parentPath in currentPaths) {
                val parentCid = when {
                    parentPath.isEmpty() -> ctx.currentCid
                    parentPath == "/" -> "0"
                    else -> ctx.resolveDirectory(parentPath)?.cid
                } ?: continue

                // 缓存优先拉取子节点
                val children = runCatching {
                    ctx.listDirectory(parentCid, forceRefresh = false)
                }.getOrDefault(emptyList())

                val matchedItems = children.filter { child ->
                    val typeMatches = isLastSegment || child.isFolder
                    typeMatches && GlobMatcher.matches(seg.text, seg.mask, child.name)
                }.sortedBy { it.name }

                // 触发单层下潜子目录数量熔断保护
                if (!isLastSegment && matchedItems.size > MAX_WILDCARD_FANOUT_DIRS) {
                    onWarning(
                        "terminal: glob: '${seg.text}' 匹配到 ${matchedItems.size} 个子目录，已触发安全熔断保护（仅保留前 ${MAX_WILDCARD_FANOUT_DIRS} 个目录）"
                    )
                }

                val effectiveItems = if (!isLastSegment) {
                    matchedItems.take(MAX_WILDCARD_FANOUT_DIRS)
                } else {
                    matchedItems
                }

                for (item in effectiveItems) {
                    val full = when {
                        parentPath.isEmpty() -> item.name
                        parentPath == "/" -> "/${item.name}"
                        else -> "$parentPath/${item.name}"
                    }
                    nextPaths.add(full)
                }
            }

            currentPaths = nextPaths
            if (currentPaths.isEmpty()) break
        }

        // POSIX 规范：若完全无匹配项，保留原字面量输入（Nomatch fallback）
        return currentPaths.ifEmpty { listOf(node.text) }
    }

    /**
     * 将带有字符级掩码的完整路径文本按 '/' 分段，并同步切片每个分段的 quoteMask
     */
    fun sliceSegmentsWithMask(text: String, mask: BooleanArray): List<SegmentWithMask> {
        val result = mutableListOf<SegmentWithMask>()
        var start = 0
        var i = 0

        while (i <= text.length) {
            if (i == text.length || text[i] == '/') {
                val segText = text.substring(start, i)
                val len = i - start
                val segMask = BooleanArray(len) { idx ->
                    mask.getOrElse(start + idx) { false }
                }
                result.add(SegmentWithMask(segText, segMask))
                start = i + 1
            }
            i++
        }

        return result
    }

    /**
     * 携带对应字符级受保护掩码的单段路径
     */
    data class SegmentWithMask(
        val text: String,
        val mask: BooleanArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SegmentWithMask) return false
            return text == other.text && mask.contentEquals(other.mask)
        }

        override fun hashCode(): Int {
            var result = text.hashCode()
            result = 31 * result + mask.contentHashCode()
            return result
        }
    }
}
