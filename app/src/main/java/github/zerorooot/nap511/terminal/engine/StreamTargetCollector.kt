package github.zerorooot.nap511.terminal.engine

import kotlinx.coroutines.flow.Flow

/**
 * 管道标准输入流（stdin）操作数与路径收集工具
 *
 * 职责（高内聚、低耦合、高复用）：
 * 统一为终端写操作及消费型命令（如 [MvCommand]、[RmCommand]、[UnzipCommand]）从上游管道提取输入参数列表。
 *
 * 核心特性：
 * 1. 自动感知与兼容定界符：
 *    - NUL 定界模式（\0）：完全适配 `find -print0`，保留文件名内部的合法空格，并可靠剥离定界符；
 *    - 行定界模式（\n / \r\n）：适配标准行文本输出（如 `find` 默认模式、`ls`），按行切分；
 * 2. 跨分块流式拼接：
 *    - 无论上游数据是以单字符、分段字符串还是整行流式推送，均在遇到定界符时精准结算并推入结果集；
 *    - 自动滤除空行与空定界项。
 */
object StreamTargetCollector {

    /**
     * 从 stdin 数据流中提取纯净的目标路径或操作数列表
     *
     * @param stdin 管道标准输入流
     * @return 提取并归一化后的非空参数列表
     */
    suspend fun collectFromStdin(stdin: Flow<String>): List<String> {
        val results = mutableListOf<String>()
        val buffer = StringBuilder()

        stdin.collect { chunk ->
            for (ch in chunk) {
                if (ch == '\u0000' || ch == '\n' || ch == '\r') {
                    val item = buffer.toString().trim { it <= ' ' || it == '\u0000' }
                    if (item.isNotEmpty()) {
                        results.add(item)
                    }
                    buffer.clear()
                } else {
                    buffer.append(ch)
                }
            }
        }

        val remaining = buffer.toString().trim { it <= ' ' || it == '\u0000' }
        if (remaining.isNotEmpty()) {
            results.add(remaining)
        }

        return results
    }
}
