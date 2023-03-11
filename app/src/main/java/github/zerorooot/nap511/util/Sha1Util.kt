package github.zerorooot.nap511.util

import android.R.attr
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*
import kotlin.math.floor
import kotlin.math.min


class Sha1Util {
    data class KeyBean(val data: String, val key: List<Int>)

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

    fun m115_encode(pickCode: String, time: Long): KeyBean {
        val src = "{\"pickcode\":\"$pickCode\"}";
        val key = stringToBytes(md5("!@###@#${time}DFDR@#@#"))
        val tmp1 = stringToBytes(src);
        val tmp2 = m115_sym_encode(tmp1, tmp1.size, key, null)
        val tmp3 = ArrayList<Int>(key.slice(IntRange(0, 15)))
        tmp3.addAll(tmp2)
        val m115AsymEncode = m115_asym_encode(tmp3, tmp3.size)
        return KeyBean(m115AsymEncode, key)
    }

    private fun m115_asym_encode(src: List<Int>, srclen: Int): String {
        val new_rsa = MyRsaUtil()
        val m = 117
        var ret = ""

        var i: Int
        var j: Int
        i = 0.also { j = it }
        val ref: Double = floor(((srclen + m - 1) / m).toDouble())
        while (if (0 <= ref) j < ref else j > ref) {
            val start = i * m
            val end = min((i + 1) * m, srclen) - 1
            val slice = src.slice(IntRange(start, end))
            val s = bytesToString(slice)
            ret += new_rsa.encrypt(s)
            i = if (0 <= ref) ++j else --j
        }
        val a = new_rsa.hex2aByteArray(ret)
        return Base64.getEncoder().encodeToString(a)
    }

    private fun m115_sym_encode(
        src: List<Int>, length: Int, key1: List<Int>, key2: List<Int>?
    ): List<Int> {
        val k1 = m115_getkey(4, key1);
        val k2 = m115_getkey(12, key2)
        val ret = xor115_enc(src, length, k1, 4).reversed()
        val r = xor115_enc(ret, length, k2, 12)
        return r
    }

    private fun m115_getkey(length: Int, key: List<Int>?): List<Int> {
        if (key != null) {
            var j: Int
            val results = arrayListOf<Int>()
            var i = 0.also { j = it }
            val ref: Int = length
            while (if (0 <= ref) j < ref else j > ref) {
                results.add(key[i] + g_kts[length * i] and 0xff xor g_kts[length * (length - 1 - i)])
                i = if (0 <= ref) ++j else --j
            }
            return results
        }

        if (length == 12) {
            return g_key_l.toList()
        }
        return g_key_s.toList()
    }


    private fun xor115_enc(src: List<Int>, length: Int, key: List<Int>, keyLen: Int): List<Int> {
        val mod4 = length % 4;
        val ret = arrayListOf<Int>()
        if (mod4 != 0) {
            var i: Int
            var j: Int
            i = 0.also { j = it }
            val ref: Int = mod4
            while (if (0 <= ref) j < ref else j > ref) {
                ret.add(src[i] xor key[i % keyLen])
                i = if (0 <= ref) ++j else --j
            }
        }

        var i: Int
        var k: Int
        var ref1: Int
        i = mod4.also { ref1 = it }.also { k = it }
        while (if (ref1 <= length) k < length else k > length) {
            ret.add(src[i] xor key[(i - mod4) % keyLen])
            i = if (ref1 <= length) ++k else --k
        }

        return ret
    }

    private fun stringToBytes(s: String): List<Int> {
        val bytes = arrayListOf<Int>()
        s.forEach { i ->
            bytes.add(i.code)
        }
        return bytes
    }

    fun m115_decode(src: String, key: List<Int>): String {
        val decode = Base64.getDecoder().decode(src).toList().map { i ->
            if (i < 0) {
                i + 256
            } else {
                i.toInt()
            }
        }
        val tmp = m115_asym_decode(decode, decode.size)
        val src1 = tmp.slice(IntRange(16, tmp.size - 1))
        val key2 = tmp.slice(IntRange(0, 15))
        val m115SymDecode = m115_sym_decode(src1, tmp.size - 16, key, key2)
        val bytesToString = bytesToString(m115SymDecode)
        return bytesToString
    }

    private fun m115_sym_decode(
        src: List<Int>,
        srclen: Int,
        key1: List<Int>,
        key2: List<Int>
    ): List<Int> {
        val k1 = m115_getkey(4, key1)
        val k2 = m115_getkey(12, key2)
        val reversed = xor115_enc(src, srclen, k2, 12).reversed()
        val xor115Enc = xor115_enc(reversed, srclen, k1, 4)
        return xor115Enc
    }

    private fun m115_asym_decode(src: List<Int>, srclen: Int): List<Int> {
        val new_rsa = MyRsaUtil()
        val m = 128
        var ret = ""
        var j: Int
        var i = 0.also { j = it }
        val ref: Double = floor(((srclen + m - 1) / m).toDouble())
        while (if (0 <= ref) j < ref else j > ref) {
            val start = i * m
            val end = min((i + 1) * m, srclen) - 1
            val slice = src.slice(IntRange(start, end))
            val s = bytesToString(slice)
            ret += new_rsa.decrypt(s)
            i = if (0 <= ref) ++j else --j
        }
        return stringToBytes(ret)
    }

    private fun bytesToString(b: List<Int>): String {
        var ret = ""
        var i: Int
        var j = 0
        val len = b.size
        while (j < len) {
            i = b[j]
            ret += i.toChar()
            j++
        }
        return ret
    }

    private fun md5(scr: String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(scr.toByteArray())).toString(16).padStart(32, '0')
    }
}