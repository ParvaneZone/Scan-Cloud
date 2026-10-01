package com.example.cfscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

// Official Cloudflare IPv4 ranges (https://www.cloudflare.com/ips-v4)
val CF_RANGES = listOf(
    "173.245.48.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22",
    "141.101.64.0/18", "108.162.192.0/18", "190.93.240.0/20", "188.114.96.0/20",
    "197.234.240.0/22", "198.41.128.0/17", "162.158.0.0/15", "104.16.0.0/13",
    "104.24.0.0/14", "172.64.0.0/13", "131.0.72.0/22"
)

// Ports proxied by Cloudflare. HTTPS: 443, 2053, 2083, 2087, 2096, 8443 / HTTP: 80, 8080, 8880, 2052, 2082, 2086, 2095
val HTTPS_PORTS = listOf(443, 2053, 2083, 2087, 2096, 8443)
val HTTP_PORTS = listOf(80, 8080, 8880, 2052, 2082, 2086, 2095)

data class Result(val ip: String, val pingMs: Long, val mbps: Double?)

fun randomIps(cidr: String, n: Int): List<String> {
    val (b, p) = cidr.split("/")
    val base = b.split(".").fold(0L) { a, s -> a * 256 + s.toLong() }
    val size = 1L shl (32 - p.toInt())
    return List(n) {
        val v = base + Random.nextLong(1, size - 1)
        "${(v shr 24) and 255}.${(v shr 16) and 255}.${(v shr 8) and 255}.${v and 255}"
    }
}

// TCP connect time in ms (null = unreachable). Uses the phone's own active network.
fun tcpPing(ip: String, port: Int, timeoutMs: Int): Long? = try {
    val t = System.nanoTime()
    Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs) }
    (System.nanoTime() - t) / 1_000_000
} catch (e: Exception) { null }

// Download ~3MB from speed.cloudflare.com through the given edge IP:port
fun speedTest(ip: String, port: Int): Double? = try {
    val https = port in HTTPS_PORTS
    val addr = InetAddress.getByName(ip)
    val client = OkHttpClient.Builder()
        .dns(object : Dns { override fun lookup(hostname: String) = listOf(addr) })
        .followRedirects(false)
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
    val url = "${if (https) "https" else "http"}://speed.cloudflare.com:$port/__down?bytes=3000000"
    val t = System.nanoTime()
    var total = 0L
    client.newCall(Request.Builder().url(url).build()).execute().use { r ->
        if (!r.isSuccessful) return@use
        val s = r.body!!.byteStream(); val buf = ByteArray(16384)
        while (true) { val n = s.read(buf); if (n < 0) break; total += n }
    }
    val sec = (System.nanoTime() - t) / 1e9
    if (total == 0L) null else total * 8 / 1e6 / sec
} catch (e: Exception) { null }

suspend fun scan(port: Int, perRange: Int, status: (String) -> Unit): List<Result> = coroutineScope {
    val ips = CF_RANGES.flatMap { randomIps(it, perRange) }
    val done = AtomicInteger(0)
    val sem = Semaphore(150)
    // Phase 1: ping every sampled IP
    val alive = ips.map { ip ->
        async(Dispatchers.IO) {
            sem.withPermit {
                val p = tcpPing(ip, port, 1500)
                status("Ping: ${done.incrementAndGet()}/${ips.size}")
                p?.let { ip to it }
            }
        }
    }.awaitAll().filterNotNull().sortedBy { it.second }
    // Phase 2: speed test the 10 best (one at a time so they don't share bandwidth)
    val out = mutableListOf<Result>()
    alive.take(10).forEachIndexed { i, (ip, ping) ->
        status("Speed: ${i + 1}/10")
        out += Result(ip, ping, withContext(Dispatchers.IO) { speedTest(ip, port) })
    }
    out + alive.drop(10).take(20).map { Result(it.first, it.second, null) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { ScannerScreen() } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen() {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var port by remember { mutableStateOf(443) }
    var perRange by remember { mutableStateOf(20f) }
    var status by remember { mutableStateOf("Ready") }
    var job by remember { mutableStateOf<Job?>(null) }
    var results by remember { mutableStateOf(listOf<Result>()) }
    val running = job?.isActive == true

    Column(Modifier.padding(16.dp).statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cloudflare IP Scanner", style = MaterialTheme.typography.titleLarge)
        Text("Port")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(HTTPS_PORTS + HTTP_PORTS) { p ->
                FilterChip(selected = port == p, enabled = !running, onClick = { port = p }, label = { Text("$p") })
            }
        }
        Text("Samples per range: ${perRange.toInt()}  (total ${perRange.toInt() * CF_RANGES.size})")
        Slider(perRange, { perRange = it }, valueRange = 5f..100f, enabled = !running)
        Button(
            onClick = {
                if (running) { job?.cancel(); status = "Stopped" } else {
                    results = emptyList()
                    job = scope.launch {
                        results = scan(port, perRange.toInt()) { s -> status = s }
                        status = "Done: ${results.size} results (tap an IP to copy)"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (running) "Stop" else "Start scan") }
        Text(status)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(results) { r ->
                Card(Modifier.fillMaxWidth().clickable { clipboard.setText(AnnotatedString(r.ip)) }) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.ip)
                        Text("${r.pingMs} ms  |  " + (r.mbps?.let { "%.1f Mbps".format(it) } ?: "-"))
                    }
                }
            }
        }
    }
}
