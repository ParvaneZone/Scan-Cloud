package com.example.cfscanner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.cfscanner.xray.DohProvider
import com.example.cfscanner.xray.SniResult
import com.example.cfscanner.xray.SniScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun SniTab(fa: Boolean) {
    fun t(key: String) = tr(key, fa)

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scanner = remember(context) {
        SniScanner(context.applicationInfo.nativeLibraryDir)
    }
    var extra by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var results by remember { mutableStateOf(listOf<SniResult>()) }
    var doh by remember { mutableStateOf(DohProvider.CLOUDFLARE) }
    val running = job?.isActive == true

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            save(context, uri, results.joinToString("\n") { it.host })
            status = t("saved")
        }
    }

    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            t("sni_title"),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            t("sni_hint"),
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = extra,
            onValueChange = { extra = it },
            label = { Text(t("extra")) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            enabled = !running
        )
        Text(t("dns"))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                DohProvider.SYSTEM to t("dns_system"),
                DohProvider.CLOUDFLARE to t("dns_cf"),
                DohProvider.GOOGLE to t("dns_google")
            ).forEach { (value, label) ->
                FilterChip(
                    selected = doh == value,
                    enabled = !running,
                    onClick = { doh = value },
                    label = { Text(label) }
                )
            }
        }

        Button(
            onClick = {
                if (running) {
                    job?.cancel()
                    status = t("stopped")
                } else {
                    results = emptyList()
                    val more = extra
                        .lowercase()
                        .split(Regex("[\\s,]+"))
                        .map {
                            it.removePrefix("https://")
                                .removePrefix("http://")
                                .trimEnd('/')
                        }
                        .filter { it.contains('.') }
                    job = scope.launch {
                        results = scanner.scan(
                            (SNI_LIST + more).distinct(),
                            doh
                        ) { done, total ->
                            status = "SNI: $done/$total"
                        }
                        status = if (results.isEmpty()) t("none") else t("done")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(if (running) t("stop") else t("start"))
        }

        if (running) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Text(status.ifEmpty { t("ready") })

        if (results.isNotEmpty() && !running) {
            Text("${t("found")}: ${results.size}")
            OutlinedButton(
                onClick = { saver.launch("reality_sni.txt") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(IconDownload, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("to_file"))
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(results) { result ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboard.setText(AnnotatedString(result.host))
                        }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(result.host)
                        Text(
                            "${result.ip}  |  ${result.pingMs} ms  |  " +
                                "Xray ${result.xrayMs} ms  |  " +
                                (result.handshakeMs?.let { "TLS $it ms" } ?: "TLS -") +
                                "  |  ${result.tlsVersion ?: "TLS -"}  |  " +
                                "ALPN ${result.alpn ?: "-"}" +
                                (if (result.h2) "  |  h2" else "") +
                                (result.mbps?.let { "  |  %.1f Mbps".format(it) } ?: "")
                        )
                    }
                }
            }
        }
    }
}
