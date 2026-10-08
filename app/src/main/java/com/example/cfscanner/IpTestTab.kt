package com.example.cfscanner

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val OkColor = Color(0xFF2E9E5B)
private val WarnColor = Color(0xFFD9922B)
private val BadColor = Color(0xFFC0504D)

private fun latencyColor(ms: Double?): Color = when {
    ms == null -> BadColor
    ms < 100 -> OkColor
    ms < 250 -> WarnColor
    else -> BadColor
}

/** Everything measured for one address family (IPv4 or IPv6). */
private class IpTestState {
    var resolved by mutableStateOf<String?>(null)

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

    var nodes by mutableStateOf<List<GlobalNode>>(emptyList())
    var nodesDone by mutableStateOf(false)
    var nodesFailed by mutableStateOf(false)

    fun reset() {
        resolved = null
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
        nodes = emptyList()
        nodesDone = false
        nodesFailed = false
    }
}

@Composable
fun IpTestTab(fa: Boolean) {
    fun t(key: String) = tr(key, fa)

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val v4 = remember { IpTestState() }
    val v6 = remember { IpTestState() }
    var selected by remember { mutableStateOf(4) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var gJob by remember { mutableStateOf<Job?>(null) }
    var gBusy by remember { mutableStateOf(false) }
    var gKind by remember { mutableStateOf("ping") }
    var gPort by remember { mutableStateOf("443") }

    var my4 by remember { mutableStateOf<IpInfo?>(null) }
    var my6 by remember { mutableStateOf<IpInfo?>(null) }
    var myLoading by remember { mutableStateOf(true) }

    fun loadMyIps() {
        myLoading = true
        scope.launch {
            coroutineScope {
                launch { my4 = withContext(Dispatchers.IO) { fetchOwnIp(false) } }
                launch { my6 = withContext(Dispatchers.IO) { fetchOwnIp(true) } }
            }
            myLoading = false
        }
    }

    LaunchedEffect(Unit) { loadMyIps() }

    suspend fun runLocal(st: IpTestState, ip: String) = coroutineScope {
        launch {
            st.info = withContext(Dispatchers.IO) { fetchIpInfo(ip) }
            st.infoDone = true
            st.rdns = reverseDns(ip)
        }
        launch {
            st.icmp = withContext(Dispatchers.IO) { icmpPing(ip) }
            st.icmpDone = true
        }
        launch {
            st.tcp = withContext(Dispatchers.IO) {
                val first = tcpPingStats(ip, 443)
                if (first.received > 0) first else tcpPingStats(ip, 80)
            }
            st.tcpDone = true
        }
        launch {
            st.ports = probePorts(ip)
            st.portsDone = true
        }
        launch {
            st.http = withContext(Dispatchers.IO) { probeHttp(ip, false) }
            st.https = withContext(Dispatchers.IO) { probeHttp(ip, true) }
            st.httpDone = true
        }
    }

    suspend fun runGlobalAll() = coroutineScope {
        val port = gPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: 443
        listOf(v4, v6).forEach { st ->
            val ip = st.resolved
            if (ip != null) {
                launch {
                    st.nodes = emptyList()
                    st.nodesDone = false
                    st.nodesFailed = false
                    val target = if (gKind == "tcp") "${hostForUrl(ip)}:$port" else ip
                    val ok = globalCheck(gKind, target) { st.nodes = it }
                    st.nodesFailed = !ok
                    st.nodesDone = true
                }
            }
        }
    }

    fun rerunGlobal() {
        if (v4.resolved == null && v6.resolved == null) return
        gJob?.cancel()
        gJob = scope.launch {
            gBusy = true
            try {
                runGlobalAll()
            } finally {
                gBusy = false
            }
        }
    }

    fun runTest() {
        val host = parseTarget(input)
        if (host.isBlank()) return
        job?.cancel()
        gJob?.cancel()
        gBusy = false
        v4.reset()
        v6.reset()
        error = null
        job = scope.launch {
            busy = true
            try {
                val (ip4, ip6) = withContext(Dispatchers.IO) { resolveTargets(host) }
                if (ip4 == null && ip6 == null) {
                    error = t("ipt_bad")
                    return@launch
                }
                v4.resolved = ip4
                v6.resolved = ip6
                selected = if (ip4 != null) 4 else 6
                coroutineScope {
                    if (ip4 != null) launch { runLocal(v4, ip4) }
                    if (ip6 != null) launch { runLocal(v6, ip6) }
                    launch { runGlobalAll() }
                }
            } finally {
                busy = false
            }
        }
    }

    fun testAddress(ip: String) {
        input = ip
        runTest()
    }

    val running = busy || gBusy

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(t("ipt_title"), style = MaterialTheme.typography.titleMedium)
        Text(t("ipt_hint"), style = MaterialTheme.typography.bodySmall)

        // Your own addresses (IPv4 and IPv6) with a re-check button.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(IconPin, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        t("ipt_my"),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (myLoading) {
                        CircularProgressIndicator(
                            Modifier
                                .padding(12.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton({ loadMyIps() }) {
                            Icon(IconRefresh, t("ipt_recheck"))
                        }
                    }
                }
                OwnIpRow(
                    "IPv4", my4, myLoading, t("ipt_na"),
                    onCopy = { clipboard.setText(AnnotatedString(it)) },
                    onTest = { testAddress(it) },
                    busy = running
                )
                OwnIpRow(
                    "IPv6", my6, myLoading, t("ipt_na"),
                    onCopy = { clipboard.setText(AnnotatedString(it)) },
                    onTest = { testAddress(it) },
                    busy = running
                )
                Text(
                    t("ipt_vpn_note"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, end = 10.dp)
                )
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
                    gJob?.cancel()
                    busy = false
                    gBusy = false
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

        error?.let { Text(it, color = BadColor) }

        val has4 = v4.resolved != null
        val has6 = v6.resolved != null
        if (has4 || has6) {
            val shown = if (selected == 6 && has6) 6 else if (has4) 4 else 6
            val cur = if (shown == 6) v6 else v4
            val curIp = cur.resolved ?: ""

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (has4) {
                    FilterChip(
                        selected = shown == 4,
                        onClick = { selected = 4 },
                        label = { Text("IPv4") }
                    )
                }
                if (has6) {
                    FilterChip(
                        selected = shown == 6,
                        onClick = { selected = 6 },
                        label = { Text("IPv6") }
                    )
                }
                if (has4 != has6) {
                    Text(
                        if (has4) t("ipt_no_v6") else t("ipt_no_v4"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                "${t("ipt_resolved")}: $curIp",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { clipboard.setText(AnnotatedString(curIp)) }
            )

            SectionCard(IconActivity, t("ipt_global"), !cur.nodesDone) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = gKind == "ping",
                        enabled = !running,
                        onClick = {
                            gKind = "ping"
                            rerunGlobal()
                        },
                        label = { Text(t("ipt_ping")) }
                    )
                    FilterChip(
                        selected = gKind == "tcp",
                        enabled = !running,
                        onClick = {
                            gKind = "tcp"
                            rerunGlobal()
                        },
                        label = { Text(t("ipt_g_tcp")) }
                    )
                    if (gKind == "tcp") {
                        OutlinedTextField(
                            value = gPort,
                            onValueChange = { gPort = it.filter { c -> c.isDigit() }.take(5) },
                            label = { Text(t("port")) },
                            singleLine = true,
                            modifier = Modifier.width(100.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(onGo = { rerunGlobal() })
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (cur.nodesFailed && cur.nodes.none { it.done }) {
                    Text(t("ipt_g_err"), color = BadColor, style = MaterialTheme.typography.bodySmall)
                } else {
                    if (cur.nodes.isNotEmpty()) {
                        Text(
                            "${t("ipt_g_ok")}: ${cur.nodes.count { it.done && it.received > 0 }} / " +
                                "${cur.nodes.size}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    cur.nodes.forEach { NodeRow(it, fa) }
                }
            }

            SectionCard(IconServer, t("ipt_info"), !cur.infoDone) {
                val info = cur.info
                if (info == null && cur.infoDone) {
                    Text(t("ipt_info_err"), style = MaterialTheme.typography.bodySmall)
                }
                Row3(t("ipt_ip"), curIp, onClick = { clipboard.setText(AnnotatedString(curIp)) })
                Row3(t("ipt_type"), info?.type)
                Row3(t("ipt_country"), info?.country)
                Row3(t("ipt_region"), info?.region)
                Row3(t("ipt_city"), info?.city)
                Row3(t("ipt_isp"), info?.isp)
                Row3(t("ipt_org"), info?.org)
                Row3(t("ipt_asn"), info?.asn)
                Row3(t("ipt_tz"), info?.timezone)
                Row3(t("ipt_rdns"), cur.rdns)
            }

            SectionCard(IconActivity, t("ipt_local_ping"), !(cur.icmpDone && cur.tcpDone)) {
                PingBlock(t("ipt_icmp"), cur.icmp, cur.icmpDone, fa)
                Spacer(Modifier.height(6.dp))
                PingBlock(t("ipt_tcpping"), cur.tcp, cur.tcpDone, fa)
            }

            SectionCard(IconPlug, t("ipt_ports"), !cur.portsDone) {
                if (cur.portsDone) {
                    Text(
                        "${t("ipt_open_count")}: ${cur.ports.count { it.open }} / ${cur.ports.size}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                cur.ports.forEach { p ->
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

            SectionCard(IconCloud, t("ipt_http"), !cur.httpDone) {
                HttpBlock("HTTP :80", cur.http, fa)
                Spacer(Modifier.height(6.dp))
                HttpBlock("HTTPS :443", cur.https, fa)
                if (cur.https != null) {
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
private fun OwnIpRow(
    label: String,
    info: IpInfo?,
    loading: Boolean,
    naText: String,
    onCopy: (String) -> Unit,
    onTest: (String) -> Unit,
    busy: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(48.dp)
        )
        Column(
            Modifier
                .weight(1f)
                .clickable(enabled = info != null) { info?.let { onCopy(it.ip) } }
        ) {
            when {
                loading -> Text("...", style = MaterialTheme.typography.bodyLarge)
                info == null -> Text(
                    naText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> {
                    Text(info.ip, style = MaterialTheme.typography.bodyLarge)
                    val sub = listOfNotNull(info.country, info.isp ?: info.org)
                        .joinToString("  |  ")
                    if (sub.isNotEmpty()) {
                        Text(sub, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (info != null && !loading) {
            IconButton(onClick = { onTest(info.ip) }, enabled = !busy) {
                Icon(IconSearch, null, Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun NodeRow(node: GlobalNode, fa: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(32.dp)
                .height(22.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(node.cc.ifEmpty { "--" }, fontSize = 10.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOf(node.country, node.city).filter { it.isNotBlank() }.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium
            )
            if (node.done && node.received > 0) {
                Text(
                    "${ms(node.minMs)} / ${ms(node.avgMs)} / ${ms(node.maxMs)} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = latencyColor(node.avgMs)
                )
            } else if (node.done) {
                Text(
                    tr("ipt_g_fail", fa) + (node.error?.takeIf { it != "-" }?.let { " ($it)" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = BadColor
                )
            }
        }
        if (node.done) {
            Text(
                "${node.received} / ${node.sent}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (node.received > 0) OkColor else BadColor
            )
        } else {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
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

private fun ms(v: Double?): String = if (v == null) "-" else "%.1f".format(v)

@Composable
private fun PingBlock(title: String, stats: PingStats?, done: Boolean, fa: Boolean) {
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
