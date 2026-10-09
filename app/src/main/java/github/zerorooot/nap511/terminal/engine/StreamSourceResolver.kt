package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.util.TextFileHelper
import github.zerorooot.nap511.util.TextValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 流式输入源条目密封接口
 */
sealed interface StreamSourceItem {
    val sourceName: String

    /**
     * 有效行文本数据流
     *
     * @property sourceName 输入源名称（如 "stdin" 或文件路径 "test.txt"）
     * @property lines 文本行冷流
     */
    data class DataStream(override val sourceName: String, val lines: Flow<String>) : StreamSourceItem

    /**
     * 单个输入源解析失败错误（独立隔离，不影响其他输入源）
     *
     * @property sourceName 输入源名称
     * @property message 错误提示信息
     */
    data class Error(override val sourceName: String, val message: String) : StreamSourceItem
}

/**
 * POSIX 混合数据源解析器 (StreamSourceResolver)
 *
 * 负责将命令行中的文件操作数与管道标准输入（stdin）归一化解析为 [StreamSourceItem] 序列：
 * 1. 当未指定文件操作数或指定单个 "-" 时，统一回退为读取标准输入 stdin；
 * 2. 多文件操作数依次解析，单个文件不存在、是目录或非文本文件时生成 [StreamSourceItem.Error]，
 *    绝不阻断后续合法文件的继续处理；
 * 3. 严格遵循文本文件大小与类型安全校验规范。
 */
object StreamSourceResolver {

    /**
     * 解析多文件操作数与标准输入
     *
     * @param ctx 终端上下文
     * @param fileOperands 文件操作数列表（如从 AST 提取的位置参数）
     * @param stdin 管道上游流入的标准输入冷流
     * @param maxKb 文件大小上限限制（KB，默认读取全局设置）
     * @return [StreamSourceItem] 列表
     */
    suspend fun resolveSources(
        ctx: TerminalContext,
        fileOperands: List<String>,
        stdin: Flow<String>,
        maxKb: Int = TextFileHelper.getDefaultSizeLimitKb()
    ): List<StreamSourceItem> {
        // 场景 1：无文件操作数或显式指定 "-"，回退为标准输入 stdin
        if (fileOperands.isEmpty() || (fileOperands.size == 1 && fileOperands[0] == "-")) {
            return listOf(StreamSourceItem.DataStream("stdin", stdin))
        }

        // 场景 2：多文件操作数依次解析与错误隔离
        return fileOperands.map { path ->
            if (path == "-") {
                StreamSourceItem.DataStream("stdin", stdin)
            } else {
                val resolved = ctx.resolveTarget(path)
                when (resolved) {
                    null -> StreamSourceItem.Error(path, "$path: No such file or directory")
                    is ResolvedTarget.Directory -> StreamSourceItem.Error(path, "$path: Is a directory")
                    is ResolvedTarget.File -> {
                        val fileBean = resolved.file
                        when (val valid = TextFileHelper.validate(fileBean, maxKb)) {
                            is TextValidationResult.NotTextFile -> {
                                StreamSourceItem.Error(path, "${fileBean.name}: Not a text file")
                            }
                            is TextValidationResult.ExceedsSizeLimit -> {
                                val sizeStr = fileBean.sizeString.ifEmpty { "${fileBean.size}B" }
                                StreamSourceItem.Error(
                                    path,
                                    "${fileBean.name}: File exceeds size limit ($sizeStr >= ${valid.limitKb}KB)"
                                )
                            }
                            is TextValidationResult.Valid -> {
                                val linesFlow = flow {
                                    val fetchResult = TextFileHelper.fetchBytes(ctx.fileRepository, fileBean)
                                    fetchResult.onSuccess { bytes ->
                                        val lines = TextFileHelper.parseLines(bytes)
                                        for (line in lines) {
                                            emit(line)
                                        }
                                    }.onFailure { err ->
                                        throw IllegalStateException("Failed to read file (${err.message ?: "unknown"})")
                                    }
                                }
                                StreamSourceItem.DataStream(path, linesFlow)
                            }
                        }
                    }
                }
            }
        }
    }
}
