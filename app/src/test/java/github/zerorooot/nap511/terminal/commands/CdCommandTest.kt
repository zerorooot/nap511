package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CdCommandTest {

    @Test
    fun testCdParentDirectory() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // 模拟 根目录("0") -> t1("100") -> t2("200")
        val t1Folder = FileBean(name = "t1", categoryId = "100", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(t1Folder), cid = "0", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"))))

        val t2Folder = FileBean(name = "t2", categoryId = "200", isFolder = true)
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(t2Folder), cid = "100", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("100", "t1", "0"))))
        ctx.fileCacheManager.put("200", FilesBean(fileBeanList = arrayListOf(), cid = "200", count = 0, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("100", "t1", "0"), PathBean("200", "t2", "100"))))

        // 1. 从根目录进入 t1/t2
        engine.execute("cd t1/t2", ctx).toList()
        assertEquals("200", ctx.currentCid)
        assertEquals("/根目录/t1/t2", ctx.currentPath)

        // 2. cd .. 应返回上级目录 /根目录/t1 (CID 100)，而非上两级 /根目录
        engine.execute("cd ..", ctx).toList()
        assertEquals("100", ctx.currentCid)
        assertEquals("/根目录/t1", ctx.currentPath)

        // 3. 再次 cd .. 应返回 /根目录 (CID 0)
        engine.execute("cd ..", ctx).toList()
        assertEquals("0", ctx.currentCid)
        assertEquals("/根目录", ctx.currentPath)

        // 4. 根目录下 cd .. 仍保留在根目录
        engine.execute("cd ..", ctx).toList()
        assertEquals("0", ctx.currentCid)
        assertEquals("/根目录", ctx.currentPath)
    }

    @Test
    fun testCdVariationsAndErrors() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val folder = FileBean(name = "docs", categoryId = "10", isFolder = true)
        val file = FileBean(name = "note.txt", fileId = "20", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(folder, file), cid = "0", count = 2, order = "", path = listOf(PathBean("0", "根目录", "0"))))
        ctx.fileCacheManager.put("10", FilesBean(fileBeanList = arrayListOf(), cid = "10", count = 0, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("10", "docs", "0"))))

        // 1. cd 到子目录（带斜杠）
        engine.execute("cd docs/", ctx).toList()
        assertEquals("10", ctx.currentCid)
        assertEquals("/根目录/docs", ctx.currentPath)

        // 2. cd 无参数默认切回根目录
        engine.execute("cd", ctx).toList()
        assertEquals("0", ctx.currentCid)
        assertEquals("/根目录", ctx.currentPath)

        // 3. cd ~ 切回根目录
        engine.execute("cd docs", ctx).toList()
        engine.execute("cd ~", ctx).toList()
        assertEquals("0", ctx.currentCid)

        // 4. cd 到普通文件报错
        val outCdFile = engine.executeStrings("cd note.txt", ctx)
        assertTrue(outCdFile[0].contains("no such file or directory: note.txt"))

        // 5. cd 到不存在的目录报错
        val outCdNotExist = engine.executeStrings("cd not_exist_dir", ctx)
        assertTrue(outCdNotExist[0].contains("no such file or directory: not_exist_dir"))
    }

    /**
     * 1. 基本功能测试用例 (C001, C004, C006-C010)
     */
    @Test
    fun testCdBasicFunctions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val t1Folder = FileBean(name = "t1", categoryId = "100", isFolder = true)
        val t2Folder = FileBean(name = "t2", categoryId = "200", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(t1Folder), cid = "0", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"))))
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(t2Folder), cid = "100", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("100", "t1", "0"))))
        ctx.fileCacheManager.put("200", FilesBean(fileBeanList = arrayListOf(), cid = "200", count = 0, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("100", "t1", "0"), PathBean("200", "t2", "100"))))

        // C001: 查看帮助
        val outHelp = engine.executeStrings("cd -h", ctx)
        assertTrue(outHelp.any { it.contains("cd") })

        // C004: 进入绝对路径
        engine.execute("cd /根目录/t1", ctx).toList()
        assertEquals("100", ctx.currentCid)

        // C006: 连续上级 cd ../..
        engine.execute("cd t2", ctx).toList()
        engine.execute("cd ../..", ctx).toList()
        assertEquals("0", ctx.currentCid)

        // C007: 当前目录 cd .
        engine.execute("cd .", ctx).toList()
        assertEquals("0", ctx.currentCid)

        // C008: 根目录 cd /
        engine.execute("cd t1", ctx).toList()
        engine.execute("cd /", ctx).toList()
        assertEquals("0", ctx.currentCid)

        // C009: 切换后 pwd
        engine.execute("cd t1", ctx).toList()
        val outPwd = engine.executeStrings("pwd", ctx)
        assertTrue(outPwd.contains("/根目录/t1"))

        // C010: 切换后 ls
        val outLs = engine.executeStrings("ls", ctx)
        assertTrue(outLs.contains("t2/"))
    }

    /**
     * 2. 路径与转义测试用例 (C011-C024)
     */
    @Test
    fun testCdPathAndEscaping() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val dirSpace = FileBean(name = "我的 目录", categoryId = "101", isFolder = true)
        val dirSingleQuote = FileBean(name = "it's", categoryId = "102", isFolder = true)
        val dirDoubleQuote = FileBean(name = "say\"hi\"", categoryId = "103", isFolder = true)
        val dirDollar = FileBean(name = "price$1", categoryId = "104", isFolder = true)
        val dirExclamation = FileBean(name = "important!", categoryId = "105", isFolder = true)
        val dirHash = FileBean(name = "note#1", categoryId = "106", isFolder = true)
        val dirChinese = FileBean(name = "测试目录", categoryId = "107", isFolder = true)
        val dirEmoji = FileBean(name = "😀目录", categoryId = "108", isFolder = true)
        val dirBackslash = FileBean(name = "a\\b", categoryId = "109", isFolder = true)
        val dirTab = FileBean(name = "a\tb", categoryId = "110", isFolder = true)
        val dirNewline = FileBean(name = "a\nb", categoryId = "111", isFolder = true)

        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(
            dirSpace, dirSingleQuote, dirDoubleQuote, dirDollar, dirExclamation,
            dirHash, dirChinese, dirEmoji, dirBackslash, dirTab, dirNewline
        ), cid = "0", count = 11, order = "", path = listOf(PathBean("0", "根目录", "0"))))

        // C011: 路径含空格未转义（解析为多参数，报错）
        val out11 = engine.executeStrings("cd /我的 目录", ctx)
        assertTrue(out11.any { it.contains("no such file") || it.contains("cd") })

        // C012: 路径含空格用双引号
        engine.execute("cd \"我的 目录\"", ctx).toList()
        assertEquals("101", ctx.currentCid)

        // C013: 路径含空格用反斜杠
        engine.execute("cd /", ctx).toList()
        engine.execute("cd /我的\\ 目录", ctx).toList()
        assertEquals("101", ctx.currentCid)

        // C014: 路径含单引号
        engine.execute("cd /", ctx).toList()
        engine.execute("cd \"it's\"", ctx).toList()
        assertEquals("102", ctx.currentCid)

        // C015: 路径含双引号
        engine.execute("cd /", ctx).toList()
        engine.execute("cd 'say\"hi\"'", ctx).toList()
        assertEquals("103", ctx.currentCid)

        // C016: 路径含 $
        engine.execute("cd /", ctx).toList()
        engine.execute("cd 'price\$1'", ctx).toList()
        assertEquals("104", ctx.currentCid)

        // C017: 路径含 !
        engine.execute("cd /", ctx).toList()
        engine.execute("cd 'important!'", ctx).toList()
        assertEquals("105", ctx.currentCid)

        // C018: 路径含 #
        engine.execute("cd /", ctx).toList()
        engine.execute("cd 'note#1'", ctx).toList()
        assertEquals("106", ctx.currentCid)

        // C019: 路径含中文
        engine.execute("cd /", ctx).toList()
        engine.execute("cd 测试目录", ctx).toList()
        assertEquals("107", ctx.currentCid)

        // C020: 路径含 emoji
        engine.execute("cd /", ctx).toList()
        engine.execute("cd '😀目录'", ctx).toList()
        assertEquals("108", ctx.currentCid)

        // C021: 路径含反斜杠
        engine.execute("cd /", ctx).toList()
        engine.execute("cd 'a\\\\b'", ctx).toList()
        assertEquals("109", ctx.currentCid)

        // C022: 路径含 tab
        engine.execute("cd /", ctx).toList()
        engine.execute("cd 'a\tb'", ctx).toList()
        assertEquals("110", ctx.currentCid)

        // C023: 路径含换行
        engine.execute("cd /", ctx).toList()
        engine.execute("cd 'a\nb'", ctx).toList()
        assertEquals("111", ctx.currentCid)

        // C024: 路径含 .. 混合
        engine.execute("cd ../测试目录/../😀目录", ctx).toList()
        assertEquals("108", ctx.currentCid)
    }

    /**
     * 3. 异常与边界测试用例 (C033-C037, C039, C040)
     */
    @Test
    fun testCdExceptionsAndBoundaries() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // C033: 空字符串 cd ''
        val out33 = engine.executeStrings("cd ''", ctx)
        assertTrue(out33.isNotEmpty())

        // C034: 多个参数 cd a b
        val out34 = engine.executeStrings("cd a b", ctx)
        assertTrue(out34.isNotEmpty())

        // C035: 权限不足/非法权限目录
        val out35 = engine.executeStrings("cd /受限目录", ctx)
        assertTrue(out35.isNotEmpty())

        // C036: 超长路径
        val longPath = "a/".repeat(100)
        val out36 = engine.executeStrings("cd $longPath", ctx)
        assertTrue(out36.isNotEmpty())

        // C037: 深层路径
        val out37 = engine.executeStrings("cd a/b/c/d/e", ctx)
        assertTrue(out37.isNotEmpty())

        // C039: 切换失败后 pwd 保持原路径
        val prevPath = ctx.currentPath
        engine.execute("cd /不存在", ctx).toList()
        assertEquals(prevPath, ctx.currentPath)

        // C040: 软链/循环链接
        val out40 = engine.executeStrings("cd link", ctx)
        assertTrue(out40.isNotEmpty())
    }

    /**
     * 4. 管道与组合逻辑测试用例 (C041-C050)
     */
    @Test
    fun testCdPipelineAndCombinations() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val folder = FileBean(name = "t1", categoryId = "100", isFolder = true)
        val subFolder = FileBean(name = "t2", categoryId = "200", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(folder), cid = "0", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"))))
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(subFolder), cid = "100", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("100", "t1", "0"))))

        // C041: cd 后接 pwd
        engine.execute("cd t1", ctx).toList()
        val out41 = engine.executeStrings("pwd", ctx)
        assertTrue(out41.contains("/根目录/t1"))

        // C042: cd 后接 ls 管道
        val out42 = engine.executeStrings("ls | head", ctx)
        assertTrue(out42.contains("t2/"))

        // C043: cd 静默执行无 stdout
        engine.execute("cd /", ctx).toList()
        val out43 = engine.executeStrings("cd t1", ctx)
        assertTrue(out43.isEmpty())

        // C044: cd 成功后执行 echo
        val out44 = engine.executeStrings("echo done", ctx)
        assertEquals(listOf("done"), out44)

        // C045: cd 失败报错
        val out45 = engine.executeStrings("cd /不存在", ctx)
        assertTrue(out45.any { it.contains("no such file") })

        // C046: cd 失败后保持原路径
        val prevPath = ctx.currentPath
        engine.execute("cd /不存在", ctx).toList()
        assertEquals(prevPath, ctx.currentPath)

        // C047: cd 切换后 pwd | wc -l
        val out47 = engine.executeStrings("pwd | wc -l", ctx)
        assertEquals(listOf("1"), out47)

        // C048: cd 切换后查看 pwd
        val out48 = engine.executeStrings("pwd", ctx)
        assertTrue(out48.contains("/根目录/t1"))

        // C049: cd 后查看 history
        val out49 = engine.executeStrings("history | tail", ctx)
        assertTrue(out49.isNotEmpty())

        // C050: cd 切换后 pwd | xargs echo
        val out50 = engine.executeStrings("pwd | xargs echo", ctx)
        assertTrue(out50[0].contains("/根目录/t1"))
    }
}
