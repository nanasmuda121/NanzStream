package com.nanzstream.nanas.data.scraper

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

object MangaScraper {

    private const val BASE_WEB = "https://global.manga-up.com"
    private const val BASE_API = "https://global-api.manga-up.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val gson = Gson()

    suspend fun getHome(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        try {
            val req = Request.Builder()
                .url("$BASE_WEB/")
                .header("User-Agent", USER_AGENT)
                .build()
            val res = ApiClient.okHttpClient.newCall(req).execute()
            val html = res.body?.string() ?: return@withContext emptyList()
            val doc = Jsoup.parse(html)
            val nextDataRaw = doc.selectFirst("#__NEXT_DATA__")?.html() ?: return@withContext emptyList()

            val json = gson.fromJson(nextDataRaw, JsonObject::class.java)
            val pageProps = json.getAsJsonObject("props")?.getAsJsonObject("pageProps")
            val home = pageProps?.getAsJsonObject("home")
            val updated = home?.getAsJsonArray("updatedTitles")
                ?: home?.getAsJsonArray("rankings")
                ?: home?.getAsJsonArray("topBanners")

            if (updated != null) {
                for (elem in updated) {
                    val obj = elem.asJsonObject
                    val id = obj.get("id")?.asString ?: obj.get("mangaId")?.asString ?: "1"
                    val title = obj.get("title")?.asString ?: obj.get("name")?.asString ?: "Manga"
                    val img = obj.get("imageUrl")?.asString ?: obj.get("cover")?.asString ?: ""
                    items.add(
                        MediaItem(
                            id = id,
                            title = title,
                            category = CategoryType.MANGA,
                            thumbnail = img,
                            slug = id,
                            badge = "Manga UP"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        try {
            val url = "$BASE_API/api/manga/search?title=${java.net.URLEncoder.encode(query, "UTF-8")}&lang=en"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Origin", BASE_WEB)
                .header("Referer", "$BASE_WEB/")
                .build()
            val res = ApiClient.okHttpClient.newCall(req).execute()
            val bytes = res.body?.bytes() ?: return@withContext emptyList()
            val proto = parseProto(bytes)

            // Titles in field 1
            val titlesArr = proto[1] ?: emptyList()
            for (tItem in titlesArr) {
                val tProto = parseProto(tItem.bytesVal)
                val id = tProto[1]?.firstOrNull()?.intVal?.toString() ?: "1"
                val name = tProto[2]?.firstOrNull()?.bytesVal?.let { String(it, Charsets.UTF_8) } ?: "Manga Title"
                val relImg = tProto[3]?.firstOrNull()?.bytesVal?.let { String(it, Charsets.UTF_8) } ?: ""
                val fullImg = if (relImg.startsWith("http")) relImg else "$BASE_API$relImg"

                items.add(
                    MediaItem(
                        id = id,
                        title = name,
                        category = CategoryType.MANGA,
                        thumbnail = fullImg,
                        slug = id,
                        badge = "Manga UP"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    suspend fun getDetail(mangaIdInput: String): MediaDetail? = withContext(Dispatchers.IO) {
        val id = if (mangaIdInput.contains("/manga/")) {
            Regex("""/manga/(\d+)""").find(mangaIdInput)?.groupValues?.get(1) ?: mangaIdInput
        } else mangaIdInput

        try {
            val req = Request.Builder()
                .url("$BASE_WEB/manga/$id")
                .header("User-Agent", USER_AGENT)
                .build()
            val res = ApiClient.okHttpClient.newCall(req).execute()
            val html = res.body?.string() ?: return@withContext null
            val doc = Jsoup.parse(html)
            val nextDataRaw = doc.selectFirst("#__NEXT_DATA__")?.html() ?: return@withContext null

            val json = gson.fromJson(nextDataRaw, JsonObject::class.java)
            val detail = json.getAsJsonObject("props")?.getAsJsonObject("pageProps")?.getAsJsonObject("detail")
            val title = detail?.get("title")?.asString ?: "Manga Detail"
            val desc = detail?.get("description")?.asString
            val img = detail?.get("mainImageUrl")?.asString ?: ""

            val chapters = mutableListOf<MangaChapterItem>()
            val rawChapters = detail?.getAsJsonArray("chapters")
            if (rawChapters != null) {
                for (chElem in rawChapters) {
                    val chObj = chElem.asJsonObject
                    val chId = chObj.get("id")?.asString ?: ""
                    val chName = chObj.get("mainName")?.asString ?: chObj.get("name")?.asString ?: "Chapter"
                    val subName = chObj.get("subName")?.asString
                    chapters.add(
                        MangaChapterItem(
                            id = chId,
                            title = chName,
                            subtitle = subName
                        )
                    )
                }
            }

            MediaDetail(
                id = id,
                title = title,
                category = CategoryType.MANGA,
                thumbnail = img,
                synopsis = desc,
                totalEpisodes = "${chapters.size} Chapter",
                chapters = chapters
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getPages(chapterId: String): List<MangaPageItem> = withContext(Dispatchers.IO) {
        val pages = mutableListOf<MangaPageItem>()
        try {
            val url = "$BASE_API/api/manga/viewer_v2?chapter_id=$chapterId&first=yes&quality=high&lang=en"
            val req = Request.Builder()
                .url(url)
                .post("{}".toRequestBody("application/json".toMediaType()))
                .header("User-Agent", USER_AGENT)
                .header("Origin", BASE_WEB)
                .header("Referer", "$BASE_WEB/")
                .build()
            val res = ApiClient.okHttpClient.newCall(req).execute()
            val bytes = res.body?.bytes() ?: return@withContext emptyList()
            val topProto = parseProto(bytes)

            val blockRaw = topProto[3]?.firstOrNull()?.bytesVal ?: return@withContext emptyList()
            val block = parseProto(blockRaw)
            val rawPages = block[3] ?: emptyList()

            for ((idx, p) in rawPages.withIndex()) {
                val pageProto = parseProto(p.bytesVal)
                val relUrl = pageProto[1]?.firstOrNull()?.bytesVal?.let { String(it, Charsets.UTF_8) } ?: ""
                val keyHex = pageProto[5]?.firstOrNull()?.bytesVal?.let { String(it, Charsets.UTF_8) }
                val ivHex = pageProto[6]?.firstOrNull()?.bytesVal?.let { String(it, Charsets.UTF_8) }

                if (relUrl.isNotBlank()) {
                    val fullUrl = if (relUrl.startsWith("http")) relUrl else "$BASE_API$relUrl"
                    pages.add(
                        MangaPageItem(
                            page = idx + 1,
                            url = fullUrl,
                            key = keyHex,
                            iv = ivHex
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        pages
    }

    // ==========================================
    // Native Protobuf Wire Format Parser
    // ==========================================
    class ProtoField(val type: Int, val intVal: Long = 0, val bytesVal: ByteArray = ByteArray(0))

    private fun parseProto(data: ByteArray): Map<Int, List<ProtoField>> {
        val result = mutableMapOf<Int, MutableList<ProtoField>>()
        var i = 0
        val len = data.size

        while (i < len) {
            var key: Long = 0
            var shift = 0
            while (i < len) {
                val b = data[i++].toInt()
                key = key or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) break
                shift += 7
            }

            val fieldNum = (key ushr 3).toInt()
            val wireType = (key and 0x07).toInt()

            when (wireType) {
                0 -> { // Varint
                    var value: Long = 0
                    shift = 0
                    while (i < len) {
                        val b = data[i++].toInt()
                        value = value or ((b and 0x7F).toLong() shl shift)
                        if ((b and 0x80) == 0) break
                        shift += 7
                    }
                    result.getOrPut(fieldNum) { mutableListOf() }
                        .add(ProtoField(type = 0, intVal = value))
                }
                2 -> { // Length-delimited
                    var length: Long = 0
                    shift = 0
                    while (i < len) {
                        val b = data[i++].toInt()
                        length = length or ((b and 0x7F).toLong() shl shift)
                        if ((b and 0x80) == 0) break
                        shift += 7
                    }
                    val intLen = length.toInt().coerceAtMost(len - i)
                    val value = data.copyOfRange(i, i + intLen)
                    i += intLen
                    result.getOrPut(fieldNum) { mutableListOf() }
                        .add(ProtoField(type = 2, bytesVal = value))
                }
                1 -> { // 64-bit
                    val value = data.copyOfRange(i, (i + 8).coerceAtMost(len))
                    i = (i + 8).coerceAtMost(len)
                    result.getOrPut(fieldNum) { mutableListOf() }
                        .add(ProtoField(type = 1, bytesVal = value))
                }
                5 -> { // 32-bit
                    val value = data.copyOfRange(i, (i + 4).coerceAtMost(len))
                    i = (i + 4).coerceAtMost(len)
                    result.getOrPut(fieldNum) { mutableListOf() }
                        .add(ProtoField(type = 5, bytesVal = value))
                }
                else -> break
            }
        }
        return result
    }
}
