package github.zerorooot.nap511.bean.cache

import com.google.gson.annotations.SerializedName
import github.zerorooot.nap511.bean.FilesBean

/**
 * 文件目录磁盘与内存缓存封装对象
 *
 * 显式添加 @SerializedName 注解以确保落盘到本地 JSON 缓存文件时，字段名不受 R8 代码混淆影响，
 * 保障应用版本迭代与构建类型切换时的持久化兼容性。
 *
 * @param data 目录下的文件集合实体
 * @param timestamp 缓存创建或更新时的毫秒时间戳（用于 TTL 有效期检查）
 */
data class CacheWrapper(
    @SerializedName("data")
    val data: FilesBean,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
