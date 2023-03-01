package github.zerorooot.nap511.service

import github.zerorooot.nap511.bean.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface OfflineService {
    companion object {
        private var offlineService: OfflineService? = null
        fun getInstance(cookie: String): OfflineService {
            if (offlineService == null) {
                offlineService = Retrofit
                    .Builder()
                    .baseUrl("https://115.com")
                    .addConverterFactory(GsonConverterFactory.create())
//                    .addConverterFactory(FileBeanConverterFactory.create())
                    //add cookie
                    .client(OkHttpClient().newBuilder().addInterceptor(Interceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder().addHeader("Cookie", cookie).build()
                        );
                    }).build())
                    .build()
                    .create(OfflineService::class.java)
            }
            return offlineService!!
        }
    }

    @FormUrlEncoded
    @POST("web/lixian/?ct=lixian&ac=add_task_urls")
    suspend fun addTask(@FieldMap body: HashMap<String, String>): BaseReturnMessage

    @GET("web/lixian/?ct=lixian&ac=get_quota_package_info")
    suspend fun quota(): QuotaBean

    @GET("?ct=offline&ac=space")
    suspend fun getSign(@Query("_") currentTime: Long = System.currentTimeMillis() / 1000): SignBean

    /**
     *
     */
    @FormUrlEncoded
    @POST("web/lixian/?ct=lixian&ac=task_lists")
    suspend fun taskList(
        @Field("uid") uid: String = "",
        @Field("sign") sign: String = "",
        @Field("page") page: Int = 1,
        @Field("time") time: Long = System.currentTimeMillis() / 1000
    ): OfflineInfo

    /**
     * hash[0]:xxxxxxx
     * uid
     * sign
     * time
     */
    @FormUrlEncoded
    @POST("web/lixian/?ct=lixian&ac=task_del")
    suspend fun deleteTask(
        @FieldMap deleteHash: HashMap<String, String>,
    ): BaseReturnMessage

    @FormUrlEncoded
    @POST("web/lixian/?ct=lixian&ac=task_clear")
    suspend fun clearFinish(@Field("flag") flag: String = "0"):BaseReturnMessage
    @FormUrlEncoded
    @POST("web/lixian/?ct=lixian&ac=task_clear")
    suspend fun clearError(@Field("flag") flag: String = "2"):BaseReturnMessage
}