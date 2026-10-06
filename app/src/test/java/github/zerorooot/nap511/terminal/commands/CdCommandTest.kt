package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CdCommandTest {

    @Test
    fun testCdParentDirectory() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 模拟 根目录("0") -> t1("100") -> t2("200")
        val t1Folder = FileBean(name = "t1", categoryId = "100", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(t1Folder), cid = "0", count = 1, order = "", path = emptyList()))

        val t2Folder = FileBean(name = "t2", categoryId = "200", isFolder = true)
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(t2Folder), cid = "100", count = 1, order = "", path = emptyList()))
        ctx.fileCacheManager.put("200", FilesBean(fileBeanList = arrayListOf(), cid = "200", count = 0, order = "", path = emptyList()))

        // 1. 从根目录进入 t1/t2
        engine.execute("cd t1/t2", ctx).toList()
        assertEquals("200", ctx.currentCid)
        assertEquals("/根目录/t1/t2", ctx.currentPath)

        // 2. cd .. 应返回上级目录 /根目录/t1 (CID 100)，而非上两级 /根目录
        engine.execute("cd ..", ctx).toList()
        assertEquals("100", ctx.currentCid)
        assertEquals("/根目录/t1", ctx.currentPath)

        // 3. 再次 cd .. 应返回 /根目录 (CID 0)
        engine.execute("cd ..", ctx).toList()
        assertEquals("0", ctx.currentCid)
        assertEquals("/根目录", ctx.currentPath)

        // 4. 根目录下 cd .. 仍保留在根目录
        engine.execute("cd ..", ctx).toList()
        assertEquals("0", ctx.currentCid)
        assertEquals("/根目录", ctx.currentPath)
    }

    @Test
    fun testCdVariationsAndErrors() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val folder = FileBean(name = "docs", categoryId = "10", isFolder = true)
        val file = FileBean(name = "note.txt", fileId = "20", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(folder, file), cid = "0", count = 2, order = "", path = emptyList()))
        ctx.fileCacheManager.put("10", FilesBean(fileBeanList = arrayListOf(), cid = "10", count = 0, order = "", path = emptyList()))

        // 1. cd 到子目录（带斜杠）
        engine.execute("cd docs/", ctx).toList()
        assertEquals("10", ctx.currentCid)
        assertEquals("/根目录/docs", ctx.currentPath)

        // 2. cd 无参数默认切回根目录
        engine.execute("cd", ctx).toList()
        assertEquals("0", ctx.currentCid)
        assertEquals("/根目录", ctx.currentPath)

        // 3. cd ~ 切回根目录
        engine.execute("cd docs", ctx).toList()
        engine.execute("cd ~", ctx).toList()
        assertEquals("0", ctx.currentCid)

        // 4. cd 到普通文件报错
        val outCdFile = engine.executeStrings("cd note.txt", ctx)
        assertTrue(outCdFile[0].contains("no such file or directory: note.txt"))

        // 5. cd 到不存在的目录报错
        val outCdNotExist = engine.executeStrings("cd not_exist_dir", ctx)
        assertTrue(outCdNotExist[0].contains("no such file or directory: not_exist_dir"))
    }
}
