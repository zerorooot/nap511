package github.zerorooot.nap511.terminal.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import github.zerorooot.nap511.screen.components.BaseTopAppBar
import github.zerorooot.nap511.terminal.viewmodel.TerminalLine
import github.zerorooot.nap511.terminal.viewmodel.TerminalLineType
import github.zerorooot.nap511.terminal.viewmodel.TerminalViewModel
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.copy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import kotlin.time.Duration.Companion.milliseconds

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onBack: () -> Unit,
    openDrawer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (viewModel.lines.size + 1).coerceAtLeast(0)
    )
    val focusRequester = remember { FocusRequester() }

    var showMenuSheet by remember { mutableStateOf(false) }
    var showTopDropdown by remember { mutableStateOf(false) }

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

            // 终端界面为深色背景，系统控制栏/导航栏与状态栏图标统一设为亮色
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

    val focusManager = LocalFocusManager.current
    var isInputFocused by remember { mutableStateOf(false) }
    var shouldScrollToBottomOnIme by remember { mutableStateOf(false) }

    // 【关键机制 - 请勿删除 safeScrollToBottom】：
    // 安全吸底函数：严格防御 IndexOutOfBoundsException。
    // 在 LazyColumn 重组或大量异步输出（如连续快速执行 ls）刷屏时，
    // listState 的内部 itemProvider 数量可能与 viewModel.lines.size 存在微小的帧异步。
    // 此处结合 layoutInfo.totalItemsCount 进行范围收敛，并在极端情况下捕获越界异常，确保应用绝对不崩溃。
    suspend fun safeScrollToBottom() {
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) {
            val targetIndex = (total - 1).coerceAtLeast(0)
            try {
                listState.scrollToItem(targetIndex)
            } catch (_: IndexOutOfBoundsException) {
                // 捕获并发帧间可能出现的短暂越界，安全降级
            }
        }
    }

    // 【关键跟随输出模式 - 请勿删除 autoScrollToBottom】：
    // 终端核心吸底跟随状态（Follow Output Mode）：
    // 1. 默认为 true（持续跟随最新输出并吸底锚定输入框）。
    // 2. 当用户主动向上拖拽手势离开底部去翻看历史输出时，置为 false，防止新输出打扰用户阅读。
    // 3. 当用户滑回底部、提交命令、点击输入框或重新呼起键盘时，立即恢复为 true。
    // 4. 彻底解决旧逻辑依赖瞬时 isAtBottom (lastVisible >= total - 3) 在快速输出大量新条目时因 totalItems 骤增
    //    导致判定瞬态失真为 false、跳过滚动使页面停留在旧位置的恶性 Bug。
    var autoScrollToBottom by remember { mutableStateOf(true) }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var userScrolled by remember { mutableStateOf(false) }

    val bringUpKeyboard: () -> Unit = {
        val current = viewModel.inputState
        if (current.selection.start != current.text.length || current.selection.end != current.text.length) {
            viewModel.onInputChange(
                current.copy(selection = TextRange(current.text.length))
            )
        }
        shouldScrollToBottomOnIme = true
        autoScrollToBottom = true
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (_: Exception) {
        }
        scope.launch {
            safeScrollToBottom()
        }
    }

    // 监听用户物理拖拽手势：用户手指按在屏幕上拖拽列表时，标记 userScrolled
    LaunchedEffect(isDragged) {
        if (isDragged) {
            userScrolled = true
            shouldScrollToBottomOnIme = false
        }
    }

    // 用户滚动进行状态监听（包含惯性滑动与程序化滚动）
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            if (userScrolled) {
                shouldScrollToBottomOnIme = false
            }
        } else {
            // 当滚动彻底停止（包括惯性滑动 fling 结束）
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems > 0) {
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val atBottom = lastVisibleIndex >= totalItems - 3
                if (atBottom) {
                    // 用户滑到底部附近，恢复自动吸底跟随模式
                    autoScrollToBottom = true
                } else if (userScrolled) {
                    // 仅当用户主动向上滑动离开底部查看历史时，才暂停自动吸底跟随模式
                    autoScrollToBottom = false
                }
            }
            userScrolled = false
        }
    }

    // 监听软键盘高度动态变化：当用户显式唤起软键盘时，在软键盘升起全过程以及升起完成后，持续锚定滚动到行尾输入框
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    var wasImeClosed by remember { mutableStateOf(true) }

    // 【关键机制 - 双保险键盘弹出吸底】：
    // 无论用户是通过点击输出区、空白区还是直接点击输入框触发的键盘升起，
    // 只要键盘高度从 0.dp 变为 > 0.dp，说明软键盘正在弹出，自动激活吸底锁定与跟随模式。
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0.dp) {
            if (wasImeClosed) {
                shouldScrollToBottomOnIme = true
                autoScrollToBottom = true
            }
            wasImeClosed = false
        } else {
            if (!wasImeClosed && isInputFocused) {
                focusManager.clearFocus()
            }
            wasImeClosed = true
        }
    }

    LaunchedEffect(imeBottom, shouldScrollToBottomOnIme) {
        if (shouldScrollToBottomOnIme && imeBottom > 0.dp) {
            safeScrollToBottom()
            // 等待软键盘升起动画稳定后解除强制吸底锁定
            delay(300.milliseconds)
            shouldScrollToBottomOnIme = false
        }
    }

    // 【智能防打扰滚动】：
    // 在输出行变化（新输出追加）或命令执行状态变化时，只要处于吸底跟随模式，自动锚定吸底
    LaunchedEffect(viewModel.lines.lastOrNull()?.id, viewModel.isExecuting) {
        if (autoScrollToBottom) {
            safeScrollToBottom()
        }
    }

    // 当列表测量布局完成、条目总数增加时，如果处于吸底跟随模式，确保滚动到最新添加的末尾项
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .collect {
                if (autoScrollToBottom) {
                    safeScrollToBottom()
                }
            }
    }

    //渲染时仅仅是把这个函数对象缓存起来；代码块 resetSession()、 onBack()
    //只会在未来用户真正触发事件（如点击按钮或按 Ctrl+D）调用 exitAndReset() 时才会被执行。
    val exitAndReset = remember(viewModel, handleBack) {
        fun() {
            viewModel.resetSession()
            handleBack()
        }
    }

    // 【关键生命周期 - 每次进入终端页面始终吸底并弹出键盘】：
    // 1. 先尝试立即吸底并呼起键盘；
    // 2. 延迟 50ms 等待 LazyColumn 首帧测量布局，确保 GhostTextField 必定渲染入视口；
    // 3. 延迟 150ms 应对 NavHost 页面入场过渡动画和系统窗口焦点切换，确保软键盘 100% 成功弹出。
    LaunchedEffect(Unit) {
        viewModel.onExitAction = exitAndReset
        // 1. 立即尝试吸底并呼起键盘
        safeScrollToBottom()
        bringUpKeyboard()

        // 2. 延迟 50ms 等待 LazyColumn 首帧测量布局，确保输入框必定渲染入视口
        delay(50.milliseconds)
        safeScrollToBottom()
        bringUpKeyboard()

        // 3. 延迟 150ms 应对 NavHost 进场动画与 Window 焦点切换，保证 100% 呼起成功
        delay(150.milliseconds)
        safeScrollToBottom()
        bringUpKeyboard()
    }

    // 系统返回键 / 返回手势：常规返回，保留当前终端会话状态
    BackHandler {
        handleBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF101010),
        contentColor = Color(0xFFECEFF1),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                BaseTopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Terminal",
                                fontSize = 17.sp,
                                color = Color(0xFFECEFF1)
                            )
                            Text(
                                text = viewModel.currentPath,
                                fontSize = 11.sp,
                                color = Color(0xFFB0BEC5),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = handleBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color(0xFFECEFF1)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            autoScrollToBottom = true
                            viewModel.clearScreen()
                        }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "清屏",
                                tint = Color(0xFFB0BEC5)
                            )
                        }
                        IconButton(onClick = { showTopDropdown = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多",
                                tint = Color(0xFFB0BEC5)
                            )
                        }
                        DropdownMenu(
                            expanded = showTopDropdown,
                            onDismissRequest = { showTopDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("帮助手册") },
                                onClick = {
                                    showTopDropdown = false
                                    autoScrollToBottom = true
                                    viewModel.onInputChange(TextFieldValue("?"))
                                    viewModel.submitInput()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("复制全部") },
                                onClick = {
                                    showTopDropdown = false
                                    viewModel.getAllTerminalText().copy(context, "Terminal Output")
                                    App.instance.toast("已复制终端输出内容")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("打开侧边") },
                                onClick = {
                                    showTopDropdown = false
                                    openDrawer()
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF181818)
                    )
                )

                // 方案 1: 顶栏底部极光流光扫描线 (Laser Scanline)
                AnimatedVisibility(
                    visible = viewModel.isExecuting,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = Color(0xFF69F0AE),
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TerminalCompletionBar(
                    candidates = viewModel.completionCandidates,
                    isVisible = viewModel.isCompletionBarVisible,
                    activeIndex = viewModel.activeCandidateIndex,
                    onSelectCandidate = { candidate -> viewModel.selectCandidate(candidate) },
                    onDismiss = { viewModel.dismissCompletionBar() }
                )
                TerminalAccessoryBar(
                    onTab = { viewModel.handleTabPress() },
                    isCtrlActive = viewModel.isCtrlActive,
                    onCtrlToggle = { viewModel.toggleCtrl() },
                    onSlash = { viewModel.insertCharacter("/") },
                    onDash = { viewModel.insertCharacter("-") },
                    onHome = { viewModel.moveCursorHome() },
                    onArrowUp = { viewModel.navigateHistoryUp() },
                    onEnd = { viewModel.moveCursorEnd() },
                    onPageUp = {
                        scope.launch {
                            val target = (listState.firstVisibleItemIndex - 12).coerceAtLeast(0)
                            try {
                                listState.animateScrollToItem(target)
                                autoScrollToBottom = false
                            } catch (_: IndexOutOfBoundsException) {
                            }
                        }
                    },
                    onMenu = { showMenuSheet = true },
                    isAltActive = viewModel.isAltActive,
                    onAltToggle = { viewModel.toggleAlt() },
                    onPipe = { viewModel.insertCharacter("|") },
                    onStar = { viewModel.insertCharacter("*") },
                    onArrowLeft = { viewModel.moveCursorLeft() },
                    onArrowDown = { viewModel.navigateHistoryDown() },
                    onArrowRight = { viewModel.moveCursorRight() },
                    onPageDown = {
                        scope.launch {
                            val maxIndex =
                                (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                            val target =
                                (listState.firstVisibleItemIndex + 12).coerceAtMost(maxIndex)
                            try {
                                listState.animateScrollToItem(target)
                                if (target >= maxIndex - 2) {
                                    autoScrollToBottom = true
                                }
                            } catch (_: IndexOutOfBoundsException) {
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        val currentBringUpKeyboard by rememberUpdatedState(bringUpKeyboard)

        // 【关键手势与焦点联动机制 - 请勿改用 clickable 或带 onLongPress 的 detectTapGestures】：
        // 1. 禁止使用 Modifier.clickable：clickable 默认带有 focusable，会抢夺焦点；且长按抬起时仍会派发 onClick。
        // 2. 禁止使用 detectTapGestures(onLongPress = ...)：Compose 的 detectTapGestures 在触发长按时会执行 consumeUntilUp()，
        //    会无差别消费后续所有 Pointer 事件，导致子级 SelectionContainer 的长按文本手势被取消。
        // 3. 采用 awaitEachGesture + PointerEventPass.Initial：
        //    a) 在手指按下的瞬间（Down），不主动调用 clearFocus()，以保证键盘弹出状态下长按选中文本时软键盘不会因失焦而收回；
        //    b) 仅在判定为轻触单击（非滑动、且耗时小于 longPressTimeoutMillis）抬手时，才唤起输入法并吸底；
        //    c) 若超时判定为长按，则立即退出循环并不消费事件，让子级 SelectionContainer 原生接管文本选区和工具栏。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101010))
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
                                    viewModel.dismissCompletionBar()
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
            // 【关键层级结构 - 请勿调换】：
            // LazyColumnScrollbar 位于外层，SelectionContainer 直接包裹 LazyColumn。
            // 避免滚动条指示器组件自身被纳入 SelectionContainer 导致选择坐标计算失真。
            LazyColumnScrollbar(
                state = listState, settings = ScrollbarSettings.Default.copy(
                    thumbUnselectedColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                // 【关键机制 - 请勿删除 focusProperties】：
                // 当输入框正持有焦点（键盘弹出）时，将 SelectionContainer 的 canFocus 设为 false。
                // 这样当用户长按选中文本时，SelectionManager 的 focusRequester.requestFocus() 不会强行夺走输入框的焦点，
                // 使得软键盘能够稳定保持弹出状态；同时由于输入框（子节点）仍持有焦点，SelectionContainer.hasFocus 仍为 true，
                // 文本选区与操作工具栏（复制、全选）依然可以正常展示。
                SelectionContainer(
                    modifier = Modifier.focusProperties {
                        canFocus = !isInputFocused
                    }
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = innerPadding,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp)
                    ) {
                        items(
                            count = viewModel.lines.size,
                            key = { index -> viewModel.lines[index].id }
                        ) { index ->
                            TerminalLineRow(line = viewModel.lines[index])
                        }

                        item(key = "terminal_ghost_input") {
                            // 【关键保护 - 请勿删除 DisableSelection】：
                            // 隔离输入框与外部 SelectionContainer，防止输入框内部文本与光标受到外部文本选择手势干扰
                            DisableSelection {
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

                                GhostTextField(
                                    value = viewModel.inputState,
                                    onValueChange = { viewModel.onInputChange(it) },
                                    ghostText = viewModel.ghostText,
                                    contextPrompt = viewModel.contextPromptText(),
                                    promptSign = if (viewModel.isWaitingConfirmation) "confirm (yes/no): " else "$ ",
                                    isWaitingConfirmation = viewModel.isWaitingConfirmation,
                                    onSubmit = {
                                        autoScrollToBottom = true
                                        viewModel.submitInput()
                                        scope.launch {
                                            safeScrollToBottom()
                                        }
                                    },
                                    onTab = { viewModel.handleTabPress() },
                                    onAcceptGhostText = { viewModel.acceptGhostText() },
                                    onArrowUp = { viewModel.navigateHistoryUp() },
                                    onArrowDown = { viewModel.navigateHistoryDown() },
                                    hardwareKeyActions = hardwareActions,
                                    focusRequester = focusRequester,
                                    onRequestScrollToBottom = {
                                        autoScrollToBottom = true
                                        shouldScrollToBottomOnIme = true
                                        scope.launch {
                                            safeScrollToBottom()
                                        }
                                    },
                                    onFocusChange = { isInputFocused = it }
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

    if (showMenuSheet) {
        TerminalQuickActionsSheet(
            onDismiss = { showMenuSheet = false },
            onExecuteCommand = { cmd ->
                viewModel.onInputChange(TextFieldValue(cmd))
                viewModel.submitInput()
            },
            onClearScreen = { viewModel.clearScreen() },
            onCopyAll = {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Terminal Output", viewModel.getAllTerminalText())
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "已复制终端输出内容", Toast.LENGTH_SHORT).show()
            },
            onOpenDrawer = openDrawer,
            onCloseTerminal = exitAndReset
        )
    }
}

@Composable
private fun TerminalLineRow(line: TerminalLine) {
    when (line.type) {
        TerminalLineType.COMMAND if line.text.contains("\n$ ") -> {
            // 双行历史命令格式美化解析：第 1 行上下文路径，第 2 行提示符与命令
            val parts = line.text.split("\n$ ", limit = 2)
            val contextPart = parts[0]
            val commandPart = parts.getOrNull(1) ?: ""

            val annotatedString = remember(line.text) {
                buildAnnotatedString {
                    // 上下文路径：青蓝色高亮
                    withStyle(SpanStyle(color = Color(0xFF4DD0E1), fontWeight = FontWeight.Bold)) {
                        append(contextPart)
                    }
                    append("\n")
                    // 提示符 $：高亮绿色
                    withStyle(SpanStyle(color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold)) {
                        append("$ ")
                    }
                    // 命令内容：亮灰白色
                    withStyle(SpanStyle(color = Color(0xFFECEFF1))) {
                        append(commandPart)
                    }
                }
            }

            Text(
                text = annotatedString,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
            )
        }

        TerminalLineType.OUTPUT -> {
            val annotatedString = remember(line.text) {
                TerminalStyleParser.parseOutputLine(line.text)
            }
            Text(
                text = annotatedString,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
            )
        }

        else -> {
            val color = when (line.type) {
                TerminalLineType.SYSTEM -> Color(0xFF4DD0E1)
                TerminalLineType.COMMAND -> Color(0xFFB0BEC5)
                TerminalLineType.ERROR -> Color(0xFFEF5350)
                TerminalLineType.PROMPT -> Color(0xFFFFD54F)
            }

            Text(
                text = line.text,
                color = color,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
            )
        }
    }
}
