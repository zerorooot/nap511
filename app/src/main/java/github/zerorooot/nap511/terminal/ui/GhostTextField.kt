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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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

/**
 * 外接物理键盘快捷键动作集合 (Hardware Key Actions)
 * 封装 Ctrl 和 Alt 系列快捷键处理回调，保持参数高内聚低耦合
 */
@Immutable
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

/**
 * 物理键盘快捷键枚举类型
 */
enum class TerminalShortcut {
    CTRL_C, CTRL_U, CTRL_K, CTRL_W, CTRL_L, CTRL_A, CTRL_E, CTRL_D, CTRL_LEFT, CTRL_RIGHT,
    ALT_B, ALT_F, ALT_D, ALT_BACKSPACE, ALT_DOT
}

/**
 * 物理键盘按键事件捕获与分发处理器 (Hardware Key Handler)
 * 独立纯逻辑处理器，方便单元测试对按键分发进行 100% 覆盖验证
 */
object TerminalHardwareKeyHandler {

    /**
     * 将快捷键类型分发执行对应的回调动作
     */
    fun dispatchShortcut(
        shortcut: TerminalShortcut,
        actions: TerminalHardwareKeyActions
    ) {
        when (shortcut) {
            TerminalShortcut.CTRL_C -> actions.onCtrlC()
            TerminalShortcut.CTRL_U -> actions.onCtrlU()
            TerminalShortcut.CTRL_K -> actions.onCtrlK()
            TerminalShortcut.CTRL_W -> actions.onCtrlW()
            TerminalShortcut.CTRL_L -> actions.onCtrlL()
            TerminalShortcut.CTRL_A -> actions.onCtrlA()
            TerminalShortcut.CTRL_E -> actions.onCtrlE()
            TerminalShortcut.CTRL_D -> actions.onCtrlD()
            TerminalShortcut.CTRL_LEFT -> actions.onCtrlLeft()
            TerminalShortcut.CTRL_RIGHT -> actions.onCtrlRight()
            TerminalShortcut.ALT_B -> actions.onAltB()
            TerminalShortcut.ALT_F -> actions.onAltF()
            TerminalShortcut.ALT_D -> actions.onAltD()
            TerminalShortcut.ALT_BACKSPACE -> actions.onAltBackspace()
            TerminalShortcut.ALT_DOT -> actions.onAltDot()
        }
    }

    /**
     * 解析按键事件对应的快捷键语义类型
     */
    fun resolveShortcut(event: KeyEvent): TerminalShortcut? {
        if (event.type != KeyEventType.KeyDown) return null

        if (event.isCtrlPressed) {
            return when (event.key) {
                Key.C -> TerminalShortcut.CTRL_C
                Key.U -> TerminalShortcut.CTRL_U
                Key.K -> TerminalShortcut.CTRL_K
                Key.W -> TerminalShortcut.CTRL_W
                Key.L -> TerminalShortcut.CTRL_L
                Key.A -> TerminalShortcut.CTRL_A
                Key.E -> TerminalShortcut.CTRL_E
                Key.D -> TerminalShortcut.CTRL_D
                Key.DirectionLeft -> TerminalShortcut.CTRL_LEFT
                Key.DirectionRight -> TerminalShortcut.CTRL_RIGHT
                else -> null
            }
        }

        if (event.isAltPressed) {
            return when (event.key) {
                Key.B -> TerminalShortcut.ALT_B
                Key.F -> TerminalShortcut.ALT_F
                Key.D -> TerminalShortcut.ALT_D
                Key.DirectionLeft -> TerminalShortcut.ALT_B
                Key.DirectionRight -> TerminalShortcut.ALT_F
                Key.Backspace -> TerminalShortcut.ALT_BACKSPACE
                Key.Period -> TerminalShortcut.ALT_DOT
                else -> null
            }
        }

        return null
    }

    /**
     * 处理物理键盘 Ctrl / Alt 组合键
     * @return true 表示已消费此事件，false 表示未消费
     */
    fun handleKeyEvent(
        event: KeyEvent,
        actions: TerminalHardwareKeyActions
    ): Boolean {
        val shortcut = resolveShortcut(event) ?: return false
        dispatchShortcut(shortcut, actions)
        return true
    }
}

/**
 * 终端幽灵预测输入行组件 (Ghost Text Field)
 *
 * 核心设计与防反复关键机制：
 * 1. 【关键机制 1 - 请勿删除】：监听 textFieldInteractionSource 上的 PressInteraction.Release，
 *    解决用户直接点击文本框内部时手势被消费而无法触发外层吸底的问题；
 * 2. 【关键机制 2 - 请勿在失焦时自动抢回焦点】：失焦时不自动强行 requestFocus()，
 *    防止破坏用户长按历史输出生成的文本选区以及避免界面异常向下回弹；
 * 3. 行内幽灵文本 (Inline Ghost Text)：基于透明文本前缀严格对齐度量与排版。
 */
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
    isExecuting: Boolean = false,
    onSubmit: () -> Unit,
    onTab: () -> Unit = {},
    onAcceptGhostText: () -> Unit = {},
    onArrowUp: () -> Unit,
    onArrowDown: () -> Unit,
    hardwareKeyActions: TerminalHardwareKeyActions = TerminalHardwareKeyActions(),
    focusRequester: FocusRequester,
    focusTrigger: Long = 0L,
    onFocusConsumed: () -> Unit = {},
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

    // 优雅的响应式焦点同步：当外部发起焦点请求时，若当前组件在视口中（或刚滚入视口挂载完成的一瞬间），
    // 立即精准请求焦点并弹出键盘，彻底替代脆弱的 delay 延时轮询
    LaunchedEffect(focusTrigger) {
        if (focusTrigger > 0L) {
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {
            } finally {
                onFocusConsumed()
            }
        }
    }

    val textFieldInteractionSource = remember { MutableInteractionSource() }

    // 【关键机制 1 - 请勿删除】：
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

    // 【关键机制 - 请勿删除】：注意：请勿在此处添加 LaunchedEffect(Unit) 挂载呼起键盘！
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
            .padding(vertical = if (isExecuting && !isWaitingConfirmation) 0.dp else 4.dp)
    ) {
        // 第一行：上下文完整路径信息（仅在非确认模式、非执行中且上下文非空时展示）
        if (!isWaitingConfirmation && !isExecuting && contextPrompt.isNotEmpty()) {
            Text(
                text = contextPrompt,
                color = TerminalColors.System,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
            )
        }

        // 实际生效的提示符：执行中（非二次确认状态）彻底隐藏，对齐 Unix 终端前台进程独占语义
        val effectivePromptSign = if (isExecuting && !isWaitingConfirmation) "" else promptSign

        // 第二行（专属输入行）：提示符号 + 独占满宽输入框与幽灵预测补全
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // 引导提示符号（如 "$ " 或确认模式 "confirm (yes/no): "）
            if (effectivePromptSign.isNotEmpty()) {
                Text(
                    text = effectivePromptSign,
                    color = promptColor,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                // 输入框：支持多行自然折行排版 (maxLines = 5)，避免超长命令截断无法查看
                BasicTextField(
                    value = value,
                    onValueChange = { newValue ->
                        // 当用户输入或修改文本时（如在行首输入字符），触发外部滚动逻辑，恢复吸底并滚到底部
                        if (newValue.text != value.text) {
                            onRequestScrollToBottom()
                        }
                        onValueChange(newValue)
                    },
                    textStyle = textStyle,
                    cursorBrush = if (isExecuting && !isWaitingConfirmation) {
                        SolidColor(Color.Transparent)
                    } else {
                        SolidColor(TerminalColors.Prompt)
                    },
                    singleLine = false,
                    maxLines = 5,
                    interactionSource = textFieldInteractionSource,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { if (!isExecuting || isWaitingConfirmation) submitAndKeepKeyboard() },
                        onGo = { if (!isExecuting || isWaitingConfirmation) submitAndKeepKeyboard() },
                        onDone = { if (!isExecuting || isWaitingConfirmation) submitAndKeepKeyboard() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        // 【关键机制 2 - 请勿在失焦时自动抢回焦点】：
                        // 1. 终端历史输出被 SelectionContainer 包裹，用户长按选中文本时焦点会转移给 SelectionContainer。
                        //    如果此处在失焦时强行 requestFocus() 抢回焦点，会导致：
                        //    a) 触发 Compose 的 bringIntoView 自动向下滚动露出输入框，出现“长按时界面向下回滑”；
                        //    b) 强行打断 SelectionContainer 的焦点持有，导致刚生成的文本选区瞬间被 onRelease() 销毁。
                        // 2. 软键盘的弹出与收起由 TerminalScreen 的外层点击与 IME 状态统一控制；
                        // 3. onFocusChange 会向外部同步焦点状态，供父容器在手指按下第一时间剥离焦点，避免长按事件失效。
                        .onFocusChanged { focusState ->
                            onFocusChange?.invoke(focusState.isFocused)
                        }
                        .onKeyEvent { event ->
                            // 1. 优先分发外接物理键盘的 Ctrl / Alt 组合键（特别是 Ctrl+C 中断）
                            if (TerminalHardwareKeyHandler.handleKeyEvent(event, hardwareKeyActions)) {
                                return@onKeyEvent true
                            }

                            // 2. 仅在 KeyDown 时响应，防止物理键盘单次敲击触发两次
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

                            // 3. 执行态控制：前台任务运行中且非二次确认时，拦截回车与普通输入按键，放行滚动按键
                            if (isExecuting && !isWaitingConfirmation) {
                                return@onKeyEvent when (event.key) {
                                    Key.PageUp, Key.PageDown, Key.DirectionUp, Key.DirectionDown -> false
                                    else -> true
                                }
                            }

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
                // 仅当非执行中、光标位于末尾且存在建议时渲染在末尾，保持字体度量与折行完全一致
                if (!isExecuting && ghostText.isNotEmpty() && value.selection.end == value.text.length) {
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
