package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.CreateFolderMessage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MkdirCommandTest {

    private fun createMockRepo(): FileRepository {
        return object : FileRepository() {
            var counter = 1000
            override suspend fun createFolder(pid: String, folderName: String): CreateFolderMessage {
                val newId = (counter++).toString()
                return CreateFolderMessage(state = true, cid = newId, fileId = newId, fileName = folderName)
            }
        }
    }

    @Test
    fun testMkdirValidation() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // M008: mkdir 缺失参数
        val mkdirEmpty = engine.executeStrings("mkdir", ctx)
        assertEquals(listOf("mkdir: missing operand"), mkdirEmpty)

        // M025: mkdir 无 -p 时若父路径不存在报错
        val mkdirNoParent = engine.executeStrings("mkdir non_existent_dir/new_sub", ctx)
        assertTrue(mkdirNoParent[0].contains("cannot create directory 'non_existent_dir/new_sub': No such file or directory"))
    }

    @Test
    fun testMkdirExistingDirectoryAndFlagP() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val existingFolder = FileBean(name = "docs", categoryId = "10", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(existingFolder), cid = "0", count = 1, order = "", path = emptyList()))

        // M004: 非 -p 模式创建已存在目录报错 File exists
        val outExist = engine.executeStrings("mkdir docs", ctx)
        assertEquals(listOf("mkdir: cannot create directory 'docs': File exists"), outExist)

        // M005: -p 模式创建已存在目录正常返回成功
        val outPExist = engine.executeStrings("mkdir -p docs", ctx)
        assertEquals(listOf("mkdir: created directory 'docs'"), outPExist)
    }

    @Test
    fun testMkdirBasicAndOptions() = runBlocking {
        val mockRepo = createMockRepo()
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo)
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(), cid = "0", count = 0, order = "", path = emptyList()))

        // M002: 新建单目录
        val outSingle = engine.executeStrings("mkdir dirA", ctx)
        assertEquals(listOf("mkdir: created directory 'dirA'"), outSingle)

        // M003: 新建多目录
        val outMulti = engine.executeStrings("mkdir dirB dirC", ctx)
        assertEquals(listOf("mkdir: created directory 'dirB'", "mkdir: created directory 'dirC'"), outMulti)

        // M006 / M026: -p 递归创建
        val outRec = engine.executeStrings("mkdir -p a/b/c", ctx)
        assertEquals(listOf("mkdir: created directory 'a/b/c'"), outRec)

        // M036: 重复选项 -p -p
        val outDupFlag = engine.executeStrings("mkdir -p -p dirE", ctx)
        assertEquals(listOf("mkdir: created directory 'dirE'"), outDupFlag)
    }

    @Test
    fun testMkdirPathAndEscaping() = runBlocking {
        val mockRepo = createMockRepo()
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo)
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(), cid = "0", count = 0, order = "", path = emptyList()))

        // M011: 含空格未转义 -> 创建两个目录
        val outMultiSpace = engine.executeStrings("mkdir my dir", ctx)
        assertEquals(listOf("mkdir: created directory 'my'", "mkdir: created directory 'dir'"), outMultiSpace)

        // M012: 含空格用双引号 -> 创建一个带空格的目录
        val outQuotedSpace = engine.executeStrings("mkdir \"my dir\"", ctx)
        assertEquals(listOf("mkdir: created directory 'my dir'"), outQuotedSpace)

        // M013: 含空格用反斜杠转义
        val outEscapedSpace = engine.executeStrings("mkdir my\\ dir2", ctx)
        assertEquals(listOf("mkdir: created directory 'my dir2'"), outEscapedSpace)

        // M014-M020: 包含单双引号、美元符号、惊叹号、井号、中文及 Emoji
        val outSpecial1 = engine.executeStrings("mkdir \"it's\"", ctx)
        assertEquals(listOf("mkdir: created directory 'it's'"), outSpecial1)

        val outSpecial2 = engine.executeStrings("mkdir 'price$1'", ctx)
        assertEquals(listOf("mkdir: created directory 'price$1'"), outSpecial2)

        val outChinese = engine.executeStrings("mkdir 测试目录", ctx)
        assertEquals(listOf("mkdir: created directory '测试目录'"), outChinese)

        val outEmoji = engine.executeStrings("mkdir '😀目录'", ctx)
        assertEquals(listOf("mkdir: created directory '😀目录'"), outEmoji)

        // M024: 绝对路径
        val outAbs = engine.executeStrings("mkdir /根目录/absdir", ctx)
        assertEquals(listOf("mkdir: created directory '/根目录/absdir'"), outAbs)

        // M029: 路径以 / 结尾
        val outSlash = engine.executeStrings("mkdir slashdir/", ctx)
        assertEquals(listOf("mkdir: created directory 'slashdir/'"), outSlash)
    }

    @Test
    fun testMkdirBoundariesAndFileConflicts() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val existingFile = FileBean(name = "f.txt", fileId = "100", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(existingFile), cid = "0", count = 1, order = "", path = emptyList()))

        // M031: 目标是已存在的文件
        val outConflict = engine.executeStrings("mkdir f.txt", ctx)
        assertEquals(listOf("mkdir: cannot create directory 'f.txt': File exists"), outConflict)

        // M040: 已存在文件加 -p
        val outConflictP = engine.executeStrings("mkdir -p f.txt", ctx)
        assertEquals(listOf("mkdir: cannot create directory 'f.txt': File exists"), outConflictP)
    }

    @Test
    fun testMkdirPipelineAndCombinations() = runBlocking {
        val mockRepo = createMockRepo()
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo)
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(), cid = "0", count = 0, order = "", path = emptyList()))

        // M010 / M041: 创建目录后使用 ls | grep 查看（目录项在 ls 输出中带斜杠）
        engine.executeStrings("mkdir pdir", ctx)
        val outGrep = engine.executeStrings("ls | grep pdir", ctx)
        assertEquals(listOf("pdir/"), outGrep)

        // M048: mkdir 输出经由管道传输
        val outPipeHead = engine.executeStrings("mkdir pdir2 | head -n 1", ctx)
        assertEquals(listOf("mkdir: created directory 'pdir2'"), outPipeHead)

        // M050: 批量创建目录后通过 ls 验证
        engine.executeStrings("mkdir q1 q2 q3", ctx)
        val outLs = engine.executeStrings("ls", ctx)
        assertTrue(outLs.contains("q1/"))
        assertTrue(outLs.contains("q2/"))
        assertTrue(outLs.contains("q3/"))
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
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

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
