package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * cd 命令测试用例集合
 * 覆盖：目录导航（相对/绝对路径、父目录切换）、转义与特殊字符路径、异常处理及管道组合
 */
class CdCommandTest {

    /**
     * 测试父目录与层级导航逻辑
     */
    @Test
    fun testCdParentDirectory() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 模拟目录结构：根目录("0") -> t1("100") -> t2("200")
        val t1Folder = createMockFolder("t1", "100")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(t1Folder), cid = "0", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"))))

        val t2Folder = createMockFolder("t2", "200")
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(t2Folder), cid = "100", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("100", "t1", "0"))))
        ctx.fileCacheManager.put("200", FilesBean(fileBeanList = arrayListOf(), cid = "200", count = 0, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("100", "t1", "0"), PathBean("200", "t2", "100"))))

        // 进入多级深层子目录 t1/t2
        engine.execute("cd t1/t2", ctx).toList()
        assertEquals("200", ctx.currentCid)
        assertEquals("/根目录/t1/t2", ctx.currentPath)

        // cd .. 返回上一级目录 /根目录/t1 (CID 100)
        engine.execute("cd ..", ctx).toList()
        assertEquals("100", ctx.currentCid)
        assertEquals("/根目录/t1", ctx.currentPath)

        // 再次 cd .. 返回顶级根目录 /根目录 (CID 0)
        engine.execute("cd ..", ctx).toList()
        assertEquals("0", ctx.currentCid)
        assertEquals("/根目录", ctx.currentPath)

        // 根目录下执行 cd .. 应静默保留在根目录
        engine.execute("cd ..", ctx).toList()
        assertEquals("0", ctx.currentCid)
        assertEquals("/根目录", ctx.currentPath)
    }

    /**
     * 测试常用变体与文件类型校验错误
     */
    @Test
    fun testCdVariationsAndErrors() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val folder = createMockFolder("docs", "10")
        val file = createMockFile("note.txt", "20")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(folder, file), cid = "0", count = 2, order = "", path = listOf(PathBean("0", "根目录", "0"))))
        ctx.fileCacheManager.put("10", FilesBean(fileBeanList = arrayListOf(), cid = "10", count = 0, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("10", "docs", "0"))))

        // cd 切换带末尾斜杠的子目录
        engine.execute("cd docs/", ctx).toList()
        assertEquals("10", ctx.currentCid)
        assertEquals("/根目录/docs", ctx.currentPath)

        // cd 无参数默认切回根目录
        engine.execute("cd", ctx).toList()
        assertEquals("0", ctx.currentCid)
        assertEquals("/根目录", ctx.currentPath)

        // cd ~ 切换至主/根目录
        engine.execute("cd docs", ctx).toList()
        engine.execute("cd ~", ctx).toList()
        assertEquals("0", ctx.currentCid)

        // cd 尝试进入普通文件报错
        val outCdFile = engine.executeStrings("cd note.txt", ctx)
        assertTrue(outCdFile[0].contains("no such file or directory: note.txt"))

        // cd 尝试进入不存在的目录报错
        val outCdNotExist = engine.executeStrings("cd not_exist_dir", ctx)
        assertTrue(outCdNotExist[0].contains("no such file or directory: not_exist_dir"))
    }

    /**
     * 1. 基本功能测试
     */
    @Test
    fun testCdBasicFunctions() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val t1Folder = createMockFolder("t1", "100")
        val t2Folder = createMockFolder("t2", "200")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(t1Folder), cid = "0", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"))))
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(t2Folder), cid = "100", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("100", "t1", "0"))))
        ctx.fileCacheManager.put("200", FilesBean(fileBeanList = arrayListOf(), cid = "200", count = 0, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("100", "t1", "0"), PathBean("200", "t2", "100"))))

        // 查看帮助文档
        val outHelp = engine.executeStrings("cd -h", ctx)
        assertTrue(outHelp.any { it.contains("cd") })

        // 切换至绝对路径
        engine.execute("cd /根目录/t1", ctx).toList()
        assertEquals("100", ctx.currentCid)

        // 连续切换上级目录 cd ../..
        engine.execute("cd t2", ctx).toList()
        engine.execute("cd ../..", ctx).toList()
        assertEquals("0", ctx.currentCid)

        // 切换至当前目录 cd .
        engine.execute("cd .", ctx).toList()
        assertEquals("0", ctx.currentCid)

        // 切换至根目录 cd /
        engine.execute("cd t1", ctx).toList()
        engine.execute("cd /", ctx).toList()
        assertEquals("0", ctx.currentCid)

        // 切换目录后通过 pwd 校验路径
        engine.execute("cd t1", ctx).toList()
        val outPwd = engine.executeStrings("pwd", ctx)
        assertTrue(outPwd.contains("/根目录/t1"))

        // 切换目录后通过 ls 校验内容
        val outLs = engine.executeStrings("ls", ctx)
        assertTrue(outLs.contains("t2/"))
    }

    /**
     * 2. 路径与转义测试
     */
    @Test
    fun testCdPathAndEscaping() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val dirSpace = createMockFolder("我的 目录", "101")
        val dirSingleQuote = createMockFolder("it's", "102")
        val dirDoubleQuote = createMockFolder("say\"hi\"", "103")
        val dirDollar = createMockFolder("price$1", "104")
        val dirExclamation = createMockFolder("important!", "105")
        val dirHash = createMockFolder("note#1", "106")
        val dirChinese = createMockFolder("测试目录", "107")
        val dirEmoji = createMockFolder("😀目录", "108")
        val dirBackslash = createMockFolder("a\\b", "109")
        val dirTab = createMockFolder("a\tb", "110")
        val dirNewline = createMockFolder("a\nb", "111")

        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(
            dirSpace, dirSingleQuote, dirDoubleQuote, dirDollar, dirExclamation,
            dirHash, dirChinese, dirEmoji, dirBackslash, dirTab, dirNewline
        ), cid = "0", count = 11, order = "", path = listOf(PathBean("0", "根目录", "0"))))

        // 路径含空格未转义报错处理
        val out11 = engine.executeStrings("cd /我的 目录", ctx)
        assertTrue(out11.any { it.contains("no such file") || it.contains("cd") })

        // 路径含空格使用双引号包裹
        engine.execute("cd \"我的 目录\"", ctx).toList()
        assertEquals("101", ctx.currentCid)

        // 路径含空格使用反斜杠转义
        engine.execute("cd /", ctx).toList()
        engine.execute("cd /我的\\ 目录", ctx).toList()
        assertEquals("101", ctx.currentCid)

        // 路径包含单引号
        engine.execute("cd /", ctx).toList()
        engine.execute("cd \"it's\"", ctx).toList()
        assertEquals("102", ctx.currentCid)

        // 路径包含双引号
        engine.execute("cd /", ctx).toList()
        engine.execute("cd 'say\"hi\"'", ctx).toList()
        assertEquals("103", ctx.currentCid)

        // 路径包含美元符号 $
        engine.execute("cd /", ctx).toList()
        engine.execute("cd 'price\$1'", ctx).toList()
        assertEquals("104", ctx.currentCid)

        // 路径包含惊叹号 !
        engine.execute("cd /", ctx).toList()
        engine.execute("cd 'important!'", ctx).toList()
        assertEquals("105", ctx.currentCid)

        // 路径包含井号 #
        engine.execute("cd /", ctx).toList()
        engine.execute("cd 'note#1'", ctx).toList()
        assertEquals("106", ctx.currentCid)

        // 路径包含中文字符
        engine.execute("cd /", ctx).toList()
        engine.execute("cd 测试目录", ctx).toList()
        assertEquals("107", ctx.currentCid)

        // 路径包含 Emoji 表情
        engine.execute("cd /", ctx).toList()
        engine.execute("cd '😀目录'", ctx).toList()
        assertEquals("108", ctx.currentCid)

        // 路径包含反斜杠
        engine.execute("cd /", ctx).toList()
        engine.execute("cd 'a\\\\b'", ctx).toList()
        assertEquals("109", ctx.currentCid)

        // 路径包含制表符 Tab
        engine.execute("cd /", ctx).toList()
        engine.execute("cd 'a\tb'", ctx).toList()
        assertEquals("110", ctx.currentCid)

        // 路径包含换行符
        engine.execute("cd /", ctx).toList()
        engine.execute("cd 'a\nb'", ctx).toList()
        assertEquals("111", ctx.currentCid)

        // 路径包含 .. 混合相对路径导航
        engine.execute("cd ../测试目录/../😀目录", ctx).toList()
        assertEquals("108", ctx.currentCid)
    }

    /**
     * 3. 异常与边界测试
     */
    @Test
    fun testCdExceptionsAndBoundaries() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 切换至空字符串路径报错处理
        val out33 = engine.executeStrings("cd ''", ctx)
        assertTrue(out33.isNotEmpty())

        // 传入多个参数报错处理
        val out34 = engine.executeStrings("cd a b", ctx)
        assertTrue(out34.isNotEmpty())

        // 切换受限/非法权限路径报错处理
        val out35 = engine.executeStrings("cd /受限目录", ctx)
        assertTrue(out35.isNotEmpty())

        // 切换超长路径报错处理
        val longPath = "a/".repeat(100)
        val out36 = engine.executeStrings("cd $longPath", ctx)
        assertTrue(out36.isNotEmpty())

        // 切换不存在深层路径报错处理
        val out37 = engine.executeStrings("cd a/b/c/d/e", ctx)
        assertTrue(out37.isNotEmpty())

        // 切换目录失败后保持原工作路径不变
        val prevPath = ctx.currentPath
        engine.execute("cd /不存在", ctx).toList()
        assertEquals(prevPath, ctx.currentPath)

        // 切换软链/循环链接路径报错处理
        val out40 = engine.executeStrings("cd link", ctx)
        assertTrue(out40.isNotEmpty())
    }

    /**
     * 4. 管道与组合逻辑测试
     */
    @Test
    fun testCdPipelineAndCombinations() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val folder = createMockFolder("t1", "100")
        val subFolder = createMockFolder("t2", "200")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(folder), cid = "0", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"))))
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(subFolder), cid = "100", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("100", "t1", "0"))))

        // cd 切换后执行 pwd 打印新路径
        engine.execute("cd t1", ctx).toList()
        val out41 = engine.executeStrings("pwd", ctx)
        assertTrue(out41.contains("/根目录/t1"))

        // cd 切换后执行 ls | head 查看内容
        val out42 = engine.executeStrings("ls | head", ctx)
        assertTrue(out42.contains("t2/"))

        // cd 成功执行静默无输出
        engine.execute("cd /", ctx).toList()
        val out43 = engine.executeStrings("cd t1", ctx)
        assertTrue(out43.isEmpty())

        // cd 成功后后置命令正常执行
        val out44 = engine.executeStrings("echo done", ctx)
        assertEquals(listOf("done"), out44)

        // cd 失败报错信息输出
        val out45 = engine.executeStrings("cd /不存在", ctx)
        assertTrue(out45.any { it.contains("no such file") })

        // cd 失败后上下文环境不受污染
        val prevPath = ctx.currentPath
        engine.execute("cd /不存在", ctx).toList()
        assertEquals(prevPath, ctx.currentPath)

        // cd 切换后执行 pwd | wc -l
        val out47 = engine.executeStrings("pwd | wc -l", ctx)
        assertEquals(listOf("1"), out47)

        // cd 切换后确认路径有效性
        val out48 = engine.executeStrings("pwd", ctx)
        assertTrue(out48.contains("/根目录/t1"))

        // cd 切换后查看 history 历史记录
        val out49 = engine.executeStrings("history | tail", ctx)
        assertTrue(out49.isNotEmpty())

        // cd 切换后执行 pwd | xargs echo
        val out50 = engine.executeStrings("pwd | xargs echo", ctx)
        assertTrue(out50[0].contains("/根目录/t1"))
    }
}
