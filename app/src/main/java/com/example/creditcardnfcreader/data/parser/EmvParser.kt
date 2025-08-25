package com.example.creditcardnfcreader.data.parser

import com.example.creditcardnfcreader.data.model.EmvCard
import com.example.creditcardnfcreader.util.HexUtils.toHexString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EmvParser {

    data class PpseData(val aids: List<AidInfo>)
    data class AidInfo(val aid: String, val priority: Int?)
    data class FciData(val dfName: String?, val afl: List<AflEntry>?)
    data class AflEntry(val sfi: Int, val firstRecord: Int, val lastRecord: Int)

    fun parsePpse(data: ByteArray): PpseData {
        val tlv = parseTlv(data)
        val fci = tlv[0x6F] ?: return PpseData(emptyList())
        val fciTlv = parseTlv(fci)
        val fciPropTemplate = fciTlv[0xA5] ?: return PpseData(emptyList())
        val fciPropTlv = parseTlv(fciPropTemplate)

        val aids = mutableListOf<AidInfo>()
        fciPropTlv.entries.forEach { entry ->
            if (entry.key == 0xBF0C) { // FCI Issuer Discretionary Data
                val appTemplateTlv = parseTlv(entry.value)
                appTemplateTlv[0x61]?.let { appTemplate ->
                    val innerTlv = parseTlv(appTemplate)
                    val aid = innerTlv[0x4F]?.toHexString()
                    val priority = innerTlv[0x87]?.firstOrNull()?.toInt()
                    if (aid != null) {
                        aids.add(AidInfo(aid, priority))
                    }
                }
            }
        }
        return PpseData(aids)
    }

    fun parseFci(data: ByteArray): FciData {
        val tlv = parseTlv(data)
        val fci = tlv[0x6F] ?: return FciData(null, null)
        val fciTlv = parseTlv(fci)
        val fciPropTemplate = fciTlv[0xA5] ?: return FciData(null, null)
        val fciPropTlv = parseTlv(fciPropTemplate)

        val dfName = fciTlv[0x84]?.let { String(it) }
        val aflBytes = fciPropTlv[0x94]
        val afl = aflBytes?.let { parseAfl(it) }

        return FciData(dfName, afl)
    }

    fun parseRecords(data: ByteArray, aid: String): EmvCard {
        val tlv = parseTlv(data)

        val pan = tlv[0x5A]?.toHexString()
        val cardholderName = tlv[0x5F20]?.let { String(it).trim() }
        val expiryDate = tlv[0x5F24]?.toHexString()?.substring(0, 4)
        val appLabel = tlv[0x50]?.let { String(it) }

        val track2 = tlv[0x57]?.toHexString()
        val panFromTrack2 = track2?.substringBefore('D')

        val finalPan = pan ?: panFromTrack2

        return EmvCard(
            scheme = getSchemeFromAid(aid),
            applicationLabel = appLabel ?: "N/A",
            pan = finalPan?.let { maskPan(it) } ?: "N/A",
            expiryDate = formatExpiry(expiryDate),
            cardholderName = cardholderName ?: "N/A"
        )
    }

    fun parseTlv(data: ByteArray, offset: Int = 0): Map<Int, ByteArray> {
        val tlvMap = mutableMapOf<Int, ByteArray>()
        var currentOffset = offset
        while (currentOffset < data.size) {
            val tag = readTag(data, currentOffset)
            currentOffset += if ((tag and 0xFF) > 0xFF) 2 else 1

            val lengthResult = readLength(data, currentOffset)
            val length = lengthResult.first
            currentOffset += lengthResult.second

            val value = data.sliceArray(currentOffset until currentOffset + length)
            tlvMap[tag] = value
            currentOffset += length
        }
        return tlvMap
    }

    private fun readTag(data: ByteArray, offset: Int): Int {
        var tag = data[offset].toInt() and 0xFF
        if ((tag and 0x1F) == 0x1F) { // Two-byte tag
            tag = (tag shl 8) or (data[offset + 1].toInt() and 0xFF)
        }
        return tag
    }

    private fun readLength(data: ByteArray, offset: Int): Pair<Int, Int> {
        var len = data[offset].toInt() and 0xFF
        var lenBytes = 1
        if ((len and 0x80) == 0x80) { // Long form
            lenBytes += len and 0x7F
            len = 0
            for (i in 1 until lenBytes) {
                len = (len shl 8) or (data[offset + i].toInt() and 0xFF)
            }
        }
        return Pair(len, lenBytes)
    }

    private fun parseAfl(data: ByteArray): List<AflEntry> {
        val aflList = mutableListOf<AflEntry>()
        for (i in 0 until data.size / 4) {
            val offset = i * 4
            val sfi = data[offset].toInt() shr 3
            val firstRecord = data[offset + 1].toInt()
            val lastRecord = data[offset + 2].toInt()
            aflList.add(AflEntry(sfi, firstRecord, lastRecord))
        }
        return aflList
    }

    private fun getSchemeFromAid(aid: String): String {
        return when {
            aid.startsWith("A000000003") -> "Visa"
            aid.startsWith("A000000004") -> "Mastercard"
            aid.startsWith("A000000025") -> "American Express"
            aid.startsWith("A000000524") -> "RuPay"
            else -> "Unknown"
        }
    }

    private fun maskPan(pan: String): String {
        if (pan.length < 10) return "Invalid PAN"
        return "${pan.take(6)}******${pan.takeLast(4)}"
    }

    private fun formatExpiry(yyMM: String?): String {
        return if (yyMM != null && yyMM.length == 4) {
            "${yyMM.substring(2, 4)}/${yyMM.substring(0, 2)}" // MM/YY
        } else "N/A"
    }
}