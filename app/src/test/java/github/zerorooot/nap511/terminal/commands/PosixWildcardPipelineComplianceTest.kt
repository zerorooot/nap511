package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * POSIX 规范通配符 (“*”, “?”) 与管道 (“|”) 跨命令/引擎级合规验收测试套件
 *
 * 参照 IEEE Std 1003.1 (POSIX) Shell & Utilities 规范，集中验证：
 * 1. 通配符展开 (Pathname Expansion) 规范：
 *    - 前导点隐藏文件隔离 (POSIX 2.13.3)
 *    - 显式前导点匹配
 *    - 无匹配回退字面量 (Nomatch Fallback)
 *    - 引号与反斜杠保护不展开 (Quoted Wildcard Isolation)
 *    - 复合模式匹配与多通配
 *    - 相对与绝对路径前缀展开
 *    - 展开排序确定性 (Collation Order)
 *    - 含空格文件名展开防二次分词 (Word Splitting / IFS Isolation)
 *    - 混合匹配与部分无匹配共存
 *    - 单字符通配符 '?' 与转义 '\?'
 *    - 前导横杠文件名展开防选项注入
 * 2. 管道流水线 (Pipelines) 规范：
 *    - 引号与转义管道符保护
 *    - 残缺/连续管道语法错误防御
 *    - 诊断通道与错误流隔离 (Diagnostic Channel Leak Defense)
 *    - 下游早退短路 (Broken Pipe / Early Termination)
 *    - 深层多级流水线连续流转
 *    - 空流流转
 *    - 混流隔离 (混合标准输出与错误输出时 stderr 绝不流入 stdin)
 *    - 管道输入与显式文件参数优先级 (Stdin vs Positional Arguments)
 */
class PosixWildcardPipelineComplianceTest {

    @org.junit.Before
    fun setUp() {
        github.zerorooot.nap511.util.TextFileHelper.clearMemoryCache()
        runBlocking {
            github.zerorooot.nap511.util.FileCacheManager.clearAll()
        }
    }

    // =========================================================================
    // 1. 通配符展开 (Pathname Expansion) 规范用例 (W-01 ~ W-11)
    // =========================================================================

    /**
     * W-01: POSIX 2.13.3 前导点隔离：模式不以点开头时，通配符严禁匹配隐藏文件
     */
    @Test
    fun testWildcardDotfilesIsolation() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val fHidden = createMockFile(".hidden.txt", "1")
        val fNormal = createMockFile("normal.txt", "2")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(fHidden, fNormal), cid = "0", count = 2, order = "", path = emptyList())
        )

        val out = engine.executeStrings("ls *", ctx)
        assertTrue("输出必须包含普通文件 normal.txt", out.contains("normal.txt"))
        assertFalse("输出必须严格排除前导点隐藏文件 .hidden.txt", out.contains(".hidden.txt"))
    }

    /**
     * W-02: 显式前导点匹配：模式以点开头时能够匹配隐藏文件，但严格排除 '.' 和 '..'
     */
    @Test
    fun testWildcardExplicitDotMatching() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val fHidden = createMockFile(".hidden.txt", "1")
        val fNormal = createMockFile("normal.txt", "2")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(fHidden, fNormal), cid = "0", count = 2, order = "", path = emptyList())
        )

        val out = engine.executeStrings("ls .*.txt", ctx)
        assertTrue("显式前导点应匹配 .hidden.txt", out.contains(".hidden.txt"))
        assertFalse("不应匹配非隐藏文件 normal.txt", out.contains("normal.txt"))
        assertFalse("严禁匹配 '.' 自身", out.contains("."))
        assertFalse("严禁匹配 '..' 自身", out.contains(".."))
    }

    /**
     * W-03: POSIX 2.6.6 无匹配回退：无匹配文件时保留原模式字面量传给下游
     */
    @Test
    fun testWildcardNomatchPreservesLiteral() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 目录下无任何 .xyz 文件
        val fOther = createMockFile("file.txt", "1")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(fOther), cid = "0", count = 1, order = "", path = emptyList())
        )

        val out = engine.executeStrings("ls *.xyz", ctx)
        assertTrue(
            "无匹配时下游命令应接收未展开的字面量并报错",
            out.any { it.contains("cannot access '*.xyz': No such file or directory") }
        )
    }

    /**
     * W-04: 引号隔离防护：单/双引号及反斜杠保护的通配符严禁展开
     */
    @Test
    fun testWildcardQuotedLiteralProtection() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val f1 = createMockFile("a.txt", "1")
        val f2 = createMockFile("b.txt", "2")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(f1, f2), cid = "0", count = 2, order = "", path = emptyList())
        )

        // 双引号保护
        val outDouble = engine.executeStrings("cat \"*.txt\"", ctx)
        assertTrue(
            "双引号通配符不应展开，系统应尝试查找名为 *.txt 的物理文件并报错",
            outDouble.any { it.contains("cat: *.txt: No such file or directory") }
        )

        // 单引号保护
        val outSingle = engine.executeStrings("cat '*.txt'", ctx)
        assertTrue(
            "单引号通配符不应展开，系统应尝试查找名为 *.txt 的物理文件并报错",
            outSingle.any { it.contains("cat: *.txt: No such file or directory") }
        )
    }

    /**
     * W-05: 复合模式与多通配符组合匹配
     */
    @Test
    fun testWildcardMultiPatternCombination() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val f1 = createMockFile("a1b.txt", "1")
        val f2 = createMockFile("a2b.txt", "2")
        val f3 = createMockFile("acx.md", "3")
        val f4 = createMockFile("other.txt", "4")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(f1, f2, f3, f4), cid = "0", count = 4, order = "", path = emptyList())
        )

        val out = engine.executeStrings("ls a*b.*", ctx)
        assertEquals(2, out.size)
        assertTrue(out.contains("a1b.txt"))
        assertTrue(out.contains("a2b.txt"))
        assertFalse(out.contains("acx.md"))
        assertFalse(out.contains("other.txt"))
    }

    /**
     * W-06: 路径前缀通配展开（相对路径与绝对路径）
     */
    @Test
    fun testWildcardPathPrefixRelativeAndAbsolute() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val subFolder = createMockFolder("sub", "100")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(subFolder), cid = "0", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0")))
        )

        val f1 = createMockFile("f1.log", "101")
        val f2 = createMockFile("f2.log", "102")
        ctx.fileCacheManager.put(
            "100",
            FilesBean(fileBeanList = arrayListOf(f1, f2), cid = "100", count = 2, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("100", "sub", "0")))
        )

        // 相对路径前缀
        val outRel = engine.executeStrings("ls sub/*.log", ctx)
        assertEquals(2, outRel.size)
        assertTrue(outRel.contains("sub/f1.log"))
        assertTrue(outRel.contains("sub/f2.log"))

        // 绝对路径前缀
        val outAbs = engine.executeStrings("ls /sub/*.log", ctx)
        assertEquals(2, outAbs.size)
        assertTrue(outAbs.contains("/sub/f1.log"))
        assertTrue(outAbs.contains("/sub/f2.log"))
    }

    /**
     * W-07: 展开结果排序确定性 (Collation Order)
     */
    @Test
    fun testWildcardExpansionCollationOrder() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 乱序加入
        val fZ = createMockFile("z.txt", "1")
        val fA = createMockFile("a.txt", "2")
        val fM = createMockFile("m.txt", "3")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(fZ, fA, fM), cid = "0", count = 3, order = "", path = emptyList())
        )

        val out = engine.executeStrings("ls *.txt", ctx)
        assertEquals(listOf("a.txt", "m.txt", "z.txt"), out)
    }

    /**
     * W-08: 含空格文件名防二次分词 (Word Splitting / IFS Isolation)
     */
    @Test
    fun testWildcardSpacesInFilenameNoWordSplitting() = runBlocking {
        val mockRepo = TestMockFileRepository().apply {
            mockDownloadStreams["compliance_801"] = "space file content"
        }
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val fSpace = createMockFile("file with space.txt", "compliance_801")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(fSpace), cid = "0", count = 1, order = "", path = emptyList())
        )

        val out = engine.executeStrings("cat *.txt", ctx)
        assertEquals(listOf("space file content"), out)
    }

    /**
     * W-09: 混合匹配与部分无匹配 (Mixed Match & Partial Nomatch)
     */
    @Test
    fun testWildcardMixedMatchAndNomatch() = runBlocking {
        val mockRepo = TestMockFileRepository().apply {
            mockDownloadStreams["compliance_901"] = "alpha text"
        }
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val fA = createMockFile("a.txt", "compliance_901")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(fA), cid = "0", count = 1, order = "", path = emptyList())
        )

        val out = engine.executeStrings("cat *.txt *.bak", ctx)
        assertTrue("成功匹配的项应正常读取并输出", out.contains("alpha text"))
        assertTrue("无匹配的模式保留字面量并由下游命令报错", out.any { it.contains("cat: *.bak: No such file or directory") })
    }

    /**
     * W-10: 单字符通配符 '?' 与转义保护
     */
    @Test
    fun testWildcardSingleCharQuestionMark() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val f1 = createMockFile("doc1.txt", "1")
        val f2 = createMockFile("doc12.txt", "2")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(f1, f2), cid = "0", count = 2, order = "", path = emptyList())
        )

        val out = engine.executeStrings("ls doc?.txt", ctx)
        assertEquals(listOf("doc1.txt"), out)
        assertFalse(out.contains("doc12.txt"))
    }

    /**
     * W-11: 前导横杠文件名展开防选项注入
     */
    @Test
    fun testWildcardLeadingDashOptionInjectionDefense() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val fDash = createMockFile("-f.txt", "1")
        val fNormal = createMockFile("normal.txt", "2")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(fDash, fNormal), cid = "0", count = 2, order = "", path = emptyList())
        )

        val out = engine.executeStrings("ls *.txt", ctx)
        assertTrue(out.contains("-f.txt"))
        assertTrue(out.contains("normal.txt"))
    }

    // =========================================================================
    // 2. 管道流水线 (Pipelines) 规范用例 (P-01 ~ P-11)
    // =========================================================================

    /**
     * P-01: 引号包裹的管道符 '|' 绝不切分命令阶段
     */
    @Test
    fun testPipeInsideDoubleAndSingleQuotes() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val outDouble = engine.executeStrings("echo \"alpha | beta\" | wc -l", ctx)
        assertEquals(listOf("1"), outDouble)

        val outSingle = engine.executeStrings("echo 'foo | bar' | grep 'bar'", ctx)
        assertEquals(listOf("foo | bar"), outSingle)
    }

    /**
     * P-02: 反斜杠转义管道符 '\|' 保护为字面量
     */
    @Test
    fun testPipeEscapedWithBackslash() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val out = engine.executeStrings("echo alpha \\| beta | wc -l", ctx)
        assertEquals(listOf("1"), out)
    }

    /**
     * P-03: 语法错误防御：尾部悬挂管道符
     */
    @Test
    fun testPipelineSyntaxErrorHangingPipe() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val out = engine.executeStrings("ls |", ctx)
        assertTrue(
            "尾部悬挂管道符必须触发语法错误诊断",
            out.any { it.contains("syntax error near unexpected token '|'") }
        )
    }

    /**
     * P-04: 语法错误防御：连续空管道阶段
     */
    @Test
    fun testPipelineSyntaxErrorConsecutivePipes() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val out = engine.executeStrings("echo hello | | grep hello", ctx)
        assertTrue(
            "连续管道符必须触发语法错误诊断",
            out.any { it.contains("syntax error near unexpected token '|'") }
        )
    }

    /**
     * P-05: 管道诊断错误通道隔离：全错误命令报错信息不流入下游 stdin
     */
    @Test
    fun testPipelineDiagnosticErrorChannelIsolation() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val outCat = engine.executeStrings("cat non_existent_file.txt | wc -l", ctx)
        assertEquals(listOf("0"), outCat)

        val outRm = engine.executeStrings("rm -f invalid_item | wc -l", ctx)
        assertEquals(listOf("0"), outRm)
    }

    /**
     * P-06: 下游短路早退 (Early Termination) 截断
     */
    @Test
    fun testPipelineEarlyTerminationShortCircuit() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val out = engine.executeStrings("echo -e 'line1\\nline2\\nline3\\nline4\\nline5' | head -n 2", ctx)
        assertEquals(listOf("line1", "line2"), out)
    }

    /**
     * P-07: 5 级深层管道流水线连续流转
     */
    @Test
    fun testPipelineDeepMultiStageChaining() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val out = engine.executeStrings(
            "echo -e '5\\n1\\n3\\n2\\n4' | grep -v 3 | sort -n | head -n 3 | wc -l",
            ctx
        )
        assertEquals(listOf("3"), out)
    }

    /**
     * P-08: 空流整条流水线无数据流转
     */
    @Test
    fun testPipelineEmptyStreamProgression() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val out = engine.executeStrings("echo -n '' | grep 'any' | sort | head -n 10 | wc -l", ctx)
        assertEquals(listOf("0"), out)
    }

    /**
     * P-09: 混流隔离：部分正常数据与部分报错同时存在时，stderr 绝不泄漏到下游管道
     */
    @Test
    fun testPipelineMixedStdoutAndStderrIsolation() = runBlocking {
        val mockRepo = TestMockFileRepository().apply {
            mockDownloadStreams["1001"] = "first valid row\nsecond valid row"
        }
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val fGood = createMockFile("valid.txt", "1001")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(fGood), cid = "0", count = 1, order = "", path = emptyList())
        )

        // 验证 1：下游 grep 正常匹配到 valid.txt 中的内容
        val outMatch = engine.executeStrings("cat valid.txt nonexist.txt | grep valid", ctx)
        assertEquals(listOf("first valid row", "second valid row"), outMatch)

        // 验证 2：下游 grep 统计 "No such file" 或 "error" 的次数必须为 0（错误不进入管道）
        val outErrorCount = engine.executeStrings("cat valid.txt nonexist.txt | grep -c 'No such file'", ctx)
        assertEquals(listOf("0"), outErrorCount)
    }

    /**
     * P-10: 管道输入与显式位置文件参数优先级冲突：显式指定文件时优先读取文件并忽略管道输入
     */
    @Test
    fun testPipelineStdinVsPositionalArgumentPriority() = runBlocking {
        val mockRepo = TestMockFileRepository().apply {
            mockDownloadStreams["2001"] = "content_from_disk_file"
        }
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val fDisk = createMockFile("real_file.txt", "2001")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(fDisk), cid = "0", count = 1, order = "", path = emptyList())
        )

        val out = engine.executeStrings("echo 'content_from_pipe' | cat real_file.txt", ctx)
        assertEquals(listOf("content_from_disk_file"), out)
    }

    /**
     * P-11: 背压与 Broken Pipe 流水线优雅收尾
     */
    @Test
    fun testPipelineUpstreamCancellationOnBrokenPipe() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 构造较大数据行流串联 head -n 5 | wc -l
        val numbers = (1..100).joinToString("\\n")
        val out = engine.executeStrings("echo -e '$numbers' | head -n 5 | wc -l", ctx)
        assertEquals(listOf("5"), out)
    }
}
