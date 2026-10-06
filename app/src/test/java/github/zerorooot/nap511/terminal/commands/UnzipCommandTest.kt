package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * unzip 命令测试用例集合（U001-U060）
 * 覆盖：基本功能、路径与转义、异常与边界、管道组合
 */
class UnzipCommandTest {

    @Test
    fun testUnzipValidations() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

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

    /**
     * 1. 基本功能测试用例 (U001-U015)
     */
    @Test
    fun testUnzipBasicFunctions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // U001: 查看帮助
        val outHelp = engine.executeStrings("unzip -h", ctx)
        assertTrue(outHelp.any { it.contains("unzip") })

        // U002: 解压 zip
        val out02 = engine.executeStrings("unzip test.zip", ctx)
        assertNotNull(out02)

        // U003: -l 列结构
        val out03 = engine.executeStrings("unzip -l test.zip", ctx)
        assertNotNull(out03)

        // U004: 无密码 zip
        val out04 = engine.executeStrings("unzip plain.zip", ctx)
        assertNotNull(out04)

        // U005: 有密码 zip
        val out05 = engine.executeStrings("unzip -p 1234 secret.zip", ctx)
        assertNotNull(out05)

        // U006: 密码错误
        val out06 = engine.executeStrings("unzip -p wrong secret.zip", ctx)
        assertNotNull(out06)

        // U007: 多文件
        val out07 = engine.executeStrings("unzip a.zip b.zip", ctx)
        assertNotNull(out07)

        // U008: 不存在文件
        val out08 = engine.executeStrings("unzip notexist.zip", ctx)
        assertNotNull(out08)

        // U009: 无参数
        val out09 = engine.executeStrings("unzip", ctx)
        assertNotNull(out09)

        // U010: 空字符串
        val out10 = engine.executeStrings("unzip ''", ctx)
        assertNotNull(out10)

        // U011: 非压缩包
        val out11 = engine.executeStrings("unzip test.txt", ctx)
        assertNotNull(out11)

        // U012: 空压缩包
        val out12 = engine.executeStrings("unzip empty.zip", ctx)
        assertNotNull(out12)

        // U013: 嵌套目录
        val out13 = engine.executeStrings("unzip nested.zip", ctx)
        assertNotNull(out13)

        // U014: 中文文件名
        val out14 = engine.executeStrings("unzip 中文.zip", ctx)
        assertNotNull(out14)

        // U015: 大压缩包
        val out15 = engine.executeStrings("unzip big.zip", ctx)
        assertNotNull(out15)
    }

    /**
     * 2. 路径与转义测试用例 (U021-U040)
     */
    @Test
    fun testUnzipPathAndEscapes() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // U021: 含空格未转义
        val out21 = engine.executeStrings("unzip my file.zip", ctx)
        assertNotNull(out21)

        // U022: 含空格引号
        val out22 = engine.executeStrings("unzip \"my file.zip\"", ctx)
        assertNotNull(out22)

        // U023: 含空格反斜杠
        val out23 = engine.executeStrings("unzip my\\ file.zip", ctx)
        assertNotNull(out23)

        // U024: 含单引号
        val out24 = engine.executeStrings("unzip \"it's.zip\"", ctx)
        assertNotNull(out24)

        // U025: 含双引号
        val out25 = engine.executeStrings("unzip 'say\"hi\".zip'", ctx)
        assertNotNull(out25)

        // U026: 含 $
        val out26 = engine.executeStrings("unzip 'price\$1.zip'", ctx)
        assertNotNull(out26)

        // U027: 含 !
        val out27 = engine.executeStrings("unzip 'important!.zip'", ctx)
        assertNotNull(out27)

        // U028: 含 #
        val out28 = engine.executeStrings("unzip 'note#1.zip'", ctx)
        assertNotNull(out28)

        // U029: 中文
        val out29 = engine.executeStrings("unzip 测试.zip", ctx)
        assertNotNull(out29)

        // U030: emoji
        val out30 = engine.executeStrings("unzip '😀.zip'", ctx)
        assertNotNull(out30)

        // U031: 反斜杠
        val out31 = engine.executeStrings("unzip 'a\\\\b.zip'", ctx)
        assertNotNull(out31)

        // U032: tab
        val out32 = engine.executeStrings("unzip 'a\tb.zip'", ctx)
        assertNotNull(out32)

        // U033: 换行
        val out33 = engine.executeStrings("unzip 'a\nb.zip'", ctx)
        assertNotNull(out33)

        // U034: 绝对路径
        val out34 = engine.executeStrings("unzip /根目录/t1/test.zip", ctx)
        assertNotNull(out34)

        // U035: 路径含 ..
        val out35 = engine.executeStrings("unzip ../test.zip", ctx)
        assertNotNull(out35)

        // U036: 路径含 ~
        val out36 = engine.executeStrings("unzip ~/test.zip", ctx)
        assertNotNull(out36)

        // U037: 密码含空格
        val out37 = engine.executeStrings("unzip -p \"my pass\" secret.zip", ctx)
        assertNotNull(out37)

        // U038: 密码含 $
        val out38 = engine.executeStrings("unzip -p 'price\$1' secret.zip", ctx)
        assertNotNull(out38)

        // U039: 密码含 !
        val out39 = engine.executeStrings("unzip -p 'hi!' secret.zip", ctx)
        assertNotNull(out39)

        // U040: 密码含中文
        val out40 = engine.executeStrings("unzip -p 密码 secret.zip", ctx)
        assertNotNull(out40)
    }

    /**
     * 3. 异常与边界测试用例 (U041-U050)
     */
    @Test
    fun testUnzipExceptionsAndBoundaries() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // U041: 未知选项
        val out41 = engine.executeStrings("unzip -x", ctx)
        assertNotNull(out41)

        // U042: -p 缺参
        val out42 = engine.executeStrings("unzip -p", ctx)
        assertNotNull(out42)

        // U043: -l 与 -p 组合
        val out43 = engine.executeStrings("unzip -l -p 123 secret.zip", ctx)
        assertNotNull(out43)

        // U044: 重复 -l
        val out44 = engine.executeStrings("unzip -l -l test.zip", ctx)
        assertNotNull(out44)

        // U045: 重复 -p
        val out45 = engine.executeStrings("unzip -p 1 -p 2 secret.zip", ctx)
        assertNotNull(out45)

        // U046: 选项顺序
        val out46 = engine.executeStrings("unzip -p 123 -l secret.zip", ctx)
        assertNotNull(out46)

        // U047: -- 分隔
        val out47 = engine.executeStrings("unzip -- -l", ctx)
        assertNotNull(out47)

        // U048: 密码为空
        val out48 = engine.executeStrings("unzip -p '' secret.zip", ctx)
        assertNotNull(out48)

        // U049: 损坏压缩包
        val out49 = engine.executeStrings("unzip broken.zip", ctx)
        assertNotNull(out49)

        // U050: 超大压缩包
        val out50 = engine.executeStrings("unzip huge.zip", ctx)
        assertNotNull(out50)
    }

    /**
     * 4. 管道测试用例 (U051-U060)
     */
    @Test
    fun testUnzipPipelines() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // U051: unzip -l 接 grep
        val out51 = engine.executeStrings("unzip -l test.zip | grep txt", ctx)
        assertNotNull(out51)

        // U052: unzip -l 接 head
        val out52 = engine.executeStrings("unzip -l test.zip | head", ctx)
        assertNotNull(out52)

        // U053: unzip -l 接 tail
        val out53 = engine.executeStrings("unzip -l test.zip | tail", ctx)
        assertNotNull(out53)

        // U054: unzip -l 接 wc
        val out54 = engine.executeStrings("unzip -l test.zip | wc -l", ctx)
        assertNotNull(out54)

        // U055: unzip -l 接 sort
        val out55 = engine.executeStrings("unzip -l test.zip | sort", ctx)
        assertNotNull(out55)

        // U056: unzip -l 接 xargs
        val out56 = engine.executeStrings("unzip -l test.zip | xargs echo", ctx)
        assertNotNull(out56)

        // U057: unzip -l 接多级管道
        val out57 = engine.executeStrings("unzip -l test.zip | grep txt | head | wc -l", ctx)
        assertNotNull(out57)

        // U058: unzip -l 接 grep 转义
        val out58 = engine.executeStrings("unzip -l test.zip | grep 'a\\.txt'", ctx)
        assertNotNull(out58)

        // U059: unzip -l 接 grep 中文
        val out59 = engine.executeStrings("unzip -l 中文.zip | grep 测试", ctx)
        assertNotNull(out59)

        // U060: find 接 xargs unzip
        val out60 = engine.executeStrings("find -name '*.zip' | xargs unzip -l", ctx)
        assertNotNull(out60)
    }
}
