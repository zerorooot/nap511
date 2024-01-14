package github.zerorooot.nap511.screen

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.Toast
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
import com.acsbendi.requestinspectorwebview.RequestInspectorWebViewClient
import com.acsbendi.requestinspectorwebview.WebViewRequest
import github.zerorooot.nap511.R
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody


@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun WebViewScreen(offlineFileViewModel: OfflineFileViewModel,fileViewModel: FileViewModel) {
    val cookieManager = CookieManager.getInstance()
    offlineFileViewModel.myCookie.split(";").forEach { a ->
        cookieManager.setCookie("https://captchaapi.115.com", a)
        cookieManager.setCookie("https://webapi.115.com", a)
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
                        webViewClient = CustomWebViewClient(
                            this,
                            fileViewModel
                        )
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


class CustomWebViewClient(
    webView: WebView,
    private val fileViewModel: FileViewModel
) : RequestInspectorWebViewClient(webView) {
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
            val response = httpClient.newCall(a.build()).execute()
            val string = response.body?.string().toString()

            if (string.contains("{\"state\":true}")) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(view.context, "验证账号成功~", Toast.LENGTH_SHORT).show()
                    fileViewModel.selectedItem = "我的文件"
                }
            }
        }
        return null
    }
}


@Preview
@Composable
fun WebViewScreenPreview() {

}