package github.zerorooot.nap511.terminal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 外接物理键盘快捷键动作集合
 * 封装 Ctrl 和 Alt 系列快捷键处理回调，保持参数高内聚低耦合
 */
data class TerminalHardwareKeyActions(
    val onCtrlC: () -> Unit = {},
    val onCtrlU: () -> Unit = {},
    val onCtrlK: () -> Unit = {},
    val onCtrlW: () -> Unit = {},
    val onCtrlL: () -> Unit = {},
    val onCtrlA: () -> Unit = {},
    val onCtrlE: () -> Unit = {},
    val onCtrlD: () -> Unit = {},
    val onAltB: () -> Unit = {},
    val onAltF: () -> Unit = {},
    val onAltD: () -> Unit = {},
    val onAltBackspace: () -> Unit = {},
    val onAltDot: () -> Unit = {}
)

@Composable
fun GhostTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    ghostText: String,
    prompt: String,
    isWaitingConfirmation: Boolean,
    onSubmit: () -> Unit,
    onTab: () -> Unit = {},
    onAcceptGhostText: () -> Unit = {},
    onArrowUp: () -> Unit,
    onArrowDown: () -> Unit,
    hardwareKeyActions: TerminalHardwareKeyActions = TerminalHardwareKeyActions(),
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val textStyle = TextStyle(
        color = Color(0xFFECEFF1),
        fontSize = 14.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 20.sp
    )

    val promptColor = if (isWaitingConfirmation) Color(0xFFFFD54F) else Color(0xFF69F0AE)
    val keyboardController = LocalSoftwareKeyboardController.current

    val requestFocusAndMoveCursorToEnd = {
        if (value.selection.start != value.text.length || value.selection.end != value.text.length) {
            onValueChange(value.copy(selection = TextRange(value.text.length)))
        }
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val submitAndKeepKeyboard = {
        onSubmit()
        requestFocusAndMoveCursorToEnd()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                requestFocusAndMoveCursorToEnd()
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 终端提示符
        Text(
            text = prompt,
            color = promptColor,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            lineHeight = 20.sp
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    requestFocusAndMoveCursorToEnd()
                }
        ) {
            // 输入框
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = textStyle,
                cursorBrush = SolidColor(Color(0xFF69F0AE)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(
                    onSend = { submitAndKeepKeyboard() },
                    onGo = { submitAndKeepKeyboard() },
                    onDone = { submitAndKeepKeyboard() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        // 1. 优先分发外接物理键盘的 Ctrl / Alt 组合键
                        if (handleHardwareShortcutKeyEvent(event, hardwareKeyActions)) {
                            return@onKeyEvent true
                        }

                        // 2. 常规按键处理 (Tab, End, 方向键等) - 仅在 KeyDown 时响应，防止物理键盘单次敲击触发两次
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

                        when (event.key) {
                            Key.Tab -> {
                                onTab()
                                true
                            }
                            Key.MoveEnd -> {
                                if (value.selection.end == value.text.length && ghostText.isNotEmpty()) {
                                    onAcceptGhostText()
                                    true
                                } else {
                                    false
                                }
                            }
                            Key.DirectionRight -> {
                                if (value.selection.end == value.text.length && ghostText.isNotEmpty()) {
                                    onAcceptGhostText()
                                    true
                                } else {
                                    false
                                }
                            }
                            Key.DirectionUp -> {
                                onArrowUp()
                                true
                            }
                            Key.DirectionDown -> {
                                onArrowDown()
                                true
                            }
                            else -> false
                        }
                    }
            )

            // 行内幽灵文本层 (Inline Ghost Text)
            // 仅当光标位于末尾且存在建议时渲染在末尾
            if (ghostText.isNotEmpty() && value.selection.end == value.text.length) {
                Text(
                    text = buildAnnotatedString {
                        // 前缀使用透明色占位，保证宽度与用户已输入文本完全一致
                        withStyle(style = SpanStyle(color = Color.Transparent)) {
                            append(value.text)
                        }
                        // 后缀以浅灰淡色展示幽灵文本
                        withStyle(style = SpanStyle(color = Color(0xFF888888), fontWeight = FontWeight.Normal)) {
                            append(ghostText)
                        }
                    },
                    style = textStyle,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 物理键盘按键事件捕获解析
 */
private fun handleHardwareShortcutKeyEvent(
    event: KeyEvent,
    actions: TerminalHardwareKeyActions
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    if (event.isCtrlPressed) {
        return when (event.key) {
            Key.C -> { actions.onCtrlC(); true }
            Key.U -> { actions.onCtrlU(); true }
            Key.K -> { actions.onCtrlK(); true }
            Key.W -> { actions.onCtrlW(); true }
            Key.L -> { actions.onCtrlL(); true }
            Key.A -> { actions.onCtrlA(); true }
            Key.E -> { actions.onCtrlE(); true }
            Key.D -> { actions.onCtrlD(); true }
            else -> false
        }
    }

    if (event.isAltPressed) {
        return when (event.key) {
            Key.B -> { actions.onAltB(); true }
            Key.F -> { actions.onAltF(); true }
            Key.D -> { actions.onAltD(); true }
            Key.Backspace -> { actions.onAltBackspace(); true }
            Key.Period -> { actions.onAltDot(); true }
            else -> false
        }
    }

    return false
}
