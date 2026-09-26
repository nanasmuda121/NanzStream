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
            cleanUrl.contains(".mp4", ignoreCase = true) ||
            cleanUrl.contains("googlevideo.com", ignoreCase = true)
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

        // 4. Vidhide embed (Used in Otakudesu Anime for direct unblocked HLS)
        if (cleanUrl.contains("vidhide") || cleanUrl.contains("odvidhide")) {
            val direct = extractVidhideHls(cleanUrl, if (referer.isNotBlank()) referer else "https://otakudesu.blog/")
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

                // 1. Direct MP4 video streams from OK.ru (fast, reliable 200 OK without HLS 400 Bad Request error)
                val videosArr = metaObj?.optJSONArray("videos")
                if (videosArr != null && videosArr.length() > 0) {
                    val preferred = listOf("hd", "sd", "full", "low", "mobile", "lowest")
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

                // 2. Check for HLS manifest (.m3u8) as secondary fallback
                val hls = metaObj?.optString("hlsManifestUrl")
                if (!hls.isNullOrBlank() && hls.startsWith("http")) {
                    return@withContext hls
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun extractOkRuStreams(okUrl: String, referer: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Pair<String, String>>()
        try {
            val req = Request.Builder()
                .url(okUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", referer)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            val resp = ApiClient.okHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext emptyList()

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

                val videosArr = metaObj?.optJSONArray("videos")
                if (videosArr != null && videosArr.length() > 0) {
                    for (i in 0 until videosArr.length()) {
                        val v = videosArr.getJSONObject(i)
                        val name = v.optString("name")
                        val url = v.optString("url")
                        if (url.startsWith("http")) {
                            val qualityLabel = when (name.lowercase()) {
                                "full" -> "1080p Full HD"
                                "hd" -> "720p HD"
                                "sd" -> "480p SD (Lancar / Anti-Lag)"
                                "low" -> "360p Low (Hemat Kuota)"
                                "mobile" -> "Mobile"
                                else -> name.uppercase()
                            }
                            list.add(qualityLabel to url)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun extractDesuStreamDirect(desuUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(desuUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://otakudesu.blog/")
                .build()
            val resp = ApiClient.okHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext null

            val mp4Match = Regex("""videoURL\s*=\s*["']([^"']+)["']""").find(html)
                ?: Regex("""(https?://[^\s"']+\.mp4[^\s"']*)""").find(html)
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

    suspend fun extractTurboVipDirect(turboUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(turboUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://anichin.ro/")
                .build()
            val resp = ApiClient.okHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext null

            val m3u8Match = Regex("""(https?://[^\s"']+\.m3u8[^\s"']*)""").find(html)?.groupValues?.get(1)
                ?: Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']""").find(html)?.groupValues?.get(1)
            if (!m3u8Match.isNullOrBlank() && m3u8Match.startsWith("http")) {
                return@withContext m3u8Match
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun extractVidhideHls(embedUrl: String, referer: String = "https://otakudesu.blog/"): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", referer)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            val resp = ApiClient.okHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext null

            // Unpack packed javascript
            val regex = Regex("""eval\(function\(p,a,c,k,e,d\)\{while\(c--\).*?return p\}\('(.*?)',(\d+),(\d+),'(.*?)'\.split\('\|'\)\)\)""", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(html) ?: return@withContext null
            var p = match.groupValues[1]
            val a = match.groupValues[2].toIntOrNull() ?: 36
            var c = match.groupValues[3].toIntOrNull() ?: 0
            val k = match.groupValues[4].split('|')

            fun baseN(num: Int, base: Int): String {
                val digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                if (num == 0) return "0"
                var n = num
                val sb = StringBuilder()
                while (n > 0) {
                    sb.append(digits[n % base])
                    n /= base
                }
                return sb.reverse().toString()
            }

            while (c > 0) {
                c--
                if (c < k.size && k[c].isNotBlank()) {
                    val key = baseN(c, a)
                    p = p.replace(Regex("""\b$key\b"""), java.util.regex.Matcher.quoteReplacement(k[c]))
                }
            }

            val m3u8Match = Regex("""https?://[^\s"',]+\.m3u8[^\s"',]*""").find(p)
            return@withContext m3u8Match?.value
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}
