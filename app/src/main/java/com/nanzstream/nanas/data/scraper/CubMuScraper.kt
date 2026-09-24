package com.nanzstream.nanas.data.scraper

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nanzstream.nanas.data.model.*
import com.nanzstream.nanas.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CubMuScraper {

    private const val BASE_URL = "https://www.cubmu.com"
    private const val SERVICE_BUS = "https://servicebuss.transvision.co.id"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val gson = Gson()

    private var cachedToken: String? = null
    private var tokenExpiresAt: Long = 0

    private fun encryptPassword(pwd: String): String {
        val r = "xx"
        val n = System.currentTimeMillis() / 1000
        var i = "$pwd{SPLITTER}$n"
        repeat(2) {
            val encoded = Base64.encodeToString(i.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            i = "$r$encoded"
        }
        return i
    }

    private suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!cachedToken.isNullOrEmpty() && tokenExpiresAt > now + 60000) {
            return@withContext cachedToken!!
        }

        val encPassword = encryptPassword("hospitality")
        val jsonPayload = """
            {
                "app_id": "cubmu",
                "tvs_platform_id": "standalone",
                "email_or_phone": "master_account@transvision.co.id",
                "password": "$encPassword",
                "device": {
                    "device_id": "web_browser",
                    "device_brand": "Web Browser",
                    "device_type": "WEB",
                    "firebase_id": "NOT_ALLOWED",
                    "notes": "Web Browser-V2.1"
                }
            }
        """.trimIndent()

        val req = Request.Builder()
            .url("$SERVICE_BUS/global/v3/auth/redirect-login")
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", USER_AGENT)
            .header("Origin", BASE_URL)
            .header("Referer", "$BASE_URL/")
            .build()

        val res = ApiClient.okHttpClient.newCall(req).execute()
        val body = res.body?.string() ?: ""
        val json = gson.fromJson(body, JsonObject::class.java)
        val token = json?.getAsJsonObject("data")?.get("access_token")?.asString ?: ""

        if (token.isNotEmpty()) {
            cachedToken = token
            tokenExpiresAt = now + 12 * 60 * 60 * 1000
        }
        token
    }

    fun decryptManifest(str: String?): String? {
        if (str.isNullOrBlank()) return null
        return try {
            var b64 = str.replace("-", "+").replace("_", "/")
            while (b64.length % 4 != 0) b64 += "="
            val buf = Base64.decode(b64, Base64.DEFAULT)
            if (buf.size < 16) return null

            val iv = buf.copyOfRange(0, 16)
            val ciphertext = buf.copyOfRange(16, buf.size)
            val key = "tr4n5V1s10nL1v3y".toByteArray(Charsets.UTF_8)

            val cipher = Cipher.getInstance("AES/CFB/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val dec = cipher.doFinal(ciphertext)
            String(dec, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getChannels(): List<LiveTvChannelItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LiveTvChannelItem>()
        try {
            val token = getAccessToken()
            val req = Request.Builder()
                .url("$SERVICE_BUS/global/v4/channel-list?page=1&per_page=50&platform_id=1")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", USER_AGENT)
                .header("Origin", BASE_URL)
                .header("Referer", "$BASE_URL/")
                .build()

            val res = ApiClient.okHttpClient.newCall(req).execute()
            val body = res.body?.string() ?: return@withContext emptyList()
            val json = gson.fromJson(body, JsonObject::class.java)
            val items = json?.getAsJsonObject("data")?.getAsJsonArray("items")

            if (items != null) {
                for (gElem in items) {
                    val gObj = gElem.asJsonObject
                    val genreName = gObj.get("genre_name")?.asString ?: "TV Nasional"
                    val channels = gObj.getAsJsonArray("channels")
                    if (channels != null) {
                        for (cElem in channels) {
                            val cObj = cElem.asJsonObject
                            val id = cObj.get("channel_id")?.asString ?: ""
                            val name = cObj.get("channel_name")?.asString ?: ""
                            val number = cObj.get("channel_number")?.asInt ?: 0
                            val img = cObj.get("channel_image")?.asString ?: ""
                            val slug = cObj.get("slug_url")?.asString ?: "$id-${name.lowercase().replace(" ", "-")}"

                            list.add(
                                LiveTvChannelItem(
                                    id = id,
                                    name = name,
                                    number = number,
                                    genre = genreName,
                                    logoUrl = img,
                                    slug = slug
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun getLiveStream(channelInput: String): StreamResult? = withContext(Dispatchers.IO) {
        try {
            var slug = channelInput
            if (channelInput.contains("/live-tv/")) {
                slug = Regex("""/live-tv/([^/?#]+)""").find(channelInput)?.groupValues?.get(1) ?: channelInput
            }

            val req = Request.Builder()
                .url("$BASE_URL/watch/live-tv/$slug")
                .header("User-Agent", USER_AGENT)
                .build()

            val res = ApiClient.okHttpClient.newCall(req).execute()
            val html = res.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html)
            val nextDataRaw = doc.selectFirst("#__NEXT_DATA__")?.html() ?: return@withContext null

            val json = gson.fromJson(nextDataRaw, JsonObject::class.java)
            val props = json.getAsJsonObject("props")?.getAsJsonObject("pageProps")
            val detail = props?.getAsJsonObject("detailChannel")
            val chName = detail?.get("channel_name")?.asString ?: "Live TV"
            val dashManifest = props?.get("manifest")?.asString

            var hlsManifest: String? = null
            val cdnList = detail?.getAsJsonArray("channel_cdn_list")
            if (cdnList != null) {
                for (cdn in cdnList) {
                    val encHls = cdn.asJsonObject.getAsJsonObject("cdn_manifest")?.get("hls")?.asString
                    val dec = decryptManifest(encHls)
                    if (!dec.isNullOrEmpty()) {
                        hlsManifest = dec
                        break
                    }
                }
            }

            val primaryStream = hlsManifest ?: dashManifest

            StreamResult(
                title = chName,
                directHlsUrl = primaryStream,
                iframePlayerUrl = primaryStream,
                servers = listOf(StreamServerItem("Direct Live Stream", primaryStream ?: "", isDirectHls = true))
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getVodList(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val token = getAccessToken()
            val req = Request.Builder()
                .url("$SERVICE_BUS/global/v4/vod/list/homepage?page=$page&per_page=10&platform_id=1")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", USER_AGENT)
                .header("Origin", BASE_URL)
                .header("Referer", "$BASE_URL/")
                .build()

            val res = ApiClient.okHttpClient.newCall(req).execute()
            val body = res.body?.string() ?: return@withContext emptyList()
            val json = gson.fromJson(body, JsonObject::class.java)
            val items = json?.getAsJsonObject("data")?.getAsJsonArray("items")

            if (items != null) {
                for (secElem in items) {
                    val secObj = secElem.asJsonObject
                    val rawList = secObj.getAsJsonArray("data") ?: secObj.getAsJsonArray("contents")
                    if (rawList != null) {
                        for (cElem in rawList) {
                            val cObj = cElem.asJsonObject
                            val id = cObj.get("vod_id")?.asString ?: ""
                            val title = cObj.get("vod_name")?.asString ?: cObj.get("title")?.asString ?: "VOD Series"
                            val img = cObj.get("poster_portrait")?.asString
                                ?: cObj.getAsJsonObject("meta")?.get("poster_og")?.asString ?: ""
                            val slug = cObj.getAsJsonObject("meta")?.get("slug_url")?.asString ?: id
                            val badge = cObj.get("type_vod")?.asString ?: "Series"

                            list.add(
                                MediaItem(
                                    id = id,
                                    title = title,
                                    category = CategoryType.VOD,
                                    thumbnail = img,
                                    slug = slug,
                                    badge = badge
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun getVodDetail(slug: String): MediaDetail? = withContext(Dispatchers.IO) {
        try {
            var pageUrl = "$BASE_URL/movie/series/$slug"
            var req = Request.Builder().url(pageUrl).header("User-Agent", USER_AGENT).build()
            var res = ApiClient.okHttpClient.newCall(req).execute()
            var html = res.body?.string() ?: ""

            if (!res.isSuccessful || html.isEmpty()) {
                pageUrl = "$BASE_URL/movie/$slug"
                req = Request.Builder().url(pageUrl).header("User-Agent", USER_AGENT).build()
                res = ApiClient.okHttpClient.newCall(req).execute()
                html = res.body?.string() ?: ""
            }

            val doc = Jsoup.parse(html)
            val nextDataRaw = doc.selectFirst("#__NEXT_DATA__")?.html() ?: return@withContext null
            val json = gson.fromJson(nextDataRaw, JsonObject::class.java)
            val detail = json.getAsJsonObject("props")?.getAsJsonObject("pageProps")?.getAsJsonObject("detailMovie")
                ?: return@withContext null

            val title = detail.get("title")?.asString ?: detail.get("name")?.asString ?: slug
            val desc = detail.get("synopsis")?.asString ?: detail.get("description")?.asString
            val img = detail.get("poster_vertical")?.asString ?: detail.get("poster_horizontal")?.asString ?: ""

            val episodes = mutableListOf<EpisodeItem>()
            val rawLibs = detail.getAsJsonArray("product_library") ?: detail.getAsJsonArray("product_libraries")
            if (rawLibs != null) {
                for (ep in rawLibs) {
                    val epObj = ep.asJsonObject
                    val epNo = epObj.get("episode_no")?.asString ?: "1"
                    val epTitle = epObj.get("episode_name")?.asString ?: "Episode $epNo"
                    val watchSlug = epObj.getAsJsonObject("meta")?.get("slug_watch_url")?.asString ?: slug
                    episodes.add(EpisodeItem(id = epNo, episodeNumber = epNo, title = epTitle, url = watchSlug))
                }
            }

            MediaDetail(
                id = slug,
                title = title,
                category = CategoryType.VOD,
                thumbnail = img,
                synopsis = desc,
                totalEpisodes = "${episodes.size} Episode",
                episodes = episodes
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getVodStream(slug: String): StreamResult? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$BASE_URL/watch/series/$slug")
                .header("User-Agent", USER_AGENT)
                .build()

            val res = ApiClient.okHttpClient.newCall(req).execute()
            val html = res.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html)
            val nextDataRaw = doc.selectFirst("#__NEXT_DATA__")?.html() ?: return@withContext null

            val json = gson.fromJson(nextDataRaw, JsonObject::class.java)
            val props = json.getAsJsonObject("props")?.getAsJsonObject("pageProps")
            val manifest = props?.get("manifest")?.asString
            val title = props?.getAsJsonObject("detailMovie")?.get("title")?.asString ?: slug

            StreamResult(
                title = title,
                directHlsUrl = manifest,
                iframePlayerUrl = manifest,
                servers = listOf(StreamServerItem("Direct HLS Stream", manifest ?: "", isDirectHls = true))
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
