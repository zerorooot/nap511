package github.zerorooot.nap511.viewmodel.platform

import github.zerorooot.nap511.bean.AvatarBean
import github.zerorooot.nap511.repository.AuthRepository

/**
 * 单元测试专用的 FakeAuthRepository 实现
 * 纯内存模拟，支持灵活配置登录和 Aria2 成功/失败结果。
 */
class FakeAuthRepository(
    var checkLoginResult: Result<AvatarBean> = Result.success(AvatarBean(userId = "123456", userName = "TestUser")),
    var checkAndSaveAria2Result: Result<String> = Result.success("aria2配置成功，请重新下载文件")
) : AuthRepository {

    var lastCheckedCookie: String? = null
    var lastAria2Url: String? = null
    var lastAria2Token: String? = null

    override suspend fun checkLogin(cookie: String): Result<AvatarBean> {
        lastCheckedCookie = cookie
        return checkLoginResult
    }

    override suspend fun checkAndSaveAria2(aria2Url: String, aria2Token: String): Result<String> {
        lastAria2Url = aria2Url
        lastAria2Token = aria2Token
        return checkAndSaveAria2Result
    }
}
