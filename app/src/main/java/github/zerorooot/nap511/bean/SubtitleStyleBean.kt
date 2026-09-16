package github.zerorooot.nap511.bean

import android.graphics.Color
import android.graphics.Typeface

/**
 * 字体种类枚举
 */
enum class FontFamilyType(val label: String, val typeface: Typeface) {
    DEFAULT("默认", Typeface.DEFAULT),
    SANS_SERIF("无衬线", Typeface.SANS_SERIF),
    SERIF("衬线", Typeface.SERIF),
    MONOSPACE("等宽", Typeface.MONOSPACE)
}

/**
 * 字幕样式自定义配置类
 */
data class SubtitleStyleBean(
    val textSizeSp: Float = 24f,
    val textColor: Int = Color.WHITE,
    val fontFamily: FontFamilyType = FontFamilyType.DEFAULT,
    val isBold: Boolean = false,
    val backgroundColor: Int = Color.TRANSPARENT
)
