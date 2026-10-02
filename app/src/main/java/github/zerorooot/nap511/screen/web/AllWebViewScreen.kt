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
        setRawCookieString(UserSessionManager.cookie) {
            isReady = true
        }
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

fun setRawCookieString(rawCookieString: String, isReady: () -> Unit) {
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
        isReady.invoke()
    }
}
private val logKeywords = listOf(
    ".js", ".css", "/api/", "upload", "offline",
    "files", "task", "ajax", "dialog"
)
fun webViewClient(onUrl: (String) -> Unit): WebViewClient {
    return object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView, request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url.toString()
            val method = request.method
            val headers = request.requestHeaders

            // 追踪关键资源加载与 API 请求（特别是上传、离线、任务、配置接口）
            if ((url.contains("115.com") || url.contains("anxia.com")) &&
                logKeywords.any { url.contains(it) }
            ) {
                XLog.d("WebView Request [$method]: $url")
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
            XLog.d("WebView Page Started: $url")
        }

        // 页面加载完成时确认 URL（处理重定向后的最终地址）
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            view?.url?.let { onUrl.invoke(it) }
            XLog.d("WebView Page Finished: $url")

            /*
             * 调试排查说明：可在 Logcat 中过滤以下 Tag/关键词：
             * - "DIAG_SUMMARY": 诊断触发原因及元素统计汇总
             * - "PAGE_STATE": 页面 html/body 状态
             * - "IFRAME_ELEMENTS": 页面内所有的 iframe 信息及穿透诊断结果
             * - "BLUR_ELEMENTS": 所有模糊滤镜（backdropFilter/filter）元素及其内部子元素解剖
             * - "OVERLAY_ELEMENTS": 所有全屏遮罩/Modal 元素及其直接子节点
             * - "DIALOG_ELEMENTS": 弹窗相关元素（普通上传、添加云下载等）的详细样式与祖先链
             * - "KEYWORD_ELEMENTS": 根据界面文案（“普通上传”、“添加文件”、“添加云下载”等）地毯式搜索到的节点
             * - "HIGH_ZINDEX_ELEMENTS": 高 z-index 元素排行榜（排查层叠上下文覆盖）
             * - "HIT_TESTS": 屏幕中心与弹窗坐标命中测试
             * - "RULE_MISKILL_CHECK": 检查 115-core-fix 中的 mask/modal 规则是否误杀了弹窗
             * - "USER_CLICK": 捕获用户在页面上的点击目标
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

                            /* 5. 核心修复：解决弹窗容器高度塌陷为 0px 导致的内容全被 overflow-hidden 截断 */
                            [class*="backdrop-blur"] > div:not([class*="border-0"]),
                            .shadow-xl.flex.flex-col,
                            div[class*="shadow-xl"][class*="flex-col"] {
                                min-height: 420px !important;
                                height: auto !important;
                                max-height: calc(100vh - 60px) !important;
                                overflow: visible !important;
                                visibility: visible !important;
                                opacity: 1 !important;
                            }

                            /* 6. 经典版弹窗层级提升与防遮挡 */
                            .dialog-box, .upload-box, .offline-box, [id*="_warp"], [id*="window_"] {
                                z-index: 1000000010 !important;
                                display: block !important;
                                visibility: visible !important;
                                opacity: 1 !important;
                            }
                        `;

                        // 动态 DOM 检查：若检测到弹窗内容容器高度仍为 0，直接以行内样式强行展开
                        var modals = document.querySelectorAll('[class*="backdrop-blur"] > div:not([class*="border-0"]), .shadow-xl.flex.flex-col');
                        for (var i = 0; i < modals.length; i++) {
                            var m = modals[i];
                            var r = m.getBoundingClientRect();
                            if (r.height <= 10) {
                                m.style.setProperty('min-height', '420px', 'important');
                                m.style.setProperty('height', 'auto', 'important');
                                m.style.setProperty('overflow', 'visible', 'important');
                                m.style.setProperty('opacity', '1', 'important');
                                m.style.setProperty('visibility', 'visible', 'important');
                            }
                        }
                    }
                    
                    window.__applyCoreFix = applyFix;
                    applyFix();
                    // 115 页面会多次重绘，采用轮询确保修复持久生效
                    var count = 0;
                    var itv = setInterval(function() {
                        applyFix();
                        if(++count > 15) clearInterval(itv);
                    }, 800);
                })();
                """.trimIndent(), null
            )

            // 调试诊断与日志输出逻辑（仅 DEBUG 模式下生效）
            if (github.zerorooot.nap511.BuildConfig.DEBUG) {
                view?.evaluateJavascript(
                    """
                (function() {
                    if (window.__web_diag_installed) return;
                    window.__web_diag_installed = true;

                    // 统一日志发送函数
                    function sendDiag(category, data) {
                        var str = (typeof data === 'string') ? data : JSON.stringify(data);
                        if (window.WebDiagBridge && window.WebDiagBridge.log) {
                            window.WebDiagBridge.log(category, str);
                        }
                        console.log("[" + category + "] " + str);
                    }

                    function sendDiagError(category, err) {
                        var str = (err && err.stack) ? (err.message + " @ " + err.stack) : String(err);
                        if (window.WebDiagBridge && window.WebDiagBridge.error) {
                            window.WebDiagBridge.error(category, str);
                        }
                        console.error("[" + category + "] " + str);
                    }

                    // 辅助：获取元素直接子节点的简要信息（解剖遮罩层内部到底有没有挂载弹窗）
                    function getChildrenSummary(el) {
                        var children = [];
                        try {
                            for (var i = 0; i < el.children.length; i++) {
                                var c = el.children[i];
                                var cs = window.getComputedStyle(c);
                                var r = c.getBoundingClientRect();
                                children.push({
                                    tag: c.tagName,
                                    id: c.id,
                                    cls: c.className,
                                    styleAttr: c.getAttribute('style') || '',
                                    computedHeight: cs.height,
                                    computedMinHeight: cs.minHeight,
                                    computedMaxHeight: cs.maxHeight,
                                    rect: [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)],
                                    display: cs.display,
                                    vis: cs.visibility,
                                    op: cs.opacity,
                                    zIndex: cs.zIndex,
                                    pos: cs.position,
                                    text: (c.innerText || '').substring(0, 40).replace(/\s+/g, ' ')
                                });
                            }
                        } catch(e) {}
                        return children;
                    }

                    // 1. 全局扫描页面中所有具有模糊效果(filter / backdropFilter)或遮罩的元素，并解剖大面积遮罩的内部结构
                    function checkBlurElements(doc) {
                        var blurList = [];
                        try {
                            var all = doc.querySelectorAll('*');
                            for (var i = 0; i < all.length; i++) {
                                var el = all[i];
                                var cs = window.getComputedStyle(el);
                                var f = cs.filter || cs.webkitFilter || '';
                                var bf = cs.backdropFilter || cs.webkitBackdropFilter || '';
                                var styleAttr = el.getAttribute('style') || '';

                                var hasBlur = f.indexOf('blur') !== -1 ||
                                             bf.indexOf('blur') !== -1 ||
                                             styleAttr.indexOf('blur') !== -1;
                                var hasFilter = (f && f !== 'none') || (bf && bf !== 'none');

                                if (hasBlur || hasFilter) {
                                    var r = el.getBoundingClientRect();
                                    var isCovering = (r.width >= window.innerWidth * 0.7 && r.height >= window.innerHeight * 0.7);
                                    var item = {
                                        tag: el.tagName,
                                        id: el.id,
                                        cls: el.className,
                                        rect: [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)],
                                        filter: f,
                                        backdropFilter: bf,
                                        pos: cs.position,
                                        zIndex: cs.zIndex,
                                        op: cs.opacity,
                                        vis: cs.visibility,
                                        display: cs.display,
                                        bg: cs.backgroundColor,
                                        isCoveringScreen: isCovering,
                                        hasBlur: hasBlur
                                    };

                                    // 如果是大面积模糊遮罩，进一步提取其直接子元素与 HTML 摘要
                                    if (isCovering && hasBlur) {
                                        item.childCount = el.children.length;
                                        item.children = getChildrenSummary(el);
                                        item.innerHTMLPreview = (el.innerHTML || '').substring(0, 200).replace(/\s+/g, ' ');
                                    }

                                    blurList.push(item);
                                }
                            }
                        } catch(e) {
                            sendDiagError('CHECK_BLUR_ERR', e);
                        }
                        return blurList;
                    }

                    // 2. 详细检查弹窗元素（普通上传、云下载、dialog-box、现代 Next.js 弹窗等）
                    function checkDialogElements(doc, contextLabel) {
                        var dialogs = [];
                        try {
                            var selector = '.dialog-box, .upload-box, .offline-box, .window-current, [id^="window_"], [id*="_warp"], [class*="dialog-box"], [class*="upload-box"], [class*="offline-box"], [class*="dialog-mini"], [class*="upload-contents"], [id*="plupload"], [class*="plupload"], div[class*="shadow-xl"][class*="flex-col"], [class*="backdrop-blur"] > div:not([class*="border-0"])';
                            var targets = doc.querySelectorAll(selector);

                            // 降级检查：如未匹配到指定 class，尝试在含有相关文字的容器中寻找
                            if (targets.length === 0) {
                                var candidates = doc.querySelectorAll('div, section');
                                for (var j = 0; j < candidates.length; j++) {
                                    var c = candidates[j];
                                    if (c.children.length > 0 && c.innerText && (c.innerText.indexOf('普通上传') !== -1 || c.innerText.indexOf('添加云下载') !== -1)) {
                                        if (c.className && /dialog|upload|offline|window/i.test(c.className)) {
                                            targets = [c];
                                            break;
                                        }
                                    }
                                }
                            }

                            for (var i = 0; i < targets.length; i++) {
                                var el = targets[i];
                                var r = el.getBoundingClientRect();
                                var cs = window.getComputedStyle(el);

                                // 祖先链路遍历：排查父节点的 display/visibility/opacity/transform/filter/overflow
                                var ancestors = [];
                                var p = el.parentElement;
                                var ancestorHidesOrClips = false;
                                while (p && p !== doc.documentElement && p !== doc.body) {
                                    var pcs = window.getComputedStyle(p);
                                    var pr = p.getBoundingClientRect();
                                    var isHidden = (pcs.display === 'none' || pcs.visibility === 'hidden' || pcs.opacity === '0');
                                    var isTransformed = (pcs.transform && pcs.transform !== 'none');
                                    var hasFilter = (pcs.filter && pcs.filter !== 'none');
                                    var hasMaskClass = !!(p.className && /mask|modal|loading/i.test(p.className));

                                    if (isHidden) ancestorHidesOrClips = true;

                                    ancestors.push({
                                        tag: p.tagName,
                                        id: p.id,
                                        cls: p.className,
                                        display: pcs.display,
                                        vis: pcs.visibility,
                                        op: pcs.opacity,
                                        overflow: pcs.overflow,
                                        pos: pcs.position,
                                        zIndex: pcs.zIndex,
                                        transform: pcs.transform,
                                        filter: pcs.filter,
                                        rect: [Math.round(pr.left), Math.round(pr.top), Math.round(pr.width), Math.round(pr.height)],
                                        isMaskClass: hasMaskClass
                                    });
                                    p = p.parentElement;
                                }

                                var winW = window.innerWidth;
                                var winH = window.innerHeight;
                                var isOffscreen = (r.right <= 0 || r.bottom <= 0 || r.left >= winW || r.top >= winH);
                                var isWidthExceeded = (r.left + r.width > winW);
                                var isHeightExceeded = (r.top + r.height > winH);

                                // 遮挡探测 (Hit Testing)
                                var hitTestResults = [];
                                var testPoints = [
                                    { name: 'center', x: Math.round(r.left + r.width / 2), y: Math.round(r.top + r.height / 2) },
                                    { name: 'topLeft', x: Math.round(r.left + 20), y: Math.round(r.top + 20) },
                                    { name: 'screenCenter', x: Math.round(winW / 2), y: Math.round(winH / 2) }
                                ];
                                testPoints.forEach(function(pt) {
                                    if (pt.x >= 0 && pt.x < winW && pt.y >= 0 && pt.y < winH) {
                                        try {
                                            var hitEl = doc.elementFromPoint(pt.x, pt.y);
                                            if (hitEl) {
                                                var isInsideDialog = el.contains(hitEl);
                                                var hcs = window.getComputedStyle(hitEl);
                                                hitTestResults.push({
                                                    point: pt.name + '(' + pt.x + ',' + pt.y + ')',
                                                    isInsideDialog: isInsideDialog,
                                                    hitTag: hitEl.tagName,
                                                    hitId: hitEl.id,
                                                    hitCls: hitEl.className,
                                                    hitZIndex: hcs.zIndex,
                                                    hitFilter: hcs.filter,
                                                    hitBackdropFilter: hcs.backdropFilter
                                                });
                                            }
                                        } catch(he) {}
                                    }
                                });

                                dialogs.push({
                                    context: contextLabel || 'TOP',
                                    tag: el.tagName,
                                    id: el.id,
                                    cls: el.className,
                                    inlineStyle: el.getAttribute('style') || '',
                                    rect: [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)],
                                    viewport: { winW: winW, winH: winH },
                                    visibilityAssessment: {
                                        isOffscreen: isOffscreen,
                                        isWidthExceeded: isWidthExceeded,
                                        isHeightExceeded: isHeightExceeded,
                                        ancestorHidesOrClips: ancestorHidesOrClips
                                    },
                                    computedStyle: {
                                        display: cs.display,
                                        vis: cs.visibility,
                                        op: cs.opacity,
                                        pos: cs.position,
                                        top: cs.top,
                                        left: cs.left,
                                        width: cs.width,
                                        height: cs.height,
                                        zIndex: cs.zIndex,
                                        transform: cs.transform,
                                        filter: cs.filter,
                                        backdropFilter: cs.backdropFilter,
                                        pointerEvents: cs.pointerEvents,
                                        overflow: cs.overflow
                                    },
                                    ancestors: ancestors,
                                    hitTests: hitTestResults,
                                    textPreview: el.innerText ? el.innerText.substring(0, 40).replace(/\s+/g, ' ') : ''
                                });
                            }
                        } catch(e) {
                            sendDiagError('CHECK_DIALOG_ERR', e);
                        }
                        return dialogs;
                    }

                    // 3. 检查全屏遮罩层 / Modal / Mask 及其子元素
                    function checkOverlays(doc) {
                        var overlays = [];
                        try {
                            var targets = doc.querySelectorAll('[class*="mask"], [class*="modal"], [class*="overlay"], [class*="backdrop"], [class*="window-mask"]');
                            for (var i = 0; i < targets.length; i++) {
                                var el = targets[i];
                                var r = el.getBoundingClientRect();
                                var cs = window.getComputedStyle(el);
                                overlays.push({
                                    tag: el.tagName,
                                    id: el.id,
                                    cls: el.className,
                                    rect: [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)],
                                    display: cs.display,
                                    vis: cs.visibility,
                                    op: cs.opacity,
                                    zIndex: cs.zIndex,
                                    pos: cs.position,
                                    filter: cs.filter,
                                    backdropFilter: cs.backdropFilter,
                                    bg: cs.backgroundColor,
                                    pointerEvents: cs.pointerEvents,
                                    childCount: el.children.length,
                                    children: getChildrenSummary(el),
                                    innerHTMLPreview: (el.innerHTML || '').substring(0, 150).replace(/\s+/g, ' ')
                                });
                            }
                        } catch(e) {
                            sendDiagError('CHECK_OVERLAY_ERR', e);
                        }
                        return overlays;
                    }

                    // 4. 深度穿透排查 Iframe / Frame
                    function checkIframes() {
                        var iframeReports = [];
                        try {
                            var iframes = document.querySelectorAll('iframe, frame');
                            for (var i = 0; i < iframes.length; i++) {
                                var ifr = iframes[i];
                                var r = ifr.getBoundingClientRect();
                                var cs = window.getComputedStyle(ifr);
                                var report = {
                                    index: i,
                                    tag: ifr.tagName,
                                    id: ifr.id,
                                    name: ifr.name,
                                    src: ifr.src || ifr.getAttribute('src') || '',
                                    rect: [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)],
                                    display: cs.display,
                                    vis: cs.visibility,
                                    op: cs.opacity,
                                    zIndex: cs.zIndex,
                                    pos: cs.position,
                                    canAccessDoc: false
                                };

                                try {
                                    var subDoc = ifr.contentDocument || ifr.contentWindow.document;
                                    if (subDoc) {
                                        report.canAccessDoc = true;
                                        report.subDocTitle = subDoc.title;
                                        report.subDocUrl = subDoc.location.href;
                                        // 深入 iframe 内部检索弹窗、模糊及关键字
                                        var subDialogs = checkDialogElements(subDoc, 'IFRAME_' + (ifr.id || ifr.name || i));
                                        var subBlurs = checkBlurElements(subDoc);
                                        var subKeywords = searchTextKeywords(subDoc, 'IFRAME_' + (ifr.id || ifr.name || i));
                                        report.subDialogCount = subDialogs.length;
                                        report.subDialogs = subDialogs;
                                        report.subBlurCount = subBlurs.length;
                                        report.subBlurs = subBlurs;
                                        report.subKeywordMatches = subKeywords;

                                        // 为 iframe 的文档也挂载监听
                                        bindIframeEvents(ifr, subDoc, i);
                                    }
                                } catch(crossErr) {
                                    report.canAccessDoc = false;
                                    report.accessError = crossErr.message;
                                }

                                iframeReports.push(report);
                            }
                        } catch(e) {
                            sendDiagError('CHECK_IFRAME_ERR', e);
                        }
                        return iframeReports;
                    }

                    // 5. 地毯式搜索页面中含有指定关键字（“普通上传”、“添加云下载”等）的文本节点
                    function searchTextKeywords(doc, contextLabel) {
                        var keywords = ['普通上传', '添加文件', '添加云下载', '支持HTTP', '开始下载', '上传文件', '添加BT任务', '拖到这里'];
                        var matches = [];
                        try {
                            var walker = doc.createTreeWalker(doc.body || doc.documentElement, NodeFilter.SHOW_TEXT, null, false);
                            var node;
                            while ((node = walker.nextNode())) {
                                var val = (node.nodeValue || '').trim();
                                if (!val) continue;
                                for (var k = 0; k < keywords.length; k++) {
                                    var kw = keywords[k];
                                    if (val.indexOf(kw) !== -1) {
                                        var p = node.parentElement;
                                        if (p) {
                                            var cs = window.getComputedStyle(p);
                                            var r = p.getBoundingClientRect();
                                            matches.push({
                                                context: contextLabel || 'TOP',
                                                keyword: kw,
                                                text: val.substring(0, 30).replace(/\s+/g, ' '),
                                                tag: p.tagName,
                                                id: p.id,
                                                cls: p.className,
                                                rect: [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)],
                                                display: cs.display,
                                                vis: cs.visibility,
                                                op: cs.opacity,
                                                zIndex: cs.zIndex,
                                                pos: cs.position
                                            });
                                        }
                                        break;
                                    }
                                }
                            }
                        } catch(e) {}
                        return matches;
                    }

                    // 6. 查找页面中所有高 z-index 元素（>= 999），排查层叠上下文覆盖
                    function checkHighZIndex(doc) {
                        var list = [];
                        try {
                            var all = doc.querySelectorAll('*');
                            for (var i = 0; i < all.length; i++) {
                                var el = all[i];
                                var cs = window.getComputedStyle(el);
                                var z = cs.zIndex;
                                var zNum = parseInt(z, 10);
                                if (!isNaN(zNum) && zNum >= 999) {
                                    var r = el.getBoundingClientRect();
                                    list.push({
                                        tag: el.tagName,
                                        id: el.id,
                                        cls: el.className,
                                        zIndex: zNum,
                                        pos: cs.position,
                                        rect: [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)],
                                        display: cs.display,
                                        vis: cs.visibility,
                                        op: cs.opacity,
                                        filter: cs.filter,
                                        backdropFilter: cs.backdropFilter
                                    });
                                }
                            }
                            list.sort(function(a, b) { return b.zIndex - a.zIndex; });
                        } catch(e) {}
                        return list.slice(0, 10);
                    }

                    // 7. 屏幕关键坐标命中测试 (Hit Tests)
                    function checkHitTests(doc) {
                        var winW = window.innerWidth;
                        var winH = window.innerHeight;
                        var points = [
                            { name: 'screenCenter', x: Math.round(winW / 2), y: Math.round(winH / 2) },
                            { name: 'uploadBoxPos', x: Math.min(313 + 100, winW - 20), y: Math.min(135 + 50, winH - 20) },
                            { name: 'offlineBoxPos', x: Math.min(259 + 100, winW - 20), y: Math.min(177 + 50, winH - 20) }
                        ];
                        var hits = [];
                        points.forEach(function(pt) {
                            try {
                                var el = doc.elementFromPoint(pt.x, pt.y);
                                if (el) {
                                    var cs = window.getComputedStyle(el);
                                    hits.push({
                                        point: pt.name + '(' + pt.x + ',' + pt.y + ')',
                                        tag: el.tagName,
                                        id: el.id,
                                        cls: el.className,
                                        zIndex: cs.zIndex,
                                        pos: cs.position,
                                        display: cs.display,
                                        filter: cs.filter,
                                        backdropFilter: cs.backdropFilter,
                                        bg: cs.backgroundColor
                                    });
                                }
                            } catch(e) {}
                        });
                        return hits;
                    }

                    // 8. 检查 115-core-fix 中的 mask/modal/loading 规则是否误杀了弹窗元素
                    function checkRuleMisKill(doc) {
                        var killed = [];
                        try {
                            var candidates = doc.querySelectorAll('.v-modal, [class*="mask"], [class*="loading"]');
                            for (var i = 0; i < candidates.length; i++) {
                                var el = candidates[i];
                                var str = (el.className || '') + ' ' + (el.id || '');
                                if (/dialog|upload|offline|window|warp|box/i.test(str) || (el.innerText && /上传|下载/i.test(el.innerText))) {
                                    killed.push({
                                        tag: el.tagName,
                                        id: el.id,
                                        cls: el.className,
                                        text: (el.innerText || '').substring(0, 30).replace(/\s+/g, ' ')
                                    });
                                }
                            }
                        } catch(e) {}
                        return killed;
                    }

                    // 9. 检查 Body / HTML 及主容器状态
                    function checkPageState() {
                        var state = {};
                        try {
                            var docEl = document.documentElement;
                            var body = document.body;
                            var docCs = window.getComputedStyle(docEl);
                            var bodyCs = body ? window.getComputedStyle(body) : null;
                            var mainContent = document.getElementById('js_mainContent') || document.querySelector('.layout-main, .layout-content, #wrap');
                            var mainCs = mainContent ? window.getComputedStyle(mainContent) : null;

                            state = {
                                html: {
                                    cls: docEl.className,
                                    filter: docCs.filter,
                                    backdropFilter: docCs.backdropFilter,
                                    overflow: docCs.overflow,
                                    w: docEl.clientWidth,
                                    h: docEl.clientHeight
                                },
                                body: body ? {
                                    cls: body.className,
                                    styleAttr: body.getAttribute('style') || '',
                                    filter: bodyCs.filter,
                                    backdropFilter: bodyCs.backdropFilter,
                                    overflow: bodyCs.overflow,
                                    pos: bodyCs.position,
                                    w: body.offsetWidth,
                                    h: body.offsetHeight
                                } : null,
                                mainContainer: mainContent ? {
                                    id: mainContent.id,
                                    cls: mainContent.className,
                                    filter: mainCs.filter,
                                    backdropFilter: mainCs.backdropFilter,
                                    overflow: mainCs.overflow
                                } : null,
                                coreFixStylePresent: !!document.getElementById('115-core-fix')
                            };
                        } catch(e) {
                            sendDiagError('CHECK_PAGE_STATE_ERR', e);
                        }
                        return state;
                    }

                    // 综合诊断主入口
                    window.__runDialogDiagnostic = function(reason) {
                        try {
                            var blurList = checkBlurElements(document);
                            var dialogList = checkDialogElements(document, 'TOP');
                            var overlayList = checkOverlays(document);
                            var pageState = checkPageState();
                            var iframeList = checkIframes();
                            var keywordList = searchTextKeywords(document, 'TOP');
                            var highZList = checkHighZIndex(document);
                            var hitTestList = checkHitTests(document);
                            var misKillList = checkRuleMisKill(document);

                            var summary = {
                                reason: reason,
                                timestamp: Date.now(),
                                url: window.location.href,
                                blurCount: blurList.length,
                                dialogCount: dialogList.length,
                                overlayCount: overlayList.length,
                                iframeCount: iframeList.length,
                                keywordMatchCount: keywordList.length,
                                misKillCount: misKillList.length
                            };

                            sendDiag('DIAG_SUMMARY', summary);
                            sendDiag('PAGE_STATE', pageState);

                            if (iframeList.length > 0) {
                                sendDiag('IFRAME_ELEMENTS', iframeList);
                            }

                            if (blurList.length > 0) {
                                sendDiag('BLUR_ELEMENTS', blurList);
                            }

                            if (overlayList.length > 0) {
                                sendDiag('OVERLAY_ELEMENTS', overlayList);
                            }

                            if (dialogList.length > 0) {
                                sendDiag('DIALOG_ELEMENTS', dialogList);
                            } else {
                                sendDiag('DIALOG_ELEMENTS', 'NO_DIALOG_FOUND_IN_TOP');
                            }

                            if (keywordList.length > 0) {
                                sendDiag('KEYWORD_ELEMENTS', keywordList);
                            }

                            if (highZList.length > 0) {
                                sendDiag('HIGH_ZINDEX_ELEMENTS', highZList);
                            }

                            sendDiag('HIT_TESTS', hitTestList);

                            if (misKillList.length > 0) {
                                sendDiag('RULE_MISKILL_CHECK', misKillList);
                            }
                        } catch(e) {
                            sendDiagError('DIAG_RUN_ERR', e);
                        }
                    };

                    // 10. 事件监听器：全局点击/触摸
                    document.addEventListener('click', function(e) {
                        try {
                            var t = e.target;
                            var text = (t.innerText || t.value || t.title || '').substring(0, 30).replace(/\s+/g, ' ');
                            sendDiag('USER_CLICK', {
                                tag: t.tagName,
                                id: t.id,
                                cls: t.className,
                                text: text
                            });
                            // 多阶段延迟采样（应对异步接口请求及动画重绘）
                            setTimeout(function() { window.__runDialogDiagnostic('CLICK_+100ms'); }, 100);
                            setTimeout(function() { window.__runDialogDiagnostic('CLICK_+500ms'); }, 500);
                            setTimeout(function() { window.__runDialogDiagnostic('CLICK_+1200ms'); }, 1200);
                            setTimeout(function() { window.__runDialogDiagnostic('CLICK_+2500ms'); }, 2500);
                            setTimeout(function() { window.__runDialogDiagnostic('CLICK_+4000ms'); }, 4000);
                        } catch(err) {}
                    }, true);

                    // 11. Iframe 事件绑定辅助函数
                    function bindIframeEvents(ifr, subDoc, idx) {
                        try {
                            if (subDoc.__diag_bound) return;
                            subDoc.__diag_bound = true;

                            subDoc.addEventListener('click', function(e) {
                                var t = e.target;
                                var text = (t.innerText || t.value || t.title || '').substring(0, 30).replace(/\s+/g, ' ');
                                sendDiag('USER_CLICK_IN_IFRAME', {
                                    iframeId: ifr.id || ifr.name || ('idx_' + idx),
                                    tag: t.tagName,
                                    id: t.id,
                                    cls: t.className,
                                    text: text
                                });
                                setTimeout(function() { window.__runDialogDiagnostic('IFRAME_CLICK_+300ms'); }, 300);
                                setTimeout(function() { window.__runDialogDiagnostic('IFRAME_CLICK_+1500ms'); }, 1500);
                            }, true);

                            var obs = new MutationObserver(function(muts) {
                                sendDiag('IFRAME_MUTATION', {
                                    iframeId: ifr.id || ifr.name || ('idx_' + idx),
                                    mutationsCount: muts.length
                                });
                                window.__runDialogDiagnostic('MUTATION_IN_IFRAME');
                            });
                            obs.observe(subDoc.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['style', 'class'] });
                        } catch(e) {}
                    }

                    // 12. DOM 变动观察器 (MutationObserver)
                    if (!window.__diag_observer) {
                        var debounceTimer = null;
                        window.__diag_observer = new MutationObserver(function(mutations) {
                            var shouldTrigger = false;
                            var triggerDetails = [];
                            for (var i = 0; i < mutations.length; i++) {
                                var m = mutations[i];
                                if (m.type === 'childList') {
                                    for (var j = 0; j < m.addedNodes.length; j++) {
                                        var node = m.addedNodes[j];
                                        if (node.nodeType === 1) {
                                            var str = (node.className || '') + ' ' + (node.id || '') + ' ' + node.tagName;
                                            if (/dialog|upload|offline|window|warp|box|mask|modal|overlay|backdrop/i.test(str)) {
                                                shouldTrigger = true;
                                                triggerDetails.push('ADD:' + node.tagName + '.' + (node.className || '') + '#' + (node.id || ''));
                                            }
                                        }
                                    }
                                } else if (m.type === 'attributes') {
                                    var target = m.target;
                                    var tStr = (target.className || '') + ' ' + (target.id || '') + ' ' + target.tagName;
                                    if (/dialog|upload|offline|window|body|html|warp|mask|modal|backdrop/i.test(tStr)) {
                                        shouldTrigger = true;
                                        triggerDetails.push('ATTR(' + m.attributeName + '):' + target.tagName + '#' + target.id);
                                    }
                                }
                            }

                            if (shouldTrigger) {
                                if (window.__applyCoreFix) window.__applyCoreFix();
                                clearTimeout(debounceTimer);
                                debounceTimer = setTimeout(function() {
                                    window.__runDialogDiagnostic('MUTATION: ' + triggerDetails.slice(0, 5).join('; '));
                                }, 80);
                            }
                        });

                        window.__diag_observer.observe(document.documentElement, {
                            childList: true,
                            subtree: true,
                            attributes: true,
                            attributeFilter: ['style', 'class', 'hidden']
                        });
                    }

                    // 13. 定时巡检 (Heartbeat Poll)
                    var pollCount = 0;
                    var pollInterval = setInterval(function() {
                        pollCount++;
                        var hasDlg = !!document.querySelector('.dialog-box, .upload-box, .offline-box, .window-current, [id^="window_"]');
                        var hasOverlay = !!document.querySelector('[class*="backdrop-blur"], [class*="mask"], [class*="modal"]');
                        if (hasDlg || hasOverlay) {
                            window.__runDialogDiagnostic('POLL_ACTIVE_DIALOG_OR_OVERLAY_#' + pollCount);
                        }
                        if (pollCount > 100) clearInterval(pollInterval);
                    }, 2000);

                    // 初始立即执行一次
                    window.__runDialogDiagnostic('INJECT_INITIAL');
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
