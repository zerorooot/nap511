package github.zerorooot.nap511.screen

import android.content.Context
import android.graphics.Bitmap.Config
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore

import com.jamal.composeprefs.ui.PrefsScreen
import com.jamal.composeprefs.ui.GroupHeader
import com.jamal.composeprefs.ui.PrefsScreen
import com.jamal.composeprefs.ui.prefs.CheckBoxPref
import com.jamal.composeprefs.ui.prefs.EditTextPref
import com.jamal.composeprefs.ui.prefs.SwitchPref
import com.jamal.composeprefs.ui.prefs.TextPref
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.DataStoreUtil.dataStore

//import com.jamal.composeprefs3.ui.GroupHeader
//import com.jamal.composeprefs3.ui.PrefsScreen
//import com.jamal.composeprefs3.ui.prefs.EditTextPref
//import com.jamal.composeprefs3.ui.prefs.SwitchPref
//import com.jamal.composeprefs3.ui.prefs.TextPref


@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterialApi::class)
@Composable
fun SettingScreen() {

    PrefsScreen(dataStore = App.instance.dataStore) {
        prefsItem {
            EditTextPref(
                key = ConfigUtil.uid,
                title = "用户id",
                summary = DataStoreUtil.getData(ConfigUtil.uid, "xxxxxxxxxx"),
                enabled = false
            )
        }

        prefsItem {
            EditTextPref(
                key = ConfigUtil.cookie,
                title = "登录Cookie",
                summary = "点击更改",
                dialogTitle = "请输入新的cookie值",
            )
        }

        prefsItem {
            EditTextPref(
                key = ConfigUtil.password,
                title = "数字安全密钥",
                summary = "清空回收站文件时输入的密码",
                dialogTitle = "请输入新的密码",
            )
        }
        prefsItem {
            EditTextPref(
                key = ConfigUtil.aria2Url,
                title = "aria2地址",
                summary = DataStoreUtil.getData(ConfigUtil.aria2Url, ConfigUtil.aria2UrldefValue),
                dialogTitle = "请输入新的aria2地址",
            )
        }
        prefsItem {
            EditTextPref(
                key = ConfigUtil.aria2Url,
                title = "aria2秘钥",
                summary = "没有留空即可",
                dialogTitle = "请输入新的aria2秘钥",
            )
        }
        prefsItem {
            SwitchPref(
                key = ConfigUtil.autoRotate,
                title = "屏幕自动旋转",
                summary = "根据视频横竖自动旋转屏幕",
            )
        }
    }
}

@Preview
@Composable
fun a() {
    SettingScreen()
}