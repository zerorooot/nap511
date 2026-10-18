package github.zerorooot.nap511.util

import com.google.gson.Gson
import github.zerorooot.nap511.bean.SettingUiState
import github.zerorooot.nap511.util.network.LanSyncCrypto
import github.zerorooot.nap511.bean.sync.LanSyncType
import github.zerorooot.nap511.bean.sync.SyncPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanSyncCryptoTest {
    private val gson = Gson()

    @Test
    fun testKeyAndTokenGeneration() {
        val key = LanSyncCrypto.generateRandomKey()
        assertEquals(32, key.size)

        val tokenHex = LanSyncCrypto.generateTokenHex()
        assertEquals(32, tokenHex.length)
    }

    @Test
    fun testAesGcmEncryptionDecryptionRoundtrip() {
        val key = LanSyncCrypto.generateRandomKey()
        val originalText = """{"cookie":"test_cookie_123","uid":"999999","configJson":"{}","ts":1700000000}"""

        val encrypted = LanSyncCrypto.encryptAesGcm(originalText, key)
        assertNotNull(encrypted.iv)
        assertNotNull(encrypted.cipher)

        val decrypted = LanSyncCrypto.decryptAesGcm(encrypted, key)
        assertEquals(originalText, decrypted)
    }

    @Test
    fun testConstantTimeEquals() {
        assertTrue(LanSyncCrypto.constantTimeEquals("abcdef123456", "abcdef123456"))
        assertFalse(LanSyncCrypto.constantTimeEquals("abcdef123456", "abcdef123457"))
        assertFalse(LanSyncCrypto.constantTimeEquals("short", "longer_string"))
    }

    @Test
    fun testBuildPayloadJson_AccountCredential() {
        val state = SettingUiState(
            cookie = "my_cookie_value",
            uid = "12345",
            themeMode = "深色模式"
        )
        val json = LanSyncCrypto.buildPayloadJson(LanSyncType.ACCOUNT_CREDENTIAL, state, gson)
        val payload = gson.fromJson(json, SyncPayload::class.java)

        assertEquals("my_cookie_value", payload.cookie)
        assertEquals("12345", payload.uid)
        assertNull(payload.configJson)
    }

    @Test
    fun testBuildPayloadJson_AppConfig() {
        val state = SettingUiState(
            cookie = "my_cookie_value",
            uid = "12345",
            themeMode = "浅色模式"
        )
        val json = LanSyncCrypto.buildPayloadJson(LanSyncType.APP_CONFIG, state, gson)
        val payload = gson.fromJson(json, SyncPayload::class.java)

        assertEquals("my_cookie_value", payload.cookie)
        assertEquals("12345", payload.uid)
        assertNotNull(payload.configJson)

        val parsedState = gson.fromJson(payload.configJson, SettingUiState::class.java)
        assertEquals("my_cookie_value", parsedState.cookie)
        assertEquals("12345", parsedState.uid)
        assertEquals("浅色模式", parsedState.themeMode)
    }
}
