package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.CreateFolderMessage
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.repository.FileRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * mkdir 命令测试用例集合
 * 覆盖：目录创建校验与 -p 递归选项、路径转义与特殊字符处理、同名冲突及本地缓存即时更新
 */
class MkdirCommandTest {

    /**
     * 测试缺失参数与缺失父目录校验
     */
    @Test
    fun testMkdirValidation() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // mkdir 缺失参数报错处理
        val mkdirEmpty = engine.executeStrings("mkdir", ctx)
        assertEquals(listOf("mkdir: missing operand"), mkdirEmpty)

        // mkdir 无 -p 选项且父路径不存在时报错处理
        val mkdirNoParent = engine.executeStrings("mkdir non_existent_dir/new_sub", ctx)
        assertTrue(mkdirNoParent[0].contains("cannot create directory 'non_existent_dir/new_sub': No such file or directory"))
    }

    /**
     * 测试已存在目录处理与 -p 选项模式
     */
    @Test
    fun testMkdirExistingDirectoryAndFlagP() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val existingFolder = createMockFolder("docs", "10")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(existingFolder), cid = "0", count = 1, order = "", path = emptyList()))

        // 非 -p 模式下创建已存在目录报错 File exists
        val outExist = engine.executeStrings("mkdir docs", ctx)
        assertEquals(listOf("mkdir: cannot create directory 'docs': File exists"), outExist)

        // -p 模式下创建已存在目录正常返回成功
        val outPExist = engine.executeStrings("mkdir -p docs", ctx)
        assertEquals(listOf("mkdir: created directory 'docs'"), outPExist)
    }

    /**
     * 测试基本目录创建与 -p 递归选项
     */
    @Test
    fun testMkdirBasicAndOptions() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(), cid = "0", count = 0, order = "", path = emptyList()))

        // 新建单个目录
        val outSingle = engine.executeStrings("mkdir dirA", ctx)
        assertEquals(listOf("mkdir: created directory 'dirA'"), outSingle)

        // 批量新建多个目录
        val outMulti = engine.executeStrings("mkdir dirB dirC", ctx)
        assertEquals(listOf("mkdir: created directory 'dirB'", "mkdir: created directory 'dirC'"), outMulti)

        // -p 选项递归创建深层目录
        val outRec = engine.executeStrings("mkdir -p a/b/c", ctx)
        assertEquals(listOf("mkdir: created directory 'a/b/c'"), outRec)

        // 重复 -p 选项容错处理
        val outDupFlag = engine.executeStrings("mkdir -p -p dirE", ctx)
        assertEquals(listOf("mkdir: created directory 'dirE'"), outDupFlag)
    }

    /**
     * 测试路径转义与特殊字符文件名创建
     */
    @Test
    fun testMkdirPathAndEscaping() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(), cid = "0", count = 0, order = "", path = emptyList()))

        // 路径包含空格未转义拆分为多个目录创建
        val outMultiSpace = engine.executeStrings("mkdir my dir", ctx)
        assertEquals(listOf("mkdir: created directory 'my'", "mkdir: created directory 'dir'"), outMultiSpace)

        // 路径包含空格使用双引号包裹
        val outQuotedSpace = engine.executeStrings("mkdir \"my dir\"", ctx)
        assertEquals(listOf("mkdir: created directory 'my dir'"), outQuotedSpace)

        // 路径包含空格使用反斜杠转义
        val outEscapedSpace = engine.executeStrings("mkdir my\\ dir2", ctx)
        assertEquals(listOf("mkdir: created directory 'my dir2'"), outEscapedSpace)

        // 路径包含单双引号、美元符号、中文及 Emoji
        val outSpecial1 = engine.executeStrings("mkdir \"it's\"", ctx)
        assertEquals(listOf("mkdir: created directory 'it's'"), outSpecial1)

        val outSpecial2 = engine.executeStrings("mkdir 'price$1'", ctx)
        assertEquals(listOf("mkdir: created directory 'price$1'"), outSpecial2)

        val outChinese = engine.executeStrings("mkdir 测试目录", ctx)
        assertEquals(listOf("mkdir: created directory '测试目录'"), outChinese)

        val outEmoji = engine.executeStrings("mkdir '😀目录'", ctx)
        assertEquals(listOf("mkdir: created directory '😀目录'"), outEmoji)

        // 使用绝对路径创建目录
        val outAbs = engine.executeStrings("mkdir /根目录/absdir", ctx)
        assertEquals(listOf("mkdir: created directory '/根目录/absdir'"), outAbs)

        // 目标路径以 / 结尾
        val outSlash = engine.executeStrings("mkdir slashdir/", ctx)
        assertEquals(listOf("mkdir: created directory 'slashdir/'"), outSlash)
    }

    /**
     * 测试边界冲突与同名文件阻退
     */
    @Test
    fun testMkdirBoundariesAndFileConflicts() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val existingFile = createMockFile("f.txt", "100")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(existingFile), cid = "0", count = 1, order = "", path = emptyList()))

        // 目标路径已存在同名文件报错
        val outConflict = engine.executeStrings("mkdir f.txt", ctx)
        assertEquals(listOf("mkdir: cannot create directory 'f.txt': File exists"), outConflict)

        // 已存在同名文件加 -p 选项仍报错
        val outConflictP = engine.executeStrings("mkdir -p f.txt", ctx)
        assertEquals(listOf("mkdir: cannot create directory 'f.txt': File exists"), outConflictP)
    }

    /**
     * 测试管道组合与下游验证
     */
    @Test
    fun testMkdirPipelineAndCombinations() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(), cid = "0", count = 0, order = "", path = emptyList()))

        // 创建目录后使用 ls | grep 查看校验（目录项在 ls 输出中带斜杠）
        engine.executeStrings("mkdir pdir", ctx)
        val outGrep = engine.executeStrings("ls | grep pdir", ctx)
        assertEquals(listOf("pdir/"), outGrep)

        // mkdir 提示信息经由管道输出传输
        val outPipeHead = engine.executeStrings("mkdir pdir2 | head -n 1", ctx)
        assertEquals(listOf("mkdir: created directory 'pdir2'"), outPipeHead)

        // 批量创建目录后通过 ls 查看验证
        engine.executeStrings("mkdir q1 q2 q3", ctx)
        val outLs = engine.executeStrings("ls", ctx)
        assertTrue(outLs.contains("q1/"))
        assertTrue(outLs.contains("q2/"))
        assertTrue(outLs.contains("q3/"))
    }

    /**
     * 测试创建目录后本地内存缓存即时拉新修改
     */
    @Test
    fun testMkdirMutatesCacheInPlace() = runBlocking {
        val mockRepo = object : FileRepository() {
            override suspend fun createFolder(pid: String, folderName: String): CreateFolderMessage {
                return CreateFolderMessage(state = true, cid = "888", fileId = "888", fileName = folderName)
            }
        }

        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

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
