package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * open 命令测试用例集合
 * 覆盖：文件存在性校验、文件打开器未配置提示、转义字符与特殊文件名打开及管道隔离
 */
class OpenCommandTest {
//todo 配置文件打开器
    /**
     * 测试缺失参数与文件不存在校验
     */
    @Test
    fun testOpenCommandValidationAndErrors() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val testFile = createMockFile("test.txt", "1")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(testFile), cid = "0", count = 1, order = "", path = emptyList()))

        // open 缺失文件参数报错
        val outEmpty = engine.executeStrings("open", ctx)
        assertEquals(listOf("open: missing file operand"), outEmpty)

        // open 试图打开不存在的文件报错
        val outNotFound = engine.executeStrings("open no_such_file.mp4", ctx)
        assertEquals(listOf("open: cannot find 'no_such_file.mp4': No such file or directory"), outNotFound)

        // 未配置文件打开器时的提示信息
        val outNoOpener = engine.executeStrings("open test.txt", ctx)
        assertEquals(listOf("open: 当前终端环境未配置文件打开器"), outNoOpener)
    }

    /**
     * 测试路径转义、中文与 Emoji 文件名打开
     */
    @Test
    fun testOpenEscapingAndPaths() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val fileSpaced = createMockFile("my file.txt", "10")
        val fileChinese = createMockFile("测试.txt", "11")
        val fileEmoji = createMockFile("😀.png", "12")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(fileSpaced, fileChinese, fileEmoji), cid = "0", count = 3, order = "", path = emptyList()))

        // 带空格文件名使用双引号包裹打开
        val outQuoted = engine.executeStrings("open \"my file.txt\"", ctx)
        assertEquals(listOf("open: 当前终端环境未配置文件打开器"), outQuoted)

        // 带空格文件名使用反斜杠转义打开
        val outEscaped = engine.executeStrings("open my\\ file.txt", ctx)
        assertEquals(listOf("open: 当前终端环境未配置文件打开器"), outEscaped)

        // 中文文件名打开
        val outChinese = engine.executeStrings("open 测试.txt", ctx)
        assertEquals(listOf("open: 当前终端环境未配置文件打开器"), outChinese)

        // Emoji 表情文件名打开
        val outEmoji = engine.executeStrings("open '😀.png'", ctx)
        assertEquals(listOf("open: 当前终端环境未配置文件打开器"), outEmoji)

        // 绝对路径缺失文件报错
        val outAbs = engine.executeStrings("open /根目录/notexist.txt", ctx)
        assertEquals(listOf("open: cannot find '/根目录/notexist.txt': No such file or directory"), outAbs)
    }

    /**
     * 测试 open 系统的错误输出隔离在通道内不污染下游数据管道
     */
    @Test
    fun testOpenPipelineCombinations() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // open 系统的错误输出隔离在通道内，不污染下游数据管道
        val outRedirect = engine.executeStrings("open notexist.txt | head -n 1", ctx)
        assertEquals(emptyList<String>(), outRedirect)
    }
}
