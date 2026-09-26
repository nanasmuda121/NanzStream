package com.nanzstream.nanas.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nanzstream.nanas.NanzStreamApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ApiClient {

    private val gson = Gson()

    /**
     * Resilient Anti-Blocking DNS:
     * 1. In-memory cache for 0ms repeated lookups.
     * 2. System DNS with IPv4 priority, filtering out Indonesian ISP block IPs (Internet Positif / Uzone).
     * 3. Automatic DoH fallback (Cloudflare 1.1.1.1 / Google 8.8.8.8) when System DNS is poisoned or fails.
     * 4. Hardcoded static IPs for primary media scrapers as guaranteed safety net.
     */
    val resilientDns = object : Dns {
        private val cache = ConcurrentHashMap<String, List<InetAddress>>()

        private val staticFallbacks = mapOf(
            "animasu.love" to listOf("104.21.83.63", "172.67.214.247", "104.21.76.66", "172.67.190.239"),
            "www.animasu.love" to listOf("104.21.83.63", "172.67.214.247", "104.21.76.66", "172.67.190.239"),
            "bacakomik.my" to listOf("104.21.32.127", "172.67.151.249", "104.21.76.66", "172.67.190.239"),
            "www.bacakomik.my" to listOf("104.21.32.127", "172.67.151.249", "104.21.76.66", "172.67.190.239"),
            "vidhidepro.com" to listOf("104.21.57.125", "172.67.163.224", "104.21.76.66"),
            "vidhide.com" to listOf("104.21.57.125", "172.67.163.224", "104.21.76.66"),
            "odvidhide.com" to listOf("104.21.57.125", "172.67.163.224"),
            "dramiyos-cdn.com" to listOf("203.188.166.60", "203.188.166.71", "203.188.166.68"),
            "acek-cdn.com" to listOf("203.188.166.71", "203.188.166.60", "203.188.166.68"),
            "amatipolanyatirudesainnya.lol" to listOf("104.21.8.11", "172.67.156.156", "104.21.76.66"),
            "bukansiapasiapa.lol" to listOf("104.21.69.230", "172.67.215.109", "104.21.76.66"),
            "imageainewgeneration.lol" to listOf("104.21.10.207", "172.67.190.254", "104.21.76.66"),
            "himmga.lat" to listOf("104.21.50.252", "172.67.215.119", "104.21.76.66"),
            "gaimgame.pics" to listOf("104.21.30.204", "172.67.173.220", "104.21.76.66"),
            "samehadaku.li" to listOf("104.21.76.66", "172.67.190.239", "104.21.83.37", "172.67.211.38"),
            "www.samehadaku.li" to listOf("104.21.76.66", "172.67.190.239", "104.21.83.37", "172.67.211.38"),
            "anichin.ro" to listOf("104.21.76.66", "172.67.190.239"),
            "www.anichin.ro" to listOf("104.21.76.66", "172.67.190.239"),
            "anichin.site" to listOf("104.21.76.66", "172.67.190.239", "104.21.13.75"),
            "dracinema.com" to listOf("104.21.76.66", "172.67.190.239", "172.67.194.112"),
            "themoviebox.online" to listOf("103.224.182.189"),
            "player.abyssplayer.com" to listOf("172.67.178.198", "104.21.56.60"),
            "dl.berkasdrive.com" to listOf("172.67.175.145", "104.21.83.118"),
            "i0.wp.com" to listOf("192.0.77.2"),
            "i1.wp.com" to listOf("192.0.77.2"),
            "i2.wp.com" to listOf("192.0.77.2"),
            "i3.wp.com" to listOf("192.0.77.2")
        )

        private fun createAddress(hostname: String, ip: String): InetAddress? {
            return try {
                val parts = ip.split('.').map { it.toInt().toByte() }.toByteArray()
                if (parts.size == 4) {
                    InetAddress.getByAddress(hostname, parts)
                } else {
                    InetAddress.getByName(ip)
                }
            } catch (e: Exception) {
                try {
                    InetAddress.getByName(ip)
                } catch (e2: Exception) {
                    null
                }
            }
        }

        private fun isBlockedIp(ip: String): Boolean {
            return ip.startsWith("118.98.") ||
                    ip.startsWith("36.86.") ||
                    ip.startsWith("180.250.") ||
                    ip.startsWith("125.160.") ||
                    ip.startsWith("61.94.") ||
                    ip.startsWith("202.134.") ||
                    ip.startsWith("112.215.") ||
                    ip.startsWith("202.152.") ||
                    ip.startsWith("103.111.") ||
                    ip.startsWith("124.81.") ||
                    ip.startsWith("114.124.") ||
                    ip.startsWith("114.125.") ||
                    ip.startsWith("103.31.") ||
                    ip.startsWith("103.253.") ||
                    ip.startsWith("10.") ||
                    ip.startsWith("192.168.") ||
                    ip.startsWith("172.16.") || ip.startsWith("172.17.") || ip.startsWith("172.18.") ||
                    ip.startsWith("172.19.") || ip.startsWith("172.20.") || ip.startsWith("172.21.") ||
                    ip.startsWith("172.22.") || ip.startsWith("172.23.") || ip.startsWith("172.24.") ||
                    ip.startsWith("172.25.") || ip.startsWith("172.26.") || ip.startsWith("172.27.") ||
                    ip.startsWith("172.28.") || ip.startsWith("172.29.") || ip.startsWith("172.30.") ||
                    ip.startsWith("172.31.") ||
                    ip.startsWith("169.254.") ||
                    ip.startsWith("127.") ||
                    ip == "0.0.0.0"
        }

        override fun lookup(hostname: String): List<InetAddress> {
            val cleanHost = hostname.lowercase().trim()
            cache[cleanHost]?.let { return it }

            // 1. Vidhide CDN direct origin routing (*.dramiyos-cdn.com, *.acek-cdn.com, etc.)
            // MUST hit origin nginx IPs (203.188.166.60 / .71 / .68) directly - Cloudflare returns 404!
            if (cleanHost.endsWith("-cdn.com") || cleanHost.contains("dramiyos") || cleanHost.contains("acek-cdn")) {
                val cdnIps = listOf("203.188.166.60", "203.188.166.71", "203.188.166.68")
                val addrs = cdnIps.mapNotNull { createAddress(cleanHost, it) }
                if (addrs.isNotEmpty()) {
                    cache[cleanHost] = addrs
                    return addrs
                }
            }

            // 2. Instant Static Fallbacks (0ms lookup, guaranteed anti-blocking for media & CDN domains)
            val matchedIps = staticFallbacks[cleanHost]
                ?: staticFallbacks.entries.firstOrNull { cleanHost == it.key || cleanHost.endsWith("." + it.key) }?.value

            if (!matchedIps.isNullOrEmpty()) {
                val staticAddrs = matchedIps.mapNotNull { createAddress(cleanHost, it) }
                if (staticAddrs.isNotEmpty()) {
                    cache[cleanHost] = staticAddrs
                    return staticAddrs
                }
            }

            // 3. Generic Cloudflare edge IPs fallback for image hosts (*.lol, *.lat, *.pics)
            if (cleanHost.endsWith(".lol") || cleanHost.endsWith(".lat") || cleanHost.endsWith(".pics")) {
                val cfIps = listOf("104.21.10.207", "172.67.190.254", "104.21.50.252", "172.67.215.119", "104.21.30.204", "172.67.173.220", "104.21.76.66", "172.67.190.239")
                val addrs = cfIps.mapNotNull { createAddress(cleanHost, it) }
                if (addrs.isNotEmpty()) {
                    cache[cleanHost] = addrs
                    return addrs
                }
            }

            // 2. Try System DNS (filter out Indonesian telco block/landing page IPs)
            try {
                val systemAddrs = Dns.SYSTEM.lookup(hostname)
                val validAddrs = systemAddrs.filter { !isBlockedIp(it.hostAddress ?: "") }
                val v4 = validAddrs.filterIsInstance<Inet4Address>()
                val candidates = if (v4.isNotEmpty()) v4 else validAddrs
                if (candidates.isNotEmpty()) {
                    cache[cleanHost] = candidates
                    return candidates
                }
            } catch (e: Exception) {
                // System DNS failed or poisoned
            }

            // 3. Fallback to Google / Cloudflare DoH (with bounded 1.2s timeout)
            try {
                val dohAddrs = resolveDoH(cleanHost)
                if (dohAddrs.isNotEmpty()) {
                    cache[cleanHost] = dohAddrs
                    return dohAddrs
                }
            } catch (e: Exception) {
                // DoH failed
            }

            // Final attempt: fallback to system lookup
            return Dns.SYSTEM.lookup(hostname)
        }

        private fun resolveDoH(hostname: String): List<InetAddress> {
            val dohUrls = listOf(
                "https://dns.google/resolve?name=$hostname&type=A",
                "https://1.1.1.1/dns-query?name=$hostname&type=A"
            )
            for (endpoint in dohUrls) {
                try {
                    val url = URL(endpoint)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 1200
                        readTimeout = 1200
                        setRequestProperty("Accept", "application/dns-json")
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                    }
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()

                    val json = JSONObject(text)
                    val answers = json.optJSONArray("Answer") ?: continue
                    val results = mutableListOf<InetAddress>()
                    for (i in 0 until answers.length()) {
                        val ans = answers.getJSONObject(i)
                        if (ans.optInt("type") == 1) { // A record
                            val ip = ans.optString("data")
                            if (ip.isNotBlank() && !isBlockedIp(ip)) {
                                createAddress(hostname, ip)?.let { results.add(it) }
                            }
                        }
                    }
                    if (results.isNotEmpty()) return results
                } catch (e: Exception) {
                    // Try next DoH provider
                }
            }
            return emptyList()
        }
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(resilientDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun getJson(endpoint: String, params: Map<String, String> = emptyMap()): JsonObject? =
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = NanzStreamApp.storage.apiBaseUrl
                val cleanBase = baseUrl.removeSuffix("/")
                val cleanEndpoint = endpoint.removePrefix("/")

                val urlBuilder = java.lang.StringBuilder("$cleanBase/$cleanEndpoint")
                if (params.isNotEmpty()) {
                    urlBuilder.append("?")
                    params.entries.forEachIndexed { i, entry ->
                        if (i > 0) urlBuilder.append("&")
                        urlBuilder.append(java.net.URLEncoder.encode(entry.key, "UTF-8"))
                        urlBuilder.append("=")
                        urlBuilder.append(java.net.URLEncoder.encode(entry.value, "UTF-8"))
                    }
                }

                val request = Request.Builder()
                    .url(urlBuilder.toString())
                    .header("User-Agent", "NanzStream-Android/1.0.0 (Linux; Android)")
                    .header("Accept", "application/json")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return@withContext null
                    gson.fromJson(bodyString, JsonObject::class.java)
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
}
