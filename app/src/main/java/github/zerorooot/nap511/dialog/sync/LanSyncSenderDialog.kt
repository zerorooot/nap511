package github.zerorooot.nap511.dialog.sync

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.Gson
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.copy
import github.zerorooot.nap511.util.network.LanIpUtil
import github.zerorooot.nap511.util.network.LanSyncCrypto
import github.zerorooot.nap511.util.network.LanSyncServerSession
import github.zerorooot.nap511.util.network.LanSyncType
import github.zerorooot.nap511.util.network.NetworkIpInfo
import github.zerorooot.nap511.util.qr.QrCodeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * 局域网同步发送端弹窗 (LanSyncSenderDialog)
 * 启动微型 HTTP 服务并展示 ZXing 二维码，支持模式二选一与剪贴板备用口令复制
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanSyncSenderDialog(
    currentState: SettingUiState,
    onDismissRequest: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val gson = remember { Gson() }

    // 1. 本机局域网 IP 列表采集与选中状态
    val availableIps = remember { LanIpUtil.getAvailableIps() }
    var selectedIpInfo by remember { mutableStateOf(availableIps.firstOrNull()) }
    var isIpDropdownExpanded by remember { mutableStateOf(false) }

    // 2. 同步模式二选一：默认仅同步账号凭据
    var syncType by remember { mutableStateOf(LanSyncType.ACCOUNT_CREDENTIAL) }

    // 3. 服务端状态与凭据
    var serverSession by remember { mutableStateOf<LanSyncServerSession?>(null) }
    var currentUriString by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var remainingSeconds by remember { mutableIntStateOf(60) }
    var serverStatusMessage by remember { mutableStateOf("正在启动服务...") }

    // 启动/重启服务的核心函数
    fun startOrRestartServer() {
        serverSession?.stop()
        serverSession = null
        remainingSeconds = 60

        val hostIp = selectedIpInfo?.ip
        if (hostIp.isNullOrBlank()) {
            serverStatusMessage = "未检测到有效局域网 IP，请检查 Wi-Fi 或热点连接"
            qrBitmap = null
            currentUriString = ""
            return
        }

        val keyBytes = LanSyncCrypto.generateRandomKey()
        val tokenHex = LanSyncCrypto.generateTokenHex()

        val session = LanSyncServerSession(
            hostIp = hostIp,
            expectedToken = tokenHex,
            keyBytes = keyBytes,
            payloadProvider = {
                // 动态获取当前选定模式的明文 JSON
                LanSyncCrypto.buildPayloadJson(syncType, currentState, gson)
            },
            onCompleted = {
                coroutineScope.launch(Dispatchers.Main) {
                    App.instance.toast("同步成功！接收端已获取数据")
                    onDismissRequest()
                }
            },
            onError = { errMsg ->
                coroutineScope.launch(Dispatchers.Main) {
                    serverStatusMessage = errMsg
                    App.instance.toast(errMsg)
                }
            }
        )

        try {
            val port = session.start(coroutineScope)
            serverSession = session
            val endpointUrl = "http://$hostIp:$port/sync"
            val uri = LanSyncCrypto.buildSyncUri(endpointUrl, tokenHex, keyBytes)
            currentUriString = uri

            // 后台生成二维码位图
            coroutineScope.launch(Dispatchers.Default) {
                val bitmap = QrCodeUtil.generateQrCodeBitmap(uri, 512)
                withContext(Dispatchers.Main) {
                    qrBitmap = bitmap
                    serverStatusMessage = "服务监听中 (端口: $port)"
                }
            }
        } catch (e: Exception) {
            serverStatusMessage = "服务启动失败: ${e.localizedMessage}"
        }
    }

    // 监听选中的 IP 或模式变化，重新生成服务与二维码
    LaunchedEffect(selectedIpInfo, syncType) {
        startOrRestartServer()
    }

    // 60 秒倒计时驱动
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000.milliseconds)
            remainingSeconds -= 1
        }
    }

    // 弹窗退出时确保服务端关闭
    DisposableEffect(Unit) {
        onDispose {
            serverSession?.stop()
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部标题与倒计时
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "局域网同步口令生成",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "有效时限: ${remainingSeconds}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (remainingSeconds <= 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 中央二维码展示区域
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = qrBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "同步二维码",
                            modifier = Modifier.size(220.dp)
                        )
                    } else {
                        Text(
                            text = serverStatusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 多网卡 IP 下拉选择器（当检测到多网卡时支持切换）
                if (availableIps.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = isIpDropdownExpanded,
                        onExpandedChange = { isIpDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedIpInfo?.displayName ?: "选择网络 IP",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("切换网络接口 IP") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isIpDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = isIpDropdownExpanded,
                            onDismissRequest = { isIpDropdownExpanded = false }
                        ) {
                            availableIps.forEach { ipInfo ->
                                DropdownMenuItem(
                                    text = { Text("${ipInfo.displayName} - ${ipInfo.ip}") },
                                    onClick = {
                                        selectedIpInfo = ipInfo
                                        isIpDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 同步模式单选按钮组（二选其一）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "同步模式选择 (二选一)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    // 选项 1：仅同步账号凭据
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .selectable(
                                selected = (syncType == LanSyncType.ACCOUNT_CREDENTIAL),
                                onClick = { syncType = LanSyncType.ACCOUNT_CREDENTIAL },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (syncType == LanSyncType.ACCOUNT_CREDENTIAL),
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "同步账号凭据 (Cookie / UID)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "仅同步账号登录态，不改动对方应用设置",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    // 选项 2：同步应用设置 (包含账号凭证)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .selectable(
                                selected = (syncType == LanSyncType.APP_CONFIG),
                                onClick = { syncType = LanSyncType.APP_CONFIG },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (syncType == LanSyncType.APP_CONFIG),
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "同步应用设置 (全部功能配置)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "完整克隆全部偏好设置，并包含当前账号登录凭证",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 底部操作区：复制口令 & 刷新 & 关闭
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (currentUriString.isNotBlank()) {
                                currentUriString.copy(context, "nap511-sync")
                                App.instance.toast("同步口令已复制至剪贴板")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制口令", fontSize = 13.sp)
                    }

                    Button(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}
