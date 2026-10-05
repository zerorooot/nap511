package github.zerorooot.nap511.terminal.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import github.zerorooot.nap511.viewmodel.AUDIO_EXTS
import github.zerorooot.nap511.viewmodel.HTML_EXTS
import github.zerorooot.nap511.viewmodel.IMG_EXTS
import github.zerorooot.nap511.viewmodel.TXT_EXTS
import github.zerorooot.nap511.viewmodel.VIDEO_EXTS
import github.zerorooot.nap511.viewmodel.ZIP_EXTS

/**
 * 终端输出文件与文件夹配色主题
 * 色彩完全对应应用内图标资源 res/drawable 的背景色 (fillColor)
 */
data class TerminalColorTheme(
    // 1. 文件夹：来自 folder.xml (#FFA726)，加粗
    val folderStyle: SpanStyle = SpanStyle(color = TerminalColors.FileFolder, fontWeight = FontWeight.Bold),
    // 2. 视频文件：来自 mp4.xml (#EF5350)
    val videoStyle: SpanStyle = SpanStyle(color = TerminalColors.FileVideo),
    // 3. 音频文件：来自 mp3.xml (#FF7043)
    val audioStyle: SpanStyle = SpanStyle(color = TerminalColors.FileAudio),
    // 4. 图片文件：来自 png.xml (#26A69A)
    val imageStyle: SpanStyle = SpanStyle(color = TerminalColors.FileImage),
    // 5. 压缩包：来自 zip.xml (#8D6E63)
    val archiveStyle: SpanStyle = SpanStyle(color = TerminalColors.FileArchive),
    // 6. 安装包：来自 apk.xml (#66BB6A)，不加粗
    val apkStyle: SpanStyle = SpanStyle(color = TerminalColors.FileApk, fontWeight = FontWeight.Normal),
    // 7. 可执行文件：来自 exe.xml (#5C6BC0)，不加粗
    val execStyle: SpanStyle = SpanStyle(color = TerminalColors.FileExecutable, fontWeight = FontWeight.Normal),
    // 8. 文本/文档：来自 txt.xml (#42A5F5)
    val documentStyle: SpanStyle = SpanStyle(color = TerminalColors.FileDocument),
    // 9. 网页文件：来自 web.xml (#00BCD4)
    val webStyle: SpanStyle = SpanStyle(color = TerminalColors.FileWeb),
    // 10. ISO/镜像：来自 iso.xml (#7E57C2)
    val isoStyle: SpanStyle = SpanStyle(color = TerminalColors.FileIso),
    // 11. 种子文件：来自 torrent.xml (#43A047)
    val torrentStyle: SpanStyle = SpanStyle(color = TerminalColors.FileTorrent),
    // 12. 其他文件：来自 other.xml (#BABABA)
    val otherStyle: SpanStyle = SpanStyle(color = TerminalColors.FileOther),
    // 13. 元数据（权限位、大小、修改时间等）
    val metadataStyle: SpanStyle = SpanStyle(color = TerminalColors.Metadata)
) {
    /**
     * 根据文件名后缀或名称特征获取对应的 SpanStyle
     * 复用 FileViewModel 中的扩展名常量集合 (ZIP_EXTS, IMG_EXTS, AUDIO_EXTS, TXT_EXTS, HTML_EXTS, VIDEO_EXTS)
     */
    fun getStyleForFilename(name: String, isDirectory: Boolean): SpanStyle {
        val cleanName = name.trim()
        if (isDirectory || cleanName.endsWith("/")) return folderStyle

        val ext = cleanName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "apk" -> apkStyle
            "exe", "sh", "bin", "bat" -> execStyle
            "iso", "img" -> isoStyle
            "torrent" -> torrentStyle
            in HTML_EXTS -> webStyle
            in ZIP_EXTS -> archiveStyle
            in IMG_EXTS -> imageStyle
            in AUDIO_EXTS -> audioStyle
            in TXT_EXTS -> documentStyle
            in VIDEO_EXTS -> videoStyle
            else -> otherStyle
        }
    }

    companion object {
        val Default = TerminalColorTheme()
    }
}

/**
 * 终端色彩主题 CompositionLocal，供 UI 树中各组件隐式获取主题配置，消除层层手动透传 (高内聚、低耦合)
 */
val LocalTerminalTheme = staticCompositionLocalOf { TerminalColorTheme.Default }
