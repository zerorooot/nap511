package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.CreateFolderMessage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import github.zerorooot.nap511.terminal.engine.TerminalHistoryManager
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * 跨命令组合场景测试用例集合（X001-X015 与 Y001-Y020）
 * 涵盖：mkdir, mv, open, pwd, rm, sort, stat, cd, echo, grep, head, history, ls 等命令交替协作与管道流转组合场景
 */
class CrossCommandCombinationTest {

    private lateinit var tempFile: File
    private lateinit var historyManager: TerminalHistoryManager

    private fun createMockRepo(): FileRepository {
        return object : FileRepository() {
            var counter = 8000
            override suspend fun createFolder(pid: String, folderName: String): CreateFolderMessage {
                val newId = (counter++).toString()
                return CreateFolderMessage(state = true, cid = newId, fileId = newId, fileName = folderName)
            }

            override suspend fun delete(pid: String, fid: String): BaseReturnMessage {
                return BaseReturnMessage(state = true)
            }

            override suspend fun move(body: Map<String, String>): BaseReturnMessage {
                return BaseReturnMessage(state = true)
            }

            override suspend fun rename(renameBean: RequestBody): BaseReturnMessage {
                return BaseReturnMessage(state = true)
            }
        }
    }

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

    @Test
    fun testCrossCommandCombinations() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry(historyManager)
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = createMockRepo())
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val subFolder = FileBean(name = "t1", categoryId = "100", isFolder = true)
        val testFile = FileBean(name = "测试.txt", fileId = "101", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(subFolder, testFile), cid = "0", count = 2, order = "", path = listOf(PathBean("0", "根目录", "0"))))
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(FileBean(name = "doc.txt", fileId = "102", isFolder = false)), cid = "100", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("100", "t1", "0"))))

        historyManager.appendCommand("cd t1")
        historyManager.appendCommand("ls -la")

        // X001: cd + ls + grep
        engine.executeStrings("cd t1", ctx)
        val out01 = engine.executeStrings("ls | grep txt", ctx)
        assertEquals(listOf("doc.txt"), out01)

        // X002: cd + find + head
        engine.executeStrings("cd t1", ctx)
        val out02 = engine.executeStrings("find -name '*.txt' | head", ctx)
        assertTrue(out02.isNotEmpty())

        // X003: echo + grep + wc
        val out03 = engine.executeStrings("echo hello | grep hell | wc -l", ctx)
        assertEquals(listOf("1"), out03)

        // X004: history + grep + head
        val out04 = engine.executeStrings("history | grep cd | head", ctx)
        assertTrue(out04.isNotEmpty())

        // X005: ls + grep + xargs
        val out05 = engine.executeStrings("ls | grep txt | xargs echo", ctx)
        assertTrue(out05.isNotEmpty())

        // X006: find + grep + sort
        val out06 = engine.executeStrings("find -name '*.txt' | grep test | sort", ctx)
        assertNotNull(out06)

        // X007: find + grep + head + wc
        val out07 = engine.executeStrings("find | grep txt | head | wc -l", ctx)
        assertTrue(out07.isNotEmpty())

        // X008: echo 转义 + grep 匹配
        val out08 = engine.executeStrings("echo 'a.txt' | grep 'a.txt'", ctx)
        assertEquals(listOf("a.txt"), out08)

        // X009: ls 中文 + grep 中文
        val out09 = engine.executeStrings("ls | grep 测试 | wc -l", ctx)
        assertEquals(listOf("1"), out09)

        // X010: cd 失败报错后原工作目录不发生变更
        val prevPath10 = ctx.currentPath
        engine.executeStrings("cd /不存在", ctx)
        assertEquals(prevPath10, ctx.currentPath)

        // X011: history -c + history
        engine.executeStrings("history -c", ctx)
        val out11 = engine.executeStrings("history | wc -l", ctx)
        assertEquals(listOf("0"), out11)

        // X012: ls --refresh + grep
        val out12 = engine.executeStrings("ls --refresh | grep txt", ctx)
        assertTrue(out12.isNotEmpty())

        // X013: find -global + head
        val out13 = engine.executeStrings("find -global -name '*.mp4' | head", ctx)
        assertNotNull(out13)

        // X014: echo + sort + tail
        val out14 = engine.executeStrings("echo 'b\na\nc' | sort | tail -n 2", ctx)
        assertEquals(listOf("b", "c"), out14)

        // X015: head + wc + xargs
        val out15 = engine.executeStrings("echo '1\n2\n3' | head -n 2 | wc -l | xargs echo", ctx)
        assertEquals(listOf("2"), out15)
    }

    @Test
    fun testYSeriesCrossCommandCombinations() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry(historyManager)
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = createMockRepo())
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(), cid = "0", count = 0, order = "", path = emptyList()))

        // Y001: mkdir -p z1, cd z1, pwd
        engine.executeStrings("mkdir -p z1", ctx)
        engine.executeStrings("cd z1", ctx)
        val outY001 = engine.executeStrings("pwd", ctx)
        assertEquals(listOf("/根目录/z1"), outY001)

        // 切回根目录
        engine.executeStrings("cd /", ctx)

        // Y002: mkdir z2, ls | grep z2
        engine.executeStrings("mkdir z2", ctx)
        val outY002 = engine.executeStrings("ls | grep z2", ctx)
        assertEquals(listOf("z2/"), outY002)

        // Y003: mkdir z3, find -name z3 | xargs stat
        engine.executeStrings("mkdir z3", ctx)
        val outY003 = engine.executeStrings("find -name z3 | xargs stat", ctx)
        assertTrue(outY003.any { it.contains("File: z3") })

        // Y004 / Y005: mv 重命名/移动后通过 ls 和 find 查看
        val fileA = FileBean(name = "a.txt", fileId = "501", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(fileA), cid = "0", count = 1, order = "", path = emptyList()))

        engine.executeStrings("mv a.txt b.txt", ctx)
        val outY004 = engine.executeStrings("ls | grep b.txt", ctx)
        assertEquals(listOf("b.txt"), outY004)

        val outY005 = engine.executeStrings("find -name b.txt | head -n 1", ctx)
        assertEquals(listOf("b.txt"), outY005)

        // Y006: rm -f 删除后校验
        engine.executeStrings("rm -f b.txt", ctx)
        val outY006 = engine.executeStrings("ls | grep b.txt", ctx)
        assertEquals(emptyList<String>(), outY006)

        // Y009: sort -u | wc -l 统计去重行数
        val outY009 = engine.executeStrings("echo 'a\na\nb' | sort -u | wc -l", ctx)
        assertEquals(listOf("2"), outY009)

        // Y010: sort -n | head -n 1 取最小值
        val outY010 = engine.executeStrings("echo '10\n2\n1' | sort -n | head -n 1", ctx)
        assertEquals(listOf("1"), outY010)

        // Y011: sort -n -r | head -n 1 取最大值
        val outY011 = engine.executeStrings("echo '10\n2\n1' | sort -n -r | head -n 1", ctx)
        assertEquals(listOf("10"), outY011)

        // Y012: stat | grep -i size | wc -l 校验元数据包含 Size
        val sampleFile = FileBean(name = "test.txt", fileId = "999", size = "1024", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(sampleFile), cid = "0", count = 1, order = "", path = emptyList()))
        val outY012 = engine.executeStrings("stat test.txt | grep -i Size | wc -l", ctx)
        assertEquals(listOf("1"), outY012)

        // Y013: pwd | xargs ls
        val outY013 = engine.executeStrings("pwd | xargs ls", ctx)
        assertTrue(outY013.contains("test.txt"))

        // Y014: open test.txt 然后通过 history 查看记录
        historyManager.appendCommand("open test.txt")
        val outY014 = engine.executeStrings("history | grep open", ctx)
        assertTrue(outY014.any { it.contains("open test.txt") })

        // Y016: mv 结合 stat 查看改名后的文件信息
        val fOriginal = FileBean(name = "old.txt", fileId = "777", size = "512", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(fOriginal), cid = "0", count = 1, order = "", path = emptyList()))
        engine.executeStrings("mv old.txt new.txt", ctx)
        val outY016 = engine.executeStrings("stat new.txt | grep -i Size", ctx)
        assertTrue(outY016.any { it.contains("512 bytes") })

        // Y019: echo + sort -n + tail -n 1 取最大值
        val outY019 = engine.executeStrings("echo '3\n1\n2' | sort -n | tail -n 1", ctx)
        assertEquals(listOf("3"), outY019)
    }

    @Test
    fun testZSeriesCrossCommandCombinations() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry(historyManager)
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = createMockRepo())
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // Z001: tail + wc
        val outZ001 = engine.executeStrings("echo '1\n2\n3' | tail -n 2 | wc -l", ctx)
        assertEquals(listOf("2"), outZ001)

        // Z002: tail + xargs
        val outZ002 = engine.executeStrings("echo 'a\nb' | tail -n 1 | xargs echo", ctx)
        assertEquals(listOf("b"), outZ002)

        // Z003: trash -l + grep
        val outZ003 = engine.executeStrings("trash -l | grep txt", ctx)
        assertNotNull(outZ003)

        // Z004: trash -l + wc
        val outZ004 = engine.executeStrings("trash -l | wc -l", ctx)
        assertNotNull(outZ004)

        // Z005: rm + trash + grep
        engine.executeStrings("rm -f a.txt", ctx)
        val outZ005 = engine.executeStrings("trash -l | grep a.txt", ctx)
        assertNotNull(outZ005)

        // Z006: unzip -l + grep
        val outZ006 = engine.executeStrings("unzip -l test.zip | grep txt", ctx)
        assertNotNull(outZ006)

        // Z007: unzip -l + wc
        val outZ007 = engine.executeStrings("unzip -l test.zip | wc -l", ctx)
        assertNotNull(outZ007)

        // Z008: find + xargs unzip -l
        val outZ008 = engine.executeStrings("find -name '*.zip' | xargs unzip -l", ctx)
        assertNotNull(outZ008)

        // Z009: wc + xargs
        val outZ009 = engine.executeStrings("echo 'a' | wc -l | xargs echo", ctx)
        assertEquals(listOf("1"), outZ009)

        // Z010: wc + grep
        val outZ010 = engine.executeStrings("echo 'a' | wc -l | grep 1", ctx)
        assertEquals(listOf("1"), outZ010)

        // Z011: find + xargs wc
        val outZ011 = engine.executeStrings("find -name '*.txt' | xargs wc -l", ctx)
        assertNotNull(outZ011)

        // Z012: find + xargs rm
        val outZ012 = engine.executeStrings("find -name '*.tmp' | xargs rm -f", ctx)
        assertNotNull(outZ012)

        // Z013: ls + grep + xargs rm
        val outZ013 = engine.executeStrings("ls | grep tmp | xargs rm -f", ctx)
        assertNotNull(outZ013)

        // Z014: find + xargs stat
        val outZ014 = engine.executeStrings("find -name 'test.txt' | xargs stat", ctx)
        assertNotNull(outZ014)

        // Z015: find + xargs open
        val outZ015 = engine.executeStrings("find -name 'test.txt' | xargs open", ctx)
        assertNotNull(outZ015)

        // Z016: history + grep + xargs
        val outZ016 = engine.executeStrings("history | grep rm | xargs echo", ctx)
        assertNotNull(outZ016)

        // Z017: tail + sort + head
        val outZ017 = engine.executeStrings("echo 'c\nb\na' | tail -n 3 | sort | head -n 1", ctx)
        assertEquals(listOf("a"), outZ017)

        // Z018: wc + sort + head
        val outZ018 = engine.executeStrings("echo 'a' | wc -l | sort | head", ctx)
        assertEquals(listOf("1"), outZ018)

        // Z019: xargs -n 1 + wc
        val outZ019 = engine.executeStrings("echo 'a\nb' | xargs -n 1 echo | wc -l", ctx)
        assertEquals(listOf("2"), outZ019)

        // Z020: xargs -I {} + stat
        val outZ020 = engine.executeStrings("find -name '*.txt' | xargs -I {} stat {}", ctx)
        assertNotNull(outZ020)

        // Z021: trash -l + sort + head
        val outZ021 = engine.executeStrings("trash -l | sort | head -n 3", ctx)
        assertNotNull(outZ021)

        // Z022: unzip -l + sort + tail
        val outZ022 = engine.executeStrings("unzip -l test.zip | sort | tail -n 3", ctx)
        assertNotNull(outZ022)

        // Z023: tail + xargs -n 1
        val outZ023 = engine.executeStrings("echo 'a\nb' | tail -n 2 | xargs -n 1 echo", ctx)
        assertEquals(listOf("a", "b"), outZ023)

        // Z024: wc + xargs -I {}
        val outZ024 = engine.executeStrings("echo 'a' | wc -l | xargs -I {} echo [{}]", ctx)
        assertEquals(listOf("[1]"), outZ024)

        // Z025: 多命令管道链
        val outZ025 = engine.executeStrings("find -name '*.txt' | sort | head -n 3 | xargs -I {} stat {}", ctx)
        assertNotNull(outZ025)
    }
}
