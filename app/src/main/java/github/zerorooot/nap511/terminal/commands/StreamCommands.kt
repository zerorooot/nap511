package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.terminal.engine.CommandRegistry
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

/**
 * 管道流处理与通用文本工具命令注册
 */
object StreamCommands {

    /**
     * 注册所有管道流处理与通用文本工具命令
     */
    fun registerAll(registry: CommandRegistry) {
        registerEcho(registry)
        registerGrep(registry)
        registerWc(registry)
        registerHead(registry)
        registerTail(registry)
        registerSort(registry)
        registerClear(registry)
        registerHelp(registry)
        registerExit(registry)
        registerXargs(registry)
    }

    /**
     * 注册 echo 命令：回显输出指定的文本
     */
    fun registerEcho(registry: CommandRegistry) {
        registry.register("echo") {
            description = "回显输出指定的文本"
            usage = "echo [text...]"
            execute { _, args, _ ->
                flow {
                    emit(args.joinToString(" "))
                }
            }
        }
    }

    /**
     * 注册 grep 命令：文本匹配与正则过滤
     */
    fun registerGrep(registry: CommandRegistry) {
        registry.register("grep") {
            description = "文本匹配与正则过滤"
            usage = "grep [-i] [-v] [-c] <pattern>"
            flag("-i", "忽略大小写")
            flag("-v", "反向匹配，输出不匹配的行")
            flag("-c", "仅输出匹配行的总数")
            execute { ctx, args, stdin ->
                flow {
                    val ignoreCase = args.contains("-i")
                    val invertMatch = args.contains("-v")
                    val countOnly = args.contains("-c")

                    val patternArgs = args.filter { !it.startsWith("-") }
                    val pattern = patternArgs.firstOrNull() ?: ""

                    var matchCount = 0
                    stdin.collect { line ->
                        val matched = if (ignoreCase) {
                            line.contains(pattern, ignoreCase = true)
                        } else {
                            line.contains(pattern)
                        }

                        val isSuccess = if (invertMatch) !matched else matched
                        if (isSuccess) {
                            matchCount++
                            if (!countOnly) {
                                emit(line)
                            }
                        }
                    }

                    if (countOnly) {
                        emit(matchCount.toString())
                    }
                }
            }
        }
    }

    /**
     * 注册 wc 命令：统计行数、单词数及字符数
     */
    fun registerWc(registry: CommandRegistry) {
        registry.register("wc") {
            description = "统计行数、单词数及字符数"
            usage = "wc [-l] [-w] [-c]"
            flag("-l", "仅统计行数")
            flag("-w", "仅统计单词数")
            flag("-c", "仅统计字符/字节数")
            execute { _, args, stdin ->
                flow {
                    val linesOnly = args.contains("-l")
                    val wordsOnly = args.contains("-w")
                    val charsOnly = args.contains("-c")

                    var lineCount = 0
                    var wordCount = 0
                    var charCount = 0

                    stdin.collect { line ->
                        lineCount++
                        charCount += line.length + 1 // +换行
                        wordCount += line.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                    }

                    when {
                        linesOnly && !wordsOnly && !charsOnly -> emit(lineCount.toString())
                        wordsOnly && !linesOnly && !charsOnly -> emit(wordCount.toString())
                        charsOnly && !linesOnly && !wordsOnly -> emit(charCount.toString())
                        else -> {
                            emit("   Lines    Words    Chars")
                            emit(String.format(java.util.Locale.getDefault(), "%8d %8d %8d", lineCount, wordCount, charCount))
                        }
                    }
                }
            }
        }
    }

    /**
     * 注册 head 命令：输出前 N 行（默认 10 行）
     */
    fun registerHead(registry: CommandRegistry) {
        registry.register("head") {
            description = "输出前 N 行（默认 10 行）"
            usage = "head [-n <行数>]"
            flag("-n <NUM>", "指定输出的前 N 行数")
            execute { _, args, stdin ->
                flow {
                    var limit = 10
                    val nIndex = args.indexOf("-n")
                    if (nIndex >= 0 && nIndex + 1 < args.size) {
                        limit = args[nIndex + 1].toIntOrNull() ?: 10
                    }

                    var count = 0
                    stdin.collect { line ->
                        if (count < limit) {
                            emit(line)
                            count++
                        }
                    }
                }
            }
        }
    }

    /**
     * 注册 tail 命令：输出末尾 N 行（默认 10 行）
     */
    fun registerTail(registry: CommandRegistry) {
        registry.register("tail") {
            description = "输出末尾 N 行（默认 10 行）"
            usage = "tail [-n <行数>]"
            flag("-n <NUM>", "指定输出的后 N 行数")
            execute { _, args, stdin ->
                flow {
                    var limit = 10
                    val nIndex = args.indexOf("-n")
                    if (nIndex >= 0 && nIndex + 1 < args.size) {
                        limit = args[nIndex + 1].toIntOrNull() ?: 10
                    }

                    val buffer = mutableListOf<String>()
                    stdin.collect { line ->
                        buffer.add(line)
                        if (buffer.size > limit) {
                            buffer.removeAt(0)
                        }
                    }
                    buffer.forEach { emit(it) }
                }
            }
        }
    }

    /**
     * 注册 sort 命令：对输入行进行文本排序
     */
    fun registerSort(registry: CommandRegistry) {
        registry.register("sort") {
            description = "对输入行进行文本排序"
            usage = "sort [-r] [-n] [-u]"
            flag("-r", "逆序排序")
            flag("-n", "按数值大小排序")
            flag("-u", "去重（唯一输出）")
            execute { _, args, stdin ->
                flow {
                    val reverse = args.contains("-r")
                    val numeric = args.contains("-n")
                    val unique = args.contains("-u")

                    val lines = stdin.toList().toMutableList()
                    val comparator = Comparator<String> { a, b ->
                        if (numeric) {
                            val numA = a.trim().toDoubleOrNull() ?: 0.0
                            val numB = b.trim().toDoubleOrNull() ?: 0.0
                            numA.compareTo(numB)
                        } else {
                            a.compareTo(b)
                        }
                    }

                    val sorted = if (reverse) lines.sortedWith(comparator.reversed()) else lines.sortedWith(comparator)
                    val finalLines = if (unique) sorted.distinct() else sorted
                    finalLines.forEach { emit(it) }
                }
            }
        }
    }

    /**
     * 注册 clear 命令：清空终端屏幕历史输出
     */
    fun registerClear(registry: CommandRegistry) {
        registry.register("clear") {
            description = "清空终端屏幕历史输出"
            usage = "clear"
            execute { _, _, _ ->
                flow {
                    emit("__TERMINAL_CLEAR_SCREEN__")
                }
            }
        }
    }

    /**
     * 注册 ? 与 help 命令：显示所有可用命令及简介
     */
    fun registerHelp(registry: CommandRegistry) {
        registry.register("?") {
            description = "显示所有可用命令及简介"
            usage = "?"
            execute { _, _, _ ->
                flow { emit(registry.buildAllHelpMessage()) }
            }
        }

        registry.register("help") {
            description = "显示所有可用命令及简介"
            usage = "help"
            execute { _, _, _ ->
                flow { emit(registry.buildAllHelpMessage()) }
            }
        }
    }

    /**
     * 注册 exit 命令：退出终端
     */
    fun registerExit(registry: CommandRegistry) {
        registry.register("exit") {
            description = "退出终端"
            usage = "exit"
            execute { _, _, _ ->
                flow {
                    emit("__TERMINAL_EXIT__")
                }
            }
        }
    }

    /**
     * 注册 xargs 命令：从标准输入读取数据并构建执行命令行
     */
    fun registerXargs(registry: CommandRegistry) {
        registry.register("xargs") {
            description = "从标准输入读取数据并构建执行命令行"
            usage = "xargs [-I <replace-str>] [-n <num>] [-t] [command [initial-args...]]"
            flag("-I <replace-str>", "将每行输入替换指定占位符并逐行执行命令（如 -I {}）")
            flag("-n <num>", "每次调用命令传递的最大参数数量（默认一次性传递全部输入）")
            flag("-t", "在执行前在屏幕打印即将运行的命令")
            execute { ctx, args, stdin ->
                flow {
                    var replaceStr: String? = null
                    var maxArgs: Int? = null
                    var verbose = false

                    val commandTokens = mutableListOf<String>()
                    var i = 0
                    while (i < args.size) {
                        val arg = args[i]
                        when {
                            arg == "-I" -> {
                                if (i + 1 < args.size) {
                                    replaceStr = args[++i]
                                }
                            }
                            arg.startsWith("-I") && arg.length > 2 -> {
                                replaceStr = arg.substring(2)
                            }
                            arg == "-n" -> {
                                if (i + 1 < args.size) {
                                    maxArgs = args[++i].toIntOrNull()
                                }
                            }
                            arg.startsWith("-n") && arg.length > 2 -> {
                                maxArgs = arg.substring(2).toIntOrNull()
                            }
                            arg == "-t" -> {
                                verbose = true
                            }
                            else -> {
                                commandTokens.add(arg)
                            }
                        }
                        i++
                    }

                    val targetCommandName = commandTokens.firstOrNull() ?: "echo"
                    val initialArgs = commandTokens.drop(1)

                    val targetCommandDef = registry.get(targetCommandName)
                    if (targetCommandDef == null) {
                        emit("xargs: $targetCommandName: command not found")
                        return@flow
                    }

                    if (replaceStr != null) {
                        // -I 模式：逐行读取并替换占位符，每行独立调用一次目标命令
                        stdin.collect { rawLine ->
                            val line = rawLine.trim()
                            if (line.isNotEmpty()) {
                                val substitutedArgs = if (initialArgs.any { it.contains(replaceStr) }) {
                                    initialArgs.map { it.replace(replaceStr, line) }
                                } else {
                                    initialArgs + line
                                }

                                if (verbose) {
                                    emit("+ $targetCommandName ${substitutedArgs.joinToString(" ")}")
                                }

                                val resultFlow = targetCommandDef.execute(ctx, substitutedArgs, kotlinx.coroutines.flow.emptyFlow())
                                resultFlow.collect { emit(it) }
                            }
                        }
                    } else {
                        // 默认批量模式：按空格/换行拆分收集所有 token，分批次执行
                        val tokens = mutableListOf<String>()
                        stdin.collect { rawLine ->
                            val lineTokens = rawLine.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                            tokens.addAll(lineTokens)
                        }

                        if (tokens.isEmpty()) {
                            return@flow
                        }

                        val batches = if (maxArgs != null && maxArgs > 0) {
                            tokens.chunked(maxArgs)
                        } else {
                            listOf(tokens)
                        }

                        for (batch in batches) {
                            val finalArgs = initialArgs + batch
                            if (verbose) {
                                emit("+ $targetCommandName ${finalArgs.joinToString(" ")}")
                            }
                            val resultFlow = targetCommandDef.execute(ctx, finalArgs, kotlinx.coroutines.flow.emptyFlow())
                            resultFlow.collect { emit(it) }
                        }
                    }
                }
            }
        }
    }
}
