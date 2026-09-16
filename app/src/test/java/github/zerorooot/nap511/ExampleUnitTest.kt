package github.zerorooot.nap511

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        val a = "123 - ni@hao"
        println(a.substringBeforeLast(".").takeIf { true }!!.substringAfterLast(" ").substringAfterLast("@"))
    }
}