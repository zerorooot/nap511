package github.zerorooot.nap511.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.chillibits.simplesettings.core.SimpleSettings
import github.zerorooot.nap511.R

@Composable
fun SettingScreen() {
//https://github.com/marcauberer/simple-settings
    val context = LocalContext.current
    SimpleSettings(context).show(R.xml.preferences)
}