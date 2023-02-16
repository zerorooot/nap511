package github.zerorooot.nap511.util

import android.content.Context
import android.content.SharedPreferences


class SharedPreferencesUtil(private val context: Context) {
    private val FILENAME = "TOKEN"

    fun save(cookie: String) {
        val preferences: SharedPreferences =
            context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
        val edit = preferences.edit()
        edit.putString("cookie", cookie)
        edit.apply()
    }

    fun get(): String? {
        val preferences: SharedPreferences =
            context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
        return preferences.getString("cookie", null)

    }
}
