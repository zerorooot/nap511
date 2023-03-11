package github.zerorooot.nap511.util

import java.math.BigInteger

class MyRsaUtil {
    private val n: BigInteger = BigInteger(
        "8686980c0f5a24c4b9d43020cd2c22703ff3f450756529058b1cf88f09b8602136477198a6e2683149659bd122c33592fdb5ad47944ad1ea4d36c6b172aad6338c3bb6ac6227502d010993ac967d1aef00f0c8e038de2e4d3bc2ec368af2e9f10a6f1eda4f7262f136420c07c331b871bf139f74f3010e3c4fe57df3afb71683",
        16
    )
    private val e: BigInteger = BigInteger("10001", 16)

    fun encrypt(text: String): String {
        val m = pkcs1pad2(text, 0x80)
        val c = m.modPow(e, n)
        var h = c.toString(16)
        while (h.length < 0x80 * 2) {
            h = "0$h"
        }
        return h
    }

    fun decrypt(text: String): String {
        val ba = Array(text.length) { 0 }
        var i = 0
        while (i < text.length) {
            ba[i] = text.toCharArray()[i].code
            i++
        }
        val a = BigInteger(a2hex(ba), 16)
        val c = a.modPow(e, n)
        return pkcs1unpad2(c)
    }

    private fun pkcs1unpad2(a: BigInteger): String {
        var b = a.toString(16)
        if (b.length % 2 != 0) {
            b = "0$b"
        }
        val c = hex2a(b)
        var i = 1
        while (c.toCharArray()[i].code != 0) {
            i++
        }
        return c.slice(IntRange(i + 1, c.length - 1))
    }


    private fun pkcs1pad2(s: String, int: Int): BigInteger {
        var n = int
        var i = s.length - 1
        val ba = Array(n) { 0 }
        while (i >= 0 && n > 0) {
            ba[--n] = s.toCharArray()[i--].code
        }
        ba[--n] = 0
        while (n > 2) { // random non-zero pad
            ba[--n] = 0xff
        }
        ba[--n] = 2
        ba[--n] = 0
        val c = a2hex(ba)
        return BigInteger(c, 16)
    }

    private fun a2hex(byteArray: Array<Int>): String {
        var hexString = ""
        var nextHexByte: String
        byteArray.forEach { i ->
            nextHexByte = i.toString(16)
            if (nextHexByte.length < 2) {
                nextHexByte = "0$nextHexByte"
            }
            hexString += nextHexByte
        }
        return hexString
    }

    private fun hex2a(hex: String): String {
        var str = ""
        var i = 0
        while (i < hex.length) {
            val s = hex.substring(i, i + 2).toInt(16).toChar()
            str += s
            i += 2
        }

        return str
    }

    fun hex2aByteArray(hex: String): ByteArray {
        val str = arrayListOf<Byte>()
        run {
            var i = 0
            while (i < hex.length) {
                val s = hex.substring(i, i + 2).toInt(16).toChar().code.toByte()
                str.add(s)
                i += 2
            }
        }
        return str.toByteArray()
    }
}
