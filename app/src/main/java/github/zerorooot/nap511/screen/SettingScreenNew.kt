package github.zerorooot.nap511.screen

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.commit
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
import androidx.preference.PreferenceFragmentCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.R
import github.zerorooot.nap511.activity.OfflineTaskWorker
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.util.SettingsDataStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreenNew(topAppBarActionButtonOnClick: () -> Unit) {
    Column {
        TopAppBar(
            title = {
                Text(text = ConfigKeyUtil.ADVANCED_SETTINGS)
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Purple80),
            navigationIcon = {
                TopAppBarActionButton(
                    imageVector = Icons.Rounded.Menu, description = "navigationIcon"
                ) {
                    topAppBarActionButtonOnClick()
                }
            },
        )
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AndroidView(
                factory = { ctx ->
                    val fragmentContainer = FrameLayout(ctx).apply { id = View.generateViewId() }
                    val fragmentManager = (ctx as? AppCompatActivity)?.supportFragmentManager
                    fragmentManager?.commit {
                        replace(fragmentContainer.id, SettingsFragment())
                    }
                    fragmentContainer
                })
        }

    }
}

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = SettingsDataStore()
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        // ✅ 取消所有 Preference 的 icon 预留空间
        preferenceScreen?.let { screen ->
            for (i in 0 until screen.preferenceCount) {
                screen.getPreference(i).isIconSpaceReserved = false
            }
        }

        findPreference<Preference>("checkMagnet")?.setOnPreferenceClickListener {
            App.selectedItem = ConfigKeyUtil.VERIFY_MAGNET_LINK_ACCOUNT
            true
        }
        findPreference<Preference>("checkVideo")?.setOnPreferenceClickListener {
            App.selectedItem = ConfigKeyUtil.VERIFY_VIDEO_ACCOUNT
            true
        }
        findPreference<Preference>(ConfigKeyUtil.CLICK_DOWNLOAD_NOW)?.setOnPreferenceClickListener {
            addOfflineTask(App.cookie)
            true
        }

        findPreference<EditTextPreference>(ConfigKeyUtil.UID)?.summaryProvider =
            SummaryProvider<EditTextPreference> { preference ->
                DataStoreUtil.getData(ConfigKeyUtil.UID, "0")
            }

        findPreference<EditTextPreference>(ConfigKeyUtil.PASSWORD)?.apply {
            setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
        }

        findPreference<EditTextPreference>(ConfigKeyUtil.ARIA2_URL)?.summaryProvider =
            SummaryProvider<EditTextPreference> { preference ->
                DataStoreUtil.getData(
                    ConfigKeyUtil.ARIA2_URL, ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE
                )
            }

        findPreference<EditTextPreference>(ConfigKeyUtil.REQUEST_LIMIT_COUNT)?.apply {
            this.summaryProvider = SummaryProvider<EditTextPreference> { preference ->
                DataStoreUtil.getData(
                    ConfigKeyUtil.REQUEST_LIMIT_COUNT, "100"
                )
            }
            this.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
        }
        findPreference<EditTextPreference>(ConfigKeyUtil.DEFAULT_OFFLINE_TIME)?.summaryProvider =
            SummaryProvider<EditTextPreference> { preference ->
                "延迟" + DataStoreUtil.getData(
                    ConfigKeyUtil.DEFAULT_OFFLINE_TIME, "5"
                ) + "分钟后统一离线下载"
            }

    }

    private fun addOfflineTask(cookie: String) {
        val workManager = WorkManager.getInstance(requireContext())
        val workQuery = WorkQuery.Builder.fromStates(
            listOf(
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING,
//                WorkInfo.State.SUCCEEDED,
//                WorkInfo.State.FAILED,
                WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED
            )
        ).build()
        val workInfos: List<WorkInfo> = workManager.getWorkInfos(workQuery).get()
        //取消所有后台任务
        if (workInfos.isNotEmpty()) {
            workInfos.forEach {
                workManager.cancelWorkById(it.id)
            }
        }

        val currentOfflineTask =
            DataStoreUtil.getData(ConfigKeyUtil.CURRENT_OFFLINE_TASK, "").split("\n")
                .filter { i -> i != "" && i != " " }.toSet().toMutableList()
        //currentOfflineTask等于0,证明没有离线链接缓存
        if (currentOfflineTask.isEmpty()) {
            App.instance.toast("没有离线任务！")
            return
        }
        XLog.d(
            "addOfflineTask workManager workInfos $workInfos currentOfflineTask size ${currentOfflineTask.size}"
        )
        //添加离线链接
        val listType = object : TypeToken<List<String?>?>() {}.type
        val list = Gson().toJson(currentOfflineTask, listType)
        val data: Data = Data.Builder().putString("cookie", cookie).putString("list", list).build()
        val request: OneTimeWorkRequest = OneTimeWorkRequest.Builder(OfflineTaskWorker::class.java)
            .addTag(ConfigKeyUtil.OFFLINE_TASK_WORKER).setInputData(data).build()
        workManager.enqueue(request)
    }
}
