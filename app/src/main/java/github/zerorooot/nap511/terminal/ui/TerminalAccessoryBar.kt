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
import androidx.compose.ui.graphics.Color
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
 * 软键盘上方双排特殊符号与控制键悬浮工具栏
 * 第一排 (8 键): ↹ (Tab)、CTRL、/、-、HOME、↑、END、PAUP
 * 第二排 (8 键): ≡、ALT、|、*、←、↓、→、PGDN
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF1E1E1E),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // 第一排: ↹, CTRL, /, -, HOME, ↑, END, PAUP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AccessoryKey(label = "↹", modifier = Modifier.weight(1f), onClick = onTab)
                AccessoryKey(
                    label = "CTRL",
                    modifier = Modifier.weight(1f),
                    onClick = onCtrlToggle,
                    isAccent = true,
                    isActive = isCtrlActive
                )
                AccessoryKey(label = "/", modifier = Modifier.weight(1f), onClick = onSlash)
                AccessoryKey(label = "-", modifier = Modifier.weight(1f), onClick = onDash)
                AccessoryKey(label = "HOME", modifier = Modifier.weight(1f), onClick = onHome)
                AccessoryKey(label = "↑", modifier = Modifier.weight(1f), onClick = onArrowUp)
                AccessoryKey(label = "END", modifier = Modifier.weight(1f), onClick = onEnd)
                AccessoryKey(label = "PAUP", modifier = Modifier.weight(1f), onClick = onPageUp)
            }

            // 第二排: ≡, ALT, |, *, ←, ↓, →, PGDN
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AccessoryKey(
                    label = "≡",
                    modifier = Modifier.weight(1f),
                    onClick = onMenu,
                    isAccent = true
                )
                AccessoryKey(
                    label = "ALT",
                    modifier = Modifier.weight(1f),
                    onClick = onAltToggle,
                    isAccent = true,
                    isActive = isAltActive
                )
                AccessoryKey(label = "|", modifier = Modifier.weight(1f), onClick = onPipe)
                AccessoryKey(label = "*", modifier = Modifier.weight(1f), onClick = onStar)
                // 左右方向键开启 autoRepeat，支持按住连按持续移动光标
                AccessoryKey(
                    label = "←",
                    modifier = Modifier.weight(1f),
                    onClick = onArrowLeft,
                    autoRepeat = true
                )
                AccessoryKey(label = "↓", modifier = Modifier.weight(1f), onClick = onArrowDown)
                AccessoryKey(
                    label = "→",
                    modifier = Modifier.weight(1f),
                    onClick = onArrowRight,
                    autoRepeat = true
                )
                AccessoryKey(label = "PGDN", modifier = Modifier.weight(1f), onClick = onPageDown)
            }
        }
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

@Composable
private fun AccessoryKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAccent: Boolean = false,
    isActive: Boolean = false,
    autoRepeat: Boolean = false,
    initialDelayMillis: Long = 350L,
    repeatIntervalMillis: Long = 50L
) {
    var isPressed by remember { mutableStateOf(false) }

    val bgColor = when {
        isActive -> Color(0xFF00ACC1) // 激活状态：突出高亮青色
        isPressed -> Color(0xFF424242) // 按下/连按中即时视觉反馈
        isAccent -> Color(0xFF383838) // 特殊功能键
        else -> Color(0xFF2C2C2C)     // 普通符号键
    }
    val textColor = when {
        isActive -> Color(0xFF101010) // 激活高反差深色字体
        isPressed -> Color(0xFF69F0AE) // 连按中绿色高亮文本
        isAccent -> Color(0xFF81D4FA) // 强调色文本
        else -> Color(0xFFE0E0E0)     // 常规白色文本
    }

    val clickModifier = if (autoRepeat) {
        Modifier.repeatingClickable(
            initialDelayMillis = initialDelayMillis,
            repeatIntervalMillis = repeatIntervalMillis,
            onPressedChange = { isPressed = it },
            onClick = onClick
        )
    } else {
        Modifier.clickable(onClick = onClick)
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
            text = label,
            color = textColor,
            fontSize = if (label.length > 3) 10.sp else 12.sp,
            fontWeight = if (isActive || isPressed) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
