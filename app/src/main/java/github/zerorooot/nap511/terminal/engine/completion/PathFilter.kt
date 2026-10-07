package github.zerorooot.nap511.terminal.engine.completion

import github.zerorooot.nap511.bean.FileBean

/**
 * 文件/路径补全过滤器函数式接口
 *
 * 遵循单一职责与高复用原则，用于在路径自动补全过程中对文件列表执行类型或特征筛选。
 */
fun interface PathFilter {
    /**
     * 判断当前文件是否符合补全条件
     *
     * @param file 待检查的文件实体 (FileBean)
     * @param argIndex 当前补全的目标位置参数索引（从 0 开始）
     * @return true 表示符合条件予以展示，false 表示过滤排除
     */
    fun accept(file: FileBean, argIndex: Int): Boolean
}
