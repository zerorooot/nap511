package github.zerorooot.nap511.viewmodel.store

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileItemUiModel
import github.zerorooot.nap511.bean.uniqueId

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileItemUiModelTest {

    @Test
    fun `test FileItemUiModel default values`() {
        val bean = FileBean(fileId = "1001", name = "movie.mp4")
        val uiModel = FileItemUiModel(fileBean = bean)

        assertEquals(bean, uiModel.fileBean)
        assertFalse(uiModel.isSelected)
        assertFalse(uiModel.isHighlighted)
    }

    @Test
    fun `test FileItemUiModel copy preserves and updates values`() {
        val bean1 = FileBean(fileId = "1001", name = "movie.mp4")
        val uiModel = FileItemUiModel(fileBean = bean1, isSelected = false, isHighlighted = false)

        val selectedModel = uiModel.copy(isSelected = true)
        assertTrue(selectedModel.isSelected)
        assertFalse(selectedModel.isHighlighted)
        assertEquals(bean1, selectedModel.fileBean)

        val highlightedModel = selectedModel.copy(isHighlighted = true)
        assertTrue(highlightedModel.isSelected)
        assertTrue(highlightedModel.isHighlighted)
    }

    @Test
    fun `test FileItemUiModel equality and hashCode`() {
        val bean = FileBean(fileId = "1001", name = "movie.mp4")
        val model1 = FileItemUiModel(fileBean = bean, isSelected = true, isHighlighted = false)
        val model2 = FileItemUiModel(fileBean = bean, isSelected = true, isHighlighted = false)
        val model3 = FileItemUiModel(fileBean = bean, isSelected = false, isHighlighted = false)

        assertEquals(model1, model2)
        assertEquals(model1.hashCode(), model2.hashCode())
        assertNotEquals(model1, model3)
    }

    @Test
    fun `test FileBean uniqueId fallback logic`() {
        // 1. fileId is present
        val beanWithFileId = FileBean(fileId = "fid_100", categoryId = "cid_200", pickCode = "pick_300")
        assertEquals("fid_100", beanWithFileId.uniqueId)

        // 2. fileId is empty, fallback to categoryId
        val beanWithCategoryId = FileBean(fileId = "", categoryId = "cid_200", pickCode = "pick_300")
        assertEquals("cid_200", beanWithCategoryId.uniqueId)

        // 3. fileId and categoryId are empty, fallback to pickCode
        val beanWithPickCode = FileBean(fileId = "", categoryId = "", pickCode = "pick_300")
        assertEquals("pick_300", beanWithPickCode.uniqueId)

        // 4. all empty
        val beanAllEmpty = FileBean(fileId = "", categoryId = "", pickCode = "")
        assertEquals("", beanAllEmpty.uniqueId)
    }

    @Test
    fun `test mapping to FileItemUiModel maintains recomposition skip invariants`() {
        val file1 = FileBean(fileId = "1", name = "file1.txt")
        val file2 = FileBean(fileId = "2", name = "file2.txt")
        val file3 = FileBean(fileId = "3", name = "file3.txt")
        val fileList = listOf(file1, file2, file3)

        // 状态 1：没有任何选中，file1 高亮
        val selectedIds1 = emptySet<String>()
        val highlightedId1 = "1"

        val uiList1 = fileList.map { file ->
            FileItemUiModel(
                fileBean = file,
                isSelected = file.uniqueId in selectedIds1,
                isHighlighted = file.uniqueId == highlightedId1
            )
        }

        assertEquals(3, uiList1.size)
        assertFalse(uiList1[0].isSelected)
        assertTrue(uiList1[0].isHighlighted)
        assertFalse(uiList1[1].isSelected)
        assertFalse(uiList1[1].isHighlighted)
        assertFalse(uiList1[2].isSelected)
        assertFalse(uiList1[2].isHighlighted)

        // 状态 2：选中 file2，file1 高亮保持不变
        val selectedIds2 = setOf("2")
        val highlightedId2 = "1"

        val uiList2 = fileList.map { file ->
            FileItemUiModel(
                fileBean = file,
                isSelected = file.uniqueId in selectedIds2,
                isHighlighted = file.uniqueId == highlightedId2
            )
        }

        // 验证 Compose 跳过机制的核心前提：
        // 未变动的项（uiList[0] 和 uiList[2]）在结构与值上完全等价，Compose 编译器可以 100% 跳过重组
        assertEquals(uiList1[0], uiList2[0]) // file1 仍是 selected=false, highlighted=true
        assertNotEquals(uiList1[1], uiList2[1]) // file2 变更为 selected=true
        assertEquals(uiList1[2], uiList2[2]) // file3 仍是 selected=false, highlighted=false
    }
}
