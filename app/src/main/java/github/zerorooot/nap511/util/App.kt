package github.zerorooot.nap511.util

import android.app.Application

class App : Application() {

    companion object {
        lateinit var instance : App
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}