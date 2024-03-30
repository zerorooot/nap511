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
            EditTextPref(
                key = ConfigUtil.requestLimitCount,
                title = "每次请求文件数",
                summary = DataStoreUtil.getData(ConfigUtil.requestLimitCount, "100"),
                defaultValue = DataStoreUtil.getData(ConfigUtil.requestLimitCount, "100"),
                dialogTitle = "每次请求文件数",
            )
        }
        prefsItem {
            SwitchPref(
                key = ConfigUtil.autoRotate,
                title = "屏幕自动旋转",
                summary = "根据视频横竖自动旋转屏幕",
            )
        }
        prefsItem {
            EditTextPref(
                key = ConfigUtil.defaultOfflineCid,
                title = "默认离线位置",
                summary = DataStoreUtil.getData(
                    ConfigUtil.defaultOfflineCid, "输入文件夹cid,长按目录可复制当前目录cid"
                ),
                dialogTitle = "长按路径可当前目录cid",
            )
        }
        prefsItem {
            EditTextPref(
                key = ConfigUtil.defaultOfflineCount,
                title = "离线任务缓存数",
                summary = DataStoreUtil.getData(
                    ConfigUtil.defaultOfflineCount, "5"
                ),
                defaultValue = DataStoreUtil.getData(
                    ConfigUtil.defaultOfflineCount, "5"
                ),
                dialogTitle = "集满${
                    DataStoreUtil.getData(
                        ConfigUtil.defaultOfflineCount, "5"
                    )
                }个链接后统一离线下载",
            )
        }
        prefsItem {
            EditTextPref(
                key = ConfigUtil.currentOfflineTask,
                title = "离线任务缓存",
                summary = DataStoreUtil.getData(
                    ConfigUtil.currentOfflineTask, "当前尚未添加离线任务的链接"
                ),
                dialogTitle = "尚未离线的任务链接 ${
                    DataStoreUtil.getData(
                        ConfigUtil.currentOfflineTask, ""
                    ).split("\n").filter { i -> i != "" && i != " " }.toSet().size
                }/${
                    DataStoreUtil.getData(
                        ConfigUtil.defaultOfflineCount, "5"
                    )
                }",
            )
        }
    }
}

@Preview
@Composable
fun a() {
    SettingScreen()
}