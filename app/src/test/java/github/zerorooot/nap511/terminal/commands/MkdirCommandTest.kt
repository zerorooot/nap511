package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.CreateFolderMessage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MkdirCommandTest {

    @Test
    fun testMkdirValidation() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 1. mkdir 缺失参数
        val mkdirEmpty = engine.executeStrings("mkdir", ctx)
        assertEquals(listOf("mkdir: missing operand"), mkdirEmpty)

        // 2. mkdir 无 -p 时若父路径不存在报错
        val mkdirNoParent = engine.executeStrings("mkdir non_existent_dir/new_sub", ctx)
        assertTrue(mkdirNoParent[0].contains("cannot create directory 'non_existent_dir/new_sub': No such file or directory"))
    }

    @Test
    fun testMkdirExistingDirectoryAndFlagP() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val existingFolder = FileBean(name = "docs", categoryId = "10", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(existingFolder), cid = "0", count = 1, order = "", path = emptyList()))

        // 1. 非 -p 模式创建已存在目录报错 File exists
        val outExist = engine.executeStrings("mkdir docs", ctx)
        assertEquals(listOf("mkdir: cannot create directory 'docs': File exists"), outExist)

        // 2. -p 模式创建已存在目录正常返回成功
        val outPExist = engine.executeStrings("mkdir -p docs", ctx)
        assertEquals(listOf("mkdir: created directory 'docs'"), outPExist)
    }

    @Test
    fun testMkdirMutatesCacheInPlace() = runBlocking {
        val mockRepo = object : FileRepository() {
            override suspend fun createFolder(pid: String, folderName: String): CreateFolderMessage {
                return CreateFolderMessage(state = true, cid = "888", fileId = "888", fileName = folderName)
            }
        }

        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo)

        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(), cid = "0", count = 0, order = "", path = emptyList()))

        engine.executeStrings("mkdir created_folder", ctx)

        assertTrue("新建文件夹后父目录缓存不应整体失效", ctx.fileCacheManager.containsKey("0"))
        assertEquals(1, ctx.fileCacheManager["0"]!!.fileBeanList.size)
        val addedFolder = ctx.fileCacheManager["0"]!!.fileBeanList[0]
        assertEquals("created_folder", addedFolder.name)
        assertEquals("888", addedFolder.categoryId)
        assertTrue(addedFolder.isFolder)
        assertEquals(1, ctx.fileCacheManager["0"]!!.count)
    }
}
