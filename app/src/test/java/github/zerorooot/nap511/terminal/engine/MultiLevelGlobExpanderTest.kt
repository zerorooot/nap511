package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.commands.createMockFile
import github.zerorooot.nap511.terminal.commands.createMockFolder
import github.zerorooot.nap511.terminal.commands.createTestContext
import github.zerorooot.nap511.terminal.commands.putMockFiles
import github.zerorooot.nap511.terminal.engine.ast.PositionalArgumentNode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阶梯式多级通配符展开器测试 (MultiLevelGlobExpanderTest)
 *
 * 验证：
 * 1. dir* / *.txt 正常逐层下潜展开，且段内掩码切片精准；
 * 2. 单层下潜匹配目录超过 3 个时触发告警并熔断截断（最多保留前 3 个目录）；
 * 3. 绝对路径 /folder/ * 正常以根 CID ("0") 阶梯展开；
 * 4. 无匹配项时回退原字面量输入 (POSIX Nomatch Fallback)；
 * 5. 缓存优先策略。
 */
class MultiLevelGlobExpanderTest {

    @Test
    fun testSliceSegmentsWithMask() {
        // "dir"/*.txt: 前 5 个字符带掩码 (dir/), 后面 *.txt 不带掩码
        val text = "dir/*.txt"
        // 假设前 3 个字符受到双引号保护: d(true), i(true), r(true), /(false), *(false), .(false), t(false), x(false), t(false)
        val mask = BooleanArray(text.length) { it < 3 }
        val segments = MultiLevelGlobExpander.sliceSegmentsWithMask(text, mask)

        assertEquals(2, segments.size)
        assertEquals("dir", segments[0].text)
        assertTrue(segments[0].mask.all { it }) // dir 全部为 true

        assertEquals("*.txt", segments[1].text)
        assertTrue(segments[1].mask.none { it }) // *.txt 全部为 false
    }

    @Test
    fun testMultiLevelDirectoryDescent() = runBlocking {
        val ctx = createTestContext()

        // 根目录下创建两个子目录: dirA (cid 101), dirB (cid 102)
        val dirA = createMockFolder("dirA", "101")
        val dirB = createMockFolder("dirB", "102")
        ctx.putMockFiles("0", listOf(dirA, dirB))

        // dirA 包含 a1.txt, a2.log
        val fileA1 = createMockFile("a1.txt", "201")
        val fileA2 = createMockFile("a2.log", "202")
        ctx.putMockFiles("101", listOf(fileA1, fileA2), path = listOf(PathBean("0", "根目录", "0"), PathBean("101", "dirA", "0")))

        // dirB 包含 b1.txt, b2.txt
        val fileB1 = createMockFile("b1.txt", "203")
        val fileB2 = createMockFile("b2.txt", "204")
        ctx.putMockFiles("102", listOf(fileB1, fileB2), path = listOf(PathBean("0", "根目录", "0"), PathBean("102", "dirB", "0")))

        val node = PositionalArgumentNode("dir*/*.txt")
        val result = MultiLevelGlobExpander.expand(ctx, node)

        // 展开结果必须按字典序严格排序：dirA/a1.txt, dirB/b1.txt, dirB/b2.txt
        assertEquals(listOf("dirA/a1.txt", "dirB/b1.txt", "dirB/b2.txt"), result)
    }

    @Test
    fun testFanoutThrottlingLimitThreeDirs() = runBlocking {
        val ctx = createTestContext()

        // 构造 5 个子目录：sub1 .. sub5
        val folders = (1..5).map { createMockFolder("sub$it", (100 + it).toString()) }
        ctx.putMockFiles("0", folders)

        // 每个子目录下放一个目标文件 match.txt
        for (i in 1..5) {
            val cid = (100 + i).toString()
            val f = createMockFile("match.txt", (200 + i).toString())
            ctx.putMockFiles(cid, listOf(f), path = listOf(PathBean("0", "根目录", "0"), PathBean(cid, "sub$i", "0")))
        }

        var warningMessage = ""
        val node = PositionalArgumentNode("sub*/*.txt")
        val result = MultiLevelGlobExpander.expand(ctx, node, onWarning = { warningMessage = it })

        // 触发熔断：告警信息不为空且包含阈值说明
        assertTrue("必须触发目录熔断告警", warningMessage.contains("已触发安全熔断保护") && warningMessage.contains("3"))

        // 仅前 3 个目录（sub1, sub2, sub3）被展开，结果大小恰为 3
        assertEquals(3, result.size)
        assertEquals(listOf("sub1/match.txt", "sub2/match.txt", "sub3/match.txt"), result)
    }

    @Test
    fun testAbsolutePathExpansion() = runBlocking {
        val ctx = createTestContext()

        val folder = createMockFolder("docs", "301")
        ctx.putMockFiles("0", listOf(folder))

        val f1 = createMockFile("doc1.pdf", "401")
        val f2 = createMockFile("doc2.pdf", "402")
        ctx.putMockFiles("301", listOf(f1, f2), path = listOf(PathBean("0", "根目录", "0"), PathBean("301", "docs", "0")))

        // 绝对路径通配: /docs/*.pdf
        val node = PositionalArgumentNode("/docs/*.pdf")
        val result = MultiLevelGlobExpander.expand(ctx, node)

        assertEquals(listOf("/docs/doc1.pdf", "/docs/doc2.pdf"), result)
    }

    @Test
    fun testNomatchFallbackPreservesLiteral() = runBlocking {
        val ctx = createTestContext()
        val f = createMockFile("hello.txt", "501")
        ctx.putMockFiles("0", listOf(f))

        // 无任何文件匹配 *.xyz
        val node = PositionalArgumentNode("*.xyz")
        val result = MultiLevelGlobExpander.expand(ctx, node)

        // POSIX 规范：若完全无匹配项，保留原字面量
        assertEquals(listOf("*.xyz"), result)
    }
}
