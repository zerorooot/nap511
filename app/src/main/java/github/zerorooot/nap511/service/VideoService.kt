package github.zerorooot.nap511.service

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.VideoInfoBean
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface VideoService {
    companion object {
        private var videoService: VideoService? = null
        fun getInstance(cookie: String): VideoService {
            if (videoService == null) {
                videoService = Retrofit
                    .Builder()
                    .baseUrl("https://v.anxia.com/webapi/files/")
                    .addConverterFactory(GsonConverterFactory.create())
                    //add cookie
                    .client(OkHttpClient().newBuilder().addInterceptor(Interceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder().addHeader("Cookie", cookie).build()
                        );
                    }).build())
                    .build()
                    .create(VideoService::class.java)
            }
            return videoService!!
        }
    }

    @FormUrlEncoded
    @POST("history")
    /**
    builder.add("op", "update")
    builder.add("pick_code", intent.getStringExtra("pick_code")!!)
    builder.add("definition", "0")
    builder.add("category", "1")
    builder.add("share_id", "0")
     */
//    suspend fun history(@FieldMap body: HashMap<String, String>): BaseReturnMessage


    @GET("video")
    suspend fun videoInfo(
        @Query("pickcode") pickCode: String,
        @Query("share_id") shareId: String = "0"
    ): VideoInfoBean


}