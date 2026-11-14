package github.zerorooot.nap511.platform

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import github.zerorooot.nap511.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地化文案与标题提供者抽象
 * 负责提供与 Android Context / R.string 解耦的标题与格式化文案
 */
interface AppStringProvider {
    /** 默认应用标题（R.string.app_name） */
    val defaultAppName: String

    /** 格式化搜索模式 AppBar 标题 */
    fun getSearchTitle(keyword: String): String

    /** 格式化分类过滤模式 AppBar 标题 */
    fun getFilterTitle(name: String): String

    /** 格式化星标文件模式 AppBar 标题 */
    fun getStarTitle(): String
}

@Singleton
class AndroidAppStringProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : AppStringProvider {
    override val defaultAppName: String
        get() = context.getString(R.string.app_name)

    override fun getSearchTitle(keyword: String): String = "搜索 - $keyword"

    override fun getFilterTitle(name: String): String = "过滤 - $name"

    override fun getStarTitle(): String = "星标文件"
}
