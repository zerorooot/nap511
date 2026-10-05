package github.zerorooot.nap511.terminal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
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
import kotlin.time.Duration.Companion.milliseconds

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
    val onCtrlLeft: () -> Unit = {},
    val onCtrlRight: () -> Unit = {},
    val onAltB: () -> Unit = {},
    val onAltF: () -> Unit = {},
    val onAltD: () -> Unit = {},
    val onAltBackspace: () -> Unit = {},
    val onAltDot: () -> Unit = {}
)

@Composable
fun GhostTextField(
    modifier: Modifier = Modifier,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    ghostText: String,
    prompt: String = "",
    contextPrompt: String = "",
    promptSign: String = if (contextPrompt.isNotEmpty()) "$ " else prompt,
    isWaitingConfirmation: Boolean = false,
    onSubmit: () -> Unit,
    onTab: () -> Unit = {},
    onAcceptGhostText: () -> Unit = {},
    onArrowUp: () -> Unit,
    onArrowDown: () -> Unit,
    hardwareKeyActions: TerminalHardwareKeyActions = TerminalHardwareKeyActions(),
    focusRequester: FocusRequester,
    onRequestScrollToBottom: () -> Unit = {},
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    val textStyle = TextStyle(
        color = TerminalColors.TextPrimary,
        fontSize = 14.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 20.sp
    )

    val promptColor = if (isWaitingConfirmation) TerminalColors.PromptConfirm else TerminalColors.Prompt
    val keyboardController = LocalSoftwareKeyboardController.current

    var hasBeenFocused by remember { mutableStateOf(false) }

    val textFieldInteractionSource = remember { MutableInteractionSource() }

    // 【关键机制 - 请勿删除】：
    // 监听 BasicTextField 内部的点击与抬起交互。当用户直接点击输入行文本或光标位置时，
    // 外层容器的手势会被 BasicTextField 自身消费，导致外层点击事件无法触发吸底。
    // 此处监听 PressInteraction.Release，确保点击输入文本框时也能通知外部立即滚动并吸底。
    LaunchedEffect(textFieldInteractionSource) {
        textFieldInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                onRequestScrollToBottom()
            }
        }
    }

    // 注意：请勿在此处添加 LaunchedEffect(Unit) 挂载呼起键盘！
    // GhostTextField 作为 LazyColumn 的末尾项，当用户向上浏览历史输出时会离屏被销毁回收；
    // 当用户在键盘收起状态下滑回底部时，GhostTextField 会重新挂载进视口。
    // 如果在此处挂载时自动 requestFocus/show()，会导致滑回底部时误弹起软键盘。
    // 页面初次进入时的键盘唤起由宿主 TerminalScreen 统一调度。

    val requestFocusAndMoveCursorToEnd = {
        if (value.text.isNotEmpty() && (value.selection.start != value.text.length || value.selection.end != value.text.length)) {
            onValueChange(value.copy(selection = TextRange(value.text.length)))
        }
        onRequestScrollToBottom()
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val submitAndKeepKeyboard = {
        onSubmit()
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                requestFocusAndMoveCursorToEnd()
            }
            .padding(vertical = 4.dp)
    ) {
        // 第一行：上下文完整路径信息（仅在非确认模式且上下文非空时展示）
        if (!isWaitingConfirmation && contextPrompt.isNotEmpty()) {
            Text(
                text = contextPrompt,
                color = TerminalColors.System, // 高亮青蓝终端配色
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
            )
        }

        // 第二行（专属输入行）：提示符号 + 独占满宽输入框与幽灵预测补全
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // 引导提示符号（如 "$ " 或确认模式 "confirm (yes/no): "）
            Text(
                text = promptSign,
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
                // 输入框：支持多行自然折行排版 (maxLines = 5)，避免超长命令截断无法查看
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(TerminalColors.Prompt),
                    singleLine = false,
                    maxLines = 5,
                    interactionSource = textFieldInteractionSource,
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
                        // 【关键机制 - 请勿在失焦时自动抢回焦点】：
                        // 1. 终端历史输出被 SelectionContainer 包裹，用户长按选中文本时焦点会转移给 SelectionContainer。
                        //    如果此处在失焦时强行 requestFocus() 抢回焦点，会导致：
                        //    a) 触发 Compose 的 bringIntoView 自动向下滚动露出输入框，出现“长按时界面向下回滑”；
                        //    b) 强行打断 SelectionContainer 的焦点持有，导致刚生成的文本选区瞬间被 onRelease() 销毁。
                        // 2. 软键盘的弹出与收起由 TerminalScreen 的外层点击与 IME 状态统一控制；
                        // 3. onFocusChange 会向外部同步焦点状态，供父容器在手指按下第一时间剥离焦点，避免长按事件失效。
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                hasBeenFocused = true
                            }
                            onFocusChange?.invoke(focusState.isFocused)
                        }
                        .onKeyEvent { event ->
                            // 1. 优先分发外接物理键盘的 Ctrl / Alt 组合键
                            if (handleHardwareShortcutKeyEvent(event, hardwareKeyActions)) {
                                return@onKeyEvent true
                            }

                            // 2. 仅在 KeyDown 时响应，防止物理键盘单次敲击触发两次
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

                            // 外接键盘回车处理：Shift+Enter 允许换行，单独 Enter 执行提交
                            when (event.key) {
                                Key.Enter, Key.NumPadEnter -> {
                                    if (event.isShiftPressed) {
                                        false
                                    } else {
                                        submitAndKeepKeyboard()
                                        true
                                    }
                                }

                                Key.Tab -> {
                                    onTab()
                                    true
                                }

                                Key.MoveEnd, Key.DirectionRight -> {
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
                // 仅当光标位于末尾且存在建议时渲染在末尾，保持字体度量与折行完全一致
                if (ghostText.isNotEmpty() && value.selection.end == value.text.length) {
                    Text(
                        text = buildAnnotatedString {
                            // 前缀使用透明色占位，保证排版与折行位置与用户已输入文本完全一致
                            withStyle(style = SpanStyle(color = Color.Transparent)) {
                                append(value.text)
                            }
                            // 后缀以浅灰淡色展示幽灵文本
                            withStyle(style = SpanStyle(color = TerminalColors.GhostText, fontWeight = FontWeight.Normal)) {
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
            Key.DirectionLeft -> { actions.onCtrlLeft(); true }
            Key.DirectionRight -> { actions.onCtrlRight(); true }
            else -> false
        }
    }

    if (event.isAltPressed) {
        return when (event.key) {
            Key.B -> { actions.onAltB(); true }
            Key.F -> { actions.onAltF(); true }
            Key.D -> { actions.onAltD(); true }
            Key.DirectionLeft -> { actions.onAltB(); true }
            Key.DirectionRight -> { actions.onAltF(); true }
            Key.Backspace -> { actions.onAltBackspace(); true }
            Key.Period -> { actions.onAltDot(); true }
            else -> false
        }
    }

    return false
}
