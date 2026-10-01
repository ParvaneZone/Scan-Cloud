package com.example.cfscanner.xray

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.Proxy
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DohResolver {
    private val client = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    fun resolve(host: String, provider: DohProvider): List<InetAddress> {
        val encodedHost = URLEncoder.encode(host, "UTF-8")
        val endpoint = when (provider) {
            DohProvider.CLOUDFLARE ->
                "https://1.1.1.1/dns-query?name=$encodedHost&type=A"

            DohProvider.GOOGLE ->
                "https://8.8.8.8/resolve?name=$encodedHost&type=A"

            DohProvider.SYSTEM -> return resolveSystemBlocking(host)
        }
        val request = Request.Builder()
            .url(endpoint)
            .header(
                "Host",
                if (provider == DohProvider.CLOUDFLARE) {
                    "cloudflare-dns.com"
                } else {
                    "dns.google"
                }
            )
            .header("Accept", "application/dns-json")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return emptyList()
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val answers = json.optJSONArray("Answer") ?: return emptyList()
            buildList {
                for (index in 0 until answers.length()) {
                    val value = answers.optJSONObject(index)
                        ?.optString("data")
                        .orEmpty()
                    runCatching { InetAddress.getByName(value) }
                        .getOrNull()
                        ?.takeIf { it.hostAddress?.contains(':') == false }
                        ?.let(::add)
                }
            }
        }
    }

    suspend fun resolveSystem(host: String): List<InetAddress> = suspendCancellableCoroutine { continuation ->
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "system-dns-resolver").apply { isDaemon = true }
        }
        val future = executor.submit(Callable { resolveSystemBlocking(host) })

        continuation.invokeOnCancellation {
            future.cancel(true)
            executor.shutdownNow()
        }

        Thread {
            try {
                val result = future.get(4, TimeUnit.SECONDS)
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            } catch (throwable: Throwable) {
                if (continuation.isActive) {
                    continuation.resumeWithException(throwable)
                }
            } finally {
                future.cancel(true)
                executor.shutdownNow()
            }
        }.apply {
            name = "system-dns-wait"
            isDaemon = true
            start()
        }
    }

    private fun resolveSystemBlocking(host: String): List<InetAddress> =
        InetAddress.getAllByName(host).toList()
}

enum class DohProvider {
    CLOUDFLARE,
    GOOGLE,
    SYSTEM
}
