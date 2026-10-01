package com.example.cfscanner.xray

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

const val XRAY_CONCURRENCY = 6
private const val XRAY_START_TIMEOUT_MS = 4_000L
private const val XRAY_REQUEST_TIMEOUT_MS = 9_000L
private const val XRAY_SPEED_BYTES = 262_144L
private const val XRAY_BIND_RETRIES = 3


data class XrayTestResult(
    val latencyMs: Long,
    val mbps: Double?
)

class XrayRunner(
    private val filesDir: File,
    private val nativeLibraryDir: String
) {
    private val semaphore = Semaphore(XRAY_CONCURRENCY)
    private val binary = File(nativeLibraryDir, "libxray.so")
    private val testDirectory = File(filesDir, "xray-tests")


    fun isAvailable(): Boolean = binary.isFile && binary.canExecute()

    suspend fun test(
        config: ProxyConfig,
        scannedIp: String,
        measureSpeed: Boolean = false
    ): XrayTestResult? = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        if (!isAvailable()) {
            return@withContext null
        }
        semaphore.withPermit {
            testOne(config, scannedIp, measureSpeed)
        }
    }

    suspend fun testMany(
        candidates: List<String>,
        config: ProxyConfig,
        measureSpeed: Boolean = false,
        status: (Int, Int) -> Unit
    ): Map<String, XrayTestResult> = coroutineScope {
        val total = candidates.size
        val done = AtomicInteger(0)
        candidates.map { ip ->
            async(Dispatchers.IO) {
                try {
                    ip to test(config, ip, measureSpeed)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    ip to null
                } finally {
                    status(done.incrementAndGet(), total)
                }
            }
        }.mapNotNull { deferred ->
            val pair = deferred.await()
            pair.second?.let { pair.first to it }
        }.toMap()
    }

    private suspend fun testOne(
        config: ProxyConfig,
        scannedIp: String,
        measureSpeed: Boolean
    ): XrayTestResult? {
        testDirectory.mkdirs()

        repeat(XRAY_BIND_RETRIES) { attempt ->
            coroutineContext.ensureActive()
            val socksPort = reservePort()
            val configFile = File(
                testDirectory,
                "test-${System.nanoTime()}-${Random.nextInt(100000)}.json"
            )
            configFile.writeText(
                XrayConfigBuilder.build(config, scannedIp, socksPort).toString(),
                Charsets.UTF_8
            )

            var process: Process? = null
            var outputThread: Thread? = null
            var proxyStarted = false
            try {
                process = ProcessBuilder(
                    binary.absolutePath,
                    "run",
                    "-c",
                    configFile.absolutePath
                )
                    .redirectErrorStream(true)
                    .start()
                outputThread = drain(process.inputStream)

                val started = waitForPort(
                    process = process,
                    port = socksPort,
                    timeoutMs = XRAY_START_TIMEOUT_MS
                )

                if (!started) {
                    if (attempt + 1 < XRAY_BIND_RETRIES) {
                        return@repeat
                    }
                    return null
                }
                proxyStarted = true

                coroutineContext.ensureActive()
                val proxy = Proxy(
                    Proxy.Type.SOCKS,
                    InetSocketAddress("127.0.0.1", socksPort)
                )
                val client = OkHttpClient.Builder()
                    .proxy(proxy)
                    .followRedirects(false)
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .writeTimeout(5, TimeUnit.SECONDS)
                    .callTimeout(XRAY_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build()

                val startedAt = System.nanoTime()
                val request = Request.Builder()
                    .url("https://cp.cloudflare.com/generate_204")
                    .header("Cache-Control", "no-cache")
                    .build()
                val response = executeCancellable(client, request)
                response.use {
                    if (it.code !in 200..399) {
                        return null
                    }
                    val latency = (System.nanoTime() - startedAt) / 1_000_000
                    val speed = if (measureSpeed) measureDownload(client) else null
                    return XrayTestResult(latency, speed)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                if (proxyStarted) {
                    return null
                }
                if (attempt + 1 >= XRAY_BIND_RETRIES) {
                    return null
                }
            } finally {
                process?.let(::killProcess)
                outputThread?.interrupt()
                runCatching { configFile.delete() }
            }
        }

        return null
    }

    private fun reservePort(): Int = ServerSocket(0).use { it.localPort }

    private suspend fun waitForPort(
        process: Process,
        port: Int,
        timeoutMs: Long
    ): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            coroutineContext.ensureActive()
            if (!process.isAlive) {
                return false
            }
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 200)
                }
                return true
            } catch (_: Throwable) {
                delay(50)
            }
        }
        return false
    }

    private fun drain(input: java.io.InputStream): Thread = Thread {
        runCatching {
            input.bufferedReader(Charsets.UTF_8).forEachLine { }
        }
    }.apply {
        name = "xray-output"
        isDaemon = true
        start()
    }

    private fun killProcess(process: Process) {
        runCatching { process.outputStream.close() }
        runCatching { process.destroy() }
        runCatching {
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
            }
        }
        runCatching { process.waitFor(500, TimeUnit.MILLISECONDS) }
    }

    private suspend fun measureDownload(client: OkHttpClient): Double? {
        val request = Request.Builder()
            .url("https://speed.cloudflare.com/__down?bytes=$XRAY_SPEED_BYTES")
            .header("Cache-Control", "no-cache")
            .build()
        val started = System.nanoTime()
        val response = executeCancellable(client, request)
        response.use { r ->
            if (!r.isSuccessful || r.body == null) {
                return null
            }
            val input = r.body!!.byteStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (total < XRAY_SPEED_BYTES) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) {
                    break
                }
                total += read
            }
            val seconds = (System.nanoTime() - started) / 1_000_000_000.0
            return if (total < 32_768 || seconds <= 0.0) {
                null
            } else {
                total * 8.0 / 1_000_000.0 / seconds
            }
        }
    }

    private suspend fun executeCancellable(
        client: OkHttpClient,
        request: Request
    ): okhttp3.Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        Thread {
            try {
                val response = call.execute()
                if (continuation.isActive) {
                    continuation.resume(response)
                } else {
                    response.close()
                }
            } catch (throwable: Throwable) {
                if (continuation.isActive) {
                    continuation.resumeWithException(throwable)
                }
            }
        }.apply {
            name = "xray-http"
            isDaemon = true
            start()
        }
    }

    companion object {
        fun cleanupLeftovers(filesDir: File) {
            val directory = File(filesDir, "xray-tests")
            directory.listFiles()?.forEach { file ->
                runCatching { file.deleteRecursively() }
            }
            runCatching { directory.mkdirs() }
        }
    }
}
