package github.zerorooot.nap511.terminal.ui

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
 * 2. 【关键机制 4 - 双保险键盘弹出吸底】：软键盘高度变化时由 imeBottom 状态响应式驱动吸底；
 * 3. 【关键机制 4B - autoScrollToBottom 跟随输出模式】：用户上滑翻看历史暂停吸底，滑回底部或提交输入自动恢复；
 * 4. 【关键机制 4C - 每次进入终端页面始终吸底并弹出键盘】：基于响应式焦点令牌 (focusTrigger)，零 delay 延时。
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

    /** 标记用户当前是否主动进行了滑动操作 */
    var userScrolled by mutableStateOf(false)

    /**
     * 响应式焦点触发令牌 (Focus Trigger Token)
     * 0L 表示无待处理焦点请求；> 0L 表示有显式焦点唤起请求。
     * 当 GhostTextField 在视口内（或滚动吸底重新挂载入视口）时，会精准消费该令牌并获取焦点。
     */
    var focusTrigger by mutableLongStateOf(0L)
        private set

    /**
     * 消费当前焦点令牌，避免重复触发
     */
    fun consumeFocus() {
        focusTrigger = 0L
    }

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
     * 优雅的响应式呼起软键盘并吸底 (Reactive Focus & Keyboard Request)
     * 彻底告别脆弱的 delay 轮询重试！
     *
     * 工作机制：
     * 1. 激活吸底跟随并立即启动 safeScrollToBottom()；
     * 2. 派发响应式焦点令牌 focusTrigger = System.currentTimeMillis()；
     * 3. 若 GhostTextField 已经在视口中，立即精准响应聚焦；
     * 4. 若用户在最顶部（GhostTextField 被 LazyColumn 离屏回收），在吸底完成、GhostTextField 进入组合树的
     *    第一帧，其内部的 LaunchedEffect(focusTrigger) 会在挂载成功的瞬间精准捕获该令牌并呼起软键盘！
     */
    fun requestKeyboard(onBeforeFocus: (() -> Unit)? = null) {
        val beforeAction = onBeforeFocus ?: this.onBeforeBringUpKeyboard
        beforeAction()
        autoScrollToBottom = true
        focusTrigger = System.currentTimeMillis()
        coroutineScope.launch {
            safeScrollToBottom()
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
        }
    }

    // 2. 监听滚动状态：惯性滑动或拖拽彻底结束时，根据最终位置决定是否恢复 autoScrollToBottom
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
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
    // 只要键盘高度从 0.dp 变为 > 0.dp，自动激活吸底跟随模式，随软键盘高度响应式驱动吸底
    var wasImeClosed by remember { mutableStateOf(true) }
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0.dp) {
            if (wasImeClosed) {
                controller.autoScrollToBottom = true
            }
            if (controller.autoScrollToBottom) {
                controller.safeScrollToBottom()
            }
            wasImeClosed = false
        } else {
            if (!wasImeClosed && isInputFocused) {
                onClearFocus()
            }
            wasImeClosed = true
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
    // 基于响应式焦点令牌 (focusTrigger)，当输入框完成首帧挂载时自发响应获取焦点并弹出键盘，零 delay 延迟
    LaunchedEffect(Unit) {
        controller.requestKeyboard(onBeforeBringUpKeyboard)
    }

    return controller
}
