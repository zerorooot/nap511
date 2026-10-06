package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RmCommandTest {

    @Test
    fun testRmValidation() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 1. rm 缺失参数
        val rmEmpty = engine.executeStrings("rm", ctx)
        assertEquals(listOf("rm: missing operand"), rmEmpty)

        // 2. rm 尝试删除根目录
        val rmRoot = engine.executeStrings("rm /", ctx)
        assertTrue(rmRoot[0].contains("Cannot remove root directory"))

        // 3. rm 尝试删除不存在的路径
        val rmNotFound = engine.executeStrings("rm 't1/non_existent/'", ctx)
        assertTrue(rmNotFound[0].contains("cannot remove 't1/non_existent/': No such file or directory"))
    }

    @Test
    fun testRmConfirmationAndSafety() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)

        // 1. 模拟交互取消删除
        val cancelCtx = TerminalContext(onConfirmRequest = { false })
        val targetFolder = FileBean(name = "important_dir", categoryId = "10", isFolder = true)
        cancelCtx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(targetFolder), cid = "0", count = 1, order = "", path = emptyList()))

        val outCancel = engine.executeStrings("rm important_dir", cancelCtx)
        assertEquals(listOf("rm: 已取消删除 'important_dir'"), outCancel)

        // 2. 尝试删除当前工作目录 '.'
        val outCurrent = engine.executeStrings("rm .", cancelCtx)
        assertTrue(outCurrent[0].contains("Cannot remove root directory") || outCurrent[0].contains("Cannot remove current working directory"))

        // 3. 在子目录下尝试删除当前工作目录
        cancelCtx.updateDirectory(listOf(PathBean("0", "根目录", "0"), PathBean("10", "important_dir", "0")))
        val outCurrentSub = engine.executeStrings("rm .", cancelCtx)
        assertTrue(outCurrentSub[0].contains("Cannot remove current working directory"))
    }

    @Test
    fun testRmMutatesCacheInPlace() = runBlocking {
        val f1 = FileBean(name = "file1.txt", fileId = "101", isFolder = false)
        val dir1 = FileBean(name = "folder1", categoryId = "201", isFolder = true)
        val childFile = FileBean(name = "child.txt", fileId = "301", isFolder = false)

        val mockRepo = object : FileRepository() {
            override suspend fun delete(pid: String, fid: String): BaseReturnMessage {
                return BaseReturnMessage(state = true)
            }
        }

        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo)

        // 初始化缓存
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, dir1), cid = "0", count = 2, order = "", path = emptyList()))
        ctx.fileCacheManager.put("201", FilesBean(fileBeanList = arrayListOf(childFile), cid = "201", count = 1, order = "", path = emptyList()))

        // 1. 删除普通文件
        engine.executeStrings("rm -f file1.txt", ctx)
        assertTrue("根目录缓存不应整体失效", ctx.fileCacheManager.containsKey("0"))
        assertEquals(1, ctx.fileCacheManager["0"]!!.fileBeanList.size)
        assertEquals("folder1", ctx.fileCacheManager["0"]!!.fileBeanList[0].name)
        assertEquals(1, ctx.fileCacheManager["0"]!!.count)

        // 2. 递归删除子目录
        engine.executeStrings("rm -r -f folder1", ctx)
        assertTrue("根目录缓存仍保留", ctx.fileCacheManager.containsKey("0"))
        assertTrue("根目录列表已清空", ctx.fileCacheManager["0"]!!.fileBeanList.isEmpty())
        assertEquals(0, ctx.fileCacheManager["0"]!!.count)
        assertFalse("被删除的子目录缓存应被递归清理", ctx.fileCacheManager.containsKey("201"))
    }
}
