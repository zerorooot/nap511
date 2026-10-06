package github.zerorooot.nap511.terminal.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 粘滞修饰键动作处理契约
 */
interface TerminalModifierActionHandler {
    fun onCtrlC()
    fun onCtrlU()
    fun onCtrlK()
    fun onCtrlW()
    fun onCtrlL()
    fun onCtrlA()
    fun onCtrlE()
    fun onCtrlD()
    fun onAltB()
    fun onAltF()
    fun onAltD()
    fun onAltBackspace()
    fun onAltDot()
}

/**
 * Termux 风格粘滞修饰键状态与分发管理器 (Terminal Key Modifiers)
 *
 * 职责：
 * 1. 管理 Termux 风格的 CTRL 与 ALT 粘滞修饰键状态（互斥开启，点按切换）。
 * 2. 拦截并分发软键盘输入的字符（如开启 CTRL 后按 C 触发 Ctrl+C）。
 * 3. 拦截并分发软键盘退格键（开启 ALT 后按退格触发 Alt+Backspace）。
 * 4. 快捷键触发后或遇到未知字符时安全复位。
 */
class TerminalKeyModifiers {

    /** Termux 风格粘滞修饰键状态：CTRL 是否激活 */
    var isCtrlActive: Boolean by mutableStateOf(false)
        private set

    /** Termux 风格粘滞修饰键状态：ALT 是否激活 */
    var isAltActive: Boolean by mutableStateOf(false)
        private set

    /**
     * 切换 CTRL 粘滞模式状态（互斥：若激活 CTRL 则自动关闭 ALT）
     */
    fun toggleCtrl() {
        isCtrlActive = !isCtrlActive
        if (isCtrlActive) isAltActive = false
    }

    /**
     * 切换 ALT 粘滞模式状态（互斥：若激活 ALT 则自动关闭 CTRL）
     */
    fun toggleAlt() {
        isAltActive = !isAltActive
        if (isAltActive) isCtrlActive = false
    }

    /**
     * 重置所有粘滞修饰键状态为未激活
     */
    fun reset() {
        isCtrlActive = false
        isAltActive = false
    }

    /**
     * 处于粘滞模式时，处理并分发软键盘键入的单个字符
     *
     * @param char 软键盘键入的字符
     * @param handler 动作回调契约
     * @return true 表示字符已被修饰快捷键拦截并消费；false 表示未匹配快捷键并已重置修饰状态
     */
    fun dispatchChar(char: Char, handler: TerminalModifierActionHandler): Boolean {
        if (isCtrlActive) {
            when (char.lowercaseChar()) {
                'c' -> handler.onCtrlC()
                'u' -> handler.onCtrlU()
                'k' -> handler.onCtrlK()
                'w' -> handler.onCtrlW()
                'l' -> handler.onCtrlL()
                'a' -> handler.onCtrlA()
                'e' -> handler.onCtrlE()
                'd' -> handler.onCtrlD()
                else -> {
                    reset()
                    return false
                }
            }
            return true
        } else if (isAltActive) {
            when {
                char.equals('b', ignoreCase = true) -> handler.onAltB()
                char.equals('f', ignoreCase = true) -> handler.onAltF()
                char.equals('d', ignoreCase = true) -> handler.onAltD()
                char == '.' -> handler.onAltDot()
                else -> {
                    reset()
                    return false
                }
            }
            return true
        }
        return false
    }

    /**
     * 处于 ALT 粘滞模式时，拦截软键盘退格事件
     *
     * @param onAltBackspace 退格处理回调
     * @return true 表示已被 Alt+Backspace 拦截并消费；false 表示未处理
     */
    fun dispatchBackspace(onAltBackspace: () -> Unit): Boolean {
        if (isAltActive) {
            onAltBackspace()
            return true
        }
        return false
    }
}
