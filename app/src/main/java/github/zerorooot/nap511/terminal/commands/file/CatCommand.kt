package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText
import github.zerorooot.nap511.util.TextFileHelper
import github.zerorooot.nap511.util.TextValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * 文本连接与打印命令（cat）
 *
 * 遵循 POSIX 与 GNU cat 规范：
 * 1. 支持指定单个或多个文件参数（如 `cat file1.txt file2.txt`），按顺序读取并输出内容；
 * 2. 当未指定文件参数时，自动从上游管道标准输入（stdin）读取并输出（如 `echo "hello" | cat`）；
 * 3. 严格遵循安全规范：仅允许读取文本类型文件，且文件大小不可超过指定阈值；
 * 4. 大小限制默认优先读取全局设置（SettingsRepository.txtSize），并支持通过 `--max-size <KB>` 命令行参数临时覆盖；
 * 5. 支持标准格式化参数：
 *    - `-n`：输出所有行并附带行号（从 1 开始）；
 *    - `-b`：仅对非空行编号（优先级高于 -n）；
 *    - `-s`：压缩连续的空白行（GNU squeeze-blank）；
 * 6. 在处理多个文件时，行号在文件间保持全局连续递增；
 * 7. 若多文件中某一个文件失败（不存在、是目录、非文本、超大等），打印错误提示后继续处理后续文件。
 */
class CatCommand : TerminalCommand {

    override val name: String = "cat"

    override val description: String = "连接文件并在标准输出上打印文本内容"

    override val usage: String = "cat [-n] [-b] [-s] [--max-size <KB>] [file...]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-n", "对所有输出行进行从 1 开始的编号"),
        CommandFlag("-b", "仅对非空输出行进行编号（优先级高于 -n）"),
        CommandFlag("-s", "压缩连续的空白行"),
        CommandFlag("--max-size <KB>", "临时指定单个文件的大小上限（KB）")
    )

    override val valueOptions: Set<String> = setOf("--max-size")

    override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        // 提取格式化选项
        val numberNonBlank = ast.hasFlag("-b")
        val numberAll = ast.hasFlag("-n") && !numberNonBlank
        val squeezeBlank = ast.hasFlag("-s")

        // 提取大小上限（优先使用命令行参数指定的 KB，未指定时读取全局设置）
        val customMaxKb = ast.getIntOption("--max-size")
        val maxKb = customMaxKb ?: TextFileHelper.getDefaultSizeLimitKb()

        val files = ast.rawPositionalValues
        val formatter = CatOutputFormatter(
            numberAll = numberAll,
            numberNonBlank = numberNonBlank,
            squeezeBlank = squeezeBlank
        )

        // 场景 1：无文件参数，处理管道标准输入 (stdin)
        if (files.isEmpty()) {
            stdin.collect { line ->
                formatter.formatAndEmit(line, this)
            }
            return@flow
        }

        // 场景 2：存在文件参数，逐个解析与输出
        for (filePath in files) {
            val resolved = ctx.resolveTarget(filePath)
            if (resolved == null) {
                emitError("cat: $filePath: No such file or directory")
                continue
            }

            when (resolved) {
                is ResolvedTarget.Directory -> {
                    emitError("cat: $filePath: Is a directory")
                }

                is ResolvedTarget.File -> {
                    val fileBean = resolved.file
                    when (val validRes = TextFileHelper.validate(fileBean, maxKb)) {
                        is TextValidationResult.NotTextFile -> {
                            emitError("cat: ${fileBean.name}: Not a text file")
                        }

                        is TextValidationResult.ExceedsSizeLimit -> {
                            val sizeStr = fileBean.sizeString.ifEmpty { "${fileBean.size}B" }
                            emitError("cat: ${fileBean.name}: File exceeds size limit ($sizeStr >= ${validRes.limitKb}KB)")
                        }

                        is TextValidationResult.Valid -> {
                            val fetchResult = TextFileHelper.fetchBytes(
                                repository = ctx.fileRepository,
                                fileBean = fileBean
                            )
                            fetchResult.onSuccess { bytes ->
                                val lines = TextFileHelper.parseLines(bytes)
                                for (line in lines) {
                                    formatter.formatAndEmit(line, this)
                                }
                            }.onFailure { err ->
                                emitError("cat: ${fileBean.name}: Failed to read file (${err.message ?: "unknown error"})")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 封装行格式化器（行号计数与空行压缩）
 *
 * 保证行号跨多个文件时全局连续递增。
 */
private class CatOutputFormatter(
    private val numberAll: Boolean,
    private val numberNonBlank: Boolean,
    private val squeezeBlank: Boolean
) {
    private var globalLineNumber = 1
    private var prevWasEmpty = false

    suspend fun formatAndEmit(line: String, collector: FlowCollector<TerminalOutput>) {
        val isEmpty = line.isEmpty()

        // 压缩连续空行（-s 选项）
        if (squeezeBlank && isEmpty && prevWasEmpty) {
            return
        }
        prevWasEmpty = isEmpty

        val formattedText = when {
            numberNonBlank -> {
                if (isEmpty) {
                    line
                } else {
                    "${(globalLineNumber++).toString().padStart(6)}\t$line"
                }
            }

            numberAll -> {
                "${(globalLineNumber++).toString().padStart(6)}\t$line"
            }

            else -> line
        }

        collector.emitText(formattedText)
    }
}
