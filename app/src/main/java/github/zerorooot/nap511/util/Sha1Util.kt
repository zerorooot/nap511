package github.zerorooot.nap511.util

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import kotlin.math.floor
import kotlin.math.min


class Sha1Util {
    data class KeyBean(val data: String, val key: IntArray)


    private val g_kts = intArrayOf(

        240,
        229,
        105,
        174,
        191,
        220,
        191,
        138,
        26,
        69,
        232,
        190,
        125,
        166,
        115,
        184,
        222,
        143,
        231,
        196,
        69,
        218,
        134,
        196,
        155,
        100,
        139,
        20,
        106,
        180,
        241,
        170,
        56,
        1,
        53,
        158,
        38,
        105,
        44,
        134,
        0,
        107,
        79,
        165,
        54,
        52,
        98,
        166,
        42,
        150,
        104,
        24,
        242,
        74,
        253,
        189,
        107,
        151,
        143,
        77,
        143,
        137,
        19,
        183,
        108,
        142,
        147,
        237,
        14,
        13,
        72,
        62,
        215,
        47,
        136,
        216,
        254,
        254,
        126,
        134,
        80,
        149,
        79,
        209,
        235,
        131,
        38,
        52,
        219,
        102,
        123,
        156,
        126,
        157,
        122,
        129,
        50,
        234,
        182,
        51,
        222,
        58,
        169,
        89,
        52,
        102,
        59,
        170,
        186,
        129,
        96,
        72,
        185,
        213,
        129,
        156,
        248,
        108,
        132,
        119,
        255,
        84,
        120,
        38,
        95,
        190,
        232,
        30,
        54,
        159,
        52,
        128,
        92,
        69,
        44,
        155,
        118,
        213,
        27,
        143,
        204,
        195,
        184,
        245
    )

    private val g_key_s = intArrayOf(0x29, 0x23, 0x21, 0x5E)

    private val g_key_l = intArrayOf(120, 6, 173, 76, 51, 134, 93, 24, 76, 1, 63, 70)

    /**
     *  src {"pickcode":"dasfasdfasf"}
     *  time System.currentTimeMillis()/1000
     */
    fun m115_encode(src: String, time: Long): KeyBean {
        /**
         *  var key, tmp, zz;
        key = stringToBytes(md5(`!@###@#${tm}DFDR@#@#`));
        tmp = stringToBytes(src);
        tmp = m115_sym_encode(tmp, tmp.length, key, null);
        tmp = key.slice(0, 16).concat(tmp);
        return {
        data: m115_asym_encode(tmp, tmp.length),
        key
        };
         */
        val key = stringToBytes("!@###@#${time}DFDR@#@#".md5()).toIntArray()
        val srcBytes = stringToBytes(src).toIntArray()

        var tmp = m115_sym_encode(srcBytes, srcBytes.size, key, null)
        tmp = key.slice(IntRange(0, 15)).map { it }.plus(tmp)

        var data = m115_asym_encode(tmp, tmp.size)
        data = URLEncoder.encode(data, Charsets.UTF_8.name())
        return KeyBean(data, key)
    }

    fun stringToBytes(text: String): List<Int> {
        val arrayListOf = arrayListOf<Int>()
        text.toCharArray().forEach { i->
            arrayListOf.add(i.code)
        }
        return arrayListOf
    }

    private fun m115_asym_encode(src: List<Int>, srclen: Int): String {
        /**
         *
         *  var i, j, m, ref, ret
        m = 128 - 11;
        ret = '';
        for (i = j = 0, ref = Math.floor((srclen + m - 1) / m); (0 <= ref ? j < ref : j > ref); i = 0 <= ref ? ++j : --j) {
        ret += new_rsa.encrypt(bytesToString(src.slice(i * m, Math.min((i + 1) * m, srclen))));
        }
        return window.btoa(new_rsa.hex2a(ret));
         */

        val m = 128 - 11
        var ret = ""
        var ref: Double
        val new_rsa = MyRsaUtil()


        kotlin.run {
            var j: Int
            var i = 0.also { j = it }
            ref = floor(((srclen + m - 1) / m).toDouble())
            while (if (0 <= ref) j < ref else j > ref) {
                var text = ""
                src.slice(IntRange(i * m, min((i + 1) * m, srclen) - 1))
                    .forEach { a -> text += a.toChar() }
                ret += new_rsa.encrypt(text)
                i = if (0 <= ref) ++j else --j
            }
        }
//        return android.util.Base64.encodeToString(new_rsa.hex2a(ret), android.util.Base64.NO_WRAP)
        val hex2a = new_rsa.hex2aBetyArray(ret)
        return Base64.getEncoder().encodeToString(hex2a)
    }


    private fun m115_sym_encode(
        src: IntArray,
        srclen: Int,
        key1: IntArray,
        key2: IntArray?
    ): List<Int> {

        /**
         * var k1, k2, ret;
        k1 = m115_getkey(4, key1);
        k2 = m115_getkey(12, key2);
        ret = xor115_enc(src, srclen, k1, 4);
        ret.reverse();
        ret = xor115_enc(ret, srclen, k2, 12);
        return ret;
         */
        val k1 = m115_getkey(4, key1)
        val k2 = m115_getkey(12, key2)
        var ret = xor115_enc(src.toList(), srclen, k1, 4)
        ret = ret.reversed()
        ret = xor115_enc(ret, srclen, k2, 12)
        return ret
    }

    private fun m115_getkey(length: Int, key: IntArray?): List<Int> {
        if (key != null) {
            val results = arrayListOf<Int>();
            run {
                var j = 0
                var i = 0
                while (if (0 <= length) j < length else j > length) {
                    results.add(key[i] + g_kts[length * i] and 0xff xor g_kts[length * (length - 1 - i)])
                    i = if (0 <= length) ++j else --j
                }
            }
            return results
        }
        if (length == 12) {
            return g_key_l.toList()
        }
        return g_key_s.toList()
    }

    private fun xor115_enc(
        src: List<Int>,
        srclen: Int,
        key: List<Int>,
        keyLength: Int
    ): List<Int> {

        /**
         *     var i, j, k, mod4, ref, ref1, ref2, ret;
        mod4 = srclen % 4;
        ret = [];
        if (mod4 !== 0) {
        for (i = j = 0, ref = mod4; (0 <= ref ? j < ref : j > ref); i = 0 <= ref ? ++j : --j) {
        ret.push(src[i] ^ key[i % keylen]);
        }
        }
        for (i = k = ref1 = mod4, ref2 = srclen; (ref1 <= ref2 ? k < ref2 : k > ref2); i = ref1 <= ref2 ? ++k : --k) {
        ret.push(src[i] ^ key[(i - mod4) % keylen]);
        }
        return ret;
         */

        val mod4 = srclen % 4

        val ret = arrayListOf<Int>()
        if (mod4 != 0) {
            run {
                var j: Int
                var i = 0.also { j = it }
                while (if (0 <= mod4) j < mod4 else j > mod4) {
                    ret.add(src[i] xor key[i % keyLength])
                    i = if (0 <= mod4) ++j else --j
                }
            }
        }

        run {
            var ref1: Int
            var k: Int
            var i = mod4.also { ref1 = it }.also { k = it }
            while (if (ref1 <= srclen) k < srclen else k > srclen) {
                ret.add(src[i] xor key[(i - mod4) % keyLength])
                i = if (ref1 <= srclen) ++k else --k
            }
        }
        return ret
    }

    private fun String.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        val array = md.digest(this.toByteArray())
        val sb = StringBuffer()
        for (i in array.indices) {
            sb.append(Integer.toHexString(array[i].toInt() and 0xFF or 0x100).substring(1, 3))
        }
        return sb.toString()
    }

    fun m115_decode(src: String, key: IntArray): String {
        val decode = Base64.getDecoder().decode(src).toList().map { i ->
            if (i < 0) {
                i + 256
            } else {
                i.toInt()
            }
        }

        val tmp = m115_asym_decode(decode, decode.size)
        //todo check slice
        val src1 = tmp.slice(IntRange(16, tmp.size - 1))
        val key2 = tmp.slice(IntRange(0, 15)).toIntArray()
        var text = ""
        m115_sym_decode(src1, tmp.size - 16, key, key2)
            .forEach { i -> text += i.toChar() }

        return text
    }

    private fun m115_asym_decode(src: List<Int>, srclen: Int): List<Int> {
        val m = 128
        var ret = ""
        var ref: Double
        val new_rsa = MyRsaUtil()

        kotlin.run {
            var j: Int
            var i = 0.also { j = it }
            ref = floor(((srclen + m - 1) / m).toDouble())
            while (if (0 <= ref) j < ref else j > ref) {
                var text = ""
                src.slice(IntRange(i * m, min((i + 1) * m, srclen) - 1))
                    .forEach { a -> text += a.toChar() }
                ret += new_rsa.decrypt(text)
                i = if (0 <= ref) ++j else --j
            }
        }


        return ret.toCharArray().map { i -> i.code }
    }

    private fun m115_sym_decode(
        src: List<Int>,
        srclen: Int,
        key1: IntArray,
        key2: IntArray
    ): List<Int> {
        val k1 = m115_getkey(4, key1)
        val k2 = m115_getkey(12, key2)
        var ret = xor115_enc(src, srclen, k2, 12)
        ret = ret.reversed()
        ret = xor115_enc(ret, srclen, k1, 4)
        return ret
    }

}