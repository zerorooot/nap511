package github.zerorooot.nap511.data.network.api

import github.zerorooot.nap511.data.model.BaseResponse
import github.zerorooot.nap511.data.model.CategoryDetailResponse
import github.zerorooot.nap511.data.model.RepeatListResponse
import github.zerorooot.nap511.data.model.RepeatStatusResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface RepeatApi {

    //https://aps.115.com/repeat/repeat_status.php?_=123123
    //{"state":false,"msg":"","msg_code":0,"data":{"group_count":0,"file_count":0,"file_size":0}}
//{"state":true,"msg":"","msg_code":0,"data":{"group_count":"766","file_count":"1632","file_size":"216379446654"}}
    @GET("repeat_status.php")
    suspend fun getRepeatStatus(
        @Query("_") timestamp: Long = System.currentTimeMillis()
    ): RepeatStatusResponse

    //https://aps.115.com/repeat/repeat_list.php?s=0&l=100&_=123123
    //{"state":true,"msg":"","msg_code":0,"data":[{"sha1":"sha1","file_id":"x","file_name":"x.iso","ico":"x","file_size":"x","user_utime":"x","user_utime_str":"1999-09-01 00:00:00","parent_id":"x","path":"x"}],"s":0,"l":"100","count":"xx"}
    @GET("repeat_list.php")
    suspend fun getRepeatList(
        @Query("s") offset: Int,
        @Query("l") limit: Int = 50,
        @Query("_") timestamp: Long = System.currentTimeMillis()
    ): RepeatListResponse

    @POST("repeat.php")
    suspend fun forceRefresh(
//        @Field("folder_id") folderId: String = ""
        @Query("_") timestamp: Long = System.currentTimeMillis()
    ): BaseResponse


    //https://aps.115.com/repeat/repeat_delete.php
    //{"state":true,"msg":"","msg_code":0}
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
