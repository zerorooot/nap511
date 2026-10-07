package github.zerorooot.nap511.util

import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.viewmodel.HTML_EXTS
import github.zerorooot.nap511.viewmodel.TXT_EXTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 文本文件规范校验结果
 */
sealed interface TextValidationResult {
    /** 校验通过，符合文本规范且大小在允许范围内 */
    object Valid : TextValidationResult

    /** 非文本文件错误 */
    data class NotTextFile(val reason: String = "非文本类型文件") : TextValidationResult

    /** 超出大小限制错误 */
    data class ExceedsSizeLimit(
        val actualBytes: Long,
        val limitBytes: Long,
        val limitKb: Int
    ) : TextValidationResult
}

/**
 * 文本文件公共处理组件 (TextFileHelper)
 *
 * 遵循高内聚、低耦合与高复用原则：
 * 1. 集中管理文本文件判定策略（图标特征与后缀白名单匹配）；
 * 2. 统一管理文本读取大小限制（支持读取全局设置与自定义阈值）；
 * 3. 封装底层字节流的安全拉取、缓存读写与行文本解码逻辑；
 * 供 [FileOpener]、命令行终端（如 `cat` 命令）及后续文本工具复用。
 */
object TextFileHelper {

    /** 默认兜底大小限制 (200 KB) */
    const val DEFAULT_TXT_SIZE_KB = 200

    /**
     * 判断指定 [FileBean] 是否为支持的文本类型文件
     *
     * 判定规则：
     * - 目录直接排除；
     * - 图标为 [R.drawable.txt] 的一律视为文本文件；
     * - 若图标未匹配，则比对扩展名是否命中预定义的 [TXT_EXTS] \ [HTML_EXTS]集合。
     */
    fun isTextFile(fileBean: FileBean): Boolean {
        if (fileBean.isFolder) return false
        if (fileBean.fileIco == R.drawable.txt) return true
        val ext = fileBean.icoString.ifEmpty {
            fileBean.name.substringAfterLast('.', "").lowercase(Locale.US)
        }
        return ext in TXT_EXTS || ext in HTML_EXTS
    }

    /**
     * 从全局设置中读取配置的文本大小上限（KB）
     * 若未配置或解析失败，降级返回默认值 [DEFAULT_TXT_SIZE_KB]
     */
    fun getDefaultSizeLimitKb(): Int {
        val configured = runCatching {
            SettingsRepository.getInstance()
                .settingUiStateFlow.value.txtSize.toIntOrNull()
        }.getOrNull()
        return configured ?: DEFAULT_TXT_SIZE_KB
    }

    /**
     * 校验文件是否满足文本类型与大小限制规范
     *
     * @param fileBean 要检验的文件元数据
     * @param maxKb 允许的最大文件大小（单位：KB），默认为全局设置项
     * @return 校验结果密封类 [TextValidationResult]
     */
    fun validate(
        fileBean: FileBean,
        maxKb: Int = getDefaultSizeLimitKb()
    ): TextValidationResult {
        if (!isTextFile(fileBean)) {
            return TextValidationResult.NotTextFile("仅支持打开/查看文本类型文件: ${fileBean.name}")
        }

        val fileSizeBytes = fileBean.size.toLongOrNull() ?: 0L
        val limitBytes = maxKb.toLong() * 1024L
        if (fileSizeBytes >= limitBytes) {
            return TextValidationResult.ExceedsSizeLimit(
                actualBytes = fileSizeBytes,
                limitBytes = limitBytes,
                limitKb = maxKb
            )
        }

        return TextValidationResult.Valid
    }

    /**
     * 安全拉取文件的原始字节流（支持优先读取/回写 [MediaViewerStateHolder] 内存缓存）
     *
     * @param repository 115 仓库层实例
     * @param fileBean 目标文件
     * @param cacheHolder 可选的查看器状态仓，用于命中内存缓存避免重复网络请求
     * @return 字节数组包装的 [Result]
     */
    /** 全局共享的小文本内容内存缓存（按 fileId/pickCode 索引），避免同一文件在终端与界面间重复发起网络 I/O */
    private val memoryTextCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    suspend fun fetchBytes(
        repository: FileRepository,
        fileBean: FileBean,
        cacheHolder: MediaViewerStateHolder? = null
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. 优先尝试从传入的状态仓瞬态缓存中复用
            cacheHolder?.getCachedBytes(fileBean)?.let { return@runCatching it }

            // 2. 检查全局共享内存缓存
            val cacheKey = fileBean.fileId.ifEmpty { fileBean.pickCode }
            if (cacheKey.isNotEmpty()) {
                memoryTextCache[cacheKey]?.let { cached ->
                    cacheHolder?.putCachedBytes(fileBean, cached)
                    return@runCatching cached
                }
            }

            // 3. 发起网络请求获取下载输入流
            val inputStream = repository.getDownloadInputStream(fileBean.pickCode, fileBean.fileId)
                ?: throw IllegalStateException("未能获取文件下载流")

            val bytes = inputStream.use { it.readBytes() }

            // 4. 回写双级缓存，供后续查看器与终端命令无缝复用
            if (cacheKey.isNotEmpty()) {
                memoryTextCache[cacheKey] = bytes
            }
            cacheHolder?.putCachedBytes(fileBean, bytes)
            bytes
        }
    }

    /**
     * 将原始字节数组解码为单行文本列表（按 LF / CRLF 拆分）
     * 遵循 Unix 标准：若文件尾部包含单个标准换行符，不将其视作额外的空白尾行。
     */
    fun parseLines(bytes: ByteArray): List<String> {
        if (bytes.isEmpty()) return emptyList()
        var content = String(bytes, Charsets.UTF_8)
        if (content.endsWith("\r\n")) {
            content = content.substring(0, content.length - 2)
        } else if (content.endsWith("\n")) {
            content = content.substring(0, content.length - 1)
        }
        return content.lines()
    }
}
