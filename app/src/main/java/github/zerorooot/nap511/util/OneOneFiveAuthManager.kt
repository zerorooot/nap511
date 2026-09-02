package github.zerorooot.nap511.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.StringJoiner
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import kotlin.coroutines.resume

/**
 * 验证码数据包
 */
data class CaptchaChallengeData(
    val sign: String,
    val targetImage: ImageBitmap,
    val candidateImages: List<ImageBitmap> // 0 到 9 共 10 个候选字符图片
)

/**
 * 登录结果状态定义
 */
sealed class LoginResult {
    data class Success(val userId: Long, val cookies: String) : LoginResult()
    data class NeedTwoFactor(val userId: Long, val maskedMobile: String, val message: String) :
        LoginResult()

    data class NeedCaptcha(val code: Int, val message: String) : LoginResult()
    data class Failure(val code: Int, val message: String) : LoginResult()
}

class OneOneFiveAuthManager {

    // 内存/持久化 CookieJar 维护同一会话状态
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val existing = cookieStore.getOrPut(url.host) { mutableListOf() }
            // 简单更新同名 Cookie
            for (c in cookies) {
                existing.removeAll { it.name == c.name }
                existing.add(c)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 构造完全对齐现代桌面 Chromium (Chrome 124) 的标准请求头
     */
    private fun applyBrowserHeaders(
        builder: Request.Builder,
        referer: String = "https://115.com/"
    ): Request.Builder {
        return builder
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header(
                "Sec-Ch-Ua",
                "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\""
            )
            .header("Sec-Ch-Ua-Mobile", "?0")
            .header("Sec-Ch-Ua-Platform", "\"Windows\"")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-site")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", "https://115.com")
            .header("Referer", referer)
    }


    /**
     * 更新后的 login 方法，支持传入图形验证码
     */
    suspend fun login(
        account: String,
        password: String,
        captchaCode: String? = null,
        captchaSign: String? = null
    ): LoginResult = withContext(Dispatchers.IO) {
        try {
            val rsaPublicKeyBase64 =
                fetchPublicKey() ?: return@withContext LoginResult.Failure(-1, "获取加密公钥失败")
            val timestamp = System.currentTimeMillis() / 1000
            val ssoext = UUID.randomUUID().toString().replace("-", "").take(16)
            val ssopw = calculateSsoPassword(password, account, ssoext)
            val encryptedPasswd = encryptRsa(sha1(password) + "_" + timestamp, rsaPublicKeyBase64)
                ?: return@withContext LoginResult.Failure(-1, "密码加密计算失败")
            val formBodyBuilder = FormBody.Builder()
                .add("login[ssoent]", "A1")
                .add("login[version]", "2.0")
                .add("login[ssoext]", ssoext)
                .add("login[ssoln]", account.trim())
                .add("login[pwd_level]", calculatePwdLevel(password).toString())
                .add("login[ssovcode]", ssoext)
                .add("login[ssopw]", ssopw)
                .add("login[safe]", "1")
                .add("login[time]", "0")
                .add("login[safe_login]", "0")
                .add("goto", "//115.com?cid=0&offset=0&mode=wangpan")
                .add("login[country]", "")
                .add("country", "")
                .add("from_browser", "1")
                .add("cipher_ver", "2")
                .add("account", account.trim())
                .add("passwd", encryptedPasswd)
                .add("time", timestamp.toString())

            // 附带图形验证码参数
            if (!captchaCode.isNullOrBlank() && !captchaSign.isNullOrBlank()) {
                formBodyBuilder.add("code", captchaCode)
                formBodyBuilder.add("code_id", captchaSign)
                formBodyBuilder.add("login[code]", captchaCode)
                formBodyBuilder.add("login[sid]", captchaSign)
            }
            val request = Request.Builder()
                .url("https://passportapi.115.com/app/1.0/web/1.0/login/login")
                .post(formBodyBuilder.build())
            applyBrowserHeaders(request)
            val response = executeRequest(request.build())
            val json = JSONObject(response)
            val isState = (json.optInt("state", -1) == 1)
            val code = json.optInt("code", json.optInt("errno", 0))
            val message = json.optString("message", json.optString("error", ""))
            if (code == 40101010 || code == 70128) {
                val dataObj = json.optJSONObject("data") ?: JSONObject()
                return@withContext LoginResult.NeedTwoFactor(
                    userId = dataObj.optLong("user_id", 0L),
                    maskedMobile = dataObj.optString("mobile", ""),
                    message = message
                )
            }
            if (code in listOf(10098, 40101004, 40103000, 90059)) {
                return@withContext LoginResult.NeedCaptcha(
                    code,
                    message.ifBlank { "请输入图形验证码" }
                )
            }
            if (isState) {
                val dataObj = json.optJSONObject("data") ?: JSONObject()
                return@withContext LoginResult.Success(
                    dataObj.optLong("user_id", 0L),
                    exportCookies()
                )
            } else {
                return@withContext LoginResult.Failure(
                    code,
                    message.ifBlank { "登录失败" }
                )
            }
        } catch (e: Exception) {
            return@withContext LoginResult.Failure(-1, e.message ?: "网络异常")
        }
    }

    private fun calculatePwdLevel(password: String): Int {
        if (password.length <= 5) return 0
        var mask = 0
        for (ch in password) {
            mask = mask or when (ch) {
                in '0'..'9' -> 1
                in 'A'..'Z' -> 2
                in 'a'..'z' -> 4
                else -> 8
            }
        }
        var categoryCount = 0
        var temp = mask
        repeat(4) {
            if ((temp and 1) != 0) categoryCount++
            temp = temp ushr 1
        }
        return categoryCount + if (password.length > 8) 1 else 0
    }

    /**
     * 拉取汉字点选验证码图片及 10 个候选按键图片
     */
    suspend fun fetchCaptchaChallenge(): CaptchaChallengeData? = withContext(Dispatchers.IO) {
        try {
            val t = System.currentTimeMillis()
            // 1. 获取 sign
            val signReq = Request.Builder().url("https://captchaapi.115.com/?ac=code&t=sign").get()
            applyBrowserHeaders(signReq)
            val signRes = JSONObject(executeRequest(signReq.build()))
            val sign = signRes.optString("sign", "")
            if (sign.isBlank()) return@withContext null
            // 2. 并行拉取目标大图与 10 个候选字符切片
            coroutineScope {
                val targetImgDeferred = async {
                    val req =
                        Request.Builder().url("https://captchaapi.115.com/?ct=index&ac=code&_t=$t")
                            .get()
                    applyBrowserHeaders(req)
                    fetchImage(req.build())
                }
                val candidateDeferreds = (0..9).map { index ->
                    async {
                        val req = Request.Builder()
                            .url("https://captchaapi.115.com/?ct=index&ac=code&t=single&id=$index&_t=$t")
                            .get()
                        applyBrowserHeaders(req)
                        fetchImage(req.build())
                    }
                }
                val targetBitmap = targetImgDeferred.await() ?: return@coroutineScope null
                val candidateBitmaps = candidateDeferreds.awaitAll().filterNotNull()
                if (candidateBitmaps.size == 10) {
                    CaptchaChallengeData(
                        sign = sign,
                        targetImage = targetBitmap.asImageBitmap(),
                        candidateImages = candidateBitmaps.map { it.asImageBitmap() }
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchImage(request: Request): Bitmap? =
        suspendCancellableCoroutine { cont ->
            val call = okHttpClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (it.isSuccessful) {
                            val bytes = it.body.bytes()
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (cont.isActive) cont.resume(bitmap)
                            return
                        }
                        if (cont.isActive) cont.resume(null)
                    }
                }
            })
        }

    // ==========================================
    // 2. 发送短信验证码
    // ==========================================

    suspend fun sendSms(userId: Long): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("user_id", userId.toString())
                .add("tpl", "verify_code")
                .add("cv21", "2")
                .build()

            val request = Request.Builder()
                .url("https://passportapi.115.com/app/1.0/web/1.0/code/sms/login")
                .post(formBody)

            applyBrowserHeaders(request)

            val response = executeRequest(request.build())
            val json = JSONObject(response)

            val state = (json.optInt("state", -1) == 1)
            val msg = json.optString("message", json.optString("error", ""))
            return@withContext if (state) {
                Pair(true, "短信验证码发送成功")
            } else {
                Pair(false, msg.ifBlank { "短信发送失败" })
            }
        } catch (e: Exception) {
            Pair(false, e.message ?: "网络异常")
        }
    }

    // ==========================================
    // 3. 提交二次验证码 (SMS 或 动态安全令)
    // ==========================================

    suspend fun submitTwoFactorCode(userId: Long, code: String): LoginResult =
        withContext(Dispatchers.IO) {
            try {
                val formBody = FormBody.Builder()
                    .add("account", userId.toString())
                    .add("code", code.trim())
                    .add("code_id", "0")
                    .add("sso_mode", "0")
                    .build()

                val request = Request.Builder()
                    .url("https://passportapi.115.com/app/1.0/web/1.0/login/vip")
                    .post(formBody)

                applyBrowserHeaders(request)

                val response = executeRequest(request.build())
                val json = JSONObject(response)

                if (json.optInt("state", -1) == 1) {
                    val data = json.optJSONObject("data") ?: JSONObject()
                    val uid = data.optLong("user_id", userId)
                    return@withContext LoginResult.Success(uid, exportCookies())
                } else {
                    val msg = json.optString("error", json.optString("message", "验证码错误"))
                    return@withContext LoginResult.Failure(json.optInt("code", -1), msg)
                }
            } catch (e: Exception) {
                LoginResult.Failure(-1, e.message ?: "请求失败")
            }
        }

    // ==========================================
    // 辅助加解密与网络工具
    // ==========================================

    private suspend fun fetchPublicKey(): String? {
        val request = Request.Builder()
            .url("https://passportapi.115.com/app/1.0/web/5.0.1/login/getKey")
            .get()
        applyBrowserHeaders(request)

        val res = executeRequest(request.build())
        val json = JSONObject(res)
        return if (json.optInt("state", -1) == 1) {
            json.optJSONObject("data")?.optString("key")
        } else null
    }

    private suspend fun executeRequest(request: Request): String =
        suspendCancellableCoroutine { continuation ->
            val call = okHttpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val bodyString = it.body?.string() ?: ""
                        if (continuation.isActive) continuation.resume(bodyString)
                    }
                }
            })
        }

    private fun sha1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun calculateSsoPassword(password: String, account: String, ssoext: String): String {
        val step1 = sha1(password)
        val step2 = sha1(account)
        val step3 = sha1(step1 + step2)
        return sha1(step3 + ssoext.uppercase())
    }

    private fun encryptRsa(plainText: String, rawServerKey: String): String? {
        return try {
            // 1. 第一次 Base64 解码：从服务器返回的字符串中解出 PEM 文本
            val pemString = String(Base64.decode(rawServerKey, Base64.DEFAULT), Charsets.UTF_8)

            // 2. 清理 PEM 头尾标识与换行符，提取真正的公钥 Base64 主体
            val cleanPublicKeyBase64 = pemString
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\r", "")
                .replace("\n", "")
                .trim()

            // 3. 第二次 Base64 解码：解出 ASN.1 DER 格式二进制数据
            val derBytes = Base64.decode(cleanPublicKeyBase64, Base64.DEFAULT)

            // 4. 生成 RSA 公钥对象 (4096 位 RSA)
            val spec = X509EncodedKeySpec(derBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey: PublicKey = keyFactory.generatePublic(spec)

            // 5. 执行 RSA 加密 (PKCS1Padding)
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // 6. 输出最终的 Base64 密文字符串 (与 JS 端的 JSEncrypt 输出一致)
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportCookies(): String {
        val stringJoiner = StringJoiner(";")
        cookieStore.values.flatten().forEach { cookie ->
            stringJoiner.add("${cookie.name}:${cookie.value}")
        }
        return stringJoiner.toString()
    }
}