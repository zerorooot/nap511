package github.zerorooot.nap511.di

import com.elvishew.xlog.XLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.zerorooot.nap511.data.network.api.FileApi
import github.zerorooot.nap511.data.network.api.OfflineApi
import github.zerorooot.nap511.data.network.api.RepeatApi
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.data.network.client.NetworkClient
import github.zerorooot.nap511.data.network.client.UserSessionManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/**
 * 网络服务依赖注入模块
 *
 * 职责：
 * 1. 维护受限作用域的 [serviceOkHttpClient] 单例，统管 115 业务专属 Cookie 与 User-Agent 请求头注入；
 * 2. 避免将业务客户端作为全局未限定单例直接暴露给外部容器，消除对 Coil 等通用网络客户端的副作用；
 * 3. 统一装配并向外部注入针对不同 BaseUrl 初始化的 Retrofit API 接口（FileService / RepeatService / OfflineService）。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * 业务私有单例客户端，确保 3 个 Retrofit Service 共享底层连接池与线程调度器
     */
    private val serviceOkHttpClient: OkHttpClient by lazy {
        XLog.i("NetworkModule: Initializing shared serviceOkHttpClient for Retrofit services")
        NetworkClient.sharedOkHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Cookie", UserSessionManager.cookie)
                    .addHeader("User-Agent", ConfigKeyUtil.USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private fun <T> createRetrofitService(baseUrl: String, serviceClass: Class<T>): T {
        XLog.d("NetworkModule: Creating Retrofit service for $serviceClass at $baseUrl")
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(serviceOkHttpClient)
            .build()
            .create(serviceClass)
    }

    @Provides
    @Singleton
    fun provideFileApi(): FileApi {
        return createRetrofitService("https://webapi.115.com/", FileApi::class.java)
    }

    @Provides
    @Singleton
    fun provideRepeatApi(): RepeatApi {
        return createRetrofitService("https://aps.115.com/repeat/", RepeatApi::class.java)
    }

    @Provides
    @Singleton
    fun provideOfflineApi(): OfflineApi {
        return createRetrofitService("https://115.com/", OfflineApi::class.java)
    }
}
