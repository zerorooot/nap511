package github.zerorooot.nap511.screen

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import github.zerorooot.nap511.R
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun WebViewScreen(offlineFileViewModel: OfflineFileViewModel) {
    val cookieManager = CookieManager.getInstance()
    offlineFileViewModel.myCookie.split(";").forEach { a ->
        cookieManager.setCookie("https://captchaapi.115.com", a)
    }
    cookieManager.flush()

    Column {
        TopAppBar(
            title = {
                Text(text = stringResource(R.string.app_name))
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Purple80),
            navigationIcon = {
                TopAppBarActionButton(
                    imageVector = Icons.Rounded.Menu,
                    description = "navigationIcon"
                ) {
                    offlineFileViewModel.openDrawerState()
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
//                        webViewClient =
//                            getWebViewClientWithCustomHeader(offlineFileViewModel.myCookie)
//                        webViewClient = CustomWebViewClient()
                        settings.javaScriptEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.setSupportZoom(true)
                    }
                },
                update = { webView ->
                    webView.loadUrl(offlineFileViewModel.url)
                }
            )
        }

    }


}

class CustomWebViewClient() : WebViewClient() {

    override fun onPageFinished(view: WebView?, url: String?) {
        val cookieManager = CookieManager.getInstance()
        val cookieStr = cookieManager.getCookie(url)
        println("$url  $cookieStr")
        super.onPageFinished(view, url)
    }

}

fun getWebViewClientWithCustomHeader(cookie: String): WebViewClient {
    return object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            return try {
                val httpClient = OkHttpClient()
                val a = Request.Builder()
                    .url(request.url.toString())
                    .addHeader("cookie",cookie)
                    .addHeader(
                        "User-Agent",
                        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                    .addHeader(
                        "sec-ch-ua",
                        "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\""
                    )
                    .addHeader(
                        "sec-ch-ua-mobile",
                        "?0"
                    )
                    .addHeader(
                        "sec-ch-ua-platform",
                        "\"Linux\""
                    )
                    .build()
                val response = httpClient.newCall(a).execute()

                var conentType =
                    response.header("Content-Type", response.body!!.contentType()!!.type)!!
                val temp = conentType.lowercase(Locale.getDefault())
                if (temp.contains("charset=utf-8")) {
                    conentType =
                        conentType.replace(("(?i)" + "charset=utf-8").toRegex(), "") //不区分大小写的替换
                }
                if (conentType.contains(";")) {
                    conentType = conentType.replace(";".toRegex(), "")
                    conentType = conentType.trim { it <= ' ' }
                }

                WebResourceResponse(
                    conentType,
                    response.header(
                        "content-encoding",
                        "utf-8"
                    ),  // Again, you can set another encoding as default
                    response.body?.byteStream()
                )
            } catch (e: IOException) {
                //return null to tell WebView we failed to fetch it WebView should try again.
                null
            }
        }
    }
}

@Preview
@Composable
fun WebViewScreenPreview() {

}