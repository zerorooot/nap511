package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.terminal.commands.cloud.DfCommand
import github.zerorooot.nap511.terminal.commands.cloud.FindCommand
import github.zerorooot.nap511.terminal.commands.cloud.OpenCommand
import github.zerorooot.nap511.terminal.commands.cloud.StatCommand
import github.zerorooot.nap511.terminal.commands.cloud.TrashCommand
import github.zerorooot.nap511.terminal.commands.cloud.UnzipCommand
import github.zerorooot.nap511.terminal.commands.file.CatCommand
import github.zerorooot.nap511.terminal.commands.file.CdCommand
import github.zerorooot.nap511.terminal.commands.file.LsCommand
import github.zerorooot.nap511.terminal.commands.file.MkdirCommand
import github.zerorooot.nap511.terminal.commands.file.MvCommand
import github.zerorooot.nap511.terminal.commands.file.PwdCommand
import github.zerorooot.nap511.terminal.commands.file.RmCommand
import github.zerorooot.nap511.terminal.commands.stream.EchoCommand
import github.zerorooot.nap511.terminal.commands.stream.GrepCommand
import github.zerorooot.nap511.terminal.commands.stream.HeadCommand
import github.zerorooot.nap511.terminal.commands.stream.SortCommand
import github.zerorooot.nap511.terminal.commands.stream.TailCommand
import github.zerorooot.nap511.terminal.commands.stream.WcCommand
import github.zerorooot.nap511.terminal.commands.stream.XargsCommand
import github.zerorooot.nap511.terminal.commands.system.ClearCommand
import github.zerorooot.nap511.terminal.commands.system.ExitCommand
import github.zerorooot.nap511.terminal.commands.system.HelpCommand
import github.zerorooot.nap511.terminal.commands.system.HistoryCommand
import github.zerorooot.nap511.terminal.engine.CommandRegistry
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.engine.TerminalHistoryManager

/**
 * 终端命令注册工厂
 *
 * 负责装配初始化所有的命令模块，构建并返回包含完整命令集的 CommandRegistry。
 */
object CommandRegistryFactory {

    /**
     * 创建默认命令注册表，接入流式持久化历史管理器
     *
     * @param historyManager 持久化历史管理器
     * @param onClearMemoryHistory 清空内存会话历史的回调函数
     */
    fun createDefaultRegistry(
        historyManager: TerminalHistoryManager,
        onClearMemoryHistory: () -> Unit = {}
    ): CommandRegistry {
        val registry = CommandRegistry()

        registry.registerAll(
            // 1. 注册流式工具及系统控制命令
            EchoCommand(),
            GrepCommand(),
            WcCommand(),
            HeadCommand(),
            TailCommand(),
            SortCommand(),
            ClearCommand(),
            HelpCommand { registry },
            ExitCommand(),
            XargsCommand { registry },
            // 2. 注册网盘基础文件管理命令
            CatCommand(),
            LsCommand(),
            CdCommand(),
            PwdCommand(),
            MkdirCommand(),
            RmCommand(),
            MvCommand(),
            // 3. 注册 115 网盘特色命令
            DfCommand(),
            FindCommand(),
            TrashCommand(),
            StatCommand(),
            UnzipCommand(),
            OpenCommand(),
            // 4. 注册历史记录命令 (通过独立 Command 类注入依赖)
            HistoryCommand(historyManager, onClearMemoryHistory)
        )

        return registry
    }
}
