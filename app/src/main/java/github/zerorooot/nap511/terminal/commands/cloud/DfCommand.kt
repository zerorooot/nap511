package github.zerorooot.nap511.terminal.commands.cloud

import com.google.gson.Gson
import github.zerorooot.nap511.bean.RemainingSpaceBean
import github.zerorooot.nap511.terminal.commands.util.TableAlignment
import github.zerorooot.nap511.terminal.commands.util.TableFormatter
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText
import github.zerorooot.nap511.util.formatFileSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

/**
 * 网盘空间配额查看命令（df）
 *
 * 查询并展示 115 网盘的总容量、已用空间、剩余空间及使用百分比。
 * 支持 -h 参数以人类可读的容量单位（如 GB、TB）进行展示。
 */
class DfCommand : TerminalCommand {

    override val name: String = "df"

    override val description: String = "查看网盘容量配额、已用空间与剩余空间"

    override val usage: String = "df [-h]"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-h", "人性化容量单位显示")
    )

    override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val isHuman = ast.hasFlag("-h")

        try {
            val json = ctx.fileRepository.remainingSpace(1)
            val spaceInfoJson = json.getAsJsonObject("data")?.getAsJsonObject("space_info")
            if (spaceInfoJson != null) {
                val bean = Gson().fromJson(spaceInfoJson, RemainingSpaceBean::class.java)
                val totalBytes = bean.total.size.toDouble().coerceAtLeast(1.0)
                val usedBytes = bean.use.size.toDouble()
                val pct = ((usedBytes / totalBytes) * 100).toInt()

                val totalStr = if (isHuman) {
                    bean.total.sizeFormat.ifEmpty { bean.total.size.formatFileSize() }
                } else {
                    "${bean.total.size} B"
                }
                val usedStr = if (isHuman) {
                    bean.use.sizeFormat.ifEmpty { bean.use.size.formatFileSize() }
                } else {
                    "${bean.use.size} B"
                }
                val availStr = if (isHuman) {
                    bean.remain.sizeFormat.ifEmpty { bean.remain.size.formatFileSize() }
                } else {
                    "${bean.remain.size} B"
                }

                val table = TableFormatter.Builder()
                    .addColumn("Filesystem", TableAlignment.LEFT, minWidth = 18)
                    .addColumn("Size", TableAlignment.RIGHT, minWidth = 10)
                    .addColumn("Used", TableAlignment.RIGHT, minWidth = 10)
                    .addColumn("Avail", TableAlignment.RIGHT, minWidth = 10)
                    .addColumn("Use%", TableAlignment.RIGHT, minWidth = 5)
                    .addColumn("Mounted on", TableAlignment.LEFT, minWidth = 10)
                    .addRow("115:CloudDrive", totalStr, usedStr, availStr, "$pct%", "/")
                    .build()
                for (line in table) {
                    emitText(line)
                }
            } else {
                emitError("df: 无法解析网盘空间配额数据")
            }
        } catch (e: Exception) {
            emitError("df: 获取网盘配额失败: ${e.message}")
        }
    }
}
