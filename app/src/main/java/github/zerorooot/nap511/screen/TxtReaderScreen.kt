package github.zerorooot.nap511.screen

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import java.nio.charset.Charset

/**
 * 搜索匹配项位置信息
 */
private data class SearchMatch(
    val paragraphIndex: Int,
    val startChar: Int,
    val length: Int
)

val PRESET_CHARSETS = listOf(
    "GBK",
    "Big5",
    "UTF-8",
    "UTF-16",
    "GB18030",
    "ISO-8859-1"
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxtReaderScreen(
    byteArray: ByteArray,
    modifier: Modifier = Modifier,
    title: String = "文本阅读",
    defaultEncoding: String = "UTF-8",
    onBackClick: (() -> Unit)
) {
    val context = LocalContext.current
    // 状态定义
    var currentEncoding by rememberSaveable { mutableStateOf(defaultEncoding) }
    var fontSizeSp by rememberSaveable { mutableFloatStateOf(16f) }
    var lineHeightMultiplier by rememberSaveable { mutableFloatStateOf(1.5f) }
    var showLineNumbers by rememberSaveable { mutableStateOf(true) } // 行号显示开关

    // 控制UI组件显示
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showCharsetDialog by rememberSaveable { mutableStateOf(false) }
    var showControls by rememberSaveable { mutableStateOf(true) }

    // 异步解码文本（段落切分）
    var paragraphs by remember { mutableStateOf<List<String>>(emptyList()) }
    var isDecoding by remember { mutableStateOf(true) }
    var decodeError by remember { mutableStateOf<String?>(null) }

    // --- 新增搜索相关状态 ---
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var currentMatchIndex by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    // 获取当前 Window 实例
    val view = LocalView.current

    LaunchedEffect(byteArray, currentEncoding) {
        isDecoding = true
        decodeError = null
        withContext(Dispatchers.Default) {
            runCatching {
                val charset = Charset.forName(currentEncoding)
                val fullText = String(byteArray, charset)
                fullText.split(Regex("\r?\n"))
            }.onSuccess {
                paragraphs = it
                isDecoding = false
            }.onFailure { err ->
                decodeError =
                    "编码 '$currentEncoding' 解析失败: ${err.localizedMessage ?: "未知错误"}"
                paragraphs = emptyList()
                isDecoding = false
            }
        }
    }

    // 计算所有匹配项的位置列表
    val searchMatches = remember(paragraphs, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val list = mutableListOf<SearchMatch>()
            paragraphs.forEachIndexed { pIdx, text ->
                var startIndex = 0
                while (startIndex < text.length) {
                    val foundIndex = text.indexOf(searchQuery, startIndex, ignoreCase = true)
                    if (foundIndex == -1) break
                    list.add(SearchMatch(pIdx, foundIndex, searchQuery.length))
                    startIndex = foundIndex + searchQuery.length
                }
            }
            list
        }
    }

// 搜索匹配项改变时重置当前焦点索引
    LaunchedEffect(searchMatches) {
        currentMatchIndex = 0
    }

// 当选中的匹配项切换时，自动滚动 LazyColumn 到对应段落
    LaunchedEffect(currentMatchIndex, searchMatches) {
        if (searchMatches.isNotEmpty() && currentMatchIndex in searchMatches.indices) {
            val targetMatch = searchMatches[currentMatchIndex]
            listState.animateScrollToItem(targetMatch.paragraphIndex)
        }
    }
    // 监听 showControls 状态，同步隐藏/显示状态栏和导航栏
    DisposableEffect(showControls) {
        val window = (view.context as? Activity)?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }

        if (showControls) {
            // 显示系统状态栏与导航栏
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            // 隐藏系统状态栏与导航栏（进入全屏沉浸模式）
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            insetsController?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 当用户返回上一页 / 当前 Screen 离开 Composition 时自动执行清理
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 主题配色逻辑
    val bgContainerColor = MaterialTheme.colorScheme.background
    val contentTextColor = MaterialTheme.colorScheme.onBackground
    val focusRequester = remember { FocusRequester() }
// 打开搜索栏时自动获取焦点唤起键盘
    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = bgContainerColor,
        topBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (isSearchOpen) {
                    TopAppBarTxtReaderSearch(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onCloseSearch = {
                            isSearchOpen = false
                            searchQuery = ""
                        },
                        matchCount = searchMatches.size,
                        currentMatchIndex = currentMatchIndex,
                        onPrevMatch = {
                            if (searchMatches.isNotEmpty()) {
                                currentMatchIndex =
                                    if (currentMatchIndex > 0) currentMatchIndex - 1 else searchMatches.lastIndex
                            }
                        },
                        onNextMatch = {
                            if (searchMatches.isNotEmpty()) {
                                currentMatchIndex =
                                    if (currentMatchIndex < searchMatches.lastIndex) currentMatchIndex + 1 else 0
                            }
                        },
                        focusRequester = focusRequester
                    )
                } else {
                    TopAppBarTxtReaderNormal(
                        title = title,
                        currentEncoding = currentEncoding,
                        paragraphsCount = paragraphs.size,
                        onBackClick = onBackClick,
                        onSearchOpen = { isSearchOpen = true },
                        onShareClick = {
                            shareAsFile(context, byteArray, title)
                        },
                        onEncodingClick = {
                            showCharsetDialog = true
                        },
                        onSettingsClick = {
                            showSettingsSheet = true
                        }
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = bgContainerColor.copy(alpha = 0.95f),
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 进度显示
                        val progress = remember {
                            derivedStateOf {
                                if (paragraphs.isEmpty()) 0f
                                else {
                                    val visibleIndex = listState.firstVisibleItemIndex
                                    (visibleIndex.toFloat() / paragraphs.size.toFloat()).coerceIn(
                                        0f,
                                        1f
                                    )
                                }
                            }
                        }

                        Text(
                            text = "进度: ${(progress.value * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentTextColor.copy(alpha = 0.7f)
                        )

                        // 快捷调节按钮（字号 / 行号切换）
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 字号微调
                            IconButton(
                                onClick = { if (fontSizeSp > 12f) fontSizeSp -= 1f },
                                enabled = fontSizeSp > 12f
                            ) {
                                Icon(
                                    Icons.Default.TextDecrease,
                                    contentDescription = "缩小字体",
                                    tint = contentTextColor
                                )
                            }
                            Text(
                                text = "${fontSizeSp.toInt()}sp",
                                style = MaterialTheme.typography.labelMedium,
                                color = contentTextColor
                            )
                            IconButton(
                                onClick = { if (fontSizeSp < 32f) fontSizeSp += 1f },
                                enabled = fontSizeSp < 32f
                            ) {
                                Icon(
                                    Icons.Default.TextIncrease,
                                    contentDescription = "放大字体",
                                    tint = contentTextColor
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding()
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showControls = !showControls
                }
        ) {
            if (isDecoding) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (decodeError != null) {
                Text(
                    text = decodeError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                SelectionContainer {
                    LazyColumnScrollbar(
                        state = listState,
                        settings = ScrollbarSettings.Default.copy(
                            thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
                        )
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 800.dp)
                                .align(Alignment.TopCenter),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            itemsIndexed(paragraphs) { index, paragraph ->
                                // 1. 动态生成高亮文本（当前选中的匹配项深色高亮，其他匹配项浅色高亮）
                                val annotatedParagraph = remember(
                                    paragraph,
                                    index,
                                    searchQuery,
                                    searchMatches,
                                    currentMatchIndex
                                ) {
                                    if (searchQuery.isBlank()) {
                                        AnnotatedString(paragraph.ifBlank { " " })
                                    } else {
                                        buildAnnotatedString {
                                            val textToDraw = paragraph.ifBlank { " " }
                                            append(textToDraw)

                                            // 找出属于当前段落的所有匹配项
                                            searchMatches.forEachIndexed { globalMatchIdx, match ->
                                                if (match.paragraphIndex == index) {
                                                    val isActive =
                                                        (globalMatchIdx == currentMatchIndex)
                                                    addStyle(
                                                        style = SpanStyle(
                                                            // 当前选中的项用亮橙色背景，其他项用浅黄色背景
                                                            background = if (isActive) Color(
                                                                0xFFFF9800
                                                            ) else Color(0xFFFFE082),
                                                            color = Color.Black
                                                        ),
                                                        start = match.startChar,
                                                        end = match.startChar + match.length
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // 行号显示（Monospace 等宽字体排版）
                                    if (showLineNumbers) {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = (fontSizeSp * 0.7f).sp,
                                            lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
                                            color = contentTextColor.copy(alpha = 0.35f),
                                            fontFamily = FontFamily.Monospace,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier
                                                .width(44.dp)
                                                .padding(end = 12.dp)
                                        )
                                    }
                                    // 文本主体
                                    Text(
                                        text = annotatedParagraph,
                                        fontSize = fontSizeSp.sp,
                                        lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
                                        color = contentTextColor,
                                        fontFamily = FontFamily.Serif,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 3. 编码选择 Dialog (支持手动输入)
    // ==========================================
    if (showCharsetDialog) {
        var customInput by remember { mutableStateOf("") }
        var inputError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showCharsetDialog = false },
            title = { Text("选择或输入文件编码") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 预设编码列表
                    Text(
                        text = "常用编码预设：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    PRESET_CHARSETS.chunked(2).forEach { rowCharsets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowCharsets.forEach { charset ->
                                FilterChip(
                                    selected = (charset == currentEncoding),
                                    onClick = {
                                        currentEncoding = charset
                                        showCharsetDialog = false
                                    },
                                    label = { Text(charset) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 手动输入编码区域
                    Text(
                        text = "手动输入编码：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = customInput,
                        onValueChange = {
                            customInput = it
                            inputError = null
                        },
                        label = { Text("例如: Shift_JIS, UTF-16LE") },
                        singleLine = true,
                        isError = inputError != null,
                        supportingText = {
                            if (inputError != null) {
                                Text(text = inputError!!, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                val trimmed = customInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    if (Charset.isSupported(trimmed)) {
                                        currentEncoding = trimmed
                                        showCharsetDialog = false
                                    } else {
                                        inputError = "不支持的编码格式"
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Check, contentDescription = "应用编码")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCharsetDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ==========================================
    // 4. 阅读设置 Bottom Sheet
    // ==========================================
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                // 1. 行号显示开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "显示行号", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "在每段/每行左侧标注序号",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showLineNumbers,
                        onCheckedChange = { showLineNumbers = it }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. 字号调节
                Text(
                    text = "字号大小 (${fontSizeSp.toInt()} sp)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = fontSizeSp,
                    onValueChange = { fontSizeSp = it },
                    valueRange = 12f..30f,
                    steps = 18
                )

                // 4. 行距调节
                Text(
                    text = "行距倍数 (${String.format("%.1f", lineHeightMultiplier)}x)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = lineHeightMultiplier,
                    onValueChange = { lineHeightMultiplier = it },
                    valueRange = 1.2f..2.2f,
                    steps = 10
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * 将 ByteArray 保存为临时文件并调用系统分享
 */
private fun shareAsFile(context: android.content.Context, byteArray: ByteArray, fileName: String) {
    runCatching {
        val cacheFile = context.cacheDir
            .resolve("txt_cache")
            .apply { mkdirs() }
            .resolve(fileName)
            .apply { writeBytes(byteArray) }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            //    type = "text/plain"
            type = "*/*" // 通用 MIME 类型，允许任何能接收文件的应用打开
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "分享文件"))
    }
}
