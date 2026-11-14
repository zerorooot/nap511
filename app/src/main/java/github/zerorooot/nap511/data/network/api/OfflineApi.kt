package github.zerorooot.nap511.data.network.api

import github.zerorooot.nap511.data.model.BaseReturnMessage
import github.zerorooot.nap511.data.model.OfflineInfo
import github.zerorooot.nap511.data.model.OfflineListCount
import github.zerorooot.nap511.data.model.QuotaBean
import github.zerorooot.nap511.data.model.SignBean
import github.zerorooot.nap511.data.model.TorrentFileBean
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface OfflineApi {

    @FormUrlEncoded
    @POST("web/lixian/?ct=lixian&ac=add_task_urls")
    suspend fun addTask(@FieldMap body: HashMap<String, String>): BaseReturnMessage

    @GET("web/lixian/?ct=lixian&ac=get_quota_package_info")
    suspend fun quota(): QuotaBean

    @GET("?ct=offline&ac=space")
    suspend fun getSign(@Query("_") currentTime: Long = System.currentTimeMillis() / 1000): SignBean


    @POST("web/lixian/?ct=lixian&ac=task_count")
    suspend fun taskCount(): OfflineListCount

    /**
     *
     */
    @FormUrlEncoded
    @POST("web/lixian/?ct=lixian&ac=task_lists")
    suspend fun taskList(
        @Field("uid") uid: String,
        @Field("sign") sign: String,
        @Field("page") page: Int,
        //9是下载失败，11是下载完成，12是正在下载
        @Field("stat") stat: Int,
        @Field("time") time: Long = System.currentTimeMillis() / 1000
    ): OfflineInfo

    @FormUrlEncoded
    @POST("web/lixian/?ct=lixian&ac=torrent")
    suspend fun getTorrentTaskList(
        @Field("sha1") sha1: String = "",
        @Field("sign") sign: String = "",
        @Field("uid") uid: String = "",
        @Field("time") time: Long = System.currentTimeMillis() / 1000
    ): TorrentFileBean

    @FormUrlEncoded
    @POST("web/lixian/?ct=lixian&ac=add_task_bt")
    suspend fun addTorrentTask(
        @Field("info_hash") infoHash: String = "",
        //0,4,6
        @Field("wanted") wanted: String = "",
        //torrent name
        @Field("savepath") savePath: String = "",
        @Field("uid") uid: String = "",
        @Field("sign") sign: String = "",
        @Field("time") time: Long = System.currentTimeMillis() / 1000
    ): BaseReturnMessage


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
    suspend fun clearFinish(@Field("flag") flag: String = "0"): BaseReturnMessage

    @FormUrlEncoded
    @POST("web/lixian/?ct=lixian&ac=task_clear")
    suspend fun clearError(@Field("flag") flag: String = "2"): BaseReturnMessage
}
