package com.nanzstream.nanas.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nanzstream.nanas.NanzStreamApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object ApiClient {

    private val gson = Gson()

    // Enforce IPv4 first to avoid mobile network IPv6 timeout routing issues
    private val ipv4Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            val v4 = addresses.filterIsInstance<Inet4Address>()
            return if (v4.isNotEmpty()) v4 else addresses
        }
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(ipv4Dns)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
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
