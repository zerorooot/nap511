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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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

    // 【关键退出状态 - 请勿删除】：
    // 标记当前页面是否正在退出（如按返回键、执行 exit 命令或页面被销毁）。
    // 传递给 GhostTextField 以彻底阻止失焦时重新请求焦点，
    // 避免退出页面时输入法意外闪弹以及在已脱落节点上触发 "visitAncestors called on an unattached node" 致命崩溃。
    var isExiting by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = remember(keyboardController, onBack) {
        {
            isExiting = true
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
                isExiting = true
                insetsController.isAppearanceLightStatusBars = originalLightStatus
                insetsController.isAppearanceLightNavigationBars = originalLightNav
                keyboardController?.hide()
            }
        } else {
            onDispose {
                isExiting = true
                keyboardController?.hide()
            }
        }
    }

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

    val bringUpKeyboard: () -> Unit = {
        val current = viewModel.inputState
        if (current.selection.start != current.text.length || current.selection.end != current.text.length) {
            viewModel.onInputChange(
                current.copy(selection = TextRange(current.text.length))
            )
        }
        shouldScrollToBottomOnIme = true
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (_: Exception) {
        }
        scope.launch {
            safeScrollToBottom()
        }
    }

    // 用户主动滚动列表时，若当前处于请求升起键盘吸底模式，则立刻解除锁定，避免与用户滑动手势冲突
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            shouldScrollToBottomOnIme = false
        }
    }

    // 监听软键盘高度动态变化：当用户显式唤起软键盘时，在软键盘升起全过程以及升起完成后，持续锚定滚动到行尾输入框
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    var wasImeClosed by remember { mutableStateOf(true) }

    // 【关键机制 - 双保险键盘弹出吸底】：
    // 无论用户是通过点击输出区、空白区还是直接点击输入框触发的键盘升起，
    // 只要键盘高度从 0.dp 变为 > 0.dp，说明软键盘正在弹出，自动激活吸底锁定。
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0.dp) {
            if (wasImeClosed) {
                shouldScrollToBottomOnIme = true
            }
            wasImeClosed = false
        } else {
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

    // 判断用户当前是否处于最底部（当前可见的最后一项是否为倒数前 3 项之一）
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf true
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= totalItems - 3
        }
    }

    // 智能防打扰滚动：在行数变化（新输出追加）或命令执行中（isExecuting），自动锚定吸底
    LaunchedEffect(viewModel.lines.size, viewModel.isExecuting) {
        if (viewModel.isExecuting || isAtBottom) {
            safeScrollToBottom()
        }
    }

    // 当列表测量布局完成、条目总数增加时，如果正在执行命令或处于底部，确保滚动到最新添加的末尾项
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .collect {
                if (viewModel.isExecuting || isAtBottom) {
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
                        IconButton(onClick = { viewModel.clearScreen() }) {
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
                            val maxIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                            val target =
                                (listState.firstVisibleItemIndex + 12).coerceAtMost(maxIndex)
                            try {
                                listState.animateScrollToItem(target)
                            } catch (_: IndexOutOfBoundsException) {
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        val currentBringUpKeyboard by rememberUpdatedState(bringUpKeyboard)

        // 【关键交互容器 - 请勿改用 Modifier.clickable】：
        // 1. 禁止使用 Modifier.clickable：clickable 默认带有 focusable 属性，会与 GhostTextField 抢夺焦点；
        //    且用户长按松开手指时，clickable 仍会派发 onClick，导致意外触发 bringUpKeyboard() 重置光标并 scrollToItem 销毁选择框。
        // 2. 必须使用 pointerInput + detectTapGestures 并显式实现 onLongPress：
        //    显式拦截长按事件，消耗事件直至手指抬起，防止长按选中文本松手时误触发 onTap。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101010))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            viewModel.dismissCompletionBar()
                            currentBringUpKeyboard()
                        },
                        onLongPress = {
                            // 显式拦截长按事件，防止松手时被判定为点击而触发 onTap
                        }
                    )
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
                SelectionContainer {
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
                                    isImeVisible = imeBottom > 0.dp,
                                    isExiting = isExiting,
                                    onRequestScrollToBottom = {
                                        shouldScrollToBottomOnIme = true
                                        scope.launch {
                                            safeScrollToBottom()
                                        }
                                    }
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
