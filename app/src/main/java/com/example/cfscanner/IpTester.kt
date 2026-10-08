package com.example.cfscanner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
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

fun fetchMyIp(): IpInfo? {
    fetchIpInfo(null)?.let { return it }
    val plain = httpGetText("https://api.ipify.org")?.trim()
    return if (!plain.isNullOrBlank() && plain.length < 46) IpInfo(plain) else null
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

fun resolveTarget(host: String): String? = try {
    if (host.isBlank()) null else InetAddress.getByName(host).hostAddress
} catch (_: Exception) {
    null
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
