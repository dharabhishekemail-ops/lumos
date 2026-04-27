package com.lumos.feature.voucher

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

interface VoucherQrScanner {
    /** Emits raw QR string payloads. */
    fun start(onPayload: (String) -> Unit)
    fun stop()
}

@Composable
fun VoucherScanScreen(
    scanner: VoucherQrScanner,
    onParsed: (Voucher) -> Unit,
    onError: (String) -> Unit,
) {
    var status by remember { mutableStateOf("Point the camera at the venue QR") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Scan Voucher", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            status = "Scanning..."
            scanner.start { payload ->
                try {
                    val v = VoucherParser.parse(payload)
                    onParsed(v)
                } catch (e: Exception) {
                    onError("Invalid voucher")
                }
            }
        }) { Text("Start Scan") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { scanner.stop(); status = "Stopped" }) { Text("Stop") }
    }
}

object VoucherParser {
    fun parse(payload: String): Voucher {
        // Payload format: base64url(JSON)
        val json = String(java.util.Base64.getUrlDecoder().decode(payload))
        val obj = org.json.JSONObject(json)
        return Voucher(
            voucherId = obj.getString("voucherId"),
            venueId = obj.getString("venueId"),
            offerTitle = obj.getString("offerTitle"),
            expiresEpochMs = obj.getLong("expiresEpochMs"),
            signatureB64 = obj.getString("sig")
        )
    }
}
