package github.zerorooot.nap511.util

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.data.model.SubtitleItem
import github.zerorooot.nap511.data.model.SubtitleSourceType
import github.zerorooot.nap511.ui.navigation.findMatchingSubtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SubtitleMatchingTest {

    @Before
    fun setUp() {
        try {
            XLog.init()
        } catch (_: Throwable) {}
    }

    private fun createSubtitle(name: String, pickCode: String = "pc_$name"): SubtitleItem {
        val ext = name.substringAfterLast('.', "srt")
        return SubtitleItem(
            id = "115_$pickCode",
            name = name,
            simpleName = name,
            sourceType = SubtitleSourceType.ONE_ONE_FIVE,
            pickCode = pickCode,
            fileId = "fid_$name",
            ext = ext,
            durationMs = 180000L
        )
    }

    @Test
    fun `test findMatchingSubtitle with exact name matches`() {
        val items = listOf(
            createSubtitle("other_song.srt"),
            createSubtitle("audio.srt"),
            createSubtitle("audio.lrc")
        )

        val matched = findMatchingSubtitle("audio.mp3", items)
        assertNotNull(matched)
        assertEquals("audio.srt", matched?.name)
    }

    @Test
    fun `test findMatchingSubtitle with lrc format`() {
        val items = listOf(
            createSubtitle("music_track.lrc")
        )

        val matched = findMatchingSubtitle("music_track.flac", items)
        assertNotNull(matched)
        assertEquals("music_track.lrc", matched?.name)
    }

    @Test
    fun `test findMatchingSubtitle with language extensions`() {
        val items = listOf(
            createSubtitle("episode01.zh.srt"),
            createSubtitle("episode01.en.vtt")
        )

        val matched = findMatchingSubtitle("episode01.mp4", items)
        assertNotNull(matched)
        assertEquals("episode01.zh.srt", matched?.name)
    }

    @Test
    fun `test findMatchingSubtitle is case insensitive`() {
        val items = listOf(
            createSubtitle("Track01.LRC")
        )

        val matched = findMatchingSubtitle("track01.mp3", items)
        assertNotNull(matched)
        assertEquals("Track01.LRC", matched?.name)
    }

    @Test
    fun `test findMatchingSubtitle ignores leading and trailing whitespaces`() {
        val items = listOf(
            createSubtitle("  song_title.srt  ")
        )

        val matched = findMatchingSubtitle(" song_title.mp3 ", items)
        assertNotNull(matched)
        assertEquals("  song_title.srt  ", matched?.name)
    }

    @Test
    fun `test findMatchingSubtitle preserves list order when multiple match`() {
        val items = listOf(
            createSubtitle("audio.lrc"),
            createSubtitle("audio.srt")
        )

        val matched = findMatchingSubtitle("audio.mp3", items)
        assertNotNull(matched)
        assertEquals("audio.lrc", matched?.name)
    }

    @Test
    fun `test findMatchingSubtitle returns null when no matching item found`() {
        val items = listOf(
            createSubtitle("different_name.srt"),
            createSubtitle("another_file.lrc")
        )

        val matched = findMatchingSubtitle("target_music.mp3", items)
        assertNull(matched)
    }

    @Test
    fun `test findMatchingSubtitle returns null for empty subtitles list`() {
        val matched = findMatchingSubtitle("target_music.mp3", emptyList())
        assertNull(matched)
    }

    @Test
    fun `test findMatchingSubtitle returns null for blank or dot-only media name`() {
        val items = listOf(createSubtitle("hidden.srt"), createSubtitle(".srt"))
        assertNull(findMatchingSubtitle("", items))
        assertNull(findMatchingSubtitle("  ", items))
        assertNull(findMatchingSubtitle(".mp3", items))
    }
}
