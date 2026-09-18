package github.zerorooot.nap511.util

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 自适应布局工具类 (Adaptive Layout Utilities)
 *
 * 遵循 Material 3 Adaptive 设计规范，为应用提供跨形态（手机/折叠屏/平板/桌面）
 * 的一致性分栏指令（PaneScaffoldDirective）计算与双栏状态检测。
 */

/**
 * 判断当前分栏指令是否处于双栏/多栏并排展示状态。
 * 当水平分栏最大分区数大于 1 时返回 true（通常在 Medium 宽度折叠屏展开或 Expanded 大屏横屏时生效）。
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val PaneScaffoldDirective.isDualPane: Boolean
    get() = maxHorizontalPartitions > 1

/**
 * 记住并计算列表-详情布局的分栏脚手架指令（PaneScaffoldDirective）。
 *
 * @param horizontalSpacer 分栏之间的水平间距，默认为 0.dp（根据设计需求紧凑排列）
 * @return 响应窗口宽度规格变化的 PaneScaffoldDirective
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberListDetailDirective(horizontalSpacer: Dp = 0.dp): PaneScaffoldDirective {
    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    return remember(windowAdaptiveInfo, horizontalSpacer) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo)
            .copy(horizontalPartitionSpacerSize = horizontalSpacer)
    }
}
