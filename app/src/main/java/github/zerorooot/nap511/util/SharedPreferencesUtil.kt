package github.zerorooot.nap511.util

import android.content.Context
import android.content.SharedPreferences


class SharedPreferencesUtil(private val context: Context) {
    private val FILENAME = "TOKEN"

    fun save(name: String, value: String) {
        val preferences: SharedPreferences =
            context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
        val edit = preferences.edit()
        edit.putString(name, value)
        edit.apply()
    }

    fun get(name:String): String? {
        val preferences: SharedPreferences =
            context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
        return preferences.getString(name, null)

    }
}
