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
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // R009: rm 缺失参数
        val rmEmpty = engine.executeStrings("rm", ctx)
        assertEquals(listOf("rm: missing operand"), rmEmpty)

        // R040: rm 尝试删除根目录
        val rmRoot = engine.executeStrings("rm /", ctx)
        assertTrue(rmRoot[0].contains("Cannot remove root directory"))

        // R011: rm 尝试删除不存在的路径
        val rmNotFound = engine.executeStrings("rm 't1/non_existent/'", ctx)
        assertTrue(rmNotFound[0].contains("cannot remove 't1/non_existent/': No such file or directory"))

        // R010: rm -f 不存在的文件提示文件不存在
        val rmNotFoundForce = engine.executeStrings("rm -f notexist_file.txt", ctx)
        assertEquals(listOf("rm: cannot remove 'notexist_file.txt': No such file or directory"), rmNotFoundForce)
    }

    @Test
    fun testRmConfirmationAndSafety() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)

        // R003: 模拟交互取消删除
        val cancelCtx = TerminalContext(onConfirmRequest = { false })
        cancelCtx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val targetFolder = FileBean(name = "important_dir", categoryId = "10", isFolder = true)
        cancelCtx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(targetFolder), cid = "0", count = 1, order = "", path = emptyList()))

        val outCancel = engine.executeStrings("rm important_dir", cancelCtx)
        assertEquals(listOf("rm: 已取消删除 'important_dir'"), outCancel)

        // 尝试删除当前工作目录 '.'
        val outCurrent = engine.executeStrings("rm .", cancelCtx)
        assertTrue(outCurrent[0].contains("Cannot remove root directory") || outCurrent[0].contains("Cannot remove current working directory"))

        // 在子目录下尝试删除当前工作目录
        cancelCtx.updateDirectory(listOf(PathBean("0", "根目录", "0"), PathBean("10", "important_dir", "0")))
        val outCurrentSub = engine.executeStrings("rm .", cancelCtx)
        assertTrue(outCurrentSub[0].contains("Cannot remove current working directory"))
    }

    @Test
    fun testRmPathAndEscaping() = runBlocking {
        val mockRepo = object : FileRepository() {
            override suspend fun delete(pid: String, fid: String): BaseReturnMessage {
                return BaseReturnMessage(state = true)
            }
        }
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo)
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val fSpaced = FileBean(name = "my file.txt", fileId = "1", isFolder = false)
        val fSpaced2 = FileBean(name = "my file2.txt", fileId = "12", isFolder = false)
        val fChinese = FileBean(name = "测试.txt", fileId = "2", isFolder = false)
        val fEmoji = FileBean(name = "😀.png", fileId = "3", isFolder = false)
        val fAbs = FileBean(name = "abs.txt", fileId = "4", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(fSpaced, fSpaced2, fChinese, fEmoji, fAbs), cid = "0", count = 5, order = "", path = emptyList()))

        // R022: 带空格用双引号包裹
        val outQuoted = engine.executeStrings("rm -f \"my file.txt\"", ctx)
        assertEquals(listOf("rm: 已移入回收站 'my file.txt'"), outQuoted)

        // R023: 带空格用反斜杠转义
        val outEscaped = engine.executeStrings("rm -f my\\ file2.txt", ctx)
        assertEquals(listOf("rm: 已移入回收站 'my file2.txt'"), outEscaped)

        // R029: 中文文件名删除
        val outChinese = engine.executeStrings("rm -f 测试.txt", ctx)
        assertEquals(listOf("rm: 已移入回收站 '测试.txt'"), outChinese)

        // R030: Emoji 文件名删除
        val outEmoji = engine.executeStrings("rm -f '😀.png'", ctx)
        assertEquals(listOf("rm: 已移入回收站 '😀.png'"), outEmoji)

        // R034: 绝对路径删除
        val outAbs = engine.executeStrings("rm -f /根目录/abs.txt", ctx)
        assertEquals(listOf("rm: 已移入回收站 'abs.txt'"), outAbs)
    }

    @Test
    fun testRmOptionCombinationsAndBoundaries() = runBlocking {
        val mockRepo = object : FileRepository() {
            override suspend fun delete(pid: String, fid: String): BaseReturnMessage {
                return BaseReturnMessage(state = true)
            }
        }
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo)
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val f1 = FileBean(name = "file1.txt", fileId = "101", isFolder = false)
        val dir1 = FileBean(name = "dirA", categoryId = "201", isFolder = true)
        val dir2 = FileBean(name = "dirB", categoryId = "202", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, dir1, dir2), cid = "0", count = 3, order = "", path = emptyList()))

        // R045: 重复选项 rm -f -f
        val outDupFlag = engine.executeStrings("rm -f -f file1.txt", ctx)
        assertEquals(listOf("rm: 已移入回收站 'file1.txt'"), outDupFlag)

        // R046 / R047: 组合选项与多路径 rm -rf dirA dirB
        val outCombo = engine.executeStrings("rm -rf dirA dirB", ctx)
        assertEquals(listOf("rm: 已移入回收站 'dirA'", "rm: 已移入回收站 'dirB'"), outCombo)
    }

    @Test
    fun testRmPipelinesAndXargs() = runBlocking {
        val mockRepo = object : FileRepository() {
            override suspend fun delete(pid: String, fid: String): BaseReturnMessage {
                return BaseReturnMessage(state = true)
            }
        }
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo)
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val f1 = FileBean(name = "tmp1.tmp", fileId = "1", isFolder = false)
        val f2 = FileBean(name = "tmp2.tmp", fileId = "2", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, f2), cid = "0", count = 2, order = "", path = emptyList()))

        // R057 / R058: 通过 echo | xargs rm -f 批量删除
        val outXargs = engine.executeStrings("echo 'tmp1.tmp tmp2.tmp' | xargs rm -f", ctx)
        assertEquals(listOf("rm: 已移入回收站 'tmp1.tmp'", "rm: 已移入回收站 'tmp2.tmp'"), outXargs)

        // R051: 删除后 ls 校验
        val outLs = engine.executeStrings("ls", ctx)
        assertEquals(emptyList<String>(), outLs)
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
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

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
