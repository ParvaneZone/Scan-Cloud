package com.example.cfscanner.xray

import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Parsed subscription URI, normalized to the fields needed by Xray's client outbound. */
data class ProxyConfig(
    val raw: String,
    val scheme: String,
    val address: String,
    val port: Int,
    val id: String? = null,
    val password: String? = null,
    val method: String? = null,
    val transport: String = "tcp",
    val security: String = "none",
    val serverName: String? = null,
    val host: String? = null,
    val path: String? = null,
    val serviceName: String? = null,
    val mode: String? = null,
    val fingerprint: String? = null,
    val alpn: List<String> = emptyList(),
    val publicKey: String? = null,
    val shortId: String? = null,
    val spiderX: String? = null,
    val flow: String? = null,
    val allowInsecure: Boolean = false,
    val vmessSecurity: String = "auto",
    val xhttpExtra: JSONObject? = null
)

object ConfigParser {
    private val supportedSchemes = setOf("vless", "vmess", "trojan", "ss", "shadowsocks")
    private val supportedTransports = setOf("tcp", "ws", "grpc", "xhttp", "splithttp", "httpupgrade")
    private val supportedSecurity = setOf("none", "tls", "reality")

    fun parse(input: String): ProxyConfig? {
        val raw = input.trim().substringBefore('#').trim()
        if (raw.isEmpty()) return null
        val scheme = raw.substringBefore("://", "").lowercase()
        return when (scheme) {
            "vmess" -> parseVmess(raw)
            "ss", "shadowsocks" -> parseShadowsocks(raw)
            "vless", "trojan" -> parseUri(raw, scheme)
            else -> null
        }
    }

    private fun parseUri(raw: String, scheme: String): ProxyConfig? {
        val uri = try {
            URI(raw)
        } catch (_: Throwable) {
            return null
        }
        val userInfo = uri.rawUserInfo ?: return null
        val address = uri.host?.removeSurrounding("[", "]")
            ?: extractHost(uri.rawAuthority ?: return null)
        val port = if (uri.port > 0) uri.port else 443
        if (address.isNullOrBlank()) return null
        val user = decode(userInfo.substringBefore(':', userInfo))
        val password = if (scheme == "trojan") {
            decode(userInfo)
        } else {
            userInfo.substringAfter(':', missingDelimiterValue = "")
                .let(::decode)
                .ifBlank { null }
        }
        val params = queryMap(uri.rawQuery)
        val transport = normalizeTransport(params["type"] ?: params["net"] ?: "tcp")
        if (transport !in supportedTransports) return null
        val security = (params["security"] ?: if (params["pbk"] != null) "reality" else "none").lowercase()
        if (security !in supportedSecurity) return null

        val sni = first(params, "sni", "serverName")
        val host = first(params, "host", "authority")
        val path = normalizePath(params["path"])
        val serviceName = params["serviceName"]?.let(::decode)?.ifBlank { null }
        val alpn = splitAlpn(params["alpn"])
        val allowInsecure = params["allowInsecure"]?.let(::parseBoolean) ?: false
        val xhttpExtra = if (transport == "xhttp") {
            params["extra"]?.let { value -> runCatching { JSONObject(value) }.getOrNull() }
        } else {
            null
        }

        if (scheme == "vless" && user.isBlank()) return null
        if (scheme == "trojan" && password.isNullOrBlank()) return null
        if (security == "reality" && (params["pbk"].isNullOrBlank() || sni.isNullOrBlank())) return null

        return ProxyConfig(
            raw = raw,
            scheme = scheme,
            address = address,
            port = port,
            id = if (scheme == "vless") user else null,
            password = if (scheme == "trojan") password else null,
            transport = transport,
            security = security,
            serverName = sni,
            host = host,
            path = path,
            serviceName = serviceName,
            mode = params["mode"]?.lowercase(),
            fingerprint = params["fp"] ?: params["fingerprint"],
            alpn = alpn,
            publicKey = params["pbk"],
            shortId = params["sid"],
            spiderX = params["spx"]?.let(::decode),
            flow = params["flow"],
            allowInsecure = allowInsecure,
            vmessSecurity = params["scy"] ?: "auto",
            xhttpExtra = xhttpExtra
        )
    }

    private fun parseVmess(raw: String): ProxyConfig? {
        val encoded = raw.substringAfter("://", "").substringBefore('#').trim()
        if (encoded.isBlank()) return null
        val jsonText = decodeBase64(encoded) ?: return null
        val json = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null
        val address = json.optString("add").takeIf { it.isNotBlank() } ?: return null
        val port = json.optInt("port", 443).takeIf { it > 0 } ?: return null
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val transport = normalizeTransport(json.optString("net", "tcp"))
        if (transport !in supportedTransports) return null
        val tlsValue = json.optString("tls", "").lowercase()
        val reality = json.optString("security", "").lowercase() == "reality" || json.optString("pbk").isNotBlank()
        val security = when {
            reality -> "reality"
            tlsValue == "tls" || tlsValue == "https" -> "tls"
            else -> "none"
        }
        val serverName = json.optString("sni").ifBlank { json.optString("host") }.ifBlank { null }
        if (security == "reality" && (json.optString("pbk").isBlank() || serverName.isNullOrBlank())) return null
        return ProxyConfig(
            raw = raw,
            scheme = "vmess",
            address = address,
            port = port,
            id = id,
            transport = transport,
            security = security,
            serverName = serverName,
            host = json.optString("host").ifBlank { null },
            path = normalizePath(json.optString("path").ifBlank { null }),
            serviceName = json.optString("serviceName").ifBlank { null },
            mode = json.optString("mode").ifBlank { null },
            fingerprint = json.optString("fp").ifBlank { null },
            alpn = splitAlpn(json.optString("alpn").ifBlank { null }),
            publicKey = json.optString("pbk").ifBlank { null },
            shortId = json.optString("sid").ifBlank { null },
            spiderX = json.optString("spx").ifBlank { null },
            flow = json.optString("flow").ifBlank { null },
            allowInsecure = json.optBoolean("allowInsecure", false),
            vmessSecurity = json.optString("scy", "auto").ifBlank { "auto" },
            xhttpExtra = if (transport == "xhttp") json.optJSONObject("extra") else null
        )
    }

    private fun parseShadowsocks(raw: String): ProxyConfig? {
        val body = raw.substringAfter("://", "").substringBefore('#')
        if (body.isBlank()) return null
        val decodedWhole = decodeBase64(body)
        val plain = if (decodedWhole?.contains('@') == true) decodedWhole else body
        val at = plain.lastIndexOf('@')
        if (at <= 0) return null
        val credentials = plain.substring(0, at)
        val endpoint = plain.substring(at + 1)
        val method: String
        val password: String
        if (credentials.contains(':')) {
            method = decode(credentials.substringBefore(':'))
            password = decode(credentials.substringAfter(':'))
        } else {
            val decoded = decodeBase64(credentials) ?: return null
            method = decoded.substringBefore(':', "")
            password = decoded.substringAfter(':', "")
        }
        val endpointUri = runCatching { URI("ss://$endpoint") }.getOrNull() ?: return null
        val address = endpointUri.host ?: endpoint.substringBeforeLast(':')
        val port = if (endpointUri.port > 0) {
            endpointUri.port
        } else {
            endpoint.substringAfterLast(':', "443").toIntOrNull() ?: 443
        }
        if (address.isBlank() || method.isBlank() || password.isBlank()) return null
        return ProxyConfig(
            raw = raw,
            scheme = "ss",
            address = address,
            port = port,
            password = password,
            method = method,
            transport = "tcp",
            security = "none"
        )
    }

    private fun queryMap(rawQuery: String?): Map<String, String> = rawQuery.orEmpty()
        .split('&')
        .mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val key = decode(part.substringBefore('='))
            val value = decode(part.substringAfter('=', ""))
            key to value
        }
        .toMap()

    private fun first(map: Map<String, String>, vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        map[key]?.takeIf { it.isNotBlank() }
    }

    private fun normalizeTransport(value: String): String = when (value.lowercase()) {
        "splithttp", "split-http", "xhttp" -> "xhttp"
        "httpupgrade", "http-upgrade" -> "httpupgrade"
        "websocket" -> "ws"
        else -> value.lowercase()
    }

    private fun normalizePath(value: String?): String? {
        val path = value?.let(::decode)?.takeIf { it.isNotBlank() } ?: return null
        return if (path.startsWith('/')) path else "/$path"
    }

    private fun splitAlpn(value: String?): List<String> = value?.let(::decode)
        ?.split(',', ';')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun parseBoolean(value: String): Boolean =
        value == "1" || value.equals("true", true) || value.equals("yes", true)

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrDefault(value)

    private fun decodeBase64(value: String): String? {
        val normalized = value.replace('-', '+').replace('_', '/').let { it + "=".repeat((4 - it.length % 4) % 4) }
        return runCatching {
            String(Base64.decode(normalized, Base64.DEFAULT), StandardCharsets.UTF_8)
        }.getOrNull() ?: runCatching {
            String(Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun extractHost(authority: String): String? {
        val hostPort = authority.substringAfterLast('@')
        if (hostPort.startsWith("[")) {
            val end = hostPort.indexOf(']')
            if (end > 1) return hostPort.substring(1, end)
        }
        val firstColon = hostPort.indexOf(':')
        val lastColon = hostPort.lastIndexOf(':')
        return when {
            firstColon < 0 -> hostPort
            firstColon == lastColon -> hostPort.substring(0, lastColon)
            else -> hostPort
        }.takeIf { it.isNotBlank() }
    }
}
