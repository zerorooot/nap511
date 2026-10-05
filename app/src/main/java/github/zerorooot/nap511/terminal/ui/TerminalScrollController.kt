package github.zerorooot.nap511.terminal.ui

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 终端列表滚动与边界安全纯算法辅助类
 * 抽离纯计算逻辑，方便单元测试对【关键机制 3】和【关键机制 4B】进行 100% 边界断言
 */
object TerminalScrollSafetyHelper {

    /**
     * 计算吸底目标行索引【关键机制 3 边界收敛算法】
     * @param totalItemsCount 列表当前已布局条目总数
     * @return 目标安全行索引，保证 >= 0；若条目为 0 则返回 0
     */
    fun calculateTargetIndex(totalItemsCount: Int): Int {
        if (totalItemsCount <= 0) return 0
        return (totalItemsCount - 1).coerceAtLeast(0)
    }

    /**
     * 判定当前滚动位置是否处于底部吸附阈值区 (最后可见项位于倒数第 3 项以内)
     */
    fun isNearBottom(lastVisibleIndex: Int, totalItemsCount: Int, threshold: Int = 3): Boolean {
        if (totalItemsCount <= 0) return true
        return lastVisibleIndex >= (totalItemsCount - threshold)
    }

    /**
     * 计算 PageUp 向上滚动目标行索引
     */
    fun calculatePageUpTarget(currentFirstIndex: Int, pageSize: Int = 12): Int {
        return (currentFirstIndex - pageSize).coerceAtLeast(0)
    }

    /**
     * 计算 PageDown 向下滚动目标行索引
     */
    fun calculatePageDownTarget(currentFirstIndex: Int, totalItemsCount: Int, pageSize: Int = 12): Int {
        val maxIndex = (totalItemsCount - 1).coerceAtLeast(0)
        return (currentFirstIndex + pageSize).coerceAtMost(maxIndex)
    }
}

/**
 * 终端吸底与滚动同步控制器 (Terminal Scroll Controller)
 *
 * 集中管理终端界面的所有滚动、软键盘呼起及吸底跟随行为，彻底收拢 TerminalScreen 中的状态机逻辑：
 * 1. 【关键机制 3 - safeScrollToBottom 防越界】：结合 totalItemsCount 边界收敛并捕获并发帧异步异常；
 * 2. 【关键机制 4 - 双保险键盘弹出吸底】：软键盘高度变化触发吸底锁定并在 300ms 后释放；
 * 3. 【关键机制 4B - autoScrollToBottom 跟随输出模式】：用户上滑翻看历史暂停吸底，滑回底部或提交输入自动恢复；
 * 4. 【关键机制 4C - 每次进入终端页面始终吸底并弹出键盘】：0ms / 50ms / 150ms 三段式延迟保障。
 */
@Stable
class TerminalScrollController(
    val listState: LazyListState,
    val coroutineScope: CoroutineScope,
    var onBeforeBringUpKeyboard: () -> Unit = {}
) {
    /**
     * 终端核心吸底跟随状态 (Follow Output Mode)【关键机制 4B】：
     * - 默认为 true（持续跟随最新输出并吸底锚定输入框）；
     * - 当用户主动向上拖拽手势离开底部去翻看历史输出时，置为 false，防止新输出打扰用户阅读；
     * - 当用户滑回底部、提交命令、点击输入框或重新呼起键盘时，立即恢复为 true。
     */
    var autoScrollToBottom by mutableStateOf(true)

    /** 标记由于软键盘弹出而需要执行强制吸底锁定 */
    var shouldScrollToBottomOnIme by mutableStateOf(false)

    /** 标记用户当前是否主动进行了滑动操作 */
    var userScrolled by mutableStateOf(false)

    /**
     * 【关键机制 3 - 请勿删除 safeScrollToBottom】：
     * 安全吸底函数：严格防御 IndexOutOfBoundsException。
     * 在 LazyColumn 重组或大量异步输出（如连续快速执行 ls）刷屏时，
     * listState 的内部 itemProvider 数量可能与 viewModel.lines.size 存在微小的帧异步。
     * 此处结合 layoutInfo.totalItemsCount 进行范围收敛，并在极端情况下捕获越界异常，确保应用绝对不崩溃。
     */
    suspend fun safeScrollToBottom() {
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) {
            val targetIndex = TerminalScrollSafetyHelper.calculateTargetIndex(total)
            try {
                listState.scrollToItem(targetIndex)
            } catch (_: IndexOutOfBoundsException) {
                // 捕获并发帧间可能出现的短暂越界，安全降级
            }
        }
    }

    /**
     * 呼起软键盘并执行安全吸底，确保输入框与光标可见
     *
     * 【关键机制增强 - 解决离开底部后单击只滑动不弹键盘的 Bug】：
     * 当用户滚动到最上面时，底部的 GhostTextField 已被 LazyColumn 离屏回收（Uncomposed）。
     * 若在此时同步执行 focusRequester.requestFocus()，由于输入框尚未挂载进视口，焦点请求会被静默丢弃。
     * 因此采用多阶段时序：
     * 1. 立即执行 safeScrollToBottom() 吸底；
     * 2. 延迟 50ms 等待 LazyColumn 完成测量布局并重新挂载 GhostTextField，执行二次聚焦并呼起键盘；
     * 3. 延迟 100ms 进一步应对动画与输入法窗口焦点切换，确保用户在顶部单次点击时同时吸底且弹出键盘！
     */
    fun bringUpKeyboard(
        focusRequester: FocusRequester,
        keyboardController: SoftwareKeyboardController?,
        onBeforeFocus: (() -> Unit)? = null
    ) {
        val beforeAction = onBeforeFocus ?: this.onBeforeBringUpKeyboard
        beforeAction()
        shouldScrollToBottomOnIme = true
        autoScrollToBottom = true
        coroutineScope.launch {
            // 1. 首次尝试吸底与聚焦
            safeScrollToBottom()
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {
            }

            // 2. 应对从顶部滑下来的情况：LazyColumn 需要等待首帧重新测量挂载 GhostTextField
            delay(50.milliseconds)
            safeScrollToBottom()
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {
            }

            // 3. 延迟 100ms 进一步应对窗口焦点切换与系统动画
            delay(100.milliseconds)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 响应辅助栏 PageUp 按键：向上滚动一页并暂停吸底跟随
     */
    fun scrollPageUp(pageSize: Int = 12) {
        coroutineScope.launch {
            val target = TerminalScrollSafetyHelper.calculatePageUpTarget(listState.firstVisibleItemIndex, pageSize)
            try {
                listState.animateScrollToItem(target)
                autoScrollToBottom = false
            } catch (_: IndexOutOfBoundsException) {
            }
        }
    }

    /**
     * 响应辅助栏 PageDown 按键：向下滚动一页，若接近底部则恢复吸底跟随
     */
    fun scrollPageDown(pageSize: Int = 12) {
        coroutineScope.launch {
            val total = listState.layoutInfo.totalItemsCount
            val target = TerminalScrollSafetyHelper.calculatePageDownTarget(
                listState.firstVisibleItemIndex,
                total,
                pageSize
            )
            try {
                listState.animateScrollToItem(target)
                val maxIndex = TerminalScrollSafetyHelper.calculateTargetIndex(total)
                if (target >= maxIndex - 2) {
                    autoScrollToBottom = true
                }
            } catch (_: IndexOutOfBoundsException) {
            }
        }
    }
}

/**
 * 创建并记忆 [TerminalScrollController]，自动绑定生命周期、手势与软键盘联动
 */
@Composable
fun rememberTerminalScrollController(
    listState: LazyListState = rememberLazyListState(),
    linesCount: Int,
    isExecuting: Boolean,
    imeBottom: Dp = 0.dp,
    focusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?,
    isInputFocused: Boolean,
    onClearFocus: () -> Unit = {},
    onBeforeBringUpKeyboard: () -> Unit = {}
): TerminalScrollController {
    val coroutineScope = rememberCoroutineScope()
    val controller = remember(listState) {
        TerminalScrollController(listState, coroutineScope, onBeforeBringUpKeyboard)
    }
    controller.onBeforeBringUpKeyboard = onBeforeBringUpKeyboard

    // 1. 监听用户物理拖拽手势：用户手指按在屏幕上拖拽列表时，标记 userScrolled
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) {
        if (isDragged) {
            controller.userScrolled = true
            controller.shouldScrollToBottomOnIme = false
        }
    }

    // 2. 监听滚动状态：惯性滑动或拖拽彻底结束时，根据最终位置决定是否恢复 autoScrollToBottom
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            if (controller.userScrolled) {
                controller.shouldScrollToBottomOnIme = false
            }
        } else {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems > 0) {
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val atBottom = TerminalScrollSafetyHelper.isNearBottom(lastVisibleIndex, totalItems)
                if (atBottom) {
                    // 用户滑到底部附近，恢复自动吸底跟随模式
                    controller.autoScrollToBottom = true
                } else if (controller.userScrolled) {
                    // 仅当用户主动向上滑动离开底部查看历史时，才暂停自动吸底跟随模式
                    controller.autoScrollToBottom = false
                }
            }
            controller.userScrolled = false
        }
    }

    // 3. 【关键机制 4 - 双保险键盘弹出吸底】：
    // 无论用户是通过点击输出区、空白区还是直接点击输入框触发的键盘升起，
    // 只要键盘高度从 0.dp 变为 > 0.dp，自动激活吸底锁定与跟随模式并在 300ms 后释放
    var wasImeClosed by remember { mutableStateOf(true) }
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0.dp) {
            if (wasImeClosed) {
                controller.shouldScrollToBottomOnIme = true
                controller.autoScrollToBottom = true
            }
            wasImeClosed = false
        } else {
            if (!wasImeClosed && isInputFocused) {
                onClearFocus()
            }
            wasImeClosed = true
        }
    }

    LaunchedEffect(imeBottom, controller.shouldScrollToBottomOnIme) {
        if (controller.shouldScrollToBottomOnIme && imeBottom > 0.dp) {
            controller.safeScrollToBottom()
            delay(300.milliseconds)
            controller.shouldScrollToBottomOnIme = false
        }
    }

    // 4. 【智能防打扰滚动】：新条目增加或执行状态变化时，若处于跟随模式则吸底
    LaunchedEffect(linesCount, isExecuting) {
        if (controller.autoScrollToBottom) {
            controller.safeScrollToBottom()
        }
    }

    // 5. 当列表测量布局完成、条目总数增加时，如果处于吸底跟随模式，确保滚动到最新添加的末尾项
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .collect {
                if (controller.autoScrollToBottom) {
                    controller.safeScrollToBottom()
                }
            }
    }

    // 6. 【关键机制 4C - 每次进入终端页面始终吸底并弹出键盘】：
    // 0ms / 50ms / 150ms 三段式延迟保障首帧测量与软键盘 100% 呼起
    LaunchedEffect(Unit) {
        controller.safeScrollToBottom()
        controller.bringUpKeyboard(focusRequester, keyboardController, onBeforeBringUpKeyboard)

        delay(50.milliseconds)
        controller.safeScrollToBottom()
        controller.bringUpKeyboard(focusRequester, keyboardController, onBeforeBringUpKeyboard)

        delay(150.milliseconds)
        controller.safeScrollToBottom()
        controller.bringUpKeyboard(focusRequester, keyboardController, onBeforeBringUpKeyboard)
    }

    return controller
}
