package github.zerorooot.nap511.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.service.Sha1Service
import github.zerorooot.nap511.util.Sha1Util
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class Sha1ViewModel(private val cookie: String) : ViewModel() {
    private val sha1Util by lazy { Sha1Util() }
//    private val sha1Service by lazy { Sha1Service.getInstance() }
    fun getSha1(fileBean: FileBean) {
        val json = "{\"pickcode\":\"${fileBean.pickCode}\"}"
        val tm = System.currentTimeMillis() / 1000
        val m115Encode = sha1Util.m115_encode(json, tm)
        val body = mapOf("data" to m115Encode.data)
//        viewModelScope.launch {
//
////            val returnJson = sha1Service.getDownloadUrl(tm, body, cookie)
////            val downloadUrl =
////                returnJson.getAsJsonObject(fileBean.cid).getAsJsonObject("url").get("url").asString
////            println(downloadUrl)
//        }
    }
}