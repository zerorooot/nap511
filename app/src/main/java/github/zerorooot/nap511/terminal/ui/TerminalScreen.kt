package github.zerorooot.nap511.terminal.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import github.zerorooot.nap511.terminal.engine.CompletionCandidate
import github.zerorooot.nap511.terminal.viewmodel.TerminalLine
import github.zerorooot.nap511.terminal.viewmodel.TerminalViewModel
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.copy
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * 终端界面顶层有状态路由适配器 (Stateful Route Adapter)
 *
 * 维持向后兼容性（供 AppNavHost 等调用），负责连接 ViewModel 状态与事件，
 * 并将纯渲染逻辑委托给无状态的 [TerminalContent]。
 */
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onBack: () -> Unit,
    openDrawer: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val handleBack: () -> Unit = remember(keyboardController, onBack) {
        {
            keyboardController?.hide()
            onBack()
        }
    }

    // 控制系统状态栏和控制栏（导航栏）沉浸并保持深色背景一致
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            val originalLightStatus = insetsController.isAppearanceLightStatusBars
            val originalLightNav = insetsController.isAppearanceLightNavigationBars

            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false

            onDispose {
                insetsController.isAppearanceLightStatusBars = originalLightStatus
                insetsController.isAppearanceLightNavigationBars = originalLightNav
                keyboardController?.hide()
            }
        } else {
            onDispose {
                keyboardController?.hide()
            }
        }
    }

    val exitAndReset = remember(viewModel, handleBack) {
        fun() {
            viewModel.resetSession()
            handleBack()
        }
    }

    DisposableEffect(viewModel, exitAndReset) {
        viewModel.onExitAction = exitAndReset
        onDispose {
            viewModel.onExitAction = null
        }
    }

    // 系统返回键 / 返回手势：常规返回，保留当前终端会话状态
    BackHandler {
        handleBack()
    }

    var showMenuSheet by remember { mutableStateOf(false) }
    var isInputFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (viewModel.lines.size + 1).coerceAtLeast(0)
    )

    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    // 统一吸底与滚动控制器【托管关键机制 3, 4, 4B, 4C】
    val scrollController = rememberTerminalScrollController(
        listState = listState,
        linesCount = viewModel.lines.size,
        isExecuting = viewModel.isExecuting,
        imeBottom = imeBottom,
        isInputFocused = isInputFocused,
        onClearFocus = { focusManager.clearFocus() },
        onBeforeBringUpKeyboard = {
            val current = viewModel.inputState
            if (current.selection.start != current.text.length || current.selection.end != current.text.length) {
                viewModel.onInputChange(
                    current.copy(selection = TextRange(current.text.length))
                )
            }
        }
    )

    // 虚拟辅助栏动作聚合
    val accessoryActions = remember(viewModel, scrollController) {
        TerminalAccessoryActions(
            onTab = { viewModel.handleTabPress() },
            onCtrlToggle = { viewModel.toggleCtrl() },
            onSlash = { viewModel.insertCharacter("/") },
            onDash = { viewModel.insertCharacter("-") },
            onHome = { viewModel.moveCursorHome() },
            onArrowUp = { viewModel.navigateHistoryUp() },
            onEnd = { viewModel.moveCursorEnd() },
            onPageUp = { scrollController.scrollPageUp() },
            onMenu = { showMenuSheet = true },
            onAltToggle = { viewModel.toggleAlt() },
            onPipe = { viewModel.insertCharacter("|") },
            onStar = { viewModel.insertCharacter("*") },
            onArrowLeft = { viewModel.moveCursorLeft() },
            onArrowDown = { viewModel.navigateHistoryDown() },
            onArrowRight = { viewModel.moveCursorRight() },
            onPageDown = { scrollController.scrollPageDown() }
        )
    }

    // 外接物理键盘快捷键动作聚合
    val hardwareActions = remember(viewModel, exitAndReset) {
        TerminalHardwareKeyActions(
            onCtrlC = { viewModel.handleCtrlC() },
            onCtrlU = { viewModel.handleCtrlU() },
            onCtrlK = { viewModel.handleCtrlK() },
            onCtrlW = { viewModel.handleCtrlW() },
            onCtrlL = { viewModel.handleCtrlL() },
            onCtrlA = { viewModel.handleCtrlA() },
            onCtrlE = { viewModel.handleCtrlE() },
            onCtrlD = { viewModel.handleCtrlD(exitAndReset) },
            onCtrlLeft = { viewModel.handleCtrlLeft() },
            onCtrlRight = { viewModel.handleCtrlRight() },
            onAltB = { viewModel.handleAltB() },
            onAltF = { viewModel.handleAltF() },
            onAltD = { viewModel.handleAltD() },
            onAltBackspace = { viewModel.handleAltBackspace() },
            onAltDot = { viewModel.handleAltDot() }
        )
    }

    CompositionLocalProvider(LocalTerminalTheme provides TerminalColorTheme.Default) {
        TerminalContent(
            lines = viewModel.lines,
            inputState = viewModel.inputState,
            ghostText = viewModel.ghostText,
            currentPath = viewModel.currentPath,
            contextPrompt = viewModel.contextPromptText(),
            isWaitingConfirmation = viewModel.isWaitingConfirmation,
            isExecuting = viewModel.isExecuting,
            isCtrlActive = viewModel.isCtrlActive,
            isAltActive = viewModel.isAltActive,
            completionCandidates = viewModel.completionCandidates,
            isCompletionBarVisible = viewModel.isCompletionBarVisible,
            activeCandidateIndex = viewModel.activeCandidateIndex,
            scrollController = scrollController,
            focusRequester = focusRequester,
            hardwareKeyActions = hardwareActions,
            accessoryActions = accessoryActions,
            onInputChange = { viewModel.onInputChange(it) },
            onSubmit = {
                scrollController.autoScrollToBottom = true
                viewModel.submitInput()
                scrollController.coroutineScope.launchSafeScroll(scrollController)
            },
            onTab = { viewModel.handleTabPress() },
            onAcceptGhostText = { viewModel.acceptGhostText() },
            onArrowUp = { viewModel.navigateHistoryUp() },
            onArrowDown = { viewModel.navigateHistoryDown() },
            onSelectCandidate = { viewModel.selectCandidate(it) },
            onDismissCompletionBar = { viewModel.dismissCompletionBar() },
            onBack = handleBack,
            onClearScreen = {
                scrollController.autoScrollToBottom = true
                viewModel.clearScreen()
            },
            onShowHelp = {
                scrollController.autoScrollToBottom = true
                viewModel.onInputChange(TextFieldValue("?"))
                viewModel.submitInput()
            },
            onCopyAll = {
                viewModel.getAllTerminalText().copy(context, "Terminal Output")
                App.instance.toast("已复制终端输出内容")
            },
            onOpenDrawer = openDrawer,
            onFocusChange = { isInputFocused = it },
            isInputFocused = isInputFocused
        )

        if (showMenuSheet) {
            TerminalQuickActionsSheet(
                onDismiss = { showMenuSheet = false },
                onExecuteCommand = { cmd ->
                    viewModel.onInputChange(TextFieldValue(cmd))
                    viewModel.submitInput()
                },
                onClearScreen = { viewModel.clearScreen() },
                onCopyAll = {
                    viewModel.getAllTerminalText().copy(context, "Terminal Output")
                    App.instance.toast("已复制终端输出内容")
                },
                onOpenDrawer = openDrawer,
                onCloseTerminal = exitAndReset
            )
        }
    }
}

private fun CoroutineScope.launchSafeScroll(controller: TerminalScrollController) {
    launch {
        controller.safeScrollToBottom()
    }
}

/**
 * 无状态终端纯渲染容器 (Stateless Presentation Component)
 * 支持 Compose @Preview、纯 UI 测试，具备零 ViewModel 强耦合特性
 */
@Composable
fun TerminalContent(
    lines: List<TerminalLine>,
    inputState: TextFieldValue,
    ghostText: String,
    currentPath: String,
    contextPrompt: String,
    isWaitingConfirmation: Boolean,
    isExecuting: Boolean,
    isCtrlActive: Boolean,
    isAltActive: Boolean,
    completionCandidates: List<CompletionCandidate>,
    isCompletionBarVisible: Boolean,
    activeCandidateIndex: Int,
    scrollController: TerminalScrollController,
    focusRequester: FocusRequester,
    hardwareKeyActions: TerminalHardwareKeyActions,
    accessoryActions: TerminalAccessoryActions,
    onInputChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    onTab: () -> Unit,
    onAcceptGhostText: () -> Unit,
    onArrowUp: () -> Unit,
    onArrowDown: () -> Unit,
    onSelectCandidate: (CompletionCandidate) -> Unit,
    onDismissCompletionBar: () -> Unit,
    onBack: () -> Unit,
    onClearScreen: () -> Unit,
    onShowHelp: () -> Unit,
    onCopyAll: () -> Unit,
    onOpenDrawer: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    isInputFocused: Boolean,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TerminalColors.Background,
        contentColor = TerminalColors.TextPrimary,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TerminalTopBar(
                currentPath = currentPath,
                isExecuting = isExecuting,
                onBack = onBack,
                onClearScreen = onClearScreen,
                onShowHelp = onShowHelp,
                onCopyAll = onCopyAll,
                onOpenDrawer = onOpenDrawer
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TerminalCompletionBar(
                    candidates = completionCandidates,
                    isVisible = isCompletionBarVisible,
                    activeIndex = activeCandidateIndex,
                    onSelectCandidate = onSelectCandidate,
                    onDismiss = onDismissCompletionBar
                )
                TerminalAccessoryBar(
                    actions = accessoryActions,
                    isCtrlActive = isCtrlActive,
                    isAltActive = isAltActive
                )
            }
        }
    ) { innerPadding ->
        val currentBringUpKeyboard by rememberUpdatedState {
            scrollController.requestKeyboard()
        }

        // 【关键机制 5B - 请勿改用 clickable 或带 onLongPress 的 detectTapGestures】：
        // 采用 awaitEachGesture + PointerEventPass.Initial：
        // 1. 在手指按下瞬间，不调用 clearFocus()，保证软键盘不因失焦收回；
        // 2. 仅在判定为轻触单击（非滑动且小于 longPressTimeoutMillis）抬手时，才唤起输入法并吸底；
        // 3. 超时判定为长按时，立即退出循环并不消费事件，让子级 SelectionContainer 原生接管文本选区。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TerminalColors.Background)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            pass = PointerEventPass.Initial,
                            requireUnconsumed = false
                        )
                        val startTime = System.currentTimeMillis()
                        var moved = false

                        // 若输入框持有焦点，不要在按下时盲目 clearFocus，否则会导致软键盘立即收回。
                        // 用户希望在软键盘展开时长按选中文本，软键盘保持展开状态。

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (change.changedToUp()) {
                                val elapsed = System.currentTimeMillis() - startTime
                                if (!moved && elapsed < viewConfiguration.longPressTimeoutMillis) {
                                    onDismissCompletionBar()
                                    currentBringUpKeyboard()
                                }
                                break
                            }

                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                            }

                            if (System.currentTimeMillis() - startTime >= viewConfiguration.longPressTimeoutMillis) {
                                // 达到长按判定阈值，交由 SelectionContainer 接管长按选中文本，本手势不消费事件且不触发单击
                                break
                            }
                        }
                    }
                }
        ) {
            // 【关键机制 5C - 请勿调换层级】：
            // LazyColumnScrollbar 位于外层，SelectionContainer 直接包裹 LazyColumn。
            LazyColumnScrollbar(
                state = scrollController.listState,
                settings = ScrollbarSettings.Default.copy(
                    thumbUnselectedColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                // 【关键机制 5 - 请勿删除 focusProperties】：
                // 当输入框持有焦点时将 canFocus 设为 false，确保长按选中文本时输入框焦点不被夺走，
                // 软键盘保持展开，同时选区与操作工具栏正常展示。
                SelectionContainer(
                    modifier = Modifier.focusProperties {
                        canFocus = !isInputFocused
                    }
                ) {
                    LazyColumn(
                        state = scrollController.listState,
                        contentPadding = innerPadding,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp)
                    ) {
                        items(
                            count = lines.size,
                            key = { index -> lines[index].id }
                        ) { index ->
                            TerminalLineRow(line = lines[index])
                        }

                        item(key = "terminal_ghost_input") {
                            // 【关键保护 - 请勿删除 DisableSelection】：
                            // 隔离输入框，防止输入框内部文本与光标受到外部文本选择手势干扰
                            DisableSelection {
                                GhostTextField(
                                    value = inputState,
                                    onValueChange = onInputChange,
                                    ghostText = ghostText,
                                    contextPrompt = contextPrompt,
                                    promptSign = if (isWaitingConfirmation) "confirm (yes/no): " else "$ ",
                                    isWaitingConfirmation = isWaitingConfirmation,
                                    onSubmit = onSubmit,
                                    onTab = onTab,
                                    onAcceptGhostText = onAcceptGhostText,
                                    onArrowUp = onArrowUp,
                                    onArrowDown = onArrowDown,
                                    hardwareKeyActions = hardwareKeyActions,
                                    focusRequester = focusRequester,
                                    focusTrigger = scrollController.focusTrigger,
                                    onFocusConsumed = { scrollController.consumeFocus() },
                                    onRequestScrollToBottom = {
                                        scrollController.autoScrollToBottom = true
                                        scrollController.coroutineScope.launchSafeScroll(scrollController)
                                    },
                                    onFocusChange = onFocusChange
                                )
                            }
                        }

                        item(key = "terminal_bottom_spacer") {
                            // 【关键保护 - 请勿删除 DisableSelection】：
                            // 隔离底部空白占位区域，防止空白区域被误选为文本
                            DisableSelection {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
