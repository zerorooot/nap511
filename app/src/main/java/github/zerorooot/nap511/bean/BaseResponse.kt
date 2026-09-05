package github.zerorooot.nap511.bean

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class Base115Response<T>(
    val state: Boolean = false,
    val data: T? = null
)

// 统一定义接口返回的包装结构
open class BaseResponse(
    @SerializedName("state") val state: Boolean = false,
    @SerializedName("msg", alternate = ["message"]) val message: String = "",
    @SerializedName("msg_code") val msgCode: Int = 0
) {
    override fun toString(): String {
        return Gson().toJson(this)
    }
}

data class BaseReturnMessage(
    @SerializedName("state") var state: Boolean = false,
    @SerializedName("error") var error: String = "",
    @SerializedName("errno") var errno: String = "",
    var message: String = "",
    @SerializedName("error_msg") var errorMsg: String = ""
)
