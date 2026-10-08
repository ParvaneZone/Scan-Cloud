package com.example.cfscanner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

data class IpInfo(
    val ip: String,
    val type: String? = null,
    val country: String? = null,
    val region: String? = null,
    val city: String? = null,
    val isp: String? = null,
    val org: String? = null,
    val asn: String? = null,
    val timezone: String? = null
)

data class PingStats(
    val sent: Int,
    val received: Int,
    val minMs: Double?,
    val avgMs: Double?,
    val maxMs: Double?,
    val method: String
) {
    val lossPercent: Int get() = if (sent == 0) 100 else (sent - received) * 100 / sent
}

data class PortProbe(val port: Int, val label: String, val open: Boolean, val ms: Long?)

data class HttpProbe(
    val https: Boolean,
    val code: Int?,
    val ms: Long?,
    val server: String?,
    val location: String?,
    val protocol: String?,
    val tls: String?,
    val error: String?
)

val TEST_PORTS = listOf(
    21 to "FTP", 22 to "SSH", 25 to "SMTP", 53 to "DNS", 80 to "HTTP", 443 to "HTTPS",
    2052 to "HTTP", 2053 to "HTTPS", 2082 to "HTTP", 2083 to "HTTPS", 2086 to "HTTP",
    2087 to "HTTPS", 2095 to "HTTP", 2096 to "HTTPS", 3389 to "RDP", 8080 to "HTTP",
    8443 to "HTTPS", 8880 to "HTTP"
)

private val infoClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
}

private fun JSONObject.str(key: String): String? =
    optString(key, "").takeIf { it.isNotBlank() && it != "null" }

private fun httpGetText(url: String): String? = try {
    infoClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (response.isSuccessful) response.body?.string() else null
    }
} catch (_: Exception) {
    null
}

/** ip == null means "the caller's own address". */
fun fetchIpInfo(ip: String?): IpInfo? {
    val body = httpGetText("https://ipwho.is/" + (ip ?: "")) ?: return null
    return try {
        val o = JSONObject(body)
        if (!o.optBoolean("success", false)) return null
        val conn = o.optJSONObject("connection")
        val asn = conn?.optInt("asn", 0) ?: 0
        IpInfo(
            ip = o.str("ip") ?: return null,
            type = o.str("type"),
            country = o.str("country"),
            region = o.str("region"),
            city = o.str("city"),
            isp = conn?.str("isp"),
            org = conn?.str("org"),
            asn = if (asn > 0) "AS$asn" else null,
            timezone = o.optJSONObject("timezone")?.str("id")
        )
    } catch (_: Exception) {
        null
    }
}

private val V4_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

private fun cleanIp(text: String?, v6: Boolean): String? {
    val ip = text?.trim() ?: return null
    return if (v6) {
        ip.takeIf { it.contains(':') && it.length < 46 && !it.contains('<') }
    } else {
        ip.takeIf { V4_REGEX.matches(it) }
    }
}

/** Own public address of one family; null when that family has no connectivity. */
fun fetchOwnIp(v6: Boolean): IpInfo? {
    val host = if (v6) "api6.ipify.org" else "api.ipify.org"
    val ip = cleanIp(httpGetText("https://$host"), v6) ?: return null
    return fetchIpInfo(ip) ?: IpInfo(ip)
}

fun parseTarget(raw: String): String {
    var s = raw.trim().lowercase()
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .substringBefore('?')
    if (s.startsWith("[")) return s.substringAfter('[').substringBefore(']')
    if (s.count { it == ':' } == 1) s = s.substringBefore(':')
    return s
}

/** Returns the first IPv4 and the first IPv6 address of a host (either may be null). */
fun resolveTargets(host: String): Pair<String?, String?> = try {
    if (host.isBlank()) {
        null to null
    } else {
        val all = InetAddress.getAllByName(host)
        all.firstOrNull { it is Inet4Address }?.hostAddress to
            all.firstOrNull { it is Inet6Address }?.hostAddress?.substringBefore('%')
    }
} catch (_: Exception) {
    null to null
}

suspend fun reverseDns(ip: String): String? {
    // Reverse lookups can block for a long time and cannot be interrupted, so run detached.
    val job = CoroutineScope(Dispatchers.IO).async {
        try {
            InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip }
        } catch (_: Exception) {
            null
        }
    }
    return withTimeoutOrNull(4000) { job.await() }
}

/** Uses the system ping binary. Returns null when it cannot be executed at all. */
fun icmpPing(ip: String, count: Int = 4): PingStats? = try {
    val bin = if (ip.contains(':') && File("/system/bin/ping6").exists()) "ping6" else "ping"
    val process = ProcessBuilder(bin, "-c", "$count", "-W", "2", "-w", "12", ip)
        .redirectErrorStream(true)
        .start()
    val out = process.inputStream.bufferedReader().readText()
    process.waitFor()
    if (out.isBlank()) {
        null
    } else {
        val times = Regex("time[=<]\\s*([0-9.]+)").findAll(out)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .toList()
        PingStats(
            sent = count,
            received = times.size,
            minMs = times.minOrNull(),
            avgMs = if (times.isEmpty()) null else times.average(),
            maxMs = times.maxOrNull(),
            method = "ICMP"
        )
    }
} catch (_: Exception) {
    null
}

fun tcpPingStats(ip: String, port: Int, count: Int = 4): PingStats {
    val times = mutableListOf<Double>()
    repeat(count) {
        tcpPing(ip, port, 2500)?.let { times.add(it.toDouble()) }
    }
    return PingStats(
        sent = count,
        received = times.size,
        minMs = times.minOrNull(),
        avgMs = if (times.isEmpty()) null else times.average(),
        maxMs = times.maxOrNull(),
        method = "TCP $port"
    )
}

suspend fun probePorts(ip: String): List<PortProbe> = coroutineScope {
    TEST_PORTS.map { (port, label) ->
        async(Dispatchers.IO) {
            val ms = tcpPing(ip, port, 3000)
            PortProbe(port, label, ms != null, ms)
        }
    }.awaitAll().sortedWith(compareByDescending<PortProbe> { it.open }.thenBy { it.port })
}

// HTTPS to a bare IP never matches the certificate name, so this probe only reads the
// status line and headers and does not verify the certificate. No user data is sent.
private fun probeClient(https: Boolean): OkHttpClient {
    val builder = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
    if (https) {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf(trustAll), SecureRandom())
        builder.sslSocketFactory(ctx.socketFactory, trustAll)
        builder.hostnameVerifier { _, _ -> true }
    }
    return builder.build()
}

fun probeHttp(ip: String, https: Boolean): HttpProbe {
    val url = (if (https) "https://" else "http://") + hostForUrl(ip) + "/"
    return try {
        val start = System.nanoTime()
        probeClient(https).newCall(Request.Builder().url(url).build()).execute().use { r ->
            HttpProbe(
                https = https,
                code = r.code,
                ms = (System.nanoTime() - start) / 1_000_000,
                server = r.header("Server"),
                location = r.header("Location"),
                protocol = r.protocol.toString(),
                tls = r.handshake?.tlsVersion?.javaName,
                error = null
            )
        }
    } catch (e: Exception) {
        HttpProbe(https, null, null, null, null, null, null, e.javaClass.simpleName)
    }
}

data class GlobalNode(
    val id: String,
    val cc: String,
    val country: String,
    val city: String,
    val done: Boolean,
    val sent: Int = 0,
    val received: Int = 0,
    val minMs: Double? = null,
    val avgMs: Double? = null,
    val maxMs: Double? = null,
    val error: String? = null
)

private const val GLOBAL_API = "https://check-host.net"

private fun globalGet(path: String): JSONObject? = try {
    val request = Request.Builder()
        .url(GLOBAL_API + path)
        .header("Accept", "application/json")
        .build()
    infoClient.newCall(request).execute().use { r ->
        if (r.isSuccessful) r.body?.string()?.let { JSONObject(it) } else null
    }
} catch (_: Exception) {
    null
}

private fun parseGlobalNode(base: GlobalNode, root: JSONObject, kind: String): GlobalNode {
    if (root.isNull(base.id)) return base
    val v: JSONArray = root.optJSONArray(base.id) ?: return base.copy(done = true, error = "-")
    if (kind == "tcp") {
        val o = v.optJSONObject(0)
        val t = o?.optDouble("time", Double.NaN) ?: Double.NaN
        return if (o != null && !t.isNaN()) {
            base.copy(
                done = true, sent = 1, received = 1,
                minMs = t * 1000, avgMs = t * 1000, maxMs = t * 1000
            )
        } else {
            base.copy(done = true, sent = 1, received = 0, error = o?.optString("error") ?: "-")
        }
    }
    val inner = v.optJSONArray(0)
        ?: return base.copy(done = true, sent = 1, received = 0, error = "-")
    val times = mutableListOf<Double>()
    for (i in 0 until inner.length()) {
        val p = inner.optJSONArray(i) ?: continue
        if (p.optString(0) == "OK") {
            val t = p.optDouble(1, Double.NaN)
            if (!t.isNaN()) times.add(t * 1000)
        }
    }
    return base.copy(
        done = true,
        sent = inner.length(),
        received = times.size,
        minMs = times.minOrNull(),
        avgMs = if (times.isEmpty()) null else times.average(),
        maxMs = times.maxOrNull()
    )
}

/**
 * Runs a ping ("ping") or TCP ("tcp", target = host:port) test from servers in many countries.
 * onUpdate receives the full, sorted node list every time results arrive.
 * Returns false when the service could not be reached.
 */
suspend fun globalCheck(kind: String, target: String, onUpdate: (List<GlobalNode>) -> Unit): Boolean {
    val enc = URLEncoder.encode(target, "UTF-8")
    val start = withContext(Dispatchers.IO) {
        globalGet("/check-$kind?host=$enc&max_nodes=40")
    } ?: return false
    val requestId = start.optString("request_id", "")
    val nodesObj = start.optJSONObject("nodes")
    if (requestId.isBlank() || nodesObj == null) return false

    val meta = HashMap<String, JSONArray?>()
    val keys = nodesObj.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        meta[key] = nodesObj.optJSONArray(key)
    }

    fun base(id: String): GlobalNode {
        val m = meta[id]
        return GlobalNode(
            id = id,
            cc = (m?.optString(0, "") ?: "").uppercase(),
            country = m?.optString(1, "") ?: "",
            city = m?.optString(2, "") ?: "",
            done = false
        )
    }

    fun ordered(list: List<GlobalNode>) =
        list.sortedWith(compareBy({ it.country }, { it.city }, { it.id }))

    var nodes = ordered(meta.keys.map { base(it) })
    onUpdate(nodes)

    repeat(15) {
        delay(2000)
        val root = withContext(Dispatchers.IO) { globalGet("/check-result/$requestId") }
        if (root != null) {
            nodes = ordered(meta.keys.map { parseGlobalNode(base(it), root, kind) })
            onUpdate(nodes)
            if (nodes.all { it.done }) return true
        }
    }
    return nodes.any { it.done }
}
