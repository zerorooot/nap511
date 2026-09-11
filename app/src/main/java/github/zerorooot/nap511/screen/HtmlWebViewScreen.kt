package github.zerorooot.nap511.screen

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import github.zerorooot.nap511.util.App
import java.io.File
import java.nio.charset.Charset

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlWebViewScreen(
    byteArray: ByteArray,
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }

    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matchCount by remember { mutableIntStateOf(0) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    var currentEncoding by remember { mutableStateOf("UTF-8") }
    var showEncodingDialog by remember { mutableStateOf(false) }

    val htmlContent = remember(byteArray, currentEncoding) {
        runCatching { String(byteArray, Charset.forName(currentEncoding)) }.getOrDefault("")
    }

    LaunchedEffect(webViewInstance) {
        webViewInstance?.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) {
                currentMatchIndex = activeMatchOrdinal
                matchCount = numberOfMatches
            }
        }
    }

    LaunchedEffect(htmlContent, webViewInstance) {
        webViewInstance?.loadDataWithBaseURL(null, htmlContent, "text/html", currentEncoding, null)
    }

    BackHandler {
        if (isSearchOpen) {
            isSearchOpen = false
            searchQuery = ""
            webViewInstance?.clearMatches()
        } else if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            Column {
                if (isSearchOpen) {
                    TopAppBarSearch(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { query ->
                            searchQuery = query
                            if (query.isNotEmpty()) {
                                webViewInstance?.findAllAsync(query)
                            } else {
                                webViewInstance?.clearMatches()
                                matchCount = 0
                                currentMatchIndex = 0
                            }
                        },
                        onCloseSearch = {
                            isSearchOpen = false
                            searchQuery = ""
                            webViewInstance?.clearMatches()
                        },
                        matchCount = matchCount,
                        currentMatchIndex = currentMatchIndex,
                        onPrevMatch = { webViewInstance?.findNext(false) },
                        onNextMatch = { webViewInstance?.findNext(true) },
                        placeholderText = "页内查找...",
                        focusRequester = focusRequester
                    )
                } else {
                    BaseTopAppBar(
                        title = {
                            Text(
                                text = title,
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回"
                                )
                            }
                        },
                        actions = {
                            HtmlWebViewTopBarDropdownMenu(onClick = { itemValue, _ ->
                                when (itemValue) {
                                    "页面刷新" -> webViewInstance?.reload()
                                    "页内查找" -> isSearchOpen = true
                                    "分享链接" -> shareLink(
                                        context,
                                        webViewInstance?.url ?: htmlContent,
                                        title
                                    )
                                    "修改编码" -> showEncodingDialog = true
                                }
                            })
                        }
                    )
                }
                if (progress < 1f && progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewInstance = this
                        applyDefaultSettings()
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            @SuppressLint("WebViewClientOnReceivedSslError")
                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: android.webkit.SslErrorHandler?,
                                error: android.net.http.SslError?
                            ) {
                                handler?.proceed()
                            }
                        }
                        loadDataWithBaseURL(null, htmlContent, "text/html", currentEncoding, null)
                    }
                },
                update = { webView ->
                    webViewInstance = webView
                }
            )
        }
    }

    if (showEncodingDialog) {
        val encodings = listOf("UTF-8", "GBK", "GB2312", "BIG5", "ISO-8859-1")
        AlertDialog(
            onDismissRequest = { showEncodingDialog = false },
            title = { Text("修改编码") },
            text = {
                Column {
                    encodings.forEach { encoding ->
                        TextButton(
                            onClick = {
                                currentEncoding = encoding
                                showEncodingDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (encoding == currentEncoding) "$encoding (当前)" else encoding,
                                color = if (encoding == currentEncoding) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEncodingDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}


private fun shareLink(context: Context, content: String, title: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        context.startActivity(Intent.createChooser(intent, "分享链接"))
    }
}
