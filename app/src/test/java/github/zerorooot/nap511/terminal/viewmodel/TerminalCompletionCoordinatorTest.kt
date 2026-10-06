package github.zerorooot.nap511.terminal.viewmodel

import github.zerorooot.nap511.terminal.engine.CandidateType
import github.zerorooot.nap511.terminal.engine.CompletionCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalCompletionCoordinatorTest {

    private val sampleCandidates = listOf(
        CompletionCandidate(
            name = "ls",
            displayText = "ls",
            insertText = "ls ",
            type = CandidateType.COMMAND,
            isDirectory = false
        ),
        CompletionCandidate(
            name = "mkdir",
            displayText = "mkdir",
            insertText = "mkdir ",
            type = CandidateType.COMMAND,
            isDirectory = false
        )
    )

    @Test
    fun testShowAndDismiss() {
        val coordinator = TerminalCompletionCoordinator()
        assertFalse(coordinator.isVisible)
        assertEquals(0, coordinator.candidates.size)
        assertEquals(-1, coordinator.activeIndex)

        coordinator.show(sampleCandidates, "l", 1)
        assertTrue(coordinator.isVisible)
        assertEquals(2, coordinator.candidates.size)
        assertEquals(-1, coordinator.activeIndex)

        coordinator.dismiss()
        assertFalse(coordinator.isVisible)
        assertEquals(0, coordinator.candidates.size)
        assertEquals(-1, coordinator.activeIndex)
    }

    @Test
    fun testCycleCandidates() {
        val coordinator = TerminalCompletionCoordinator()
        coordinator.show(sampleCandidates, "l", 1)

        // 第一次轮换：选中第 0 项 "ls"
        val cycled1 = coordinator.cycle("l", 1)
        assertNotNull(cycled1)
        assertEquals(0, coordinator.activeIndex)
        assertEquals("ls ", cycled1!!.first)

        // 第二次轮换：选中第 1 项 "mkdir"
        val cycled2 = coordinator.cycle("ls ", 3)
        assertNotNull(cycled2)
        assertEquals(1, coordinator.activeIndex)
        assertEquals("mkdir ", cycled2!!.first)

        // 第三次轮换：循环回到第 0 项 "ls"
        val cycled3 = coordinator.cycle("mkdir ", 6)
        assertNotNull(cycled3)
        assertEquals(0, coordinator.activeIndex)
        assertEquals("ls ", cycled3!!.first)
    }

    @Test
    fun testApplySelected() {
        val coordinator = TerminalCompletionCoordinator()
        coordinator.show(sampleCandidates, "l", 1)

        val applied = coordinator.applySelected(sampleCandidates[0], "l", 1)
        assertEquals("ls ", applied.first)
        assertEquals(3, applied.second)
    }

    @Test
    fun testCascadeDirectoryAnchor() {
        val dirCandidate = CompletionCandidate(
            name = "docs",
            displayText = "docs/",
            insertText = "docs/",
            type = CandidateType.DIRECTORY,
            isDirectory = true
        )
        val coordinator = TerminalCompletionCoordinator()
        coordinator.show(listOf(dirCandidate), "cd d", 4)

        val applied = coordinator.applySelected(dirCandidate, "cd d", 4)
        assertEquals("cd docs/", applied.first)

        // 级联子目录设置锚点与更新候选
        coordinator.setCascadeAnchor(applied.first, applied.second)
        val subCandidates = listOf(
            CompletionCandidate(
                name = "report.pdf",
                displayText = "report.pdf",
                insertText = "report.pdf ",
                type = CandidateType.FILE,
                isDirectory = false
            )
        )
        coordinator.updateCandidates(subCandidates)
        assertTrue(coordinator.isVisible)
        assertEquals(1, coordinator.candidates.size)
        assertEquals("report.pdf", coordinator.candidates.first().name)
    }
}
