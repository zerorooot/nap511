package github.zerorooot.nap511.service

import com.google.gson.JsonObject
import github.zerorooot.nap511.bean.BaseResponse
import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.CreateFolderMessage
import github.zerorooot.nap511.bean.EncryptionData
import github.zerorooot.nap511.bean.ExtractData
import github.zerorooot.nap511.bean.FileInfo
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.ImageDate
import github.zerorooot.nap511.bean.ProcessData
import github.zerorooot.nap511.bean.RecycleInfo
import github.zerorooot.nap511.bean.VideoInfoBean
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface FileService {
    companion object {
        @Volatile
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
                                try {
                                    val request = chain.request().newBuilder()
                                        .addHeader("Cookie", cookie)
                                        .addHeader(
                                            "User-Agent",
                                            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.61 Safari/537.36 115Browser/23.9.3.6"
                                        )
                                        .build()
                                    val response = chain.proceed(request)
                                    val code = response.code
                                    if (code >= 400) {
                                        response.close() // 关闭原始响应体，防止泄漏
                                        // 手动抛出异常，强制进入下方的 catch 块
                                        // 这样会复用你原本处理 SocketTimeoutException 的逻辑
                                        throw java.io.IOException("HTTP $code error")
                                    }
                                    return@Interceptor response
                                } catch (e: Exception) {
                                    //防止java.net.SocketTimeoutException: timeout 错误
                                    Response.Builder()
                                        .request(chain.request())
                                        .protocol(Protocol.HTTP_1_1)
                                        .code(500)
                                        .message(e.message ?: "Network error")
                                        .body("".toResponseBody("text/plain".toMediaTypeOrNull()))
                                        .build()
                                }
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
        @Query("show_dir") showDir: Int,
        @Query("aid") aid: Int,
        @Query("asc") asc: Int,
        @Query("o") order: String,
        @Query("limit") limit: Int
    ): FilesBean

    @GET("category/get")
    suspend fun getFileInfo(@Query("cid") cid: String): FileInfo

    @GET("files/image")
    suspend fun image(@Query("pickcode") pickCode: String, @Query("_") current: Long): ImageDate

    @GET("files/video")
    suspend fun video(@Query("pickcode") pickCode: String): VideoInfoBean

    @FormUrlEncoded
    @POST("files/history")
    /**
    builder.add("op", "update")
    builder.add("pick_code", intent.getStringExtra("pick_code")!!)
    builder.add("time", "0")
    builder.add("category", "1")
    builder.add("format", "json")
     */
    suspend fun videoHistory(@FieldMap body: HashMap<String, String>): BaseReturnMessage

    @GET("files/search")
    suspend fun search(
        @Query("cid") cid: String,
        @Query("search_value") searchValue: String,
        @Query("aid") aid: Int,
        @Query("offset") asc: Int,
        @Query("limit") limit: Int
    ): FilesBean


    /**
     *
    user_order:file_size
    file_id:2573609193685653011
    user_asc:1
    fc_mix:0
     */
    @POST("files/order")
    @FormUrlEncoded
    suspend fun order(@FieldMap body: Map<String, String>): BaseReturnMessage

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


    @GET("rb")
    suspend fun recycleList(
        @Query("aid") aid: String = "7",
        @Query("cid") cid: String = "0",
        @Query("offset") offset: String = "0",
        @Query("limit") limit: String = "100"
    ): RecycleInfo

    @FormUrlEncoded
    @POST("rb/clean")
    suspend fun recycleClean(
        @Field("rid[0]") rid: String,
        @Field("password") password: String
    ): BaseReturnMessage

    @FormUrlEncoded
    @POST("rb/clean")
    suspend fun recycleCleanAll(
        @Field("password") password: String
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

    @FormUrlEncoded
    @POST("offine/downpath")
    suspend fun setDownloadPath(@Field("file_id") cid: String): BaseReturnMessage


    /**
     * 获取剩余空间
     */
    @GET("files/index_info")
    suspend fun remainingSpace(@Query("count_space_nums") countSpaceNum: Int): JsonObject

    @GET("files/extract_info")
    suspend fun getZipListFile(
        @Query("pick_code") pickCode: String,
        @Query("file_name") fileName: String = "",
        @Query("paths") paths: String = "文件",
        @Query("page_count") pageCount: String = "999",
    ): JsonObject


    /**
     * {"state":true,"message":"","code":"","data":{"unzip_status":4}}
     */
    @FormUrlEncoded
    @POST("files/push_extract")
    suspend fun decryptZip(
        @Field("pick_code") pickCode: String,
        @Field("secret") secret: String
    ): BaseResponse<EncryptionData>

    /**
     *非加密文件 {"state":true,"message":"","code":"","data":{"unzip_status":1}}
     *
     *加密文件 {"state":true,"message":"","code":"","data":{"unzip_status":6}}
     */
    @FormUrlEncoded
    @POST("files/push_extract")
    suspend fun checkEncryptionStatus(
        @Field("pick_code") pickCode: String
    ): BaseResponse<EncryptionData>

    /**
     * {"state":true,"message":"","code":"","data":{"extract_status":{"unzip_status":4,"progress":100}}}
     */
    @GET("files/push_extract")
    suspend fun getDecryptZipProcess(
        @Query("pick_code") pickCode: String
    ): BaseResponse<ExtractData>


    /**
     * map extract_file[] -> xxxx
     * extract_file[] -> xxxx
     * extract_dir[] -> xxx
     * extract_dir[] -> xxx
     * return {"state":true,"message":"","code":"","data":{"extract_id":1231231}}
     */
    @FormUrlEncoded
    @POST("files/add_extract_file")
    suspend fun unzipFile(
        @Field("pick_code") pickCode: String,
        @Field("to_pid") pid: String,
        @Field("extract_file[]") files: List<String>?,
        @Field("extract_dir[]") dirs: List<String>?,
        @Field("paths") paths: String = "文件"
    ): BaseResponse<ProcessData>

    /**
     * {"state":true,"message":"","code":"","data":{"extract_id":"id","to_pid":"pid","percent":100}}
     */
    @GET("files/add_extract_file")
    suspend fun unzipFileProcess(
        @Query("extract_id") extractId: Long,
    ): BaseResponse<ProcessData>
}

