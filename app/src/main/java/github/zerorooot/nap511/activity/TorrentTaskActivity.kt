package github.zerorooot.nap511.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.viewmodel.platform.ToastProvider
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

@AndroidEntryPoint
class TorrentTaskActivity : androidx.activity.ComponentActivity() {

    @Inject
    lateinit var fileRepository: FileRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var toastProvider: ToastProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val torrentFile = fileFromContentUri(this, intent.data!!)
            lifecycleScope.launch {
                val defaultOfflineCid =
                    settingsRepository.getDataSuspend(ConfigKeyUtil.DEFAULT_OFFLINE_CID, "")
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
                toastProvider.showToast(message)
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
