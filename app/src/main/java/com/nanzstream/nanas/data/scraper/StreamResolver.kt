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

        // 2. TurboVIP embed (Used in Donghua)
        if (cleanUrl.contains("turbovidhls.com") || cleanUrl.contains("turbovid")) {
            val direct = extractTurboVipDirect(cleanUrl)
            if (!direct.isNullOrBlank()) {
                return@withContext direct
            }
        }

        // 3. Vidhide embed (Used in Animasu for direct unblocked HLS)
        if (cleanUrl.contains("vidhide") || cleanUrl.contains("odvidhide")) {
            val direct = extractVidhideHls(cleanUrl, if (referer.isNotBlank()) referer else "https://animasu.love/")
            if (!direct.isNullOrBlank()) {
                return@withContext direct
            }
        }

        // 5. YourUpload embed (Used in Animasu Anime for direct MP4)
        if (cleanUrl.contains("yourupload.com")) {
            val direct = extractYourUploadDirect(cleanUrl, if (referer.isNotBlank()) referer else "https://animasu.love/")
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

    suspend fun extractTurboVipDirect(turboUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(turboUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://anichin.ro/")
                .build()
            val resp = ApiClient.okHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext null

            val m3u8Match = Regex("""(https?://[^\s"']+\.m3u8[^"']*)""").find(html)?.groupValues?.get(1)
                ?: Regex("""file:\s*["']([^"']+\.m3u8[^"']*)["']""").find(html)?.groupValues?.get(1)
            if (!m3u8Match.isNullOrBlank() && m3u8Match.startsWith("http")) {
                return@withContext m3u8Match
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    private const val PACKER_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private fun decodeBaseN(str: String, base: Int): Int? {
        var res = 0
        for (ch in str) {
            val idx = PACKER_ALPHABET.indexOf(ch)
            if (idx == -1 || idx >= base) return null
            res = res * base + idx
        }
        return res
    }

    suspend fun extractVidhideHls(embedUrl: String, referer: String = "https://animasu.love/"): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", referer)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            val resp = ApiClient.okHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext null

            // Unpack packed javascript in a single pass (ultra-fast < 15ms)
            val regex = Regex("""eval\(function\(p,a,c,k,e,d\)\{while\(c--\).*?return p\}\('(.*?)',(\d+),(\d+),'(.*?)'\.split\('\|'\)\)\)""", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(html) ?: return@withContext null
            val p = match.groupValues[1]
            val a = match.groupValues[2].toIntOrNull() ?: 36
            val k = match.groupValues[4].split('|')

            val tokenRegex = Regex("""\b\w+\b""")
            val unpacked = tokenRegex.replace(p) { m ->
                val word = m.value
                val idx = decodeBaseN(word, a)
                if (idx != null && idx < k.size && k[idx].isNotBlank()) {
                    k[idx]
                } else {
                    word
                }
            }

            val m3u8Match = Regex("""https?://[^\s"',]+\.m3u8[^\s"',]*""").find(unpacked)
            return@withContext m3u8Match?.value
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun extractYourUploadDirect(url: String, referer: String = "https://animasu.love/"): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", referer)
                .build()
            val resp = ApiClient.okHttpClient.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext null
            val fileMatch = Regex("""file:\s*['"]([^'"]+\.mp4[^'"]*)['"]""").find(html)?.groupValues?.get(1)
                ?: Regex("""property=['"]og:video['"]\s*content=['"]([^'"]+)['"]""").find(html)?.groupValues?.get(1)
            if (!fileMatch.isNullOrBlank() && fileMatch.startsWith("http")) {
                return@withContext fileMatch
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}
