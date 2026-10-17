package github.zerorooot.nap511.util.network

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

/**
 * 局域网同步服务端会话控制器
 * 基于 OkHttp MockWebServer 提供高安全性单次消费与防探测自毁机制
 *
 * @param hostIp 服务监听绑定的本机局域网 IPv4
 * @param expectedToken 用于接口鉴权的 16 字节随机 Token (Hex)
 * @param keyBytes 用于 AES-GCM 加密的 32 字节对称密钥
 * @param payloadProvider 待加密返回的业务载荷 JSON 提供者（根据当前模式动态获取）
 * @param onCompleted 成功向客户端传输并正常关闭后的回调
 * @param onError 异常或探测超限导致熔断时的回调
 */
class LanSyncServerSession(
    private val hostIp: String,
    private val expectedToken: String,
    private val keyBytes: ByteArray,
    private val payloadProvider: () -> String,
    private val onCompleted: () -> Unit = {},
    private val onError: (String) -> Unit = {}
) {
    private val server = MockWebServer()
    private val failureCount = AtomicInteger(0)
    private val isStopped = AtomicBoolean(false)
    private val gson = Gson()
    private var timeoutJob: Job? = null

    /**
     * 启动微型 HTTP 服务，返回实际监听的动态端口号
     */
    fun start(scope: CoroutineScope): Int {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                val token = request.requestUrl?.queryParameter("t") ?: ""

                // 接口路径必须为 /sync，且使用常量时间安全比较校验 Token
                if (!path.startsWith("/sync") || !LanSyncCrypto.constantTimeEquals(token, expectedToken)) {
                    val failures = failureCount.incrementAndGet()
                    if (failures >= 3) {
                        scope.launch(Dispatchers.IO) { stop() }
                        onError("安全防护触发：非法探测次数超限，服务已即时熔断销毁")
                    }
                    return MockResponse().setResponseCode(403).setBody("Forbidden")
                }

                // 鉴权通过：获取业务数据并执行 AES-GCM 加密返回
                return try {
                    val plainJson = payloadProvider()
                    val encryptedResponse = LanSyncCrypto.encryptAesGcm(plainJson, keyBytes)
                    val responseJson = gson.toJson(encryptedResponse)

                    // 延时 200ms 保证客户端完整读取 HTTP 报文后立即自毁服务端
                    scope.launch(Dispatchers.IO) {
                        delay(200.milliseconds)
                        stop()
                        onCompleted()
                    }

                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json; charset=utf-8")
                        .setHeader("Connection", "close")
                        .setBody(responseJson)
                } catch (e: Exception) {
                    MockResponse().setResponseCode(500).setBody("Internal Server Error")
                }
            }
        }

        // 绑定指定网卡 IP，分配端口 0 由系统指定空闲随机端口
        server.start(InetAddress.getByName(hostIp), 0)

        // 启动 60 秒倒计时防挂起自毁机制
        timeoutJob = scope.launch(Dispatchers.IO) {
            delay(60_000.milliseconds)
            if (!isStopped.get()) {
                stop()
                onError("同步服务已达 60 秒时限，自动销毁")
            }
        }

        return server.port
    }

    /**
     * 停止并释放服务端套接字资源
     */
    fun stop() {
        if (isStopped.compareAndSet(false, true)) {
            timeoutJob?.cancel()
            try {
                server.shutdown()
            } catch (_: Exception) {}
        }
    }
}
