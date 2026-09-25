package com.nanzstream.nanas.data.scraper

import com.nanzstream.nanas.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.parser.Parser

object StreamResolver {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    /**
     * Resolves any stream URL (direct or embed iframe from OK.ru, DesuStream, TurboVIP, etc.)
     * into a direct playable stream URL (HLS .m3u8, MP4, DASH .mpd) for native ExoPlayer.
     */
    suspend fun resolveToDirectStream(url: String, referer: String = ""): String = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return@withContext ""

        // Already a direct stream
        if (cleanUrl.contains(".m3u8", ignoreCase = true) ||
            cleanUrl.contains(".mpd", ignoreCase = true) ||
            cleanUrl.contains(".mp4", ignoreCase = true)
        ) {
            return@withContext cleanUrl
        }

        // 1. OK.ru embed (Used extensively in Donghua / Anichin & Anime)
        if (cleanUrl.contains("ok.ru/videoembed/")) {
            val direct = extractOkRuDirect(cleanUrl, if (referer.isNotBlank()) referer else "https://anichin.ro/")
            if (!direct.isNullOrBlank()) {
                return@withContext direct
            }
        }

        // 2. DesuStream embed (Used in Otakudesu)
        if (cleanUrl.contains("desustream.net")) {
            val direct = extractDesuStreamDirect(cleanUrl)
            if (!direct.isNullOrBlank()) {
                return@withContext direct
            }
        }

        // 3. TurboVIP embed (Used in Donghua)
        if (cleanUrl.contains("turbovidhls.com") || cleanUrl.contains("turbovid")) {
            val direct = extractTurboVipDirect(cleanUrl)
            if (!direct.isNullOrBlank()) {
                return@withContext direct
            }
        }

        // Fallback: return the original URL so ExoPlayer attempts to play it directly
        cleanUrl
    }

    suspend fun extractOkRuDirect(okUrl: String, referer: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(okUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", referer)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            val resp = ApiClient.okHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext null

            val optMatch = Regex("""data-options=["']([^"']+)["']""").find(html)
            if (optMatch != null) {
                val rawJson = Parser.unescapeEntities(optMatch.groupValues[1], false)
                val optObj = JSONObject(rawJson)
                val flashvars = optObj.optJSONObject("flashvars")
                val metaRaw = flashvars?.opt("metadata")
                val metaObj = when (metaRaw) {
                    is JSONObject -> metaRaw
                    is String -> JSONObject(metaRaw)
                    else -> null
                }

                // 1. Check for HLS manifest (.m3u8)
                val hls = metaObj?.optString("hlsManifestUrl")
                if (!hls.isNullOrBlank() && hls.startsWith("http")) {
                    return@withContext hls
                }

                // 2. Check for direct MP4 video sources (ordered by quality)
                val videosArr = metaObj?.optJSONArray("videos")
                if (videosArr != null && videosArr.length() > 0) {
                    val preferred = listOf("full", "hd", "sd", "low", "lowest", "mobile")
                    for (p in preferred) {
                        for (i in 0 until videosArr.length()) {
                            val v = videosArr.getJSONObject(i)
                            if (v.optString("name").equals(p, ignoreCase = true)) {
                                val direct = v.optString("url")
                                if (direct.startsWith("http")) return@withContext direct
                            }
                        }
                    }
                    val fallback = videosArr.getJSONObject(0).optString("url")
                    if (fallback.startsWith("http")) return@withContext fallback
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    private suspend fun extractDesuStreamDirect(desuUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(desuUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://otakudesu.blog/")
                .build()
            val resp = ApiClient.okHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext null

            val mp4Match = Regex("""(https?://[^\s"']+\.mp4[^\s"']*)""").find(html)
            val m3u8Match = Regex("""(https?://[^\s"']+\.m3u8[^\s"']*)""").find(html)
            val direct = (mp4Match ?: m3u8Match)?.groupValues?.get(1)
            if (!direct.isNullOrBlank() && direct.startsWith("http") && !direct.endsWith("/.mp4") && !direct.contains("/download/.mp4")) {
                return@withContext direct
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    private suspend fun extractTurboVipDirect(turboUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(turboUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://anichin.ro/")
                .build()
            val resp = ApiClient.okHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext null

            val m3u8Match = Regex("""(https?://[^\s"']+\.m3u8[^\s"']*)""").find(html)?.groupValues?.get(1)
            if (!m3u8Match.isNullOrBlank() && m3u8Match.startsWith("http")) {
                return@withContext m3u8Match
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}
