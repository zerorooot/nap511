package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * mv 命令测试用例集合
 * 覆盖：移动/重命名校验、特殊字符与中文 Emoji 路径移动、管道与重定向交互
 */
class MvCommandTest {

    /**
     * 测试缺失参数校验
     */
    @Test
    fun testMvMissingOperandValidation() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // mv 缺少目标文件/目录参数报错
        val out = engine.executeStrings("mv ../", ctx)
        assertEquals(listOf("mv: missing file operand"), out)
    }

    /**
     * 测试非法目标目录与不存在源文件操作报错
     */
    @Test
    fun testMvOperationsAndErrors() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val file1 = createMockFile("a.txt", "1")
        val file2 = createMockFile("b.txt", "2")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(file1, file2), cid = "0", count = 2, order = "", path = emptyList()))

        // 存在多个源文件且目标路径非目录时报错
        val outMulti = engine.executeStrings("mv a.txt b.txt not_a_dir", ctx)
        assertEquals(listOf("mv: target 'not_a_dir' is not a directory"), outMulti)

        // 移动不存在的源文件报错
        val outSrcNotExist = engine.executeStrings("mv no_such_file.txt target_dir", ctx)
        assertEquals(listOf("mv: cannot stat 'no_such_file.txt': No such file or directory"), outSrcNotExist)

        // 移动到不存在且以斜杠结尾的路径报错
        val outSlashNotExist = engine.executeStrings("mv a.txt non_existent_folder/", ctx)
        assertEquals(listOf("mv: target 'non_existent_folder/' is not a directory"), outSlashNotExist)
    }

    /**
     * 测试路径转义、中文及 Emoji 文件名移动
     */
    @Test
    fun testMvPathAndEscaping() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val file1 = createMockFile("my file.txt", "1")
        val file2 = createMockFile("测试.txt", "2")
        val file3 = createMockFile("😀.png", "3")
        val dirA = createMockFolder("dirA", "100")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(file1, file2, file3, dirA), cid = "0", count = 4, order = "", path = emptyList()))
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(), cid = "100", count = 0, order = "", path = emptyList()))

        // 源文件名包含空格且使用双引号包裹
        val outQuoted = engine.executeStrings("mv \"my file.txt\" dirA/", ctx)
        assertEquals(listOf("mv: 'my file.txt' -> 'dirA/'"), outQuoted)

        // 中文文件名移动
        val outChinese = engine.executeStrings("mv 测试.txt dirA/", ctx)
        assertEquals(listOf("mv: '测试.txt' -> 'dirA/'"), outChinese)

        // Emoji 表情文件名移动
        val outEmoji = engine.executeStrings("mv '😀.png' dirA/", ctx)
        assertEquals(listOf("mv: '😀.png' -> 'dirA/'"), outEmoji)

        // 使用绝对路径移动
        val file4 = createMockFile("abs.txt", "4")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(file4, dirA), cid = "0", count = 2, order = "", path = emptyList()))
        val outAbs = engine.executeStrings("mv /根目录/abs.txt /根目录/dirA/", ctx)
        assertEquals(listOf("mv: '/根目录/abs.txt' -> '/根目录/dirA/'"), outAbs)
    }

    /**
     * 测试移动文件夹（如 mv a/ b/）：
     * 1. 在源父目录缓存中删除 a 文件夹条目
     * 2. 级联递归删除 a 文件夹自身及所有子孙文件夹的缓存
     * 3. 强制网络刷新目标目录 b 的缓存
     */
    @Test
    fun testMvFolderRemovesSourceCacheRecursivelyAndRefreshesDestination() = runBlocking {
        val folderA = createMockFolder("a", "100")
        val folderB = createMockFolder("b", "200")
        val subFolder = createMockFolder("sub_a", "101")
        val subFile = createMockFile("file_in_a.txt", "102")

        val mockRepo = object : TestMockFileRepository() {
            override suspend fun getFiles(
                cid: String,
                showDir: Int,
                aid: Int,
                asc: Int,
                naturalSort: Int,
                order: String,
                limit: Int,
                format: String
            ): FilesBean {
                if (cid == "200") {
                    return FilesBean(
                        fileBeanList = arrayListOf(folderA),
                        cid = "200",
                        count = 1,
                        order = "",
                        path = listOf(
                            PathBean(cid = "0", name = "根目录", pid = "0"),
                            PathBean(cid = "200", name = "b", pid = "0")
                        )
                    )
                }
                return super.getFiles(cid, showDir, aid, asc, naturalSort, order, limit, format)
            }
        }

        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        // 初始化缓存：根目录有 a 和 b，a 目录下有子目录 101 和文件 102
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(folderA, folderB), cid = "0", count = 2, order = "", path = emptyList()))
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(subFolder, subFile), cid = "100", count = 2, order = "", path = emptyList()))
        ctx.fileCacheManager.put("101", FilesBean(fileBeanList = arrayListOf(), cid = "101", count = 0, order = "", path = emptyList()))
        ctx.fileCacheManager.put("200", FilesBean(fileBeanList = arrayListOf(), cid = "200", count = 0, order = "", path = emptyList()))

        org.junit.Assert.assertTrue(ctx.fileCacheManager.containsKey("100"))
        org.junit.Assert.assertTrue(ctx.fileCacheManager.containsKey("101"))
        assertEquals(2, ctx.fileCacheManager.getDate("0")?.fileBeanList?.size)
        assertEquals(0, ctx.fileCacheManager.getDate("200")?.fileBeanList?.size)

        // 执行 mv a/ b/
        val out = engine.executeStrings("mv a/ b/", ctx)
        assertEquals(listOf("mv: 'a/' -> 'b/'"), out)

        // 1. 验证 a 的源父目录（0）已删除 a 文件夹条目
        val rootFiles = ctx.fileCacheManager.getDate("0")?.fileBeanList
        assertEquals(1, rootFiles?.size)
        assertEquals("b", rootFiles?.first()?.name)

        // 2. 验证 a 文件夹自身（100）及子文件夹（101）缓存已被递归清理
        org.junit.Assert.assertFalse(ctx.fileCacheManager.containsKey("100"))
        org.junit.Assert.assertFalse(ctx.fileCacheManager.containsKey("101"))

        // 3. 验证 b 目录（200）已从服务端强制刷新，包含最新数据
        val bFiles = ctx.fileCacheManager.getDate("200")?.fileBeanList
        assertEquals(1, bFiles?.size)
        assertEquals("a", bFiles?.first()?.name)
    }
}
