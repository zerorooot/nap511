package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * pwd 命令测试用例集合
 * 覆盖：不同工作目录下的路径打印与 cd 导航配合校验、管道组合传输场景
 */
class PwdCommandTest {

    /**
     * 测试 cd 目录导航配合下的 pwd 路径打印
     */
    @Test
    fun testPwdBasicAndCdNavigation() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 顶级根目录下 pwd 打印
        val outRoot = engine.executeStrings("pwd", ctx)
        assertEquals(listOf("/根目录"), outRoot)

        // cd 进入子目录后 pwd 打印完整路径
        val workDir = createMockFolder("work", "99")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(workDir), cid = "0", count = 1, order = "", path = emptyList()))
        ctx.fileCacheManager.put("99", FilesBean(fileBeanList = arrayListOf(), cid = "99", count = 0, order = "", path = emptyList()))

        engine.executeStrings("cd work", ctx)
        val outWork = engine.executeStrings("pwd", ctx)
        assertEquals(listOf("/根目录/work"), outWork)

        // cd .. 返回上级目录后 pwd 校验
        engine.executeStrings("cd ..", ctx)
        val outBack = engine.executeStrings("pwd", ctx)
        assertEquals(listOf("/根目录"), outBack)

        // cd / 切回根路径后 pwd 校验
        engine.executeStrings("cd work", ctx)
        engine.executeStrings("cd /", ctx)
        val outSlash = engine.executeStrings("pwd", ctx)
        assertEquals(listOf("/根目录"), outSlash)

        // 多次 cd 连续导航后 pwd 校验
        engine.executeStrings("cd work", ctx)
        engine.executeStrings("cd ..", ctx)
        val outMultiCd = engine.executeStrings("pwd", ctx)
        assertEquals(listOf("/根目录"), outMultiCd)
    }

    /**
     * 测试 pwd 管道传输场景
     */
    @Test
    fun testPwdPipelines() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // pwd 输出接 wc -l 统计
        val outWc = engine.executeStrings("pwd | wc -l", ctx)
        assertEquals(listOf("1"), outWc)

        // pwd 输出接 grep 过滤匹配
        val outGrep = engine.executeStrings("pwd | grep 根目录", ctx)
        assertEquals(listOf("/根目录"), outGrep)

        // pwd 输出接 head 截取
        val outHead = engine.executeStrings("pwd | head -n 1", ctx)
        assertEquals(listOf("/根目录"), outHead)

        // pwd 输出接 tail 截取
        val outTail = engine.executeStrings("pwd | tail -n 1", ctx)
        assertEquals(listOf("/根目录"), outTail)

        // pwd 输出接 xargs echo 传递
        val outXargs = engine.executeStrings("pwd | xargs echo", ctx)
        assertEquals(listOf("/根目录"), outXargs)

        // pwd 输出接 sort 排序
        val outSort = engine.executeStrings("pwd | sort", ctx)
        assertEquals(listOf("/根目录"), outSort)

        // pwd 多级管道 pwd | grep | wc -l
        val outMultiPipe = engine.executeStrings("pwd | grep 根目录 | wc -l", ctx)
        assertEquals(listOf("1"), outMultiPipe)
    }
}
