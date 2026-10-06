package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
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
}
