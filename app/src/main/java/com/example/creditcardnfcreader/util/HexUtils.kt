package com.example.creditcardnfcreader.util

object HexUtils {
    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    fun ByteArray.toHexString(): String {
        val result = StringBuilder(size * 2)
        forEach { byte ->
            val i = byte.toInt()
            result.append(HEX_CHARS[i shr 4 and 0x0F])
            result.append(HEX_CHARS[i and 0x0F])
        }
        return result.toString()
    }
}