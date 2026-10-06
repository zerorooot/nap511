package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PwdCommandTest {

    @Test
    fun testPwdBasicAndCdNavigation() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        // 1. 初始化为顶级根目录（CID "0"）
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // P002: 顶级根目录下 pwd
        val outRoot = engine.executeStrings("pwd", ctx)
        assertEquals(listOf("/根目录"), outRoot)

        // P003: cd 进入子目录后 pwd
        val workDir = FileBean(name = "work", categoryId = "99", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(workDir), cid = "0", count = 1, order = "", path = emptyList()))
        ctx.fileCacheManager.put("99", FilesBean(fileBeanList = arrayListOf(), cid = "99", count = 0, order = "", path = emptyList()))

        engine.executeStrings("cd work", ctx)
        val outWork = engine.executeStrings("pwd", ctx)
        assertEquals(listOf("/根目录/work"), outWork)

        // P004: cd .. 返回上级后 pwd
        engine.executeStrings("cd ..", ctx)
        val outBack = engine.executeStrings("pwd", ctx)
        assertEquals(listOf("/根目录"), outBack)

        // P005: cd / 切回根路径后 pwd
        engine.executeStrings("cd work", ctx)
        engine.executeStrings("cd /", ctx)
        val outSlash = engine.executeStrings("pwd", ctx)
        assertEquals(listOf("/根目录"), outSlash)

        // P008: 多次 cd 后 pwd
        engine.executeStrings("cd work", ctx)
        engine.executeStrings("cd ..", ctx)
        val outMultiCd = engine.executeStrings("pwd", ctx)
        assertEquals(listOf("/根目录"), outMultiCd)
    }

    @Test
    fun testPwdPipelines() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // P011: pwd 接 wc -l
        val outWc = engine.executeStrings("pwd | wc -l", ctx)
        assertEquals(listOf("1"), outWc)

        // P012 / P018: pwd 接 grep
        val outGrep = engine.executeStrings("pwd | grep 根目录", ctx)
        assertEquals(listOf("/根目录"), outGrep)

        // P013: pwd 接 head
        val outHead = engine.executeStrings("pwd | head -n 1", ctx)
        assertEquals(listOf("/根目录"), outHead)

        // P014: pwd 接 tail
        val outTail = engine.executeStrings("pwd | tail -n 1", ctx)
        assertEquals(listOf("/根目录"), outTail)

        // P015: pwd 接 xargs echo
        val outXargs = engine.executeStrings("pwd | xargs echo", ctx)
        assertEquals(listOf("/根目录"), outXargs)

        // P016: pwd 接 sort
        val outSort = engine.executeStrings("pwd | sort", ctx)
        assertEquals(listOf("/根目录"), outSort)

        // P019: pwd 多级管道 pwd | grep | wc -l
        val outMultiPipe = engine.executeStrings("pwd | grep 根目录 | wc -l", ctx)
        assertEquals(listOf("1"), outMultiPipe)
    }
}
