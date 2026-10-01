package com.example.cfscanner

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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

val FASTLY_RANGES = listOf(
    "23.235.32.0/20", "43.249.72.0/22", "103.244.50.0/24", "103.245.222.0/23",
    "103.245.224.0/24", "104.156.80.0/20", "140.248.64.0/18", "140.248.128.0/17",
    "146.75.0.0/17", "151.101.0.0/16", "157.52.64.0/18", "167.82.0.0/17",
    "167.82.128.0/20", "167.82.160.0/20", "167.82.224.0/20", "172.111.64.0/18",
    "185.31.16.0/22", "199.27.72.0/21", "199.232.0.0/16"
)

val HTTPS_PORTS = listOf(443, 2053, 2083, 2087, 2096, 8443)
val HTTP_PORTS = listOf(80, 8080, 8880, 2052, 2082, 2086, 2095)

data class Result(
    val ip: String,
    val pingMs: Long,
    val mbps: Double?,
    val cfgMs: Long? = null,
    val cfgMbps: Double? = null
)

fun save(ctx: Context, uri: Uri, text: String) {
    ctx.contentResolver.openOutputStream(uri)?.use { output ->
        output.write(text.toByteArray())
    }
}

fun randomIps(cidr: String, n: Int): List<String> {
    val (baseAddress, prefix) = cidr.split("/")
    val base = baseAddress.split('.').fold(0L) { acc, part ->
        acc * 256 + part.toLong()
    }
    val size = 1L shl (32 - prefix.toInt())
    return List(n) {
        val value = base + Random.nextLong(1, size - 1)
        "${(value shr 24) and 255}.${(value shr 16) and 255}.${(value shr 8) and 255}.${value and 255}"
    }
}

fun tcpPing(ip: String, port: Int, timeoutMs: Int): Long? = try {
    val start = System.nanoTime()
    Socket().use { socket ->
        socket.connect(InetSocketAddress(ip, port), timeoutMs)
    }
    (System.nanoTime() - start) / 1_000_000
} catch (_: Exception) {
    null
}

fun speedTest(ip: String, port: Int, provider: Int): Double? = try {
    val https = port in HTTPS_PORTS
    val address = InetAddress.getByName(ip)
    val client = okhttp3.OkHttpClient.Builder()
        .dns { listOf(address) }
        .followRedirects(false)
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
    val target = if (provider == 0) {
        "speed.cloudflare.com" to "/__down?bytes=3000000"
    } else {
        "files.pythonhosted.org" to "/packages/source/p/pip/pip-24.0.tar.gz"
    }
    val url = "${if (https) "https" else "http"}://${target.first}:$port${target.second}"
    val start = System.nanoTime()
    var total = 0L
    client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful || response.body == null) {
            return@use
        }
        val stream = response.body!!.byteStream()
        val buffer = ByteArray(16_384)
        while (total < 3_000_000) {
            val read = stream.read(buffer)
            if (read < 0) {
                break
            }
            total += read
        }
    }
    val seconds = (System.nanoTime() - start) / 1e9
    if (total < 50_000 || seconds <= 0) null else total * 8 / 1e6 / seconds
} catch (_: Exception) {
    null
}

fun liveRanges(provider: Int): List<String> {
    val fallback = if (provider == 0) CF_RANGES else FASTLY_RANGES
    return try {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val url = if (provider == 0) {
            "https://www.cloudflare.com/ips-v4"
        } else {
            "https://api.fastly.com/public-ip-list"
        }
        val body = client.newCall(okhttp3.Request.Builder().url(url).build())
            .execute()
            .use { it.body?.string().orEmpty() }
        val list = if (provider == 0) {
            body.lines().map(String::trim)
        } else {
            JSONObject(body).getJSONArray("addresses").let { array ->
                List(array.length()) { index -> array.getString(index) }
            }
        }
        list.filter { Regex("\\d+\\.\\d+\\.\\d+\\.\\d+/\\d+").matches(it) }
            .ifEmpty { fallback }
    } catch (_: Exception) {
        fallback
    }
}

suspend fun scan(
    provider: Int,
    port: Int,
    perRange: Int,
    status: (String) -> Unit
): List<Result> = kotlinx.coroutines.coroutineScope {
    status("Loading ranges")
    val ranges = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        liveRanges(provider)
    }
    val ips = ranges.flatMap { randomIps(it, perRange) }.distinct()
    val done = AtomicInteger(0)
    val sem = kotlinx.coroutines.sync.Semaphore(150)
    val alive = ips.map { ip ->
        kotlinx.coroutines.async(kotlinx.coroutines.Dispatchers.IO) {
            sem.withPermit {
                val ping = tcpPing(ip, port, 1_500)
                status("Ping: ${done.incrementAndGet()}/${ips.size}")
                ping?.let { ip to it }
            }
        }
    }.awaitAll().filterNotNull().sortedBy { it.second }
    val out = mutableListOf<Result>()
    alive.take(20).forEachIndexed { index, (ip, ping) ->
        status("Speed: ${index + 1}/20")
        out += Result(
            ip = ip,
            pingMs = ping,
            mbps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                speedTest(ip, port, provider)
            }
        )
    }
    out + alive.drop(20).map { Result(it.first, it.second, null) }
}

fun fetchLatest(): Pair<String, String?>? = try {
    val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    client.newCall(
        okhttp3.Request.Builder()
            .url(RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .build()
    ).execute().use { response ->
        if (!response.isSuccessful || response.body == null) {
            null
        } else {
            val json = JSONObject(response.body!!.string())
            val assets = json.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val url = assets.getJSONObject(index).optString("browser_download_url")
                    if (url.endsWith(".apk")) {
                        apk = url
                        break
                    }
                }
            }
            json.getString("tag_name") to apk
        }
    }
} catch (_: Exception) {
    null
}

fun isNewer(remote: String, local: String): Boolean {
    fun parts(version: String): List<Int> = version.trim()
        .removePrefix("v")
        .split('.')
        .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    val remoteParts = parts(remote)
    val localParts = parts(local)
    for (index in 0 until maxOf(remoteParts.size, localParts.size)) {
        val remoteValue = remoteParts.getOrElse(index) { 0 }
        val localValue = localParts.getOrElse(index) { 0 }
        if (remoteValue != localValue) {
            return remoteValue > localValue
        }
    }
    return false
}

fun configForIp(raw: String, ip: String): String {
    val parsed = com.example.cfscanner.xray.ConfigParser.parse(raw) ?: return raw
    return when (parsed.scheme) {
        "vmess" -> {
            val encoded = raw.substringAfter("://", "").substringBefore('#')
            val decoded = runCatching {
                val normalized = encoded.replace('-', '+').replace('_', '/') +
                    "=".repeat((4 - encoded.length % 4) % 4)
                String(
                    android.util.Base64.decode(normalized, android.util.Base64.DEFAULT),
                    StandardCharsets.UTF_8
                )
            }.getOrNull() ?: return raw
            val json = runCatching { JSONObject(decoded) }.getOrNull() ?: return raw
            json.put("add", ip)
            val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
            "vmess://${android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)}"
        }
        "vless", "trojan" -> {
            val user = if (parsed.scheme == "vless") {
                parsed.id.orEmpty()
            } else {
                parsed.password.orEmpty()
            }
            val query = buildList {
                add("type=${parsed.transport}")
                add("security=${parsed.security}")
                parsed.serverName?.let { add("sni=${Uri.encode(it)}") }
                parsed.fingerprint?.let { add("fp=${Uri.encode(it)}") }
                parsed.alpn.takeIf { it.isNotEmpty() }?.let {
                    add("alpn=${Uri.encode(it.joinToString(","))}")
                }
                parsed.host?.let { add("host=${Uri.encode(it)}") }
                parsed.path?.let { add("path=${Uri.encode(it)}") }
                parsed.serviceName?.let { add("serviceName=${Uri.encode(it)}") }
                parsed.mode?.let { add("mode=${Uri.encode(it)}") }
                parsed.publicKey?.let { add("pbk=${Uri.encode(it)}") }
                parsed.shortId?.let { add("sid=${Uri.encode(it)}") }
                parsed.spiderX?.let { add("spx=${Uri.encode(it)}") }
                parsed.flow?.let { add("flow=${Uri.encode(it)}") }
                if (parsed.allowInsecure) {
                    add("allowInsecure=1")
                }
            }.joinToString("&")
            "${parsed.scheme}://$user@$ip:${parsed.port}?$query"
        }
        "ss", "shadowsocks" -> {
            val method = Uri.encode(parsed.method.orEmpty())
            val password = Uri.encode(parsed.password.orEmpty())
            "ss://$method:$password@$ip:${parsed.port}"
        }
        else -> raw
    }
}

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
