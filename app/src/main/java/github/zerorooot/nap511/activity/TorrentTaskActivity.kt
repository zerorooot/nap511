package github.zerorooot.nap511.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class TorrentTaskActivity : androidx.activity.ComponentActivity() {
    private val fileRepository by lazy { FileRepository.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val torrentFile = fileFromContentUri(this, intent.data!!)
            lifecycleScope.launch {
                val defaultOfflineCid =
                    SettingsRepository.getDataSuspend(ConfigKeyUtil.DEFAULT_OFFLINE_CID, "")
                val result = fileRepository.uploadFile(
                    file = torrentFile,
                    targetCid = defaultOfflineCid,
                    mimeType = "application/x-bittorrent"
                )
                val message = if (result.state) {
                    "上传种子文件成功,种子文件保存到默认离线位置中"
                } else {
                    "上传种子文件失败,${result.message}"
                }
                App.instance.toast(message)
                moveTaskToBack(true)
                finishAndRemoveTask()
            }
        } else {
            moveTaskToBack(true)
            finishAndRemoveTask()
        }
    }

    private fun fileFromContentUri(context: Context, contentUri: Uri): File {
        val fileName = File(contentUri.path!!).name
        val tempFile = File(context.cacheDir, fileName)
        if (tempFile.exists()) {
            tempFile.delete()
        }
        tempFile.createNewFile()
        try {
            val oStream = FileOutputStream(tempFile)
            val inputStream = context.contentResolver.openInputStream(contentUri)
            inputStream?.let {
                copy(inputStream, oStream)
            }
            oStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return tempFile
    }

    @Throws(java.io.IOException::class)
    private fun copy(source: InputStream, target: OutputStream) {
        val buf = ByteArray(8192)
        var length: Int
        while (source.read(buf).also { length = it } > 0) {
            target.write(buf, 0, length)
        }
    }
}
