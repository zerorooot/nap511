package github.zerorooot.nap511.terminal.commands

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 终端命令选项结束符 "--" (End-of-options Delimiter) 专项测试
 *
 * 验证 github.zerorooot.nap511.terminal.commands 包下的各类命令在遇到 "--" 时的行为：
 * 1. 选项结束符前支持正常 Flag/Option；
 * 2. 选项结束符 "--" 本身被正确剔除；
 * 3. 选项结束符之后所有以 "-" 开头的参数均作为位置参数（文件名、路径、模式等）处理，不再解析为标志位。
 */
class CommandOptionDelimiterTest {

    @Test
    fun testCdWithDelimiter() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val dashFolder = createMockFolder("-test_dir", "101")
        ctx.putMockFiles("0", listOf(dashFolder))

        // cd -- -test_dir: 成功进入以 "-" 开头的目录
        engine.executeStrings("cd -- -test_dir", ctx)
        assertEquals("101", ctx.currentCid)
    }

    @Test
    fun testLsWithDelimiter() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val dashFolder = createMockFolder("-a", "102")
        val hiddenFile = createMockFile(".hidden.txt", "201")
        val normalFile = createMockFile("normal.txt", "202")
        ctx.putMockFiles("0", listOf(dashFolder))
        ctx.putMockFiles("102", listOf(hiddenFile, normalFile))

        // ls -- -a: -a 应被当作目标目录路径而非 -a (显示隐藏文件) 选项
        val out = engine.executeStrings("ls -- -a", ctx)
        assertTrue(out.contains("normal.txt"))
        assertFalse(out.contains(".hidden.txt"))

        // ls -l -- -a: -l 保留为选项，-a 作为路径
        val outLong = engine.executeStrings("ls -l -- -a", ctx)
        assertTrue(outLong.any { it.contains("normal.txt") })
    }

    @Test
    fun testMkdirWithDelimiter() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        // mkdir -- -p: -p 应作为新建文件夹的名称，而非递归创建开关
        engine.executeStrings("mkdir -- -p", ctx)
        val files = ctx.listDirectory("0")
        assertTrue(files.any { it.name == "-p" && it.isFolder })
    }

    @Test
    fun testRmWithDelimiter() = runBlocking {
        var confirmed = false
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(
            fileRepository = mockRepo,
            onConfirmRequest = {
                confirmed = true
                true
            }
        )

        val dashFile = createMockFile("-f", "301")
        ctx.putMockFiles("0", listOf(dashFile))

        // rm -- -f: -f 作为待删除文件名，此时需要二次确认（未带 -f 强制删除选项）
        engine.executeStrings("rm -- -f", ctx)
        assertTrue(confirmed)
        assertTrue(mockRepo.deletedItems.any { it.second == "301" })

        // 再次创建并使用 rm -f -- -another: 带有 -f 选项
        val dashFile2 = createMockFile("-another", "302")
        ctx.putMockFiles("0", listOf(dashFile2))
        confirmed = false
        engine.executeStrings("rm -f -- -another", ctx)
        assertFalse(confirmed) // 免确认
        assertTrue(mockRepo.deletedItems.any { it.second == "302" })
    }

    @Test
    fun testMvWithDelimiter() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val srcFile = createMockFile("-src.txt", "401")
        val destDir = createMockFolder("dest", "501")
        ctx.putMockFiles("0", listOf(srcFile, destDir))

        // mv -- -src.txt dest: -src.txt 作为源文件移动至 dest 目录
        engine.executeStrings("mv -- -src.txt dest", ctx)
        assertTrue(mockRepo.movedItems.isNotEmpty())
    }

    @Test
    fun testGrepWithDelimiter() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // grep -- -v: -v 作为匹配字符串，而非反向匹配标志
        val out1 = engine.executeStrings("echo '-v test\nnormal line' | grep -- -v", ctx)
        assertEquals(listOf("-v test"), out1)

        // grep -i -- -V: -i 作为标志，-V 作为模式
        val out2 = engine.executeStrings("echo '-v test\nnormal line' | grep -i -- -V", ctx)
        assertEquals(listOf("-v test"), out2)
    }

    @Test
    fun testWcWithDelimiter() = runBlocking {
        val repo = TestMockFileRepository().apply {
            mockDownloadStreams["303"] = "hello world\n"
        }
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = repo)

        val dashFile = createMockFile("-l", "303", size = "12", icoString = "txt")
        ctx.putMockFiles("0", listOf(dashFile))

        // wc -- -l: -l 在 "--" 之后作为待统计文件名，不作为仅统计行数标志，输出多列统计信息及文件名
        val out = engine.executeStrings("wc -- -l", ctx)
        assertTrue(out.any { it.contains("Lines") && it.contains("Words") && it.contains("Chars") })
        assertTrue(out.any { it.contains("-l") })
    }

    @Test
    fun testSortWithDelimiter() = runBlocking {
        val repo = TestMockFileRepository().apply {
            mockDownloadStreams["901"] = "2\n1\n3"
        }
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = repo)

        val rFile = createMockFile("-r", "901", size = "5", icoString = "txt")
        ctx.putMockFiles("0", listOf(rFile))

        // sort -- -r: -r 在 "--" 之后作为待排序文件名，不作为逆序选项，输出升序结果
        val out = engine.executeStrings("sort -- -r", ctx)
        assertEquals(listOf("1", "2", "3"), out)
    }

    @Test
    fun testXargsWithDelimiter() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // xargs -- echo: "--" 后为目标命令 echo
        val out = engine.executeStrings("echo 'apple' | xargs -- echo", ctx)
        assertEquals(listOf("apple"), out)
    }

    @Test
    fun testUnzipWithDelimiter() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val archiveFile = createMockFile("-l", "601")
        ctx.putMockFiles("0", listOf(archiveFile))

        // unzip -- -l: 此时 -l 被当作待解压的文件名，尝试找文件 -l
        val out = engine.executeStrings("unzip -- -l", ctx)
        assertNotNull(out)
        // 不应报错 "unzip: missing file operand"
        assertFalse(out.any { it.contains("missing file operand") })
    }

    @Test
    fun testFindWithDelimiter() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val dashDir = createMockFolder("-search_dir", "701")
        val targetFile = createMockFile("found.txt", "702")
        ctx.putMockFiles("0", listOf(dashDir))
        ctx.putMockFiles("701", listOf(targetFile))

        // find -- -search_dir: -search_dir 作为搜索起始路径
        val out = engine.executeStrings("find -- -search_dir", ctx)
        assertTrue(out.any { it.contains("found.txt") })
    }

    @Test
    fun testHistoryWithDelimiter() = runBlocking {
        val engine = createTestEngine(historyList = listOf("cmd1", "cmd2", "cmd3", "cmd4", "cmd5"))
        val ctx = createTestContext()

        // history -- 3: "--" 之后传数量 3，限制输出前 3 条
        val outLimit = engine.executeStrings("history -- 3", ctx)
        assertEquals(3, outLimit.size)

        // history -- -c: -c 在 "--" 之后不作为清空标志
        val outNoClear = engine.executeStrings("history -- -c", ctx)
        assertFalse(outNoClear.any { it.contains("history cleared") })
    }

    @Test
    fun testStatWithDelimiter() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val dashFile = createMockFile("-meta.txt", "801", size = "2048")
        ctx.putMockFiles("0", listOf(dashFile))

        // stat -- -meta.txt: 查看名称为 -meta.txt 的文件元数据
        val out = engine.executeStrings("stat -- -meta.txt", ctx)
        assertTrue(out.any { it.contains("File: -meta.txt") })
    }
}
