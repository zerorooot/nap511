package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SortCommandTest {

    @Test
    fun testSortBasicFunctionsAndOptions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // S002: 默认字典序
        val outAsc = engine.executeStrings("echo 'b\na\nc' | sort", ctx)
        assertEquals(listOf("a", "b", "c"), outAsc)

        // S003: 单行输入
        val outSingle = engine.executeStrings("echo 'a' | sort", ctx)
        assertEquals(listOf("a"), outSingle)

        // S005: 数字默认按字典序排序 ("1", "10", "2")
        val outDictNum = engine.executeStrings("echo '10\n2\n1' | sort", ctx)
        assertEquals(listOf("1", "10", "2"), outDictNum)

        // S006: -n 按数值排序
        val outNum = engine.executeStrings("echo '10\n2\n1' | sort -n", ctx)
        assertEquals(listOf("1", "2", "10"), outNum)

        // S007: -r 逆序排序
        val outRev = engine.executeStrings("echo 'a\nb\nc' | sort -r", ctx)
        assertEquals(listOf("c", "b", "a"), outRev)

        // S008: -n -r 数值逆序
        val outNumRev = engine.executeStrings("echo '10\n2\n1' | sort -n -r", ctx)
        assertEquals(listOf("10", "2", "1"), outNumRev)

        // S009: -u 去重排序
        val outUniq = engine.executeStrings("echo 'a\nb\na' | sort -u", ctx)
        assertEquals(listOf("a", "b"), outUniq)

        // S011: -n -u 数值去重
        val outNumUniq = engine.executeStrings("echo '1\n1\n2' | sort -n -u", ctx)
        assertEquals(listOf("1", "2"), outNumUniq)

        // S013: -n -r -u 数值逆序去重
        val outAllFlags = engine.executeStrings("echo '1\n1\n2' | sort -n -r -u", ctx)
        assertEquals(listOf("2", "1"), outAllFlags)
    }

    @Test
    fun testSortSpecialCharactersAndEscaping() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // S015: 中文排序 ("你" \u4f60 < "好" \u597d)
        val outChinese = engine.executeStrings("echo '你\n好' | sort", ctx)
        assertEquals(listOf("你", "好"), outChinese)

        // S016: Emoji 排序
        val outEmoji = engine.executeStrings("echo '😁\n😀' | sort", ctx)
        assertEquals(listOf("😀", "😁"), outEmoji)

        // S031: 包含特殊字符与美元符号
        val outDollar = engine.executeStrings("echo 'price$2\nprice$1' | sort", ctx)
        assertEquals(listOf("price$1", "price$2"), outDollar)
    }

    @Test
    fun testSortPipelines() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // S046: sort 接 head
        val outHead = engine.executeStrings("echo 'c\nb\na' | sort | head -n 2", ctx)
        assertEquals(listOf("a", "b"), outHead)

        // S047: sort 接 tail
        val outTail = engine.executeStrings("echo 'c\nb\na' | sort | tail -n 2", ctx)
        assertEquals(listOf("b", "c"), outTail)

        // S048: sort 接 wc -l
        val outWc = engine.executeStrings("echo 'c\nb\na' | sort | wc -l", ctx)
        assertEquals(listOf("3"), outWc)

        // S049: sort 接 grep
        val outGrep = engine.executeStrings("echo 'a\nb' | sort | grep a", ctx)
        assertEquals(listOf("a"), outGrep)

        // S050: sort 接 xargs
        val outXargs = engine.executeStrings("echo 'b\na' | sort | xargs echo", ctx)
        assertEquals(listOf("a b"), outXargs)

        // S051: sort -u 接 wc -l
        val outUniqWc = engine.executeStrings("echo 'a\na\nb' | sort -u | wc -l", ctx)
        assertEquals(listOf("2"), outUniqWc)
    }
}
