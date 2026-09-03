package github.zerorooot.nap511.util

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
}
