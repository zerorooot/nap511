package github.zerorooot.nap511.terminal.context

import github.zerorooot.nap511.bean.PathBean

/**
 * 网盘根目录常量配置与标准模型定义
 */
object TerminalPathConstants {
    const val ROOT_CID = "0"
    const val ROOT_NAME = "根目录"
    const val ROOT_DISPLAY_PATH = "/根目录"

    /**
     * 115 网盘顶级根目录节点标准 PathBean
     */
    val ROOT_PATH_BEAN = PathBean(cid = ROOT_CID, name = ROOT_NAME, pid = "0")
}

/**
 * 结构化终端路径描述符 (Terminal Path Descriptor)
 *
 * 站在网盘应用全局视角，将终端输入的原始路径字符串解析为强类型对象，
 * 消除零散硬编码的 startsWith、split、endsWith、substring 等操作，
 * 提升代码的高内聚、低耦合与高复用度。
 *
 * @param raw 原始输入的路径字符串（已去除两端首尾空白字符）
 * @param isAbsolute 是否为绝对路径（以 '/'、'~' 开头或以 '根目录' 顶级标识开头）
 * @param hasTrailingSlash 输入末尾是否显式包含 '/'（表明调用方期望严格匹配目录）
 * @param segments 规范化后的路径各级分段（已过滤空分段、当前目录 '.' 及开头的 '根目录' / '~' 冗余前缀）
 * @param targetName 目标文件名或目录名（即 segments 最后一级；若为空或根目录则为 ROOT_NAME）
 * @param parentPathString 目标所在父路径的字符串表达（用于逐级解析父目录）
 */
data class TerminalPath(
    val raw: String,
    val isAbsolute: Boolean,
    val hasTrailingSlash: Boolean,
    val segments: List<String>,
    val targetName: String,
    val parentPathString: String
) {
    /**
     * 是否指向网盘顶级根目录（如 "/"、"~"、"/根目录" 或空绝对输入）
     */
    val isRoot: Boolean
        get() = isAbsolute && segments.isEmpty()

    /**
     * 是否仅指代当前工作目录（如 "" 或 "."）
     */
    val isCurrentDirectory: Boolean
        get() = !isAbsolute && segments.isEmpty()

    /**
     * 是否包含多级路径分隔
     */
    val isMultiSegment: Boolean
        get() = segments.size > 1

    companion object {
        /**
         * 解析用户输入的任意路径字符串为结构化 [TerminalPath]
         *
         * 防御性清洗：剥离可能因管道传输残留的不可见 NUL 控制字符 (\u0000)，
         * 作为全系统路径解析的统一安全屏障，杜绝控制字符污染路径匹配。
         */
        fun parse(input: String): TerminalPath {
            val trimmed = input.replace("\u0000", "").trim()
            if (trimmed.isEmpty() || trimmed == ".") {
                return TerminalPath(
                    raw = trimmed,
                    isAbsolute = false,
                    hasTrailingSlash = false,
                    segments = emptyList(),
                    targetName = "",
                    parentPathString = ""
                )
            }

            if (trimmed == "/" || trimmed == "~" || trimmed == "/根目录" || trimmed == "/根目录/") {
                return TerminalPath(
                    raw = trimmed,
                    isAbsolute = true,
                    hasTrailingSlash = trimmed.endsWith("/"),
                    segments = emptyList(),
                    targetName = TerminalPathConstants.ROOT_NAME,
                    parentPathString = ""
                )
            }

            val hasTrailing = trimmed.endsWith("/")
            val isAbs =
                trimmed.startsWith("/") || trimmed.startsWith("~/") || trimmed == TerminalPathConstants.ROOT_NAME || trimmed.startsWith(
                    "${TerminalPathConstants.ROOT_NAME}/"
                )

            var rawSegments = trimmed.split("/").filter { it.isNotEmpty() && it != "." }
            // 如果是绝对路径且首段为 "根目录" 或 "~"，剥离该冗余标识（因为 CID "0" 即代表根目录）
            if (isAbs && rawSegments.firstOrNull() == TerminalPathConstants.ROOT_NAME) {
                rawSegments = rawSegments.drop(1)
            }
            if (isAbs && rawSegments.firstOrNull() == "~") {
                rawSegments = rawSegments.drop(1)
            }

            val target =
                rawSegments.lastOrNull() ?: if (isAbs) TerminalPathConstants.ROOT_NAME else ""
            val parentSegments = if (rawSegments.size > 1) rawSegments.dropLast(1) else emptyList()
            val parentPathStr = when {
                parentSegments.isEmpty() && isAbs -> TerminalPathConstants.ROOT_DISPLAY_PATH
                parentSegments.isEmpty() -> ""
                isAbs -> TerminalPathConstants.ROOT_DISPLAY_PATH + "/" + parentSegments.joinToString(
                    "/"
                )

                else -> parentSegments.joinToString("/")
            }

            return TerminalPath(
                raw = trimmed,
                isAbsolute = isAbs,
                hasTrailingSlash = hasTrailing,
                segments = rawSegments,
                targetName = target,
                parentPathString = parentPathStr
            )
        }

        /**
         * 拆分自动补全或通配符场景中的 (父目录前缀, 当前编辑前缀/文件名)
         * 例如 "sub/mov" -> Pair("sub/", "mov")；"mov" -> Pair("", "mov")
         */
        fun splitParentAndPrefix(input: String): Pair<String, String> {
            val lastSlash = input.lastIndexOf('/')
            return if (lastSlash != -1) {
                Pair(input.substring(0, lastSlash + 1), input.substring(lastSlash + 1))
            } else {
                Pair("", input)
            }
        }
    }
}

/**
 * 将 List<PathBean> 面包屑链表转为规范化的显示路径字符串
 * 若链表为空则返回 Unix 根路径 "/"；若包含面包屑节点则拼接为 "/根目录/Movies/Action" 或 "/MyFolder"
 */
fun List<PathBean>.toDisplayPath(): String {
    if (isEmpty()) return "/"
    val names = map { it.name }.filter { it.isNotEmpty() }
    if (names.isEmpty()) return "/"
    return "/" + names.joinToString("/")
}

/**
 * 返回当前所处目录的 CID
 */
fun List<PathBean>.currentCid(): String =
    lastOrNull()?.cid?.ifEmpty { TerminalPathConstants.ROOT_CID } ?: TerminalPathConstants.ROOT_CID

/**
 * 返回当前所处目录的名称
 */
fun List<PathBean>.currentName(): String =
    lastOrNull()?.name ?: TerminalPathConstants.ROOT_NAME

/**
 * 返回上一级父目录的 CID（若当前已处于根目录则返回 null）
 */
fun List<PathBean>.parentCid(): String? =
    if (size > 1) this[size - 2].cid else null

/**
 * 向上一级（cd ..）：弹出末尾层级，但始终保留根目录
 */
fun List<PathBean>.cdUp(): List<PathBean> =
    if (size > 1) dropLast(1) else listOf(TerminalPathConstants.ROOT_PATH_BEAN)

/**
 * 向下一级（进入子目录）：追加新的 PathBean
 */
fun List<PathBean>.cdDown(cid: String, name: String): List<PathBean> {
    val pid = currentCid()
    return this + PathBean(cid = cid, name = name, pid = pid)
}
