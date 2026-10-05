package github.zerorooot.nap511.terminal.ui

import androidx.compose.ui.graphics.Color

/**
 * 终端色彩系统统一常量池（Terminal Design Palette）
 *
 * 集中管理整个终端控制台（Terminal）模块的所有色彩常量，彻底杜绝 UI 渲染、组件布局及主题解析中的色彩魔数。
 * 遵循沉浸式深色终端（Dark Monokai / Cyberpunk Minimal）设计规范：
 * 1. 结构与容器背景色（Background & Surface）
 * 2. 文本与排版前景色（Typography & Text）
 * 3. 命令行状态与提示符（Terminal States & Prompts）
 * 4. 键盘悬浮辅助栏按键色彩（Accessory Bar Keys）
 * 5. 115 业务文件类型色彩（与 res/drawable 目录图标 fillColor 严格对齐）
 * 6. 标准 ANSI 16 色调色板（SGR 30-37 与 90-97 转义序列）
 */
object TerminalColors {

    // =========================================================================
    // 1. 结构与容器背景色（Background & Surface）
    // =========================================================================

    /** 终端主工作区与主窗口底色：极深黑 */
    val Background: Color = Color(0xFF101010)

    /** 终端顶部导航栏背景色：微亮沉浸黑 */
    val SurfaceTopBar: Color = Color(0xFF181818)

    /** 键盘辅助栏（Accessory Bar）容器背景色 */
    val AccessoryBarBackground: Color = Color(0xFF1E1E1E)


    // =========================================================================
    // 2. 文本与排版前景色（Typography & Text）
    // =========================================================================

    /** 主要前景色：纯净亮灰白（终端主文本、帮助信息、顶栏标题） */
    val TextPrimary: Color = Color(0xFFECEFF1)

    /** 次要前景色：石板蓝灰（用户已执行历史命令、次要图标与操作副标题） */
    val TextSecondary: Color = Color(0xFFB0BEC5)

    /** 弱化前景色：中性中灰（普通无扩展名文件、常规无类型输出） */
    val TextMuted: Color = Color(0xFFBABABA)

    /** 幽灵预览文本色：灰暗中灰（输入框内 Tab 自动补全建议候选提示） */
    val GhostText: Color = Color(0xFF888888)


    // =========================================================================
    // 3. 命令行状态与提示符（Terminal States & Prompts）
    // =========================================================================

    /** 常规提示符及活动光标：荧光亮绿（对应命令行 `$ ` 提示与呼吸光标） */
    val Prompt: Color = Color(0xFF69F0AE)

    /** 交互确认提示符：明亮黄（对应 rm 危险操作二次确认 `confirm (yes/no): `） */
    val PromptConfirm: Color = Color(0xFFFFD54F)

    /** 系统与路径上下文信息色：高亮青蓝（对应双行提示符第一行的目录上下文与系统欢迎消息） */
    val System: Color = Color(0xFF4DD0E1)

    /** 命令行输入历史文本色：与 TextSecondary 保持一致的蓝灰 */
    val Command: Color = Color(0xFFB0BEC5)

    /** 错误与失败告警色：醒目珊瑚红（对应命令未找到、异常失败及语法报错） */
    val Error: Color = Color(0xFFEF5350)

    /** 详细列表元数据与目录路径前缀色：淡化灰蓝（对应 ls -l 权限位、大小及多级目录前缀） */
    val Metadata: Color = Color(0xFF78909C)


    // =========================================================================
    // 4. 键盘悬浮辅助栏按键色彩（Accessory Bar Keys）
    // =========================================================================

    /** 辅助按键常规状态背景色 */
    val KeyDefault: Color = Color(0xFF2C2C2C)

    /** 辅助按键强调功能状态背景色（如 Ctrl、Alt、Tab、Esc 等特殊功能键） */
    val KeyAccent: Color = Color(0xFF383838)

    /** 辅助按键按下与连按即时反馈背景色 */
    val KeyPressed: Color = Color(0xFF424242)

    /** 辅助按键锁定与激活高亮背景色（如 Ctrl/Alt 开启锁定态） */
    val KeyActive: Color = Color(0xFF00ACC1)

    /** 辅助按键常规文本文字色 */
    val KeyTextDefault: Color = Color(0xFFE0E0E0)

    /** 辅助按键激活状态高反差深色文字 */
    val KeyTextActive: Color = Color(0xFF101010)

    /** 辅助按键连按与按下反馈高亮文本色（鲜绿） */
    val KeyTextPressed: Color = Color(0xFF69F0AE)

    /** 辅助按键特殊功能键文本色（淡青蓝） */
    val KeyTextAccent: Color = Color(0xFF81D4FA)


    // =========================================================================
    // 5. 115 业务文件类型配色（File Types，与 res/drawable 目录图标完全一致）
    // =========================================================================

    /** 文件夹：暖金橙色（对应 folder.xml #FFA726） */
    val FileFolder: Color = Color(0xFFFFA726)

    /** 视频媒体：活力红色（对应 mp4.xml #EF5350） */
    val FileVideo: Color = Color(0xFFEF5350)

    /** 音频媒体：橙红色（对应 mp3.xml #FF7043） */
    val FileAudio: Color = Color(0xFFFF7043)

    /** 图像文件：蓝绿青色（对应 png.xml #26A69A） */
    val FileImage: Color = Color(0xFF26A69A)

    /** 压缩包文件：棕褐色（对应 zip.xml #8D6E63） */
    val FileArchive: Color = Color(0xFF8D6E63)

    /** Android 应用安装包：清爽绿色（对应 apk.xml #66BB6A） */
    val FileApk: Color = Color(0xFF66BB6A)

    /** 可执行程序与脚本：靛蓝紫色（对应 exe.xml #5C6BC0） */
    val FileExecutable: Color = Color(0xFF5C6BC0)

    /** 纯文本与文档：明亮蓝色（对应 txt.xml #42A5F5） */
    val FileDocument: Color = Color(0xFF42A5F5)

    /** 网页文件：湖水青色（对应 web.xml #00BCD4） */
    val FileWeb: Color = Color(0xFF00BCD4)

    /** 镜像与系统光盘：紫罗兰色（对应 iso.xml #7E57C2） */
    val FileIso: Color = Color(0xFF7E57C2)

    /** BT 种子文件：森林深绿（对应 torrent.xml #43A047） */
    val FileTorrent: Color = Color(0xFF43A047)

    /** 其他通用未知文件：中性浅灰色（对应 other.xml #BABABA） */
    val FileOther: Color = Color(0xFFBABABA)


    // =========================================================================
    // 6. 补全提示条与候选项标签（Completion Bar & Chips）
    // =========================================================================

    /** 候选标签常规底色：深灰黑 */
    val ChipBackground: Color = Color(0xFF242424)

    /** 候选标签高亮选中底色：深蓝灰 */
    val ChipBackgroundSelected: Color = Color(0xFF263238)

    /** 候选标签常规边框色 */
    val ChipBorder: Color = Color(0xFF383838)

    /** 候选标签高亮选中边框色：荧光亮绿 */
    val ChipBorderSelected: Color = Color(0xFF69F0AE)

    /** 弱化图标色（对应补全栏关闭叉号等辅助控件） */
    val IconMuted: Color = Color(0xFF9E9E9E)


    // =========================================================================
    // 7. 标准 ANSI 16 色调色板（ANSI SGR 30-37 与 90-97）
    // =========================================================================

    object Ansi {
        /** ANSI 30: 黑色 (Black) */
        val Black: Color = Color(0xFF212121)

        /** ANSI 31: 红色 (Red) */
        val Red: Color = Color(0xFFEF5350)

        /** ANSI 32: 绿色 (Green) */
        val Green: Color = Color(0xFF66BB6A)

        /** ANSI 33: 黄色 (Yellow) */
        val Yellow: Color = Color(0xFFFFA726)

        /** ANSI 34: 蓝色 (Blue) */
        val Blue: Color = Color(0xFF42A5F5)

        /** ANSI 35: 品红/洋红 (Magenta) */
        val Magenta: Color = Color(0xFFAB47BC)

        /** ANSI 36: 青色 (Cyan) */
        val Cyan: Color = Color(0xFF26A69A)

        /** ANSI 37: 白色 (White) */
        val White: Color = Color(0xFFECEFF1)

        /** ANSI 90: 明亮黑/暗灰 (Bright Black / Gray) */
        val BrightBlack: Color = Color(0xFF78909C)

        /** ANSI 91: 明亮红 (Bright Red) */
        val BrightRed: Color = Color(0xFFFF7043)

        /** ANSI 92: 明亮绿 (Bright Green) */
        val BrightGreen: Color = Color(0xFFB2FF59)

        /** ANSI 93: 明亮黄 (Bright Yellow) */
        val BrightYellow: Color = Color(0xFFFFD54F)

        /** ANSI 94: 明亮蓝 (Bright Blue) */
        val BrightBlue: Color = Color(0xFF90CAF9)

        /** ANSI 95: 明亮品红 (Bright Magenta) */
        val BrightMagenta: Color = Color(0xFFE040FB)

        /** ANSI 96: 明亮青 (Bright Cyan) */
        val BrightCyan: Color = Color(0xFF4DD0E1)

        /** ANSI 97: 明亮白 (Bright White) */
        val BrightWhite: Color = Color(0xFFFFFFFF)
    }
}
