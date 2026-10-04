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
import androidx.compose.material3.MaterialTheme
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
 * 软键盘上方双排特殊符号悬浮工具栏
 * 第一排: tab、/、-、home、↑、end、paup
 * 第二排: ≡、|、*、←、↓、→、PGDN
 */
@Composable
fun TerminalAccessoryBar(
    onTab: () -> Unit,
    onSlash: () -> Unit,
    onDash: () -> Unit,
    onHome: () -> Unit,
    onArrowUp: () -> Unit,
    onEnd: () -> Unit,
    onPageUp: () -> Unit,
    onMenu: () -> Unit,
    onPipe: () -> Unit,
    onStar: () -> Unit,
    onArrowLeft: () -> Unit,
    onArrowDown: () -> Unit,
    onArrowRight: () -> Unit,
    onPageDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        color = Color(0xFF1E1E1E),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // 第一排: tab, /, -, home, ↑, end, paup
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AccessoryKey(label = "↹", modifier = Modifier.weight(1f), onClick = onTab)
                AccessoryKey(label = "/", modifier = Modifier.weight(1f), onClick = onSlash)
                AccessoryKey(label = "-", modifier = Modifier.weight(1f), onClick = onDash)
                AccessoryKey(label = "HOME", modifier = Modifier.weight(1f), onClick = onHome)
                AccessoryKey(label = "↑", modifier = Modifier.weight(1f), onClick = onArrowUp)
                AccessoryKey(label = "END", modifier = Modifier.weight(1f), onClick = onEnd)
                AccessoryKey(label = "PAUP", modifier = Modifier.weight(1f), onClick = onPageUp)
            }

            // 第二排: ≡, |, *, ←, ↓, →, PGDN
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AccessoryKey(label = "≡", modifier = Modifier.weight(1f), onClick = onMenu, isAccent = true)
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
    isAccent: Boolean = false
) {
    val bgColor = if (isAccent) Color(0xFF383838) else Color(0xFF2C2C2C)
    val textColor = if (isAccent) Color(0xFF81D4FA) else Color(0xFFE0E0E0)

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
            fontSize = if (label.length > 3) 11.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}
