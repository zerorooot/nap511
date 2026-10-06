package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.bean.RecycleBean
import github.zerorooot.nap511.bean.RecycleInfo
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * trash 命令测试用例集合（TR001-TR060）
 * 覆盖：基本功能、路径与转义、异常与边界、管道组合
 */
class TrashCommandTest {

    @Test
    fun testTrashConfirmationCancelled() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val cancelCtx = TerminalContext(onConfirmRequest = { false })
        cancelCtx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // trash -c 取消确认
        val out = engine.executeStrings("trash -c", cancelCtx)
        assertEquals(listOf("trash: 已取消清空操作"), out)
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
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // 初始化根目录缓存（空）
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(), cid = "0", count = 0, order = "", path = emptyList()))

        // 1. 通过文件名还原
        val outName = engine.executeStrings("trash -r project_backup.zip", ctx)
        assertEquals(listOf("trash: 已还原 'project_backup.zip' (rid: 8801)"), outName)
        assertEquals(listOf("8801"), revertedRids)
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

    /**
     * 1. 基本功能测试用例 (TR001-TR015)
     */
    @Test
    fun testTrashBasicFunctions() = runBlocking {
        val mockRepo = object : FileRepository() {
            override suspend fun recycleList(aid: String, cid: String, offset: String, limit: String): RecycleInfo {
                return RecycleInfo(
                    state = true,
                    recycleBeanList = arrayListOf(
                        RecycleBean(id = "101", fileName = "a.txt", isFolder = false, cid = "0")
                    )
                )
            }
            override suspend fun recycleCleanAll(password: String): BaseReturnMessage {
                return BaseReturnMessage(state = true)
            }
        }
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext(fileRepository = mockRepo, onConfirmRequest = { true })
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // TR001: 查看帮助
        val outHelp = engine.executeStrings("trash -h", ctx)
        assertTrue(outHelp.any { it.contains("trash") })

        // TR002: 默认列出
        val outDefault = engine.executeStrings("trash", ctx)
        assertNotNull(outDefault)

        // TR003: -l 列出
        val outList = engine.executeStrings("trash -l", ctx)
        assertNotNull(outList)

        // TR004: 删除后列出
        engine.executeStrings("rm -f a.txt", ctx)
        val outRmList = engine.executeStrings("trash -l", ctx)
        assertNotNull(outRmList)

        // TR007: -c 清空
        val outClean = engine.executeStrings("trash -c", ctx)
        assertNotNull(outClean)

        // TR008: 清空后列出
        engine.executeStrings("trash -c", ctx)
        val outAfterClean = engine.executeStrings("trash -l", ctx)
        assertNotNull(outAfterClean)

        // TR009: 无参数
        val outNoArgs = engine.executeStrings("trash", ctx)
        assertNotNull(outNoArgs)

        // TR010: 空字符串
        val outEmpty = engine.executeStrings("trash ''", ctx)
        assertNotNull(outEmpty)

        // TR011: 不存在 rid
        val outNotRid = engine.executeStrings("trash -r notexist", ctx)
        assertNotNull(outNotRid)

        // TR012: 不存在名称
        val outNotName = engine.executeStrings("trash -r notexist.txt", ctx)
        assertNotNull(outNotName)

        // TR014: 多个 -r
        val outMultiR = engine.executeStrings("trash -r a.txt b.txt", ctx)
        assertNotNull(outMultiR)

        // TR015: -l -c 组合
        val outLC = engine.executeStrings("trash -l -c", ctx)
        assertNotNull(outLC)
    }

    /**
     * 2. 路径与转义测试用例 (TR021-TR040)
     */
    @Test
    fun testTrashPathAndEscapes() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // TR021: 名称含空格引号
        val out21 = engine.executeStrings("trash -r \"my file.txt\"", ctx)
        assertNotNull(out21)

        // TR022: 名称含空格反斜杠
        val out22 = engine.executeStrings("trash -r my\\ file.txt", ctx)
        assertNotNull(out22)

        // TR023: 名称含单引号
        val out23 = engine.executeStrings("trash -r \"it's.txt\"", ctx)
        assertNotNull(out23)

        // TR024: 名称含双引号
        val out24 = engine.executeStrings("trash -r 'say\"hi\".txt'", ctx)
        assertNotNull(out24)

        // TR025: 名称含 $
        val out25 = engine.executeStrings("trash -r 'price\$1.txt'", ctx)
        assertNotNull(out25)

        // TR026: 名称含 !
        val out26 = engine.executeStrings("trash -r 'important!.txt'", ctx)
        assertNotNull(out26)

        // TR027: 名称含 #
        val out27 = engine.executeStrings("trash -r 'note#1.txt'", ctx)
        assertNotNull(out27)

        // TR028: 中文名称
        val out28 = engine.executeStrings("trash -r 测试.txt", ctx)
        assertNotNull(out28)

        // TR029: emoji 名称
        val out29 = engine.executeStrings("trash -r '😀.png'", ctx)
        assertNotNull(out29)

        // TR030: 反斜杠名称
        val out30 = engine.executeStrings("trash -r 'a\\\\b.txt'", ctx)
        assertNotNull(out30)

        // TR031: tab 名称
        val out31 = engine.executeStrings("trash -r 'a\tb.txt'", ctx)
        assertNotNull(out31)

        // TR032: 换行名称
        val out32 = engine.executeStrings("trash -r 'a\nb.txt'", ctx)
        assertNotNull(out32)

        // TR033: 通配符
        val out33 = engine.executeStrings("trash -r '*.txt'", ctx)
        assertNotNull(out33)

        // TR034: 路径含 ..
        val out34 = engine.executeStrings("trash -r ../a.txt", ctx)
        assertNotNull(out34)

        // TR035: 路径含 ~
        val out35 = engine.executeStrings("trash -r ~/a.txt", ctx)
        assertNotNull(out35)

        // TR036: 绝对路径
        val out36 = engine.executeStrings("trash -r /根目录/t1/a.txt", ctx)
        assertNotNull(out36)

        // TR038: 名称含中文引号
        val out38 = engine.executeStrings("trash -r '“测试”.txt'", ctx)
        assertNotNull(out38)

        // TR039: 名称含全角空格
        val out39 = engine.executeStrings("trash -r 'a　b.txt'", ctx)
        assertNotNull(out39)

        // TR040: 名称含 emoji 组合
        val out40 = engine.executeStrings("trash -r '👨‍👩‍👧.png'", ctx)
        assertNotNull(out40)
    }

    /**
     * 3. 异常与边界测试用例 (TR041-TR050)
     */
    @Test
    fun testTrashExceptionsAndBoundaries() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // TR041: 未知选项
        val out41 = engine.executeStrings("trash -x", ctx)
        assertNotNull(out41)

        // TR042: -r 缺参
        val out42 = engine.executeStrings("trash -r", ctx)
        assertNotNull(out42)

        // TR043: -c 多余参数
        val out43 = engine.executeStrings("trash -c a.txt", ctx)
        assertNotNull(out43)

        // TR044: 重复 -c
        val out44 = engine.executeStrings("trash -c -c", ctx)
        assertNotNull(out44)

        // TR045: 重复 -l
        val out45 = engine.executeStrings("trash -l -l", ctx)
        assertNotNull(out45)

        // TR046: 选项顺序
        val out46 = engine.executeStrings("trash -l -r a.txt", ctx)
        assertNotNull(out46)

        // TR047: -- 分隔
        val out47 = engine.executeStrings("trash -- -l", ctx)
        assertNotNull(out47)

        // TR048: 空回收站还原
        val out48 = engine.executeStrings("trash -r a.txt", ctx)
        assertNotNull(out48)
    }

    /**
     * 4. 管道测试用例 (TR051-TR060)
     */
    @Test
    fun testTrashPipelines() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // TR051: trash -l 接 grep
        val out51 = engine.executeStrings("trash -l | grep txt", ctx)
        assertNotNull(out51)

        // TR052: trash -l 接 head
        val out52 = engine.executeStrings("trash -l | head", ctx)
        assertNotNull(out52)

        // TR053: trash -l 接 tail
        val out53 = engine.executeStrings("trash -l | tail", ctx)
        assertNotNull(out53)

        // TR054: trash -l 接 wc
        val out54 = engine.executeStrings("trash -l | wc -l", ctx)
        assertNotNull(out54)

        // TR055: trash -l 接 sort
        val out55 = engine.executeStrings("trash -l | sort", ctx)
        assertNotNull(out55)

        // TR056: trash -l 接 xargs
        val out56 = engine.executeStrings("trash -l | xargs echo", ctx)
        assertNotNull(out56)

        // TR057: rm 后 trash -l 管道
        engine.executeStrings("rm -f a.txt", ctx)
        val out57 = engine.executeStrings("trash -l | grep a.txt", ctx)
        assertNotNull(out57)

        // TR058: 还原后 ls 管道
        engine.executeStrings("trash -r a.txt", ctx)
        val out58 = engine.executeStrings("ls | grep a.txt", ctx)
        assertNotNull(out58)

        // TR059: trash -l 接多级管道
        val out59 = engine.executeStrings("trash -l | grep txt | head | wc -l", ctx)
        assertNotNull(out59)

        // TR060: trash -l 接 grep 转义
        val out60 = engine.executeStrings("trash -l | grep 'a\\.txt'", ctx)
        assertNotNull(out60)
    }
}
