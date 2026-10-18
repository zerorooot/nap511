package github.zerorooot.nap511.bean.sync

import com.google.gson.annotations.SerializedName

/**
 * 局域网同步类型枚举（二选一模式）
 */
enum class LanSyncType {
    /** 模式 1：仅账号凭据 (Cookie / UID)，不附带任何应用配置 */
    ACCOUNT_CREDENTIAL,

    /** 模式 2：同步全部应用设置 (包含 SettingUiState 全部配置项以及 Cookie 与 UID 凭据) */
    APP_CONFIG
}

/**
 * 加密响应数据结构
 *
 * 通过 AES-GCM 加密后的数据传输容器，使用 @SerializedName 显式保护字段名，
 * 避免在 Release 构建混淆时字段名被压缩压缩，确保跨版本、跨构建类型解析一致。
 *
 * @param iv Base64Url 编码的 12 字节随机初始化向量
 * @param cipher Base64Url 编码的 AES-GCM 密文（含 128 位认证标签）
 */
data class EncryptedResponse(
    @SerializedName("iv")
    val iv: String,

    @SerializedName("cipher")
    val cipher: String
)

/**
 * 局域网同步传输业务明文载荷
 *
 * 封装待同步的 Cookie 凭据、UID 以及 SettingUiState 完整配置 JSON。
 *
 * @param cookie 115 账号 Cookie
 * @param uid 用户 ID
 * @param configJson SettingUiState 的完整序列化 JSON 字符串
 * @param ts 发送端生成载荷时的时间戳（毫秒），用于防重放与时效性校验
 */
data class SyncPayload(
    @SerializedName("cookie")
    val cookie: String? = null,

    @SerializedName("uid")
    val uid: String? = null,

    @SerializedName("configJson")
    val configJson: String? = null,

    @SerializedName("ts")
    val ts: Long = System.currentTimeMillis()
)

/**
 * 局域网同步协议解析结果
 *
 * 承载从 nap511://lan-sync?... 控制信道 URI 中解析出的连接元数据。
 *
 * @param endpointUrl 发送端 HTTP 监听服务的端点 URL
 * @param tokenHex 16 字节随机鉴权 Token（Hex 编码）
 * @param keyBytes 32 字节高熵随机 AES-256 密钥
 */
data class LanSyncUriInfo(
    val endpointUrl: String,
    val tokenHex: String,
    val keyBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as LanSyncUriInfo
        return endpointUrl == other.endpointUrl &&
                tokenHex == other.tokenHex &&
                keyBytes.contentEquals(other.keyBytes)
    }

    override fun hashCode(): Int {
        var result = endpointUrl.hashCode()
        result = 31 * result + tokenHex.hashCode()
        result = 31 * result + keyBytes.contentHashCode()
        return result
    }
}
