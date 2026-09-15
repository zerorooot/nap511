package github.zerorooot.nap511.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import github.zerorooot.nap511.repository.AuthRepository
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.screen.auth.LoginCredential
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginViewModel : ViewModel() {

    fun performLogin(credential: LoginCredential, onLoginSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = when (credential) {
                is LoginCredential.Cookie -> {
                    val replace = credential.cookieString.replace(" ", "")
                        .replace("[\r\n]".toRegex(), "")
                    AuthRepository.checkLogin(replace)
                        .onSuccess { App.instance.toast("登录成功～") }
                        .onFailure { App.instance.toast("验证失败: ${it.localizedMessage}") }
                        .isSuccess
                }

                is LoginCredential.ConfigFile -> {
                    try {
                        val gson = GsonBuilder().setPrettyPrinting().create()
                        val jsonString = credential.rawJson
                        val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)

                        jsonObject.entrySet().forEach { (key, element) ->
                            if (element.isJsonPrimitive) {
                                val primitive = element.asJsonPrimitive
                                when {
                                    primitive.isBoolean -> SettingsRepository.saveData(
                                        key,
                                        primitive.asBoolean
                                    )

                                    primitive.isString -> SettingsRepository.saveData(
                                        key,
                                        primitive.asString
                                    )

                                    primitive.isNumber -> SettingsRepository.saveData(
                                        key,
                                        primitive.asNumber
                                    )
                                }
                            }
                        }
                        val cookie = jsonObject.get(ConfigKeyUtil.COOKIE).asString
                        AuthRepository.checkLogin(cookie)
                            .onSuccess { App.instance.toast("登录成功～") }
                            .onFailure { App.instance.toast("验证失败: ${it.localizedMessage}") }
                            .isSuccess
                    } catch (e: Exception) {
                        App.instance.toast("解析配置失败")
                        XLog.e(
                            "LoginScreen LoginCredential.ConfigFile jsonString ${credential.rawJson}",
                            e
                        )
                        false
                    }
                }
            }
            if (success) {
                withContext(Dispatchers.Main) {
                    onLoginSuccess()
                }
            }
        }
    }
}
