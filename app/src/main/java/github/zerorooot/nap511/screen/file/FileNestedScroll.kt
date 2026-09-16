package github.zerorooot.nap511.screen.file

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

@Composable
fun rememberFileNestedScrollConnection(
    thresholdPx: Float,
    isPreviewActive: Boolean,
    isBottomBarShow: Boolean,
    onTopBarShowChange: (Boolean) -> Unit,
    onBottomBarShowChange: (Boolean) -> Unit
): NestedScrollConnection {
    return remember(thresholdPx, isPreviewActive, isBottomBarShow) {
        var accumulatedDelta = 0f
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y

                // 方向改变，重置滑动累加值
                if ((delta > 0 && accumulatedDelta < 0) || (delta < 0 && accumulatedDelta > 0)) {
                    accumulatedDelta = 0f
                }

                accumulatedDelta += delta

                // 【关键点】增加状态判断 (`&& isBottomBarShow` / `&& !isBottomBarShow`)，防止重复更新状态引发卡顿
                if (accumulatedDelta < -thresholdPx) {
                    if (isPreviewActive) {
                        onTopBarShowChange(false)
                    }
                    if (isBottomBarShow) {
                        onBottomBarShowChange(false)
                    }
                }
                if (accumulatedDelta > thresholdPx) {
                    if (isPreviewActive) {
                        onTopBarShowChange(true)
                    }
                    if (!isBottomBarShow) {
                        onBottomBarShowChange(true)
                    }
                }

                return Offset.Zero
            }
        }
    }
}
