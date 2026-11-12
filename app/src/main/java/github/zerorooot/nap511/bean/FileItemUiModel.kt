package github.zerorooot.nap511.bean

import androidx.compose.runtime.Immutable

/**
 * 文件列表项专属表现层模型（严格不可变）
 * 聚合纯净的文件元数据与瞬态交互状态，保证 Compose 编译器识别为强稳定
 */
@Immutable
data class FileItemUiModel(
    val fileBean: FileBean,
    val isSelected: Boolean = false,
    val isHighlighted: Boolean = false
)
