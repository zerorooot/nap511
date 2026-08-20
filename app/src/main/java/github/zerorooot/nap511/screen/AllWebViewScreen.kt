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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.acsbendi.requestinspectorwebview.RequestInspectorWebViewClient
import com.acsbendi.requestinspectorwebview.WebViewRequest
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.R
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.time.Duration.Companion.milliseconds

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

    Column {
        TopAppBar(
            title = {
                Text(text = titleText)
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Purple80),
            navigationIcon = {
                TopAppBarActionButton(
                    imageVector = Icons.Rounded.Menu, description = "navigationIcon"
                ) {
                    topAppBarActionButtonOnClick()
                }
            },
            actions = {
                actions()
            })
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
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                WebView(context).apply {
                    webViewInstance = this
                    this.webViewClient = webViewClient.invoke(this)
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
                            if (newProgress == 100) {
                                XLog.d("WebView Progress 100, attempting auto dump")
                                view?.evaluateJavascript(
                                    "(function() { return document.documentElement.outerHTML; })();"
                                ) { result ->
                                    if (result != null && result != "null") {
                                        XLog.d("PROGRESS_DUMP_SUCCESS")
                                        dumpFullHtml(result)
                                    }
                                }
                            }
                            if (newProgress > 5) {
                                // 深度伪装环境 + API 拦截监控
                                view?.evaluateJavascript(
                                    """
                                        (function() {
                                            if (window._hook_fixed) return;
                                            Object.defineProperty(navigator, 'userAgent', { get: function(){ return '${ConfigKeyUtil.USER_AGENT}'; } });
                                            Object.defineProperty(navigator, 'platform', { get: function(){ return 'Win32'; } });
                                            Object.defineProperty(navigator, 'webdriver', { get: function(){ return false; } });
                                            window.is115Browser = true;
                                            window._hook_fixed = true;
                                        })();
                                        """.trimIndent(), null
                                )
                            }
                        }

                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            val msg = consoleMessage?.message() ?: ""
                            val source = consoleMessage?.sourceId() ?: ""
                            val line = consoleMessage?.lineNumber() ?: 0
                            XLog.d("WebView Console: $msg -- From line $line of $source")

                            // 特别识别 API 错误
                            if (msg.contains("failed") || msg.contains("error") || msg.contains(
                                    "403"
                                ) || msg.contains("401")
                            ) {
                                XLog.e("WebView CRITICAL ERROR: $msg")
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
    val url = "https://115.com/?cid=0&offset=0&mode=wangpan"
    var isReady by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // 用于驱动 Compose UI 实时响应当前 URL 的状态
    var currentUrl by remember { mutableStateOf(url) }
    val rootUrls = remember {
        setOf(
            url, "https://115.com/?cid=0&offset=0&tab=&mode=wangpan"
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
        XLog.d("WebViewScreen LaunchedEffect, cookie length: ${App.cookie.length}")
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        WebView.setWebContentsDebuggingEnabled(true)

        // 1. 注入 Cookie
        setRawCookieString(App.cookie)

        // 2. 强制显式同步并引入物理延迟，确保 API 请求发起时 Cookie 已在磁盘就绪
        cookieManager.flush()
        kotlinx.coroutines.delay(1500.milliseconds)

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
            loadUrl = url,
            actions = {
                TopAppBarActionButton(
                    imageVector = Icons.Rounded.Print, description = "Dump HTML"
                ) {
                    webViewRef?.evaluateJavascript(
                        "(function() { return document.documentElement.outerHTML; })();"
                    ) { result ->
                        XLog.d("MANUAL_DUMP_RECEIVED")
                        dumpFullHtml(result)
                    }
                }
                TopAppBarActionButton(
                    imageVector = Icons.Rounded.Refresh, description = "Refresh"
                ) {
                    webViewRef?.reload()
                }
            })
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
            val headers = request.requestHeaders
            if (headers.containsKey("X-Requested-With")) {
                headers.remove("X-Requested-With")
            }
            // 关键：彻底剥离 Android WebView 标识
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

            // 核心修复脚本：
            // 1. 暴力移除加载屏蔽层
            // 2. 强制 body 可见
            // 3. 诊断与源码导出
            view?.evaluateJavascript(
                """
                (function() {
                    function bruteForceVisible() {
                        // 移除所有可能的遮罩层 (加载中、验证中)
                        var selectors = [
                            '[class*="loading"]', '[id*="loading"]', 
                            '[class*="mask"]', '[id*="mask"]',
                            '[class*="overlay"]'
                        ];
                        selectors.forEach(function(s) {
                            document.querySelectorAll(s).forEach(function(el) { 
                                // 只有当元素占满全屏且透明或带动画时才移除，避免误删正常 UI
                                if(el.offsetHeight > window.innerHeight * 0.8) {
                                    el.style.display = 'none';
                                    el.style.opacity = '0';
                                }
                            });
                        });

                        document.body.style.opacity = '1';
                        document.body.style.visibility = 'visible';
                        document.body.style.display = 'block';
                        
                        // 穿透透明度死锁
                        var styleId = 'force-visible-brute';
                        var style = document.getElementById(styleId);
                        if (!style) {
                            style = document.createElement('style');
                            style.id = styleId;
                            style.innerHTML = 'body { opacity: 1 !important; visibility: visible !important; } .jsx-e02dea5b1df978c2 { opacity: 1 !important; display: block !important; }';
                            document.head.appendChild(style);
                        }
                    }
                    
                    bruteForceVisible();
                    // 持续巡检，防止 JS 框架重新生成遮罩
                    var count = 0;
                    var itv = setInterval(function() {
                        bruteForceVisible();
                        if(++count > 10) clearInterval(itv);
                    }, 1000);
                    
                    try { return document.documentElement.outerHTML; } catch(e) { return 'ERROR: ' + e.message; }
                })();
                """.trimIndent()
            ) { result ->
                XLog.d("AUTO_DUMP_RECEIVED for $url")
                dumpFullHtml(result)
            }
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

private fun dumpFullHtml(jsonResult: String?) {
    if (jsonResult == null || jsonResult == "null") {
        XLog.e("dumpFullHtml: Result is null")
        return
    }
    try {
        // 由于返回的是 JSON 化的字符串，包含转义，这里简单处理或直接输出关键部分
        XLog.d("FULL_HTML_START")
        val chunkSize = 3000
        var index = 0
        while (index < jsonResult.length) {
            val end = (index + chunkSize).coerceAtMost(jsonResult.length)
            XLog.d("FULL_HTML_PART: ${jsonResult.substring(index, end)}")
            index = end
        }
        XLog.d("FULL_HTML_END")
    } catch (e: Exception) {
        XLog.e("dumpFullHtml error", e)
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
                    XLog.d("loginWebViewClient onPageFinished cookie $cookie")
                }
            }
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
