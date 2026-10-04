package github.zerorooot.nap511.terminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                AccessoryKey(label = "←", modifier = Modifier.weight(1f), onClick = onArrowLeft)
                AccessoryKey(label = "↓", modifier = Modifier.weight(1f), onClick = onArrowDown)
                AccessoryKey(label = "→", modifier = Modifier.weight(1f), onClick = onArrowRight)
                AccessoryKey(label = "PGDN", modifier = Modifier.weight(1f), onClick = onPageDown)
            }
        }
    }
}

@Composable
private fun AccessoryKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAccent: Boolean = false,
    isActive: Boolean = false
) {
    val bgColor = when {
        isActive -> Color(0xFF00ACC1) // 激活状态：突出高亮青色
        isAccent -> Color(0xFF383838) // 特殊功能键
        else -> Color(0xFF2C2C2C)     // 普通符号键
    }
    val textColor = when {
        isActive -> Color(0xFF101010) // 激活高反差深色字体
        isAccent -> Color(0xFF81D4FA) // 强调色文本
        else -> Color(0xFFE0E0E0)     // 常规白色文本
    }

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = if (label.length > 3) 10.sp else 12.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
