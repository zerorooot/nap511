package github.zerorooot.nap511.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import github.zerorooot.nap511.util.LoginResult
import github.zerorooot.nap511.util.OneOneFiveAuthManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreenAccount(authManager: OneOneFiveAuthManager = remember { OneOneFiveAuthManager() }) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // 2FA 弹窗状态
    var showTwoFactorDialog by remember { mutableStateOf(false) }
    var currentUserId by remember { mutableLongStateOf(0L) }
    var maskedMobile by remember { mutableStateOf("") }

    // 图形验证码状态
    var showCaptchaDialog by remember { mutableStateOf(false) }
    var captchaMessage by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "115 账号登录",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                label = { Text("手机号 / 115 账号") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (account.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "请输入账号和密码", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isLoading = true
                    coroutineScope.launch {
                        val res = authManager.login(account, password)
                        isLoading = false
                        when (res) {
                            is LoginResult.Success -> {
                                Toast.makeText(
                                    context,
                                    "登录成功！UID: ${res.userId}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            is LoginResult.NeedTwoFactor -> {
                                currentUserId = res.userId
                                maskedMobile = res.maskedMobile
                                showTwoFactorDialog = true
                            }

                            is LoginResult.NeedCaptcha -> {
                                captchaMessage = res.message
                                showCaptchaDialog = true
                            }

                            is LoginResult.Failure -> {
                                Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("登 录", fontSize = 16.sp)
                }
            }
        }
    }

    // 2FA / 短信核验弹窗
    if (showTwoFactorDialog) {
        TwoFactorVerifyDialog(
            userId = currentUserId,
            maskedMobile = maskedMobile,
            onDismiss = { showTwoFactorDialog = false },
            onSendSms = {
                authManager.sendSms(currentUserId)
            },
            onSubmitCode = { code ->
                when (val result = authManager.submitTwoFactorCode(currentUserId, code)) {
                    is LoginResult.Success -> {
                        showTwoFactorDialog = false
                        Toast.makeText(context, "二次核验成功，已登录！", Toast.LENGTH_LONG).show()
                    }

                    is LoginResult.Failure -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }

                    else -> {}
                }
            }
        )
    }

    // 行为式/图形验证码提示弹窗
// 在 LoginScreen.kt 中：

    if (showCaptchaDialog) {
        CaptchaPromptDialog(
            authManager = authManager,
            onDismiss = { showCaptchaDialog = false },
            onVerifySuccess = { codeString, sign ->
                showCaptchaDialog = false
                isLoading = true
                coroutineScope.launch {
                    // 带上验证码重新调用 login
                    val res = authManager.login(
                        account = account,
                        password = password,
                        captchaCode = codeString,
                        captchaSign = sign
                    )
                    isLoading = false
                    when (res) {
                        is LoginResult.Success -> {
                            Toast.makeText(
                                context,
                                "登录成功！UID: ${res.userId}",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        is LoginResult.NeedTwoFactor -> {
                            currentUserId = res.userId
                            maskedMobile = res.maskedMobile
                            showTwoFactorDialog = true
                        }

                        is LoginResult.NeedCaptcha -> {
                            // 如果输错再次拉起
                            Toast.makeText(context, "验证码错误，请重新输入", Toast.LENGTH_SHORT)
                                .show()
                            showCaptchaDialog = true
                        }

                        is LoginResult.Failure -> {
                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }// 在 LoginScreen.kt 中：

    if (showCaptchaDialog) {
        CaptchaPromptDialog(
            authManager = authManager,
            onDismiss = { showCaptchaDialog = false },
            onVerifySuccess = { codeString, sign ->
                showCaptchaDialog = false
                isLoading = true
                coroutineScope.launch {
                    // 带上验证码重新调用 login
                    val res = authManager.login(
                        account = account,
                        password = password,
                        captchaCode = codeString,
                        captchaSign = sign
                    )
                    isLoading = false
                    when (res) {
                        is LoginResult.Success -> {
                            Toast.makeText(
                                context,
                                "登录成功！UID: ${res.userId}",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        is LoginResult.NeedTwoFactor -> {
                            currentUserId = res.userId
                            maskedMobile = res.maskedMobile
                            showTwoFactorDialog = true
                        }

                        is LoginResult.NeedCaptcha -> {
                            // 如果输错再次拉起
                            Toast.makeText(context, "验证码错误，请重新输入", Toast.LENGTH_SHORT)
                                .show()
                            showCaptchaDialog = true
                        }

                        is LoginResult.Failure -> {
                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }
}

/**
 * 2FA 验证码输入与短信重发组件
 */
@Composable
fun TwoFactorVerifyDialog(
    userId: Long,
    maskedMobile: String,
    onDismiss: () -> Unit,
    onSendSms: suspend () -> Pair<Boolean, String>,
    onSubmitCode: suspend (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var verifyCode by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }

    // 倒计时协程
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000L.milliseconds)
            countdown--
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "两步安全验证", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (maskedMobile.isNotBlank()) "点击获取验证码，验证码将发送至: $maskedMobile " else "请输入 6 位短信验证码或 115 动态安全令",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = verifyCode,
                        onValueChange = { if (it.length <= 6) verifyCode = it },
                        label = { Text("6位验证码") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val (ok, msg) = onSendSms()
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (ok) countdown = 60
                            }
                        },
                        enabled = countdown == 0,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(if (countdown > 0) "${countdown}s" else "获取验证码", fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (verifyCode.length != 6) {
                                Toast.makeText(
                                    context,
                                    "请输入完整的 6 位验证码",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            isSubmitting = true
                            coroutineScope.launch {
                                onSubmitCode(verifyCode)
                                isSubmitting = false
                            }
                        },
                        enabled = !isSubmitting
                    ) {
                        Text("确认验证")
                    }
                }
            }
        }
    }
}
