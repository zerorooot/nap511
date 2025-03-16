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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.Preferences
import androidx.fragment.app.commit
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
import androidx.preference.PreferenceFragmentCompat
import github.zerorooot.nap511.R
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
                    imageVector = Icons.Rounded.Menu,
                    description = "navigationIcon"
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
                }
            )
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
                    ConfigKeyUtil.ARIA2_URL,
                    ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE
                )
            }

        findPreference<EditTextPreference>(ConfigKeyUtil.REQUEST_LIMIT_COUNT)?.apply {
            this.summaryProvider = SummaryProvider<EditTextPreference> { preference ->
                DataStoreUtil.getData(
                    ConfigKeyUtil.REQUEST_LIMIT_COUNT,
                    "100"
                )
            }
            this.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
        }
        findPreference<EditTextPreference>(ConfigKeyUtil.DEFAULT_OFFLINE_TIME)?.summaryProvider =
            SummaryProvider<EditTextPreference> { preference ->
                "延迟" + DataStoreUtil.getData(
                    ConfigKeyUtil.DEFAULT_OFFLINE_TIME,
                    "5"
                ) + "分钟后统一离线下载"
            }

    }
}
