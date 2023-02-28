package github.zerorooot.nap511.factory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel

class FileCookieViewModelFactory(private val cookie: String,private val application: Application): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FileViewModel(cookie,application) as T
    }
}

class OfflineFileCookieViewModelFactory(private val cookie: String,private val application: Application): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return OfflineFileViewModel(cookie,application) as T
    }
}