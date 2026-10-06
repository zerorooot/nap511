package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
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
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // V009 / V010: mv 缺少目标参数
        val out = engine.executeStrings("mv ../", ctx)
        assertEquals(listOf("mv: missing file operand"), out)
    }

    @Test
    fun testMvOperationsAndErrors() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val file1 = FileBean(name = "a.txt", fileId = "1", isFolder = false)
        val file2 = FileBean(name = "b.txt", fileId = "2", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(file1, file2), cid = "0", count = 2, order = "", path = emptyList()))

        // V005 / V039: 多个源且目标不是目录
        val outMulti = engine.executeStrings("mv a.txt b.txt not_a_dir", ctx)
        assertEquals(listOf("mv: target 'not_a_dir' is not a directory"), outMulti)

        // V031: 源文件不存在
        val outSrcNotExist = engine.executeStrings("mv no_such_file.txt target_dir", ctx)
        assertEquals(listOf("mv: cannot stat 'no_such_file.txt': No such file or directory"), outSrcNotExist)

        // V003 / V028: 目标为不存在的以斜杠结尾的路径
        val outSlashNotExist = engine.executeStrings("mv a.txt non_existent_folder/", ctx)
        assertEquals(listOf("mv: target 'non_existent_folder/' is not a directory"), outSlashNotExist)
    }

    @Test
    fun testMvPathAndEscaping() = runBlocking {
        val mockRepo = object : FileRepository() {
            override suspend fun move(body: Map<String, String>): BaseReturnMessage {
                return BaseReturnMessage(state = true)
            }
        }
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo)
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val file1 = FileBean(name = "my file.txt", fileId = "1", isFolder = false)
        val file2 = FileBean(name = "测试.txt", fileId = "2", isFolder = false)
        val file3 = FileBean(name = "😀.png", fileId = "3", isFolder = false)
        val dirA = FileBean(name = "dirA", categoryId = "100", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(file1, file2, file3, dirA), cid = "0", count = 4, order = "", path = emptyList()))
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(), cid = "100", count = 0, order = "", path = emptyList()))

        // V012: 源文件名称含空格并带有双引号
        val outQuoted = engine.executeStrings("mv \"my file.txt\" dirA/", ctx)
        assertEquals(listOf("mv: 'my file.txt' -> 'dirA/'"), outQuoted)

        // V020: 中文文件名移动
        val outChinese = engine.executeStrings("mv 测试.txt dirA/", ctx)
        assertEquals(listOf("mv: '测试.txt' -> 'dirA/'"), outChinese)

        // V021: Emoji 文件名移动
        val outEmoji = engine.executeStrings("mv '😀.png' dirA/", ctx)
        assertEquals(listOf("mv: '😀.png' -> 'dirA/'"), outEmoji)

        // V025: 绝对路径移动
        val file4 = FileBean(name = "abs.txt", fileId = "4", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(file4, dirA), cid = "0", count = 2, order = "", path = emptyList()))
        val outAbs = engine.executeStrings("mv /根目录/abs.txt /根目录/dirA/", ctx)
        assertEquals(listOf("mv: '/根目录/abs.txt' -> '/根目录/dirA/'"), outAbs)
    }

    @Test
    fun testMvPipelineAndStdin() = runBlocking {
        val mockRepo = object : FileRepository() {
            override suspend fun move(body: Map<String, String>): BaseReturnMessage {
                return BaseReturnMessage(state = true)
            }
        }
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo)
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val file1 = FileBean(name = "item1.txt", fileId = "1", isFolder = false)
        val targetDir = FileBean(name = "targetDir", categoryId = "200", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(file1, targetDir), cid = "0", count = 2, order = "", path = emptyList()))
        ctx.fileCacheManager.put("200", FilesBean(fileBeanList = arrayListOf(), cid = "200", count = 0, order = "", path = emptyList()))

        // V049 / V050: 通过管道传递源文件列表至 mv
        val outPipe = engine.executeStrings("echo 'item1.txt' | mv targetDir", ctx)
        assertEquals(listOf("mv: 'item1.txt' -> 'targetDir/'"), outPipe)

        // 验证移动后查询结果
        val outLs = engine.executeStrings("ls targetDir", ctx)
        assertEquals(listOf("item1.txt"), outLs)
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
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1), cid = "0", count = 1, order = "", path = emptyList()))

        // V002 / V006: 重命名文件
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
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, targetDir), cid = "0", count = 2, order = "", path = emptyList()))
        ctx.fileCacheManager.put("500", FilesBean(fileBeanList = arrayListOf(), cid = "500", count = 0, order = "", path = emptyList()))

        // V003 / V008: 移动文件至目标目录
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
