package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LsCommandTest {

    @Test
    fun testLsSpecificFileWithSpace() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // 模拟当前根目录存在文件夹 "SampleFolder" (cid=123)
        val folder = FileBean(
            name = "SampleFolder",
            categoryId = "123",
            isFolder = true
        )
        val rootFiles = FilesBean(
            fileBeanList = arrayListOf(folder),
            cid = "0",
            count = 1,
            order = "",
            path = listOf(PathBean("0", "根目录", "0"))
        )
        ctx.fileCacheManager.put("0", rootFiles)

        // 模拟子目录下存在带空格的文件 "Sample Document.txt"
        val file = FileBean(
            name = "Sample Document.txt",
            fileId = "999",
            isFolder = false,
            size = "1024"
        )
        val subFiles = FilesBean(
            fileBeanList = arrayListOf(file),
            cid = "123",
            count = 1,
            order = "",
            path = listOf(PathBean("0", "根目录", "0"), PathBean("123", "SampleFolder", "0"))
        )
        ctx.fileCacheManager.put("123", subFiles)

        // 执行 ls -l SampleFolder/Sample\ Document.txt
        val out = engine.executeStrings("ls -l SampleFolder/Sample\\ Document.txt", ctx)
        assertEquals(1, out.size)
        assertTrue(out[0].contains("SampleFolder/Sample Document.txt"))

        // 执行普通 ls SampleFolder/Sample\ Document.txt
        val outShort = engine.executeStrings("ls SampleFolder/Sample\\ Document.txt", ctx)
        assertEquals(listOf("SampleFolder/Sample Document.txt"), outShort)
    }

    @Test
    fun testLsSortingAndPathOptions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val f1 = FileBean(name = "file_c.txt", size = "300", isFolder = false, modifiedTime = "1000")
        val f2 = FileBean(name = "file_a.txt", size = "100", isFolder = false, modifiedTime = "3000")
        val f3 = FileBean(name = ".hidden", size = "50", isFolder = false, modifiedTime = "2000")
        val f4 = FileBean(name = "file_b.txt", size = "200", isFolder = false, modifiedTime = "4000")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, f2, f3, f4), cid = "0", count = 4, order = "", path = listOf(PathBean("0", "根目录", "0"))))

        // 默认 ls 不显示隐藏文件
        val outDefault = engine.executeStrings("ls", ctx)
        assertEquals(listOf("file_c.txt", "file_a.txt", "file_b.txt"), outDefault)

        // ls -a 显示全部文件
        val outAll = engine.executeStrings("ls -a", ctx)
        assertTrue(outAll.contains(".hidden"))
        assertEquals(4, outAll.size)

        // ls -S 按大小降序
        val outSize = engine.executeStrings("ls -S", ctx)
        assertEquals(listOf("file_c.txt", "file_b.txt", "file_a.txt"), outSize)

        // ls -t 按修改时间降序 (4000 -> file_b, 3000 -> file_a, 1000 -> file_c)
        val outTime = engine.executeStrings("ls -t", ctx)
        assertEquals(listOf("file_b.txt", "file_a.txt", "file_c.txt"), outTime)

        // ls -r 反向排序
        val outReverse = engine.executeStrings("ls -S -r", ctx)
        assertEquals(listOf("file_a.txt", "file_b.txt", "file_c.txt"), outReverse)

        // ls 访问不存在的路径
        val outNotFound = engine.executeStrings("ls non_existent_dir", ctx)
        assertTrue(outNotFound[0].contains("cannot access 'non_existent_dir': No such file or directory"))
    }

    /**
     * 1. 基本功能扩展测试用例 (L001, L003-L006, L008-L010)
     */
    @Test
    fun testLsBasicFunctions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val folder = FileBean(name = "t1", categoryId = "100", isFolder = true)
        val file = FileBean(name = "test.txt", fileId = "101", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(folder, file), cid = "0", count = 2, order = "", path = listOf(PathBean("0", "根目录", "0"))))
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(), cid = "100", count = 0, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("100", "t1", "0"))))

        // L001: 查看帮助
        val outHelp = engine.executeStrings("ls -h", ctx)
        assertTrue(outHelp.any { it.contains("ls") })

        // L003: 指定目录
        val out03 = engine.executeStrings("ls /根目录/t1", ctx)
        assertTrue(out03.isEmpty())

        // L004: 当前目录
        val outDot = engine.executeStrings("ls .", ctx)
        assertTrue(outDot.contains("t1/"))

        // L005: 上级目录
        val out05 = engine.executeStrings("ls ..", ctx)
        assertTrue(out05.contains("t1/"))

        // L006: 根目录
        val out06 = engine.executeStrings("ls /", ctx)
        assertTrue(out06.contains("t1/"))

        // L008: 文件路径
        val outSingleFile = engine.executeStrings("ls test.txt", ctx)
        assertEquals(listOf("test.txt"), outSingleFile)

        // L009: 多路径
        val out09 = engine.executeStrings("ls t1 test.txt", ctx)
        assertTrue(out09.isNotEmpty())

        // L010: 空目录
        val outEmptyDir = engine.executeStrings("ls t1", ctx)
        assertTrue(outEmptyDir.isEmpty())
    }

    /**
     * 2. 选项扩展测试用例 (L011, L014, L016, L018-L030)
     */
    @Test
    fun testLsOptionsAndCombinations() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val f1 = FileBean(name = "file_c.doc", size = "300", isFolder = false, updateTime = "1000")
        val f2 = FileBean(name = "file_a.txt", size = "100", isFolder = false, updateTime = "3000")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, f2), cid = "0", count = 2, order = "", path = listOf(PathBean("0", "根目录", "0"))))

        // L011: -l 详细列表
        val outLong = engine.executeStrings("ls -l", ctx)
        assertTrue(outLong[0].startsWith("total"))

        // L014: -u 按访问时间排序
        val outAtime = engine.executeStrings("ls -u", ctx)
        assertEquals(listOf("file_a.txt", "file_c.doc"), outAtime)

        // L016: -X 按扩展名排序
        val outExt = engine.executeStrings("ls -X", ctx)
        assertEquals(listOf("file_c.doc", "file_a.txt"), outExt)

        // L018: -l -a
        val out18 = engine.executeStrings("ls -la", ctx)
        assertTrue(out18.isNotEmpty())

        // L019: -l -t -r
        val out19 = engine.executeStrings("ls -ltr", ctx)
        assertTrue(out19.isNotEmpty())

        // L020: -S -r
        val out20 = engine.executeStrings("ls -Sr", ctx)
        assertEquals(listOf("file_a.txt", "file_c.doc"), out20)

        // L021: -X -r
        val out21 = engine.executeStrings("ls -Xr", ctx)
        assertEquals(listOf("file_a.txt", "file_c.doc"), out21)

        // L022: --refresh
        val out22 = engine.executeStrings("ls --refresh", ctx)
        assertTrue(out22.isNotEmpty())

        // L023: --refresh -l
        val out23 = engine.executeStrings("ls --refresh -l", ctx)
        assertTrue(out23.isNotEmpty())

        // L024: 未知选项
        val out24 = engine.executeStrings("ls -z", ctx)
        assertTrue(out24.isNotEmpty())

        // L025: 选项组合顺序 ls -l -a vs ls -a -l
        val out1 = engine.executeStrings("ls -l -a", ctx)
        val out2 = engine.executeStrings("ls -a -l", ctx)
        assertEquals(out1, out2)

        // L026: 重复选项 ls -l -l
        val out26 = engine.executeStrings("ls -l -l", ctx)
        assertTrue(out26.isNotEmpty())

        // L027: 选项后路径
        val out27 = engine.executeStrings("ls -l /根目录", ctx)
        assertTrue(out27.isNotEmpty())

        // L028: 路径后选项
        val out28 = engine.executeStrings("ls /根目录 -l", ctx)
        assertTrue(out28.isNotEmpty())

        // L029: -- 分隔
        val out29 = engine.executeStrings("ls -- -l", ctx)
        assertTrue(out29.isNotEmpty())

        // L030: 空选项
        val out30 = engine.executeStrings("ls ''", ctx)
        assertTrue(out30.isNotEmpty())
    }

    /**
     * 3. 路径与转义测试用例 (L031, L033-L045)
     */
    @Test
    fun testLsPathAndEscaping() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // L031: 路径含空格引号
        val out31 = engine.executeStrings("ls \"/我的 目录\"", ctx)
        assertTrue(out31.isNotEmpty())

        // L033: 路径含单引号
        val out33 = engine.executeStrings("ls \"it's\"", ctx)
        assertTrue(out33.isNotEmpty())

        // L034: 路径含双引号
        val out34 = engine.executeStrings("ls 'say\"hi\"'", ctx)
        assertTrue(out34.isNotEmpty())

        // L035: 路径含 $
        val out35 = engine.executeStrings("ls 'price\$1'", ctx)
        assertTrue(out35.isNotEmpty())

        // L036: 路径含 !
        val out36 = engine.executeStrings("ls 'important!'", ctx)
        assertTrue(out36.isNotEmpty())

        // L037: 路径含 #
        val out37 = engine.executeStrings("ls 'note#1'", ctx)
        assertTrue(out37.isNotEmpty())

        // L038: 路径含中文
        val out38 = engine.executeStrings("ls 测试目录", ctx)
        assertTrue(out38.isNotEmpty())

        // L039: 路径含 emoji
        val out39 = engine.executeStrings("ls '😀目录'", ctx)
        assertTrue(out39.isNotEmpty())

        // L040: 路径含反斜杠
        val out40 = engine.executeStrings("ls 'a\\\\b'", ctx)
        assertTrue(out40.isNotEmpty())

        // L041: 路径含 tab
        val out41 = engine.executeStrings("ls 'a\tb'", ctx)
        assertTrue(out41.isNotEmpty())

        // L042: 路径含换行
        val out42 = engine.executeStrings("ls 'a\nb'", ctx)
        assertTrue(out42.isNotEmpty())

        // L043: 通配符
        val out43 = engine.executeStrings("ls '*.txt'", ctx)
        assertTrue(out43.isNotEmpty())

        // L044: 路径含 ~
        val out44 = engine.executeStrings("ls ~", ctx)
        assertTrue(out44.isNotEmpty())

        // L045: 路径含 ..
        val out45 = engine.executeStrings("ls ../t1", ctx)
        assertTrue(out45.isNotEmpty())
    }

    /**
     * 4. 管道测试用例 (L051-L075)
     */
    @Test
    fun testLsPipelines() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val f1 = FileBean(name = "test1.txt", size = "100", isFolder = false)
        val f2 = FileBean(name = "test2.doc", size = "200", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, f2), cid = "0", count = 2, order = "", path = listOf(PathBean("0", "根目录", "0"))))

        // L051: ls 接 grep
        val out51 = engine.executeStrings("ls | grep txt", ctx)
        assertEquals(listOf("test1.txt"), out51)

        // L052: ls 接 grep -v
        val out52 = engine.executeStrings("ls | grep -v txt", ctx)
        assertEquals(listOf("test2.doc"), out52)

        // L053: ls 接 head
        val out53 = engine.executeStrings("ls | head", ctx)
        assertTrue(out53.size <= 10)

        // L054: ls 接 head -n
        val out54 = engine.executeStrings("ls | head -n 1", ctx)
        assertEquals(1, out54.size)

        // L055: ls 接 tail
        val out55 = engine.executeStrings("ls | tail", ctx)
        assertTrue(out55.size <= 10)

        // L056: ls 接 tail -n
        val out56 = engine.executeStrings("ls | tail -n 1", ctx)
        assertEquals(1, out56.size)

        // L057: ls 接 wc
        val out57 = engine.executeStrings("ls | wc -l", ctx)
        assertEquals(listOf("2"), out57)

        // L058: ls 接 sort
        val out58 = engine.executeStrings("ls | sort", ctx)
        assertEquals(listOf("test1.txt", "test2.doc"), out58)

        // L059: ls 接 xargs
        val out59 = engine.executeStrings("ls | xargs echo", ctx)
        assertEquals(listOf("test1.txt test2.doc"), out59)

        // L060: ls -l 接 grep
        val out60 = engine.executeStrings("ls -l | grep txt", ctx)
        assertTrue(out60.isNotEmpty())

        // L061: ls -l 接 head
        val out61 = engine.executeStrings("ls -l | head -n 2", ctx)
        assertTrue(out61.size <= 2)

        // L062: ls -a 接 grep
        val out62 = engine.executeStrings("ls -a | grep '^\\.'", ctx)
        assertTrue(out62.isEmpty() || out62.all { it.startsWith(".") })

        // L063: ls 接 grep 接 wc
        val out63 = engine.executeStrings("ls | grep txt | wc -l", ctx)
        assertEquals(listOf("1"), out63)

        // L064: ls 接 grep 接 head
        val out64 = engine.executeStrings("ls | grep txt | head -n 2", ctx)
        assertTrue(out64.size <= 2)

        // L065: ls 接 grep 转义
        val out65 = engine.executeStrings("ls | grep 'a\\.txt'", ctx)
        assertTrue(out65.isEmpty())

        // L066: ls 接 grep 中文
        val out66 = engine.executeStrings("ls | grep 测试", ctx)
        assertTrue(out66.isEmpty())

        // L067: ls 接 grep $
        val out67 = engine.executeStrings("ls | grep 'price\\\$1'", ctx)
        assertTrue(out67.isEmpty())

        // L068: ls 接 grep !
        val out68 = engine.executeStrings("ls | grep 'hi!'", ctx)
        assertTrue(out68.isEmpty())

        // L069: ls 接 grep #
        val out69 = engine.executeStrings("ls | grep 'a#b'", ctx)
        assertTrue(out69.isEmpty())

        // L070: 多级管道
        val out70 = engine.executeStrings("ls -l | grep txt | head | wc -l", ctx)
        assertEquals(listOf("1"), out70)

        // L071: ls --refresh 管道
        val out71 = engine.executeStrings("ls --refresh | head", ctx)
        assertTrue(out71.size <= 10)

        // L072: ls -S 接 head
        val out72 = engine.executeStrings("ls -S | head -n 1", ctx)
        assertEquals(listOf("test2.doc"), out72)

        // L073: ls -t 接 head
        val out73 = engine.executeStrings("ls -t | head -n 1", ctx)
        assertTrue(out73.isNotEmpty())

        // L074: ls -X 接 head
        val out74 = engine.executeStrings("ls -X | head -n 1", ctx)
        assertEquals(listOf("test2.doc"), out74)

        // L075: ls 接 xargs 接 wc
        val out75 = engine.executeStrings("ls | xargs echo | wc -w", ctx)
        assertEquals(listOf("2"), out75)
    }
}
