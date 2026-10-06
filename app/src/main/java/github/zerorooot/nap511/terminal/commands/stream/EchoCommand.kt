package github.zerorooot.nap511.terminal.commands.stream

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitAnsi
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 文本回显命令（echo）
 *
 * 将传入的参数拼接为空格分隔的字符串并输出到标准输出流。
 * 支持以下选项：
 * - `-n`：不输出尾随换行符（无输出内容时静默）
 * - `-e`：启用反斜杠转义序列解释（\n, \t, \r, \\, \a, \b, \f, \v, \0NNN, \xHH, \c 等）
 * - `-E`：明确禁用反斜杠转义序列解释
 * 若输出内容包含标准 ANSI SGR 转义码，则标记为 OUTPUT_ANSI，否则标记为纯文本 OUTPUT_TEXT。
 */
class EchoCommand : TerminalCommand {

    override val name: String = "echo"

    override val description: String = "回显输出指定的文本"

    override val usage: String = "echo [-n] [-e] [text...]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-n", "不输出尾随换行符"),
        CommandFlag("-e", "启用反斜杠转义字符解释")
    )

    override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val args = ast.rawArgs
        var noNewline = false
        var enableEscapes = false
        var textStartIndex = 0

        // 解析前导选项（-n, -e, -E 或其组合如 -ne, -en）
        while (textStartIndex < args.size) {
            val arg = args[textStartIndex]
            if (arg.startsWith("-") && arg.length > 1 && arg.substring(1).all { it == 'n' || it == 'e' || it == 'E' }) {
                if (arg.contains('n')) noNewline = true
                if (arg.contains('e')) enableEscapes = true
                if (arg.contains('E')) enableEscapes = false
                textStartIndex++
            } else {
                break
            }
        }

        val textTokens = args.subList(textStartIndex, args.size)
        var result = textTokens.joinToString(" ")

        if (enableEscapes) {
            val (escaped, suppressNewline) = interpretEscapes(result)
            result = escaped
            if (suppressNewline) {
                noNewline = true
            }
        }

        // -n 模式下若内容为空则静默，不发射任何终端行
        if (noNewline && result.isEmpty()) {
            return@flow
        }

        if (result.contains("\u001B[")) {
            emitAnsi(result)
        } else {
            emitText(result)
        }
    }

    /**
     * 解释字符串中的常见反斜杠转义序列（遵从标准 Unix echo -e 规范）
     *
     * @return 转换后的字符串以及是否由 \c 触发终止后续输出并抑制换行
     */
    private fun interpretEscapes(input: String): Pair<String, Boolean> {
        val sb = StringBuilder()
        var i = 0

        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                when (val next = input[i + 1]) {
                    '\\' -> { sb.append('\\'); i += 2 }
                    'a' -> { sb.append('\u0007'); i += 2 } // 响铃 Alert
                    'b' -> { sb.append('\b'); i += 2 }     // 退格 Backspace
                    'c' -> {
                        // 产生无后续输出并抑制后续换行
                        return Pair(sb.toString(), true)
                    }
                    'e', 'E' -> { sb.append('\u001B'); i += 2 } // Escape
                    'f' -> { sb.append('\u000C'); i += 2 } // 换页 Formfeed
                    'n' -> { sb.append('\n'); i += 2 }     // 换行 Newline
                    'r' -> { sb.append('\r'); i += 2 }     // 回车 Carriage return
                    't' -> { sb.append('\t'); i += 2 }     // 水平制表符 Tab
                    'v' -> { sb.append('\u000B'); i += 2 } // 垂直制表符
                    '0' -> {
                        // 八进制格式 \0NNN (最多 3 位八进制数字 0-7)
                        var j = i + 2
                        while (j < input.length && j < i + 5 && input[j] in '0'..'7') {
                            j++
                        }
                        if (j > i + 2) {
                            val octalStr = input.substring(i + 2, j)
                            val code = octalStr.toIntOrNull(8) ?: 0
                            sb.append(code.toChar())
                            i = j
                        } else {
                            sb.append('\u0000')
                            i += 2
                        }
                    }
                    'x' -> {
                        // 十六进制格式 \xHH (最多 2 位十六进制字符)
                        var j = i + 2
                        while (j < input.length && j < i + 4 && (input[j] in '0'..'9' || input[j] in 'a'..'f' || input[j] in 'A'..'F')) {
                            j++
                        }
                        if (j > i + 2) {
                            val hexStr = input.substring(i + 2, j)
                            val code = hexStr.toIntOrNull(16) ?: 0
                            sb.append(code.toChar())
                            i = j
                        } else {
                            sb.append('\\').append('x')
                            i += 2
                        }
                    }
                    else -> {
                        sb.append('\\').append(next)
                        i += 2
                    }
                }
            } else {
                sb.append(c)
                i++
            }
        }

        return Pair(sb.toString(), false)
    }
}
