package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatCommandTest {

    @Test
    fun testStatDirectoryAndRoot() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // T010: 查看顶级根目录元数据
        val outRoot = engine.executeStrings("stat /", ctx)
        assertTrue(outRoot.any { it.contains("File: 根目录") })
        assertTrue(outRoot.any { it.contains("Type: Directory") })
        assertTrue(outRoot.any { it.contains("CID:  0") })

        // T003: 查看子目录元数据
        val t1Folder = FileBean(name = "dirA", categoryId = "100", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(t1Folder), cid = "0", count = 1, order = "", path = emptyList()))

        val outDir = engine.executeStrings("stat dirA", ctx)
        assertTrue(outDir.any { it.contains("File: dirA") })
        assertTrue(outDir.any { it.contains("Type: Directory") })
    }

    @Test
    fun testStatCommandWithPathAndTrailingSlash() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val t1Folder = FileBean(name = "t1", categoryId = "100", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(t1Folder), cid = "0", count = 1, order = "", path = emptyList()))

        val subFolder = FileBean(name = "Sample Dir A", categoryId = "200", isFolder = true)
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(subFolder), cid = "100", count = 1, order = "", path = emptyList()))

        // T007 / T022: 带空格及斜杠的绝对路径
        val out = engine.executeStrings("stat '/根目录/t1/Sample Dir A/'", ctx)
        assertTrue(out.any { it.contains("File: Sample Dir A") })
        assertFalse(out.contains("  File: /"))
    }

    @Test
    fun testStatFileAndErrors() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val sampleFile = FileBean(
            name = "data.csv",
            fileId = "888",
            categoryId = "0",
            size = "2048",
            pickCode = "abcd1234efgh",
            sha1 = "1234567890abcdef",
            isFolder = false
        )
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(sampleFile), cid = "0", count = 1, order = "", path = emptyList()))

        // T005: 缺失参数
        val outEmpty = engine.executeStrings("stat", ctx)
        assertEquals(listOf("stat: missing operand"), outEmpty)

        // T004: 查看不存在的文件
        val outNotFound = engine.executeStrings("stat no_file", ctx)
        assertEquals(listOf("stat: cannot stat 'no_file': No such file or directory"), outNotFound)

        // T002: 查看普通文件元数据
        val outStat = engine.executeStrings("stat data.csv", ctx)
        assertTrue(outStat.any { it.contains("File: data.csv") })
        assertTrue(outStat.any { it.contains("Type: Regular File") })
        assertTrue(outStat.any { it.contains("2048 bytes") })
        assertTrue(outStat.any { it.contains("PickCode: abcd1234efgh") })
        assertTrue(outStat.any { it.contains("SHA-1:    1234567890abcdef") })
    }

    @Test
    fun testStatEscapingAndSpecialCharacters() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val fSpaced = FileBean(name = "my file.txt", fileId = "1", isFolder = false)
        val fChinese = FileBean(name = "测试.txt", fileId = "2", isFolder = false)
        val fEmoji = FileBean(name = "😀.png", fileId = "3", isFolder = false)
        val fDollar = FileBean(name = "price$1.txt", fileId = "4", isFolder = false)
        val fHash = FileBean(name = "note#1.txt", fileId = "5", isFolder = false)
        val fEscaped = FileBean(name = "a\\b.txt", fileId = "6", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(fSpaced, fChinese, fEmoji, fDollar, fHash, fEscaped), cid = "0", count = 6, order = "", path = emptyList()))

        // T022: 带空格文件名用引号包裹
        val outQuoted = engine.executeStrings("stat \"my file.txt\"", ctx)
        assertTrue(outQuoted.any { it.contains("File: my file.txt") })

        // T023: 带空格文件名用反斜杠转义
        val outEscaped = engine.executeStrings("stat my\\ file.txt", ctx)
        assertTrue(outEscaped.any { it.contains("File: my file.txt") })

        // T028: 包含 # 号
        val outHash = engine.executeStrings("stat 'note#1.txt'", ctx)
        assertTrue(outHash.any { it.contains("File: note#1.txt") })

        // T029: 中文文件名
        val outChinese = engine.executeStrings("stat 测试.txt", ctx)
        assertTrue(outChinese.any { it.contains("File: 测试.txt") })

        // T030: Emoji 文件名
        val outEmoji = engine.executeStrings("stat '😀.png'", ctx)
        assertTrue(outEmoji.any { it.contains("File: 😀.png") })

        // T026: 包含美元符号
        val outDollar = engine.executeStrings("stat 'price$1.txt'", ctx)
        assertTrue(outDollar.any { it.contains("File: price$1.txt") })
    }

    @Test
    fun testStatPipelines() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val sampleFile = FileBean(name = "data.csv", fileId = "888", size = "2048", isFolder = false)
        val chineseFile = FileBean(name = "测试.txt", fileId = "889", size = "1024", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(sampleFile, chineseFile), cid = "0", count = 2, order = "", path = emptyList()))

        // T041: stat 输出与 grep 过滤配合
        val outGrep = engine.executeStrings("stat data.csv | grep Size", ctx)
        assertEquals(1, outGrep.size)
        assertTrue(outGrep[0].contains("2048 bytes"))

        // T042: stat 输出与 head 截取配合
        val outHead = engine.executeStrings("stat data.csv | head -n 2", ctx)
        assertEquals(2, outHead.size)
        assertTrue(outHead[0].contains("File: data.csv"))

        // T043: stat 输出与 tail 截取配合
        val outTail = engine.executeStrings("stat data.csv | tail -n 2", ctx)
        assertTrue(outTail.isNotEmpty())

        // T044: stat 输出与 wc -l 统计行数
        val outWc = engine.executeStrings("stat data.csv | wc -l", ctx)
        assertTrue(outWc.isNotEmpty() && outWc[0].toInt() > 0)

        // T045: stat 输出与 sort 结合
        val outSort = engine.executeStrings("stat data.csv | sort", ctx)
        assertTrue(outSort.isNotEmpty())

        // T046: stat 输出与 xargs 结合
        val outXargs = engine.executeStrings("stat data.csv | xargs echo", ctx)
        assertTrue(outXargs.isNotEmpty())

        // T047: 中文文件名 stat 接 grep 过滤
        val outChineseGrep = engine.executeStrings("stat 测试.txt | grep 测试", ctx)
        assertTrue(outChineseGrep.any { it.contains("File: 测试.txt") })

        // T049: 多级管道 stat | grep | head
        val outMultiPipe = engine.executeStrings("stat data.csv | grep -i Size | head -n 1", ctx)
        assertEquals(1, outMultiPipe.size)
        assertTrue(outMultiPipe[0].contains("2048 bytes"))

        // T050: 通过 xargs stat 查看元数据
        val outXargsStat = engine.executeStrings("echo 'data.csv' | xargs stat", ctx)
        assertTrue(outXargsStat.any { it.contains("File: data.csv") })
    }
}
