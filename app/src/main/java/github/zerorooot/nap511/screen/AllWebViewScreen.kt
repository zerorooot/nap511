package github.zerorooot.nap511.screen

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.acsbendi.requestinspectorwebview.RequestInspectorWebViewClient
import com.acsbendi.requestinspectorwebview.WebViewRequest
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.R
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseWebViewScreen(
    titleText: String = stringResource(R.string.app_name),
    topAppBarActionButtonOnClick: () -> Unit,
    webViewClient: (WebView) -> WebViewClient,
    loadUrl: String,
    actions: @Composable () -> Unit = {}
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .padding(end = 12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = titleText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Purple80,
                        scrolledContainerColor = Purple80
                    ),
                    navigationIcon = {
                        TopAppBarActionButton(
                            imageVector = Icons.Rounded.Menu, description = "navigationIcon"
                        ) {
                            topAppBarActionButtonOnClick()
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { webViewInstance?.goBack() },
                            enabled = canGoBack
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                        IconButton(
                            onClick = { webViewInstance?.goForward() },
                            enabled = canGoForward
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Forward"
                            )
                        }
                        actions()
                    },
                    scrollBehavior = scrollBehavior
                )
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
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                WebView(context).apply {
                    webViewInstance = this
                    val originalClient = webViewClient.invoke(this)
                    this.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? =
                            originalClient.shouldInterceptRequest(view, request)

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            originalClient.onPageStarted(view, url, favicon)
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            originalClient.onPageFinished(view, url)
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                        }

                        override fun doUpdateVisitedHistory(
                            view: WebView?,
                            url: String?,
                            isReload: Boolean
                        ) {
                            originalClient.doUpdateVisitedHistory(view, url, isReload)
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: android.webkit.SslErrorHandler?,
                            error: android.net.http.SslError?
                        ) = originalClient.onReceivedSslError(view, handler, error)

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) = originalClient.onReceivedError(view, request, error)

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) = originalClient.onReceivedHttpError(view, request, errorResponse)
                    }
                    settings.apply {
                        javaScriptEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        domStorageEnabled = true
                        databaseEnabled = true
                        textZoom = 100
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        // 关键：桌面版可能需要这些
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        allowContentAccess = true
                    }

                    // 使用纯净的现代桌面端 User Agent
                    settings.userAgentString = ConfigKeyUtil.USER_AGENT
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress / 100f
                            if (newProgress > 10) {
                                // 1. 环境指纹伪装
                                view?.evaluateJavascript(
                                    """
                                        (function() {
                                            if (window._hook_fixed) return;
                                            var UA = '${ConfigKeyUtil.USER_AGENT}';
                                            Object.defineProperty(navigator, 'userAgent', { get: function(){ return UA; } });
                                            Object.defineProperty(navigator, 'platform', { get: function(){ return 'Win32'; } });
                                            Object.defineProperty(navigator, 'vendor', { get: function(){ return 'Google Inc.'; } });
                                            window.is115Browser = true;
                                            if(!window.external) window.external = {};
                                            window._hook_fixed = true;
                                        })();
                                        """.trimIndent(), null
                                )
                            }
                        }

                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            val msg = consoleMessage?.message() ?: ""
                            if (msg.contains("failed") || msg.contains("error") || msg.contains("403")) {
                                XLog.e("WebView_ERROR: $msg")
                            }
                            return true
                        }
                    }

                    val headers = HashMap<String, String>()
                    headers["X-Requested-With"] = ""
                    loadUrl(loadUrl, headers)
                }
            }, update = { webView ->
                webViewInstance = webView
            })
        }

    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun WebViewScreen(onClick: () -> Unit) {
    var isReady by remember { mutableStateOf(false) }
    val initialUrl = "https://115.com/storage/allfiles?cid=0&mode=wangpan"
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // 用于驱动 Compose UI 实时响应当前 URL 的状态
    var currentUrl by remember { mutableStateOf(initialUrl) }
    val rootUrls = remember {
        setOf(
            "https://115.com/?cid=0&offset=0&mode=wangpan",
            "https://115.com/storage/allfiles?cid=0&mode=wangpan",
            "https://115.com/?cid=0&offset=0&tab=&mode=wangpan",
            "https://115.com/storage/allfiles"
        )
    }

    BackHandler(currentUrl !in rootUrls) {
        webViewRef?.let {
            if (it.canGoBack()) {
                it.goBack()
            }
        }
    }
    LaunchedEffect(Unit) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        WebView.setWebContentsDebuggingEnabled(DataStoreUtil.getData(ConfigKeyUtil.LOG, false))
        // 1. 注入 Cookie
        setRawCookieString(App.cookie)
        // 2. 强制显式同步并引入物理延迟，确保 API 请求发起时 Cookie 已在磁盘就绪
        cookieManager.flush()

        isReady = true
    }

    if (isReady) {
        BaseWebViewScreen(
            titleText = currentUrl,
            topAppBarActionButtonOnClick = onClick,
            webViewClient = {
                webViewRef = it
                webViewClient { u ->
                    currentUrl = u
                }
            },
            loadUrl = initialUrl
        )
    }
}

fun setRawCookieString(rawCookieString: String) {
    XLog.d("setRawCookieString start, length: ${rawCookieString.length}")
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)

    // 只取 key=value 核心部分，彻底移除多余属性
    val cookiePairs = rawCookieString.split(";").map { it.trim() }.filter { it.contains("=") }

    val domains = arrayOf(".115.com", "115.com", "webapi.115.com", "cdnassets.115.com", "anxia.com")

    cookiePairs.forEach { pair ->
        domains.forEach { domain ->
            cookieManager.setCookie("https://$domain", "$pair; Domain=.115.com; Path=/")
        }
    }
    // 强制注入旧版模式标记，规避 Next.js 兼容性黑洞
    cookieManager.setCookie("https://115.com", "OO_V=2014; Domain=.115.com; Path=/")

    cookieManager.flush()
    XLog.d("setRawCookieString finished with OO_V=2014")
}

fun webViewClient(onUrl: (String) -> Unit): WebViewClient {
    return object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView, request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url.toString()
            val headers = request.requestHeaders

            // 追踪关键资源加载
            if (url.contains("115.com")) {
                if (url.contains(".js") || url.contains(".css") || url.contains("/api/")) {
                    XLog.v("WebView Requesting: $url")
                }
            }

            if (headers.containsKey("X-Requested-With")) {
                val newHeaders = HashMap(headers)
                newHeaders.remove("X-Requested-With")
                // 注意：如果只是返回 null，WebView 仍会发送原请求。
                // 这里我们仅做日志记录，具体修改 headers 可能需要拦截并重新发起（略复杂）
                XLog.d("WebView stripped X-Requested-With for $url")
            }
            return null
        }

        // 页面开始加载时获取 URL
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            url?.let { onUrl.invoke(it) }
        }

        // 页面加载完成时确认 URL（处理重定向后的最终地址）
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            view?.url?.let { onUrl.invoke(it) }
            XLog.d("WebView Page Finished: $url")

            view?.evaluateJavascript(
                """
                (function() {
                    function applyFix() {
                        var styleId = '115-core-fix';
                        var pxHeight = window.innerHeight + 'px';
                        var style = document.getElementById(styleId);
                        if (!style) {
                            style = document.createElement('style');
                            style.id = styleId;
                            document.head.appendChild(style);
                        }
                        style.textContent = `
                            html, body, #__next, [class*="h-screen"] {
                                height: ${'$'}{pxHeight} !important;
                                min-height: ${'$'}{pxHeight} !important;
                            }
                            body { display: block !important; overflow: auto !important; }
                            #js_mainContent, .layout-main, .layout-content {
                                overflow: auto !important;
                                min-height: 100% !important;
                            }
                            .flex.relative.min-w-\[800px\] { min-width: 800px !important; }
                            .v-modal, [class*="mask"], [class*="loading"] { display: none !important; pointer-events: none !important; }
                        `;
                    }
                    
                    applyFix();
                    // 115 页面会多次重绘，采用轮询确保修复持久生效
                    var count = 0;
                    var itv = setInterval(function() {
                        applyFix();
                        if(++count > 10) clearInterval(itv);
                    }, 1000);
                })();
                """.trimIndent(), null
            )
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: android.webkit.SslErrorHandler?,
            error: android.net.http.SslError?
        ) {
            handler?.proceed()
        }

        override fun onReceivedError(
            view: WebView?, request: WebResourceRequest?, error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            XLog.e("WebView Error: ${error?.description} (code: ${error?.errorCode}) for URL: ${request?.url}")
            if (request?.isForMainFrame == true) {
                App.instance.toast("网页加载错误: ${error?.description}")
            }
        }

        override fun onReceivedHttpError(
            view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?
        ) {
            super.onReceivedHttpError(view, request, errorResponse)
            if (request?.url.toString().contains("115.com")) {
                XLog.e("WebView HTTP Error: ${errorResponse?.statusCode} for URL: ${request?.url}")
            }
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            super.doUpdateVisitedHistory(view, url, isReload)
            // 应对单页应用（SPA）无刷新路由切换时的 URL 变化
            view?.url?.let { onUrl.invoke(it) }
        }
    }
}

@Composable
fun LoginWebViewScreen(onClick: () -> Unit) {
    BaseWebViewScreen(
        titleText = "通过网页登陆",
        topAppBarActionButtonOnClick = onClick,
        webViewClient = { loginWebViewClient(it) },
        loadUrl = "https://115.com/"
    )
}

fun loginWebViewClient(webView: WebView): WebViewClient {
    return object : RequestInspectorWebViewClient(webView) {
        override fun shouldInterceptRequest(
            view: WebView, webViewRequest: WebViewRequest
        ): WebResourceResponse? {
            var cookie: String? = null
            val urlList = setOf(
                "https://115.com/storage/netdisk",
                "https://115.com/storage/allfiles",
                "https://115.com/storage/netdisk?cid=0&mode=wangpan",
                "https://115.com/?cid=0&offset=0&mode=wangpan",
                "https://my.115.com/?ct=guide&ac=status"
            )

//            for ((_, it) in urlList.withIndex()) {
//                if (it == webViewRequest.url) {
//                    cookie = CookieManager.getInstance().getCookie(it)
//                    XLog.d("$it cookie $cookie")
//                    break
//                }
//            }
            val url = webViewRequest.url
            if (url in urlList) {
                cookie = CookieManager.getInstance().getCookie(url)
                XLog.d("$url cookie $cookie")
            }

            if (cookie != null) {
                runBlocking {
                    App.instance.checkLogin(cookie)
                }
            }
            return super.shouldInterceptRequest(view, webViewRequest)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            url?.let {
                val cookie = CookieManager.getInstance().getCookie(it)
                if (!cookie.isNullOrBlank()) {
                    XLog.d("loginWebViewClient onPageFinished cookie length: ${cookie.length}")
                }
            }
            // 登录页面也注入诊断，防止登录也白屏
            view?.evaluateJavascript(
                "(function() { return {url: window.location.href, title: document.title, elements: document.getElementsByTagName('*').length}; })();",
                { result -> XLog.d("LOGIN_DIAG_DATA: $result") }
            )
        }
    }

}

@Composable
fun CaptchaWebViewScreen(fileViewModel: FileViewModel, onNav: (String) -> Unit) {
    val cookieManager = CookieManager.getInstance()
    App.cookie.split(";").forEach { a ->
        cookieManager.setCookie("https://captchaapi.115.com", a)
        cookieManager.setCookie("https://webapi.115.com", a)
        cookieManager.setCookie("https://webapi.115.com/user/captcha", a)
    }
    cookieManager.flush()

    BaseWebViewScreen(
        titleText = "磁力链接验证码",
        topAppBarActionButtonOnClick = {
            onNav.invoke("topAppBarActionButtonOnClick")
        },
        webViewClient = {
            captchaWebViewClient(fileViewModel, it) { gesture, select ->
                if (gesture) {
                    fileViewModel.gesturesEnabled = true
                }
                if (select) {
                    onNav.invoke("select")
                }
            }
        },
        loadUrl = "https://captchaapi.115.com/?ac=security_code&type=web&cb=Close911_" + System.currentTimeMillis()
    )

}

@Composable
fun CaptchaVideoWebViewScreen(fileViewModel: FileViewModel, onNav: (String) -> Unit) {
    val cookieManager = CookieManager.getInstance()

    App.cookie.split(";").forEach { a ->
        cookieManager.setCookie("https://115vod.com/captchaapi/", a)
        cookieManager.setCookie("https://115vod.com/webapi/user/captcha", a)
    }
    cookieManager.flush()
    BaseWebViewScreen(
        titleText = "视频播放验证码",
        topAppBarActionButtonOnClick = {
            onNav.invoke("topAppBarActionButtonOnClick")
        },
        webViewClient = {
            captchaWebViewClient(fileViewModel, it) { gesture, select ->
                if (gesture) {
                    fileViewModel.gesturesEnabled = true
                }
                if (select) {
                    onNav.invoke("select")
                }
            }
        },
        loadUrl = "https://115vod.com/captchaapi/?ac=security_code&client=web&type=web&ctype=web&cb=Close911_" + System.currentTimeMillis()
    )

}

fun captchaWebViewClient(
    fileViewModel: FileViewModel, webView: WebView, handle: (Boolean, Boolean) -> Unit
): WebViewClient {
    return object : RequestInspectorWebViewClient(webView) {
        override fun shouldInterceptRequest(
            view: WebView, webViewRequest: WebViewRequest
        ): WebResourceResponse? {
            //磁力链接验证
            if ("https://webapi.115.com/user/captcha" == webViewRequest.url) {
                if (check("https://webapi.115.com/user/captcha", webViewRequest, handle)) {
                    fileViewModel.handleOfflineTask()
                    App.instance.toast("验证账号成功~，重新添加链接中.......")
                }
            }
            //视频验证
            if ("https://115vod.com/webapi/user/captcha" == webViewRequest.url) {
                if (check("https://115vod.com/webapi/user/captcha", webViewRequest, handle)) {
                    App.instance.toast("视频验证成功~")
                }
            }
            return super.shouldInterceptRequest(view, webViewRequest)
        }
    }
}

private fun check(
    url: String, webViewRequest: WebViewRequest, handle: (Boolean, Boolean) -> Unit
): Boolean {
    val httpClient = OkHttpClient()
    val a = Request.Builder().url(url).method("POST", webViewRequest.body.toRequestBody())
    webViewRequest.headers.forEach { (t, u) -> a.addHeader(t, u) }
    //移除web添加的cookie
    a.removeHeader("cookie")
    a.addHeader("cookie", App.cookie)

    val response = httpClient.newCall(a.build()).execute()
    val string = response.body.string()
    //启用手势，不跳转页面
    handle.invoke(true, false)

    if (string.contains("{\"state\":true}")) {
        //启用手势，跳转页面
        handle.invoke(true, true)
        return true
    }
    return false
}
