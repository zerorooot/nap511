package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MvCommandTest {

    @Test
    fun testMvMissingOperandValidation() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val out = engine.executeStrings("mv ../", ctx)
        assertEquals(listOf("mv: missing file operand"), out)
    }

    @Test
    fun testMvOperationsAndErrors() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val file1 = FileBean(name = "a.txt", fileId = "1", isFolder = false)
        val file2 = FileBean(name = "b.txt", fileId = "2", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(file1, file2), cid = "0", count = 2, order = "", path = emptyList()))

        // 1. 多个源目标不是目录
        val outMulti = engine.executeStrings("mv a.txt b.txt not_a_dir", ctx)
        assertEquals(listOf("mv: target 'not_a_dir' is not a directory"), outMulti)

        // 2. 源文件不存在
        val outSrcNotExist = engine.executeStrings("mv no_such_file.txt target_dir", ctx)
        assertEquals(listOf("mv: cannot stat 'no_such_file.txt': No such file or directory"), outSrcNotExist)

        // 3. 目标为不存在的以斜杠结尾的路径
        val outSlashNotExist = engine.executeStrings("mv a.txt non_existent_folder/", ctx)
        assertEquals(listOf("mv: target 'non_existent_folder/' is not a directory"), outSlashNotExist)
    }

    @Test
    fun testMvRenameMutatesCacheInPlace() = runBlocking {
        val f1 = FileBean(name = "old_name.txt", fileId = "101", isFolder = false)
        val mockRepo = object : FileRepository() {
            override suspend fun rename(renameBean: RequestBody): BaseReturnMessage {
                return BaseReturnMessage(state = true)
            }
        }

        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo)

        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1), cid = "0", count = 1, order = "", path = emptyList()))

        engine.executeStrings("mv old_name.txt new_name.txt", ctx)

        assertTrue("重命名后缓存不应失效", ctx.fileCacheManager.containsKey("0"))
        assertEquals(1, ctx.fileCacheManager["0"]!!.fileBeanList.size)
        assertEquals("new_name.txt", ctx.fileCacheManager["0"]!!.fileBeanList[0].name)
        assertEquals("101", ctx.fileCacheManager["0"]!!.fileBeanList[0].fileId)
    }

    @Test
    fun testMvMoveMutatesCacheInPlace() = runBlocking {
        val f1 = FileBean(name = "doc.txt", fileId = "101", isFolder = false)
        val targetDir = FileBean(name = "targetDir", categoryId = "500", isFolder = true)

        val mockRepo = object : FileRepository() {
            override suspend fun move(body: Map<String, String>): BaseReturnMessage {
                return BaseReturnMessage(state = true)
            }
        }

        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo)

        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, targetDir), cid = "0", count = 2, order = "", path = emptyList()))
        ctx.fileCacheManager.put("500", FilesBean(fileBeanList = arrayListOf(), cid = "500", count = 0, order = "", path = emptyList()))

        engine.executeStrings("mv doc.txt targetDir/", ctx)

        // 源目录就地移除
        assertTrue("源目录缓存不应整体失效", ctx.fileCacheManager.containsKey("0"))
        assertEquals(1, ctx.fileCacheManager["0"]!!.fileBeanList.size)
        assertEquals("targetDir", ctx.fileCacheManager["0"]!!.fileBeanList[0].name)
        assertEquals(1, ctx.fileCacheManager["0"]!!.count)

        // 目标目录就地追加
        assertTrue("目标目录缓存不应整体失效", ctx.fileCacheManager.containsKey("500"))
        assertEquals(1, ctx.fileCacheManager["500"]!!.fileBeanList.size)
        assertEquals("doc.txt", ctx.fileCacheManager["500"]!!.fileBeanList[0].name)
        assertEquals(1, ctx.fileCacheManager["500"]!!.count)
    }
}
