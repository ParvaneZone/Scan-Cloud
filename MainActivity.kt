package com.example.cfscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLDecoder
import java.security.cert.X509Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.random.Random

val CF_RANGES = listOf(
    "173.245.48.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22",
    "141.101.64.0/18", "108.162.192.0/18", "190.93.240.0/20", "188.114.96.0/20",
    "197.234.240.0/22", "198.41.128.0/17", "162.158.0.0/15", "104.16.0.0/13",
    "104.24.0.0/14", "172.64.0.0/13", "131.0.72.0/22"
)
val HTTPS_PORTS = listOf(443, 2053, 2083, 2087, 2096, 8443)
val HTTP_PORTS = listOf(80, 8080, 8880, 2052, 2082, 2086, 2095)

data class Result(val ip: String, val pingMs: Long, val mbps: Double?, val cfgMs: Long? = null)

// ---------- Strings (English, Persian) ----------
val S = mapOf(
    "title" to ("Cloudflare IP Scanner" to "اسکنر IP کلودفلر"),
    "dark" to ("Dark" to "تیره"),
    "port" to ("Port" to "پورت"),
    "samples" to ("Samples per range" to "تعداد نمونه از هر رنج"),
    "start" to ("Start scan" to "شروع اسکن"),
    "stop" to ("Stop" to "توقف"),
    "ready" to ("Ready" to "آماده"),
    "stopped" to ("Stopped" to "متوقف شد"),
    "done" to ("Done (tap an IP to copy)" to "تمام شد (برای کپی روی IP بزنید)"),
    "q_out" to ("What should I do with the results?" to "با نتایج چه کنم؟"),
    "to_file" to ("Deliver as file" to "تحویل در فایل"),
    "show_only" to ("Just show" to "فقط نمایش"),
    "q_cfg" to ("Do you also want to test with your config?" to "با کانفیگ شما هم تست بگیرم؟"),
    "yes" to ("Yes" to "بله"),
    "no" to ("No" to "خیر"),
    "cfg_t" to ("Paste your config (vless/trojan with ws, grpc or xhttp)" to "کانفیگ را بگذارید (vless یا trojan با ws، grpc یا xhttp)"),
    "test" to ("Test" to "تست"),
    "cancel" to ("Cancel" to "لغو"),
    "bad" to ("Config not valid or type not supported" to "کانفیگ معتبر نیست یا نوعش پشتیبانی نمی‌شود"),
    "saved" to ("File saved" to "فایل ذخیره شد"),
    "none" to ("No working IP found" to "IP سالمی پیدا نشد")
)

// ---------- Scanning ----------
fun randomIps(cidr: String, n: Int): List<String> {
    val (b, p) = cidr.split("/")
    val base = b.split(".").fold(0L) { a, s -> a * 256 + s.toLong() }
    val size = 1L shl (32 - p.toInt())
    return List(n) {
        val v = base + Random.nextLong(1, size - 1)
        "${(v shr 24) and 255}.${(v shr 16) and 255}.${(v shr 8) and 255}.${v and 255}"
    }
}

fun tcpPing(ip: String, port: Int, timeoutMs: Int): Long? = try {
    val t = System.nanoTime()
    Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs) }
    (System.nanoTime() - t) / 1_000_000
} catch (e: Exception) { null }

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
    val alive = ips.map { ip ->
        async(Dispatchers.IO) {
            sem.withPermit {
                val p = tcpPing(ip, port, 1500)
                status("Ping: ${done.incrementAndGet()}/${ips.size}")
                p?.let { ip to it }
            }
        }
    }.awaitAll().filterNotNull().sortedBy { it.second }
    val out = mutableListOf<Result>()
    alive.take(10).forEachIndexed { i, (ip, ping) ->
        status("Speed: ${i + 1}/10")
        out += Result(ip, ping, withContext(Dispatchers.IO) { speedTest(ip, port) })
    }
    out + alive.drop(10).map { Result(it.first, it.second, null) }
}

// ---------- Config test ----------
data class Cfg(val type: String, val host: String, val sni: String, val path: String)

fun parseCfg(s: String): Cfg? = try {
    val u = s.trim()
    if (!u.startsWith("vless://") && !u.startsWith("trojan://")) null else {
        val q = u.substringAfter("?", "").substringBefore("#")
        val m = q.split("&").filter { it.contains("=") }
            .associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }
        val type = (m["type"] ?: "tcp").lowercase()
        val sni = m["sni"] ?: m["host"] ?: ""
        val host = m["host"] ?: sni
        val path = if (type == "grpc") "/" + (m["serviceName"] ?: "") + "/Tun" else (m["path"] ?: "/")
        if (sni.isEmpty() || type !in listOf("ws", "grpc", "xhttp", "splithttp")) null
        else Cfg(if (type == "splithttp") "xhttp" else type, host, sni, if (path.startsWith("/")) path else "/$path")
    }
} catch (e: Exception) { null }

// Connects to the scanned IP:port, uses the config's SNI/Host/path, returns ms if the server answers correctly
fun cfgTest(ip: String, port: Int, c: Cfg): Long? = try {
    val addr = InetAddress.getByName(ip)
    val tm = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
    val ctx = SSLContext.getInstance("TLS"); ctx.init(null, arrayOf(tm), null)
    val client = OkHttpClient.Builder()
        .dns(object : Dns { override fun lookup(hostname: String) = listOf(addr) })
        .sslSocketFactory(ctx.socketFactory, tm).hostnameVerifier { _, _ -> true }
        .followRedirects(false)
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
    val url = "https://${c.sni}:$port${c.path}"
    val t = System.nanoTime()
    val ms = { (System.nanoTime() - t) / 1_000_000 }
    if (c.type == "ws") {
        val latch = CountDownLatch(1); val ok = AtomicBoolean(false)
        client.newWebSocket(Request.Builder().url(url).header("Host", c.host).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { ok.set(true); latch.countDown(); webSocket.cancel() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { latch.countDown() }
        })
        latch.await(8, TimeUnit.SECONDS)
        if (ok.get()) ms() else null
    } else {
        val b = Request.Builder().url(url).header("Host", c.host)
        val req = if (c.type == "grpc")
            b.header("TE", "trailers").post(ByteArray(5).toRequestBody("application/grpc".toMediaType())).build()
        else b.get().build()
        client.newCall(req).execute().use { r -> if (r.code < 500 && r.code != 403) ms() else null }
    }
} catch (e: Exception) { null }

suspend fun testCfg(list: List<Result>, port: Int, c: Cfg, status: (String) -> Unit): List<Result> = coroutineScope {
    val cand = list.take(200)
    val sem = Semaphore(20); val done = AtomicInteger(0)
    cand.map { r ->
        async(Dispatchers.IO) {
            sem.withPermit {
                val ms = cfgTest(r.ip, port, c)
                status("Config: ${done.incrementAndGet()}/${cand.size}")
                ms?.let { r.copy(cfgMs = it) }
            }
        }
    }.awaitAll().filterNotNull().sortedBy { it.cfgMs }
}

// ---------- UI ----------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        setContent {
            var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
            var fa by remember { mutableStateOf(prefs.getBoolean("fa", false)) }
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides if (fa) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(Modifier.fillMaxSize()) {
                        ScannerScreen(fa, dark,
                            { fa = it; prefs.edit().putBoolean("fa", it).apply() },
                            { dark = it; prefs.edit().putBoolean("dark", it).apply() })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(fa: Boolean, dark: Boolean, onFa: (Boolean) -> Unit, onDark: (Boolean) -> Unit) {
    fun t(k: String) = S[k]!!.let { if (fa) it.second else it.first }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var port by remember { mutableStateOf(443) }
    var perRange by remember { mutableStateOf(20f) }
    var status by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var results by remember { mutableStateOf(listOf<Result>()) }
    var scanned by remember { mutableStateOf(listOf<Result>()) }
    var stage by remember { mutableStateOf(0) } // 1 ask output, 2 ask config, 3 enter config
    var toFile by remember { mutableStateOf(false) }
    var cfgText by remember { mutableStateOf("") }
    var cfgError by remember { mutableStateOf(false) }
    val running = job?.isActive == true

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            ctx.contentResolver.openOutputStream(uri)?.use { o -> o.write(results.joinToString("\n") { it.ip }.toByteArray()) }
            status = t("saved")
        }
    }
    fun finish(list: List<Result>) {
        results = list
        status = if (list.isEmpty()) t("none") else t("done")
        if (toFile && list.isNotEmpty()) saver.launch("cloudflare_ips.txt")
    }

    if (stage == 1) AlertDialog(onDismissRequest = {}, title = { Text(t("q_out")) },
        confirmButton = { TextButton({ toFile = true; stage = 2 }) { Text(t("to_file")) } },
        dismissButton = { TextButton({ toFile = false; stage = 2 }) { Text(t("show_only")) } })
    if (stage == 2) AlertDialog(onDismissRequest = {}, title = { Text(t("q_cfg")) },
        confirmButton = { TextButton({ stage = 3 }) { Text(t("yes")) } },
        dismissButton = { TextButton({ stage = 0; finish(scanned) }) { Text(t("no")) } })
    if (stage == 3) AlertDialog(onDismissRequest = {}, title = { Text(t("cfg_t")) },
        text = {
            Column {
                OutlinedTextField(cfgText, { cfgText = it; cfgError = false }, placeholder = { Text("vless://...") }, maxLines = 6)
                if (cfgError) Text(t("bad"), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton({
                val c = parseCfg(cfgText)
                if (c == null) cfgError = true else {
                    stage = 0
                    job = scope.launch { finish(testCfg(scanned, port, c) { s -> status = s }) }
                }
            }) { Text(t("test")) }
        },
        dismissButton = { TextButton({ stage = 0; finish(scanned) }) { Text(t("cancel")) } })

    Column(Modifier.padding(16.dp).statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(t("title"), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("dark")); Spacer(Modifier.width(4.dp)); Switch(dark, onDark)
                TextButton({ onFa(!fa) }) { Text(if (fa) "English" else "فارسی") }
            }
        }
        Text(t("port"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(HTTPS_PORTS + HTTP_PORTS) { p ->
                FilterChip(selected = port == p, enabled = !running, onClick = { port = p }, label = { Text("$p") })
            }
        }
        Text("${t("samples")}: ${perRange.toInt()}  (${perRange.toInt() * CF_RANGES.size})")
        Slider(perRange, { perRange = it }, valueRange = 5f..100f, enabled = !running)
        Button(
            onClick = {
                if (running) { job?.cancel(); status = t("stopped") } else {
                    results = emptyList()
                    job = scope.launch {
                        scanned = scan(port, perRange.toInt()) { s -> status = s }
                        results = scanned
                        status = ""
                        if (scanned.isEmpty()) status = t("none") else stage = 1
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (running) t("stop") else t("start")) }
        Text(status.ifEmpty { t("ready") })
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(results.take(100)) { r ->
                Card(Modifier.fillMaxWidth().clickable { clipboard.setText(AnnotatedString(r.ip)) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.ip)
                        Text("${r.pingMs} ms  |  " + (r.mbps?.let { "%.1f Mbps".format(it) } ?: "-") +
                            (r.cfgMs?.let { "  |  config ✓ $it ms" } ?: ""))
                    }
                }
            }
        }
    }
}
