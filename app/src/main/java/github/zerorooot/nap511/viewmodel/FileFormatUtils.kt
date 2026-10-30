package github.zerorooot.nap511.viewmodel

import android.annotation.SuppressLint
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.util.formatFileSize
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

// 1. 静态提取扩展名集合，避免每次遍历重复构造数组
val ZIP_EXTS = setOf("rar", "tar", "gz", "7z", "zip", "part", "jar")
val IMG_EXTS =
    setOf("gif", "jpg", "png", "jpeg", "bmp", "tif", "svg", "pic", "heic", "dng", "webp")
val AUDIO_EXTS = setOf(
    "mp3", "wma", "wav", "midi", "flac", "ram", "ra", "mid", "aac", "m4a", "ape", "au",
    "ogg", "aif", "aiff", "snd", "voc", "mpa", "cda", "vqf", "wvx", "wmx", "m3u", "m3u8",
    "ttbl", "ttpl", "tta", "tak", "mpc", "mp+", "mp3pro", "mp1", "mp2", "mac", "xm",
    "umx", "stm", "s3m", "mtm", "mod", "it", "far", "rmi", "fla", "dts", "dtswav", "awb"
)
val TXT_EXTS = setOf(
    "doc", "docx", "xls", "pdf", "ppt", "wps", "dps", "et", "mdb", "reg", "txt", "wri",
    "rtf", "lrc", "vob", "sub", "srt", "ass", "ssa", "idx", "umd", "xlsx", "xlsm", "xltx",
    "xltm", "xlam", "xlsb", "odt", "pptx", "ods", "odp", "chm", "pot", "pps", "ppsx",
    "smi", "vtt", "stl", "sbv", "ttml", "ksc", "snc", "krc", "c", "cpp", "h", "asm",
    "s", "java", "o", "asp", "aspx", "bat", "bas", "prg", "cmd", "log", "php", "js",
    "go", "sh", "css", "scss", "sass", "less", "class", "hpp", "cc", "hex", "hxx",
    "cxx", "c++", "cs", "py", "pl", "pm", "md", "cue", "utf", "dpt", "ofd", "eto",
    "ets", "mhtml", "mht", "uof", "dot", "wpt", "dotx", "docm", "dotm", "ett", "xlt",
    "pptm", "ppsm", "potx", "potm", "csv", "xml", "url"
)
val HTML_EXTS = setOf(
    "html", "htm"
)
val VIDEO_EXTS = setOf(
    "mp4", "mkv", "avi", "flv", "mov", "rmvb", "wmv", "m4v", "webm", "ts"
)

// 2. 改造函数：入参和返回值均为 List，利用 .map() 生成全新的不可变列表
fun formatFileBeanList(fileBeanList: List<FileBean>): ArrayList<FileBean> {
    // 复用同一个 SimpleDateFormat 实例
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    return fileBeanList.map { fileBean ->
        // 解析时间戳
        // 可能被 FileCacheManager 解析，所以判断一下 ifBlank
        val modifiedTimeString = fileBean.modifiedTimeString.ifBlank {
            fileBean.modifiedTime.toLongOrNull()?.let {
                dateFormat.format(it * 1000)
            } ?: ""
        }

        val createTimeString = fileBean.createTimeString.ifBlank {
            fileBean.createTime.toLongOrNull()?.let {
                dateFormat.format(it * 1000)
            } ?: ""
        }
        var playLongRatio = ""

        // 判断是否为文件夹。此函数可能被 FileCacheManager 调用，缓存时已经判断好了 isFolder，直接调用就行
        val isFolder = fileBean.isFolder || fileBean.fileId.isEmpty()
        val finalFileId = if (isFolder) fileBean.categoryId else fileBean.fileId
        val finalParentId = if (isFolder) fileBean.parentId else fileBean.categoryId

        var sizeString = fileBean.sizeString
        var timeString: String
        var rawTime = fileBean.time

        if (isFolder) {
            timeString = fileBean.timeString.ifBlank {
                fileBean.time.toLongOrNull()?.let {
                    dateFormat.format(it * 1000)
                } ?: ""
            }
        } else {
            sizeString = fileBean.sizeString.ifBlank {
                (fileBean.size.toLongOrNull() ?: 0L).formatFileSize() + " "
            }
            timeString = fileBean.time

            if (fileBean.time.isNotEmpty() && fileBean.time.all { it.isDigit() }) {
                val parsedTime = runCatching {
                    dateFormat.parse(fileBean.time)?.time?.div(1000)
                }.getOrNull()
                if (parsedTime != null) {
                    rawTime = parsedTime.toString()
                }
            }

            if (fileBean.currentPlayTime != 0 && fileBean.playLong != 0.00) {
                val playTime =
                    ((fileBean.currentPlayTime.toFloat() / fileBean.playLong) * 100).roundToInt()
                playLongRatio = "▶️ $playTime%"
            }
        }

        // 图标与时长处理
        var playLongString = fileBean.playLongString
        val ext =
            fileBean.icoString.ifEmpty { fileBean.name.substringAfterLast('.', "").lowercase() }
        val icoRes = when {
            isFolder -> R.drawable.folder
            fileBean.isVideo == 1 || ext in VIDEO_EXTS -> {
                playLongString =
                    playLongString.ifBlank { generateTime(fileBean.playLong.toLong()) + " " }
                R.drawable.mp4
            }

            ext in HTML_EXTS -> R.drawable.web
            ext in ZIP_EXTS -> R.drawable.zip
            ext in IMG_EXTS -> R.drawable.png
            ext in TXT_EXTS -> R.drawable.txt
            ext in AUDIO_EXTS -> {
                playLongString =
                    playLongString.ifBlank { generateTime(fileBean.playLong.toLong()) + " " }
                R.drawable.mp3
            }

            ext == "apk" -> R.drawable.apk
            ext == "iso" -> R.drawable.iso
            ext == "torrent" -> R.drawable.torrent
            else -> R.drawable.other
        }

        // 使用 copy() 拷贝并返回更新后的不可变对象
        fileBean.copy(
            fileId = finalFileId,
            parentId = finalParentId,
            isFolder = isFolder,
            fileIco = icoRes,
            modifiedTimeString = modifiedTimeString,
            createTimeString = createTimeString,
            timeString = timeString,
            time = rawTime,
            sizeString = sizeString,
            playLongRatio = playLongRatio,
            playLongString = playLongString
        )
    }.toMutableList() as ArrayList<FileBean>
}

@SuppressLint("DefaultLocale")
private fun generateTime(totalSeconds: Long): String {
    val seconds = totalSeconds % 60
    val minutes = totalSeconds / 60 % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) String.format(
        "%02d:%02d:%02d", hours, minutes, seconds
    ) else String.format("%02d:%02d", minutes, seconds)
}
