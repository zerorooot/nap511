package github.zerorooot.nap511.terminal.engine

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 终端行编辑器实用工具
 *
 * 封装类 Linux / Readline 文本编辑与光标定位的纯函数逻辑，保持高内聚与低耦合，
 * 方便单独进行单元测试并在不同组件间复用。
 */
object TerminalLineEditor {

    /**
     * 将光标移动到行首 (Home / Ctrl+A)
     */
    fun moveCursorHome(value: TextFieldValue): TextFieldValue {
        return value.copy(selection = TextRange(0))
    }

    /**
     * 将光标移动到行尾 (End / Ctrl+E)
     */
    fun moveCursorEnd(value: TextFieldValue): TextFieldValue {
        return value.copy(selection = TextRange(value.text.length))
    }

    /**
     * 光标左移一个字符 (Left Arrow)
     */
    fun moveCursorLeft(value: TextFieldValue): TextFieldValue {
        val newPos = (value.selection.start - 1).coerceAtLeast(0)
        return value.copy(selection = TextRange(newPos))
    }

    /**
     * 光标右移一个字符 (Right Arrow)
     */
    fun moveCursorRight(value: TextFieldValue): TextFieldValue {
        val newPos = (value.selection.end + 1).coerceAtMost(value.text.length)
        return value.copy(selection = TextRange(newPos))
    }

    /**
     * 光标按词向前（左）跳跃 (Ctrl+Left / Alt+B)
     */
    fun moveWordBackward(value: TextFieldValue): TextFieldValue {
        val text = value.text
        var pos = value.selection.start.coerceIn(0, text.length)
        if (pos == 0) return value

        // 1. 跳过光标左侧的连续非字母数字字符（空格、标点符号）
        while (pos > 0 && (!text[pos - 1].isLetterOrDigit())) {
            pos--
        }
        // 2. 跳过连续字母数字字符（单词主体）
        while (pos > 0 && text[pos - 1].isLetterOrDigit()) {
            pos--
        }
        return value.copy(selection = TextRange(pos))
    }

    /**
     * 光标按词向后（右）跳跃 (Ctrl+Right / Alt+F)
     */
    fun moveWordForward(value: TextFieldValue): TextFieldValue {
        val text = value.text
        var pos = value.selection.end.coerceIn(0, text.length)
        val len = text.length
        if (pos >= len) return value

        // 1. 跳过光标右侧的连续非字母数字字符（空格、标点符号）
        while (pos < len && (!text[pos].isLetterOrDigit())) {
            pos++
        }
        // 2. 跳过连续字母数字字符（单词主体）
        while (pos < len && text[pos].isLetterOrDigit()) {
            pos++
        }
        return value.copy(selection = TextRange(pos))
    }

    /**
     * 删除光标到行首的内容 (Ctrl+U)
     * 保留光标右侧的文本，光标置于 0
     */
    fun deleteToBeginning(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val cursor = value.selection.start.coerceIn(0, text.length)
        if (cursor == 0) return value

        val remainingText = text.substring(cursor)
        return TextFieldValue(text = remainingText, selection = TextRange(0))
    }

    /**
     * 删除光标到行尾的内容 (Ctrl+K)
     * 保留光标左侧的文本，光标位置不变
     */
    fun deleteToEnd(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val cursor = value.selection.start.coerceIn(0, text.length)
        if (cursor >= text.length) return value

        val remainingText = text.substring(0, cursor)
        return TextFieldValue(text = remainingText, selection = TextRange(cursor))
    }

    /**
     * 向前删除一个单词 (Ctrl+W / Alt+Backspace)
     * 从光标处往左删除直到词首
     */
    fun deleteWordBackward(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val cursor = value.selection.start.coerceIn(0, text.length)
        if (cursor == 0) return value

        var start = cursor
        // 1. 跳过连续空白
        while (start > 0 && text[start - 1].isWhitespace()) {
            start--
        }
        // 2. 跳过单词主体
        while (start > 0 && !text[start - 1].isWhitespace()) {
            start--
        }

        val newText = text.substring(0, start) + text.substring(cursor)
        return TextFieldValue(text = newText, selection = TextRange(start))
    }

    /**
     * 向后删除一个单词 (Alt+D)
     * 从光标处往右删除直到词尾
     */
    fun deleteWordForward(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val cursor = value.selection.start.coerceIn(0, text.length)
        val len = text.length
        if (cursor >= len) return value

        var end = cursor
        // 1. 跳过连续空白
        while (end < len && text[end].isWhitespace()) {
            end++
        }
        // 2. 跳过单词主体
        while (end < len && !text[end].isWhitespace()) {
            end++
        }

        val newText = text.substring(0, cursor) + text.substring(end)
        return TextFieldValue(text = newText, selection = TextRange(cursor))
    }

    /**
     * 向后删除单个字符 (Ctrl+D 且非空时)
     */
    fun deleteCharacterForward(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val cursor = value.selection.start.coerceIn(0, text.length)
        if (cursor >= text.length) return value

        val newText = text.substring(0, cursor) + text.substring(cursor + 1)
        return TextFieldValue(text = newText, selection = TextRange(cursor))
    }

    /**
     * 在光标处插入文本 (支持普通字符、符号或 Alt+. 提取的参数)
     */
    fun insertTextAtCursor(value: TextFieldValue, insertString: String): TextFieldValue {
        val text = value.text
        val cursor = value.selection.start.coerceIn(0, text.length)
        val newText = text.substring(0, cursor) + insertString + text.substring(cursor)
        val newCursor = cursor + insertString.length
        return TextFieldValue(text = newText, selection = TextRange(newCursor))
    }

    /**
     * 计算幽灵文本 (Ghost Text) 的下一个单词片段 (用于 Ctrl+Right / Alt+F 渐进式采纳建议)
     */
    fun extractNextWord(ghostText: String): String {
        if (ghostText.isEmpty()) return ""
        var pos = 0
        val len = ghostText.length

        // 1. 跳过前导的非字母数字字符（如空格、标点符号）
        while (pos < len && (!ghostText[pos].isLetterOrDigit())) {
            pos++
        }
        // 2. 跳过单词主体（字母数字字符）
        while (pos < len && ghostText[pos].isLetterOrDigit()) {
            pos++
        }
        return ghostText.substring(0, pos)
    }

    /**
     * 从上一条历史命令中提取最后一个参数 (Alt+.)
     * 基于 Lexer.tokenizeWithQuoteMask 进行精确参数解析
     */
    fun extractLastArgument(lastCommand: String?): String? {
        if (lastCommand.isNullOrBlank()) return null
        val tokenResult = Lexer.tokenizeWithQuoteMask(lastCommand)
        return if (tokenResult is TokenizeResult.Success) {
            tokenResult.tokens.lastOrNull()?.text
        } else {
            null
        }
    }

    /**
     * 当文本增加 1 个字符时，准确找出被插入的新字符（兼容各输入法光标延迟与文本追加）
     */
    fun findSingleInsertedChar(oldText: String, newText: String): Char? {
        if (newText.length != oldText.length + 1) return null
        var diffIndex = 0
        while (diffIndex < oldText.length && oldText[diffIndex] == newText[diffIndex]) {
            diffIndex++
        }
        return newText[diffIndex]
    }
}
