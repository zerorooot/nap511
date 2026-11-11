package github.zerorooot.nap511.viewmodel.platform

/**
 * 测试替身：内存 AppStringProvider 实现，避免单元测试依赖 Android Context 资源
 */
class FakeAppStringProvider(
    override val defaultAppName: String = "nap511"
) : AppStringProvider {
    override fun getSearchTitle(keyword: String): String = "搜索 - $keyword"
    override fun getFilterTitle(name: String): String = "过滤 - $name"
    override fun getStarTitle(): String = "星标文件"
}
