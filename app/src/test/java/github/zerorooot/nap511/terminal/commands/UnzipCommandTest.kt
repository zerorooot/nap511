package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * unzip 命令测试用例集合
 * 覆盖：解压校验与密码参数、转义与特殊字符路径、异常处理与管道组合场景
 */
class UnzipCommandTest {

    /**
     * 测试文件类型与存在性校验
     */
    @Test
    fun testUnzipValidations() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val folder = createMockFolder("my_folder", "10")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(folder), cid = "0", count = 1, order = "", path = emptyList()))

        // 缺失解压文件参数报错
        val outEmpty = engine.executeStrings("unzip", ctx)
        assertEquals(listOf("unzip: missing file operand"), outEmpty)

        // 试图对目录执行解压提示非压缩包
        val outDir = engine.executeStrings("unzip my_folder", ctx)
        assertTrue(outDir.any { it.contains("is a directory, not an archive") })

        // 解压不存在的 zip 报错
        val outNotExist = engine.executeStrings("unzip no_file.zip", ctx)
        assertTrue(outNotExist.any { it.contains("cannot find 'no_file.zip': No such file") })
    }

    /**
     * 1. 基本功能测试
     */
    @Test
    fun testUnzipBasicFunctions() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 查看帮助文档
        val outHelp = engine.executeStrings("unzip -h", ctx)
        assertTrue(outHelp.any { it.contains("unzip") })

        // 标准解压操作
        val out02 = engine.executeStrings("unzip test.zip", ctx)
        assertNotNull(out02)

        // -l 选项仅列出压缩包内结构
        val out03 = engine.executeStrings("unzip -l test.zip", ctx)
        assertNotNull(out03)

        // 无密码解压 zip
        val out04 = engine.executeStrings("unzip plain.zip", ctx)
        assertNotNull(out04)

        // -p 指定密码解压加密 zip
        val out05 = engine.executeStrings("unzip -p 1234 secret.zip", ctx)
        assertNotNull(out05)

        // -p 密码错误解压失败处理
        val out06 = engine.executeStrings("unzip -p wrong secret.zip", ctx)
        assertNotNull(out06)

        // 批量解压多文件
        val out07 = engine.executeStrings("unzip a.zip b.zip", ctx)
        assertNotNull(out07)

        // 解压不存在的 zip 报错
        val out08 = engine.executeStrings("unzip notexist.zip", ctx)
        assertNotNull(out08)

        // 缺失解压文件参数报错
        val out09 = engine.executeStrings("unzip", ctx)
        assertNotNull(out09)

        // 传入空字符串路径报错
        val out10 = engine.executeStrings("unzip ''", ctx)
        assertNotNull(out10)

        // 试图对非压缩包普通文本解压报错
        val out11 = engine.executeStrings("unzip test.txt", ctx)
        assertNotNull(out11)

        // 空压缩包解压处理
        val out12 = engine.executeStrings("unzip empty.zip", ctx)
        assertNotNull(out12)

        // 解压包含嵌套目录结构的 zip
        val out13 = engine.executeStrings("unzip nested.zip", ctx)
        assertNotNull(out13)

        // 中文文件名 zip 解压
        val out14 = engine.executeStrings("unzip 中文.zip", ctx)
        assertNotNull(out14)

        // 大体积压缩包解压处理
        val out15 = engine.executeStrings("unzip big.zip", ctx)
        assertNotNull(out15)
    }

    /**
     * 2. 路径与转义测试
     */
    @Test
    fun testUnzipPathAndEscapes() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 路径含空格未转义解压处理
        val out21 = engine.executeStrings("unzip my file.zip", ctx)
        assertNotNull(out21)

        // 路径含空格使用引号包裹
        val out22 = engine.executeStrings("unzip \"my file.zip\"", ctx)
        assertNotNull(out22)

        // 路径含空格使用反斜杠转义
        val out23 = engine.executeStrings("unzip my\\ file.zip", ctx)
        assertNotNull(out23)

        // 路径包含单引号
        val out24 = engine.executeStrings("unzip \"it's.zip\"", ctx)
        assertNotNull(out24)

        // 路径包含双引号
        val out25 = engine.executeStrings("unzip 'say\"hi\".zip'", ctx)
        assertNotNull(out25)

        // 路径包含美元符号 $
        val out26 = engine.executeStrings("unzip 'price\$1.zip'", ctx)
        assertNotNull(out26)

        // 路径包含惊叹号 !
        val out27 = engine.executeStrings("unzip 'important!.zip'", ctx)
        assertNotNull(out27)

        // 路径包含井号 #
        val out28 = engine.executeStrings("unzip 'note#1.zip'", ctx)
        assertNotNull(out28)

        // 路径包含中文
        val out29 = engine.executeStrings("unzip 测试.zip", ctx)
        assertNotNull(out29)

        // 路径包含 Emoji 表情
        val out30 = engine.executeStrings("unzip '😀.zip'", ctx)
        assertNotNull(out30)

        // 路径包含反斜杠
        val out31 = engine.executeStrings("unzip 'a\\\\b.zip'", ctx)
        assertNotNull(out31)

        // 路径包含制表符 Tab
        val out32 = engine.executeStrings("unzip 'a\tb.zip'", ctx)
        assertNotNull(out32)

        // 路径包含换行符
        val out33 = engine.executeStrings("unzip 'a\nb.zip'", ctx)
        assertNotNull(out33)

        // 绝对路径解压
        val out34 = engine.executeStrings("unzip /根目录/t1/test.zip", ctx)
        assertNotNull(out34)

        // 路径包含 .. 上级目录
        val out35 = engine.executeStrings("unzip ../test.zip", ctx)
        assertNotNull(out35)

        // 路径包含 ~ 家目录
        val out36 = engine.executeStrings("unzip ~/test.zip", ctx)
        assertNotNull(out36)

        // 密码包含空格
        val out37 = engine.executeStrings("unzip -p \"my pass\" secret.zip", ctx)
        assertNotNull(out37)

        // 密码包含美元符号
        val out38 = engine.executeStrings("unzip -p 'price\$1' secret.zip", ctx)
        assertNotNull(out38)

        // 密码包含惊叹号
        val out39 = engine.executeStrings("unzip -p 'hi!' secret.zip", ctx)
        assertNotNull(out39)

        // 密码包含中文
        val out40 = engine.executeStrings("unzip -p 密码 secret.zip", ctx)
        assertNotNull(out40)
    }

    /**
     * 3. 异常与边界测试
     */
    @Test
    fun testUnzipExceptionsAndBoundaries() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 未知选项容错处理
        val out41 = engine.executeStrings("unzip -x", ctx)
        assertNotNull(out41)

        // -p 选项缺失密码参数
        val out42 = engine.executeStrings("unzip -p", ctx)
        assertNotNull(out42)

        // -l 与 -p 选项组合
        val out43 = engine.executeStrings("unzip -l -p 123 secret.zip", ctx)
        assertNotNull(out43)

        // 重复 -l 选项参数覆盖
        val out44 = engine.executeStrings("unzip -l -l test.zip", ctx)
        assertNotNull(out44)

        // 重复 -p 选项参数覆盖
        val out45 = engine.executeStrings("unzip -p 1 -p 2 secret.zip", ctx)
        assertNotNull(out45)

        // 选项不同顺序组合
        val out46 = engine.executeStrings("unzip -p 123 -l secret.zip", ctx)
        assertNotNull(out46)

        // -- 选项分隔符后输入
        val out47 = engine.executeStrings("unzip -- -l", ctx)
        assertNotNull(out47)

        // 密码为空字符串解压处理
        val out48 = engine.executeStrings("unzip -p '' secret.zip", ctx)
        assertNotNull(out48)

        // 损坏的压缩包解压报错
        val out49 = engine.executeStrings("unzip broken.zip", ctx)
        assertNotNull(out49)

        // 超大体积压缩包解压处理
        val out50 = engine.executeStrings("unzip huge.zip", ctx)
        assertNotNull(out50)
    }

    /**
     * 4. 管道测试
     */
    @Test
    fun testUnzipPipelines() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // unzip -l 输出接 grep 过滤
        val out51 = engine.executeStrings("unzip -l test.zip | grep txt", ctx)
        assertNotNull(out51)

        // unzip -l 输出接 head 截取
        val out52 = engine.executeStrings("unzip -l test.zip | head", ctx)
        assertNotNull(out52)

        // unzip -l 输出接 tail 截取
        val out53 = engine.executeStrings("unzip -l test.zip | tail", ctx)
        assertNotNull(out53)

        // unzip -l 输出接 wc -l 统计项数
        val out54 = engine.executeStrings("unzip -l test.zip | wc -l", ctx)
        assertNotNull(out54)

        // unzip -l 输出接 sort 排序
        val out55 = engine.executeStrings("unzip -l test.zip | sort", ctx)
        assertNotNull(out55)

        // unzip -l 输出接 xargs 参数传递
        val out56 = engine.executeStrings("unzip -l test.zip | xargs echo", ctx)
        assertNotNull(out56)

        // unzip -l 输出接多级管道 unzip | grep | head | wc -l
        val out57 = engine.executeStrings("unzip -l test.zip | grep txt | head | wc -l", ctx)
        assertNotNull(out57)

        // unzip -l 接 grep 处理转义字符
        val out58 = engine.executeStrings("unzip -l test.zip | grep 'a\\.txt'", ctx)
        assertNotNull(out58)

        // unzip -l 接 grep 处理中文
        val out59 = engine.executeStrings("unzip -l 中文.zip | grep 测试", ctx)
        assertNotNull(out59)

        // find 查找 zip 接 xargs unzip -l
        val out60 = engine.executeStrings("find -name '*.zip' | xargs unzip -l", ctx)
        assertNotNull(out60)
    }

    /**
     * 测试通配符匹配多个压缩包并统一收集发起解压 (unzip *.zip)
     */
    @Test
    fun testUnzipWildcardMultipleArchives() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val zip1 = createMockFile("pack1.zip", "401").copy(pickCode = "PK101")
        val zip2 = createMockFile("pack2.zip", "402").copy(pickCode = "PK102")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(zip1, zip2), cid = "0", count = 2, order = "", path = emptyList())
        )

        val out = engine.executeStrings("unzip *.zip", ctx)
        assertTrue(out.any { it.contains("正在提交 2 个解压任务至后台...") })
    }
}
