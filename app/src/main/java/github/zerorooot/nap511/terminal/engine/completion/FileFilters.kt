package github.zerorooot.nap511.terminal.engine.completion

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.util.TextFileHelper
import github.zerorooot.nap511.viewmodel.ZIP_EXTS
import java.util.Locale

/**
 * 常用文件过滤器集合（高内聚、高复用策略集）
 *
 * 核心设计准则：
 * 1. 文件夹（file.isFolder）在文件类过滤中默认保留为 true，确保用户可流畅进行多级子目录深入补全（如 cat dir/sub/file.txt）；
 * 2. 普通文件根据特定业务特征（文本判定、压缩包后缀判定）进行严格筛选；
 * 3. 筛选逻辑彻底复用现有公用组件（如 [TextFileHelper]），避免规则割裂与重复维护。
 */
object FileFilters {

    /**
     * 允许所有文件与目录（默认行为，不过滤任何文件）
     */
    val ALL: PathFilter = PathFilter { _, _ -> true }

    /**
     * 仅允许目录（用于 cd 等导航命令）
     */
    val DIRECTORY_ONLY: PathFilter = PathFilter { file, _ -> file.isFolder }

    /**
     * 允许文本文件及所有目录（用于 cat、head、tail、wc、sort 等命令）
     * 目录始终保留供路径下钻；普通文件复用 [TextFileHelper.isTextFile] 判定
     */
    val TEXT_FILES: PathFilter = PathFilter { file, _ ->
        file.isFolder || TextFileHelper.isTextFile(file)
    }

    /**
     * 自定义扩展名集合过滤器构建工厂
     *
     * @param extensions 允许的扩展名小写集合（不含前导点号）
     * @param allowDirectories 是否允许目录（默认为 true，保留目录供多级路径下钻）
     */
    fun byExtensions(
        extensions: Set<String>,
        allowDirectories: Boolean = true
    ): PathFilter = PathFilter { file, _ ->
        if (file.isFolder) return@PathFilter allowDirectories
        val ext = file.icoString.ifEmpty {
            file.name.substringAfterLast('.', "").lowercase(Locale.US)
        }
        ext in extensions
    }

    /**
     * 自定义扩展名变长参数过滤器构建工厂
     *
     * @param extensions 允许的扩展名列表（不含前导点号）
     * @param allowDirectories 是否允许目录（默认为 true，保留目录供多级路径下钻）
     */
    fun byExtensions(
        vararg extensions: String,
        allowDirectories: Boolean = true
    ): PathFilter = byExtensions(extensions.toSet(), allowDirectories)

    /**
     * 允许压缩包文件及所有目录（用于 unzip 等解压预览命令）
     * 目录始终保留供路径下钻；普通文件基于 [byExtensions] 配合 [ZIP_EXTS] 判定
     */
    val ARCHIVE_FILES: PathFilter = byExtensions(ZIP_EXTS)
}
