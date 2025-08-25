package com.example.creditcardnfcreader.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.creditcardnfcreader.data.model.CardApplication
import com.example.creditcardnfcreader.data.model.EmvCard
import com.example.creditcardnfcreader.ui.viewmodel.MainViewModel
import com.example.creditcardnfcreader.ui.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showConsentDialog by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EMV NFC Card Reader") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is UiState.Idle -> IdleScreen(onReset = { viewModel.resetState() })
                is UiState.Loading -> LoadingScreen()
                is UiState.Success -> SuccessScreen(card = state.card, onReset = { viewModel.resetState() })
                is UiState.Error -> ErrorScreen(message = state.message, onReset = { viewModel.resetState() })
            }

            if (showConsentDialog && uiState is UiState.Idle) {
                ConsentDialog(onDismiss = { showConsentDialog = false })
            }
        }
    }
}

@Composable
fun IdleScreen(onReset: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ready to Scan", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Hold an NFC card near your phone.", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun LoadingScreen() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Scanning...", style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
fun ErrorScreen(message: String, onReset: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Error", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onReset) {
            Text("Scan Again")
        }
    }
}

@Composable
fun SuccessScreen(card: EmvCard, onReset: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        CardSummary(card)
        Spacer(modifier = Modifier.height(16.dp))
        ApplicationsList(card.applications)
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = {
                val cardDetails = """
                    Scheme: ${card.scheme}
                    PAN: ${card.pan}
                    Expiry: ${card.expiryDate}
                """.trimIndent()
                clipboardManager.setText(AnnotatedString(cardDetails))
            }) {
                Text("Copy Details")
            }
            Button(onClick = onReset) {
                Text("Scan New Card")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        AboutAndDisclaimer()
    }
}

@Composable
fun CardSummary(card: EmvCard) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Card Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            InfoRow("Scheme / Brand", card.scheme)
            InfoRow("Application Label", card.applicationLabel)
            InfoRow("Masked PAN", card.pan)
            InfoRow("Expiry Date (MM/YY)", card.expiryDate)
            InfoRow("Cardholder Name", card.cardholderName)
        }
    }
}

@Composable
fun ApplicationsList(applications: List<CardApplication>) {
    AnimatedVisibility(visible = applications.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Detected Applications", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                applications.forEach { app ->
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        InfoRow("AID", app.aid)
                        InfoRow("DF Name", app.dfName)
                        app.priority?.let { InfoRow("Priority", it.toString()) }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutAndDisclaimer() {
    Text(
        "Disclaimer",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Text(
        "This app is for educational purposes only. It reads non-sensitive, publicly accessible data from EMV contactless cards as defined by payment network standards. It does not access, store, or transmit secure information like your full card number, CVV, or PIN. No data is sent over the network.",
        style = MaterialTheme.typography.bodySmall,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            "$label: ",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ConsentDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("User Consent") },
        text = { Text("This app reads non-sensitive, publicly accessible EMV data from contactless cards. It will not write to your card or store your data by default. Continue?") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Accept and Continue")
            }
        }
    )
}