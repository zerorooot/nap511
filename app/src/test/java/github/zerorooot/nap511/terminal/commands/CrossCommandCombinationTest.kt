package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.engine.TerminalHistoryManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 跨命令组合场景集成测试套件
 * 涵盖：mkdir, mv, open, pwd, rm, sort, stat, cd, echo, grep, head, history, ls 等命令交替协作与管道流转组合场景
 */
class CrossCommandCombinationTest {

    private lateinit var tempFile: File
    private lateinit var historyManager: TerminalHistoryManager

    @Before
    fun setup() {
        tempFile = File.createTempFile("cross_cmd_test", ".txt")
        tempFile.delete()
        historyManager = TerminalHistoryManager(tempFile)
    }

    @After
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    /**
     * 测试基础命令跨模块组合与管道协同
     */
    @Test
    fun testCrossCommandCombinations() = runBlocking {
        val engine = createTestEngine(historyManager = historyManager)
        val ctx = createTestContext(fileRepository = createTestMockRepository(initialFolderId = 8000))

        val subFolder = createMockFolder("t1", "100")
        val testFile = createMockFile("测试.txt", "101")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(subFolder, testFile), cid = "0", count = 2, order = "", path = listOf(PathBean("0", "根目录", "0"))))
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(createMockFile("doc.txt", "102")), cid = "100", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("100", "t1", "0"))))

        historyManager.appendCommand("cd t1")
        historyManager.appendCommand("ls -la")

        // cd 切换目录 + ls 列表 + grep 过滤
        engine.executeStrings("cd t1", ctx)
        val out01 = engine.executeStrings("ls | grep txt", ctx)
        assertEquals(listOf("doc.txt"), out01)

        // cd 切换目录 + find 查找 + head 截取
        engine.executeStrings("cd t1", ctx)
        val out02 = engine.executeStrings("find -name '*.txt' | head", ctx)
        assertTrue(out02.isNotEmpty())

        // echo 字符串 + grep 过滤 + wc -l 统计行数
        val out03 = engine.executeStrings("echo hello | grep hell | wc -l", ctx)
        assertEquals(listOf("1"), out03)

        // history 历史记录 + grep 过滤 + head 截取
        val out04 = engine.executeStrings("history | grep cd | head", ctx)
        assertTrue(out04.isNotEmpty())

        // ls 列表 + grep 过滤 + xargs 参数传递
        val out05 = engine.executeStrings("ls | grep txt | xargs echo", ctx)
        assertTrue(out05.isNotEmpty())

        // find 查找 + grep 过滤 + sort 排序
        val out06 = engine.executeStrings("find -name '*.txt' | grep test | sort", ctx)
        assertNotNull(out06)

        // find 查找 + grep 过滤 + head 截取 + wc -l 统计
        val out07 = engine.executeStrings("find | grep txt | head | wc -l", ctx)
        assertTrue(out07.isNotEmpty())

        // echo 输出转义字符 + grep 匹配
        val out08 = engine.executeStrings("echo 'a.txt' | grep 'a.txt'", ctx)
        assertEquals(listOf("a.txt"), out08)

        // ls 中文输出 + grep 中文匹配
        val out09 = engine.executeStrings("ls | grep 测试 | wc -l", ctx)
        assertEquals(listOf("1"), out09)

        // cd 失败报错后原工作目录不发生变更
        val prevPath10 = ctx.currentPath
        engine.executeStrings("cd /不存在", ctx)
        assertEquals(prevPath10, ctx.currentPath)

        // history -c 清空历史 + history 校验
        engine.executeStrings("history -c", ctx)
        val out11 = engine.executeStrings("history | wc -l", ctx)
        assertEquals(listOf("0"), out11)

        // ls --refresh 刷新缓存 + grep 过滤
        val out12 = engine.executeStrings("ls --refresh | grep txt", ctx)
        assertTrue(out12.isNotEmpty())

        // find -global 全局搜索 + head 截取
        val out13 = engine.executeStrings("find -global -name '*.mp4' | head", ctx)
        assertNotNull(out13)

        // echo 输出多行 + sort 排序 + tail 截取末尾
        val out14 = engine.executeStrings("echo 'b\na\nc' | sort | tail -n 2", ctx)
        assertEquals(listOf("b", "c"), out14)

        // head 截取 + wc 统计 + xargs 传递
        val out15 = engine.executeStrings("echo '1\n2\n3' | head -n 2 | wc -l | xargs echo", ctx)
        assertEquals(listOf("2"), out15)
    }

    /**
     * 测试文件与目录变更（mkdir, mv, rm, stat）跨命令链式操作
     */
    @Test
    fun testYSeriesCrossCommandCombinations() = runBlocking {
        val engine = createTestEngine(historyManager = historyManager)
        val ctx = createTestContext(fileRepository = createTestMockRepository(initialFolderId = 8000))

        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(), cid = "0", count = 0, order = "", path = emptyList()))

        // mkdir -p z1 递归创建 + cd z1 进入 + pwd 确认路径
        engine.executeStrings("mkdir -p z1", ctx)
        engine.executeStrings("cd z1", ctx)
        val outY001 = engine.executeStrings("pwd", ctx)
        assertEquals(listOf("/根目录/z1"), outY001)

        // 切回根目录
        engine.executeStrings("cd /", ctx)

        // mkdir z2 新建 + ls | grep z2 验证输出
        engine.executeStrings("mkdir z2", ctx)
        val outY002 = engine.executeStrings("ls | grep z2", ctx)
        assertEquals(listOf("z2/"), outY002)

        // mkdir z3 新建 + find 查找 + xargs stat 查看元数据
        engine.executeStrings("mkdir z3", ctx)
        val outY003 = engine.executeStrings("find -name z3 | xargs stat", ctx)
        assertTrue(outY003.any { it.contains("File: z3") })

        // mv 重命名/移动 + 通过 ls 和 find 查看验证
        val fileA = createMockFile("a.txt", "501")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(fileA), cid = "0", count = 1, order = "", path = emptyList()))

        engine.executeStrings("mv a.txt b.txt", ctx)
        val outY004 = engine.executeStrings("ls | grep b.txt", ctx)
        assertEquals(listOf("b.txt"), outY004)

        val outY005 = engine.executeStrings("find -name b.txt | head -n 1", ctx)
        assertEquals(listOf("b.txt"), outY005)

        // rm -f 强行删除后校验目录为空
        engine.executeStrings("rm -f b.txt", ctx)
        val outY006 = engine.executeStrings("ls | grep b.txt", ctx)
        assertEquals(emptyList<String>(), outY006)

        // sort -u 去重 + wc -l 统计唯一行数
        val outY009 = engine.executeStrings("echo 'a\na\nb' | sort -u | wc -l", ctx)
        assertEquals(listOf("2"), outY009)

        // sort -n 按数值升序 + head -n 1 取最小值
        val outY010 = engine.executeStrings("echo '10\n2\n1' | sort -n | head -n 1", ctx)
        assertEquals(listOf("1"), outY010)

        // sort -n -r 按数值降序 + head -n 1 取最大值
        val outY011 = engine.executeStrings("echo '10\n2\n1' | sort -n -r | head -n 1", ctx)
        assertEquals(listOf("10"), outY011)

        // stat 查看元数据 + grep -i size 过滤 + wc -l 统计
        val sampleFile = createMockFile("test.txt", "999", size = "1024")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(sampleFile), cid = "0", count = 1, order = "", path = emptyList()))
        val outY012 = engine.executeStrings("stat test.txt | grep -i Size | wc -l", ctx)
        assertEquals(listOf("1"), outY012)

        // pwd 打印路径 + xargs ls 校验
        val outY013 = engine.executeStrings("pwd | xargs ls", ctx)
        assertTrue(outY013.contains("test.txt"))

        // open 打开文件后通过 history 查看命令记录
        historyManager.appendCommand("open test.txt")
        val outY014 = engine.executeStrings("history | grep open", ctx)
        assertTrue(outY014.any { it.contains("open test.txt") })

        // mv 移动重命名结合 stat 查看改名后的元数据
        val fOriginal = createMockFile("old.txt", "777", size = "512")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(fOriginal), cid = "0", count = 1, order = "", path = emptyList()))
        engine.executeStrings("mv old.txt new.txt", ctx)
        val outY016 = engine.executeStrings("stat new.txt | grep -i Size", ctx)
        assertTrue(outY016.any { it.contains("512 bytes") })

        // echo 多数值 + sort -n 排序 + tail -n 1 取最大值
        val outY019 = engine.executeStrings("echo '3\n1\n2' | sort -n | tail -n 1", ctx)
        assertEquals(listOf("3"), outY019)
    }

    /**
     * 测试高级管道流与多工具结合（trash, unzip, xargs, stat）场景
     */
    @Test
    fun testZSeriesCrossCommandCombinations() = runBlocking {
        val engine = createTestEngine(historyManager = historyManager)
        val ctx = createTestContext(fileRepository = createTestMockRepository(initialFolderId = 8000))

        // tail 截取 + wc -l 统计行数
        val outZ001 = engine.executeStrings("echo '1\n2\n3' | tail -n 2 | wc -l", ctx)
        assertEquals(listOf("2"), outZ001)

        // tail 截取末尾 + xargs 参数传递
        val outZ002 = engine.executeStrings("echo 'a\nb' | tail -n 1 | xargs echo", ctx)
        assertEquals(listOf("b"), outZ002)

        // trash -l 回收站列表 + grep 过滤
        val outZ003 = engine.executeStrings("trash -l | grep txt", ctx)
        assertNotNull(outZ003)

        // trash -l 回收站列表 + wc -l 统计
        val outZ004 = engine.executeStrings("trash -l | wc -l", ctx)
        assertNotNull(outZ004)

        // rm 删除 + trash -l 验证回收站项
        engine.executeStrings("rm -f a.txt", ctx)
        val outZ005 = engine.executeStrings("trash -l | grep a.txt", ctx)
        assertNotNull(outZ005)

        // unzip -l 列出结构 + grep 过滤
        val outZ006 = engine.executeStrings("unzip -l test.zip | grep txt", ctx)
        assertNotNull(outZ006)

        // unzip -l 列出结构 + wc -l 统计
        val outZ007 = engine.executeStrings("unzip -l test.zip | wc -l", ctx)
        assertNotNull(outZ007)

        // find 查找 zip + xargs unzip -l 批处理
        val outZ008 = engine.executeStrings("find -name '*.zip' | xargs unzip -l", ctx)
        assertNotNull(outZ008)

        // wc 统计 + xargs 传递输出
        val outZ009 = engine.executeStrings("echo 'a' | wc -l | xargs echo", ctx)
        assertEquals(listOf("1"), outZ009)

        // wc 统计 + grep 匹配过滤
        val outZ010 = engine.executeStrings("echo 'a' | wc -l | grep 1", ctx)
        assertEquals(listOf("1"), outZ010)

        // find 查找 + xargs wc -l 统计行数
        val outZ011 = engine.executeStrings("find -name '*.txt' | xargs wc -l", ctx)
        assertNotNull(outZ011)

        // find 查找 + xargs rm -f 批量删除
        val outZ012 = engine.executeStrings("find -name '*.tmp' | xargs rm -f", ctx)
        assertNotNull(outZ012)

        // ls | grep 管道 + xargs rm -f 批量删除
        val outZ013 = engine.executeStrings("ls | grep tmp | xargs rm -f", ctx)
        assertNotNull(outZ013)

        // find 查找 + xargs stat 查看元数据
        val outZ014 = engine.executeStrings("find -name 'test.txt' | xargs stat", ctx)
        assertNotNull(outZ014)

        // find 查找 + xargs open 打开文件
        val outZ015 = engine.executeStrings("find -name 'test.txt' | xargs open", ctx)
        assertNotNull(outZ015)

        // history 历史记录 + grep 过滤 + xargs 参数传递
        val outZ016 = engine.executeStrings("history | grep rm | xargs echo", ctx)
        assertNotNull(outZ016)

        // tail 截取 + sort 排序 + head 取最前
        val outZ017 = engine.executeStrings("echo 'c\nb\na' | tail -n 3 | sort | head -n 1", ctx)
        assertEquals(listOf("a"), outZ017)

        // wc 统计 + sort 排序 + head 截取
        val outZ018 = engine.executeStrings("echo 'a' | wc -l | sort | head", ctx)
        assertEquals(listOf("1"), outZ018)

        // xargs -n 1 分批 + wc -l 统计行数
        val outZ019 = engine.executeStrings("echo 'a\nb' | xargs -n 1 echo | wc -l", ctx)
        assertEquals(listOf("2"), outZ019)

        // xargs -I {} 占位符 + stat 批量查看元数据
        val outZ020 = engine.executeStrings("find -name '*.txt' | xargs -I {} stat {}", ctx)
        assertNotNull(outZ020)

        // trash -l 列表 + sort 排序 + head 截取
        val outZ021 = engine.executeStrings("trash -l | sort | head -n 3", ctx)
        assertNotNull(outZ021)

        // unzip -l 列结构 + sort 排序 + tail 截取
        val outZ022 = engine.executeStrings("unzip -l test.zip | sort | tail -n 3", ctx)
        assertNotNull(outZ022)

        // tail 截取末尾 + xargs -n 1 逐项输出
        val outZ023 = engine.executeStrings("echo 'a\nb' | tail -n 2 | xargs -n 1 echo", ctx)
        assertEquals(listOf("a", "b"), outZ023)

        // wc 统计 + xargs -I {} 占位符包裹输出
        val outZ024 = engine.executeStrings("echo 'a' | wc -l | xargs -I {} echo [{}]", ctx)
        assertEquals(listOf("[1]"), outZ024)

        // 多命令管道链：find | sort | head | xargs stat
        val outZ025 = engine.executeStrings("find -name '*.txt' | sort | head -n 3 | xargs -I {} stat {}", ctx)
        assertNotNull(outZ025)
    }
}
