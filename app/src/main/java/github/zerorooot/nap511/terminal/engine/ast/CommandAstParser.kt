package github.zerorooot.nap511.terminal.engine.ast

import github.zerorooot.nap511.terminal.engine.Token

/**
 * 终端命令抽象语法树解析器 (Command AST Parser)
 *
 * 遵循 POSIX 命令行语法规范，将词法 Token 序列一次性构建为结构化 [CommandInvocationAst]：
 * 1. 选项结束符支持：遇见第一个未加引号的 "--" 时，其后所有参数均一律归类为位置参数；
 * 2. 引号隔离安全机制：被单/双引号包裹的 Token（哪怕以 "-" 开头）严格作为字面量位置参数；
 * 3. 复合布尔开关拆分：如 -rf 自动分解为 -r 与 -f，同时保留原复合标签；
 * 4. 键值选项识别：支持分离式（"-n 10"、"-I {}"）与紧凑式（"-n10"、"-I{}"、"--key=val"）；
 * 5. 避免各命令重复解析裸字符串，从源头彻底终结过程式 CommandArgs。
 */
object CommandAstParser {

    /**
     * 将带引号元信息的 Token 序列解析为 AST
     *
     * 遵循 POSIX Utility Syntax Guidelines 规范：
     * 1. 选项结束符支持：遇见第一个未加引号的 "--" 时，其后所有参数均一律归类为位置参数；
     * 2. 引号隔离安全机制：被单/双引号包裹的 Token（哪怕以 "-" 开头）严格作为字面量位置参数；
     * 3. 复合布尔开关拆分：如 -rf 自动分解为 -r 与 -f，同时保留原复合标签；
     * 4. 键值选项识别：支持分离式（"-n 10"、"-I {}"）与紧凑式（"-n10"、"-I{}"、"--key=val"）；
     * 5. 包装命令感知 (Guideline 13)：若 [isWrapperCommand] 为 true，首个非选项操作数及其后续所有 Token
     *    完整原貌封包为 [SubcommandAst]，杜绝外层解析器劫持子命令内部的专属选项（如 unzip -l）。
     *
     * @param commandName 命令名称
     * @param tokens 词法分析器产生的 Token 列表
     * @param allowedValueOptions 该命令支持带参数值的选项前缀集合（如 setOf("-n", "-I", "-suffix")）
     * @param isWrapperCommand 是否为高阶包装命令（如 xargs），启用 Guideline 13 透传子命令语法
     */
    fun parse(
        commandName: String,
        tokens: List<Token>,
        allowedValueOptions: Set<String> = emptySet(),
        isWrapperCommand: Boolean = false
    ): CommandInvocationAst {
        val flags = mutableSetOf<String>()
        val options = mutableMapOf<String, OptionValueNode>()
        val positional = mutableListOf<PositionalArgumentNode>()
        var inDelimiter = false
        var subcommandAst: SubcommandAst? = null

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            val text = token.text

            if (inDelimiter) {
                // "--" 之后：若为包装命令，首个遇到的参数为子命令名，其后所有参数原样归入子命令
                if (isWrapperCommand) {
                    val subTokens = if (i + 1 < tokens.size) tokens.subList(i + 1, tokens.size) else emptyList()
                    subcommandAst = SubcommandAst(text, subTokens)
                    positional.add(PositionalArgumentNode(text, token.isQuoted, fromDelimiter = true))
                    for (st in subTokens) {
                        positional.add(PositionalArgumentNode(st.text, st.isQuoted, fromDelimiter = true))
                    }
                    break
                }
                // 非包装命令直接作为普通位置参数
                positional.add(PositionalArgumentNode(text, token.isQuoted, fromDelimiter = true))
                i++
                continue
            }

            if (token.isQuoted) {
                // 被引号包裹的 Token 视为位置参数
                if (isWrapperCommand) {
                    val subTokens = if (i + 1 < tokens.size) tokens.subList(i + 1, tokens.size) else emptyList()
                    subcommandAst = SubcommandAst(text, subTokens)
                    positional.add(PositionalArgumentNode(text, true))
                    for (st in subTokens) {
                        positional.add(PositionalArgumentNode(st.text, st.isQuoted, fromDelimiter = false))
                    }
                    break
                }
                positional.add(PositionalArgumentNode(text, true))
                i++
                continue
            }

            when {
                text == "--" -> {
                    inDelimiter = true
                }

                // 长选项形式：--opt 或 --key=val
                text.startsWith("--") && text.length > 2 -> {
                    if (text.contains('=')) {
                        val key = text.substringBefore('=')
                        val value = text.substringAfter('=')
                        options[key] = OptionValueNode(key, value, isQuoted = false)
                    } else if (allowedValueOptions.contains(text)) {
                        // 长选项且带值，如 --suffix apk
                        if (i + 1 < tokens.size) {
                            val next = tokens[++i]
                            options[text] = OptionValueNode(text, next.text, next.isQuoted)
                        } else {
                            flags.add(text)
                        }
                    } else {
                        flags.add(text)
                    }
                }

                // 短选项或复合短选项
                text.startsWith("-") && text.length > 1 -> {
                    // 检查是否匹配带值选项前缀（如 -n 或 -I）
                    val matchedValueOpt = allowedValueOptions.firstOrNull { opt ->
                        text == opt || (text.startsWith(opt) && text.length > opt.length)
                    }

                    if (matchedValueOpt != null) {
                        if (text == matchedValueOpt) {
                            // 分离形式：-n 10
                            if (i + 1 < tokens.size) {
                                val next = tokens[++i]
                                options[matchedValueOpt] = OptionValueNode(matchedValueOpt, next.text, next.isQuoted)
                            }
                        } else {
                            // 紧贴形式：-n10
                            val valuePart = text.substring(matchedValueOpt.length)
                            options[matchedValueOpt] = OptionValueNode(matchedValueOpt, valuePart, isQuoted = false)
                        }
                    } else {
                        // 复合布尔开关拆分：例如 -rf 自动拆分为 -r 和 -f
                        for (ch in text.substring(1)) {
                            flags.add("-$ch")
                        }
                        // 同时保留复合字符串（兼容按 -rf 整体判定的逻辑）
                        flags.add(text)
                    }
                }

                else -> {
                    // 普通位置参数（路径、文件名或包装命令的目标子命令）
                    if (isWrapperCommand) {
                        // POSIX Guideline 13：首个位置操作数即为目标子命令，后续所有 Token 无损封包为子命令参数
                        val subTokens = if (i + 1 < tokens.size) tokens.subList(i + 1, tokens.size) else emptyList()
                        subcommandAst = SubcommandAst(text, subTokens)
                        positional.add(PositionalArgumentNode(text, false))
                        for (st in subTokens) {
                            positional.add(PositionalArgumentNode(st.text, st.isQuoted, fromDelimiter = false))
                        }
                        break
                    } else {
                        positional.add(PositionalArgumentNode(text, false))
                    }
                }
            }
            i++
        }

        return CommandInvocationAst(
            commandName = commandName,
            flags = flags,
            options = options,
            positionalArgs = positional,
            hasDelimiter = inDelimiter,
            rawArgs = tokens.map { it.text },
            subcommand = subcommandAst
        )
    }
}
