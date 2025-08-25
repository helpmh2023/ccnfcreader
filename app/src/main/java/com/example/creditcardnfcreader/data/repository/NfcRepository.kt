package com.example.creditcardnfcreader.data.repository


import android.nfc.tech.IsoDep
import com.example.creditcardnfcreader.data.model.CardApplication
import com.example.creditcardnfcreader.data.model.EmvCard
import com.example.creditcardnfcreader.data.parser.EmvParser
import com.example.creditcardnfcreader.util.HexUtils.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class NfcRepository {

    suspend fun readNfcTag(isoDep: IsoDep): Result<EmvCard> = withContext(Dispatchers.IO) {
        try {
            isoDep.connect()
            isoDep.timeout = 5000 // 5-second timeout

            val ppseResponse = isoDep.transceive(SELECT_PPSE_COMMAND)
            if (!isSuccess(ppseResponse)) {
                return@withContext Result.failure(IOException("Failed to select PPSE."))
            }

            val ppseData = EmvParser.parsePpse(ppseResponse)
            val applications = mutableListOf<CardApplication>()
            var finalCardData: EmvCard? = null

            // Select highest priority application first
            val sortedAids = ppseData.aids.sortedBy { it.priority }

            for (appInfo in sortedAids) {
                val selectAppResponse = isoDep.transceive(createSelectAidCommand(appInfo.aid))
                if (!isSuccess(selectAppResponse)) continue

                val appData = EmvParser.parseFci(selectAppResponse)
                applications.add(
                    CardApplication(
                        aid = appInfo.aid,
                        dfName = appData.dfName ?: "N/A",
                        priority = appInfo.priority
                    )
                )

                // Read records only from the first successfully selected application
                if (finalCardData == null) {
                    appData.afl?.let { afl ->
                        val allRecords = readAllRecords(isoDep, afl)
                        finalCardData = EmvParser.parseRecords(allRecords, appInfo.aid)
                    }
                }
            }

            isoDep.close()

            if (finalCardData != null) {
                Result.success(finalCardData!!.copy(applications = applications))
            } else {
                Result.failure(IOException("Could not read any application data from the card."))
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            if (isoDep.isConnected) {
                isoDep.close()
            }
        }
    }

    private fun readAllRecords(isoDep: IsoDep, afl: List<EmvParser.AflEntry>): ByteArray {
        var allRecordsData = byteArrayOf()
        for (entry in afl) {
            for (recordNum in entry.firstRecord..entry.lastRecord) {
                val readRecordCommand = createReadRecordCommand(entry.sfi, recordNum)
                val response = isoDep.transceive(readRecordCommand)
                if (isSuccess(response)) {
                    val tlvData = EmvParser.parseTlv(response)
                    tlvData[0x70]?.let { allRecordsData += it }
                }
            }
        }
        return allRecordsData
    }

    private fun isSuccess(response: ByteArray): Boolean {
        return response.size >= 2 && response[response.size - 2] == 0x90.toByte() && response.last() == 0x00.toByte()
    }

    companion object {
        // Command to select the Proximity Payment System Environment (PPSE)
        private val SELECT_PPSE_COMMAND = "00A404000E325041592E5359532E444446303100".chunked(2)
            .map { it.toInt(16).toByte() }.toByteArray()

        private fun createSelectAidCommand(aid: String): ByteArray {
            val aidBytes = aid.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val header = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00)
            val length = byteArrayOf(aidBytes.size.toByte())
            return header + length + aidBytes + 0x00.toByte()
        }

        private fun createReadRecordCommand(sfi: Int, recordNum: Int): ByteArray {
            val p1 = recordNum.toByte()
            val p2 = ((sfi shl 3) or 4).toByte() // SFI | 100b
            return byteArrayOf(0x00, 0xB2.toByte(), p1, p2, 0x00)
        }
    }
}