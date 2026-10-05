package github.zerorooot.nap511.terminal.engine

/**
 * 终端底层系统控制标记与信号协议定义
 *
 * 约定命令层（commands）与上层终端会话（TerminalViewModel / UI）之间的带外控制流标记。
 * 消除散落在各处的魔法字符串（Magic String），提升类型安全与代码可读性。
 */
object TerminalControlTokens {

    /**
     * 清空屏幕输出标记（对应 clear 命令或 Ctrl+L 快捷键）
     *
     * 当终端流式管道发射此标记时，终端 ViewModel 会捕获并重置行缓冲，而不将其作为普通文本输出。
     */
    const val CLEAR_SCREEN = "__TERMINAL_CLEAR_SCREEN__"

    /**
     * 退出终端会话标记（对应 exit 命令或 Ctrl+D 空输入退出）
     *
     * 当终端流式管道发射此标记时，终端 ViewModel 会触发终端页面的关闭或会话终止回调。
     */
    const val EXIT = "__TERMINAL_EXIT__"

    /**
     * 命令行帮助文档前缀标记（对应 --help、-h 或 help/? 命令输出）
     *
     * 用于在发射源头为帮助文档流打上专属语义元数据标签，使终端 ViewModel 直接将其归类为 TerminalLineType.HELP，
     * 从而在根源上一开始就杜绝帮助说明中的字符（如紧凑斜杠、冒号、标点）被下游样式解析器意外误判为文件路径。
     */
    const val HELP_PREFIX = "__TERMINAL_HELP_DOC__:"
}
