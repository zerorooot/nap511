package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * find 命令测试用例集合
 * 覆盖：指定目录查找、-type 类型选择、-size 尺寸过滤、-empty 空文件/空目录匹配及 -name / -suffix 模糊匹配
 */
class FindCommandTest {

    /**
     * 测试在特定指定目录下搜索与 -type d 目录类型筛选
     */
    @Test
    fun testFindInSpecificDirectory() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 模拟根目录结构：根目录("0") 包含 test("100") 和 2023("200")
        val testFolder = createMockFolder("test", "100")
        val dir2023Folder = createMockFolder("2023", "200")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(testFolder, dir2023Folder), cid = "0", count = 2, order = "", path = emptyList()))

        // 2023 包含 22("300")
        val dir22Folder = createMockFolder("22", "300")
        ctx.fileCacheManager.put("200", FilesBean(fileBeanList = arrayListOf(dir22Folder), cid = "200", count = 1, order = "", path = emptyList()))

        // 2023/22 目录下包含子文件夹 sub_dir("400") 和普通文件 file.txt
        val subDirFolder = createMockFolder("sub_dir", "400")
        val docFile = createMockFile("file.txt", "500")
        ctx.fileCacheManager.put("300", FilesBean(fileBeanList = arrayListOf(subDirFolder, docFile), cid = "300", count = 2, order = "", path = emptyList()))

        // 切换当前工作目录到 /test
        ctx.updateDirectory(listOf(PathBean("100", "test", "0")))

        // 在 /test 目录下执行 "find 2023/22 -type d" 相对路径检索
        val out = engine.executeStrings("find 2023/22 -type d", ctx)

        // 验证查找结果为目标路径下的子目录而非当前 /test 目录项
        assertEquals(1, out.size)
        assertTrue(out[0].contains("/2023/22/sub_dir/"))
    }

    /**
     * 测试 -empty 与 -size 大小过滤选项
     */
    @Test
    fun testFindEmptyAndSizeOptions() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val bigFile = createMockFile("big.mp4", "1", size = "209715200") // 200M
        val smallFile = createMockFile("small.txt", "2", size = "1024")  // 1k
        val emptyFile = createMockFile("empty_file.txt", "3", size = "0")
        val emptyFolder = createMockFolder("empty_folder", "10")
        val nonEmptyFolder = createMockFolder("non_empty_folder", "20")

        ctx.fileCacheManager.put("0", FilesBean(
            fileBeanList = arrayListOf(bigFile, smallFile, emptyFile, emptyFolder, nonEmptyFolder),
            cid = "0", count = 5, order = "", path = emptyList()
        ))
        ctx.fileCacheManager.put("10", FilesBean(
            fileBeanList = arrayListOf(), cid = "10", count = 0, order = "", path = emptyList()
        ))
        ctx.fileCacheManager.put("20", FilesBean(
            fileBeanList = arrayListOf(smallFile), cid = "20", count = 1, order = "", path = emptyList()
        ))

        // find -size +100M 查找大于 100M 的文件
        val outSizePlus = engine.executeStrings("find -size +100M", ctx)
        assertEquals(1, outSizePlus.size)
        assertTrue(outSizePlus[0].contains("big.mp4"))

        // find -type f -size -10k 查找小于 10k 的文件
        val outSizeMinus = engine.executeStrings("find -type f -size -10k", ctx)
        assertEquals(3, outSizeMinus.size)
        assertTrue(outSizeMinus.contains("/根目录/small.txt"))
        assertTrue(outSizeMinus.contains("/根目录/empty_file.txt"))
        assertTrue(outSizeMinus.contains("/根目录/non_empty_folder/small.txt"))

        // find -empty 查找空文件与空文件夹
        val outEmpty = engine.executeStrings("find -empty", ctx)
        assertEquals(2, outEmpty.size)
        assertTrue(outEmpty.any { it.contains("empty_file.txt") })
        assertTrue(outEmpty.any { it.contains("empty_folder/") })
    }

    /**
     * 测试 -name / -suffix 文件名与后缀过滤选项
     */
    @Test
    fun testFindFileAndSuffixOptions() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val docFile = createMockFile("report.pdf", "10", size = "500")
        val videoFile = createMockFile("movie.mp4", "20", size = "1000")
        ctx.fileCacheManager.put("0", FilesBean(
            fileBeanList = arrayListOf(docFile, videoFile), cid = "0", count = 2, order = "", path = emptyList()
        ))

        // find -name '*.pdf' 通配符过滤
        val outPdf = engine.executeStrings("find -name '*.pdf'", ctx)
        assertEquals(1, outPdf.size)
        assertTrue(outPdf[0].contains("report.pdf"))

        // find -suffix mp4 后缀过滤
        val outMp4 = engine.executeStrings("find -suffix mp4", ctx)
        assertEquals(1, outMp4.size)
        assertTrue(outMp4[0].contains("movie.mp4"))
    }
}
