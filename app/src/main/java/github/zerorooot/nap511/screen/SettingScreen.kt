package github.zerorooot.nap511.screen

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.tooling.preview.Preview
import com.jamal.composeprefs.ui.PrefsScreen
import com.jamal.composeprefs.ui.prefs.EditTextPref
import com.jamal.composeprefs.ui.prefs.SwitchPref
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.DataStoreUtil.dataStore


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
                key = ConfigUtil.aria2Token,
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