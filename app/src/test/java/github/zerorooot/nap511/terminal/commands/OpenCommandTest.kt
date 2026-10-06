package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenCommandTest {
//todo 配置文件打开器
    @Test
    fun testOpenCommandValidationAndErrors() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val testFile = FileBean(name = "test.txt", fileId = "1", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(testFile), cid = "0", count = 1, order = "", path = emptyList()))

        // O008: 缺失参数
        val outEmpty = engine.executeStrings("open", ctx)
        assertEquals(listOf("open: missing file operand"), outEmpty)

        // O009: 文件不存在
        val outNotFound = engine.executeStrings("open no_such_file.mp4", ctx)
        assertEquals(listOf("open: cannot find 'no_such_file.mp4': No such file or directory"), outNotFound)

        // O002: 未配置文件打开器
        val outNoOpener = engine.executeStrings("open test.txt", ctx)
        assertEquals(listOf("open: 当前终端环境未配置文件打开器"), outNoOpener)
    }

    @Test
    fun testOpenEscapingAndPaths() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val fileSpaced = FileBean(name = "my file.txt", fileId = "10", isFolder = false)
        val fileChinese = FileBean(name = "测试.txt", fileId = "11", isFolder = false)
        val fileEmoji = FileBean(name = "😀.png", fileId = "12", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(fileSpaced, fileChinese, fileEmoji), cid = "0", count = 3, order = "", path = emptyList()))

        // O012: 带空格文件名用引号包裹
        val outQuoted = engine.executeStrings("open \"my file.txt\"", ctx)
        assertEquals(listOf("open: 当前终端环境未配置文件打开器"), outQuoted)

        // O013: 带空格文件名用反斜杠转义
        val outEscaped = engine.executeStrings("open my\\ file.txt", ctx)
        assertEquals(listOf("open: 当前终端环境未配置文件打开器"), outEscaped)

        // O019: 中文文件名
        val outChinese = engine.executeStrings("open 测试.txt", ctx)
        assertEquals(listOf("open: 当前终端环境未配置文件打开器"), outChinese)

        // O020: Emoji 文件名
        val outEmoji = engine.executeStrings("open '😀.png'", ctx)
        assertEquals(listOf("open: 当前终端环境未配置文件打开器"), outEmoji)

        // O024: 绝对路径缺失文件报错
        val outAbs = engine.executeStrings("open /根目录/notexist.txt", ctx)
        assertEquals(listOf("open: cannot find '/根目录/notexist.txt': No such file or directory"), outAbs)
    }

    @Test
    fun testOpenPipelineCombinations() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // O031: open 命令系统错误输出会被通道隔离，不污染下游数据管道
        val outRedirect = engine.executeStrings("open notexist.txt | head -n 1", ctx)
        assertEquals(emptyList<String>(), outRedirect)
    }
}
