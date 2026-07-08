package github.zerorooot.nap511.screen

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.acsbendi.requestinspectorwebview.RequestInspectorWebViewClient
import com.acsbendi.requestinspectorwebview.WebViewRequest
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jakewharton.processphoenix.ProcessPhoenix
import github.zerorooot.nap511.R
import github.zerorooot.nap511.worker.OfflineTaskWorker
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.LocalDrawerState
import github.zerorooot.nap511.viewmodel.FileViewModel
import kotlinx.coroutines.launch
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
    loadUrl: String
) {
    Column {
        TopAppBar(
            title = {
                Text(text = titleText)
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Purple80),
            navigationIcon = {
                TopAppBarActionButton(
                    imageVector = Icons.Rounded.Menu,
                    description = "navigationIcon"
                ) {
                    topAppBarActionButtonOnClick()
                }
            },
        )
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        this.webViewClient = webViewClient.invoke(this)
                        settings.javaScriptEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.javaScriptCanOpenWindowsAutomatically = true;
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.domStorageEnabled = true;//开启DOM缓存，关闭的话H5自身的一些操作是无效的
                        settings.cacheMode = WebSettings.LOAD_DEFAULT;
                        // Allow mixed content for WebSocket connections (optional, if needed)
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString =
                            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    }
                },
                update = { webView ->
                    webView.loadUrl(loadUrl)
                }
            )
        }

    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun WebViewScreen() {
    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()
    val fileViewModel = viewModel<FileViewModel>()
    fileViewModel.gesturesEnabled = false

    CookieManager.getInstance().removeAllCookies { }
    CookieManager.getInstance().setAcceptCookie(true)
    WebView.setWebContentsDebuggingEnabled(true)
    setRawCookieString("https://115.com/", App.cookie)
    BaseWebViewScreen(
        titleText = "网页版",
        topAppBarActionButtonOnClick = {
            scope.launch { drawerState.open() }
        },
        webViewClient = { webViewClient() },
        loadUrl = "https://115.com/"
    )
}

fun setRawCookieString(url: String, rawCookieString: String) {
    val cookieManager = CookieManager.getInstance()
//    println("$url ${cookieManager.getCookie(url)}")
//    if (!url.startsWith("https://webapi.115.com") && !url.startsWith("https://115.com") && !url.startsWith(
//            "https://115vod.com/"
//        ) && !url.startsWith("https://aps.115.com/")&& !url.startsWith("https://passportapi.115.com/")
//
//    ) {
//        return
//    }

    // 1. 使用分号分割字符串
    val cookies = rawCookieString.split(";")
    // 2. 遍历每一个部分
    cookies.forEach { cookie ->
        val cleanCookie = cookie.trim()
        if (cleanCookie.isNotEmpty()) {
            val cookieToSet = "$cleanCookie ; Domain=.115.com; Path=/;"
            cookieManager.setCookie(url, cookieToSet)
//            cookieManager.setCookie(url, cleanCookie)
        }
    }
    // 6. 同步
    cookieManager.flush()

}

fun webViewClient(): WebViewClient {

    return object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
        ): WebResourceResponse? {
//            request?.let {
//                val url = it.url.toString()
//                val headers = it.requestHeaders
//
//                // 1. 检查 X-Requested-With 头 (这是最标准的 XHR 标志)
//                val isAjaxHeader = headers["X-Requested-With"] == "XMLHttpRequest"
//
//                // 2. 检查 Accept 头 (通常 XHR 请求 JSON 数据)
//                val acceptHeader = headers["Accept"] ?: ""
//                val isJsonType = acceptHeader.contains("application/json")
//
//                if (isAjaxHeader || isJsonType) {
//                    // 这是一个 XHR 请求
//                    println("Caught XHR: $url")
//                    setRawCookieString(url, App.cookie)
//                    // 注意：如果你不打算替换响应，必须返回 null 让 WebView 继续加载
//                    return null
//                }
//            }
            return super.shouldInterceptRequest(view, request)
        }
    }
}

@Composable
fun LoginWebViewScreen() {
    val fileViewModel = viewModel<FileViewModel>()
    fileViewModel.gesturesEnabled = false

    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()
    BaseWebViewScreen(
        titleText = "通过网页登陆",
        topAppBarActionButtonOnClick = {
            scope.launch { drawerState.open() }
        },
        webViewClient = { loginWebViewClient(it) },
        loadUrl = "https://115.com/"
    )
}

fun loginWebViewClient(webView: WebView): WebViewClient {
    return object : RequestInspectorWebViewClient(webView) {
        override fun shouldInterceptRequest(
            view: WebView,
            webViewRequest: WebViewRequest
        ): WebResourceResponse? {
            var cookie: String? = null
            val urlList = arrayOf(
                "https://115.com/storage/netdisk",
                "https://115.com/storage/allfiles",
                "https://115.com/storage/netdisk?cid=0&mode=wangpan",
                "https://115.com/?cid=0&offset=0&mode=wangpan",
                "https://my.115.com/?ct=guide&ac=status"
            )

            for ((_, it) in urlList.withIndex()) {
                if (it == webViewRequest.url) {
                    cookie = CookieManager.getInstance().getCookie(it)
                    XLog.d("$it cookie $cookie")
                    break
                }
            }

            if (cookie != null) {
                val checkLogin = App.instance.checkLogin(cookie)
                App.instance.toast(checkLogin.second)
                if (!checkLogin.first) {
                    return null
                }
                //restart
                ProcessPhoenix.triggerRebirth(App.instance);
            }
            return super.shouldInterceptRequest(view, webViewRequest)
        }
    }

}

@Composable
fun CaptchaWebViewScreen() {
    val cookieManager = CookieManager.getInstance()
    App.cookie.split(";").forEach { a ->
        cookieManager.setCookie("https://captchaapi.115.com", a)
        cookieManager.setCookie("https://webapi.115.com", a)
        cookieManager.setCookie("https://webapi.115.com/user/captcha", a)
    }
    cookieManager.flush()
    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()
    val fileViewModel = viewModel<FileViewModel>()
    BaseWebViewScreen(
        titleText = "磁力链接验证码",
        topAppBarActionButtonOnClick = {
            scope.launch { drawerState.open() }
        },
        webViewClient = {
            captchaWebViewClient(it) { gesture, select ->
                if (gesture) {
                    fileViewModel.gesturesEnabled = true
                }
                if (select) {
                    fileViewModel.selectedItem = ConfigKeyUtil.MY_FILE
                }
            }
        },
        loadUrl = "https://captchaapi.115.com/?ac=security_code&type=web&cb=Close911_" + System.currentTimeMillis()
    )

}

@Composable
fun CaptchaVideoWebViewScreen() {
    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()
    val cookieManager = CookieManager.getInstance()
    val fileViewModel = viewModel<FileViewModel>()

    App.cookie.split(";").forEach { a ->
        cookieManager.setCookie("https://115vod.com/captchaapi/", a)
        cookieManager.setCookie("https://115vod.com/webapi/user/captcha", a)
    }
    cookieManager.flush()
    BaseWebViewScreen(
        titleText = "视频播放验证码",
        topAppBarActionButtonOnClick = {
            scope.launch { drawerState.open() }
        },
        webViewClient = {
            captchaWebViewClient(it) { gesture, select ->
                if (gesture) {
                    fileViewModel.gesturesEnabled = true
                }
                if (select) {
                    fileViewModel.selectedItem = ConfigKeyUtil.MY_FILE
                }
            }
        },
        loadUrl = "https://115vod.com/captchaapi/?ac=security_code&client=web&type=web&ctype=web&cb=Close911_" + System.currentTimeMillis()
    )

}

fun captchaWebViewClient(webView: WebView, handle: (Boolean, Boolean) -> Unit): WebViewClient {
    return object : RequestInspectorWebViewClient(webView) {
        override fun shouldInterceptRequest(
            view: WebView,
            webViewRequest: WebViewRequest
        ): WebResourceResponse? {
            //磁力链接验证
            if ("https://webapi.115.com/user/captcha" == webViewRequest.url) {
                if (check("https://webapi.115.com/user/captcha", webViewRequest, handle)) {
                    addTask()
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
    url: String,
    webViewRequest: WebViewRequest,
    handle: (Boolean, Boolean) -> Unit
): Boolean {
    val httpClient = OkHttpClient()
    val a = Request.Builder()
        .url(url)
        .method("POST", webViewRequest.body.toRequestBody())
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

private fun addTask() {
    val currentOfflineTaskList =
        DataStoreUtil.getData(ConfigKeyUtil.CURRENT_OFFLINE_TASK, "")
            .split("\n")
            .filter { i -> i != "" && i != " " }
            .toSet()
            .toMutableList()
    val listType = object : TypeToken<List<String?>?>() {}.type
    val list = Gson().toJson(currentOfflineTaskList, listType)
    val data: Data =
        Data.Builder().putString("cookie", App.cookie).putString("list", list)
            .build()
    val request: OneTimeWorkRequest =
        OneTimeWorkRequest.Builder(OfflineTaskWorker::class.java).setInputData(data)
            .build()
    WorkManager.getInstance(App.instance.applicationContext).enqueue(request)
}
