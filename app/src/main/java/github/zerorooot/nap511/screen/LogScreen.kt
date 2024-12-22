package github.zerorooot.nap511.screen

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun LogScreen() {
    var log = remember {
        mutableStateOf(
            try {
                readInputStreamAsString(
                    FileInputStream(
                        File(
                            App.instance.cacheDir,
                            "log"
                        )
                    )
                )
            } catch (_: Exception) {
                ""
            }
        )
    }

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val coroutine = rememberCoroutineScope()
    val appBarOnClick = { name: String ->
        when (name) {
            "滚动顶部" -> {
                coroutine.launch {
                    verticalScrollState.animateScrollTo(0)
                }
            }

            "滚动底部" -> {
                coroutine.launch {
                    verticalScrollState.animateScrollTo(verticalScrollState.maxValue)
                }
            }

            "清空日志" -> {
                File(
                    App.instance.cacheDir,
                    "log"
                ).delete()
                log.value = ""
            }

            "导出日志" -> {
                writeToPublicExternalStorage(App.instance, "log.txt", log.value)
            }

            "ModalNavigationDrawerMenu" -> App.instance.openDrawerState()
            else -> {}
        }
    }

    Column {
        AppTopBarLogScreen(ConfigKeyUtil.LOG_SCREEN, appBarOnClick as (String) -> Unit)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState)
                .horizontalScroll(horizontalScrollState)
        ) {

            Text(
                text = log.value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }



    LaunchedEffect(Unit) {
        delay(10)
        verticalScrollState.animateScrollTo(verticalScrollState.maxValue)
    }

}

//private fun checkAndRequestPermissions() {
//    if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//        requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
//    }
//}

fun writeToPublicExternalStorage(
    applicationContext: Application,
    fileName: String,
    content: String
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Scoped Storage for Android 10+
        val resolver = applicationContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri =
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(content.toByteArray())
                App.instance.toast("导出成功，日志文件保存至 Downloads 目录")
                XLog.d("FileWrite File written to Downloads: $uri")
            }
        }
    } else {
        // Legacy method for Android 9 and below
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        try {
            file.writeText(content)
            XLog.d("FileWrite File written to: ${file.absolutePath}")
        } catch (e: IOException) {
            e.printStackTrace()
            XLog.e("FileWrite Error writing file: $e")
        }
    }
}


fun readInputStreamAsString(`in`: InputStream): String {
    val bis = BufferedInputStream(`in`)
    val buf = ByteArrayOutputStream()
    var result = bis.read()
    while (result != -1) {
        val b = result.toByte()
        buf.write(b.toInt())
        result = bis.read()
    }
    return buf.toString()
}