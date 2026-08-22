package github.zerorooot.nap511.screen

import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.nativeClipboardManager
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil

sealed interface LoginCredential {
    // 1. 账号密码登录
    data class AccountPassword(
        val username: String,
        val password: String
    ) : LoginCredential

    // 2. Cookie 登录 (无论是网页捕获还是手动粘贴)
    data class Cookie(
        val cookieString: String
    ) : LoginCredential

    // 3. 配置文件导入登录
    data class ConfigFile(
        val rawJson: String
    ) : LoginCredential
}

// ==========================================
// 1. 主登录界面 (LoginScreen)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (credential: LoginCredential) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 账号密码, 1: 网页登录
    var showAdvancedSheet by remember { mutableStateOf(false) }
    val uid = remember { DataStoreUtil.getData(ConfigKeyUtil.UID, "") }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 头部 Logo & 欢迎语
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (uid == "") Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("欢迎使用nap511", style = MaterialTheme.typography.headlineMedium)
            }

            // 中部核心登录卡片
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // 分段切换按钮 (SegmentedButton)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("账号登录") }
                        SegmentedButton(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("网页登录") }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 平滑切换表单内容
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "form_switch"
                    ) { tab ->
                        when (tab) {
                            0 -> AccountPasswordForm(onLogin = { u, p ->
                                onLoginSuccess(
                                    LoginCredential.AccountPassword(
                                        username = u,
                                        password = p
                                    )
                                )
                            })

                            1 -> {
                                WebLoginCardSection {
                                    selectedTab = 0
                                }
                            }
                        }
                    }
                }
            }

            // 底部高级登录入口
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                TextButton(onClick = { showAdvancedSheet = true }) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cookie登录 / 配置导入")
                }
            }
        }
    }

    // 底部展开框：高级登录
    if (showAdvancedSheet) {
        ModalBottomSheet(onDismissRequest = { showAdvancedSheet = false }) {
            AdvancedLoginSheetContent(
                onCookieSubmit = { cookie ->
                    showAdvancedSheet = false
                    onLoginSuccess(LoginCredential.Cookie(cookie))
                },
                onConfigImported = { configJson ->
                    showAdvancedSheet = false
                    onLoginSuccess(LoginCredential.ConfigFile(configJson))
                }
            )
        }
    }
}


// ==========================================
// 2. 方式一：账号密码表单
// ==========================================
@Composable
private fun AccountPasswordForm(
    onLogin: (username: String, pass: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("账号 / 邮箱") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentType = ContentType.Username // 标记为用户名/账号自动填充类型
                },
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentType = ContentType.Password // 标记为密码自动填充类型
                },
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { onLogin(username, password) },
            enabled = username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("登录")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebLoginCardSection(onClick: () -> Unit) {
    Dialog(
        onDismissRequest = { onClick.invoke() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, // 关键：禁用默认宽度限制，实现全屏
        )
    ) {
        val view = LocalView.current
        // 配置 Dialog 独立 Window 的系统栏属性
        DisposableEffect(view) {
            val dialogWindow = (view.parent as? DialogWindowProvider)?.window
            //清除 Dialog 默认的变暗蒙层（解决状态栏和全屏内容整体发灰）
            dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            onDispose {}
        }

        LoginWebViewScreen {
            onClick.invoke()
        }
    }

}

// ==========================================
// 4. 高级模式：Cookie 粘贴与文件导入 Sheet
// ==========================================
@Composable
private fun AdvancedLoginSheetContent(
    onCookieSubmit: (String) -> Unit,
    onConfigImported: (String) -> Unit
) {
    var subTab by remember { mutableIntStateOf(0) } // 0: Cookie, 1: 导入文件
    var cookieInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboard.current
    val context = LocalContext.current

    // 系统 SAF 文件选择器（支持 .json 或所有文件）
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val content =
                    context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                        reader.readText()
                    }
                if (!content.isNullOrBlank()) {
                    onConfigImported(content)
                } else {
                    App.instance.toast("文件内容为空")
                }
            } catch (e: Exception) {
                App.instance.toast("读取文件失败: ${e.message}")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text("其他登录方式", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        // 二级切换选项
        PrimaryTabRow(selectedTabIndex = subTab) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }) {
                Text("手动 Cookie", modifier = Modifier.padding(vertical = 12.dp))
            }
            Tab(selected = subTab == 1, onClick = { subTab = 1 }) {
                Text("导入配置文件", modifier = Modifier.padding(vertical = 12.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (subTab) {
            0 -> {
                // Cookie 粘贴区域
                OutlinedTextField(
                    value = cookieInput,
                    onValueChange = { cookieInput = it },
                    label = { Text("粘贴 Cookie 字符串") },
                    placeholder = { Text("session=xxxx; token=yyyy") },
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboardManager.nativeClipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
                                ?.let {
                                    cookieInput = it
                                }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "粘贴剪贴板")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onCookieSubmit(cookieInput) },
                    enabled = cookieInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("使用 Cookie 登录")
                }
            }

            1 -> {
                // 配置文件导入区域
                OutlinedCard(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "点击选择 \".json\" 文件",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "支持选择导出的配置文件",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}