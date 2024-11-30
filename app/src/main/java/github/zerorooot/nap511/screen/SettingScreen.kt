package github.zerorooot.nap511.screen

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.tooling.preview.Preview
import com.jamal.composeprefs.ui.PrefsScreen
import com.jamal.composeprefs.ui.prefs.EditTextPref
import com.jamal.composeprefs.ui.prefs.SwitchPref
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.DataStoreUtil.dataStore


@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterialApi::class)
@Composable
fun SettingScreen() {

    PrefsScreen(dataStore = App.instance.dataStore) {
        prefsItem {
            EditTextPref(
                key = ConfigKeyUtil.UID,
                title = "用户id",
                summary = DataStoreUtil.getData(ConfigKeyUtil.UID, "xxxxxxxxxx"),
                enabled = false
            )
        }

        prefsItem {
            EditTextPref(
                key = ConfigKeyUtil.COOKIE,
                title = "登录Cookie",
                summary = "点击更改",
                dialogTitle = "请输入新的cookie值",
            )
        }

        prefsItem {
            EditTextPref(
                key = ConfigKeyUtil.PASSWORD,
                title = "数字安全密钥",
                summary = "清空回收站文件时输入的密码",
                dialogTitle = "请输入新的密码",
            )
        }
        prefsItem {
            EditTextPref(
                key = ConfigKeyUtil.ARIA2_URL,
                title = "aria2地址",
                summary = DataStoreUtil.getData(ConfigKeyUtil.ARIA2_URL, ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE),
                dialogTitle = "请输入新的aria2地址",
            )
        }
        prefsItem {
            EditTextPref(
                key = ConfigKeyUtil.ARIA2_TOKEN,
                title = "aria2秘钥",
                summary = "没有留空即可",
                dialogTitle = "请输入新的aria2秘钥",
            )
        }
        prefsItem {
            EditTextPref(
                key = ConfigKeyUtil.REQUEST_LIMIT_COUNT,
                title = "每次请求文件数",
                summary = DataStoreUtil.getData(ConfigKeyUtil.REQUEST_LIMIT_COUNT, "100"),
                defaultValue = DataStoreUtil.getData(ConfigKeyUtil.REQUEST_LIMIT_COUNT, "100"),
                dialogTitle = "每次请求文件数",
            )
        }
        prefsItem {
            SwitchPref(
                key = ConfigKeyUtil.AUTO_ROTATE,
                title = "屏幕自动旋转",
                summary = "根据视频横竖自动旋转屏幕",
            )
        }
        prefsItem {
            SwitchPref(
                key = ConfigKeyUtil.EARLY_LOADING,
                title = "提前加载",
                summary = "进入下级目录时，提前加载当前文件夹上下两个文件夹内的文件",
            )
        }
        prefsItem {
            EditTextPref(
                key = ConfigKeyUtil.DEFAULT_OFFLINE_CID,
                title = "默认离线位置",
                summary = DataStoreUtil.getData(
                    ConfigKeyUtil.DEFAULT_OFFLINE_CID, "输入文件夹cid,长按目录可复制当前目录cid"
                ),
                dialogTitle = "长按路径可当前目录cid",
            )
        }
        prefsItem {
            SwitchPref(
                key = ConfigKeyUtil.OFFLINE_METHOD,
                title = "离线任务缓存方式",
                summary = "true为x分钟后统一下载，false为集满x个后统一下载。更改后需重启",
                defaultChecked = true
            )
        }

        if (DataStoreUtil.getData(
                ConfigKeyUtil.OFFLINE_METHOD,
                true
            )
        ) {
            prefsItem {
                EditTextPref(
                    key = ConfigKeyUtil.DEFAULT_OFFLINE_TIME,
                    title = "离线任务延迟时间",
                    summary = DataStoreUtil.getData(
                        ConfigKeyUtil.DEFAULT_OFFLINE_TIME, "5"
                    ),
                    defaultValue = DataStoreUtil.getData(
                        ConfigKeyUtil.DEFAULT_OFFLINE_TIME, "5"
                    ),
                    dialogTitle = "延迟${
                        DataStoreUtil.getData(
                            ConfigKeyUtil.DEFAULT_OFFLINE_TIME, "5"
                        )
                    }分钟后统一离线下载",
                )
            }
        }else{
            prefsItem {
                EditTextPref(
                    key = ConfigKeyUtil.DEFAULT_OFFLINE_COUNT,
                    title = "离线任务缓存数",
                    summary = DataStoreUtil.getData(
                        ConfigKeyUtil.DEFAULT_OFFLINE_COUNT, "5"
                    ),
                    defaultValue = DataStoreUtil.getData(
                        ConfigKeyUtil.DEFAULT_OFFLINE_COUNT, "5"
                    ),
                    dialogTitle = "集满${
                        DataStoreUtil.getData(
                            ConfigKeyUtil.DEFAULT_OFFLINE_COUNT, "5"
                        )
                    }个链接后统一离线下载",
                )
            }
        }

        prefsItem {
            EditTextPref(
                key = ConfigKeyUtil.CURRENT_OFFLINE_TASK,
                title = "离线任务缓存",
                summary = DataStoreUtil.getData(
                    ConfigKeyUtil.CURRENT_OFFLINE_TASK, "当前尚未添加离线任务的链接"
                ),
                dialogTitle = if (DataStoreUtil.getData(
                        ConfigKeyUtil.OFFLINE_METHOD,
                        false
                    )
                ) {
                    "尚未离线的任务链接"
                } else {
                    "尚未离线的任务链接 ${
                        DataStoreUtil.getData(
                            ConfigKeyUtil.CURRENT_OFFLINE_TASK, ""
                        ).split("\n").filter { i -> i != "" && i != " " }.toSet().size
                    }/${
                        DataStoreUtil.getData(
                            ConfigKeyUtil.DEFAULT_OFFLINE_COUNT, "5"
                        )
                    }"
                },
            )
        }
    }
}

@Preview
@Composable
fun a() {
    SettingScreen()
}