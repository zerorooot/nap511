package github.zerorooot.nap511.screen

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.ValueCallback
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
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
import github.zerorooot.nap511.activity.OfflineTaskWorker
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.StringJoiner
import kotlin.text.startsWith


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
                factory = { context ->
                    WebView(context).apply {
                        this.webViewClient = webViewClient.invoke(this)
                        settings.javaScriptEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.javaScriptCanOpenWindowsAutomatically = true;
                        settings.setSupportZoom(true)
                        settings.databaseEnabled = true;
                        settings.domStorageEnabled = true;//开启DOM缓存，关闭的话H5自身的一些操作是无效的
                        settings.cacheMode = WebSettings.LOAD_DEFAULT;
                        // Allow mixed content for WebSocket connections (optional, if needed)
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString =
                            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
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
    App.gesturesEnabled = false
    CookieManager.getInstance().removeAllCookies(object : ValueCallback<Boolean> {
        override fun onReceiveValue(value: Boolean?) {
            XLog.d("removeAllCookies $value")
        }

    })
    BaseWebViewScreen(
        titleText = "网页版",
        topAppBarActionButtonOnClick = {
            App.instance.openDrawerState()
        },
        webViewClient = { webViewClient() },
        loadUrl = "https://115.com/"
    )
}

fun webViewClient(): WebViewClient {
    return object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url.toString()
            val cookieManager = CookieManager.getInstance()
            App.cookie.split(";").forEach { a ->
                cookieManager.setCookie(url, a)
            }
            return super.shouldInterceptRequest(view, request)
        }
    }
}

@SuppressLint("JavascriptInterface")
fun webViewClient(webView: WebView): WebViewClient {
    return object : RequestInspectorWebViewClient(webView) {
        override fun shouldInterceptRequest(
            view: WebView,
            webViewRequest: WebViewRequest
        ): WebResourceResponse? {
            val url = webViewRequest.url
            val cookieManager = CookieManager.getInstance()
            App.cookie.split(";").forEach { a ->
                cookieManager.setCookie(url, a)
            }
            return super.shouldInterceptRequest(view, webViewRequest)
        }
    }
//    return object : WebViewClient() {
//        override fun shouldInterceptRequest(
//            view: WebView?,
//            request: WebResourceRequest?
//        ): WebResourceResponse? {
//            val url = request!!.url.toString()
//            val cookieManager = CookieManager.getInstance()
//            App.cookie.split(";").forEach { a ->
//                cookieManager.setCookie(url, a)
//                cookieManager.setCookie(url, a)
//            }
//            return super.shouldInterceptRequest(view, request)
//        }
//    }

}

@Composable
fun LoginWebViewScreen() {
    App.gesturesEnabled = false
    BaseWebViewScreen(
        titleText = "通过网页登陆",
        topAppBarActionButtonOnClick = {
            App.instance.openDrawerState()
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
            val loginUrlVip = "https://passportapi.115.com/app/1.0/web/1.0/login/vip"
            if (loginUrlVip == webViewRequest.url) {
                val httpClient = OkHttpClient()
                val a = Request.Builder()
                    .url(loginUrlVip)
                    .method("POST", webViewRequest.body.toRequestBody())
                val headerMap = mapOf(
                    "sec-ch-ua-platform" to "\"Linux\"",
                    "sec-ch-ua" to "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"121\", \"Chromium\";v=\"121\"",
                    "sec-ch-ua-mobile" to "?0",
                    "user-agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
                )
                webViewRequest.headers.forEach { (t, u) -> a.addHeader(t, u) }
                headerMap.forEach { (t, u) ->
                    a.removeHeader(t)
                    a.addHeader(t, u)
                }
                val response = httpClient.newCall(a.build()).execute()
                val headers = response.headers("set-cookie")
                XLog.d("headers $headers")
                val sj = StringJoiner(";")
                headers.forEach { i ->
                    sj.add(i.split(";")[0])
                }
                XLog.d("cookie $sj")
                val checkLogin = App.instance.checkLogin(sj.toString())

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
    BaseWebViewScreen(
        titleText = "磁力链接验证码",
        topAppBarActionButtonOnClick = {
            App.instance.openDrawerState()
        },
        webViewClient = { captchaWebViewClient(it) },
        loadUrl = "https://captchaapi.115.com/?ac=security_code&type=web&cb=Close911_" + System.currentTimeMillis()
    )

}

@Composable
fun CaptchaVideoWebViewScreen() {
    val cookieManager = CookieManager.getInstance()
    App.cookie.split(";").forEach { a ->
        cookieManager.setCookie("https://v.anxia.com/captchaapi/", a)
        cookieManager.setCookie("https://v.anxia.com/webapi/user/captcha", a)
    }
    cookieManager.flush()
    BaseWebViewScreen(
        titleText = "视频播放验证码",
        topAppBarActionButtonOnClick = {
            App.instance.openDrawerState()
        },
        webViewClient = { captchaWebViewClient(it) },
        loadUrl = "https://v.anxia.com/captchaapi/?ac=security_code&client=web&type=web&ctype=web&cb=Close911_" + System.currentTimeMillis()
    )

}

fun captchaWebViewClient(webView: WebView): WebViewClient {
    return object : RequestInspectorWebViewClient(webView) {
        override fun shouldInterceptRequest(
            view: WebView,
            webViewRequest: WebViewRequest
        ): WebResourceResponse? {
            if ("https://webapi.115.com/user/captcha" == webViewRequest.url) {
                val httpClient = OkHttpClient()
                val a = Request.Builder()
                    .url("https://webapi.115.com/user/captcha")
                    .method("POST", webViewRequest.body.toRequestBody())
                webViewRequest.headers.forEach { (t, u) -> a.addHeader(t, u) }
                //移除web添加的cookie
                a.removeHeader("cookie")
                a.addHeader("cookie", App.cookie)

                val response = httpClient.newCall(a.build()).execute()
                val string = response.body.string()
                if (string.contains("{\"state\":true}")) {
                    App.selectedItem = ConfigKeyUtil.MY_FILE
                    addTask()
                    App.instance.toast("验证账号成功~，重新添加链接中.......")
                }
            }

            if (webViewRequest.url.startsWith("https://v.anxia.com/webapi/user/captcha")) {
                return super.shouldInterceptRequest(view, webViewRequest)
            }
            return super.shouldInterceptRequest(view, webViewRequest)
        }
    }
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
