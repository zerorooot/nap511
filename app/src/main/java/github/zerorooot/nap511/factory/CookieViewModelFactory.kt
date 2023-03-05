package github.zerorooot.nap511.factory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CookieViewModelFactory(
    private val cookie: String,
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return modelClass.getDeclaredConstructor(String::class.java, Application::class.java)
            .newInstance(cookie, application) as T
    }
}