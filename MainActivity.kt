package com.example.cfscanner

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import org.json.JSONObject
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.random.Random

const val CHANNEL = "https://t.me/ParvaneZone"
const val DEV = "https://t.me/Parv49e"
const val RELEASES_API = "https://api.github.com/repos/ParvaneZone/Scan-Cloud/releases/latest"

val CF_RANGES = listOf(
    "173.245.48.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22",
    "141.101.64.0/18", "108.162.192.0/18", "190.93.240.0/20", "188.114.96.0/20",
    "197.234.240.0/22", "198.41.128.0/17", "162.158.0.0/15", "104.16.0.0/13",
    "104.24.0.0/14", "172.64.0.0/13", "131.0.72.0/22"
)
// Fallback lists; the app tries to download the official up-to-date lists first
val FASTLY_RANGES = listOf(
    "23.235.32.0/20", "43.249.72.0/22", "103.244.50.0/24", "103.245.222.0/23", "103.245.224.0/24",
    "104.156.80.0/20", "140.248.64.0/18", "140.248.128.0/17", "146.75.0.0/17", "151.101.0.0/16",
    "157.52.64.0/18", "167.82.0.0/17", "167.82.128.0/20", "167.82.160.0/20", "167.82.224.0/20",
    "172.111.64.0/18", "185.31.16.0/22", "199.27.72.0/21", "199.232.0.0/16"
)
val HTTPS_PORTS = listOf(443, 2053, 2083, 2087, 2096, 8443)
val HTTP_PORTS = listOf(80, 8080, 8880, 2052, 2082, 2086, 2095)

data class Result(val ip: String, val pingMs: Long, val mbps: Double?, val cfgMs: Long? = null)

// ---------- Strings (English, Persian) ----------
val S = mapOf(
    "title" to ("Cloudflare IP Scanner" to "اسکنر IP کلودفلر"),
    "dark" to ("Dark" to "تیره"),
    "port" to ("Port" to "پورت"),
    "scan_size" to ("Scan size (IPs tested)" to "حجم اسکن (تعداد IP تست‌شده)"),
    "quick" to ("Quick" to "سریع"),
    "normal" to ("Normal" to "معمولی"),
    "deep" to ("Deep" to "عمیق"),
    "start" to ("Start scan" to "شروع اسکن"),
    "stop" to ("Stop" to "توقف"),
    "ready" to ("Ready" to "آماده"),
    "stopped" to ("Stopped" to "متوقف شد"),
    "done" to ("Done (tap an item to copy)" to "تمام شد (برای کپی روی مورد بزنید)"),
    "q_out" to ("What should I do with the results?" to "با نتایج چه کنم؟"),
    "to_file" to ("Deliver as file" to "تحویل در فایل"),
    "show_only" to ("Just show" to "فقط نمایش"),
    "q_cfg" to ("Do you also want to test with your config?" to "با کانفیگ شما هم تست بگیرم؟"),
    "yes" to ("Yes" to "بله"),
    "no" to ("No" to "خیر"),
    "cfg_t" to ("Paste your config (vless/trojan, TLS, with ws, grpc or xhttp)" to "کانفیگ را بگذارید (vless یا trojan با TLS و ws، grpc یا xhttp)"),
    "test" to ("Test" to "تست"),
    "cancel" to ("Cancel" to "لغو"),
    "bad" to ("Config not valid. Reality and non-TLS configs are not supported here." to "کانفیگ معتبر نیست. کانفیگ Reality و بدون TLS اینجا پشتیبانی نمی‌شود."),
    "saved" to ("File saved" to "فایل ذخیره شد"),
    "none" to ("Nothing found" to "موردی پیدا نشد"),
    "found" to ("Results" to "نتایج"),
    "save_ips" to ("Save IPs file" to "ذخیرهٔ فایل IPها"),
    "save_cfgs" to ("Save all configs file" to "ذخیرهٔ فایل همهٔ کانفیگ‌ها"),
    "tab_cf" to ("IP Scan" to "اسکن IP"),
    "tab_sni" to ("SNI" to "SNI"),
    "tab_about" to ("About" to "درباره ما"),
    "sni_title" to ("SNI / Target Scanner" to "اسکنر SNI / تارگت"),
    "sni_hint" to ("Tests foreign sites from your phone's network: ping, response time, speed, TLS 1.3 and h2 (needed for Reality). Tap a domain to copy." to "سایت‌های خارجی را با اینترنت گوشی شما تست می‌کند: پینگ، زمان پاسخ، سرعت، TLS 1.3 و h2 (لازم برای Reality). برای کپی روی دامنه بزنید."),
    "extra" to ("Extra domains (optional)" to "دامنه‌های اضافه (اختیاری)"),
    "join_t" to ("Join our Telegram channel" to "به کانال تلگرام ما بپیوندید"),
    "join_msg" to ("Get updates and new versions of the app. Joining is optional." to "از آپدیت‌ها و نسخه‌های جدید برنامه باخبر شوید. عضویت اختیاری است."),
    "join" to ("Join channel" to "عضویت در کانال"),
    "later" to ("Skip for now" to "فعلاً نه"),
    "about_t" to ("About us" to "درباره ما"),
    "about_text" to (
        "Parvane Scanner is a free tool that finds the best Cloudflare IPs and the best SNI / target domains, based on the ping and speed of YOUR own internet connection.\n\n" +
        "• This app is released free and open source.\n" +
        "• Nobody has the right to sell this app or modified copies of it. If you paid for it, you were scammed.\n" +
        "• The app sends no data to the developer. All tests run on your phone.\n" +
        "• Results depend on your operator, time and network conditions, and are not guaranteed.\n" +
        "• Please use it responsibly and according to the laws of your country.\n\n" +
        "For suggestions or problems, message the developer with the button below." to
        "اسکنر پروانه یک ابزار رایگان برای پیدا کردن بهترین IPهای کلودفلر و بهترین SNI / تارگت است، بر اساس پینگ و سرعت اینترنت خودِ شما.\n\n" +
        "• این برنامه کاملاً رایگان و به صورت متن‌باز منتشر شده است.\n" +
        "• هیچ‌کس حق فروش این برنامه یا نسخه‌های تغییریافتهٔ آن را ندارد. اگر برای آن پول پرداخته‌اید، فریب خورده‌اید.\n" +
        "• برنامه هیچ داده‌ای برای سازنده ارسال نمی‌کند. همهٔ تست‌ها روی گوشی خودتان انجام می‌شود.\n" +
        "• نتایج به اپراتور، زمان و شرایط شبکهٔ شما بستگی دارد و تضمینی نیست.\n" +
        "• لطفاً مسئولانه و طبق قوانین کشور خود استفاده کنید.\n\n" +
        "برای پیشنهاد یا گزارش مشکل، با دکمهٔ زیر به سازنده پیام بدهید."),
    "check_update" to ("Check for updates" to "بررسی آپدیت"),
    "checking" to ("Checking..." to "در حال بررسی..."),
    "upd_none" to ("You have the latest version." to "شما آخرین نسخه را دارید."),
    "upd_new" to ("New version available:" to "نسخهٔ جدید موجود است:"),
    "upd_err" to ("Could not check for updates. Check your internet." to "بررسی آپدیت انجام نشد. اینترنت خود را چک کنید."),
    "upd_noapk" to ("A new version exists but it has no APK file yet." to "نسخهٔ جدید هست ولی هنوز فایل APK ندارد."),
    "update" to ("Update" to "آپدیت"),
    "downloading" to ("Downloading... the installer opens when it finishes." to "در حال دانلود... بعد از پایان، نصب‌کننده باز می‌شود."),
    "allow_install" to ("Allow this app to install updates in the settings that just opened, then press Update again." to "در تنظیماتی که باز شد اجازهٔ نصب آپدیت را بدهید و دوباره آپدیت را بزنید."),
    "ok" to ("OK" to "باشه"),
    "provider" to ("Network" to "شبکه"),
    "channel" to ("Telegram channel" to "کانال تلگرام"),
    "contact" to ("Message the developer on Telegram" to "پیام به سازنده در تلگرام")
)
fun tr(k: String, fa: Boolean) = S[k]!!.let { if (fa) it.second else it.first }
fun save(ctx: Context, uri: Uri, text: String) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }

// ---------- Cloudflare scanning ----------
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

fun speedTest(ip: String, port: Int, provider: Int): Double? = try {
    val https = port in HTTPS_PORTS
    val addr = InetAddress.getByName(ip)
    val client = OkHttpClient.Builder()
        .dns(object : Dns { override fun lookup(hostname: String) = listOf(addr) })
        .followRedirects(false)
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).build()
    val target = if (provider == 0) "speed.cloudflare.com" to "/__down?bytes=3000000"
                 else "files.pythonhosted.org" to "/packages/source/p/pip/pip-24.0.tar.gz"
    val url = "${if (https) "https" else "http"}://${target.first}:$port${target.second}"
    val t = System.nanoTime()
    var total = 0L
    client.newCall(Request.Builder().url(url).build()).execute().use { r ->
        if (!r.isSuccessful) return@use
        val s = r.body!!.byteStream(); val buf = ByteArray(16384)
        while (total < 3_000_000) { val n = s.read(buf); if (n < 0) break; total += n }
    }
    val sec = (System.nanoTime() - t) / 1e9
    if (total < 50_000) null else total * 8 / 1e6 / sec
} catch (e: Exception) { null }

// provider 0 = Cloudflare, 1 = Fastly. Downloads the official range list, falls back to the built-in one.
fun liveRanges(provider: Int): List<String> {
    val fallback = if (provider == 0) CF_RANGES else FASTLY_RANGES
    return try {
        val c = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()
        val url = if (provider == 0) "https://www.cloudflare.com/ips-v4" else "https://api.fastly.com/public-ip-list"
        val body = c.newCall(Request.Builder().url(url).build()).execute().use { it.body!!.string() }
        val list = if (provider == 0) body.lines().map { it.trim() }
                   else JSONObject(body).getJSONArray("addresses").let { a -> List(a.length()) { a.getString(it) } }
        list.filter { Regex("""\d+\.\d+\.\d+\.\d+/\d+""").matches(it) }.ifEmpty { fallback }
    } catch (e: Exception) { fallback }
}

suspend fun scan(provider: Int, port: Int, perRange: Int, status: (String) -> Unit): List<Result> = coroutineScope {
    status("Loading ranges...")
    val ranges = withContext(Dispatchers.IO) { liveRanges(provider) }
    val ips = ranges.flatMap { randomIps(it, perRange) }.distinct()
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
    alive.take(20).forEachIndexed { i, (ip, ping) ->
        status("Speed: ${i + 1}/20")
        out += Result(ip, ping, withContext(Dispatchers.IO) { speedTest(ip, port, provider) })
    }
    out + alive.drop(20).map { Result(it.first, it.second, null) }
}

// ---------- Update check (GitHub releases) ----------
fun fetchLatest(): Pair<String, String?>? = try {
    val c = OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    c.newCall(Request.Builder().url(RELEASES_API).header("Accept", "application/vnd.github+json").build()).execute().use { r ->
        if (!r.isSuccessful) null else {
            val j = JSONObject(r.body!!.string())
            val assets = j.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val u = assets.getJSONObject(i).optString("browser_download_url")
                if (u.endsWith(".apk")) { apk = u; break }
            }
            j.getString("tag_name") to apk
        }
    }
} catch (e: Exception) { null }

fun isNewer(remote: String, local: String): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").split(".").map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
    val a = parts(remote); val b = parts(local)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

// ---------- Config test ----------
data class Cfg(val raw: String, val scheme: String, val user: String, val port: Int,
               val type: String, val host: String, val sni: String, val path: String)

fun parseCfg(s: String): Cfg? = try {
    val u = s.trim()
    val scheme = u.substringBefore("://")
    if (scheme != "vless" && scheme != "trojan") null else {
        val body = u.substringAfter("://").substringBefore("#")
        val hp = body.substringAfter("@").substringBefore("?")
        val user = URLDecoder.decode(body.substringBefore("@"), "UTF-8")
        val m = body.substringAfter("?", "").split("&").filter { it.contains("=") }
            .associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }
        val type = (m["type"] ?: "tcp").lowercase()
        val sni = m["sni"] ?: m["host"] ?: ""
        val host = m["host"] ?: sni
        val path = if (type == "grpc") "/" + (m["serviceName"] ?: "") + "/Tun" else (m["path"] ?: "/")
        if (m["security"] != "tls" || sni.isEmpty() || type !in listOf("ws", "grpc", "xhttp", "splithttp")) null
        else Cfg(u, scheme, user, hp.substringAfterLast(":").toIntOrNull() ?: 443,
            if (type == "splithttp") "xhttp" else type, host, sni, if (path.startsWith("/")) path else "/$path")
    }
} catch (e: Exception) { null }

// Same config, but with the scanned IP (and the config's own port) in place of the original address
fun withIp(raw: String, ip: String, port: Int): String {
    val scheme = raw.substringBefore("://")
    val rest = raw.substringAfter("://")
    val user = rest.substringBefore("@")
    val tail = rest.substringAfter("@").dropWhile { it != '?' && it != '#' }
    val tag = if ('#' in tail) "$tail%20$ip" else "$tail#$ip"
    return "$scheme://$user@$ip:$port$tag"
}

// First bytes of a real VLESS / Trojan request that asks the server to open 1.1.1.1:80
fun tunnelHeader(c: Cfg): ByteArray? = try {
    if (c.scheme == "vless") {
        val id = c.user.replace("-", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        if (id.size != 16) null else byteArrayOf(0) + id + byteArrayOf(0, 1, 0, 80, 1, 1, 1, 1, 1)
    } else {
        val h = MessageDigest.getInstance("SHA-224").digest(c.user.toByteArray()).joinToString("") { "%02x".format(it) }
        (h + "\r\n").toByteArray() + byteArrayOf(1, 1, 1, 1, 1, 1, 0, 80) + "\r\n".toByteArray()
    }
} catch (e: Exception) { null }

// ws: full end-to-end test (the server must really proxy a request). grpc/xhttp: TLS + HTTP answer only.
fun cfgTest(ip: String, c: Cfg): Long? = try {
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
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).callTimeout(9, TimeUnit.SECONDS).build()
    val url = "https://${c.sni}:${c.port}${c.path}"
    val t0 = System.nanoTime()
    val ms = { (System.nanoTime() - t0) / 1_000_000 }
    if (c.type == "ws") {
        val header = tunnelHeader(c)
        val req = "GET / HTTP/1.1\r\nHost: 1.1.1.1\r\nConnection: close\r\n\r\n".toByteArray()
        val latch = CountDownLatch(1); val ok = AtomicBoolean(false); val got = ByteArrayOutputStream()
        client.newWebSocket(Request.Builder().url(url).header("Host", c.host).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (header == null) { ok.set(true); latch.countDown(); webSocket.cancel() }
                else webSocket.send((header + req).toByteString())
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                got.write(bytes.toByteArray())
                if (String(got.toByteArray(), Charsets.ISO_8859_1).contains("HTTP/")) { ok.set(true); latch.countDown(); webSocket.cancel() }
            }
            override fun onFailure(webSocket: WebSocket, th: Throwable, response: Response?) { latch.countDown() }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { latch.countDown() }
        })
        latch.await(9, TimeUnit.SECONDS)
        if (ok.get()) ms() else null
    } else {
        val b = Request.Builder().url(url).header("Host", c.host)
        val req = if (c.type == "grpc")
            b.header("TE", "trailers").post(ByteArray(5).toRequestBody("application/grpc".toMediaType())).build()
        else b.get().build()
        client.newCall(req).execute().use { r -> if (r.code < 500 && r.code != 403) ms() else null }
    }
} catch (e: Exception) { null }

suspend fun testCfg(list: List<Result>, c: Cfg, status: (String) -> Unit): List<Result> = coroutineScope {
    val cand = list.take(400)
    val sem = Semaphore(20); val done = AtomicInteger(0)
    cand.map { r ->
        async(Dispatchers.IO) {
            sem.withPermit {
                val ms = cfgTest(r.ip, c)
                status("Config: ${done.incrementAndGet()}/${cand.size}")
                ms?.let { r.copy(cfgMs = it) }
            }
        }
    }.awaitAll().filterNotNull().sortedBy { it.cfgMs }
}

// ---------- SNI / Reality target scanner ----------
val SNI_LIST = listOf(
    "www.microsoft.com", "www.apple.com", "icloud.com", "www.samsung.com", "www.amd.com", "www.nvidia.com",
    "www.intel.com", "www.cisco.com", "www.oracle.com", "www.ibm.com", "www.dell.com", "www.hp.com",
    "www.lenovo.com", "www.asus.com", "www.logitech.com", "www.sony.com", "www.speedtest.net",
    "addons.mozilla.org", "www.mozilla.org", "www.python.org", "nodejs.org", "www.docker.com", "github.com",
    "gitlab.com", "stackoverflow.com", "www.wikipedia.org", "www.bbc.com", "www.amazon.com", "aws.amazon.com",
    "www.ebay.com", "www.zoom.us", "www.adobe.com", "www.salesforce.com", "www.spotify.com", "www.tesla.com",
    "www.ubuntu.com", "www.debian.org", "www.kernel.org", "www.gnu.org", "www.mit.edu", "www.stanford.edu",
    "www.harvard.edu", "www.ted.com", "www.booking.com", "www.airbnb.com", "www.vmware.com", "www.shopify.com",
    "www.dropbox.com", "www.berkeley.edu", "www.cmu.edu", "www.ox.ac.uk", "www.cam.ac.uk", "www.nasa.gov",
    "www.nih.gov", "www.cdc.gov", "www.who.int", "www.un.org", "www.europa.eu", "www.nature.com",
    "www.science.org", "www.sciencedirect.com", "arxiv.org", "www.ieee.org", "www.acm.org", "www.reuters.com",
    "www.theguardian.com", "www.cnn.com", "www.nytimes.com", "www.bloomberg.com", "www.forbes.com",
    "www.wired.com", "www.theverge.com", "www.techcrunch.com", "www.cloudflare.com", "www.fastly.com",
    "www.akamai.com", "www.digitalocean.com", "www.linode.com", "www.vultr.com", "www.hetzner.com",
    "www.ovhcloud.com", "azure.microsoft.com", "cloud.google.com", "developer.apple.com",
    "developer.android.com", "developer.mozilla.org", "learn.microsoft.com", "docs.python.org", "pypi.org",
    "www.npmjs.com", "crates.io", "rubygems.org", "hub.docker.com", "www.jetbrains.com", "code.visualstudio.com",
    "www.postman.com", "www.atlassian.com", "slack.com", "www.notion.so", "www.figma.com", "www.canva.com",
    "www.mailchimp.com", "stripe.com", "www.paypal.com", "www.visa.com", "www.mastercard.com", "www.netflix.com",
    "www.hulu.com", "www.twitch.tv", "www.imdb.com", "www.goodreads.com", "medium.com", "www.quora.com",
    "www.reddit.com", "www.pinterest.com", "www.linkedin.com", "wordpress.com", "www.wix.com",
    "www.squarespace.com", "www.godaddy.com", "www.namecheap.com", "www.bestbuy.com", "www.walmart.com",
    "www.target.com", "www.costco.com", "www.ikea.com", "www.nike.com", "www.adidas.com", "www.toyota.com",
    "www.bmw.com", "www.mercedes-benz.com", "www.volkswagen.com", "www.ford.com", "www.honda.com",
    "www.philips.com", "www.siemens.com", "www.bosch.com", "www.panasonic.com", "www.canon.com",
    "www.nikon.com", "www.xiaomi.com", "www.huawei.com", "www.oneplus.com", "www.garmin.com",
    "www.synology.com", "www.qnap.com", "www.tp-link.com", "www.netgear.com", "www.mikrotik.com",
    "www.seagate.com", "www.westerndigital.com", "www.kingston.com", "www.corsair.com", "www.razer.com",
    "www.msi.com", "www.gigabyte.com"
)

data class SniRes(val host: String, val pingMs: Long, val totalMs: Long, val mbps: Double?, val tls13: Boolean, val h2: Boolean)

fun sniTest(host: String): SniRes? = try {
    val addr = InetAddress.getByName(host)
    val tp = System.nanoTime()
    Socket().use { it.connect(InetSocketAddress(addr, 443), 2500) }
    val ping = (System.nanoTime() - tp) / 1_000_000
    val client = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)).followRedirects(false)
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
    val t = System.nanoTime()
    client.newCall(Request.Builder().url("https://$host/").build()).execute().use { r ->
        val ttfb = (System.nanoTime() - t) / 1_000_000
        val tls13 = r.handshake?.tlsVersion == TlsVersion.TLS_1_3
        val h2 = r.protocol == Protocol.HTTP_2
        val t2 = System.nanoTime(); var total = 0L
        val s = r.body!!.byteStream(); val buf = ByteArray(16384)
        while (total < 500_000) { val n = s.read(buf); if (n < 0) break; total += n }
        val sec = (System.nanoTime() - t2) / 1e9
        SniRes(host, ping, ttfb, if (total > 50_000 && sec > 0) total * 8 / 1e6 / sec else null, tls13, h2)
    }
} catch (e: Exception) { null }

suspend fun scanSni(hosts: List<String>, status: (String) -> Unit): List<SniRes> = coroutineScope {
    val sem = Semaphore(10); val done = AtomicInteger(0)
    hosts.map { h ->
        async(Dispatchers.IO) { sem.withPermit { val r = sniTest(h); status("${done.incrementAndGet()}/${hosts.size}"); r } }
    }.awaitAll().filterNotNull()
        .sortedWith(compareByDescending<SniRes> { it.h2 && it.tls13 }.thenBy { it.pingMs + it.totalMs })
}

// ---------- Icons (SVG path data drawn as vectors) ----------
fun svgIcon(vararg d: String): ImageVector {
    val b = ImageVector.Builder("icon", 24.dp, 24.dp, 24f, 24f)
    try {
        d.forEach {
            b.addPath(PathParser().parsePathString(it).toNodes(), stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }
    } catch (e: Exception) { }
    return b.build()
}
val CIRCLE = "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z"
val IconMenu by lazy { svgIcon("M3 12h18", "M3 6h18", "M3 18h18") }
val IconCloud by lazy { svgIcon("M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z") }
val IconGlobe by lazy { svgIcon(CIRCLE, "M2 12h20", "M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z") }
val IconInfo by lazy { svgIcon(CIRCLE, "M12 16v-4", "M12 8h.01") }
val IconRefresh by lazy { svgIcon("M23 4v6h-6", "M1 20v-6h6", "M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15") }
val IconSend by lazy { svgIcon("M22 2L11 13", "M22 2l-7 20-4-9-9-4 20-7z") }
val IconUsers by lazy { svgIcon("M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2", "M9 3a4 4 0 1 0 0 8a4 4 0 1 0 0-8z", "M23 21v-2a4 4 0 0 0-3-3.87", "M16 3.13a4 4 0 0 1 0 7.75") }
val IconDownload by lazy { svgIcon("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5", "M12 15V3") }

// ---------- UI ----------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        setContent {
            var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
            var fa by remember { mutableStateOf(prefs.getBoolean("fa", true)) }
            // Shown on every launch until the user taps "Join"; "Skip" only hides it for this launch
            var showJoin by remember { mutableStateOf(!prefs.getBoolean("joined", false)) }
            val uri = LocalUriHandler.current
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides if (fa) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(Modifier.fillMaxSize()) {
                        ScannerScreen(fa, dark,
                            { fa = it; prefs.edit().putBoolean("fa", it).apply() },
                            { dark = it; prefs.edit().putBoolean("dark", it).apply() })
                        if (showJoin) AlertDialog(
                            onDismissRequest = { showJoin = false },
                            title = { Text(tr("join_t", fa)) },
                            text = { Text(tr("join_msg", fa)) },
                            confirmButton = {
                                TextButton({ prefs.edit().putBoolean("joined", true).apply(); showJoin = false; uri.openUri(CHANNEL) }) { Text(tr("join", fa)) }
                            },
                            dismissButton = { TextButton({ showJoin = false }) { Text(tr("later", fa)) } })
                    }
                }
            }
        }
    }
}

@Composable
fun ScannerScreen(fa: Boolean, dark: Boolean, onFa: (Boolean) -> Unit, onDark: (Boolean) -> Unit) {
    fun t(k: String) = tr(k, fa)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var tab by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var updMsg by remember { mutableStateOf("") }
    var updUrl by remember { mutableStateOf<String?>(null) }
    val cur = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0" } catch (e: Exception) { "0" } }

    fun checkUpdate() {
        updMsg = t("checking"); updUrl = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { fetchLatest() }
            if (r == null) updMsg = t("upd_err")
            else if (!isNewer(r.first, cur)) updMsg = t("upd_none")
            else if (r.second == null) updMsg = t("upd_noapk")
            else { updMsg = "${t("upd_new")} ${r.first}"; updUrl = r.second }
        }
    }
    fun startUpdate(url: String) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            updMsg = t("allow_install"); updUrl = null
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        updMsg = t("downloading"); updUrl = null
        scope.launch {
            try {
                val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val id = dm.enqueue(DownloadManager.Request(Uri.parse(url)).setTitle("Parvane Scanner")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, "update-${System.currentTimeMillis()}.apk"))
                while (true) {
                    val st = dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                        if (c.moveToFirst()) c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) else DownloadManager.STATUS_FAILED
                    }
                    if (st == DownloadManager.STATUS_SUCCESSFUL) {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(dm.getUriForDownloadedFile(id), "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                        updMsg = ""; return@launch
                    }
                    if (st == DownloadManager.STATUS_FAILED) throw Exception("failed")
                    delay(800)
                }
            } catch (e: Exception) { updMsg = ""; uri.openUri(url) }
        }
    }

    val url = updUrl
    if (updMsg.isNotEmpty()) AlertDialog(
        onDismissRequest = { updMsg = "" },
        title = { Text(t("check_update")) },
        text = { Text(updMsg) },
        confirmButton = {
            if (url != null) TextButton({ startUpdate(url) }) { Text(t("update")) }
            else TextButton({ updMsg = "" }) { Text(t("ok")) }
        },
        dismissButton = { if (url != null) TextButton({ updMsg = "" }) { Text(t("cancel")) } })

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton({ menu = true }) { Icon(IconMenu, null) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(t("check_update")) }, leadingIcon = { Icon(IconRefresh, null) },
                            onClick = { menu = false; checkUpdate() })
                        DropdownMenuItem(text = { Text("v$cur") }, onClick = {}, enabled = false)
                    }
                }
                Image(painterResource(R.drawable.logo), null, Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(8.dp))
                Text("Parvane Scanner", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(t("dark")); Spacer(Modifier.width(4.dp)); Switch(dark, onDark)
                TextButton({ onFa(!fa) }) { Text(if (fa) "English" else "فارسی") }
            }
        },
        bottomBar = {
            NavigationBar {
                listOf(IconCloud to "tab_cf", IconGlobe to "tab_sni", IconInfo to "tab_about").forEachIndexed { i, (ic, k) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, null) }, label = { Text(t(k)) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            // all tabs stay composed so a running scan is not lost when switching tabs
            for (i in 0..2) Box(if (tab == i) Modifier.fillMaxSize() else Modifier.size(0.dp).clipToBounds()) {
                when (i) {
                    0 -> CfTab(fa)
                    1 -> SniTab(fa)
                    else -> AboutTab(fa)
                }
            }
        }
    }
}

@Composable
fun CfTab(fa: Boolean) {
    fun t(k: String) = tr(k, fa)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var provider by remember { mutableStateOf(0) } // 0 Cloudflare, 1 Fastly
    var port by remember { mutableStateOf(443) }
    var perRange by remember { mutableStateOf(30) }
    var status by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var results by remember { mutableStateOf(listOf<Result>()) }
    var scanned by remember { mutableStateOf(listOf<Result>()) }
    var stage by remember { mutableStateOf(0) } // 1 ask output, 2 ask config, 3 enter config
    var toFile by remember { mutableStateOf(false) }
    var cfgText by remember { mutableStateOf("") }
    var cfgError by remember { mutableStateOf(false) }
    var cfgUsed by remember { mutableStateOf<Cfg?>(null) }
    val running = job?.isActive == true
    val rangeCount = if (provider == 0) CF_RANGES.size else FASTLY_RANGES.size

    val ipSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) { save(ctx, uri, results.joinToString("\n") { it.ip }); status = t("saved") }
    }
    val cfgSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val c = cfgUsed
        if (uri != null && c != null) { save(ctx, uri, results.joinToString("\n") { withIp(c.raw, it.ip, c.port) }); status = t("saved") }
    }
    fun finish(list: List<Result>, c: Cfg?) {
        results = list; cfgUsed = c
        status = if (list.isEmpty()) t("none") else t("done")
        if (toFile && list.isNotEmpty()) { if (c != null) cfgSaver.launch("configs.txt") else ipSaver.launch("ips.txt") }
    }

    if (stage == 1) AlertDialog(onDismissRequest = {}, title = { Text(t("q_out")) },
        confirmButton = { TextButton({ toFile = true; stage = 2 }) { Text(t("to_file")) } },
        dismissButton = { TextButton({ toFile = false; stage = 2 }) { Text(t("show_only")) } })
    if (stage == 2) AlertDialog(onDismissRequest = {}, title = { Text(t("q_cfg")) },
        confirmButton = { TextButton({ stage = 3 }) { Text(t("yes")) } },
        dismissButton = { TextButton({ stage = 0; finish(scanned, null) }) { Text(t("no")) } })
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
                    job = scope.launch { finish(testCfg(scanned, c) { s -> status = s }, c) }
                }
            }) { Text(t("test")) }
        },
        dismissButton = { TextButton({ stage = 0; finish(scanned, null) }) { Text(t("cancel")) } })

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("provider"))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Cloudflare", "Fastly").forEachIndexed { i, n ->
                FilterChip(selected = provider == i, enabled = !running, onClick = { provider = i; port = 443 }, label = { Text(n) })
            }
        }
        Text(t("port"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(if (provider == 0) HTTPS_PORTS + HTTP_PORTS else listOf(443, 80)) { p ->
                FilterChip(selected = port == p, enabled = !running, onClick = { port = p }, label = { Text("$p") })
            }
        }
        Text(t("scan_size"))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(10 to "quick", 30 to "normal", 80 to "deep").forEach { (n, k) ->
                FilterChip(selected = perRange == n, enabled = !running, onClick = { perRange = n },
                    label = { Text("${t(k)} (${n * rangeCount})") })
            }
        }
        Button(
            onClick = {
                if (running) { job?.cancel(); status = t("stopped") } else {
                    results = emptyList(); cfgUsed = null
                    job = scope.launch {
                        scanned = scan(provider, port, perRange) { s -> status = s }
                        results = scanned
                        if (scanned.isEmpty()) status = t("none") else stage = 1
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (running) t("stop") else t("start")) }
        if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(status.ifEmpty { t("ready") })
        if (results.isNotEmpty() && !running) {
            Text("${t("found")}: ${results.size}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ ipSaver.launch("ips.txt") }, Modifier.weight(1f)) {
                    Icon(IconDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("save_ips"))
                }
                if (cfgUsed != null) OutlinedButton({ cfgSaver.launch("configs.txt") }, Modifier.weight(1f)) {
                    Icon(IconDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("save_cfgs"))
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(results) { r ->
                Card(Modifier.fillMaxWidth().clickable { clipboard.setText(AnnotatedString(r.ip)) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.ip)
                        Text("${r.pingMs} ms  |  " + (r.mbps?.let { "%.1f Mbps".format(it) } ?: "-") +
                            (r.cfgMs?.let { "  |  config OK $it ms" } ?: ""))
                    }
                }
            }
        }
    }
}

@Composable
fun SniTab(fa: Boolean) {
    fun t(k: String) = tr(k, fa)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var extra by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    var results by remember { mutableStateOf(listOf<SniRes>()) }
    val running = job?.isActive == true
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val good = results.filter { it.h2 && it.tls13 }.ifEmpty { results }
            save(ctx, uri, good.joinToString("\n") { it.host }); status = t("saved")
        }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("sni_title"), style = MaterialTheme.typography.titleMedium)
        Text(t("sni_hint"), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(extra, { extra = it }, label = { Text(t("extra")) }, modifier = Modifier.fillMaxWidth(), maxLines = 3, enabled = !running)
        Button(
            onClick = {
                if (running) { job?.cancel(); status = t("stopped") } else {
                    results = emptyList()
                    val more = extra.lowercase().split(Regex("[\\s,]+")).map { it.removePrefix("https://").trimEnd('/') }.filter { it.contains(".") }
                    job = scope.launch {
                        results = scanSni((SNI_LIST + more).distinct()) { s -> status = s }
                        status = if (results.isEmpty()) t("none") else t("done")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (running) t("stop") else t("start")) }
        if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(status.ifEmpty { t("ready") })
        if (results.isNotEmpty() && !running) {
            Text("${t("found")}: ${results.size}")
            OutlinedButton({ saver.launch("reality_sni.txt") }, Modifier.fillMaxWidth()) {
                Icon(IconDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("to_file"))
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(results) { r ->
                Card(Modifier.fillMaxWidth().clickable { clipboard.setText(AnnotatedString(r.host)) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.host)
                        Text("${r.pingMs} ms  |  ${r.totalMs} ms  |  " + (r.mbps?.let { "%.1f Mbps".format(it) } ?: "-") +
                            (if (r.tls13) "  |  TLS1.3" else "") + (if (r.h2) "  |  h2" else ""))
                    }
                }
            }
        }
    }
}

@Composable
fun AboutTab(fa: Boolean) {
    fun t(k: String) = tr(k, fa)
    val uri = LocalUriHandler.current
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("about_t"), style = MaterialTheme.typography.titleMedium)
        Image(painterResource(R.drawable.logo), null, Modifier.size(120.dp).clip(RoundedCornerShape(20.dp)).align(Alignment.CenterHorizontally))
        Text(t("about_text"))
        OutlinedButton({ uri.openUri(CHANNEL) }, Modifier.fillMaxWidth()) {
            Icon(IconUsers, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("channel"))
        }
        Button({ uri.openUri(DEV) }, Modifier.fillMaxWidth().height(52.dp)) {
            Icon(IconSend, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("contact"))
        }
    }
}
