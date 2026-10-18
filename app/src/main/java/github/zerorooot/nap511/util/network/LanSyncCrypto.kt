package github.zerorooot.nap511.util.network

import android.net.Uri
import com.google.gson.Gson
import github.zerorooot.nap511.bean.SettingUiState
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import androidx.core.net.toUri
import github.zerorooot.nap511.bean.sync.EncryptedResponse
import github.zerorooot.nap511.bean.sync.LanSyncType
import github.zerorooot.nap511.bean.sync.LanSyncUriInfo
import github.zerorooot.nap511.bean.sync.SyncPayload

/**
 * 局域网同步加解密与协议编解码工具类
 */
object LanSyncCrypto {
    private val secureRandom = SecureRandom()
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BIT_LENGTH = 128
    const val URI_SCHEME = "nap511"
    const val URI_HOST = "lan-sync"

    /**
     * 生成 32 字节高熵随机 AES-256 密钥
     */
    fun generateRandomKey(): ByteArray = ByteArray(32).also { secureRandom.nextBytes(it) }

    /**
     * 生成 16 字节随机 Token（Hex 编码，共 32 个字符）
     */
    fun generateTokenHex(): String {
        val bytes = ByteArray(16).also { secureRandom.nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 构建 nap511://lan-sync?... 控制信道 URI
     */
    fun buildSyncUri(endpointUrl: String, tokenHex: String, keyBytes: ByteArray): String {
        val encodedUrl = Uri.encode(endpointUrl)
        val keyBase64Url = keyBytes.toByteString().base64Url()
        return "$URI_SCHEME://$URI_HOST?url=$encodedUrl&t=$tokenHex&k=$keyBase64Url"
    }

    /**
     * 解析 nap511://lan-sync?... 控制信道 URI
     */
    fun parseSyncUri(uriString: String): LanSyncUriInfo? {
        return try {
            val uri = uriString.trim().toUri()
            if (uri.scheme != URI_SCHEME || uri.host != URI_HOST) {
                return null
            }
            val rawUrl = uri.getQueryParameter("url") ?: return null
            val tokenHex = uri.getQueryParameter("t") ?: return null
            val keyBase64Url = uri.getQueryParameter("k") ?: return null

            val endpointUrl = Uri.decode(rawUrl)
            val keyBytes = keyBase64Url.decodeBase64()?.toByteArray() ?: return null
            if (keyBytes.size != 32 || tokenHex.length != 32) {
                return null
            }

            LanSyncUriInfo(endpointUrl, tokenHex, keyBytes)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * AES-GCM 加密业务明文
     */
    fun encryptAesGcm(plainText: String, keyBytes: ByteArray): EncryptedResponse {
        val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BIT_LENGTH, iv))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return EncryptedResponse(
            iv = iv.toByteString().base64Url(),
            cipher = cipherBytes.toByteString().base64Url()
        )
    }

    /**
     * AES-GCM 解密返回业务明文
     */
    fun decryptAesGcm(encrypted: EncryptedResponse, keyBytes: ByteArray): String {
        val iv = encrypted.iv.decodeBase64()?.toByteArray() ?: error("无效 IV")
        val cipherBytes = encrypted.cipher.decodeBase64()?.toByteArray() ?: error("无效密文")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BIT_LENGTH, iv))
        return String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
    }

    /**
     * 常量时间字符串比较，防止针对 Token 鉴权的时序侧信道攻击
     */
    fun constantTimeEquals(a: String, b: String): Boolean {
        return MessageDigest.isEqual(
            a.toByteArray(StandardCharsets.UTF_8),
            b.toByteArray(StandardCharsets.UTF_8)
        )
    }

    /**
     * 根据二选一模式组装待发送的明文 JSON 字符串
     */
    fun buildPayloadJson(
        syncType: LanSyncType,
        currentState: SettingUiState,
        gson: Gson
    ): String {
        val payload = when (syncType) {
            LanSyncType.ACCOUNT_CREDENTIAL -> {
                // 模式 1：仅传输 Cookie 与 UID，不附带应用配置
                SyncPayload(
                    cookie = currentState.cookie.takeIf { it.isNotBlank() },
                    uid = currentState.uid.takeIf { it.isNotBlank() },
                    configJson = null,
                    ts = System.currentTimeMillis()
                )
            }
            LanSyncType.APP_CONFIG -> {
                // 模式 2：完整同步 SettingUiState（保留 Cookie、UID 及全部功能配置项，不抹除凭证）
                val fullConfigJson = gson.toJson(currentState)
                SyncPayload(
                    cookie = currentState.cookie.takeIf { it.isNotBlank() },
                    uid = currentState.uid.takeIf { it.isNotBlank() },
                    configJson = fullConfigJson,
                    ts = System.currentTimeMillis()
                )
            }
        }
        return gson.toJson(payload)
    }
}
