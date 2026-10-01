package github.zerorooot.nap511.screen.web

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.acsbendi.requestinspectorwebview.RequestInspectorWebViewClient
import com.acsbendi.requestinspectorwebview.WebViewRequest
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.repository.AuthRepository
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.network.NetworkClient
import github.zerorooot.nap511.util.network.UserSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@SuppressLint("SetJavaScriptEnabled")
fun WebView.applyDefaultSettings() {
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
        mediaPlaybackRequiresUserGesture = false
        allowFileAccess = true
        allowContentAccess = true
    }
    settings.userAgentString = ConfigKeyUtil.USER_AGENT
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseWebViewScreen(
    topAppBarActionButtonOnClick: () -> Unit,
    showTopBarButton: Boolean = true,
    //全局默认窗口边距，能避开系统状态栏、手势操作条、横屏时的屏幕刘海/挖孔避让区（Display Cutout）或侧边导航栏宽度
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    webViewClient: (WebView) -> WebViewClient,
    loadUrl: String
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = contentWindowInsets,
        topBar = {
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
        }) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box {
                AndroidView(
                    modifier = Modifier.fillMaxSize(), factory = { context ->
                        WebView(context).apply {
                            webViewInstance = this
                            this.webViewClient = webViewClient.invoke(this)
                            applyDefaultSettings()
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

                                // 2. 网页 Console 日志捕获（拦截 JS 报错，方便 Logcat 过滤排查）
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    val msg = consoleMessage?.message() ?: ""
                                    val source = consoleMessage?.sourceId() ?: ""
                                    val line = consoleMessage?.lineNumber() ?: 0
                                    if (msg.contains("failed") || msg.contains("error") || msg.contains(
                                            "403"
                                        ) || msg.contains("401")
                                    ) {
                                        XLog.e("WebView CRITICAL ERROR: $msg -- From line $line of $source")
                                    } else {
                                        XLog.d("WebView Console [${consoleMessage?.messageLevel()}]: $msg -- From line $line of $source")
                                    }
                                    return true
                                }

                                // 3. 页面 Title 监测（监听 SPA 无刷新路由切换，触发诊断）
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    XLog.d("WebView Title: $title")
                                    if (title?.contains("全部文件") == true || title?.contains("115") == true) {
                                        view?.evaluateJavascript(
                                            "if(window.runDiagnostic) window.runDiagnostic('TITLE_CHANGE_' + document.title);",
                                            null
                                        )
                                    }
                                }
                            }
                            val headers = HashMap<String, String>()
                            headers["X-Requested-With"] = ""
                            loadUrl(loadUrl, headers)
                        }
                    }, update = { webView ->
                        webViewInstance = webView
                    },
                    onRelease = { webView ->
                        // 离开 Composition 时显式释放 WebView
                        webView.stopLoading()
                        webView.loadUrl("about:blank")
                        webView.clearHistory()
                        webView.removeAllViews()
                        webView.destroy()
                    })

                if (showTopBarButton) {
                    IconButton(
                        onClick = {
                            topAppBarActionButtonOnClick.invoke()
                        },
                        modifier = Modifier
                            .padding(start = 12.dp, top = 8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "打开侧边栏菜单",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
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
        WebView.setWebContentsDebuggingEnabled(
            SettingsRepository.getDataSuspend(
                ConfigKeyUtil.LOG, false
            )
        )
        // 1. 注入 Cookie
        setRawCookieString(UserSessionManager.cookie)
        // 2. 强制显式同步并引入物理延迟，确保 API 请求发起时 Cookie 已在磁盘就绪
        cookieManager.flush()

        isReady = true
    }

    if (isReady) {
        BaseWebViewScreen(
            topAppBarActionButtonOnClick = onClick, webViewClient = {
                webViewRef = it
                webViewClient { u ->
                    currentUrl = u
                }
            }, loadUrl = initialUrl
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

    cookieManager.removeAllCookies {
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
                XLog.v("WebView stripped X-Requested-With for $url")
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

            /*
             * 调试排查说明：可在 Logcat 中过滤以下 Tag/关键词：
             * - "DIAG_DATA": JS 采集打印的 DOM 节点数量、视口宽高、Body 高度、子元素排版结构与文件表头位置
             * - "POPUP_DIALOG_DETECTED": 动态监听并打印弹出的对话框（如“普通上传”、“添加云下载”）信息
             * - "WebView Console": 网页原生 console.log 日志
             * - "WebView CRITICAL ERROR": 网页原生 JS 运行/请求报错信息
             * - "FULL_HTML": 查看 dump 的完整 DOM HTML 源码
             */
            // 核心 UI 修复逻辑（跨版本兼容与样式重置）
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
                            /* 1. 强撑高度：解决 vh 失效导致的白屏 */
                            html, body, #__next, [class*="h-screen"] {
                                height: ${'$'}{pxHeight} !important;
                                min-height: ${'$'}{pxHeight} !important;
                            }
                            body { display: block !important; overflow: auto !important; }
                               /* 2. 强制显示：解决 overflow: hidden 导致的内容不可见 */
                            #js_mainContent, .layout-main, .layout-content {
                                overflow: auto !important;
                                min-height: 100% !important;
                            }
                              /* 3. 桌面适配：防止窄屏下主分栏 UI 挤压错位 */
                            .flex.relative.min-w-\[800px\] { min-width: 800px !important; }
                             /* 4. 遮罩清除：隐藏全屏阻挡视线的加载指示器 */
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

            // 调试诊断与日志输出逻辑（仅 DEBUG 模式下生效）
            if (github.zerorooot.nap511.BuildConfig.DEBUG) {
                view?.evaluateJavascript(
                    """
                    (function() {
                        // 诊断函数：采集并分析当前 DOM 结构与节点排版
                        window.runDiagnostic = function(reason) {
                            var body = document.body;
                            var info = {
                                reason: reason || 'FINISH',
                                url: window.location.href,
                                title: document.title,
                                wh: window.innerWidth + 'x' + window.innerHeight,
                                body_h: body ? body.offsetHeight : -1,
                                elems: document.getElementsByTagName('*').length,
                                // 输出 Body 下直接一级子元素的排版结构与高度摘要（排查高度塌陷/错位）
                                structure: body ? Array.from(body.children).map(function(c) {
                                    return c.tagName + '.' + c.className.split(' ').join('.') + 
                                           '(' + c.offsetHeight + 'px) -> ' + 
                                           (c.innerText ? c.innerText.substring(0, 15).replace(/\n/g, ' ') : 'EMPTY');
                                }) : [],
                                // 检查文件列表标头（“文件名”/“大小”）位置与可见性
                                list_check: (function(){
                                    var t = Array.from(document.querySelectorAll('*')).find(function(el) { 
                                        return el.innerText && (el.innerText === '文件名' || el.innerText === '大小') && el.children.length === 0;
                                    });
                                    return t ? { tag: t.tagName, rect: t.getBoundingClientRect() } : 'LIST_HEADER_NOT_FOUND';
                                })()
                            };
                            console.log("DIAG_DATA: " + JSON.stringify(info));
                        };

                        // 详细分析并打印当前 DOM 中所有弹窗、对话框、遮罩层的实时计算样式与位置
                        window.logDialogDetails = function(triggerTag) {
                            var targets = document.querySelectorAll('.dialog-box, [id*="window_"], .upload-box, .offline-box, .window-current, [class*="dialog"], [class*="offline"], [class*="upload"], [class*="mask"], [class*="modal"]');
                            if (!targets || targets.length === 0) {
                                console.log("DIALOG_LOG [" + triggerTag + "]: No dialog/mask element found in DOM");
                                return;
                            }
                            var logs = [];
                            targets.forEach(function(el, idx) {
                                var rect = el.getBoundingClientRect();
                                var cs = window.getComputedStyle(el);
                                var parent = el.parentElement;
                                var pcs = parent ? window.getComputedStyle(parent) : {};
                                logs.push({
                                    idx: idx,
                                    id: el.id,
                                    cls: el.className,
                                    rect: [Math.round(rect.left), Math.round(rect.top), Math.round(rect.width), Math.round(rect.height)],
                                    style: {
                                        display: cs.display,
                                        vis: cs.visibility,
                                        op: cs.opacity,
                                        zIndex: cs.zIndex,
                                        pos: cs.position,
                                        top: cs.top,
                                        left: cs.left,
                                        width: cs.width,
                                        height: cs.height,
                                        filter: cs.filter,
                                        bFilter: cs.backdropFilter
                                    },
                                    parent: parent ? {
                                        tag: parent.tagName,
                                        id: parent.id,
                                        cls: parent.className,
                                        filter: pcs.filter,
                                        op: pcs.opacity,
                                        display: pcs.display
                                    } : 'NONE',
                                    text: el.innerText ? el.innerText.substring(0, 30).replace(/\n/g, ' ') : ''
                                });
                            });
                            console.log("DIALOG_LOG [" + triggerTag + "]: " + JSON.stringify(logs));
                        };

                        // 全局点击/触摸拦截：当点击页面任何按钮时，延迟多次记录弹窗节点状态
                        document.addEventListener('click', function(e) {
                            var t = e.target;
                            var txt = t ? (t.innerText || t.value || t.className || t.tagName) : '';
                            console.log("DIALOG_CLICK_EVENT: target=" + t.tagName + "." + t.className + " text=" + String(txt).substring(0, 30).replace(/\n/g, ' '));
                            setTimeout(function() { if (window.logDialogDetails) window.logDialogDetails('AFTER_CLICK_100ms'); }, 100);
                            setTimeout(function() { if (window.logDialogDetails) window.logDialogDetails('AFTER_CLICK_500ms'); }, 500);
                            setTimeout(function() { if (window.logDialogDetails) window.logDialogDetails('AFTER_CLICK_1200ms'); }, 1200);
                        }, true);

                        // DOM 变动观察器：监听 DOM 节点新增/属性变更并打印日志
                        if (!window._dialog_observer) {
                            window._dialog_observer = new MutationObserver(function(mutations) {
                                var shouldLog = false;
                                mutations.forEach(function(m) {
                                    if (m.type === 'childList') {
                                        m.addedNodes.forEach(function(node) {
                                            if (node.nodeType === 1) {
                                                var str = (node.className || '') + ' ' + (node.id || '');
                                                if (/dialog|upload|offline|window|mask|modal|overlay/i.test(str)) {
                                                    shouldLog = true;
                                                }
                                            }
                                        });
                                    } else if (m.type === 'attributes') {
                                        var target = m.target;
                                        var str = (target.className || '') + ' ' + (target.id || '');
                                        if (/dialog|upload|offline|window|mask|modal|overlay/i.test(str)) {
                                            shouldLog = true;
                                        }
                                    }
                                });
                                if (shouldLog) {
                                    if (window.logDialogDetails) window.logDialogDetails('MUTATION_DETECTED');
                                }
                            });
                            window._dialog_observer.observe(document.documentElement, {
                                childList: true,
                                subtree: true,
                                attributes: true,
                                attributeFilter: ['style', 'class']
                            });
                        }

                        if (window.runDiagnostic) window.runDiagnostic('INITIAL');

                        // 持续轮询：定期扫描当前弹窗
                        var count = 0;
                        var itv = setInterval(function() {
                            if (count % 3 === 0) {
                                var dlg = document.querySelector('.dialog-box, [id*="window_"], .upload-box, .offline-box, .window-current');
                                if (dlg && window.logDialogDetails) {
                                    window.logDialogDetails('POLL_FOUND_DIALOG');
                                }
                            }
                            if (++count > 60) clearInterval(itv);
                        }, 1000);
                    })();
                    """.trimIndent(), null
                )
            }
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
                XLog.v("$url cookie $cookie")
            }

            if (cookie != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    AuthRepository.checkLogin(cookie)
                        .onSuccess { App.instance.toast("登录成功～") }
                        .onFailure { App.instance.toast("验证失败: ${it.localizedMessage}") }
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
                "(function() { return {url: window.location.href, title: document.title, elements: document.getElementsByTagName('*').length}; })();"
            ) { result -> XLog.v("LOGIN_DIAG_DATA: $result") }
        }
    }

}

@Composable
fun CaptchaWebViewScreen(
    handleOfflineTask: () -> Unit, onNav: (String) -> Unit
) {
    LaunchedEffect(Unit) {
        val cookieManager = CookieManager.getInstance()
        UserSessionManager.cookie.split(";").forEach { a ->
            cookieManager.setCookie("https://captchaapi.115.com", a)
            cookieManager.setCookie("https://webapi.115.com", a)
            cookieManager.setCookie("https://webapi.115.com/user/captcha", a)
        }
        cookieManager.flush()
    }
    BaseWebViewScreen(
        topAppBarActionButtonOnClick = {
            onNav.invoke("topAppBarActionButtonOnClick")
        },
        webViewClient = {
            captchaWebViewClient(it, handleOfflineTask) { select ->
                if (select) {
                    onNav.invoke("select")
                }
            }
        },
        loadUrl = "https://captchaapi.115.com/?ac=security_code&type=web&cb=Close911_" + System.currentTimeMillis()
    )

}

@Composable
fun CaptchaVideoWebViewScreen(
    showTopBarButton: Boolean = true,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    onNav: (String) -> Unit
) {
    LaunchedEffect(Unit) {
        val cookieManager = CookieManager.getInstance()
        UserSessionManager.cookie.split(";").forEach { a ->
            cookieManager.setCookie("https://115vod.com/captchaapi/", a)
            cookieManager.setCookie("https://115vod.com/webapi/user/captcha", a)
        }
        cookieManager.flush()
    }

    BaseWebViewScreen(
        showTopBarButton = showTopBarButton,
        contentWindowInsets = contentWindowInsets,
        topAppBarActionButtonOnClick = {
            onNav.invoke("topAppBarActionButtonOnClick")
        },
        webViewClient = {
            captchaWebViewClient(it) { select ->
                if (select) {
                    onNav.invoke("select")
                }
            }
        },
        loadUrl = "https://115vod.com/captchaapi/?ac=security_code&client=web&type=web&ctype=web&cb=Close911_" + System.currentTimeMillis()
    )

}

fun captchaWebViewClient(
    webView: WebView,
    handleOfflineTask: (() -> Unit)? = null,
    nav: (Boolean) -> Unit,
): WebViewClient {
    return object : RequestInspectorWebViewClient(webView) {
        override fun shouldInterceptRequest(
            view: WebView, webViewRequest: WebViewRequest
        ): WebResourceResponse? {
            //磁力链接验证
            if ("https://webapi.115.com/user/captcha" == webViewRequest.url) {
                if (check("https://webapi.115.com/user/captcha", webViewRequest, nav)) {
                    handleOfflineTask?.invoke()
                    App.instance.toast("验证账号成功，重新添加链接中")
                }
            }
            //视频验证
            if ("https://115vod.com/webapi/user/captcha" == webViewRequest.url) {
                if (check("https://115vod.com/webapi/user/captcha", webViewRequest, nav)) {
                    App.instance.toast("视频验证成功~")
                }
            }
            return super.shouldInterceptRequest(view, webViewRequest)
        }
    }
}

private fun check(
    url: String, webViewRequest: WebViewRequest, nav: (Boolean) -> Unit
): Boolean {
    val httpClient = NetworkClient.sharedOkHttpClient
    val a = Request.Builder().url(url).method("POST", webViewRequest.body.toRequestBody())
    webViewRequest.headers.forEach { (t, u) -> a.addHeader(t, u) }
    //移除web添加的cookie
    a.removeHeader("cookie")
    a.addHeader("cookie", UserSessionManager.cookie)

    val response = httpClient.newCall(a.build()).execute()
    val string = response.body.string()
    XLog.d("captchaWebViewClient $string")
    //启用手势，不跳转页面
    nav.invoke(false)

    if (string.contains("{\"state\":true}")) {
        //启用手势，跳转页面
        nav.invoke(true)
        return true
    }
    return false
}
