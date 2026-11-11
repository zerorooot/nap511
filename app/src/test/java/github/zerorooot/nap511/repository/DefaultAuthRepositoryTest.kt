package github.zerorooot.nap511.repository

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.network.UserSessionManager
import github.zerorooot.nap511.viewmodel.platform.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultAuthRepositoryTest {

    private lateinit var fakeSettingsRepo: FakeSettingsRepository

    @Before
    fun setUp() {
        try {
            XLog.init()
        } catch (_: Throwable) {}
        fakeSettingsRepo = FakeSettingsRepository()
        UserSessionManager.clearSession()
    }

    @After
    fun tearDown() {
        UserSessionManager.clearSession()
    }

    private fun createMockOkHttpClient(responseHandler: (okhttp3.Request) -> Response): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                responseHandler(chain.request())
            }
            .build()
    }

    @Test
    fun testCheckLogin_success() = runTest {
        val successJson = """
            {
                "state": true,
                "data": {
                    "user_id": "999888",
                    "user_name": "Test115User",
                    "face": "https://example.com/avatar.png",
                    "expire": 1750000000
                }
            }
        """.trimIndent()

        val client = createMockOkHttpClient { request ->
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(successJson.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }

        val authRepo = DefaultAuthRepository(fakeSettingsRepo, client)
        val result = authRepo.checkLogin("test_cookie_123")

        assertTrue("checkLogin should succeed", result.isSuccess)
        val avatar = result.getOrNull()
        assertNotNull(avatar)
        assertEquals("999888", avatar?.userId)
        assertEquals("Test115User", avatar?.userName)
        assertNotNull(avatar?.expireString)

        // 校验 UserSession 纯内存更新
        assertEquals("test_cookie_123", UserSessionManager.cookie)
        assertEquals("999888", UserSessionManager.uid)

        // 校验 SettingsRepository 落盘保存
        assertEquals("test_cookie_123", fakeSettingsRepo.getDataSuspend(ConfigKeyUtil.COOKIE, ""))
        assertEquals("999888", fakeSettingsRepo.getDataSuspend(ConfigKeyUtil.UID, ""))
        assertTrue(fakeSettingsRepo.getDataSuspend(ConfigKeyUtil.AVATAR_BEAN, "").contains("999888"))
    }

    @Test
    fun testCheckLogin_httpError() = runTest {
        val client = createMockOkHttpClient { request ->
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Server Error")
                .body("Internal error".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val authRepo = DefaultAuthRepository(fakeSettingsRepo, client)
        val result = authRepo.checkLogin("bad_cookie")

        assertTrue("checkLogin should fail on HTTP 500", result.isFailure)
        assertEquals("", UserSessionManager.cookie)
        assertEquals("", UserSessionManager.uid)
    }

    @Test
    fun testCheckLogin_businessError() = runTest {
        val errorJson = """
            {
                "state": false,
                "error": "cookie expired"
            }
        """.trimIndent()

        val client = createMockOkHttpClient { request ->
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(errorJson.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }

        val authRepo = DefaultAuthRepository(fakeSettingsRepo, client)
        val result = authRepo.checkLogin("expired_cookie")

        assertTrue("checkLogin should fail when data is null", result.isFailure)
    }

    @Test
    fun testCheckAndSaveAria2_success() = runTest {
        val successJson = """
            {
                "jsonrpc": "2.0",
                "id": "nap511",
                "result": {
                    "version": "1.36.0"
                }
            }
        """.trimIndent()

        val client = createMockOkHttpClient { request ->
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(successJson.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }

        val authRepo = DefaultAuthRepository(fakeSettingsRepo, client)
        val result = authRepo.checkAndSaveAria2("http://127.0.0.1:6800/jsonrpc", "token123")

        assertTrue("checkAndSaveAria2 should succeed", result.isSuccess)
        assertEquals("http://127.0.0.1:6800/jsonrpc", fakeSettingsRepo.getDataSuspend(ConfigKeyUtil.ARIA2_URL, ""))
        assertEquals("token123", fakeSettingsRepo.getDataSuspend(ConfigKeyUtil.ARIA2_TOKEN, ""))
    }

    @Test
    fun testCheckAndSaveAria2_rpcError() = runTest {
        val rpcErrorJson = """
            {
                "jsonrpc": "2.0",
                "id": "nap511",
                "error": {
                    "code": 1,
                    "message": "Unauthorized"
                }
            }
        """.trimIndent()

        val client = createMockOkHttpClient { request ->
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(rpcErrorJson.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }

        val authRepo = DefaultAuthRepository(fakeSettingsRepo, client)
        val result = authRepo.checkAndSaveAria2("http://127.0.0.1:6800/jsonrpc", "wrong_token")

        assertTrue("checkAndSaveAria2 should fail on RPC error", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Unauthorized") == true)
        assertEquals("", fakeSettingsRepo.getDataSuspend(ConfigKeyUtil.ARIA2_URL, ""))
    }

    @Test
    fun testCheckAndSaveAria2_httpError() = runTest {
        val client = createMockOkHttpClient { request ->
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .body("Not Found".toResponseBody("text/plain".toMediaType()))
                .build()
        }

        val authRepo = DefaultAuthRepository(fakeSettingsRepo, client)
        val result = authRepo.checkAndSaveAria2("http://127.0.0.1:6800/jsonrpc", "token123")

        assertTrue("checkAndSaveAria2 should fail on HTTP 404", result.isFailure)
        assertEquals("", fakeSettingsRepo.getDataSuspend(ConfigKeyUtil.ARIA2_URL, ""))
    }
}
