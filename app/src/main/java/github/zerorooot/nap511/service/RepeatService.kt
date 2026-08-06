package github.zerorooot.nap511.service

import github.zerorooot.nap511.bean.BaseResponse
import github.zerorooot.nap511.bean.CategoryDetailResponse
import github.zerorooot.nap511.bean.RepeatListResponse
import github.zerorooot.nap511.bean.RepeatStatusResponse
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface RepeatService {
    companion object {
        private var repeatService: RepeatService? = null
        fun getInstance(cookie: String): RepeatService {
            if (repeatService == null) {
                repeatService = Retrofit
                    .Builder()
                    .baseUrl("https://aps.115.com/repeat/")
                    .addConverterFactory(GsonConverterFactory.create())
                    //add cookie
                    .client(OkHttpClient().newBuilder().addInterceptor(Interceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder().addHeader("Cookie", cookie).build()
                        );
                    }).build())
                    .build()
                    .create(RepeatService::class.java)
            }
            return repeatService!!
        }
    }

    //https://aps.115.com/repeat/repeat_status.php?_=123123
    @GET("repeat_status.php")
    suspend fun getRepeatStatus(
        @Query("_") timestamp: Long = System.currentTimeMillis()
    ): RepeatStatusResponse

    //https://aps.115.com/repeat/repeat_list.php?s=0&l=100&_=123123
    @GET("repeat_list.php")
    suspend fun getRepeatList(
        @Query("s") offset: Int,
        @Query("l") limit: Int = 100,
        @Query("_") timestamp: Long = System.currentTimeMillis()
    ): RepeatListResponse

    @POST("repeat.php")
    suspend fun forceRefresh(
//        @Field("folder_id") folderId: String = ""
        @Query("_") timestamp: Long = System.currentTimeMillis()
    ): BaseResponse


    @FormUrlEncoded
    @POST("repeat_delete.php")
    suspend fun deleteRepeatFiles(
        @Field("filter_field") filterField: String,
        @Field("filter_order") filterOrder: String,
        @Field("batch") batch: Int = 1
    ): BaseResponse

    @GET("https://webapi.115.com/category/get")
    suspend fun getCategoryDetail(
        @Query("cid") cid: String
    ): CategoryDetailResponse

    @GET("https://115.com/")
    suspend fun clearEmpty(
        @Query("ct") ct: String = "tool",
        @Query("ac") ac: String = "clear_empty_folder",
        @Query("_") timestamp: Long = System.currentTimeMillis()
    ): BaseResponse
}