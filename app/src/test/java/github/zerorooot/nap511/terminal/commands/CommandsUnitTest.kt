package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.CreateFolderMessage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.RecycleBean
import github.zerorooot.nap511.bean.RecycleInfo
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import github.zerorooot.nap511.terminal.viewmodel.TerminalLineType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单元测试辅助扩展函数：将命令管道执行的 Flow<TerminalOutput> 转换为纯文本列表 List<String>，便于断言比较
 */
private suspend fun PipelineEngine.executeStrings(cmd: String, ctx: TerminalContext): List<String> =
    execute(cmd, ctx).toList().map { it.text }

class CommandsUnitTest {

    @Test
    fun testStreamCommandsPipeline() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { listOf("ls", "cd Movies") }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 测试 echo | grep
        val out1 = engine.executeStrings("echo 'apple\nbanana\napricot' | grep ap", ctx)
        assertEquals(2, out1.size)
        assertTrue(out1.contains("apple"))
        assertTrue(out1.contains("apricot"))

        // 测试 sort -r
        val out2 = engine.executeStrings("echo '3\n1\n2' | sort -n", ctx)
        assertEquals(listOf("1", "2", "3"), out2)

        // 测试 wc -l
        val out3 = engine.executeStrings("echo 'one\ntwo\nthree' | wc -l", ctx)
        assertEquals(listOf("3"), out3)

        // 测试 wc (无参数时先输出各列名称，下一行输出各计数值)
        val outWcDefault = engine.executeStrings("echo 'one\ntwo\nthree' | wc", ctx)
        assertEquals(2, outWcDefault.size)
        assertTrue(outWcDefault[0].contains("Lines"))
        assertTrue(outWcDefault[0].contains("Words"))
        assertTrue(outWcDefault[0].contains("Chars"))
        assertTrue(outWcDefault[1].contains("3"))

        // 测试 head -n 2
        val out4 = engine.executeStrings("echo '1\n2\n3\n4' | head -n 2", ctx)
        assertEquals(listOf("1", "2"), out4)

        // 测试 tail -n 2
        val out5 = engine.executeStrings("echo '1\n2\n3\n4' | tail -n 2", ctx)
        assertEquals(listOf("3", "4"), out5)

        // 测试 history
        val out6 = engine.executeStrings("history", ctx)
        assertEquals(2, out6.size)
        assertTrue(out6[0].contains("ls"))
        assertTrue(out6[1].contains("cd Movies"))
    }

    @Test
    fun testCommandHelpInterception() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val out = engine.execute("ls -h", ctx).toList()
        assertTrue(out.isNotEmpty())
        assertEquals("帮助拦截必须在源头携带 TerminalLineType.System.HELP 语义类型", TerminalLineType.System.HELP, out.first().type)
        val text = out.joinToString("\n") { it.text }
        assertTrue(text.contains("ls"))
        assertTrue(text.contains("-l"))
        assertTrue(text.contains("-t"))
        assertTrue(text.contains("-S"))

        val findHelpList = engine.execute("find -h", ctx).toList()
        assertTrue(findHelpList.isNotEmpty())
        assertEquals("find -h 必须在源头携带 TerminalLineType.System.HELP 语义类型", TerminalLineType.System.HELP, findHelpList.first().type)
        val findHelp = findHelpList.joinToString("\n") { it.text }
        assertTrue(findHelp.contains("find"))
        assertTrue(findHelp.contains("-filter"))
        assertTrue(findHelp.contains("-name"))
    }

    @Test
    fun testChineseQuestionMarkHelpCommand() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val outAsciiList = engine.execute("?", ctx).toList()
        val outChineseList = engine.execute("man", ctx).toList()
        val outHelpList = engine.execute("help", ctx).toList()

        assertEquals("help 汇总输出必须在源头携带 TerminalLineType.System.HELP 语义类型", TerminalLineType.System.HELP, outChineseList.first().type)
        val outAscii = outAsciiList.joinToString("\n") { it.text }
        val outChinese = outChineseList.joinToString("\n") { it.text }
        val outHelp = outHelpList.joinToString("\n") { it.text }

        assertTrue(outChinese.contains("可用命令列表"))
        assertEquals(outAscii, outChinese)
        assertEquals(outHelp, outChinese)
    }

    @Test
    fun testLsSpecificFileWithSpace() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 模拟当前根目录存在文件夹 "SampleFolder" (cid=123)
        val folder = FileBean(
            name = "SampleFolder",
            categoryId = "123",
            isFolder = true
        )
        val rootFiles = FilesBean(
            fileBeanList = arrayListOf(folder),
            cid = "0",
            count = 1,
            order = "",
            path = emptyList()
        )
        ctx.fileCacheManager.put("0", rootFiles)

        // 模拟子目录下存在带空格的文件 "Sample Document.txt"
        val file = FileBean(
            name = "Sample Document.txt",
            fileId = "999",
            isFolder = false,
            size = "1024"
        )
        val subFiles = FilesBean(
            fileBeanList = arrayListOf(file),
            cid = "123",
            count = 1,
            order = "",
            path = emptyList()
        )
        ctx.fileCacheManager.put("123", subFiles)

        // 执行 ls -l SampleFolder/Sample\ Document.txt
        val out = engine.executeStrings("ls -l SampleFolder/Sample\\ Document.txt", ctx)
        assertEquals(1, out.size)
        assertTrue(out[0].contains("SampleFolder/Sample Document.txt"))

        // 执行普通 ls SampleFolder/Sample\ Document.txt
        val outShort = engine.executeStrings("ls SampleFolder/Sample\\ Document.txt", ctx)
        assertEquals(listOf("SampleFolder/Sample Document.txt"), outShort)
    }

    @Test
    fun testExitCommand() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        val out = engine.executeStrings("exit", ctx)
        assertEquals(listOf("__TERMINAL_EXIT__"), out)
    }

    @Test
    fun testXargsCommand() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 1. 默认参数：echo 输出，相当于将输入作为 echo 参数输出
        val out1 = engine.executeStrings("echo 'a\nb\nc' | xargs", ctx)
        assertEquals(listOf("a b c"), out1)

        // 2. -n 1 逐个分批传递给 echo
        val out2 = engine.executeStrings("echo 'apple banana orange' | xargs -n 1 echo", ctx)
        assertEquals(listOf("apple", "banana", "orange"), out2)

        // 3. -I {} 占位符逐行替换测试
        val out3 = engine.executeStrings("echo 'folder1\nfolder2' | xargs -I {} echo move_{}_target", ctx)
        assertEquals(listOf("move_folder1_target", "move_folder2_target"), out3)

        // 4. -t 选项回显测试
        val out4 = engine.executeStrings("echo 'hello' | xargs -t echo", ctx)
        assertEquals(listOf("+ echo hello", "hello"), out4)
    }

    @Test
    fun testMvMissingOperandValidation() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val out = engine.executeStrings("mv ../", ctx)
        assertEquals(listOf("mv: missing file operand"), out)
    }

    @Test
    fun testResolveTargetWithRootPrefix() = runBlocking {
        val ctx = TerminalContext()

        // 模拟根目录下包含文件夹 "t1" (cid=100)
        val t1Folder = FileBean(
            name = "t1",
            categoryId = "100",
            isFolder = true
        )
        val rootFiles = FilesBean(
            fileBeanList = arrayListOf(t1Folder),
            cid = "0",
            count = 1,
            order = "",
            path = emptyList()
        )
        ctx.fileCacheManager.put("0", rootFiles)

        // 模拟 t1 目录下包含文件夹 "Sample Dir Alpha" (cid=200)
        val subFolder = FileBean(
            name = "Sample Dir Alpha",
            categoryId = "200",
            isFolder = true
        )
        val t1Files = FilesBean(
            fileBeanList = arrayListOf(subFolder),
            cid = "100",
            count = 1,
            order = "",
            path = emptyList()
        )
        ctx.fileCacheManager.put("100", t1Files)

        // 测试解析包含 "/根目录" 前缀的绝对路径
        val resolved = ctx.resolveTarget("/根目录/t1/Sample Dir Alpha/")
        assertTrue(resolved is github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory)
        val dir = resolved as github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory
        assertEquals("200", dir.cid)
        assertEquals("/根目录/t1/Sample Dir Alpha", dir.path)
    }

    @Test
    fun testCdParentDirectory() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 模拟 根目录("0") -> t1("100") -> t2("200")
        val t1Folder = FileBean(name = "t1", categoryId = "100", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(t1Folder), cid = "0", count = 1, order = "", path = emptyList()))

        val t2Folder = FileBean(name = "t2", categoryId = "200", isFolder = true)
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(t2Folder), cid = "100", count = 1, order = "", path = emptyList()))
        ctx.fileCacheManager.put("200", FilesBean(fileBeanList = arrayListOf(), cid = "200", count = 0, order = "", path = emptyList()))

        // 1. 从根目录进入 t1/t2
        engine.execute("cd t1/t2", ctx).toList()
        assertEquals("200", ctx.currentCid)
        assertEquals("/根目录/t1/t2", ctx.currentPath)

        // 2. cd .. 应返回上级目录 /根目录/t1 (CID 100)，而非上两级 /根目录
        engine.execute("cd ..", ctx).toList()
        assertEquals("100", ctx.currentCid)
        assertEquals("/根目录/t1", ctx.currentPath)

        // 3. 再次 cd .. 应返回 /根目录 (CID 0)
        engine.execute("cd ..", ctx).toList()
        assertEquals("0", ctx.currentCid)
        assertEquals("/根目录", ctx.currentPath)

        // 4. 根目录下 cd .. 仍保留在根目录
        engine.execute("cd ..", ctx).toList()
        assertEquals("0", ctx.currentCid)
        assertEquals("/根目录", ctx.currentPath)
    }

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
        ctx.updateDirectory("100", "/test")

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

        // 模拟根目录文件列表：
        // 1. big.mp4 (size = 200MB = 209715200 bytes)
        // 2. small.txt (size = 1KB = 1024 bytes)
        // 3. empty_file.txt (size = 0 bytes)
        // 4. empty_folder (empty dir, cid = "10")
        // 5. non_empty_folder (non-empty dir, cid = "20")
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

        // 测试 1: find -size +100M (仅查出 big.mp4)
        val outSizePlus = engine.executeStrings("find -size +100M", ctx)
        assertEquals(1, outSizePlus.size)
        assertTrue(outSizePlus[0].contains("big.mp4"))

        // 测试 2: find -type f -size -10k (查出 /small.txt, /empty_file.txt, 和 /non_empty_folder/small.txt 共 3 项)
        val outSizeMinus = engine.executeStrings("find -type f -size -10k", ctx)
        assertEquals(3, outSizeMinus.size)
        assertTrue(outSizeMinus.contains("/small.txt"))
        assertTrue(outSizeMinus.contains("/empty_file.txt"))
        assertTrue(outSizeMinus.contains("/non_empty_folder/small.txt"))

        // 测试 3: find -empty (查出 empty_file.txt 和 empty_folder)
        val outEmpty = engine.executeStrings("find -empty", ctx)
        assertEquals(2, outEmpty.size)
        assertTrue(outEmpty.any { it.contains("empty_file.txt") })
        assertTrue(outEmpty.any { it.contains("empty_folder/") })
    }

    @Test
    fun testDfCommandAndOptionH(): Unit = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 测试 df --help 输出帮助文档
        val helpOut = engine.executeStrings("df --help", ctx).joinToString("\n")
        assertTrue(helpOut.contains("命令名称: df"))
        assertTrue(helpOut.contains("人性化容量单位显示"))

        // 测试 df -h 不再被误拦截为帮助，而是正常执行 df 指令逻辑
        val dfOut = engine.executeStrings("df -h", ctx).joinToString("\n")
        assertFalse(dfOut.contains("命令名称: df"))
    }

    @Test
    fun testResolveTargetDirectoryMetadataAndTrailingSlash() = runBlocking {
        val ctx = TerminalContext()

        // 模拟根目录下包含文件夹 "t1" (cid="100")
        val t1Folder = FileBean(name = "t1", categoryId = "100", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(
            fileBeanList = arrayListOf(t1Folder), cid = "0", count = 1, order = "", path = emptyList()
        ))

        // 模拟 t1 目录下包含 "Sample Dir A" (cid="200") 和 "Sample Dir B(1)" (cid="201")
        val sub1 = FileBean(name = "Sample Dir A", categoryId = "200", isFolder = true)
        val sub2 = FileBean(name = "Sample Dir B(1)", categoryId = "201", isFolder = true)
        ctx.fileCacheManager.put("100", FilesBean(
            fileBeanList = arrayListOf(sub1, sub2), cid = "100", count = 2, order = "", path = emptyList()
        ))

        // 1. 绝对路径且带末尾斜杠
        val res1 = ctx.resolveTarget("/根目录/t1/Sample Dir A/")
        assertTrue(res1 is github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory)
        val dir1 = res1 as github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory
        assertEquals("200", dir1.cid)
        assertEquals("100", dir1.parentCid)
        assertEquals("Sample Dir A", dir1.name)
        assertEquals("/根目录/t1/Sample Dir A", dir1.path)

        // 2. 相对路径且带末尾斜杠及转义/空格
        val res2 = ctx.resolveTarget("t1/Sample Dir B(1)/")
        assertTrue(res2 is github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory)
        val dir2 = res2 as github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory
        assertEquals("201", dir2.cid)
        assertEquals("100", dir2.parentCid)
        assertEquals("Sample Dir B(1)", dir2.name)

        // 3. 在 t1 目录下直接解析当前目录下的子文件夹（带或不带斜杠）
        ctx.updateDirectory("100", "/根目录/t1")
        val res3 = ctx.resolveTarget("Sample Dir A")
        assertTrue(res3 is github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory)
        val dir3 = res3 as github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory
        assertEquals("200", dir3.cid)
        assertEquals("100", dir3.parentCid)
        assertEquals("Sample Dir A", dir3.name)

        val res3Slash = ctx.resolveTarget("Sample Dir A/")
        assertTrue(res3Slash is github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory)
        val dir3Slash = res3Slash as github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory
        assertEquals("200", dir3Slash.cid)
        assertEquals("100", dir3Slash.parentCid)
        assertEquals("Sample Dir A", dir3Slash.name)
    }

    @Test
    fun testStatCommandWithPathAndTrailingSlash() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val t1Folder = FileBean(name = "t1", categoryId = "100", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(t1Folder), cid = "0", count = 1, order = "", path = emptyList()))

        val subFolder = FileBean(name = "Sample Dir A", categoryId = "200", isFolder = true)
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(subFolder), cid = "100", count = 1, order = "", path = emptyList()))

        val out = engine.executeStrings("stat '/根目录/t1/Sample Dir A/'", ctx)
        assertTrue(out.any { it.contains("File: Sample Dir A") })
        assertFalse(out.contains("  File: /"))
    }

    @Test
    fun testRmAndMkdirValidation() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 1. rm 缺失参数
        val rmEmpty = engine.executeStrings("rm", ctx)
        assertEquals(listOf("rm: missing operand"), rmEmpty)

        // 2. rm 尝试删除根目录
        val rmRoot = engine.executeStrings("rm /", ctx)
        assertTrue(rmRoot[0].contains("Cannot remove root directory"))

        // 3. rm 尝试删除不存在的路径
        val rmNotFound = engine.executeStrings("rm 't1/non_existent/'", ctx)
        assertTrue(rmNotFound[0].contains("cannot remove 't1/non_existent/': No such file or directory"))

        // 4. mkdir 缺失参数
        val mkdirEmpty = engine.executeStrings("mkdir", ctx)
        assertEquals(listOf("mkdir: missing operand"), mkdirEmpty)

        // 5. mkdir 无 -p 时若父路径不存在报错
        val mkdirNoParent = engine.executeStrings("mkdir non_existent_dir/new_sub", ctx)
        assertTrue(mkdirNoParent[0].contains("cannot create directory 'non_existent_dir/new_sub': No such file or directory"))
    }

    @Test
    fun testLsSortingAndPathOptions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val f1 = FileBean(name = "file_c.txt", size = "300", isFolder = false, modifiedTime = "1000")
        val f2 = FileBean(name = "file_a.txt", size = "100", isFolder = false, modifiedTime = "3000")
        val f3 = FileBean(name = ".hidden", size = "50", isFolder = false, modifiedTime = "2000")
        val f4 = FileBean(name = "file_b.txt", size = "200", isFolder = false, modifiedTime = "4000")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, f2, f3, f4), cid = "0", count = 4, order = "", path = emptyList()))

        // 默认 ls 不显示隐藏文件
        val outDefault = engine.executeStrings("ls", ctx)
        assertEquals(listOf("file_c.txt", "file_a.txt", "file_b.txt"), outDefault)

        // ls -a 显示全部文件
        val outAll = engine.executeStrings("ls -a", ctx)
        assertTrue(outAll.contains(".hidden"))
        assertEquals(4, outAll.size)

        // ls -S 按大小降序
        val outSize = engine.executeStrings("ls -S", ctx)
        assertEquals(listOf("file_c.txt", "file_b.txt", "file_a.txt"), outSize)

        // ls -t 按修改时间降序 (4000 -> file_b, 3000 -> file_a, 1000 -> file_c)
        val outTime = engine.executeStrings("ls -t", ctx)
        assertEquals(listOf("file_b.txt", "file_a.txt", "file_c.txt"), outTime)

        // ls -r 反向排序
        val outReverse = engine.executeStrings("ls -S -r", ctx)
        assertEquals(listOf("file_a.txt", "file_b.txt", "file_c.txt"), outReverse)

        // ls 访问不存在的路径
        val outNotFound = engine.executeStrings("ls non_existent_dir", ctx)
        assertTrue(outNotFound[0].contains("cannot access 'non_existent_dir': No such file or directory"))
    }

    @Test
    fun testCdVariationsAndErrors() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val folder = FileBean(name = "docs", categoryId = "10", isFolder = true)
        val file = FileBean(name = "note.txt", fileId = "20", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(folder, file), cid = "0", count = 2, order = "", path = emptyList()))
        ctx.fileCacheManager.put("10", FilesBean(fileBeanList = arrayListOf(), cid = "10", count = 0, order = "", path = emptyList()))

        // 1. cd 到子目录（带斜杠）
        engine.execute("cd docs/", ctx).toList()
        assertEquals("10", ctx.currentCid)
        assertEquals("/根目录/docs", ctx.currentPath)

        // 2. cd 无参数默认切回根目录
        engine.execute("cd", ctx).toList()
        assertEquals("0", ctx.currentCid)
        assertEquals("/根目录", ctx.currentPath)

        // 3. cd ~ 切回根目录
        engine.execute("cd docs", ctx).toList()
        engine.execute("cd ~", ctx).toList()
        assertEquals("0", ctx.currentCid)

        // 4. cd 到普通文件报错
        val outCdFile = engine.executeStrings("cd note.txt", ctx)
        assertTrue(outCdFile[0].contains("no such file or directory: note.txt"))

        // 5. cd 到不存在的目录报错
        val outCdNotExist = engine.executeStrings("cd not_exist_dir", ctx)
        assertTrue(outCdNotExist[0].contains("no such file or directory: not_exist_dir"))
    }

    @Test
    fun testPwdCommand() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val out = engine.executeStrings("pwd", ctx)
        assertEquals(listOf("/"), out)

        ctx.updateDirectory("99", "/根目录/work")
        val outWork = engine.executeStrings("pwd", ctx)
        assertEquals(listOf("/根目录/work"), outWork)
    }

    @Test
    fun testRmConfirmationAndSafety() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)

        // 1. 模拟交互取消删除
        val cancelCtx = TerminalContext(onConfirmRequest = { false })
        val targetFolder = FileBean(name = "important_dir", categoryId = "10", isFolder = true)
        cancelCtx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(targetFolder), cid = "0", count = 1, order = "", path = emptyList()))

        val outCancel = engine.executeStrings("rm important_dir", cancelCtx)
        assertEquals(listOf("rm: 已取消删除 'important_dir'"), outCancel)

        // 2. 尝试删除当前工作目录 '.'
        val outCurrent = engine.executeStrings("rm .", cancelCtx)
        assertTrue(outCurrent[0].contains("Cannot remove root directory") || outCurrent[0].contains("Cannot remove current working directory"))

        // 3. 在子目录下尝试删除当前工作目录
        cancelCtx.updateDirectory("10", "/根目录/important_dir")
        val outCurrentSub = engine.executeStrings("rm .", cancelCtx)
        assertTrue(outCurrentSub[0].contains("Cannot remove current working directory"))
    }

    @Test
    fun testMvOperationsAndErrors() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val file1 = FileBean(name = "a.txt", fileId = "1", isFolder = false)
        val file2 = FileBean(name = "b.txt", fileId = "2", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(file1, file2), cid = "0", count = 2, order = "", path = emptyList()))

        // 1. 多个源目标不是目录
        val outMulti = engine.executeStrings("mv a.txt b.txt not_a_dir", ctx)
        assertEquals(listOf("mv: target 'not_a_dir' is not a directory"), outMulti)

        // 2. 源文件不存在
        val outSrcNotExist = engine.executeStrings("mv no_such_file.txt target_dir", ctx)
        assertEquals(listOf("mv: cannot stat 'no_such_file.txt': No such file or directory"), outSrcNotExist)

        // 3. 目标为不存在的以斜杠结尾的路径
        val outSlashNotExist = engine.executeStrings("mv a.txt non_existent_folder/", ctx)
        assertEquals(listOf("mv: target 'non_existent_folder/' is not a directory"), outSlashNotExist)
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
        assertEquals(listOf("/根目录/report.pdf"), outSingle)

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
    fun testStatFileAndErrors() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val sampleFile = FileBean(
            name = "data.csv",
            fileId = "888",
            categoryId = "0",
            size = "2048",
            pickCode = "abcd1234efgh",
            sha1 = "1234567890abcdef",
            isFolder = false
        )
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(sampleFile), cid = "0", count = 1, order = "", path = emptyList()))

        // 1. 缺失参数
        val outEmpty = engine.executeStrings("stat", ctx)
        assertEquals(listOf("stat: missing operand"), outEmpty)

        // 2. 查看不存在的文件
        val outNotFound = engine.executeStrings("stat no_file", ctx)
        assertEquals(listOf("stat: cannot stat 'no_file': No such file or directory"), outNotFound)

        // 3. 查看普通文件元数据
        val outStat = engine.executeStrings("stat data.csv", ctx)
        assertTrue(outStat.any { it.contains("File: data.csv") })
        assertTrue(outStat.any { it.contains("Type: Regular File") })
        assertTrue(outStat.any { it.contains("2048 bytes") })
        assertTrue(outStat.any { it.contains("PickCode: abcd1234efgh") })
        assertTrue(outStat.any { it.contains("SHA-1:    1234567890abcdef") })
    }

    @Test
    fun testUnzipValidations() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val folder = FileBean(name = "my_folder", categoryId = "10", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(folder), cid = "0", count = 1, order = "", path = emptyList()))

        // 1. 缺失参数
        val outEmpty = engine.executeStrings("unzip", ctx)
        assertEquals(listOf("unzip: missing file operand"), outEmpty)

        // 2. 对目录执行解压提示非压缩包
        val outDir = engine.executeStrings("unzip my_folder", ctx)
        assertTrue(outDir.any { it.contains("is a directory, not an archive") })

        // 3. 文件不存在
        val outNotExist = engine.executeStrings("unzip no_file.zip", ctx)
        assertTrue(outNotExist.any { it.contains("cannot find 'no_file.zip': No such file") })
    }

    @Test
    fun testOpenCommand() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val testFile = FileBean(name = "test.txt", fileId = "1", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(testFile), cid = "0", count = 1, order = "", path = emptyList()))

        // 1. 缺失参数
        val outEmpty = engine.executeStrings("open", ctx)
        assertEquals(listOf("open: missing file operand"), outEmpty)

        // 2. 文件不存在
        val outNotFound = engine.executeStrings("open no_such_file.mp4", ctx)
        assertEquals(listOf("open: cannot find 'no_such_file.mp4': No such file or directory"), outNotFound)

        // 3. 未配置文件打开器
        val outNoOpener = engine.executeStrings("open test.txt", ctx)
        assertEquals(listOf("open: 当前终端环境未配置文件打开器"), outNoOpener)
    }

    @Test
    fun testStreamCommandsExtendedOptions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 1. grep -i 忽略大小写
        val grepI = engine.executeStrings("echo 'Apple\nbanana\nAPPLE' | grep -i apple", ctx)
        assertEquals(listOf("Apple", "APPLE"), grepI)

        // 2. grep -v 反向匹配
        val grepV = engine.executeStrings("echo 'Apple\nbanana\nApple' | grep -v Apple", ctx)
        assertEquals(listOf("banana"), grepV)

        // 3. grep -c 统计行数
        val grepC = engine.executeStrings("echo 'Apple\nbanana\nAPPLE' | grep -c Apple", ctx)
        assertEquals(listOf("1"), grepC)

        // 4. wc -w 统计词数与 wc -c 统计字符数
        val wcW = engine.executeStrings("echo 'hello world test' | wc -w", ctx)
        assertEquals(listOf("3"), wcW)

        // 5. sort 默认与 sort -r 逆序
        val sortAsc = engine.executeStrings("echo 'banana\napple\norange' | sort", ctx)
        assertEquals(listOf("apple", "banana", "orange"), sortAsc)

        val sortDesc = engine.executeStrings("echo 'banana\napple\norange' | sort -r", ctx)
        assertEquals(listOf("orange", "banana", "apple"), sortDesc)

        // 6. clear 命令
        val clearOut = engine.executeStrings("clear", ctx)
        assertEquals(listOf("__TERMINAL_CLEAR_SCREEN__"), clearOut)
    }

    @Test
    fun testTrashConfirmationCancelled() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val cancelCtx = TerminalContext(onConfirmRequest = { false })

        // trash -c 取消确认
        val out = engine.executeStrings("trash -c", cancelCtx)
        assertEquals(listOf("trash: 已取消清空操作"), out)
    }

    @Test
    fun testMkdirExistingDirectoryAndFlagP() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val existingFolder = FileBean(name = "docs", categoryId = "10", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(existingFolder), cid = "0", count = 1, order = "", path = emptyList()))

        // 1. 非 -p 模式创建已存在目录报错 File exists
        val outExist = engine.executeStrings("mkdir docs", ctx)
        assertEquals(listOf("mkdir: cannot create directory 'docs': File exists"), outExist)

        // 2. -p 模式创建已存在目录正常返回成功
        val outPExist = engine.executeStrings("mkdir -p docs", ctx)
        assertEquals(listOf("mkdir: created directory 'docs'"), outPExist)
    }

    @Test
    fun testCommandOutputLineTypes() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 1. 普通文本输出 echo -> Output.TEXT
        val echoOut = engine.execute("echo hello", ctx).toList()
        assertEquals(TerminalLineType.Output.TEXT, echoOut[0].type)

        // 2. 文件列表 ls -> Output.FILE_ENTRY
        val file = FileBean(name = "test.txt", fileId = "1", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(file), cid = "0", count = 1, order = "", path = emptyList()))
        val lsOut = engine.execute("ls", ctx).toList()
        assertEquals(TerminalLineType.Output.FILE_ENTRY, lsOut[0].type)

        // 3. 详细列表 ls -l -> 第一行 "total N" (Output.TEXT)，后续文件行 (Output.LONG_LISTING)
        val lsLongOut = engine.execute("ls -l", ctx).toList()
        assertEquals(2, lsLongOut.size)
        assertEquals(TerminalLineType.Output.TEXT, lsLongOut[0].type)
        assertEquals(TerminalLineType.Output.LONG_LISTING, lsLongOut[1].type)

        // 4. 路径输出 find -> Output.PATH_ENTRY
        val findOut = engine.execute("find test.txt", ctx).toList()
        assertEquals(TerminalLineType.Output.PATH_ENTRY, findOut[0].type)

        // 5. 错误输出 -> System.ERROR
        val errOut = engine.execute("rm", ctx).toList()
        assertEquals(TerminalLineType.System.ERROR, errOut[0].type)
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

        // 验证系统行被隔离：xargs 不会执行 rm 分类筛选结果
        assertFalse(outputs.any { it.contains("cannot remove '分类筛选结果") })
        // 验证 3 个文件成功删除
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

        // 1. 用户取消确认
        val cancelCtx = TerminalContext(fileRepository = mockRepo, onConfirmRequest = { false })
        val cancelOut = engine.executeStrings("find -filter 5 -delete", cancelCtx)
        assertTrue(cancelOut.any { it.contains("find: 已取消删除操作") })
        assertEquals(0, deletedFiles.size)

        // 2. 用户确认删除
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

        // 即使 onConfirmRequest 为 false，-f 标志也会跳过交互确认直接删除
        val ctx = TerminalContext(fileRepository = mockRepo, onConfirmRequest = { false })
        val out = engine.executeStrings("find -filter 5 -delete -f", ctx)
        assertTrue(out.any { it.contains("已成功删除 1 / 1 个项目") })
        assertEquals(1, deletedFiles.size)
        assertEquals("0" to "201", deletedFiles[0])
    }

    @Test
    fun testTrashRevertByFileNameAndRid() = runBlocking {
        val revertedRids = mutableListOf<String>()
        val mockRepo = object : FileRepository() {
            override suspend fun recycleList(
                aid: String,
                cid: String,
                offset: String,
                limit: String
            ): RecycleInfo {
                return RecycleInfo(
                    state = true,
                    recycleBeanList = arrayListOf(
                        RecycleBean(id = "8801", fileName = "project_backup.zip", isFolder = false, cid = "0"),
                        RecycleBean(id = "8802", fileName = "notes.txt", isFolder = false, cid = "0")
                    )
                )
            }

            override suspend fun revert(rid: String): BaseReturnMessage {
                revertedRids.add(rid)
                return BaseReturnMessage(state = true)
            }
        }

        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo)

        // 初始化根目录缓存（空）
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(), cid = "0", count = 0, order = "", path = emptyList()))

        // 1. 通过文件名还原
        val outName = engine.executeStrings("trash -r project_backup.zip", ctx)
        assertEquals(listOf("trash: 已还原 'project_backup.zip' (rid: 8801)"), outName)
        assertEquals(listOf("8801"), revertedRids)
        // 验证 addCachedFile 原位补入缓存
        assertTrue(ctx.fileCacheManager.containsKey("0"))
        assertEquals(1, ctx.fileCacheManager["0"]!!.fileBeanList.size)
        assertEquals("project_backup.zip", ctx.fileCacheManager["0"]!!.fileBeanList[0].name)
        assertEquals(1, ctx.fileCacheManager["0"]!!.count)

        // 2. 通过 RID 直接还原
        val outRid = engine.executeStrings("trash -r 8802", ctx)
        assertEquals(listOf("trash: 已还原 '8802'"), outRid)
        assertEquals(listOf("8801", "8802"), revertedRids)

        // 3. 还原不存在的文件报错
        val outNotFound = engine.executeStrings("trash -r non_existing.doc", ctx)
        assertEquals(listOf("trash: 未在回收站中找到 'non_existing.doc'"), outNotFound)
    }

    @Test
    fun testPipelineDiagnosticChannelIsolation() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // find 针对空目录或无匹配项会 emitSystem("未找到匹配的项目")
        // 当管道连接到 wc -l 时，System.INFO 行不会进入下游，下游计数应为 0
        val outEmptyFindPipe = engine.executeStrings("find -suffix nonexistent | wc -l", ctx)
        assertEquals(listOf("0"), outEmptyFindPipe)
    }

    @Test
    fun testRmMutatesCacheInPlace() = runBlocking {
        val f1 = FileBean(name = "file1.txt", fileId = "101", isFolder = false)
        val dir1 = FileBean(name = "folder1", categoryId = "201", isFolder = true)
        val childFile = FileBean(name = "child.txt", fileId = "301", isFolder = false)

        val mockRepo = object : FileRepository() {
            override suspend fun delete(pid: String, fid: String): BaseReturnMessage {
                return BaseReturnMessage(state = true)
            }
        }

        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo)

        // 初始化缓存
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, dir1), cid = "0", count = 2, order = "", path = emptyList()))
        ctx.fileCacheManager.put("201", FilesBean(fileBeanList = arrayListOf(childFile), cid = "201", count = 1, order = "", path = emptyList()))

        // 1. 删除普通文件
        engine.executeStrings("rm -f file1.txt", ctx)
        // 验证当前目录缓存未被移除，而是就地删除了该项且 count 减 1
        assertTrue("根目录缓存不应整体失效", ctx.fileCacheManager.containsKey("0"))
        assertEquals(1, ctx.fileCacheManager["0"]!!.fileBeanList.size)
        assertEquals("folder1", ctx.fileCacheManager["0"]!!.fileBeanList[0].name)
        assertEquals(1, ctx.fileCacheManager["0"]!!.count)

        // 2. 递归删除子目录
        engine.executeStrings("rm -r -f folder1", ctx)
        // 验证根目录中移除了 folder1
        assertTrue("根目录缓存仍保留", ctx.fileCacheManager.containsKey("0"))
        assertTrue("根目录列表已清空", ctx.fileCacheManager["0"]!!.fileBeanList.isEmpty())
        assertEquals(0, ctx.fileCacheManager["0"]!!.count)
        // 验证子目录缓存被递归清理
        assertFalse("被删除的子目录缓存应被递归清理", ctx.fileCacheManager.containsKey("201"))
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

        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1), cid = "0", count = 1, order = "", path = emptyList()))

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

        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(f1, targetDir), cid = "0", count = 2, order = "", path = emptyList()))
        ctx.fileCacheManager.put("500", FilesBean(fileBeanList = arrayListOf(), cid = "500", count = 0, order = "", path = emptyList()))

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
}
