package github.zerorooot.nap511.screen.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.elvishew.xlog.XLog
import com.google.gson.JsonParser
import github.zerorooot.nap511.BuildConfig
import github.zerorooot.nap511.R
import github.zerorooot.nap511.screen.components.BaseTopAppBar
import github.zerorooot.nap511.screen.components.TopAppBarActionButton
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.copy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.compose.ui.res.stringResource

/**
 * 关于应用页面 (AboutScreen)
 *
 * 遵循 Material 3 设计规范与高内聚设计：
 * 1. 顶部 Header 品牌与版本号展示，支持 Git SHA 构建标识及一键复制；
 * 2. 进入页面自动异步请求 GitHub Releases API 检查新版本，发现更新在 Summary 括号高亮提示；
 * 3. 分组卡片：版本与更新（轻量浏览器跳转）、开源与社区、法律合规与许可；
 * 4. 法律合规与开源许可无缝联动 HtmlWebViewScreen 提供优雅美观的富文本排版；
 * 5. 底部版权与免责落款。
 */
// 顶层持久状态（跟随应用进程生命周期，页面退出再次进入不会重复请求）
private var hasCheckedUpdate = false
private var latestReleaseVersion by mutableStateOf<String?>(null)
private var hasNewVersion by mutableStateOf(false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onDrawerClick: () -> Unit,
    onNavigateToLicense: () -> Unit,
    onNavigateToDisclaimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val buildInfo =
        "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · git-${BuildConfig.GIT_SHA}"

    // 自动在进入页面时异步检查 GitHub 是否有新版本（仅首次进入触发）
    LaunchedEffect(Unit) {
        if (hasCheckedUpdate) return@LaunchedEffect
        hasCheckedUpdate = true
        withContext(Dispatchers.IO) {
            try {
                XLog.i("进入关于页面，开始后台检查 GitHub 最新版本...")
                val request = Request.Builder()
                    .url(AboutConstants.GITHUB_API_LATEST_RELEASE)
                    .header("User-Agent", "nap511")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                val response = OkHttpClient().newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body.string()
                    if (body.isNotEmpty()) {
                        val json = JsonParser.parseString(body).asJsonObject
                        val tagName = json.get("tag_name")?.asString?.trim() ?: ""
                        XLog.i("获取到 GitHub 最新发版标签: $tagName")
                        if (tagName.isNotEmpty()) {
                            if (AboutConstants.isNewerVersion(tagName, BuildConfig.VERSION_NAME)) {
                                latestReleaseVersion = tagName
                                hasNewVersion = true
                                XLog.i("检测到新版本: $tagName (本地版本: ${BuildConfig.VERSION_NAME})")
                            } else {
                                XLog.i("当前已是最新版本或超前开发版本 (云端: $tagName, 本地: ${BuildConfig.VERSION_NAME})")
                            }
                        }
                    }
                } else {
                    XLog.w("检查更新请求失败，HTTP code: ${response.code}")
                }
            } catch (e: Exception) {
                XLog.e("检查更新网络请求异常", e)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BaseTopAppBar(
                title = { Text("关于应用") },
                navigationIcon = {
                    TopAppBarActionButton(
                        imageVector = Icons.Rounded.Menu,
                        description = "打开抽屉"
                    ) {
                        XLog.d("点击打开抽屉菜单")
                        onDrawerClick()
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // 1. 品牌 Header 区域
            Image(
                painter = rememberAsyncImagePainter(R.mipmap.ic_launcher),
                contentDescription = "应用图标",
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = AboutConstants.APP_SLOGAN,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 版本号与 Git SHA 胶囊标签（支持点击复制）
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.clickable {
                    buildInfo.copy(context)
                    App.instance.toast("已复制版本与构建信息")
                    XLog.i("已复制构建信息至剪贴板: $buildInfo")
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = buildInfo,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制版本信息",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. 分组一：版本与更新
            AboutGroupCard(title = "版本与更新") {
                val checkUpdateSummary = if (hasNewVersion && latestReleaseVersion != null) {
                    "前往 GitHub Releases 查看最新发布（发现新版本 $latestReleaseVersion 🎉）"
                } else {
                    "前往 GitHub Releases 查看最新发布"
                }

                AboutItem(
                    title = "检查更新",
                    summary = checkUpdateSummary,
                    icon = Icons.Default.Update,
                    summaryColor = if (hasNewVersion) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    showDivider = true,
                    onClick = {
                        XLog.i("用户点击检查更新")
                        openExternalUrl(context, AboutConstants.GITHUB_RELEASES_URL)
                    }
                )
                AboutItem(
                    title = "更新记录",
                    summary = "查看历史版本迭代与变更记录",
                    icon = Icons.AutoMirrored.Default.Article,
                    onClick = {
                        XLog.i("用户点击查看更新记录")
                        openExternalUrl(context, AboutConstants.GITHUB_COMMITS_URL)
                    }
                )
            }

            // 3. 分组二：开源生态与社区
            AboutGroupCard(title = "开源与社区") {
                AboutItem(
                    title = "开源主页",
                    summary = AboutConstants.GITHUB_REPO_URL,
                    icon = Icons.Default.Code,
                    showDivider = true,
                    onClick = {
                        XLog.i("用户点击访问开源主页")
                        openExternalUrl(context, AboutConstants.GITHUB_REPO_URL)
                    }
                )
                AboutItem(
                    title = "问题反馈",
                    summary = "提交 Bug 报告或功能建议 (GitHub Issues)",
                    icon = Icons.Default.BugReport,
                    onClick = {
                        XLog.i("用户点击问题反馈")
                        openExternalUrl(context, AboutConstants.GITHUB_ISSUES_URL)
                    }
                )
            }

            // 4. 分组三：法律与合规
            AboutGroupCard(title = "法律与合规") {
                AboutItem(
                    title = "开源许可",
                    summary = "GNU GPL-3.0 及第三方开源依赖库清单",
                    icon = Icons.Default.Description,
                    showDivider = true,
                    onClick = {
                        XLog.i("用户点击查看开源许可 (原生组件)")
                        onNavigateToLicense()
                    }
                )
                AboutItem(
                    title = "免责声明",
                    summary = "说明本项目与 115 官方关系及用户免责条款",
                    icon = Icons.Default.Gavel,
                    onClick = {
                        XLog.i("用户点击查看免责声明 (原生组件)")
                        onNavigateToDisclaimer()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. 底部版权与落款
            Text(
                text = AboutConstants.COPYRIGHT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight
            )
        }
    }
}

/**
 * 分组卡片容器
 */
@Composable
private fun AboutGroupCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

/**
 * 关于页面单项组件
 */
@Composable
private fun AboutItem(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showDivider: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = summaryColor
            )
        }
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}
