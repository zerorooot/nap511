package github.zerorooot.nap511.terminal.viewmodel

enum class TerminalLineType {
    SYSTEM,   // 系统问候、提示信息
    COMMAND,  // 用户输入的带提示符的命令行
    OUTPUT,   // 命令标准输出（需解析富文本，如 ls/find）
    HELP,     // 命令行帮助与快捷键文档（源头直发，保证整洁纯净，不走二次路径猜测）
    ERROR,    // 错误信息
    PROMPT    // 交互式提示（如 rm 确认）
}

data class TerminalLine(
    val text: String,
    val type: TerminalLineType = TerminalLineType.OUTPUT,
    val id: Long = System.nanoTime()
)
