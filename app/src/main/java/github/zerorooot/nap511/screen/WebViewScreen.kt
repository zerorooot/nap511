package github.zerorooot.nap511.screen

import android.annotation.SuppressLint
import android.app.Application
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
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


@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun WebViewScreen(offlineFileViewModel: OfflineFileViewModel) {
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
//                        webViewClient = CustomWebViewClient()
//                        webChromeClient = CustomWebChromeClient(offlineFileViewModel.myApplication)
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

//class CustomWebChromeClient(private val application: Application) : WebChromeClient() {
//    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
//        val message = consoleMessage.message()
//        println(message)
//        return true
//    }
//}
//
//class CustomWebViewClient() : WebViewClient() {
//    override fun onPageFinished(view: WebView, url: String?) {
//        view.loadUrl("javascript:console.log(document.getElementsByTagName('html')[0].innerHTML);")
//    }
//}


@Preview
@Composable
fun WebViewScreenPreview() {

}