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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import github.zerorooot.nap511.screen.components.BaseTopAppBar
import github.zerorooot.nap511.terminal.viewmodel.TerminalLine
import github.zerorooot.nap511.terminal.viewmodel.TerminalLineType
import github.zerorooot.nap511.terminal.viewmodel.TerminalViewModel
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.copy
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.isImeVisible

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
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    var showMenuSheet by remember { mutableStateOf(false) }
    var showTopDropdown by remember { mutableStateOf(false) }

    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current

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
            }
        } else {
            onDispose {}
        }
    }

    val bringUpKeyboard: () -> Unit = {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // 实时监听软键盘状态与输出行数变化，软键盘升起或新行追加时锚定滚动到底部输入框
    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(viewModel.lines.size, isImeVisible) {
        if (viewModel.lines.isNotEmpty()) {
            listState.scrollToItem((viewModel.lines.size + 1).coerceAtLeast(0))
        }
    }

    // 默认请求焦点弹出输入法
    LaunchedEffect(Unit) {
        bringUpKeyboard()
    }

    BackHandler {
        onBack()
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
                        IconButton(onClick = onBack) {
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
            TerminalAccessoryBar(
                onTab = {
                    if (!viewModel.acceptGhostText()) {
                        // 若无幽灵文本则插入制表缩进或触发提示
                        viewModel.insertCharacter("  ")
                    }
                },
                onSlash = { viewModel.insertCharacter("/") },
                onDash = { viewModel.insertCharacter("-") },
                onHome = { viewModel.moveCursorHome() },
                onArrowUp = { viewModel.navigateHistoryUp() },
                onEnd = { viewModel.moveCursorEnd() },
                onPageUp = {
                    scope.launch {
                        val target = (listState.firstVisibleItemIndex - 12).coerceAtLeast(0)
                        listState.animateScrollToItem(target)
                    }
                },
                onMenu = { showMenuSheet = true },
                onPipe = { viewModel.insertCharacter("|") },
                onStar = { viewModel.insertCharacter("*") },
                onArrowLeft = { viewModel.moveCursorLeft() },
                onArrowDown = { viewModel.navigateHistoryDown() },
                onArrowRight = { viewModel.moveCursorRight() },
                onPageDown = {
                    scope.launch {
                        val target =
                            (listState.firstVisibleItemIndex + 12).coerceAtMost(viewModel.lines.size)
                        listState.animateScrollToItem(target)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101010))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    bringUpKeyboard()
                }
        ) {
            LazyColumn(
                state = listState,
                contentPadding = innerPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .imeNestedScroll()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        bringUpKeyboard()
                    }
                    .padding(horizontal = 10.dp)
            ) {
                items(
                    count = viewModel.lines.size,
                    key = { index -> "line_$index" }
                ) { index ->
                    TerminalLineRow(
                        line = viewModel.lines[index],
                        onClick = bringUpKeyboard
                    )
                }

                item(key = "terminal_ghost_input") {
                    GhostTextField(
                        value = viewModel.inputState,
                        onValueChange = { viewModel.onInputChange(it) },
                        ghostText = viewModel.ghostText,
                        prompt = if (viewModel.isWaitingConfirmation) "confirm (yes/no): " else viewModel.promptText(),
                        isWaitingConfirmation = viewModel.isWaitingConfirmation,
                        onSubmit = { viewModel.submitInput() },
                        onTabOrRight = { viewModel.acceptGhostText() },
                        onArrowUp = { viewModel.navigateHistoryUp() },
                        onArrowDown = { viewModel.navigateHistoryDown() },
                        focusRequester = focusRequester
                    )
                }

                item(key = "terminal_bottom_spacer") {
                    Spacer(modifier = Modifier.height(8.dp))
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
            onCloseTerminal = onBack
        )
    }
}

@Composable
private fun TerminalLineRow(line: TerminalLine, onClick: () -> Unit) {
    val color = when (line.type) {
        TerminalLineType.SYSTEM -> Color(0xFF4DD0E1)
        TerminalLineType.COMMAND -> Color(0xFFB0BEC5)
        TerminalLineType.OUTPUT -> Color(0xFFEEEEEE)
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 1.dp)
    )
}
