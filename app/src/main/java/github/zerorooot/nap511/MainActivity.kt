package github.zerorooot.nap511

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import github.zerorooot.nap511.activity.VideoActivity
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.factory.CookieViewModelFactory
import github.zerorooot.nap511.screen.CookieDialog
import github.zerorooot.nap511.screen.FileScreen
import github.zerorooot.nap511.ui.theme.Nap511Theme
import github.zerorooot.nap511.util.SharedPreferencesUtil
import github.zerorooot.nap511.viewmodel.FileViewModel

class MainActivity : ComponentActivity() {
    @SuppressLint("CoroutineCreationDuringComposition")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Nap511Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val cookie = SharedPreferencesUtil(this).get()
                    if (cookie == null) {
                        CookieDialog {
                            if (it != "") {
                                SharedPreferencesUtil(this).save(it.replace(" ", ""))
                                Toast.makeText(this, "Cookie保存成功", Toast.LENGTH_SHORT)
                                    .show()

                                finish()
                                startActivity(intent)
                            } else {
                                Toast.makeText(this, "请输入cookie", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        }
                        return@Surface
                    }

                    val fileViewModel by viewModels<FileViewModel> {
                        CookieViewModelFactory(
                            cookie,
                            this.application
                        )
                    }


                    fileViewModel.init()

                    val fileBeanList = fileViewModel.fileBeanList
                    FileScreen(
                        fileViewModel,
                        onItemClick(cookie, fileViewModel),
                        appBarClick(fileBeanList, fileViewModel),
                    )

                }
            }
        }
    }

    private fun appBarClick(fileBeanList: List<FileBean>, fileViewModel: FileViewModel) =
        fun(name: String) {
            when (name) {
                "cut" -> fileViewModel.cut()
                "search" -> search(fileBeanList, fileViewModel)
                "delete" -> fileViewModel.deleteMultiple()
                "selectAll" -> fileViewModel.selectAll()
                "selectReverse" -> fileViewModel.selectReverse()
                "0" -> orderByName(fileBeanList, fileViewModel)
                "1" -> orderByCreateTime(fileBeanList, fileViewModel)
                "2" -> orderByChangeTime(fileBeanList, fileViewModel)
                "3" -> fileViewModel.refresh()
            }
        }

    private fun orderByName(fileBeanList: List<FileBean>, fileViewModel: FileViewModel) {

    }

    private fun orderByCreateTime(fileBeanList: List<FileBean>, fileViewModel: FileViewModel) {

    }

    private fun orderByChangeTime(fileBeanList: List<FileBean>, fileViewModel: FileViewModel) {

    }


    private fun search(fileBeanList: List<FileBean>, fileViewModel: FileViewModel) {

    }


    private fun onItemClick(cookie: String, fileViewModel: FileViewModel) =
        fun(index: Int) {
            val fileBean = fileViewModel.fileBeanList[index]
            if (fileBean.isFolder) {
                fileViewModel.getFiles(fileBean.categoryId)
            }
            if (fileBean.isVideo == 1) {
                val intent = Intent(this, VideoActivity::class.java)
                intent.putExtra("cookie", cookie)
                intent.putExtra("title", fileBean.name)
                intent.putExtra("pick_code", fileBean.pickCode)
                startActivity(intent)
            }
        }

}



