package github.zerorooot.nap511.data.network.client

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClient {
    /**
     * 全局共用的 OkHttpClient 基础单例。
     * 共享连接池 (ConnectionPool)、线程池 (Dispatcher) 和 TLS 状态，避免重复创建资源开销。
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
     * Coil 图片/缩略图加载专属 OkHttpClient 单例。
     * 拥有独立的 Dispatcher（maxRequestsPerHost = 20）与独立的 ConnectionPool，
     * 与业务 API 通道彻底物理隔离，避免高并发图片缩略图请求阻塞目录核心 API 的通信通道。
     */
    val imageOkHttpClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 20
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(10, 2, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(OneOneFiveImageExpirationInterceptor())
            .build()
    }
}

