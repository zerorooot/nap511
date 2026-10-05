package github.zerorooot.nap511.terminal.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.zerorooot.nap511.terminal.viewmodel.TerminalLine
import github.zerorooot.nap511.terminal.viewmodel.TerminalLineType

/**
 * 终端单行输出渲染组件 (Terminal Line Row)
 *
 * 遵循极速直通渲染架构：
 * 1. OUTPUT_TEXT 与 HELP 普通文本直接使用 Text 单色渲染，零 AnnotatedString 组装开销；
 * 2. COMMAND（含换行美化）及富文本类型统一委托给 [TerminalStyleParser] 处理；
 * 3. 彻底消除原本泄漏在界面层的字符分割与样式计算逻辑，实现 100% 样式高内聚。
 */
@Composable
fun TerminalLineRow(
    line: TerminalLine,
    modifier: Modifier = Modifier,
    theme: TerminalColorTheme = LocalTerminalTheme.current
) {
    when (line.type) {
        // 普通文本输出（echo, wc, stat, 普通管道等，占绝大多数终端行）
        // 核心直通渲染通道：直接单色渲染，实现极致滚动性能
        TerminalLineType.OUTPUT_TEXT -> {
            Text(
                text = line.text,
                color = TerminalColors.TextPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
            )
        }

        TerminalLineType.HELP -> {
            Text(
                text = line.text,
                color = TerminalColors.TextMuted,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
            )
        }

        // 双行历史命令美化格式（第 1 行上下文路径，第 2 行提示符与命令）
        TerminalLineType.COMMAND if line.text.contains("\n$ ") -> {
            val annotatedString = remember(line.text, theme) {
                TerminalStyleParser.parseCommandLine(line.text, theme)
            }
            Text(
                text = annotatedString,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
            )
        }

        // 富文本输出类型：按显式 TerminalLineType 委托给 TerminalStyleParser 组装样式
        TerminalLineType.OUTPUT_FILE_ENTRY,
        TerminalLineType.OUTPUT_PATH_ENTRY,
        TerminalLineType.OUTPUT_LONG_LISTING,
        TerminalLineType.OUTPUT_FIND_CATEGORY,
        TerminalLineType.OUTPUT_ANSI -> {
            val annotatedString = remember(line.text, line.type, theme) {
                TerminalStyleParser.parseLine(line.text, line.type, theme)
            }
            Text(
                text = annotatedString,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
            )
        }

        @Suppress("DEPRECATION")
        TerminalLineType.OUTPUT -> {
            val annotatedString = remember(line.text, theme) {
                TerminalStyleParser.parseLine(line.text, TerminalLineType.OUTPUT, theme)
            }
            Text(
                text = annotatedString,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
            )
        }

        TerminalLineType.SYSTEM -> {
            Text(
                text = line.text,
                color = TerminalColors.System,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
            )
        }

        TerminalLineType.COMMAND -> {
            Text(
                text = line.text,
                color = TerminalColors.Command,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
            )
        }

        TerminalLineType.ERROR -> {
            Text(
                text = line.text,
                color = TerminalColors.Error,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
            )
        }

        TerminalLineType.PROMPT -> {
            Text(
                text = line.text,
                color = TerminalColors.PromptConfirm,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
            )
        }
    }
}
