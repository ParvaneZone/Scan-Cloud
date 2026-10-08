package com.example.cfscanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val OkColor = Color(0xFF2E9E5B)
private val BadColor = Color(0xFFC0504D)

private class IpTestState {
    var resolved by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)

    var info by mutableStateOf<IpInfo?>(null)
    var rdns by mutableStateOf<String?>(null)
    var infoDone by mutableStateOf(false)

    var icmp by mutableStateOf<PingStats?>(null)
    var icmpDone by mutableStateOf(false)
    var tcp by mutableStateOf<PingStats?>(null)
    var tcpDone by mutableStateOf(false)

    var ports by mutableStateOf<List<PortProbe>>(emptyList())
    var portsDone by mutableStateOf(false)

    var http by mutableStateOf<HttpProbe?>(null)
    var https by mutableStateOf<HttpProbe?>(null)
    var httpDone by mutableStateOf(false)

    fun reset() {
        resolved = null
        error = null
        info = null
        rdns = null
        infoDone = false
        icmp = null
        icmpDone = false
        tcp = null
        tcpDone = false
        ports = emptyList()
        portsDone = false
        http = null
        https = null
        httpDone = false
    }
}

@Composable
fun IpTestTab(fa: Boolean) {
    fun t(key: String) = tr(key, fa)

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val state = remember { IpTestState() }
    var input by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    val running = job?.isActive == true

    var myInfo by remember { mutableStateOf<IpInfo?>(null) }
    var myLoading by remember { mutableStateOf(true) }

    fun loadMyIp() {
        myLoading = true
        scope.launch {
            myInfo = withContext(Dispatchers.IO) { fetchMyIp() }
            myLoading = false
        }
    }

    LaunchedEffect(Unit) { loadMyIp() }

    fun runTest() {
        val host = parseTarget(input)
        if (host.isBlank()) return
        job?.cancel()
        state.reset()
        job = scope.launch {
            val ip = withContext(Dispatchers.IO) { resolveTarget(host) }
            if (ip == null) {
                state.error = t("ipt_bad")
                return@launch
            }
            state.resolved = ip
            coroutineScope {
                launch {
                    state.info = withContext(Dispatchers.IO) { fetchIpInfo(ip) }
                    state.infoDone = true
                    state.rdns = reverseDns(ip)
                }
                launch {
                    state.icmp = withContext(Dispatchers.IO) { icmpPing(ip) }
                    state.icmpDone = true
                }
                launch {
                    state.tcp = withContext(Dispatchers.IO) {
                        val first = tcpPingStats(ip, 443)
                        if (first.received > 0) first else tcpPingStats(ip, 80)
                    }
                    state.tcpDone = true
                }
                launch {
                    state.ports = probePorts(ip)
                    state.portsDone = true
                }
                launch {
                    state.http = withContext(Dispatchers.IO) { probeHttp(ip, false) }
                    state.https = withContext(Dispatchers.IO) { probeHttp(ip, true) }
                    state.httpDone = true
                }
            }
        }
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(t("ipt_title"), style = MaterialTheme.typography.titleMedium)
        Text(t("ipt_hint"), style = MaterialTheme.typography.bodySmall)

        // Your own IP, with a re-check button next to it.
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(IconPin, null, Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { myInfo?.let { clipboard.setText(AnnotatedString(it.ip)) } }
                ) {
                    Text(t("ipt_my"), style = MaterialTheme.typography.labelMedium)
                    val mine = myInfo
                    when {
                        myLoading -> Text("...", style = MaterialTheme.typography.titleMedium)
                        mine == null -> Text(
                            t("ipt_my_err"),
                            style = MaterialTheme.typography.bodySmall,
                            color = BadColor
                        )
                        else -> {
                            Text(mine.ip, style = MaterialTheme.typography.titleMedium)
                            val sub = listOfNotNull(mine.country, mine.isp ?: mine.org)
                                .joinToString("  |  ")
                            if (sub.isNotEmpty()) {
                                Text(sub, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Text(
                        t("ipt_vpn_note"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (myLoading) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                } else {
                    IconButton({ loadMyIp() }) {
                        Icon(IconRefresh, t("ipt_recheck"))
                    }
                }
            }
            val mine = myInfo
            if (mine != null && !myLoading) {
                TextButton(
                    onClick = {
                        input = mine.ip
                        runTest()
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(IconSearch, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("ipt_use_mine"))
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(t("ipt_field")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { runTest() })
        )

        Button(
            onClick = {
                if (running) {
                    job?.cancel()
                } else {
                    runTest()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(if (running) IconClose else IconSearch, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (running) t("stop") else t("ipt_run"))
        }

        state.error?.let {
            Text(it, color = BadColor)
        }

        val resolved = state.resolved
        if (resolved != null) {
            Text("${t("ipt_resolved")}: $resolved", style = MaterialTheme.typography.bodyMedium)

            SectionCard(IconServer, t("ipt_info"), !state.infoDone) {
                val info = state.info
                if (info == null && state.infoDone) {
                    Text(t("ipt_info_err"), style = MaterialTheme.typography.bodySmall)
                }
                Row3(t("ipt_ip"), resolved, onClick = { clipboard.setText(AnnotatedString(resolved)) })
                Row3(t("ipt_type"), info?.type)
                Row3(t("ipt_country"), info?.country)
                Row3(t("ipt_region"), info?.region)
                Row3(t("ipt_city"), info?.city)
                Row3(t("ipt_isp"), info?.isp)
                Row3(t("ipt_org"), info?.org)
                Row3(t("ipt_asn"), info?.asn)
                Row3(t("ipt_tz"), info?.timezone)
                Row3(t("ipt_rdns"), state.rdns)
            }

            SectionCard(IconActivity, t("ipt_ping"), !(state.icmpDone && state.tcpDone)) {
                PingBlock(t("ipt_icmp"), state.icmp, state.icmpDone, fa)
                Spacer(Modifier.height(6.dp))
                PingBlock(t("ipt_tcpping"), state.tcp, state.tcpDone, fa)
            }

            SectionCard(IconPlug, t("ipt_ports"), !state.portsDone) {
                if (state.portsDone) {
                    Text(
                        "${t("ipt_open_count")}: ${state.ports.count { it.open }} / ${state.ports.size}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                state.ports.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (p.open) IconCheck else IconClose,
                            null,
                            Modifier.size(16.dp),
                            tint = if (p.open) OkColor else BadColor
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${p.port}  ${p.label}", Modifier.weight(1f))
                        Text(
                            if (p.open) "${t("ipt_open")}  |  ${p.ms} ms" else t("ipt_closed"),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (p.open) OkColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SectionCard(IconCloud, t("ipt_http"), !state.httpDone) {
                HttpBlock("HTTP :80", state.http, fa)
                Spacer(Modifier.height(6.dp))
                HttpBlock("HTTPS :443", state.https, fa)
                if (state.https != null) {
                    Text(
                        t("ipt_cert_note"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    loading: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun Row3(label: String, value: String?, onClick: (() -> Unit)? = null) {
    if (value.isNullOrBlank()) return
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(vertical = 2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(value, Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

private fun ms(v: Double?): String = if (v == null) "-" else "%.0f".format(v)

@Composable
private fun PingBlock(
    title: String,
    stats: PingStats?,
    done: Boolean,
    fa: Boolean
) {
    fun t(key: String) = tr(key, fa)
    Text(title, style = MaterialTheme.typography.labelLarge)
    if (stats == null) {
        if (done) Text(t("ipt_noicmp"), style = MaterialTheme.typography.bodySmall)
        return
    }
    if (stats.received == 0) {
        Text(t("ipt_fail"), color = BadColor, style = MaterialTheme.typography.bodySmall)
    }
    Row3(t("ipt_sent"), "${stats.sent} / ${stats.received}")
    Row3(t("ipt_loss"), "${stats.lossPercent}%")
    if (stats.received > 0) {
        Row3(t("ipt_latency"), "${ms(stats.minMs)} / ${ms(stats.avgMs)} / ${ms(stats.maxMs)} ms")
    }
}

@Composable
private fun HttpBlock(title: String, probe: HttpProbe?, fa: Boolean) {
    fun t(key: String) = tr(key, fa)
    Text(title, style = MaterialTheme.typography.labelLarge)
    if (probe == null) return
    if (probe.code == null) {
        Text(
            "${t("ipt_fail")} (${probe.error ?: "-"})",
            color = BadColor,
            style = MaterialTheme.typography.bodySmall
        )
        return
    }
    Row3(t("ipt_status"), probe.code.toString())
    Row3(t("ipt_time"), probe.ms?.let { "$it ms" })
    Row3(t("ipt_server"), probe.server)
    Row3(t("ipt_redirect"), probe.location)
    Row3(t("ipt_proto"), listOfNotNull(probe.protocol, probe.tls).joinToString("  |  "))
}
