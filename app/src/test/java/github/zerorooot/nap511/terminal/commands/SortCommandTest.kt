package github.zerorooot.nap511.terminal.commands

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * sort 命令测试用例集合
 * 覆盖：基本功能与选项（-n 数值、-r 逆序、-u 去重）、特殊字符排序、管道组合场景
 */
class SortCommandTest {

    /**
     * 1. 基本功能与选项测试
     */
    @Test
    fun testSortBasicFunctionsAndOptions() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 默认字典序升序排序
        val outAsc = engine.executeStrings("echo 'b\na\nc' | sort", ctx)
        assertEquals(listOf("a", "b", "c"), outAsc)

        // 单行输入排序
        val outSingle = engine.executeStrings("echo 'a' | sort", ctx)
        assertEquals(listOf("a"), outSingle)

        // 默认模式下的数字字典序排序 ("1", "10", "2")
        val outDictNum = engine.executeStrings("echo '10\n2\n1' | sort", ctx)
        assertEquals(listOf("1", "10", "2"), outDictNum)

        // -n 选项按数值大小排序
        val outNum = engine.executeStrings("echo '10\n2\n1' | sort -n", ctx)
        assertEquals(listOf("1", "2", "10"), outNum)

        // -r 选项按逆序排序
        val outRev = engine.executeStrings("echo 'a\nb\nc' | sort -r", ctx)
        assertEquals(listOf("c", "b", "a"), outRev)

        // -n -r 组合按数值逆序排序
        val outNumRev = engine.executeStrings("echo '10\n2\n1' | sort -n -r", ctx)
        assertEquals(listOf("10", "2", "1"), outNumRev)

        // -u 选项去重排序
        val outUniq = engine.executeStrings("echo 'a\nb\na' | sort -u", ctx)
        assertEquals(listOf("a", "b"), outUniq)

        // -n -u 组合按数值去重排序
        val outNumUniq = engine.executeStrings("echo '1\n1\n2' | sort -n -u", ctx)
        assertEquals(listOf("1", "2"), outNumUniq)

        // -n -r -u 显式组合按数值逆序去重排序
        val outAllFlags = engine.executeStrings("echo '1\n1\n2' | sort -n -r -u", ctx)
        assertEquals(listOf("2", "1"), outAllFlags)
    }

    /**
     * 2. 特殊字符与多语言排序测试
     */
    @Test
    fun testSortSpecialCharactersAndEscaping() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 中文字符串排序
        val outChinese = engine.executeStrings("echo '你\n好' | sort", ctx)
        assertEquals(listOf("你", "好"), outChinese)

        // Emoji 表情排序
        val outEmoji = engine.executeStrings("echo '😁\n😀' | sort", ctx)
        assertEquals(listOf("😀", "😁"), outEmoji)

        // 包含特殊字符与美元符号排序
        val outDollar = engine.executeStrings("echo 'price$2\nprice$1' | sort", ctx)
        assertEquals(listOf("price$1", "price$2"), outDollar)
    }

    /**
     * 3. 管道组合测试
     */
    @Test
    fun testSortPipelines() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // sort 排序结果接 head 截取
        val outHead = engine.executeStrings("echo 'c\nb\na' | sort | head -n 2", ctx)
        assertEquals(listOf("a", "b"), outHead)

        // sort 排序结果接 tail 截取
        val outTail = engine.executeStrings("echo 'c\nb\na' | sort | tail -n 2", ctx)
        assertEquals(listOf("b", "c"), outTail)

        // sort 排序结果接 wc -l 统计行数
        val outWc = engine.executeStrings("echo 'c\nb\na' | sort | wc -l", ctx)
        assertEquals(listOf("3"), outWc)

        // sort 排序结果接 grep 过滤
        val outGrep = engine.executeStrings("echo 'a\nb' | sort | grep a", ctx)
        assertEquals(listOf("a"), outGrep)

        // sort 排序结果接 xargs 参数传递
        val outXargs = engine.executeStrings("echo 'b\na' | sort | xargs echo", ctx)
        assertEquals(listOf("a b"), outXargs)

        // sort -u 去重结果接 wc -l 统计唯一行数
        val outUniqWc = engine.executeStrings("echo 'a\na\nb' | sort -u | wc -l", ctx)
        assertEquals(listOf("2"), outUniqWc)
    }
}
