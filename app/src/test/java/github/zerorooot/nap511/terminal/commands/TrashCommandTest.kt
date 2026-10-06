package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.RecycleBean
import github.zerorooot.nap511.bean.RecycleInfo
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashCommandTest {

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
}
