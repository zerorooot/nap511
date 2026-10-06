package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ls 命令测试用例集合
 * 覆盖：特定路径与含空格转义、排序选项（-S 大小, -t 时间, -r 逆序, -a 隐藏文件）及错误列举
 */
class LsCommandTest {

    /**
     * 测试指定带空格路径及列表格式输出
     */
    @Test
    fun testLsSpecificFileWithSpace() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 模拟当前根目录存在文件夹 "SampleFolder" (cid=123)
        val folder = createMockFolder("SampleFolder", "123")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(folder), cid = "0", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"))))

        // 模拟子目录下存在带空格的文件 "Sample Document.txt"
        val file = createMockFile("Sample Document.txt", "999")
        ctx.fileCacheManager.put("123", FilesBean(fileBeanList = arrayListOf(file), cid = "123", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("123", "SampleFolder", "0"))))

        // 执行 ls -l SampleFolder/Sample\ Document.txt 查看长列表格式
        val out = engine.executeStrings("ls -l SampleFolder/Sample\\ Document.txt", ctx)
        assertEquals(1, out.size)
        assertTrue(out[0].contains("SampleFolder/Sample Document.txt"))

        // 执行普通 ls SampleFolder/Sample\ Document.txt
        val outShort = engine.executeStrings("ls SampleFolder/Sample\\ Document.txt", ctx)
        assertEquals(listOf("SampleFolder/Sample Document.txt"), outShort)
    }

    /**
     * 测试排序与路径匹配选项 (-a, -S, -t, -r)
     */
    @Test
    fun testLsSortingAndPathOptions() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val f1 = createMockFile("file_c.txt", "1", size = "300", modifiedTime = "1000")
        val f2 = createMockFile("file_a.txt", "2", size = "100", modifiedTime = "3000")
        val f3 = createMockFile(".hidden", "3", size = "50", modifiedTime = "2000")
        val f4 = createMockFile("file_b.txt", "4", size = "200", modifiedTime = "4000")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, f2, f3, f4), cid = "0", count = 4, order = "", path = listOf(PathBean("0", "根目录", "0"))))

        // 默认 ls 不显示隐藏文件
        val outDefault = engine.executeStrings("ls", ctx)
        assertEquals(listOf("file_c.txt", "file_a.txt", "file_b.txt"), outDefault)

        // ls -a 显示全部文件（包含隐藏文件）
        val outAll = engine.executeStrings("ls -a", ctx)
        assertTrue(outAll.contains(".hidden"))
        assertEquals(4, outAll.size)

        // ls -S 按文件大小降序排序
        val outSize = engine.executeStrings("ls -S", ctx)
        assertEquals(listOf("file_c.txt", "file_b.txt", "file_a.txt"), outSize)

        // ls -t 按修改时间降序排序 (4000 -> file_b, 3000 -> file_a, 1000 -> file_c)
        val outTime = engine.executeStrings("ls -t", ctx)
        assertEquals(listOf("file_b.txt", "file_a.txt", "file_c.txt"), outTime)

        // ls -r 反向逆序排序
        val outReverse = engine.executeStrings("ls -S -r", ctx)
        assertEquals(listOf("file_a.txt", "file_b.txt", "file_c.txt"), outReverse)

        // ls 访问不存在的路径报错处理
        val outNotFound = engine.executeStrings("ls non_existent_dir", ctx)
        assertTrue(outNotFound[0].contains("cannot access 'non_existent_dir': No such file or directory"))
    }
}
