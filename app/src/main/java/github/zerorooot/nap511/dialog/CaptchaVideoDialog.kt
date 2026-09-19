package github.zerorooot.nap511.dialog

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.zerorooot.nap511.screen.web.CaptchaVideoWebViewScreen

/**
 * 视频 115 账号安全验证码内容视图
 *
 * 1. 顶部提供“115 账号安全验证”标题与标准关闭按钮；
 * 2. 隐藏原 Web 页面内的侧边栏抽屉菜单图标；
 * 3. 横屏（Landscape）模式下自动限制比例 (55% 宽, 85% 高) 及最大宽度 520dp，规避横屏畸变；
 * 4. 竖屏模式下使用 92% 宽度、70% 高度；
 *
 * @param onDismiss 用户点击关闭按钮时的回调（验证取消/失败）
 * @param onSuccess 验证码通过时的回调（验证成功）
 */
@Composable
fun CaptchaVideoContent(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Card(
        modifier = Modifier
            .fillMaxWidth(if (isLandscape) 0.55f else 0.92f)
            .fillMaxHeight(if (isLandscape) 0.85f else 0.70f)
            .widthIn(max = 520.dp)
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部清晰标题栏：标题 + 右上角关闭按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "115 账号安全验证",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭验证弹窗",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 嵌入验证码 WebView 页面（隐藏原 TopBar 抽屉图标）
            CaptchaVideoWebViewScreen(
                showTopBarButton = false,
                //弹窗嵌入时不吸收系统 safeInsets
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { action ->
                when (action) {
                    "select" -> onSuccess()
                    else -> onDismiss()
                }
            }
        }
    }
}
