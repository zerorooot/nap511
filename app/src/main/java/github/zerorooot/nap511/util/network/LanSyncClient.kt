package github.zerorooot.nap511.util.network

import com.google.gson.Gson
import github.zerorooot.nap511.bean.sync.EncryptedResponse
import github.zerorooot.nap511.bean.sync.LanSyncUriInfo
import github.zerorooot.nap511.bean.sync.SyncPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * 局域网同步客户端网络请求与解密拉取工具
 */
object LanSyncClient {
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    // 载荷时间戳最大允许偏差：5 分钟（防止过期旧报文重放）
    private const val MAX_TIME_DIFF_MILLIS = 5 * 60 * 1000L

    /**
     * 向发送端发起局域网同步请求并解密还原载荷
     * @param uriInfo 经扫码或剪贴板解析得到的控制信道元数据（地址、Token、密钥）
     */
    suspend fun fetchPayload(uriInfo: LanSyncUriInfo): Result<SyncPayload> = withContext(Dispatchers.IO) {
        try {
            val delimiter = if (uriInfo.endpointUrl.contains("?")) "&" else "?"
            val fullUrl = "${uriInfo.endpointUrl}${delimiter}t=${uriInfo.tokenHex}"

            val request = Request.Builder()
                .url(fullUrl)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("HTTP 请求失败: 状态码 ${response.code} (${response.message})")
                )
            }

            val responseBody = response.body.string()
            if (responseBody.isBlank()) {
                return@withContext Result.failure(Exception("返回响应内容为空"))
            }

            // 1. 反序列化密文结构
            val encryptedResponse = gson.fromJson(responseBody, EncryptedResponse::class.java)
                ?: return@withContext Result.failure(Exception("密文格式解析失败"))

            // 2. 使用提取出的 32 字节 AES 密钥解密
            val plainText = LanSyncCrypto.decryptAesGcm(encryptedResponse, uriInfo.keyBytes)

            // 3. 反序列化明文业务载荷
            val payload = gson.fromJson(plainText, SyncPayload::class.java)
                ?: return@withContext Result.failure(Exception("业务载荷解析失败"))

            // 4. 防重放校验：强制检验时间戳在 5 分钟有效窗口内
            val now = System.currentTimeMillis()
            if (abs(now - payload.ts) > MAX_TIME_DIFF_MILLIS) {
                return@withContext Result.failure(Exception("载荷已超过 5 分钟有效时限，请重新发起同步"))
            }

            Result.success(payload)
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("连接发送端超时，请排查局域网连通性或是否开启了路由器 AP 隔离"))
        } catch (e: ConnectException) {
            Result.failure(Exception("连接被拒绝，发送端可能已退出或不在同一局域网"))
        } catch (e: Exception) {
            Result.failure(Exception("数据获取或解密失败: ${e.localizedMessage ?: e.message}"))
        }
    }
}
