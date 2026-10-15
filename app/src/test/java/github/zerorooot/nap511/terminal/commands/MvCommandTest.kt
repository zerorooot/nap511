package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    /**
     * 测试带子目录路径前缀的 Glob 通配符批量移动
     */
    @Test
    fun testMvSubdirectoryGlobExpansion() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()

        // 构造目录树：根目录(0) -> 工作目录 ta(50) -> 子目录 smll(60)
        val dirTa = createMockFolder("ta", "50")
        val dirSmll = createMockFolder("smll", "60").copy(parentId = "50")
        val zip1 = createMockFile("package_alpha.zip", "601", categoryId = "60")
        val zip2 = createMockFile("package_beta.zip", "602", categoryId = "60")

        val ctx = createTestContext(fileRepository = mockRepo)

        // ta 目录下包含子目录 smll
        ctx.fileCacheManager.put(
            "50",
            FilesBean(
                fileBeanList = arrayListOf(dirSmll),
                cid = "50",
                count = 1,
                order = "",
                path = listOf(PathBean("0", "根目录", "0"), PathBean("50", "ta", "0"))
            )
        )

        // smll 目录下包含 2 个 zip 文件
        ctx.fileCacheManager.put(
            "60",
            FilesBean(
                fileBeanList = arrayListOf(zip1, zip2),
                cid = "60",
                count = 2,
                order = "",
                path = listOf(PathBean("0", "根目录", "0"), PathBean("50", "ta", "0"), PathBean("60", "smll", "50"))
            )
        )

        // 根目录
        ctx.fileCacheManager.put(
            "0",
            FilesBean(
                fileBeanList = arrayListOf(dirTa),
                cid = "0",
                count = 1,
                order = "",
                path = listOf(PathBean("0", "根目录", "0"))
            )
        )

        // 在 /根目录/ta 下执行：mv smll/*.zip ../
        engine.executeStrings("cd ta", ctx)
        val out = engine.executeStrings("mv smll/*.zip ../", ctx)

        // 验证通配符成功展开并批量移入上级目录
        assertEquals(listOf("mv: 已成功移动 2 个文件/文件夹至 '根目录/'"), out)

        // 验证发起 1 次批量移动请求，pid 为根目录 0
        assertEquals(1, mockRepo.movedItems.size)
        val call = mockRepo.movedItems[0]
        assertEquals("0", call["pid"])
        assertEquals("601", call["fid[0]"])
        assertEquals("602", call["fid[1]"])
    }

    /**
     * 测试同一文件快速短路拦截 (mv a.txt a.txt 及 mv a.txt .)
     */
    @Test
    fun testMvSameFileShortCircuit() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val f = createMockFile("a.txt", "1")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f), cid = "0", count = 1, order = "", path = emptyList()))

        // 1. mv a.txt a.txt
        val outSame = engine.executeStrings("mv a.txt a.txt", ctx)
        assertEquals(listOf("mv: 'a.txt' and 'a.txt' are the same file"), outSame)

        // 2. mv a.txt .
        val outDot = engine.executeStrings("mv a.txt .", ctx)
        assertEquals(listOf("mv: 'a.txt' and '.' are the same file"), outDot)

        // 验证未发起任何移动或重命名请求
        assertTrue(mockRepo.movedItems.isEmpty())
        assertTrue(mockRepo.renamedItems.isEmpty())
    }

    /**
     * 测试特殊目录（. 与 ..）及工作目录祖先移动安全拦截
     */
    @Test
    fun testMvAncestorAndSpecialDirectoryProtection() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val folderA = createMockFolder("folderA", "10")
        val subFolder = createMockFolder("subFolder", "20")
        val destFolder = createMockFolder("dest", "30")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(folderA, destFolder), cid = "0", count = 2, order = "", path = emptyList()))
        ctx.fileCacheManager.put("10", FilesBean(fileBeanList = arrayListOf(subFolder), cid = "10", count = 1, order = "", path = emptyList()))
        ctx.fileCacheManager.put("20", FilesBean(fileBeanList = arrayListOf(), cid = "20", count = 0, order = "", path = emptyList()))
        ctx.fileCacheManager.put("30", FilesBean(fileBeanList = arrayListOf(), cid = "30", count = 0, order = "", path = emptyList()))

        // 切换到 /folderA/subFolder
        ctx.updateDirectory(listOf(
            PathBean("0", "根目录", "0"),
            PathBean("10", "folderA", "0"),
            PathBean("20", "subFolder", "10")
        ))

        // 1. 移动 . 与 .. 保护
        val outDot = engine.executeStrings("mv . /dest", ctx)
        assertTrue(outDot.any { it.contains("Cannot move '.' or '..' directory") })

        val outDotDot = engine.executeStrings("mv .. /dest", ctx)
        assertTrue(outDotDot.any { it.contains("Cannot move '.' or '..' directory") })

        // 2. 移动当前工作目录祖先
        val outAncestor = engine.executeStrings("mv /folderA /dest", ctx)
        assertTrue(outAncestor.any { it.contains("Cannot move ancestor of current working directory") })
    }

    /**
     * 测试跨目录移动并重命名（两阶段操作）及重命名失败时的反向事务回滚
     */
    @Test
    fun testMvCrossDirectoryMoveAndRenameSuccessAndRollback() = runBlocking {
        val engine = createTestEngine()

        // 1. 成功场景：跨目录移动并在目标目录下重命名
        val mockRepoSuccess = createTestMockRepository()
        val ctxSuccess = createTestContext(fileRepository = mockRepoSuccess)

        val fileSrc = createMockFile("old.txt", "101")
        val destDir = createMockFolder("target_dir", "200")
        ctxSuccess.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(fileSrc, destDir), cid = "0", count = 2, order = "", path = emptyList()))
        ctxSuccess.fileCacheManager.put("200", FilesBean(fileBeanList = arrayListOf(), cid = "200", count = 0, order = "", path = emptyList()))

        val outSuccess = engine.executeStrings("mv old.txt target_dir/new.txt", ctxSuccess)
        assertEquals(listOf("mv: 'old.txt' -> 'target_dir/new.txt'"), outSuccess)
        assertEquals(1, mockRepoSuccess.movedItems.size)
        assertEquals("200", mockRepoSuccess.movedItems[0]["pid"])
        assertEquals("101", mockRepoSuccess.movedItems[0]["fid[0]"])
        assertEquals(1, mockRepoSuccess.renamedItems.size)
        assertTrue(mockRepoSuccess.renamedItems[0].contains("new.txt"))

        // 2. 失败回滚场景：移动成功但重命名失败，触发事务回滚移回源目录
        val mockRepoRollback = object : TestMockFileRepository() {
            override suspend fun rename(renameBean: okhttp3.RequestBody): BaseReturnMessage {
                return BaseReturnMessage(state = false, error = "目标重名或文件已锁定")
            }
        }
        val ctxRollback = createTestContext(fileRepository = mockRepoRollback)
        ctxRollback.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(fileSrc, destDir), cid = "0", count = 2, order = "", path = emptyList()))
        ctxRollback.fileCacheManager.put("200", FilesBean(fileBeanList = arrayListOf(), cid = "200", count = 0, order = "", path = emptyList()))

        val outRollback = engine.executeStrings("mv old.txt target_dir/new.txt", ctxRollback)
        assertTrue(outRollback.any { it.contains("目标重名或文件已锁定") })
        // 验证执行了 2 次移动操作：第 1 次前向移动到目标目录(200)，第 2 次逆向回滚到源目录(0)
        assertEquals(2, mockRepoRollback.movedItems.size)
        assertEquals("200", mockRepoRollback.movedItems[0]["pid"])
        assertEquals("0", mockRepoRollback.movedItems[1]["pid"])
        assertEquals("101", mockRepoRollback.movedItems[1]["fid[0]"])
    }
}
