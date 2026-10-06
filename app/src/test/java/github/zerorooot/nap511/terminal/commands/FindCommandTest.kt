package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindCommandTest {

    @Test
    fun testFindInSpecificDirectory() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 模拟根目录结构：根目录("0") 包含 test("100") 和 2023("200")
        val testFolder = FileBean(name = "test", categoryId = "100", isFolder = true)
        val dir2023Folder = FileBean(name = "2023", categoryId = "200", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(testFolder, dir2023Folder), cid = "0", count = 2, order = "", path = emptyList()))

        // 2023 包含 22("300")
        val dir22Folder = FileBean(name = "22", categoryId = "300", isFolder = true)
        ctx.fileCacheManager.put("200", FilesBean(fileBeanList = arrayListOf(dir22Folder), cid = "200", count = 1, order = "", path = emptyList()))

        // 2023/22 目录下包含子文件夹 sub_dir("400") 和普通文件 file.txt
        val subDirFolder = FileBean(name = "sub_dir", categoryId = "400", isFolder = true)
        val docFile = FileBean(name = "file.txt", fileId = "500", isFolder = false)
        ctx.fileCacheManager.put("300", FilesBean(fileBeanList = arrayListOf(subDirFolder, docFile), cid = "300", count = 2, order = "", path = emptyList()))

        // 切换工作目录到 /test
        ctx.updateDirectory(listOf(PathBean("100", "test", "0")))

        // 在 /test 目录下执行 "find 2023/22 -type d"
        val out = engine.executeStrings("find 2023/22 -type d", ctx)

        // 验证查找到的是 2023/22 目录下的子目录，而非在 /test 目录下查找
        assertEquals(1, out.size)
        assertTrue(out[0].contains("/2023/22/sub_dir/"))
    }

    @Test
    fun testFindEmptyAndSizeOptions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val bigFile = FileBean(name = "big.mp4", fileId = "1", size = "209715200", isFolder = false)
        val smallFile = FileBean(name = "small.txt", fileId = "2", size = "1024", isFolder = false)
        val emptyFile = FileBean(name = "empty_file.txt", fileId = "3", size = "0", isFolder = false)
        val emptyFolder = FileBean(name = "empty_folder", categoryId = "10", isFolder = true)
        val nonEmptyFolder = FileBean(name = "non_empty_folder", categoryId = "20", isFolder = true)

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

        // 测试 1: find -size +100M
        val outSizePlus = engine.executeStrings("find -size +100M", ctx)
        assertEquals(1, outSizePlus.size)
        assertTrue(outSizePlus[0].contains("big.mp4"))

        // 测试 2: find -type f -size -10k
        val outSizeMinus = engine.executeStrings("find -type f -size -10k", ctx)
        assertEquals(3, outSizeMinus.size)
        assertTrue(outSizeMinus.contains("/根目录/small.txt"))
        assertTrue(outSizeMinus.contains("/根目录/empty_file.txt"))
        assertTrue(outSizeMinus.contains("/根目录/non_empty_folder/small.txt"))

        // 测试 3: find -empty
        val outEmpty = engine.executeStrings("find -empty", ctx)
        assertEquals(2, outEmpty.size)
        assertTrue(outEmpty.any { it.contains("empty_file.txt") })
        assertTrue(outEmpty.any { it.contains("empty_folder/") })
    }

    @Test
    fun testFindFileAndSuffixOptions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val docFile = FileBean(name = "report.pdf", fileId = "10", size = "500", isFolder = false)
        val videoFile = FileBean(name = "movie.mp4", fileId = "20", size = "1000", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(docFile, videoFile), cid = "0", count = 2, order = "", path = emptyList()))

        // 1. 针对单个普通文件执行 find
        val outSingle = engine.executeStrings("find report.pdf", ctx)
        assertEquals(listOf("/report.pdf"), outSingle)

        // 2. 搜索不存在的路径
        val outNotFound = engine.executeStrings("find not_exist.txt", ctx)
        assertEquals(listOf("find: 'not_exist.txt': No such file or directory"), outNotFound)

        // 3. -suffix 过滤
        val outSuffix = engine.executeStrings("find -suffix mp4", ctx)
        assertEquals(listOf("/movie.mp4"), outSuffix)

        // 4. -name 匹配
        val outName = engine.executeStrings("find -name '*.pdf'", ctx)
        assertEquals(listOf("/report.pdf"), outName)
    }

    @Test
    fun testFindFilterPipeXargsRm() = runBlocking {
        val folderT1 = FileBean(name = "t1", categoryId = "10", isFolder = true)
        val f1 = FileBean(name = "backup archive 2026 part1.zip", fileId = "101", isFolder = false, size = "303649000")
        val f2 = FileBean(name = "backup archive 2026 part2.zip", fileId = "102", isFolder = false, size = "25171000")
        val f3 = FileBean(name = "backup archive 2026 part3.zip", fileId = "103", isFolder = false, size = "148537000")

        val deletedFiles = mutableListOf<Pair<String, String>>()
        val mockRepo = object : FileRepository() {
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
                return if (cid == "0") {
                    FilesBean(fileBeanList = arrayListOf(folderT1), cid = "0", count = 1, order = "", path = emptyList())
                } else {
                    FilesBean(fileBeanList = arrayListOf(f1, f2, f3), cid = "10", count = 3, order = "", path = emptyList())
                }
            }

            override suspend fun filterFile(cid: String, type: Int, limit: Int): FilesBean {
                return FilesBean(fileBeanList = arrayListOf(f1, f2, f3), cid = cid, count = 3, order = "", path = emptyList())
            }

            override suspend fun delete(pid: String, fid: String): BaseReturnMessage {
                deletedFiles.add(pid to fid)
                return BaseReturnMessage(state = true)
            }
        }

        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo, onConfirmRequest = { true })

        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(folderT1), cid = "0", count = 1, order = "", path = emptyList()))
        ctx.fileCacheManager.put("10", FilesBean(fileBeanList = arrayListOf(f1, f2, f3), cid = "10", count = 3, order = "", path = emptyList()))

        val outputs = engine.executeStrings("find t1/ -filter 5 | xargs -t -I _ rm _", ctx)

        assertFalse(outputs.any { it.contains("cannot remove '分类筛选结果") })
        assertEquals(3, deletedFiles.size)
        assertEquals(listOf("10" to "101", "10" to "102", "10" to "103"), deletedFiles)
    }

    @Test
    fun testFindWithDeleteInteractiveConfirmAndCancel() = runBlocking {
        val deletedFiles = mutableListOf<Pair<String, String>>()
        val mockRepo = object : FileRepository() {
            override suspend fun filterFile(cid: String, type: Int, limit: Int): FilesBean {
                val f1 = FileBean(name = "a.zip", fileId = "101", isFolder = false)
                val f2 = FileBean(name = "b.zip", fileId = "102", isFolder = false)
                return FilesBean(fileBeanList = arrayListOf(f1, f2), cid = cid, count = 2, order = "", path = emptyList())
            }

            override suspend fun delete(pid: String, fid: String): BaseReturnMessage {
                deletedFiles.add(pid to fid)
                return BaseReturnMessage(state = true)
            }
        }

        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)

        val cancelCtx = TerminalContext(fileRepository = mockRepo, onConfirmRequest = { false })
        val cancelOut = engine.executeStrings("find -filter 5 -delete", cancelCtx)
        assertTrue(cancelOut.any { it.contains("find: 已取消删除操作") })
        assertEquals(0, deletedFiles.size)

        val confirmCtx = TerminalContext(fileRepository = mockRepo, onConfirmRequest = { true })
        val confirmOut = engine.executeStrings("find -filter 5 -delete", confirmCtx)
        assertTrue(confirmOut.any { it.contains("已成功删除 2 / 2 个项目") })
        assertEquals(2, deletedFiles.size)
    }

    @Test
    fun testFindWithDeleteForceFlag() = runBlocking {
        val deletedFiles = mutableListOf<Pair<String, String>>()
        val mockRepo = object : FileRepository() {
            override suspend fun filterFile(cid: String, type: Int, limit: Int): FilesBean {
                val f1 = FileBean(name = "doc.pdf", fileId = "201", isFolder = false)
                return FilesBean(fileBeanList = arrayListOf(f1), cid = cid, count = 1, order = "", path = emptyList())
            }

            override suspend fun delete(pid: String, fid: String): BaseReturnMessage {
                deletedFiles.add(pid to fid)
                return BaseReturnMessage(state = true)
            }
        }

        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)

        val ctx = TerminalContext(fileRepository = mockRepo, onConfirmRequest = { false })
        val out = engine.executeStrings("find -filter 5 -delete -f", ctx)
        assertTrue(out.any { it.contains("已成功删除 1 / 1 个项目") })
        assertEquals(1, deletedFiles.size)
        assertEquals("0" to "201", deletedFiles[0])
    }

    @Test
    fun testFindDeleteMutatesCacheInPlace() = runBlocking {
        val f1 = FileBean(name = "test.log", fileId = "101", isFolder = false)
        val f2 = FileBean(name = "test.txt", fileId = "102", isFolder = false)

        val mockRepo = object : FileRepository() {
            override suspend fun delete(pid: String, fid: String): BaseReturnMessage {
                return BaseReturnMessage(state = true)
            }
        }

        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo, onConfirmRequest = { false })

        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, f2), cid = "0", count = 2, order = "", path = emptyList()))

        engine.executeStrings("find -suffix .log -delete -f", ctx)

        assertTrue("find -delete 后缓存不应失效", ctx.fileCacheManager.containsKey("0"))
        assertEquals(1, ctx.fileCacheManager["0"]!!.fileBeanList.size)
        assertEquals("test.txt", ctx.fileCacheManager["0"]!!.fileBeanList[0].name)
        assertEquals(1, ctx.fileCacheManager["0"]!!.count)
    }

    @Test
    fun testFindBasicPathAndErrors() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val f1 = FileBean(name = "fileA.txt", fileId = "1", isFolder = false)
        val dir1 = FileBean(name = "FolderB", categoryId = "2", isFolder = true)
        val f2 = FileBean(name = "fileC.txt", fileId = "3", isFolder = false)
        val dirSpace = FileBean(name = "我的 目录", categoryId = "10", isFolder = true)
        val docInSpace = FileBean(name = "doc.txt", fileId = "11", isFolder = false)

        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, dir1, dirSpace), cid = "0", count = 3, order = "", path = emptyList()))
        ctx.fileCacheManager.put("2", FilesBean(fileBeanList = arrayListOf(f2), cid = "2", count = 1, order = "", path = emptyList()))
        ctx.fileCacheManager.put("10", FilesBean(fileBeanList = arrayListOf(docInSpace), cid = "10", count = 1, order = "", path = emptyList()))

        val outDefault = engine.executeStrings("find", ctx)
        assertTrue(outDefault.contains("/根目录/fileA.txt"))
        assertTrue(outDefault.contains("/根目录/FolderB/"))
        assertTrue(outDefault.contains("/根目录/FolderB/fileC.txt"))

        val outDot = engine.executeStrings("find .", ctx)
        assertEquals(outDefault, outDot)

        val outSub = engine.executeStrings("find /根目录/FolderB", ctx)
        assertEquals(listOf("/根目录/FolderB/fileC.txt"), outSub)

        val outNotFound = engine.executeStrings("find /不存在的目录", ctx)
        assertEquals(listOf("find: '/不存在的目录': No such file or directory"), outNotFound)

        val outQuoteSpace = engine.executeStrings("find \"/根目录/我的 目录\"", ctx)
        assertEquals(listOf("/根目录/我的 目录/doc.txt"), outQuoteSpace)

        val outEscapedSpace = engine.executeStrings("find /根目录/我的\\ 目录", ctx)
        assertEquals(listOf("/根目录/我的 目录/doc.txt"), outEscapedSpace)
    }

    @Test
    fun testFindNameMatchingAndWildcards() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val files = arrayListOf(
            FileBean(name = "test.txt", fileId = "1", isFolder = false),
            FileBean(name = "test1.txt", fileId = "2", isFolder = false),
            FileBean(name = "testA.txt", fileId = "3", isFolder = false),
            FileBean(name = "my file.txt", fileId = "4", isFolder = false),
            FileBean(name = "it's.txt", fileId = "5", isFolder = false),
            FileBean(name = "say\"hi\".txt", fileId = "6", isFolder = false),
            FileBean(name = "a\\b.txt", fileId = "7", isFolder = false),
            FileBean(name = "price$1.txt", fileId = "8", isFolder = false),
            FileBean(name = "important!.txt", fileId = "9", isFolder = false),
            FileBean(name = "note#1.txt", fileId = "10", isFolder = false),
            FileBean(name = "测试文件.txt", fileId = "11", isFolder = false),
            FileBean(name = "😀emoji.png", fileId = "12", isFolder = false)
        )
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = files, cid = "0", count = files.size, order = "", path = emptyList()))

        assertEquals(listOf("/根目录/test.txt"), engine.executeStrings("find -name test.txt", ctx))

        assertEquals(listOf("/根目录/😀emoji.png"), engine.executeStrings("find -name '*.png'", ctx))
        assertEquals(listOf("/根目录/test1.txt", "/根目录/testA.txt"), engine.executeStrings("find -name 'test?.txt'", ctx))

        assertEquals(listOf("/根目录/my file.txt"), engine.executeStrings("find -name 'my file.txt'", ctx))
        assertEquals(listOf("/根目录/it's.txt"), engine.executeStrings("find -name \"it's.txt\"", ctx))
        assertEquals(listOf("/根目录/say\"hi\".txt"), engine.executeStrings("find -name 'say\"hi\".txt'", ctx))
        assertEquals(listOf("/根目录/price$1.txt"), engine.executeStrings("find -name 'price\$1.txt'", ctx))
        assertEquals(listOf("/根目录/important!.txt"), engine.executeStrings("find -name 'important!.txt'", ctx))
        assertEquals(listOf("/根目录/note#1.txt"), engine.executeStrings("find -name 'note#1.txt'", ctx))
        assertEquals(listOf("/根目录/测试文件.txt"), engine.executeStrings("find -name '测试*.txt'", ctx))
        assertEquals(listOf("/根目录/😀emoji.png"), engine.executeStrings("find -name '😀*.png'", ctx))

        assertTrue(engine.executeStrings("find -name ''", ctx).isEmpty())
    }

    @Test
    fun testFindTypeOptionVariations() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val f1 = FileBean(name = "a.txt", fileId = "1", isFolder = false)
        val f2 = FileBean(name = "b.log", fileId = "2", isFolder = false)
        val dir1 = FileBean(name = "subDir", categoryId = "10", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, f2, dir1), cid = "0", count = 3, order = "", path = emptyList()))
        ctx.fileCacheManager.put("10", FilesBean(fileBeanList = arrayListOf(), cid = "10", count = 0, order = "", path = emptyList()))

        assertEquals(listOf("/根目录/a.txt", "/根目录/b.log"), engine.executeStrings("find -type f", ctx))

        assertEquals(listOf("/根目录/subDir/"), engine.executeStrings("find -type d", ctx))

        assertEquals(listOf("/根目录/a.txt"), engine.executeStrings("find -type f -name '*.txt'", ctx))
    }

    @Test
    fun testFindSuffixOptionVariations() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val f1 = FileBean(name = "doc.pdf", fileId = "1", isFolder = false)
        val f2 = FileBean(name = "test_video.mp4", fileId = "2", isFolder = false)
        val f3 = FileBean(name = "test_doc.txt", fileId = "3", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, f2, f3), cid = "0", count = 3, order = "", path = emptyList()))

        assertEquals(listOf("/根目录/doc.pdf"), engine.executeStrings("find -suffix pdf", ctx))
        assertEquals(listOf("/根目录/test_video.mp4"), engine.executeStrings("find -suffix .mp4", ctx))

        assertEquals(listOf("/根目录/test_doc.txt"), engine.executeStrings("find -suffix TXT", ctx))

        assertEquals(listOf("/根目录/test_doc.txt"), engine.executeStrings("find -name 'test*' -suffix txt", ctx))
    }

    @Test
    fun testFindFilterCategoriesAndCombinations() = runBlocking {
        val docFile = FileBean(name = "doc.pdf", fileId = "1", isFolder = false)
        val imgFile = FileBean(name = "photo.jpg", fileId = "2", isFolder = false)
        val audioFile = FileBean(name = "song.mp3", fileId = "3", isFolder = false)
        val videoFile = FileBean(name = "movie.mp4", fileId = "4", isFolder = false)
        val appFile = FileBean(name = "app.apk", fileId = "5", isFolder = false)

        val mockRepo = object : FileRepository() {
            override suspend fun filterFile(cid: String, type: Int, limit: Int): FilesBean {
                return when (type) {
                    1 -> FilesBean(fileBeanList = arrayListOf(docFile), cid = cid, count = 1, order = "", path = emptyList())
                    2 -> FilesBean(fileBeanList = arrayListOf(imgFile), cid = cid, count = 1, order = "", path = emptyList())
                    3 -> FilesBean(fileBeanList = arrayListOf(audioFile), cid = cid, count = 1, order = "", path = emptyList())
                    4 -> FilesBean(fileBeanList = arrayListOf(videoFile), cid = cid, count = 1, order = "", path = emptyList())
                    6 -> FilesBean(fileBeanList = arrayListOf(appFile), cid = cid, count = 1, order = "", path = emptyList())
                    else -> FilesBean(fileBeanList = arrayListOf(), cid = cid, count = 0, order = "", path = emptyList())
                }
            }
        }

        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo)
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val outDoc = engine.executeStrings("find -filter doc", ctx)
        assertTrue(outDoc.contains("/根目录/doc.pdf"))

        val outImg = engine.executeStrings("find -filter img", ctx)
        assertTrue(outImg.contains("/根目录/photo.jpg"))

        val outAudio = engine.executeStrings("find -filter 3", ctx)
        assertTrue(outAudio.contains("/根目录/song.mp3"))

        val outVideo = engine.executeStrings("find -filter video", ctx)
        assertTrue(outVideo.contains("/根目录/movie.mp4"))

        val outApp = engine.executeStrings("find -filter app", ctx)
        assertTrue(outApp.contains("/根目录/app.apk"))

        val outImgName = engine.executeStrings("find -filter img -name '*.jpg'", ctx)
        assertTrue(outImgName.contains("/根目录/photo.jpg"))

        val outInvalid = engine.executeStrings("find -filter 99", ctx)
        assertEquals(listOf("find: 未找到匹配的分类文件 (filterType: 99)"), outInvalid)
    }

    @Test
    fun testFindMaxdepthOptions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val dirL1 = FileBean(name = "dirL1", categoryId = "10", isFolder = true)
        val fileL1 = FileBean(name = "fileL1.txt", fileId = "1", isFolder = false)
        val dirL2 = FileBean(name = "dirL2", categoryId = "20", isFolder = true)
        val fileL2 = FileBean(name = "fileL2.txt", fileId = "2", isFolder = false)
        val fileL3 = FileBean(name = "fileL3.txt", fileId = "3", isFolder = false)

        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(dirL1, fileL1), cid = "0", count = 2, order = "", path = emptyList()))
        ctx.fileCacheManager.put("10", FilesBean(fileBeanList = arrayListOf(dirL2, fileL2), cid = "10", count = 2, order = "", path = emptyList()))
        ctx.fileCacheManager.put("20", FilesBean(fileBeanList = arrayListOf(fileL3), cid = "20", count = 1, order = "", path = emptyList()))

        val outDepth1 = engine.executeStrings("find -maxdepth 1", ctx)
        assertEquals(listOf("/根目录/dirL1/", "/根目录/fileL1.txt"), outDepth1)

        val outDepth2 = engine.executeStrings("find -maxdepth 2", ctx)
        assertEquals(
            listOf("/根目录/dirL1/", "/根目录/dirL1/dirL2/", "/根目录/dirL1/fileL2.txt", "/根目录/fileL1.txt"),
            outDepth2
        )

        val outDepth1Dir = engine.executeStrings("find -maxdepth 1 -type d", ctx)
        assertEquals(listOf("/根目录/dirL1/"), outDepth1Dir)

        val outDepth2Name = engine.executeStrings("find -maxdepth 2 -name '*.txt'", ctx)
        assertEquals(listOf("/根目录/dirL1/fileL2.txt", "/根目录/fileL1.txt"), outDepth2Name)
    }

    @Test
    fun testFindSizeOptionVariations() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val zeroFile = FileBean(name = "zero.bin", fileId = "1", size = "0", isFolder = false)
        val smallFile = FileBean(name = "small.txt", fileId = "2", size = "500", isFolder = false)
        val mediumFile = FileBean(name = "medium.dat", fileId = "3", size = "512000", isFolder = false)
        val largeFile = FileBean(name = "large.mkv", fileId = "4", size = "2147483648", isFolder = false)

        ctx.fileCacheManager.put("0", FilesBean(
            fileBeanList = arrayListOf(zeroFile, smallFile, mediumFile, largeFile),
            cid = "0", count = 4, order = "", path = emptyList()
        ))

        assertEquals(listOf("/根目录/zero.bin"), engine.executeStrings("find -size 0", ctx))
        assertEquals(listOf("/根目录/large.mkv"), engine.executeStrings("find -size +100M", ctx))
        assertEquals(listOf("/根目录/zero.bin", "/根目录/small.txt"), engine.executeStrings("find -size -1k", ctx))
        assertEquals(listOf("/根目录/large.mkv"), engine.executeStrings("find -size +1G", ctx))
        assertEquals(listOf("/根目录/large.mkv"), engine.executeStrings("find -suffix mkv -size +100M", ctx))
    }

    @Test
    fun testFindGlobalSearchOptions() = runBlocking {
        val globalResult = FileBean(name = "cloud_movie.mp4", fileId = "999", isFolder = false)
        val mockRepo = object : FileRepository() {
            override suspend fun search(cid: String, searchValue: String, aid: Int, asc: Int, limit: Int): FilesBean {
                return if (searchValue == "*.mp4" || searchValue == "mp4") {
                    FilesBean(fileBeanList = arrayListOf(globalResult), cid = "0", count = 1, order = "", path = emptyList())
                } else {
                    FilesBean(fileBeanList = arrayListOf(), cid = "0", count = 0, order = "", path = emptyList())
                }
            }
        }

        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo)
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        val outName = engine.executeStrings("find -global -name '*.mp4'", ctx)
        assertTrue(outName.contains("cloud_movie.mp4"))

        val outSuffix = engine.executeStrings("find -global -suffix mp4", ctx)
        assertTrue(outSuffix.contains("cloud_movie.mp4"))

        val outError = engine.executeStrings("find -global", ctx)
        assertEquals(listOf("find: -global 全局搜索需要提供 -name 或 -suffix 关键词"), outError)

        val outPipe = engine.executeStrings("find -global -name '*.mp4' | head -n 1", ctx)
        assertTrue(outPipe[0].contains("全局搜索结果") || outPipe[0].contains("cloud_movie.mp4"))
    }

    @Test
    fun testFindDeleteAndForceCombinations() = runBlocking {
        val deletedTargets = mutableListOf<String>()
        val mockRepo = object : FileRepository() {
            override suspend fun delete(pid: String, fid: String): BaseReturnMessage {
                deletedTargets.add(fid)
                return BaseReturnMessage(state = true)
            }
            override suspend fun search(cid: String, searchValue: String, aid: Int, asc: Int, limit: Int): FilesBean {
                val f = FileBean(name = "global_del.txt", fileId = "777", categoryId = "0", isFolder = false)
                return FilesBean(fileBeanList = arrayListOf(f), cid = "0", count = 1, order = "", path = emptyList())
            }
        }

        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)

        val file1 = FileBean(name = "del.txt", fileId = "101", isFolder = false)
        val folder1 = FileBean(name = "deldir", categoryId = "201", isFolder = true)

        val cancelCtx = TerminalContext(fileRepository = mockRepo, onConfirmRequest = { false })
        cancelCtx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(file1), cid = "0", count = 1, order = "", path = emptyList()))
        val outCancel = engine.executeStrings("find -name 'del.txt' -delete", cancelCtx)
        assertTrue(outCancel.contains("find: 已取消删除操作"))

        val forceCtx = TerminalContext(fileRepository = mockRepo, onConfirmRequest = { false })
        forceCtx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(file1, folder1), cid = "0", count = 2, order = "", path = emptyList()))
        forceCtx.fileCacheManager.put("201", FilesBean(fileBeanList = arrayListOf(), cid = "201", count = 0, order = "", path = emptyList()))

        val outForce = engine.executeStrings("find -name 'del.txt' -f -delete", forceCtx)
        assertTrue(outForce.any { it.contains("已成功删除 1 / 1 个项目") })
        assertTrue(deletedTargets.contains("101"))

        val outForceDir = engine.executeStrings("find -name 'deldir' -type d -delete -f", forceCtx)
        assertTrue(outForceDir.any { it.contains("已成功删除 1 / 1 个项目") })
        assertTrue(deletedTargets.contains("201"))

        val outNoMatch = engine.executeStrings("find -name 'nothing' -delete -f", forceCtx)
        assertEquals(listOf("find: 未找到匹配的删除目标"), outNoMatch)

        val outGlobalDel = engine.executeStrings("find -global -name 'global_del.txt' -delete -f", forceCtx)
        assertTrue(outGlobalDel.any { it.contains("已成功删除 1 / 1 个项目") })
        assertTrue(deletedTargets.contains("777"))
    }

    @Test
    fun testFindPipelineCombinations() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        val pathBeanList = listOf(PathBean("0", "根目录", "0"))
        ctx.updateDirectory(pathBeanList)

        val f1 = FileBean(name = "a_test.txt", fileId = "1", isFolder = false)
        val f2 = FileBean(name = "b_sample.txt", fileId = "2", isFolder = false)
        val f3 = FileBean(name = "c_test.log", fileId = "3", isFolder = false)
        val f4 = FileBean(name = "my file.txt", fileId = "4", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, f2, f3, f4), cid = "0", count = 4, order = "", path =pathBeanList))

        val outGrep = engine.executeStrings("find -name '*.txt' | grep test", ctx)
        assertEquals(listOf("/根目录/a_test.txt"), outGrep)

        val outSort = engine.executeStrings("find -name '*.txt' | sort", ctx)
        assertEquals(listOf("/根目录/a_test.txt", "/根目录/b_sample.txt", "/根目录/my file.txt"), outSort)

        val outHead = engine.executeStrings("find -name '*.txt' | head -n 1", ctx)
        assertEquals(listOf("/根目录/a_test.txt"), outHead)

        val outTail = engine.executeStrings("find -name '*.txt' | tail -n 1", ctx)
        assertEquals(listOf("/根目录/my file.txt"), outTail)

        val outWc = engine.executeStrings("find -name '*.txt' | wc -l", ctx)
        assertEquals(listOf("3"), outWc)

        val outGrepV = engine.executeStrings("find -name '*.txt' | grep -v test", ctx)
        assertEquals(listOf("/根目录/b_sample.txt", "/根目录/my file.txt"), outGrepV)

        val outEmptyWc = engine.executeStrings("find -name 'non_existent' | wc -l", ctx)
        assertEquals(listOf("0"), outEmptyWc)

        val outXargs = engine.executeStrings("find -name 'my file.txt' | xargs echo", ctx)
        assertEquals(listOf("/根目录/my file.txt"), outXargs)
    }

    @Test
    fun testFindCombinationsAndBoundaries() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        val pathBeanList = listOf(PathBean("0", "根目录", "0"))

        ctx.updateDirectory(pathBeanList)

        val f1 = FileBean(name = "doc.txt", fileId = "1", size = "2048", isFolder = false)
        val f2 = FileBean(name = "big.txt", fileId = "2", size = "10485760", isFolder = false)
        val f3 = FileBean(name = "small.pdf", fileId = "3", size = "512", isFolder = false)

        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, f2, f3), cid = "0", count = 3, order = "", path = pathBeanList))

        val outMulti = engine.executeStrings("find -type f -suffix txt -size +1k -maxdepth 3", ctx)
        assertEquals(listOf("/根目录/doc.txt", "/根目录/big.txt"), outMulti)

        val outPathAfter = engine.executeStrings("find -name '*.txt' /根目录", ctx)
        assertEquals(listOf("/根目录/doc.txt", "/根目录/big.txt"), outPathAfter)

        val outHelpPipe = engine.executeStrings("find -h | head -n 3", ctx)
        assertEquals(3, outHelpPipe.size)
        assertTrue(outHelpPipe[0].contains("find"))
    }
}
