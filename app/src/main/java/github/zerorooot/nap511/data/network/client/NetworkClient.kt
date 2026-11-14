package github.zerorooot.nap511.data.network.client

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClient {
    /**
     * 全局共用的 OkHttpClient 基础单例。
     * 用于核心业务 API 与数据仓库交互，共享连接池 (ConnectionPool)、线程池 (Dispatcher) 和 TLS 状态。
     */
    val sharedOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Coil 图片加载专属 OkHttpClient 单例。
     * 配备完全独立的 Dispatcher (maxRequestsPerHost = 20) 与独立 ConnectionPool，
     * 并针对图片加载特性将超时缩短为 15 秒（快速失败防卡死），
     * 彻底隔绝图片缩略图请求与目录核心 API 的通信通道，杜绝连接池与并发等待争用。
     */
    val coilOkHttpClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 20
        }
        val connectionPool = ConnectionPool(20, 2, TimeUnit.MINUTES)

        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(OneOneFiveImageExpirationInterceptor())
            .build()
    }
}

