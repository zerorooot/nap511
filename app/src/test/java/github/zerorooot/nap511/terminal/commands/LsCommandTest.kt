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

    /**
     * 测试多文件参数（含空格脱敏文件名，格式与真实字段一致）全部正确展示
     */
    @Test
    fun testLsMultipleFilesWithSpaces() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 模拟当前工作目录下存在文件夹 "smll" (cid=500)
        val smllFolder = createMockFolder("smll", "500")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(
                fileBeanList = arrayListOf(smllFolder),
                cid = "0",
                count = 1,
                order = "",
                path = listOf(PathBean("0", "根目录", "0"))
            )
        )

        // 模拟 smll 目录下存在 3 个带空格的压缩包文件（脱敏格式：album photo xxx.zip）
        val f1 = createMockFile("album photo pisces.zip", "501", size = "1024", modifiedTime = "1000")
        val f2 = createMockFile("album photo christmas.zip", "502", size = "2048", modifiedTime = "2000")
        val f3 = createMockFile("album photo kimono.zip", "503", size = "4096", modifiedTime = "3000")
        ctx.fileCacheManager.put(
            "500",
            FilesBean(
                fileBeanList = arrayListOf(f1, f2, f3),
                cid = "500",
                count = 3,
                order = "",
                path = listOf(PathBean("0", "根目录", "0"), PathBean("500", "smll", "0"))
            )
        )

        // 1. 测试直接指定多个文件路径：全部 3 个文件都必须被列出
        val outMulti = engine.executeStrings(
            "ls \"smll/album photo pisces.zip\" \"smll/album photo christmas.zip\" \"smll/album photo kimono.zip\"",
            ctx
        )
        assertEquals(3, outMulti.size)
        assertTrue(outMulti.contains("smll/album photo pisces.zip"))
        assertTrue(outMulti.contains("smll/album photo christmas.zip"))
        assertTrue(outMulti.contains("smll/album photo kimono.zip"))

        // 2. 测试通过通配符展开（ls smll/*.zip）：全部 3 个文件必须完整展示（验证用户现场缺陷彻底修复）
        val outGlob = engine.executeStrings("ls smll/*.zip", ctx)
        assertEquals(3, outGlob.size)
        assertEquals(
            listOf(
                "smll/album photo christmas.zip",
                "smll/album photo kimono.zip",
                "smll/album photo pisces.zip"
            ),
            outGlob
        )

        // 3. 测试长列表模式（ls -l smll/*.zip）：显式展示多文件时，不输出 total 标头，且输出 3 行长列表
        val outLong = engine.executeStrings("ls -l smll/*.zip", ctx)
        assertEquals(3, outLong.size)
        assertTrue(outLong.none { it.startsWith("total") })
        assertTrue(outLong.any { it.contains("smll/album photo pisces.zip") })
        assertTrue(outLong.any { it.contains("smll/album photo christmas.zip") })
        assertTrue(outLong.any { it.contains("smll/album photo kimono.zip") })
    }

    /**
     * 测试多目录参数独立分块展示（带 "dir:" 标头与空行分隔）
     */
    @Test
    fun testLsMultipleDirectories_ShowsHeaders() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val dirA = createMockFolder("dir_alpha", "601")
        val dirB = createMockFolder("dir_beta", "602")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(
                fileBeanList = arrayListOf(dirA, dirB),
                cid = "0",
                count = 2,
                order = "",
                path = listOf(PathBean("0", "根目录", "0"))
            )
        )

        val fileInA = createMockFile("alpha item.txt", "611")
        ctx.fileCacheManager.put(
            "601",
            FilesBean(
                fileBeanList = arrayListOf(fileInA),
                cid = "601",
                count = 1,
                order = "",
                path = listOf(PathBean("0", "根目录", "0"), PathBean("601", "dir_alpha", "0"))
            )
        )

        val fileInB = createMockFile("beta item.txt", "621")
        ctx.fileCacheManager.put(
            "602",
            FilesBean(
                fileBeanList = arrayListOf(fileInB),
                cid = "602",
                count = 1,
                order = "",
                path = listOf(PathBean("0", "根目录", "0"), PathBean("602", "dir_beta", "0"))
            )
        )

        val out = engine.executeStrings("ls dir_alpha dir_beta", ctx)
        assertTrue(out.contains("dir_alpha:"))
        assertTrue(out.contains("alpha item.txt"))
        assertTrue(out.contains("dir_beta:"))
        assertTrue(out.contains("beta item.txt"))
    }

    /**
     * 测试文件与目录混合参数，以及包含不存在路径时的容错输出
     */
    @Test
    fun testLsMixedAndNonExistentTargets() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val folder = createMockFolder("my_folder", "701")
        val file = createMockFile("standalone doc.txt", "702")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(
                fileBeanList = arrayListOf(folder, file),
                cid = "0",
                count = 2,
                order = "",
                path = listOf(PathBean("0", "根目录", "0"))
            )
        )

        val subFile = createMockFile("inside sub.txt", "711")
        ctx.fileCacheManager.put(
            "701",
            FilesBean(
                fileBeanList = arrayListOf(subFile),
                cid = "701",
                count = 1,
                order = "",
                path = listOf(PathBean("0", "根目录", "0"), PathBean("701", "my_folder", "0"))
            )
        )

        // 包含不存在项、文件项、目录项混合执行
        val out = engine.executeStrings("ls nonexistent_file.txt \"standalone doc.txt\" my_folder", ctx)

        // 1. 不存在项输出错误提示，不阻断后续合法项
        assertTrue(out.any { it.contains("cannot access 'nonexistent_file.txt': No such file or directory") })
        // 2. 普通文件被优先输出
        assertTrue(out.contains("standalone doc.txt"))
        // 3. 目录项随后以分块形式输出
        assertTrue(out.contains("my_folder:"))
        assertTrue(out.contains("inside sub.txt"))
    }
}
