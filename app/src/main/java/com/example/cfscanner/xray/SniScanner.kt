package com.example.cfscanner.xray

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

private const val SNI_CONCURRENCY = 8
private const val SNI_PREFILTER_TIMEOUT_MS = 4_000L
private const val SNI_XRAY_TIMEOUT_MS = 7_000L

data class SniResult(
    val host: String,
    val ip: String,
    val pingMs: Long,
    val xrayMs: Long,
    val handshakeMs: Long?,
    val tlsVersion: String?,
    val alpn: String?,
    val h2: Boolean,
    val mbps: Double?
)

class SniScanner(
    private val nativeLibraryDir: String,
    private val dohResolver: DohResolver = DohResolver()
) {
    private val binary = File(nativeLibraryDir, "libxray.so")
    private val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
    }

    suspend fun scan(
        hosts: List<String>,
        doh: DohProvider,
        status: (Int, Int) -> Unit
    ): List<SniResult> = coroutineScope {
        val uniqueHosts = hosts.distinct()
        val total = uniqueHosts.size
        val sem = Semaphore(SNI_CONCURRENCY)
        val done = AtomicInteger(0)
        uniqueHosts.map { host ->
            async(Dispatchers.IO) {
                sem.withPermit {
                    val result = scanOne(host, doh)
                    status(done.incrementAndGet(), total)
                    result
                }
            }
        }.awaitAll()
            .filterNotNull()
            .sortedWith(
                compareByDescending<SniResult> {
                    it.h2 && it.tlsVersion == "TLS 1.3"
                }.thenBy {
                    it.pingMs + (it.handshakeMs ?: it.xrayMs)
                }
            )
    }

    private suspend fun scanOne(host: String, doh: DohProvider): SniResult? = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val addresses = try {
            if (doh == DohProvider.SYSTEM) {
                dohResolver.resolveSystem(host)
            } else {
                dohResolver.resolve(host, doh)
            }
        } catch (_: Throwable) {
            emptyList()
        }
        val address = addresses
            .sortedBy { it.hostAddress?.contains(':') == true }
            .firstOrNull()
            ?: return@withContext null

        val prefilter = okHttpPrefilter(host, address) ?: return@withContext null
        coroutineContext.ensureActive()
        val xray = xrayPing(host, address.hostAddress ?: return@withContext null) ?: return@withContext null
        val handshake = tlsHandshake(host, address)
        SniResult(
            host = host,
            ip = address.hostAddress.orEmpty(),
            pingMs = prefilter.pingMs,
            xrayMs = xray.elapsedMs,
            handshakeMs = handshake?.elapsedMs,
            tlsVersion = normalizeTlsVersion(xray.tlsVersion ?: handshake?.tlsVersion),
            alpn = handshake?.alpn ?: if (prefilter.h2) "h2" else null,
            h2 = if (Build.VERSION.SDK_INT >= 29) {
                prefilter.h2 || handshake?.alpn == "h2"
            } else {
                prefilter.h2
            },
            mbps = prefilter.mbps
        )
    }

    private fun okHttpPrefilter(host: String, address: InetAddress): Prefilter? = try {
        val pingStart = System.nanoTime()
        Socket().use { it.connect(InetSocketAddress(address, 443), 2_500) }
        val pingMs = (System.nanoTime() - pingStart) / 1_000_000
        val client = OkHttpClient.Builder()
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<InetAddress> = listOf(address)
            })
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .followRedirects(false)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(SNI_PREFILTER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
        val start = System.nanoTime()
        client.newCall(Request.Builder().url("https://$host/").build()).execute().use { response ->
            val total = response.body?.bytes()?.size ?: 0
            val seconds = (System.nanoTime() - start) / 1_000_000_000.0
            val mbps = if (total > 50_000 && seconds > 0) {
                total * 8.0 / 1_000_000.0 / seconds
            } else {
                null
            }
            Prefilter(pingMs, response.protocol == Protocol.HTTP_2, mbps)
        }
    } catch (_: Exception) {
        null
    }

    private suspend fun xrayPing(host: String, ip: String): XrayPing? = suspendCancellableCoroutine { continuation ->
        if (!binary.isFile || !binary.canExecute()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val start = System.nanoTime()
        val process = try {
            ProcessBuilder(binary.absolutePath, "tls", "ping", "-ip", ip, host)
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val output = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                    output.append(line).append('\n')
                }
            }
        }.apply { isDaemon = true; name = "xray-tls-output"; start() }
        continuation.invokeOnCancellation {
            runCatching { process.destroy() }
            runCatching { process.destroyForcibly() }
            reader.interrupt()
        }
        Thread {
            try {
                val finished = process.waitFor(SNI_XRAY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                if (!finished) process.destroyForcibly()
                runCatching { reader.join(500) }
                val elapsed = (System.nanoTime() - start) / 1_000_000
                val text = output.toString()
                val result = if (finished && text.contains("Handshake succeeded")) {
                    val tls = Regex("TLS Version:\\s*(TLS [0-9.]+)")
                        .find(text)
                        ?.groupValues
                        ?.get(1)
                        ?.let(::normalizeTlsVersion)
                    XrayPing(elapsed, tls)
                } else null
                if (continuation.isActive) continuation.resume(result)
            } catch (t: Throwable) {
                if (continuation.isActive) continuation.resume(null)
            }
        }.apply { isDaemon = true; name = "xray-tls-wait"; start() }
    }

    private fun tlsHandshake(host: String, address: InetAddress): HandshakeResult? = try {
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustManager), null) }
        val start = System.nanoTime()
        Socket().use { raw ->
            raw.connect(InetSocketAddress(address, 443), 2_500)
            val ssl = context.socketFactory.createSocket(raw, host, 443, true) as SSLSocket
            ssl.use {
                val params = it.sslParameters
                params.serverNames = listOf(SNIHostName(host))
                if (Build.VERSION.SDK_INT >= 29) {
                    params.applicationProtocols = arrayOf("h2", "http/1.1")
                }
                it.sslParameters = params
                it.startHandshake()
                val state = it.session
                val alpn = if (Build.VERSION.SDK_INT >= 29) {
                    it.applicationProtocol
                } else {
                    null
                }
                HandshakeResult(
                    elapsedMs = (System.nanoTime() - start) / 1_000_000,
                    tlsVersion = normalizeTlsVersion(state.protocol),
                    alpn = alpn
                )
            }
        }
    } catch (_: Throwable) {
        null
    }

    private fun normalizeTlsVersion(value: String?): String? = when (value) {
        "TLSv1.3", "TLS 1.3" -> "TLS 1.3"
        "TLSv1.2", "TLS 1.2" -> "TLS 1.2"
        "TLSv1.1", "TLS 1.1" -> "TLS 1.1"
        "TLSv1", "TLS 1" -> "TLS 1.0"
        else -> value
    }

    private data class Prefilter(val pingMs: Long, val h2: Boolean, val mbps: Double?)
    private data class XrayPing(val elapsedMs: Long, val tlsVersion: String?)
    private data class HandshakeResult(val elapsedMs: Long, val tlsVersion: String?, val alpn: String?)
}
