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
            "otakudesu.blog" to listOf("172.67.220.233", "104.21.94.77"),
            "anichin.ro" to listOf("104.21.76.66", "172.67.190.239"),
            "anichin.site" to listOf("104.21.13.75", "172.67.198.201"),
            "www.webtoons.com" to listOf("203.104.174.129"),
            "dracinema.com" to listOf("172.67.194.112", "104.21.33.253"),
            "themoviebox.online" to listOf("103.224.182.189")
        )

        private fun isBlockedIp(ip: String): Boolean {
            return ip.startsWith("118.98.") ||
                    ip.startsWith("36.86.") ||
                    ip.startsWith("180.250.") ||
                    ip.startsWith("10.") ||
                    ip.startsWith("127.") ||
                    ip == "0.0.0.0"
        }

        override fun lookup(hostname: String): List<InetAddress> {
            cache[hostname]?.let { return it }

            // 1. Try system DNS first
            try {
                val systemAddrs = Dns.SYSTEM.lookup(hostname)
                val validAddrs = systemAddrs.filter { !isBlockedIp(it.hostAddress ?: "") }
                val v4 = validAddrs.filterIsInstance<Inet4Address>()
                val candidates = if (v4.isNotEmpty()) v4 else validAddrs
                if (candidates.isNotEmpty()) {
                    cache[hostname] = candidates
                    return candidates
                }
            } catch (e: Exception) {
                // System DNS failed, fallback to DoH / static
            }

            // 2. Try Cloudflare DoH (1.1.1.1)
            try {
                val dohAddrs = resolveDoH(hostname)
                if (dohAddrs.isNotEmpty()) {
                    cache[hostname] = dohAddrs
                    return dohAddrs
                }
            } catch (e: Exception) {
                // DoH failed, fallback to static IPs
            }

            // 3. Fallback to preconfigured static IPs
            staticFallbacks[hostname.lowercase()]?.let { ips ->
                val staticAddrs = ips.mapNotNull {
                    try {
                        InetAddress.getByName(it)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (staticAddrs.isNotEmpty()) {
                    cache[hostname] = staticAddrs
                    return staticAddrs
                }
            }

            // Final attempt: rethrow or return system lookup
            return Dns.SYSTEM.lookup(hostname)
        }

        private fun resolveDoH(hostname: String): List<InetAddress> {
            val url = URL("https://1.1.1.1/dns-query?name=$hostname&type=A")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("Accept", "application/dns-json")
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(text)
            val answers = json.optJSONArray("Answer") ?: return emptyList()
            val results = mutableListOf<InetAddress>()
            for (i in 0 until answers.length()) {
                val ans = answers.getJSONObject(i)
                if (ans.optInt("type") == 1) { // A record
                    val ip = ans.optString("data")
                    if (ip.isNotBlank() && !isBlockedIp(ip)) {
                        try {
                            results.add(InetAddress.getByName(ip))
                        } catch (e: Exception) {
                            // ignore parse error
                        }
                    }
                }
            }
            return results
        }
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(resilientDns)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
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
