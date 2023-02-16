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
    suspend fun addTask(@FieldMap body: HashMap<String, String>)


}