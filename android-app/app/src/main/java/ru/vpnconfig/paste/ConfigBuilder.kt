package ru.vpnconfig.paste

import android.util.Base64

/**
 * Парсит ss:// ссылку и собирает JSON-конфиг для Xray/V2Ray.
 * Формат: ss://base64(method:password)@host:port#tag
 */
object ConfigBuilder {

    fun buildFromSsLink(ssLink: String): String? {
        val link = ssLink.trim().removePrefix("ss://")
        val hashIndex = link.indexOf('#')
        val main = if (hashIndex >= 0) link.substring(0, hashIndex) else link
        val atIndex = main.indexOf('@')
        if (atIndex <= 0) return null
        val userInfo = main.substring(0, atIndex)
        val hostPort = main.substring(atIndex + 1)
        val colon = hostPort.lastIndexOf(':')
        if (colon <= 0) return null
        val host = hostPort.substring(0, colon)
        val portStr = hostPort.substring(colon + 1).split("/").first().split("?").first()
        val port = portStr.toIntOrNull() ?: return null

        val decoded = try {
            String(Base64.decode(userInfo.replace("-", "+").replace("_", "/"), Base64.NO_WRAP or Base64.URL_SAFE))
        } catch (_: Exception) {
            return null
        }
        val methodPassword = decoded.split(":", limit = 2)
        if (methodPassword.size != 2) return null
        val method = methodPassword[0].trim()
        val password = methodPassword[1].trim()

        return buildConfig(host, port, method, password)
    }

    private fun buildConfig(host: String, port: Int, method: String, password: String): String {
        return """
{
  "log": { "loglevel": "warning" },
  "inbounds": [],
  "outbounds": [{
    "protocol": "shadowsocks",
    "settings": {
      "servers": [{
        "address": "$host",
        "port": $port,
        "method": "$method",
        "password": "$password"
      }]
    },
    "tag": "proxy"
  },{
    "protocol": "freedom",
    "settings": {},
    "tag": "direct"
  },{
    "protocol": "blackhole",
    "settings": {},
    "tag": "block"
  }],
  "routing": {
    "domainStrategy": "IPOnDemand",
    "rules": [
      { "type": "field", "network": "tcp,udp", "outboundTag": "proxy" }
    ]
  }
}
        """.trimIndent()
    }
}
