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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.cfscanner.xray.ConfigParser
import com.example.cfscanner.xray.ProxyConfig
import com.example.cfscanner.xray.XrayRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun CfTab(fa: Boolean) {
    fun t(key: String) = tr(key, fa)

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val xray = remember(context) {
        XrayRunner(context.filesDir, context.applicationInfo.nativeLibraryDir)
    }
    var provider by remember { mutableStateOf(0) }
    var port by remember { mutableStateOf(443) }
    var perRange by remember { mutableStateOf(30) }
    var status by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var results by remember { mutableStateOf(listOf<Result>()) }
    var scanned by remember { mutableStateOf(listOf<Result>()) }
    var stage by remember { mutableStateOf(0) }
    var toFile by remember { mutableStateOf(false) }
    var configText by remember { mutableStateOf("") }
    var configError by remember { mutableStateOf(false) }
    var configUsed by remember { mutableStateOf<ProxyConfig?>(null) }
    var xraySpeed by remember { mutableStateOf(false) }

    val running = job?.isActive == true
    val rangeCount = if (provider == 0) CF_RANGES.size else FASTLY_RANGES.size

    val ipSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            save(context, uri, results.joinToString("\n") { it.ip })
            status = t("saved")
        }
    }

    val configSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val config = configUsed
        if (uri != null && config != null) {
            save(
                context,
                uri,
                results.joinToString("\n") { result ->
                    configForIp(config.raw, result.ip)
                }
            )
            status = t("saved")
        }
    }

    fun finish(list: List<Result>, config: ProxyConfig?) {
        results = list
        configUsed = config
        status = if (list.isEmpty()) t("none") else t("done")
        if (toFile && list.isNotEmpty()) {
            if (config != null) {
                configSaver.launch("configs.txt")
            } else {
                ipSaver.launch("ips.txt")
            }
        }
    }

    if (stage == 1) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(t("q_out")) },
            confirmButton = {
                TextButton({
                    toFile = true
                    stage = 2
                }) {
                    Text(t("to_file"))
                }
            },
            dismissButton = {
                TextButton({
                    toFile = false
                    stage = 2
                }) {
                    Text(t("show_only"))
                }
            }
        )
    }

    if (stage == 2) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(t("q_cfg")) },
            confirmButton = {
                TextButton({ stage = 3 }) {
                    Text(t("yes"))
                }
            },
            dismissButton = {
                TextButton({
                    stage = 0
                    finish(scanned, null)
                }) {
                    Text(t("no"))
                }
            }
        )
    }

    if (stage == 3) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(t("cfg_t")) },
            text = {
                Column {
                    OutlinedTextField(
                        value = configText,
                        onValueChange = {
                            configText = it
                            configError = false
                        },
                        placeholder = { Text("Paste a config URI") },
                        maxLines = 6
                    )
                    Text(
                        t("cfg_hint"),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (configError) {
                        Text(
                            t("bad"),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton({
                    val config = ConfigParser.parse(configText)
                    if (config == null || !xray.isAvailable()) {
                        configError = true
                    } else {
                        stage = 0
                        job = scope.launch {
                            val output = xray.testMany(
                                scanned.map { it.ip }.take(400),
                                config,
                                xraySpeed
                            ) { done, total ->
                                status = "Xray: $done/$total"
                            }
                            finish(
                                scanned.mapNotNull { result ->
                                    output[result.ip]?.let { test ->
                                        result.copy(
                                            cfgMs = test.latencyMs,
                                            cfgMbps = test.mbps
                                        )
                                    }
                                },
                                config
                            )
                        }
                    }
                }) {
                    Text(t("test"))
                }
            },
            dismissButton = {
                TextButton({
                    stage = 0
                    finish(scanned, null)
                }) {
                    Text(t("cancel"))
                }
            }
        )
    }

    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(t("provider"))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Cloudflare", "Fastly").forEachIndexed { index, name ->
                FilterChip(
                    selected = provider == index,
                    enabled = !running,
                    onClick = {
                        provider = index
                        port = 443
                    },
                    label = { Text(name) }
                )
            }
        }

        Text(t("port"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(if (provider == 0) HTTPS_PORTS + HTTP_PORTS else listOf(443, 80)) { selectedPort ->
                FilterChip(
                    selected = port == selectedPort,
                    enabled = !running,
                    onClick = { port = selectedPort },
                    label = { Text("$selectedPort") }
                )
            }
        }

        Text(t("scan_size"))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                10 to "quick",
                30 to "normal",
                80 to "deep"
            ).forEach { (count, key) ->
                FilterChip(
                    selected = perRange == count,
                    enabled = !running,
                    onClick = { perRange = count },
                    label = { Text("${t(key)} (${count * rangeCount})") }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = xraySpeed,
                onCheckedChange = { xraySpeed = it },
                enabled = !running
            )
            Text(t("xray_speed"))
        }

        Button(
            onClick = {
                if (running) {
                    job?.cancel()
                    status = t("stopped")
                } else {
                    results = emptyList()
                    configUsed = null
                    job = scope.launch {
                        scanned = scan(provider, port, perRange) { message ->
                            status = message
                        }
                        results = scanned
                        if (scanned.isEmpty()) {
                            status = t("none")
                        } else {
                            stage = 1
                        }
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

        Text(
            status.ifEmpty {
                if (!xray.isAvailable()) t("xray_unavailable") else t("ready")
            }
        )

        if (results.isNotEmpty() && !running) {
            Text("${t("found")}: ${results.size}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { ipSaver.launch("ips.txt") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(IconDownload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("save_ips"))
                }
                if (configUsed != null) {
                    OutlinedButton(
                        onClick = { configSaver.launch("configs.txt") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(IconDownload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("save_cfgs"))
                    }
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(results) { result ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboard.setText(AnnotatedString(result.ip))
                        }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(result.ip)
                        Text(
                            "${result.pingMs} ms  |  " +
                                (result.mbps?.let { "%.1f Mbps".format(it) } ?: "-") +
                                (result.cfgMs?.let { "  |  Xray $it ms" } ?: "") +
                                (result.cfgMbps?.let { "  |  %.1f Mbps".format(it) } ?: "")
                        )
                    }
                }
            }
        }
    }
}
