package github.zerorooot.nap511.data.repository

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.data.model.BaseResponse
import github.zerorooot.nap511.data.model.CategoryDetailResponse
import github.zerorooot.nap511.data.model.RepeatListResponse
import github.zerorooot.nap511.data.model.RepeatStatusResponse
import github.zerorooot.nap511.data.network.api.RepeatApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 重复文件清理与查重数据仓库契约
 */
interface RepeatRepository {
    suspend fun getRepeatStatus(timestamp: Long = System.currentTimeMillis()): RepeatStatusResponse
    suspend fun getRepeatList(offset: Int, limit: Int = 50, timestamp: Long = System.currentTimeMillis()): RepeatListResponse
    suspend fun forceRefresh(timestamp: Long = System.currentTimeMillis()): BaseResponse
    suspend fun deleteRepeatFiles(filterField: String, filterOrder: String, batch: Int = 1): BaseResponse
    suspend fun getCategoryDetail(cid: String): CategoryDetailResponse
    suspend fun clearEmpty(ct: String = "tool", ac: String = "clear_empty_folder", timestamp: Long = System.currentTimeMillis()): BaseResponse
}

/**
 * 重复文件仓库默认实现
 */
@Singleton
class DefaultRepeatRepository @Inject constructor(
    private val repeatApi: RepeatApi
) : RepeatRepository {

    override suspend fun getRepeatStatus(timestamp: Long): RepeatStatusResponse {
        XLog.d("RepeatRepository: getRepeatStatus timestamp=$timestamp")
        return repeatApi.getRepeatStatus(timestamp)
    }

    override suspend fun getRepeatList(offset: Int, limit: Int, timestamp: Long): RepeatListResponse {
        XLog.d("RepeatRepository: getRepeatList offset=$offset, limit=$limit")
        return repeatApi.getRepeatList(offset, limit, timestamp)
    }

    override suspend fun forceRefresh(timestamp: Long): BaseResponse {
        XLog.i("RepeatRepository: forceRefresh")
        return repeatApi.forceRefresh(timestamp)
    }

    override suspend fun deleteRepeatFiles(filterField: String, filterOrder: String, batch: Int): BaseResponse {
        XLog.i("RepeatRepository: deleteRepeatFiles field=$filterField, order=$filterOrder, batch=$batch")
        return repeatApi.deleteRepeatFiles(filterField, filterOrder, batch)
    }

    override suspend fun getCategoryDetail(cid: String): CategoryDetailResponse {
        XLog.d("RepeatRepository: getCategoryDetail cid=$cid")
        return repeatApi.getCategoryDetail(cid)
    }

    override suspend fun clearEmpty(ct: String, ac: String, timestamp: Long): BaseResponse {
        XLog.i("RepeatRepository: clearEmpty ct=$ct, ac=$ac")
        return repeatApi.clearEmpty(ct, ac, timestamp)
    }
}
