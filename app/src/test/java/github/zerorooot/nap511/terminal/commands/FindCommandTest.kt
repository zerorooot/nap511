package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * find 命令测试用例集合
 * 覆盖：指定目录查找、-type 类型选择、-size 尺寸过滤、-empty 空文件/空目录匹配及 -name / -suffix 模糊匹配
 */
class FindCommandTest {

    /**
     * 测试在特定指定目录下搜索与 -type d 目录类型筛选
     */
    @Test
    fun testFindInSpecificDirectory() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 模拟根目录结构：根目录("0") 包含 test("100") 和 2023("200")
        val testFolder = createMockFolder("test", "100")
        val dir2023Folder = createMockFolder("2023", "200")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(testFolder, dir2023Folder), cid = "0", count = 2, order = "", path = emptyList()))

        // 2023 包含 22("300")
        val dir22Folder = createMockFolder("22", "300")
        ctx.fileCacheManager.put("200", FilesBean(fileBeanList = arrayListOf(dir22Folder), cid = "200", count = 1, order = "", path = emptyList()))

        // 2023/22 目录下包含子文件夹 sub_dir("400") 和普通文件 file.txt
        val subDirFolder = createMockFolder("sub_dir", "400")
        val docFile = createMockFile("file.txt", "500")
        ctx.fileCacheManager.put("300", FilesBean(fileBeanList = arrayListOf(subDirFolder, docFile), cid = "300", count = 2, order = "", path = emptyList()))

        // 切换当前工作目录到 /test
        ctx.updateDirectory(listOf(PathBean("100", "test", "0")))

        // 在 /test 目录下执行 "find 2023/22 -type d" 相对路径检索
        val out = engine.executeStrings("find 2023/22 -type d", ctx)

        // 验证查找结果为目标路径下的子目录而非当前 /test 目录项
        assertEquals(1, out.size)
        assertTrue(out[0].contains("/2023/22/sub_dir/"))
    }

    /**
     * 测试 -empty 与 -size 大小过滤选项
     */
    @Test
    fun testFindEmptyAndSizeOptions() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val bigFile = createMockFile("big.mp4", "1", size = "209715200") // 200M
        val smallFile = createMockFile("small.txt", "2", size = "1024")  // 1k
        val emptyFile = createMockFile("empty_file.txt", "3", size = "0")
        val emptyFolder = createMockFolder("empty_folder", "10")
        val nonEmptyFolder = createMockFolder("non_empty_folder", "20")

        ctx.fileCacheManager.put("0", FilesBean(
            fileBeanList = arrayListOf(bigFile, smallFile, emptyFile, emptyFolder, nonEmptyFolder),
            cid = "0", count = 5, order = "", path = emptyList()
        ))
        ctx.fileCacheManager.put("10", FilesBean(
            fileBeanList = arrayListOf(), cid = "10", count = 0, order = "", path = emptyList()
        ))
        ctx.fileCacheManager.put("20", FilesBean(
            fileBeanList = arrayListOf(smallFile), cid = "20", count = 1, order = "", path = emptyList()
        ))

        // find -size +100M 查找大于 100M 的文件
        val outSizePlus = engine.executeStrings("find -size +100M", ctx)
        assertEquals(1, outSizePlus.size)
        assertTrue(outSizePlus[0].contains("big.mp4"))

        // find -type f -size -10k 查找小于 10k 的文件
        val outSizeMinus = engine.executeStrings("find -type f -size -10k", ctx)
        assertEquals(3, outSizeMinus.size)
        assertTrue(outSizeMinus.contains("/根目录/small.txt"))
        assertTrue(outSizeMinus.contains("/根目录/empty_file.txt"))
        assertTrue(outSizeMinus.contains("/根目录/non_empty_folder/small.txt"))

        // find -empty 查找空文件与空文件夹
        val outEmpty = engine.executeStrings("find -empty", ctx)
        assertEquals(2, outEmpty.size)
        assertTrue(outEmpty.any { it.contains("empty_file.txt") })
        assertTrue(outEmpty.any { it.contains("empty_folder/") })
    }

    /**
     * 测试 -name / -suffix 文件名与后缀过滤选项
     */
    @Test
    fun testFindFileAndSuffixOptions() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val docFile = createMockFile("report.pdf", "10", size = "500")
        val videoFile = createMockFile("movie.mp4", "20", size = "1000")
        ctx.fileCacheManager.put("0", FilesBean(
            fileBeanList = arrayListOf(docFile, videoFile), cid = "0", count = 2, order = "", path = emptyList()
        ))

        // find -name '*.pdf' 通配符过滤
        val outPdf = engine.executeStrings("find -name '*.pdf'", ctx)
        assertEquals(1, outPdf.size)
        assertTrue(outPdf[0].contains("report.pdf"))

        // find -suffix mp4 后缀过滤
        val outMp4 = engine.executeStrings("find -suffix mp4", ctx)
        assertEquals(1, outMp4.size)
        assertTrue(outMp4[0].contains("movie.mp4"))
    }

    /**
     * 测试 -not / ! 逻辑非操作符
     */
    @Test
    fun testFindNotOperator() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val textFile = createMockFile("notes.txt", "1", size = "100")
        val imageFile = createMockFile("photo.png", "2", size = "200")
        val emptyFile = createMockFile("zero.dat", "3", size = "0")
        val subFolder = createMockFolder("folder", "4")

        ctx.putMockFiles("0", listOf(textFile, imageFile, emptyFile, subFolder))
        ctx.putMockFiles("4", emptyList())

        // find -not -name '*.txt': 排除所有 txt 文件
        val outNotTxt = engine.executeStrings("find -not -name '*.txt'", ctx)
        assertFalse(outNotTxt.any { it.contains("notes.txt") })
        assertTrue(outNotTxt.any { it.contains("photo.png") })
        assertTrue(outNotTxt.any { it.contains("zero.dat") })
        assertTrue(outNotTxt.any { it.contains("folder/") })

        // find ! -name '*.txt': 别名 ! 行为与 -not 一致
        val outBangTxt = engine.executeStrings("find ! -name '*.txt'", ctx)
        assertEquals(outNotTxt, outBangTxt)

        // find -type f -not -suffix png: 普通文件中排除 png
        val outTypeNotPng = engine.executeStrings("find -type f -not -suffix png", ctx)
        assertTrue(outTypeNotPng.any { it.contains("notes.txt") })
        assertTrue(outTypeNotPng.any { it.contains("zero.dat") })
        assertFalse(outTypeNotPng.any { it.contains("photo.png") })
        assertFalse(outTypeNotPng.any { it.contains("folder/") })

        // find -not -empty: 排除空文件与空目录
        val outNotEmpty = engine.executeStrings("find -not -empty", ctx)
        assertTrue(outNotEmpty.any { it.contains("notes.txt") })
        assertTrue(outNotEmpty.any { it.contains("photo.png") })
        assertFalse(outNotEmpty.any { it.contains("zero.dat") })
        assertFalse(outNotEmpty.any { it.contains("folder/") })
    }

    /**
     * 测试 -or / -o 逻辑或操作符
     */
    @Test
    fun testFindOrOperator() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val txtFile = createMockFile("doc.txt", "1", size = "100")
        val mdFile = createMockFile("readme.md", "2", size = "200")
        val logFile = createMockFile("app.log", "3", size = "300")
        val largeZip = createMockFile("archive.zip", "4", size = "209715200") // 200M
        val subFolder = createMockFolder("logs_dir", "5")

        ctx.putMockFiles("0", listOf(txtFile, mdFile, logFile, largeZip, subFolder))
        ctx.putMockFiles("5", emptyList())

        // find -name '*.txt' -or -name '*.md': 或运算匹配 txt 或 md
        val outTxtOrMd = engine.executeStrings("find -name '*.txt' -or -name '*.md'", ctx)
        assertEquals(2, outTxtOrMd.size)
        assertTrue(outTxtOrMd.any { it.contains("doc.txt") })
        assertTrue(outTxtOrMd.any { it.contains("readme.md") })

        // find -suffix txt -o -suffix md: 别名 -o
        val outAliasO = engine.executeStrings("find -suffix txt -o -suffix md", ctx)
        assertEquals(outTxtOrMd, outAliasO)

        // find -type d -or -size +100M: 匹配目录或大于 100M 的文件
        val outDirOrBig = engine.executeStrings("find -type d -or -size +100M", ctx)
        assertEquals(2, outDirOrBig.size)
        assertTrue(outDirOrBig.any { it.contains("logs_dir/") })
        assertTrue(outDirOrBig.any { it.contains("archive.zip") })
    }

    /**
     * 测试运算符优先级：AND 优先于 OR
     */
    @Test
    fun testFindOperatorPrecedence() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 构造三类文件验证优先级：AND 优先于 OR
        // A: small.txt (1KB, txt)
        // B: small.md (1KB, md)
        // C: big.md (20KB, md)
        val smallTxt = createMockFile("small.txt", "1", size = "1024")
        val smallMd = createMockFile("small.md", "2", size = "1024")
        val bigMd = createMockFile("big.md", "3", size = "20480")

        ctx.putMockFiles("0", listOf(smallTxt, smallMd, bigMd))

        // find -name '*.txt' -or -name '*.md' -and -size +10k
        // 按照 POSIX 优先级，等价于: (*.txt) OR (*.md AND >10k)
        // 期望命中：small.txt（满足左边），big.md（满足右边 AND）；small.md 不应命中
        val outExplicitAnd = engine.executeStrings("find -name '*.txt' -or -name '*.md' -and -size +10k", ctx)
        assertEquals(2, outExplicitAnd.size)
        assertTrue(outExplicitAnd.any { it.contains("small.txt") })
        assertTrue(outExplicitAnd.any { it.contains("big.md") })
        assertFalse(outExplicitAnd.any { it.contains("small.md") })

        // find -name '*.txt' -or -name '*.md' -size +10k
        // 隐式 AND 同样必须优先于 -or
        val outImplicitAnd = engine.executeStrings("find -name '*.txt' -or -name '*.md' -size +10k", ctx)
        assertEquals(outExplicitAnd, outImplicitAnd)
    }

    /**
     * 测试括号分组：显式覆盖运算符优先级
     */
    @Test
    fun testFindParenthesesGrouping() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val smallTxt = createMockFile("small.txt", "1", size = "1024")
        val bigTxt = createMockFile("big.txt", "2", size = "20480")
        val smallMd = createMockFile("small.md", "3", size = "1024")
        val bigMd = createMockFile("big.md", "4", size = "20480")

        ctx.putMockFiles("0", listOf(smallTxt, bigTxt, smallMd, bigMd))

        // find ( -name '*.txt' -or -name '*.md' ) -size +10k
        // 括号改变优先级：无论是 txt 还是 md，都必须大于 10k
        val outGrouped = engine.executeStrings("find ( -name '*.txt' -or -name '*.md' ) -size +10k", ctx)
        assertEquals(2, outGrouped.size)
        assertTrue(outGrouped.any { it.contains("big.txt") })
        assertTrue(outGrouped.any { it.contains("big.md") })
        assertFalse(outGrouped.any { it.contains("small.txt") })
        assertFalse(outGrouped.any { it.contains("small.md") })

        // 测试转义反斜杠括号形式 \( ... \)
        val outEscaped = engine.executeStrings("find \\( -name '*.txt' -or -name '*.md' \\) -size +10k", ctx)
        assertEquals(outGrouped, outEscaped)
    }

    /**
     * 测试德摩根定律与嵌套逻辑
     */
    @Test
    fun testFindDeMorganAndNestedLogic() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val txtFile = createMockFile("data.txt", "1", size = "100")
        val mdFile = createMockFile("data.md", "2", size = "200")
        val logFile = createMockFile("data.log", "3", size = "300")

        ctx.putMockFiles("0", listOf(txtFile, mdFile, logFile))

        // 德摩根定律：!(A or B) 等价于 !A and !B
        // find -not ( -name '*.txt' -or -name '*.md' )
        val outDeMorgan = engine.executeStrings("find -not ( -name '*.txt' -or -name '*.md' )", ctx)
        assertEquals(1, outDeMorgan.size)
        assertTrue(outDeMorgan[0].contains("data.log"))

        // 双重否定：-not -not A 等价于 A
        val outDoubleNot = engine.executeStrings("find -not -not -name '*.log'", ctx)
        assertEquals(1, outDoubleNot.size)
        assertTrue(outDoubleNot[0].contains("data.log"))
    }

    /**
     * 测试逻辑运算符与 -delete -f 批量安全删除协同
     */
    @Test
    fun testFindLogicalWithDelete() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val tmpFile = createMockFile("cache.tmp", "101")
        val bakFile = createMockFile("config.bak", "102")
        val keepFile = createMockFile("source.kt", "103")

        ctx.putMockFiles("0", listOf(tmpFile, bakFile, keepFile))

        // find -name '*.tmp' -or -name '*.bak' -delete -f
        // 逻辑或与批量删除协同：删除 tmp 和 bak，保留 kt
        val outDelete = engine.executeStrings("find -name '*.tmp' -or -name '*.bak' -delete -f", ctx)
        assertTrue(outDelete.any { it.contains("已成功删除 2 / 2 个项目") })
        assertEquals(2, mockRepo.deletedItems.size)
        assertTrue(mockRepo.deletedItems.any { it.second == "101" })
        assertTrue(mockRepo.deletedItems.any { it.second == "102" })
        assertFalse(mockRepo.deletedItems.any { it.second == "103" })
    }

    /**
     * 测试语法异常与边界错误诊断
     */
    @Test
    fun testFindSyntaxErrorHandling() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 1. -not 后面缺少表达式
        val outHangingNot = engine.executeStrings("find -not", ctx)
        assertTrue(outHangingNot.any { it.contains("'-not' 运算符后面缺少表达式") })

        // 2. -or 后面缺少表达式
        val outHangingOr = engine.executeStrings("find -name '*.txt' -or", ctx)
        assertTrue(outHangingOr.any { it.contains("'-or' 运算符右侧缺少表达式") })

        // 3. -or 前面缺少表达式
        val outLeadingOr = engine.executeStrings("find -or -name '*.txt'", ctx)
        assertTrue(outLeadingOr.any { it.contains("'-or' 运算符左侧缺少表达式") })

        // 4. 缺少闭合括号
        val outUnclosedParen = engine.executeStrings("find ( -name '*.txt'", ctx)
        assertTrue(outUnclosedParen.any { it.contains("缺少闭合括号 ')'") })

        // 5. 多余闭合括号
        val outExtraParen = engine.executeStrings("find -name '*.txt' )", ctx)
        assertTrue(outExtraParen.any { it.contains("多余的闭合括号 ')'") })

        // 6. 空括号
        val outEmptyParen = engine.executeStrings("find ( )", ctx)
        assertTrue(outEmptyParen.any { it.contains("括号内表达式不能为空") })

        // 7. -name 缺少参数
        val outMissingNameArg = engine.executeStrings("find -name", ctx)
        assertTrue(outMissingNameArg.any { it.contains("'-name' 缺少参数") })

        // 8. -type 非法类型
        val outInvalidType = engine.executeStrings("find -type x", ctx)
        assertTrue(outInvalidType.any { it.contains("未知的类型 'x'") })

        // 9. -size 非法格式
        val outInvalidSize = engine.executeStrings("find -size abc", ctx)
        assertTrue(outInvalidSize.any { it.contains("无效的文件大小格式 'abc'") })
    }
}
