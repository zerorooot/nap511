package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LsCommandTest {

    @Test
    fun testLsSpecificFileWithSpace() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 模拟当前根目录存在文件夹 "SampleFolder" (cid=123)
        val folder = FileBean(
            name = "SampleFolder",
            categoryId = "123",
            isFolder = true
        )
        val rootFiles = FilesBean(
            fileBeanList = arrayListOf(folder),
            cid = "0",
            count = 1,
            order = "",
            path = emptyList()
        )
        ctx.fileCacheManager.put("0", rootFiles)

        // 模拟子目录下存在带空格的文件 "Sample Document.txt"
        val file = FileBean(
            name = "Sample Document.txt",
            fileId = "999",
            isFolder = false,
            size = "1024"
        )
        val subFiles = FilesBean(
            fileBeanList = arrayListOf(file),
            cid = "123",
            count = 1,
            order = "",
            path = emptyList()
        )
        ctx.fileCacheManager.put("123", subFiles)

        // 执行 ls -l SampleFolder/Sample\ Document.txt
        val out = engine.executeStrings("ls -l SampleFolder/Sample\\ Document.txt", ctx)
        assertEquals(1, out.size)
        assertTrue(out[0].contains("SampleFolder/Sample Document.txt"))

        // 执行普通 ls SampleFolder/Sample\ Document.txt
        val outShort = engine.executeStrings("ls SampleFolder/Sample\\ Document.txt", ctx)
        assertEquals(listOf("SampleFolder/Sample Document.txt"), outShort)
    }

    @Test
    fun testLsSortingAndPathOptions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val f1 = FileBean(name = "file_c.txt", size = "300", isFolder = false, modifiedTime = "1000")
        val f2 = FileBean(name = "file_a.txt", size = "100", isFolder = false, modifiedTime = "3000")
        val f3 = FileBean(name = ".hidden", size = "50", isFolder = false, modifiedTime = "2000")
        val f4 = FileBean(name = "file_b.txt", size = "200", isFolder = false, modifiedTime = "4000")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, f2, f3, f4), cid = "0", count = 4, order = "", path = emptyList()))

        // 默认 ls 不显示隐藏文件
        val outDefault = engine.executeStrings("ls", ctx)
        assertEquals(listOf("file_c.txt", "file_a.txt", "file_b.txt"), outDefault)

        // ls -a 显示全部文件
        val outAll = engine.executeStrings("ls -a", ctx)
        assertTrue(outAll.contains(".hidden"))
        assertEquals(4, outAll.size)

        // ls -S 按大小降序
        val outSize = engine.executeStrings("ls -S", ctx)
        assertEquals(listOf("file_c.txt", "file_b.txt", "file_a.txt"), outSize)

        // ls -t 按修改时间降序 (4000 -> file_b, 3000 -> file_a, 1000 -> file_c)
        val outTime = engine.executeStrings("ls -t", ctx)
        assertEquals(listOf("file_b.txt", "file_a.txt", "file_c.txt"), outTime)

        // ls -r 反向排序
        val outReverse = engine.executeStrings("ls -S -r", ctx)
        assertEquals(listOf("file_a.txt", "file_b.txt", "file_c.txt"), outReverse)

        // ls 访问不存在的路径
        val outNotFound = engine.executeStrings("ls non_existent_dir", ctx)
        assertTrue(outNotFound[0].contains("cannot access 'non_existent_dir': No such file or directory"))
    }
}
