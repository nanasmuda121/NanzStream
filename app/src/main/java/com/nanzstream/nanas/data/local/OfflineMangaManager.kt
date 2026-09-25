package com.nanzstream.nanas.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nanzstream.nanas.data.model.MangaPageItem
import com.nanzstream.nanas.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class OfflineChapter(
    val mangaId: String,
    val mangaTitle: String,
    val chapterId: String,
    val chapterTitle: String,
    val thumbnail: String,
    val pageCount: Int,
    val downloadedAt: Long,
    val pages: List<String>
)

class OfflineMangaManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("nanzstream_offline_manga", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val offlineDir: File = File(context.filesDir, "offline_manga")

    init {
        if (!offlineDir.exists()) {
            offlineDir.mkdirs()
        }
    }

    private fun hash(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getAllOfflineChapters(): List<OfflineChapter> {
        val json = prefs.getString("chapters", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<OfflineChapter>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isChapterDownloaded(chapterId: String): Boolean {
        return getAllOfflineChapters().any { it.chapterId == chapterId }
    }

    fun getOfflineChapter(chapterId: String): OfflineChapter? {
        return getAllOfflineChapters().find { it.chapterId == chapterId }
    }

    fun getOfflinePages(chapterId: String): List<MangaPageItem> {
        val chapter = getOfflineChapter(chapterId) ?: return emptyList()
        return chapter.pages.mapIndexed { index, path ->
            MangaPageItem(
                page = index + 1,
                url = if (path.startsWith("file://")) path else "file://$path"
            )
        }
    }

    suspend fun downloadChapter(
        mangaId: String,
        mangaTitle: String,
        chapterId: String,
        chapterTitle: String,
        thumbnail: String,
        pages: List<MangaPageItem>,
        onProgress: (downloaded: Int, total: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val chapterHash = hash(chapterId)
            val mangaDir = File(offlineDir, hash(mangaId))
            val targetDir = File(mangaDir, chapterHash)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val savedPagePaths = mutableListOf<String>()
            val total = pages.size

            for ((index, page) in pages.withIndex()) {
                val pageFile = File(targetDir, "page_${"%03d".format(index + 1)}.jpg")

                // If already downloaded and valid, reuse
                if (!pageFile.exists() || pageFile.length() == 0L) {
                    val req = Request.Builder()
                        .url(page.url)
                        .header("Referer", "https://www.webtoons.com/")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .build()

                    val res = ApiClient.okHttpClient.newCall(req).execute()
                    if (res.isSuccessful && res.body != null) {
                        res.body!!.byteStream().use { input ->
                            FileOutputStream(pageFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        // Retry once
                        val retryRes = ApiClient.okHttpClient.newCall(req).execute()
                        if (retryRes.isSuccessful && retryRes.body != null) {
                            retryRes.body!!.byteStream().use { input ->
                                FileOutputStream(pageFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }

                if (pageFile.exists() && pageFile.length() > 0L) {
                    savedPagePaths.add(pageFile.absolutePath)
                }
                onProgress(index + 1, total)
            }

            if (savedPagePaths.isNotEmpty()) {
                val offlineChapter = OfflineChapter(
                    mangaId = mangaId,
                    mangaTitle = mangaTitle.ifBlank { "Komik Webtoon" },
                    chapterId = chapterId,
                    chapterTitle = chapterTitle.ifBlank { "Chapter" },
                    thumbnail = thumbnail,
                    pageCount = savedPagePaths.size,
                    downloadedAt = System.currentTimeMillis(),
                    pages = savedPagePaths
                )

                val list = getAllOfflineChapters().filter { it.chapterId != chapterId }.toMutableList()
                list.add(0, offlineChapter)
                prefs.edit().putString("chapters", gson.toJson(list)).apply()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteOfflineChapter(chapterId: String): Boolean {
        try {
            val chapter = getOfflineChapter(chapterId)
            if (chapter != null) {
                chapter.pages.forEach { path ->
                    val file = File(path.removePrefix("file://"))
                    if (file.exists()) file.delete()
                }
                val chapterHash = hash(chapterId)
                val mangaDir = File(offlineDir, hash(chapter.mangaId))
                val targetDir = File(mangaDir, chapterHash)
                if (targetDir.exists()) targetDir.deleteRecursively()
            }

            val list = getAllOfflineChapters().filter { it.chapterId != chapterId }
            prefs.edit().putString("chapters", gson.toJson(list)).apply()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
