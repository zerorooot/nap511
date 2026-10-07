package github.zerorooot.nap511.terminal.commands

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * xargs 命令测试用例集合
 * 覆盖：基本参数传递与占位符（-n, -I, -t）、转义与特殊字符、管道组合及异常边界场景
 */
class XargsCommandTest {

    /**
     * 测试 xargs 基本功能与常见选项
     */
    @Test
    fun testXargsCommand() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 默认参数：echo 输出，相当于将输入作为 echo 参数输出
        val out1 = engine.executeStrings("echo 'a\nb\nc' | xargs", ctx)
        assertEquals(listOf("a b c"), out1)

        // -n 1 逐个分批传递给 echo
        val out2 = engine.executeStrings("echo 'apple banana orange' | xargs -n 1 echo", ctx)
        assertEquals(listOf("apple", "banana", "orange"), out2)

        // -I {} 占位符逐行替换测试
        val out3 = engine.executeStrings("echo 'folder1\nfolder2' | xargs -I {} echo move_{}_target", ctx)
        assertEquals(listOf("move_folder1_target", "move_folder2_target"), out3)

        // -t 选项回显测试
        val out4 = engine.executeStrings("echo 'hello' | xargs -t echo", ctx)
        assertEquals(listOf("+ echo hello", "hello"), out4)
    }

    /**
     * 1. 基本功能测试
     */
    @Test
    fun testXargsBasicFunctions() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 查看帮助文档
        val outHelp = engine.executeStrings("xargs -h", ctx)
        assertTrue(outHelp.any { it.contains("xargs") })

        // 默认参数传递给 echo 合并输出
        val out02 = engine.executeStrings("echo 'a\nb' | xargs", ctx)
        assertEquals(listOf("a b"), out02)

        // 显式指定 echo 命令
        val out03 = engine.executeStrings("echo 'a b' | xargs echo", ctx)
        assertEquals(listOf("a b"), out03)

        // -n 1 选项逐个分批传递
        val out04 = engine.executeStrings("echo 'a\nb' | xargs -n 1 echo", ctx)
        assertEquals(listOf("a", "b"), out04)

        // -n 2 选项每两个一批传递
        val out05 = engine.executeStrings("echo 'a\nb\nc' | xargs -n 2 echo", ctx)
        assertEquals(listOf("a b", "c"), out05)

        // -I {} 选项占位符逐行替换
        val out06 = engine.executeStrings("echo 'a\nb' | xargs -I {} echo [{}]", ctx)
        assertEquals(listOf("[a]", "[b]"), out06)

        // -t 选项回显命令信息
        val out07 = engine.executeStrings("echo 'a' | xargs -t echo", ctx)
        assertTrue(out07.contains("+ echo a"))

        // -I 与 -n 选项组合
        val out08 = engine.executeStrings("echo 'a\nb' | xargs -I {} -n 1 echo {}", ctx)
        assertNotNull(out08)

        // 空输入流处理
        val out09 = engine.executeStrings("echo '' | xargs echo", ctx)
        assertNotNull(out09)

        // 单行多词拆分
        val out10 = engine.executeStrings("echo 'a b c' | xargs echo", ctx)
        assertEquals(listOf("a b c"), out10)

        // 多行输入合并
        val out11 = engine.executeStrings("echo 'a\nb' | xargs echo", ctx)
        assertEquals(listOf("a b"), out11)

        // 包含空格参数传递
        val out12 = engine.executeStrings("echo 'my file' | xargs echo", ctx)
        assertNotNull(out12)

        // 包含单引号参数传递
        val out13 = engine.executeStrings("echo \"it's\" | xargs echo", ctx)
        assertNotNull(out13)

        // 包含双引号参数传递
        val out14 = engine.executeStrings("echo 'say\"hi\"' | xargs echo", ctx)
        assertNotNull(out14)

        // 带有初始前缀参数输出
        val out15 = engine.executeStrings("echo 'a' | xargs echo prefix", ctx)
        assertEquals(listOf("prefix a"), out15)

        // 指定其它命令（如 ls）
        val out16 = engine.executeStrings("echo 'a' | xargs ls", ctx)
        assertNotNull(out16)

        // 找不到命令时的容错处理
        val out17 = engine.executeStrings("echo 'a' | xargs notexist", ctx)
        assertNotNull(out17)

        // 命令为空字符串处理
        val out18 = engine.executeStrings("echo 'a' | xargs ''", ctx)
        assertNotNull(out18)

        // 未知选项容错处理
        val out19 = engine.executeStrings("xargs -x", ctx)
        assertNotNull(out19)

        // -n 缺失参数处理
        val out20 = engine.executeStrings("xargs -n", ctx)
        assertNotNull(out20)
    }

    /**
     * 2. 转义与特殊字符测试
     */
    @Test
    fun testXargsEscapesAndSpecialCharacters() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 美元符号 $ 参数传递
        val out21 = engine.executeStrings("echo 'price\$1' | xargs echo", ctx)
        assertEquals(listOf("price\$1"), out21)

        // 惊叹号 ! 参数传递
        val out22 = engine.executeStrings("echo 'hi!' | xargs echo", ctx)
        assertEquals(listOf("hi!"), out22)

        // 井号 # 参数传递
        val out23 = engine.executeStrings("echo 'a#b' | xargs echo", ctx)
        assertEquals(listOf("a#b"), out23)

        // 反斜杠转义字符传递
        val out24 = engine.executeStrings("echo 'a\\\\b' | xargs echo", ctx)
        assertNotNull(out24)

        // 通配符星号 * 传递
        val out25 = engine.executeStrings("echo '*.txt' | xargs echo", ctx)
        assertEquals(listOf("*.txt"), out25)

        // 通配符问号 ? 传递
        val out26 = engine.executeStrings("echo '?.txt' | xargs echo", ctx)
        assertEquals(listOf("?.txt"), out26)

        // 方括号 [] 传递
        val out27 = engine.executeStrings("echo '[test]' | xargs echo", ctx)
        assertEquals(listOf("[test]"), out27)

        // 反`号传递
        val out28 = engine.executeStrings("echo '`whoami`' | xargs echo", ctx)
        assertNotNull(out28)

        // 命令替换 $(whoami) 语法传递
        val out29 = engine.executeStrings("echo '\$(whoami)' | xargs echo", ctx)
        assertNotNull(out29)

        // 中文字符传递
        val out30 = engine.executeStrings("echo '测试' | xargs echo", ctx)
        assertEquals(listOf("测试"), out30)

        // Emoji 表情传递
        val out31 = engine.executeStrings("echo '😀' | xargs echo", ctx)
        assertEquals(listOf("😀"), out31)

        // 制表符 Tab 字符传递
        val out32 = engine.executeStrings("echo 'a\tb' | xargs echo", ctx)
        assertEquals(listOf("a b"), out32)

        // 换行符 \n 与 -I 结合传递
        val out33 = engine.executeStrings("echo 'a\nb' | xargs -I {} echo {}", ctx)
        assertEquals(listOf("a", "b"), out33)

        // 单引号包裹字符串传递
        val out34 = engine.executeStrings("echo \"it's\" | xargs -I {} echo {}", ctx)
        assertEquals(listOf("it's"), out34)

        // 双引号包裹字符串传递
        val out35 = engine.executeStrings("echo 'say\"hi\"' | xargs -I {} echo {}", ctx)
        assertEquals(listOf("say\"hi\""), out35)

        // 全角空格处理
        val out36 = engine.executeStrings("echo 'a　b' | xargs echo", ctx)
        assertNotNull(out36)

        // 中文引号处理
        val out37 = engine.executeStrings("echo '“测试”' | xargs echo", ctx)
        assertEquals(listOf("“测试”"), out37)

        // Emoji 组合符号传递
        val out38 = engine.executeStrings("echo '👨‍👩‍👧' | xargs echo", ctx)
        assertEquals(listOf("👨‍👩‍👧"), out38)

        // 前导空格修剪传递
        val out39 = engine.executeStrings("echo '  a' | xargs echo", ctx)
        assertEquals(listOf("a"), out39)

        // 尾随空格修剪传递
        val out40 = engine.executeStrings("echo 'a  ' | xargs echo", ctx)
        assertEquals(listOf("a"), out40)
    }

    /**
     * 3. 管道与组合测试
     */
    @Test
    fun testXargsPipelinesAndCombinations() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // find 输出接 xargs
        val out41 = engine.executeStrings("find -name '*.txt' | xargs echo", ctx)
        assertNotNull(out41)

        // find 输出接 xargs -n 1
        val out42 = engine.executeStrings("find -name '*.txt' | xargs -n 1 echo", ctx)
        assertNotNull(out42)

        // find 输出接 xargs -I {}
        val out43 = engine.executeStrings("find -name '*.txt' | xargs -I {} echo [{}]", ctx)
        assertNotNull(out43)

        // ls 输出接 xargs
        val out44 = engine.executeStrings("ls | xargs echo", ctx)
        assertNotNull(out44)

        // grep 过滤接 xargs
        val out45 = engine.executeStrings("ls | grep txt | xargs echo", ctx)
        assertNotNull(out45)

        // sort 排序接 xargs
        val out46 = engine.executeStrings("echo 'b\na' | sort | xargs echo", ctx)
        assertEquals(listOf("a b"), out46)

        // head 截取接 xargs
        val out47 = engine.executeStrings("echo '1\n2' | head -n 1 | xargs echo", ctx)
        assertEquals(listOf("1"), out47)

        // tail 截取接 xargs
        val out48 = engine.executeStrings("echo '1\n2' | tail -n 1 | xargs echo", ctx)
        assertEquals(listOf("2"), out48)

        // wc 统计接 xargs
        val out49 = engine.executeStrings("echo 'a' | wc -l | xargs echo", ctx)
        assertEquals(listOf("1"), out49)

        // history 历史接 xargs
        val out50 = engine.executeStrings("history | xargs echo", ctx)
        assertNotNull(out50)

        // xargs 结果接 wc
        val out51 = engine.executeStrings("echo 'a b' | xargs echo | wc -w", ctx)
        assertEquals(listOf("2"), out51)

        // xargs 结果接 grep
        val out52 = engine.executeStrings("echo 'a\nb' | xargs echo | grep a", ctx)
        assertEquals(listOf("a b"), out52)

        // xargs 结果接 head
        val out53 = engine.executeStrings("echo 'a\nb' | xargs echo | head", ctx)
        assertEquals(listOf("a b"), out53)

        // xargs 结果接 tail
        val out54 = engine.executeStrings("echo 'a\nb' | xargs echo | tail", ctx)
        assertEquals(listOf("a b"), out54)

        // xargs 结果接 sort
        val out55 = engine.executeStrings("echo 'b a' | xargs -n 1 echo | sort", ctx)
        assertEquals(listOf("a", "b"), out55)

        // find 输出接 xargs rm 删除
        val out56 = engine.executeStrings("find -name '*.tmp' | xargs rm -f", ctx)
        assertNotNull(out56)

        // ls | grep 输出接 xargs rm 删除
        val out57 = engine.executeStrings("ls | grep tmp | xargs rm -f", ctx)
        assertNotNull(out57)

        // find 输出接 xargs stat 查看元数据
        val out58 = engine.executeStrings("find -name 'test.txt' | xargs stat", ctx)
        assertNotNull(out58)

        // find 输出接 xargs open 打开文件
        val out59 = engine.executeStrings("find -name 'test.txt' | xargs open", ctx)
        assertNotNull(out59)

        // 多级管道 find | grep | sort | head | xargs stat
        val out60 = engine.executeStrings("find | grep txt | sort | head | xargs stat", ctx)
        assertNotNull(out60)
    }

    /**
     * 4. 异常与边界测试
     */
    @Test
    fun testXargsExceptionsAndBoundaries() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // -I 选项缺失占位符参数
        val out61 = engine.executeStrings("xargs -I", ctx)
        assertNotNull(out61)

        // -I 占位符为空字符串
        val out62 = engine.executeStrings("xargs -I '' echo", ctx)
        assertNotNull(out62)

        // -n 0 选项容错处理
        val out63 = engine.executeStrings("echo 'a' | xargs -n 0 echo", ctx)
        assertNotNull(out63)

        // -n 负数选项容错处理
        val out64 = engine.executeStrings("echo 'a' | xargs -n -1 echo", ctx)
        assertNotNull(out64)

        // -n 非数字选项容错处理
        val out65 = engine.executeStrings("echo 'a' | xargs -n abc echo", ctx)
        assertNotNull(out65)

        // -n 超大数值选项处理
        val out66 = engine.executeStrings("echo 'a' | xargs -n 999999 echo", ctx)
        assertEquals(listOf("a"), out66)

        // -I 与 -n 冲突时的处理机制
        val out67 = engine.executeStrings("echo 'a' | xargs -I {} -n 1 echo", ctx)
        assertNotNull(out67)

        // -t 回显与 -I 结合
        val out68 = engine.executeStrings("echo 'a' | xargs -t -I {} echo {}", ctx)
        assertTrue(out68.contains("+ echo a"))

        // 重复 -n 选项参数覆盖
        val out69 = engine.executeStrings("echo 'a' | xargs -n 1 -n 2 echo", ctx)
        assertNotNull(out69)

        // -- 选项分隔符
        val out70 = engine.executeStrings("echo 'a' | xargs -- echo", ctx)
        assertEquals(listOf("a"), out70)

        // 空输入接命令执行
        val out76 = engine.executeStrings("echo '' | xargs echo", ctx)
        assertNotNull(out76)

        // 输入仅包含空白字符处理
        val out77 = engine.executeStrings("echo '   ' | xargs echo", ctx)
        assertNotNull(out77)

        // 输入包含换行与空格与 -n 1 结合
        val out78 = engine.executeStrings("echo 'a b\nc d' | xargs -n 1 echo", ctx)
        assertEquals(listOf("a", "b", "c", "d"), out78)

        // -I {} 多个占位符替换
        val out79 = engine.executeStrings("echo 'a' | xargs -I {} echo {} {}", ctx)
        assertEquals(listOf("a a"), out79)

        // -I 占位符使用特殊字符 (如 '@@')
        val out80 = engine.executeStrings("echo 'a' | xargs -I '@@' echo '@@'", ctx)
        assertEquals(listOf("a"), out80)
    }

    /**
     * 5. POSIX 规范化增强测试（子命令选项无损透传、-0、-p、-L、-E、引号空格保护）
     */
    @Test
    fun testXargsPosixStandards() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 1. 核心 Bug 验收：子命令专属选项无损透传（POSIX Guideline 13），-l 不被 xargs 劫持
        val outSubFlag = engine.executeStrings("echo 'archive.zip' | xargs -t -I _ unzip -l _", ctx)
        assertTrue(
            "xargs -t 输出必须包含完整的子命令选项 -l",
            outSubFlag.any { it.contains("+ unzip -l archive.zip") }
        )

        // 2. -0 / --null NUL 字符模式：安全处理带空格与特殊字符的文件名
        val outNullDelim = engine.executeStrings("echo -e 'a b.txt\\0c d.txt\\0' | xargs -0 -n 1 echo", ctx)
        assertEquals(listOf("a b.txt", "c d.txt"), outNullDelim)

        // 3. -L 行批处理模式：按行合并参数分批执行
        val outLines = engine.executeStrings("echo -e '1\\n2\\n3\\n4' | xargs -L 2 echo", ctx)
        assertEquals(listOf("1 2", "3 4"), outLines)

        // 4. -E 逻辑 EOF 终止符：遇到指定标记行立即终止读取并执行
        val outEof = engine.executeStrings("echo -e 'first\\nSTOP\\nsecond' | xargs -E STOP echo", ctx)
        assertEquals(listOf("first"), outEof)

        // 5. 默认模式支持单双引号保留文件名内嵌空格（避免 \s+ 暴力破坏）
        val outQuotes = engine.executeStrings("echo \"'song a.mp3' 'song b.mp3'\" | xargs -n 1 echo", ctx)
        assertEquals(listOf("song a.mp3", "song b.mp3"), outQuotes)

        // 6. -p 交互式确认测试：支持用户确认与取消
        var promptTriggered = false
        val confirmCtx = createTestContext(
            onConfirmRequest = { prompt ->
                promptTriggered = true
                prompt.contains("+ echo test")
            }
        )
        val outPrompt = engine.executeStrings("echo 'test' | xargs -p echo", confirmCtx)
        assertTrue("必须触发交互确认询问", promptTriggered)
        assertEquals(listOf("test"), outPrompt)

        // 7. -p 交互式取消测试：用户拒绝时跳过执行
        var rejectPromptTriggered = false
        val rejectCtx = createTestContext(
            onConfirmRequest = {
                rejectPromptTriggered = true
                false // 模拟用户输入 no 取消
            }
        )
        val outReject = engine.executeStrings("echo 'test' | xargs -p echo", rejectCtx)
        assertTrue(rejectPromptTriggered)
        assertEquals(emptyList<String>(), outReject)
    }
}
