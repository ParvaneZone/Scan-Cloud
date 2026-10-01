package com.example.cfscanner.xray

import org.json.JSONArray
import org.json.JSONObject

object XrayConfigBuilder {
    fun build(config: ProxyConfig, scannedIp: String, socksPort: Int): JSONObject {
        val inbound = JSONObject()
            .put("listen", "127.0.0.1")
            .put("port", socksPort)
            .put("protocol", "socks")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", false))

        val outbound = JSONObject()
            .put("protocol", config.schemeName())
            .put("settings", outboundSettings(config, scannedIp))
            .put("streamSettings", streamSettings(config))

        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put("inbounds", JSONArray().put(inbound))
            .put("outbounds", JSONArray().put(outbound))
    }

    private fun ProxyConfig.schemeName(): String = when (scheme) {
        "ss", "shadowsocks" -> "shadowsocks"
        else -> scheme
    }

    private fun outboundSettings(config: ProxyConfig, scannedIp: String): JSONObject = when (config.scheme) {
        "vless" -> {
            val user = JSONObject()
                .put("id", config.id)
                .put("encryption", "none")
            config.flow?.takeIf { it.isNotBlank() }?.let { user.put("flow", it) }
            JSONObject().put(
                "vnext",
                JSONArray().put(
                    JSONObject()
                        .put("address", scannedIp)
                        .put("port", config.port)
                        .put("users", JSONArray().put(user))
                )
            )
        }

        "vmess" -> {
            val user = JSONObject()
                .put("id", config.id)
                .put("alterId", 0)
                .put("security", config.vmessSecurity)
            JSONObject().put(
                "vnext",
                JSONArray().put(
                    JSONObject()
                        .put("address", scannedIp)
                        .put("port", config.port)
                        .put("users", JSONArray().put(user))
                )
            )
        }

        "trojan" -> JSONObject().put(
            "servers",
            JSONArray().put(
                JSONObject()
                    .put("address", scannedIp)
                    .put("port", config.port)
                    .put("password", config.password)
            )
        )

        "ss", "shadowsocks" -> JSONObject()
            .put("address", scannedIp)
            .put("port", config.port)
            .put("method", config.method)
            .put("password", config.password)

        else -> error("Unsupported proxy scheme: ${config.scheme}")
    }

    private fun streamSettings(config: ProxyConfig): JSONObject {
        val stream = JSONObject().put("network", config.transport)
        if (config.security != "none") {
            stream.put("security", config.security)
        }

        val hostFallback = config.serverName ?: config.host ?: config.address

        when (config.transport) {
            "ws" -> {
                val ws = JSONObject()
                config.path?.let { ws.put("path", it) }
                ws.put("headers", JSONObject().put("Host", config.host ?: hostFallback))
                stream.put("wsSettings", ws)
            }

            "grpc" -> {
                val grpc = JSONObject()
                config.serviceName?.let { grpc.put("serviceName", it.trimStart('/')) }
                config.host?.takeIf { it.isNotBlank() }?.let { grpc.put("authority", it) }
                config.mode?.let { grpc.put("multiMode", it.equals("multi", true) || it == "1") }
                stream.put("grpcSettings", grpc)
            }

            "xhttp" -> {
                val xhttp = JSONObject()
                config.path?.let { xhttp.put("path", it) }
                xhttp.put("host", config.host ?: hostFallback)
                config.mode?.takeIf { it.isNotBlank() }?.let { xhttp.put("mode", it) }
                config.xhttpExtra?.let { xhttp.put("extra", JSONObject(it.toString())) }
                stream.put("xhttpSettings", xhttp)
            }

            "httpupgrade" -> {
                val hu = JSONObject()
                config.path?.let { hu.put("path", it) }
                hu.put("host", config.host ?: hostFallback)
                stream.put("httpupgradeSettings", hu)
            }
        }

        when (config.security) {
            "tls" -> {
                val tls = JSONObject()
                    .put("serverName", config.serverName ?: config.host ?: config.address)
                    .put("allowInsecure", config.allowInsecure)
                config.fingerprint?.takeIf { it.isNotBlank() }?.let { tls.put("fingerprint", it) }
                if (config.alpn.isNotEmpty()) {
                    tls.put("alpn", JSONArray(config.alpn))
                }
                stream.put("tlsSettings", tls)
            }

            "reality" -> {
                val reality = JSONObject()
                    .put("serverName", config.serverName ?: config.host ?: config.address)
                    .put("publicKey", config.publicKey)
                    .put("fingerprint", config.fingerprint?.takeIf { it.isNotBlank() } ?: "chrome")
                config.shortId?.takeIf { it.isNotBlank() }?.let { reality.put("shortId", it) }
                config.spiderX?.let { reality.put("spiderX", it) }
                stream.put("realitySettings", reality)
            }
        }

        return stream
    }
}
