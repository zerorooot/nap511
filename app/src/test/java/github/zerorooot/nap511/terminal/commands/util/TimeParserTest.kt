package github.zerorooot.nap511.terminal.commands.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TimeParser 单元测试用例
 * 覆盖：单位后缀 (s/m/h)、纯数字秒数、冒号格式 (HH:mm:ss, mm:ss)、操作符 (+, -, =) 以及非法格式校验
 */
class TimeParserTest {

    @Test
    fun testParseUnitSuffix() {
        // 1. 秒数
        val filter30s = TimeParser.parse("+30s")
        assertNotNull(filter30s)
        assertEquals('+', filter30s!!.operator)
        assertEquals(30.0, filter30s.target, 0.001)
        assertTrue(TimeParser.matches(35.0, filter30s))
        assertFalse(TimeParser.matches(30.0, filter30s))
        assertFalse(TimeParser.matches(25.0, filter30s))

        // 2. 分钟
        val filter10m = TimeParser.parse("-10m")
        assertNotNull(filter10m)
        assertEquals('-', filter10m!!.operator)
        assertEquals(600.0, filter10m.target, 0.001)
        assertTrue(TimeParser.matches(500.0, filter10m))
        assertFalse(TimeParser.matches(600.0, filter10m))
        assertFalse(TimeParser.matches(700.0, filter10m))

        // 3. 小时与小数
        val filter1p5h = TimeParser.parse("+1.5h")
        assertNotNull(filter1p5h)
        assertEquals('+', filter1p5h!!.operator)
        assertEquals(5400.0, filter1p5h.target, 0.001)
        assertTrue(TimeParser.matches(5500.0, filter1p5h))

        // 4. 等于与纯数字无单位（默认秒）
        val filter300 = TimeParser.parse("300")
        assertNotNull(filter300)
        assertEquals('=', filter300!!.operator)
        assertEquals(300.0, filter300.target, 0.001)
        assertTrue(TimeParser.matches(300.4, filter300)) // 容差在 1.0 秒内
        assertFalse(TimeParser.matches(302.0, filter300))
    }

    @Test
    fun testParseColonFormat() {
        // 1. mm:ss
        val filterMmSs = TimeParser.parse("-10:00")
        assertNotNull(filterMmSs)
        assertEquals('-', filterMmSs!!.operator)
        assertEquals(600.0, filterMmSs.target, 0.001)
        assertTrue(TimeParser.matches(599.0, filterMmSs))
        assertFalse(TimeParser.matches(600.0, filterMmSs))

        // 2. HH:mm:ss
        val filterHhMmSs = TimeParser.parse("+01:30:00")
        assertNotNull(filterHhMmSs)
        assertEquals('+', filterHhMmSs!!.operator)
        assertEquals(5400.0, filterHhMmSs.target, 0.001)
        assertTrue(TimeParser.matches(5401.0, filterHhMmSs))
        assertFalse(TimeParser.matches(5400.0, filterHhMmSs))
        assertFalse(TimeParser.matches(5399.0, filterHhMmSs))

        // 3. 显式 = 操作符
        val filterEq = TimeParser.parse("=00:05:30")
        assertNotNull(filterEq)
        assertEquals('=', filterEq!!.operator)
        assertEquals(330.0, filterEq.target, 0.001)
        assertTrue(TimeParser.matches(330.0, filterEq))
    }

    @Test
    fun testParseInvalidInputs() {
        assertNull(TimeParser.parse(""))
        assertNull(TimeParser.parse("   "))
        assertNull(TimeParser.parse("+"))
        assertNull(TimeParser.parse("-"))
        assertNull(TimeParser.parse("="))
        assertNull(TimeParser.parse("+10x"))
        assertNull(TimeParser.parse("-invalid"))
        assertNull(TimeParser.parse("1:2:3:4"))
        assertNull(TimeParser.parse("-05:-10"))
    }
}
