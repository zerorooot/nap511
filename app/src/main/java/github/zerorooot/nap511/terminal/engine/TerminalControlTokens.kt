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
}
