package com.example.creditcardnfcreader.data.model

data class EmvCard(
    val scheme: String = "Unknown",
    val applicationLabel: String = "N/A",
    val pan: String = "N/A", // Masked PAN
    val expiryDate: String = "N/A", // YY/MM
    val cardholderName: String = "N/A",
    val applications: List<CardApplication> = emptyList(),
    val transactionLogs: List<TransactionLog> = emptyList()
)

data class CardApplication(
    val aid: String,
    val dfName: String,
    val priority: Int?
)

data class TransactionLog(
    val date: String, // YYYY-MM-DD
    val amount: String,
    val currency: String
)