package github.zerorooot.nap511.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 单个虚拟按键配置模型 (Accessory Key Model)
 *
 * @param label 按键显示文本（如 "CTRL", "↹", "←"）
 * @param onClick 按键点击或连发触发时的回调
 * @param isAccent 是否为强调色特殊按键（如 CTRL, ALT, 菜单等）
 * @param isActive 是否处于锁定/高亮激活状态（如 CTRL/ALT 开启时）
 * @param autoRepeat 是否支持长按连续触发（如左右方向键持续移动光标）
 * @param initialDelayMillis 长按连击前静默等待时间
 * @param repeatIntervalMillis 连击触发间隔
 */
@Immutable
data class AccessoryKeyItem(
    val label: String,
    val onClick: () -> Unit,
    val isAccent: Boolean = false,
    val isActive: Boolean = false,
    val autoRepeat: Boolean = false,
    val initialDelayMillis: Long = 350L,
    val repeatIntervalMillis: Long = 50L
)

/**
 * 终端辅助按键行为聚合类 (Accessory Actions Data Clump Solution)
 * 将 16 个分散的回调统一聚拢为单一动作对象，实现高内聚、低耦合
 */
@Immutable
data class TerminalAccessoryActions(
    val onTab: () -> Unit = {},
    val onCtrlToggle: () -> Unit = {},
    val onSlash: () -> Unit = {},
    val onDash: () -> Unit = {},
    val onHome: () -> Unit = {},
    val onArrowUp: () -> Unit = {},
    val onEnd: () -> Unit = {},
    val onPageUp: () -> Unit = {},
    val onMenu: () -> Unit = {},
    val onAltToggle: () -> Unit = {},
    val onPipe: () -> Unit = {},
    val onStar: () -> Unit = {},
    val onArrowLeft: () -> Unit = {},
    val onArrowDown: () -> Unit = {},
    val onArrowRight: () -> Unit = {},
    val onPageDown: () -> Unit = {}
)

/**
 * 终端默认按键排布构造工厂
 */
object TerminalAccessoryDefaults {

    /**
     * 构造标准的终端双排 16 键布局配置
     * 第一排 (8 键): ↹ (Tab)、CTRL、/、-、HOME、↑、END、PAUP
     * 第二排 (8 键): ≡、ALT、|、*、←、↓、→、PGDN
     */
    fun defaultKeyRows(
        actions: TerminalAccessoryActions,
        isCtrlActive: Boolean = false,
        isAltActive: Boolean = false
    ): List<List<AccessoryKeyItem>> {
        val row1 = listOf(
            AccessoryKeyItem(label = "↹", onClick = actions.onTab),
            AccessoryKeyItem(label = "|", onClick = actions.onPipe),
            AccessoryKeyItem(label = "/", onClick = actions.onSlash),
            AccessoryKeyItem(label = "-", onClick = actions.onDash),
            AccessoryKeyItem(label = "HOME", onClick = actions.onHome),
            AccessoryKeyItem(label = "↑", onClick = actions.onArrowUp),
            AccessoryKeyItem(label = "END", onClick = actions.onEnd),
            AccessoryKeyItem(label = "PAUP", onClick = actions.onPageUp)
        )

        val row2 = listOf(
            AccessoryKeyItem(label = "≡", onClick = actions.onMenu, isAccent = true),
            AccessoryKeyItem(label = "CTRL", onClick = actions.onCtrlToggle, isAccent = true, isActive = isCtrlActive),
            AccessoryKeyItem(label = "ALT", onClick = actions.onAltToggle, isAccent = true, isActive = isAltActive),
            AccessoryKeyItem(label = "*", onClick = actions.onStar),
            // 左右方向键开启 autoRepeat，支持按住连按持续移动光标
            AccessoryKeyItem(label = "←", onClick = actions.onArrowLeft, autoRepeat = true),
            AccessoryKeyItem(label = "↓", onClick = actions.onArrowDown),
            AccessoryKeyItem(label = "→", onClick = actions.onArrowRight, autoRepeat = true),
            AccessoryKeyItem(label = "PGDN", onClick = actions.onPageDown)
        )

        return listOf(row1, row2)
    }
}

/**
 * 数据驱动的模型驱动虚拟悬浮工具栏 (Model-Driven Terminal Accessory Bar)
 *
 * @param rows 按行组织的按键配置列表，支持任意多排、任意数量按键，具备极高复用度
 * @param modifier 外部修饰符
 */
@Composable
fun TerminalAccessoryBar(
    rows: List<List<AccessoryKeyItem>>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = TerminalColors.AccessoryBarBackground,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            rows.forEach { keyRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    keyRow.forEach { keyItem ->
                        AccessoryKey(
                            item = keyItem,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 聚合 Actions 便捷重载方法
 */
@Composable
fun TerminalAccessoryBar(
    actions: TerminalAccessoryActions,
    isCtrlActive: Boolean = false,
    isAltActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val rows = remember(actions, isCtrlActive, isAltActive) {
        TerminalAccessoryDefaults.defaultKeyRows(actions, isCtrlActive, isAltActive)
    }
    TerminalAccessoryBar(rows = rows, modifier = modifier)
}

/**
 * 兼容旧签名的重载方法，防止外部未重构代码编译报错
 */
@Composable
fun TerminalAccessoryBar(
    modifier: Modifier = Modifier,
    onTab: () -> Unit,
    onCtrlToggle: () -> Unit = {},
    onSlash: () -> Unit,
    onDash: () -> Unit,
    onHome: () -> Unit,
    onArrowUp: () -> Unit,
    onEnd: () -> Unit,
    onPageUp: () -> Unit,
    onMenu: () -> Unit,
    onAltToggle: () -> Unit = {},
    onPipe: () -> Unit,
    onStar: () -> Unit,
    onArrowLeft: () -> Unit,
    onArrowDown: () -> Unit,
    onArrowRight: () -> Unit,
    onPageDown: () -> Unit,
    isCtrlActive: Boolean = false,
    isAltActive: Boolean = false,
) {
    val actions = remember(
        onTab, onCtrlToggle, onSlash, onDash, onHome, onArrowUp, onEnd, onPageUp,
        onMenu, onAltToggle, onPipe, onStar, onArrowLeft, onArrowDown, onArrowRight, onPageDown
    ) {
        TerminalAccessoryActions(
            onTab = onTab,
            onCtrlToggle = onCtrlToggle,
            onSlash = onSlash,
            onDash = onDash,
            onHome = onHome,
            onArrowUp = onArrowUp,
            onEnd = onEnd,
            onPageUp = onPageUp,
            onMenu = onMenu,
            onAltToggle = onAltToggle,
            onPipe = onPipe,
            onStar = onStar,
            onArrowLeft = onArrowLeft,
            onArrowDown = onArrowDown,
            onArrowRight = onArrowRight,
            onPageDown = onPageDown
        )
    }
    TerminalAccessoryBar(
        actions = actions,
        isCtrlActive = isCtrlActive,
        isAltActive = isAltActive,
        modifier = modifier
    )
}

/**
 * 单个按键 UI 渲染组件
 */
@Composable
private fun AccessoryKey(
    item: AccessoryKeyItem,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    val bgColor = when {
        item.isActive -> TerminalColors.KeyActive       // 激活状态：突出高亮青色
        isPressed -> TerminalColors.KeyPressed         // 按下/连按中即时视觉反馈
        item.isAccent -> TerminalColors.KeyAccent       // 特殊功能键
        else -> TerminalColors.KeyDefault              // 普通符号键
    }
    val textColor = when {
        item.isActive -> TerminalColors.KeyTextActive   // 激活高反差深色字体
        isPressed -> TerminalColors.KeyTextPressed     // 连按中绿色高亮文本
        item.isAccent -> TerminalColors.KeyTextAccent   // 强调色文本
        else -> TerminalColors.KeyTextDefault          // 常规白色文本
    }

    val clickModifier = if (item.autoRepeat) {
        Modifier.repeatingClickable(
            initialDelayMillis = item.initialDelayMillis,
            repeatIntervalMillis = item.repeatIntervalMillis,
            onPressedChange = { isPressed = it },
            onClick = item.onClick
        )
    } else {
        Modifier.clickable(onClick = item.onClick)
    }

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = item.label,
            color = textColor,
            fontSize = if (item.label.length > 3) 10.sp else 12.sp,
            fontWeight = if (item.isActive || isPressed) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * 支持单次点击与长按连续触发 (Auto-Repeat) 的高复用 Modifier 扩展
 *
 * 特性：
 * 1. 单次点击立即响应，零延迟；
 * 2. 按住不松开时，在 initialDelayMillis 静默期后，按照 repeatIntervalMillis 循环持续触发；
 * 3. 手指松开、滑出或手势取消时，自动立即停止连按协程并复位按下状态。
 *
 * @param enabled 是否启用手势监听
 * @param initialDelayMillis 长按开始连按前的初始等待延迟（默认 350ms）
 * @param repeatIntervalMillis 连按循环的时间间隔（默认 50ms，约每秒 20 次）
 * @param onPressedChange 按下/抬起状态回调，用于 UI 视觉高亮
 * @param onClick 单击及每次连按触发时的回调
 */
fun Modifier.repeatingClickable(
    enabled: Boolean = true,
    initialDelayMillis: Long = 350L,
    repeatIntervalMillis: Long = 50L,
    onPressedChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    if (!enabled) return@composed this

    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnPressedChange by rememberUpdatedState(onPressedChange)
    val coroutineScope = rememberCoroutineScope()

    this.pointerInput(initialDelayMillis, repeatIntervalMillis) {
        detectTapGestures(
            onPress = {
                currentOnPressedChange?.invoke(true)
                currentOnClick()

                val repeatJob = coroutineScope.launch {
                    delay(initialDelayMillis.milliseconds)
                    while (isActive) {
                        currentOnClick()
                        delay(repeatIntervalMillis.milliseconds)
                    }
                }

                try {
                    tryAwaitRelease()
                } finally {
                    repeatJob.cancel()
                    currentOnPressedChange?.invoke(false)
                }
            }
        )
    }
}
