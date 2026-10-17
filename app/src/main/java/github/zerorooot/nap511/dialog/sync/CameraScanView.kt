package github.zerorooot.nap511.dialog.sync

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 纯 Compose 声明式相机取景框视图 (CameraScanView)
 * 采用无状态组件设计，专注于相机画面的 Viewfinder 渲染与顶层取景框蒙层绘制
 *
 * @param surfaceRequest 由 CameraX Preview 用例提供的 SurfaceRequest（为 null 时展示占位底色）
 * @param modifier 外部布局修饰符
 */
@Composable
fun CameraScanView(
    surfaceRequest: SurfaceRequest?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 1. 底层：CameraXViewfinder 纯 Compose 相机取景渲染（基于 SurfaceView 的 ImplementationMode.EXTERNAL）
        if (surfaceRequest != null) {
            CameraXViewfinder(
                surfaceRequest = surfaceRequest,
                implementationMode = ImplementationMode.EXTERNAL,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 初始化或未就绪时填充纯黑底色，防止白屏突兀闪烁
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        // 2. 顶层：Canvas 绘制半透明蒙层与中央取景边框
        val primaryColor = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val boxSize = minOf(canvasWidth, canvasHeight) * 0.7f
            val left = (canvasWidth - boxSize) / 2
            val top = (canvasHeight - boxSize) / 2

            // 半透明暗色背景
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                size = size
            )

            // 抠出中央透明扫描取景窗口
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                blendMode = BlendMode.Clear
            )

            // 取景框高亮圆角边框
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(left, top),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}
