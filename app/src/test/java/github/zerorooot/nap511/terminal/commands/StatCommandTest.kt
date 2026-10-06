package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * stat 命令测试用例集合
 * 覆盖：目录与根目录元数据查看、文件与转义路径、参数校验及管道组合场景
 */
class StatCommandTest {

    /**
     * 测试目录与根路径元数据查看
     */
    @Test
    fun testStatDirectoryAndRoot() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 查看顶级根目录元数据
        val outRoot = engine.executeStrings("stat /", ctx)
        assertTrue(outRoot.any { it.contains("File: 根目录") })
        assertTrue(outRoot.any { it.contains("Type: Directory") })
        assertTrue(outRoot.any { it.contains("CID:  0") })

        // 查看普通子目录元数据
        val t1Folder = createMockFolder("dirA", "100")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(t1Folder), cid = "0", count = 1, order = "", path = emptyList()))

        val outDir = engine.executeStrings("stat dirA", ctx)
        assertTrue(outDir.any { it.contains("File: dirA") })
        assertTrue(outDir.any { it.contains("Type: Directory") })
    }

    /**
     * 测试复杂路径与末尾斜杠路径处理
     */
    @Test
    fun testStatCommandWithPathAndTrailingSlash() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val t1Folder = createMockFolder("t1", "100")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(t1Folder), cid = "0", count = 1, order = "", path = emptyList()))

        val subFolder = createMockFolder("Sample Dir A", "200")
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(subFolder), cid = "100", count = 1, order = "", path = emptyList()))

        // 带空格及末尾斜杠的绝对路径元数据查看
        val out = engine.executeStrings("stat '/根目录/t1/Sample Dir A/'", ctx)
        assertTrue(out.any { it.contains("File: Sample Dir A") })
        assertFalse(out.contains("  File: /"))
    }

    /**
     * 测试普通文件元数据展示与异常处理
     */
    @Test
    fun testStatFileAndErrors() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val sampleFile = createMockFile(
            name = "data.csv",
            fileId = "888",
            size = "2048",
            pickCode = "abcd1234efgh",
            sha1 = "1234567890abcdef"
        )
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(sampleFile), cid = "0", count = 1, order = "", path = emptyList()))

        // 缺失参数容错处理
        val outEmpty = engine.executeStrings("stat", ctx)
        assertEquals(listOf("stat: missing operand"), outEmpty)

        // 查看不存在的文件容错处理
        val outNotFound = engine.executeStrings("stat no_file", ctx)
        assertEquals(listOf("stat: cannot stat 'no_file': No such file or directory"), outNotFound)

        // 查看普通文件元数据（大小、PickCode、SHA-1）
        val outStat = engine.executeStrings("stat data.csv", ctx)
        assertTrue(outStat.any { it.contains("File: data.csv") })
        assertTrue(outStat.any { it.contains("Type: Regular File") })
        assertTrue(outStat.any { it.contains("2048 bytes") })
        assertTrue(outStat.any { it.contains("PickCode: abcd1234efgh") })
        assertTrue(outStat.any { it.contains("SHA-1:    1234567890abcdef") })
    }

    /**
     * 测试转义字符与特殊文件名路径元数据查看
     */
    @Test
    fun testStatEscapingAndSpecialCharacters() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val fSpaced = createMockFile("my file.txt", "1")
        val fChinese = createMockFile("测试.txt", "2")
        val fEmoji = createMockFile("😀.png", "3")
        val fDollar = createMockFile("price$1.txt", "4")
        val fHash = createMockFile("note#1.txt", "5")
        val fEscaped = createMockFile("a\\b.txt", "6")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(fSpaced, fChinese, fEmoji, fDollar, fHash, fEscaped), cid = "0", count = 6, order = "", path = emptyList()))

        // 带空格文件名使用双引号包裹
        val outQuoted = engine.executeStrings("stat \"my file.txt\"", ctx)
        assertTrue(outQuoted.any { it.contains("File: my file.txt") })

        // 带空格文件名使用反斜杠转义
        val outEscaped = engine.executeStrings("stat my\\ file.txt", ctx)
        assertTrue(outEscaped.any { it.contains("File: my file.txt") })

        // 文件名包含 # 井号
        val outHash = engine.executeStrings("stat 'note#1.txt'", ctx)
        assertTrue(outHash.any { it.contains("File: note#1.txt") })

        // 文件名包含中文
        val outChinese = engine.executeStrings("stat 测试.txt", ctx)
        assertTrue(outChinese.any { it.contains("File: 测试.txt") })

        // 文件名包含 Emoji 表情
        val outEmoji = engine.executeStrings("stat '😀.png'", ctx)
        assertTrue(outEmoji.any { it.contains("File: 😀.png") })

        // 文件名包含美元符号 $
        val outDollar = engine.executeStrings("stat 'price$1.txt'", ctx)
        assertTrue(outDollar.any { it.contains("File: price$1.txt") })
    }

    /**
     * 测试管道传递组合场景
     */
    @Test
    fun testStatPipelines() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val sampleFile = createMockFile("data.csv", "888", size = "2048")
        val chineseFile = createMockFile("测试.txt", "889", size = "1024")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(sampleFile, chineseFile), cid = "0", count = 2, order = "", path = emptyList()))

        // stat 输出与 grep 过滤配合
        val outGrep = engine.executeStrings("stat data.csv | grep Size", ctx)
        assertEquals(1, outGrep.size)
        assertTrue(outGrep[0].contains("2048 bytes"))

        // stat 输出与 head 截取配合
        val outHead = engine.executeStrings("stat data.csv | head -n 2", ctx)
        assertEquals(2, outHead.size)
        assertTrue(outHead[0].contains("File: data.csv"))

        // stat 输出与 tail 截取配合
        val outTail = engine.executeStrings("stat data.csv | tail -n 2", ctx)
        assertTrue(outTail.isNotEmpty())

        // stat 输出与 wc -l 统计行数
        val outWc = engine.executeStrings("stat data.csv | wc -l", ctx)
        assertTrue(outWc.isNotEmpty() && outWc[0].toInt() > 0)

        // stat 输出与 sort 结合
        val outSort = engine.executeStrings("stat data.csv | sort", ctx)
        assertTrue(outSort.isNotEmpty())

        // stat 输出与 xargs 结合
        val outXargs = engine.executeStrings("stat data.csv | xargs echo", ctx)
        assertTrue(outXargs.isNotEmpty())

        // 中文文件名 stat 接 grep 过滤
        val outChineseGrep = engine.executeStrings("stat 测试.txt | grep 测试", ctx)
        assertTrue(outChineseGrep.any { it.contains("File: 测试.txt") })

        // 多级管道 stat | grep | head
        val outMultiPipe = engine.executeStrings("stat data.csv | grep -i Size | head -n 1", ctx)
        assertEquals(1, outMultiPipe.size)
        assertTrue(outMultiPipe[0].contains("2048 bytes"))

        // 通过 xargs stat 查看元数据
        val outXargsStat = engine.executeStrings("echo 'data.csv' | xargs stat", ctx)
        assertTrue(outXargsStat.any { it.contains("File: data.csv") })
    }
}
