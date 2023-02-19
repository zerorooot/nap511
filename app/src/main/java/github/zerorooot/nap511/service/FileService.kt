package github.zerorooot.nap511.service

import github.zerorooot.nap511.bean.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface FileService {
    companion object {
        private var fileService: FileService? = null
        fun getInstance(cookie: String): FileService {
            if (fileService == null) {
                fileService = Retrofit
                    .Builder()
                    .baseUrl("https://webapi.115.com/")
                    .addConverterFactory(GsonConverterFactory.create())
//                    .addConverterFactory(FileBeanConverterFactory.create())
                    //add cookie
                    .client(
                        OkHttpClient().newBuilder()
                            .addInterceptor(Interceptor { chain ->
                                chain.proceed(
                                    chain.request().newBuilder()
                                        .addHeader("Cookie", cookie)
                                        .addHeader(
                                            "User-Agent",
                                            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
                                        )
                                        .build()
                                );
                            })
                            .build()
                    )
                    .build()
                    .create(FileService::class.java)
            }
            return fileService!!
        }
    }

//    @GET("files")
//    suspend fun getFileList(
//        @Query("cid") cid: String,
//        @Query("show_dir") showDir: Int = 1,
//        @Query("aid") aid: Int = 1,
//        @Query("limit") limit: Int = 40
//    ): ArrayList<FileBean>

    @GET("files")
    suspend fun getFiles(
        @Query("cid") cid: String,
        @Query("show_dir") showDir: Int = 1,
        @Query("aid") aid: Int = 1,
        @Query("o") order: String = OrderBean.name,
        @Query("limit") limit: Int = 400
    ): FilesBean

    @GET("category/get")
    suspend fun getFileInfo(@Query("cid") cid: String): FileInfo

    /**
     *
    pid:currentCid
    move_proid:xxxx
    fid[0]:xxx
    fid[1]:xxxx
     */
    @FormUrlEncoded
    @POST("files/move")
    suspend fun move(@FieldMap body: Map<String, String>): BaseReturnMessage

    @FormUrlEncoded
    @POST("rb/delete")
    suspend fun delete(
        @Field("pid") pid: String,
        @Field("fid[0]") fid: String,
        @Field("ignore_warn") ignoreWarn: Int = 1
    ): BaseReturnMessage

    @FormUrlEncoded
    @POST("rb/delete")
    suspend fun deleteMultiple(
        @FieldMap data: Map<String, String>
    ): BaseReturnMessage

    @FormUrlEncoded
    @POST("rb/revert")
    suspend fun revert(
        @Field("rid[0]") rid: String
    ): BaseReturnMessage


    /**
     * cid 当前目录的cid
     */
    @FormUrlEncoded
    @POST("files/add")
    suspend fun createFolder(
        @Field("pid") pid: String,
        @Field("cname") folderName: String
    ): CreateFolderMessage

    /**
     * files_new_name[fid]=newName
     */
    @POST("files/batch_rename")
    suspend fun rename(@Body renameBean: RequestBody): BaseReturnMessage


}

