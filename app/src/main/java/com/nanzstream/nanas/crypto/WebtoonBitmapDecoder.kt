package com.nanzstream.nanas.crypto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import com.nanzstream.nanas.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

object WebtoonBitmapDecoder {

    private const val MAX_SLICE_HEIGHT = 2048

    private fun md5(str: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(str.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Downloads (or loads from disk cache/local storage) a manga/webtoon image,
     * decrypts if necessary, and splits tall images (height > 2048px) into vertical
     * slices so that Android OpenGL GPU texture limits (GL_MAX_TEXTURE_SIZE) are never exceeded.
     */
    suspend fun loadPageSlices(
        context: Context,
        pageUrl: String,
        keyHex: String? = null,
        ivHex: String? = null
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = pageUrl.trim()
            if (cleanUrl.isBlank()) return@withContext emptyList()

            // 1. If it's a local file (e.g. offline downloaded chapter)
            if (cleanUrl.startsWith("file://") || cleanUrl.startsWith("/")) {
                val filePath = cleanUrl.removePrefix("file://")
                val localFile = File(filePath)
                if (localFile.exists() && localFile.length() > 0) {
                    return@withContext decodeFileOrBytes(localFile = localFile, keyHex = keyHex, ivHex = ivHex)
                }
            }

            // 2. Network image: Check disk cache first
            val cacheDir = File(context.cacheDir, "manga_page_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val cacheFile = File(cacheDir, md5(cleanUrl) + ".cache")
            if (cacheFile.exists() && cacheFile.length() > 0) {
                val cachedSlices = decodeFileOrBytes(localFile = cacheFile, keyHex = keyHex, ivHex = ivHex)
                if (cachedSlices.isNotEmpty()) return@withContext cachedSlices
            }

            // 3. Download via ApiClient OkHttpClient
            val referer = when {
                cleanUrl.contains("animasu") -> "https://animasu.love/"
                cleanUrl.contains("bacakomik") || cleanUrl.contains(".lol") || cleanUrl.contains(".lat") || cleanUrl.contains(".pics") -> "https://bacakomik.my/"
                else -> "https://bacakomik.my/"
            }

            val req = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Referer", referer)
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .build()

            val resp = ApiClient.okHttpClient.newCall(req).execute()
            if (!resp.isSuccessful || resp.body == null) {
                return@withContext emptyList()
            }

            val tempFile = File(cacheDir, cacheFile.name + ".tmp")
            resp.body!!.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.exists() && tempFile.length() > 0) {
                tempFile.renameTo(cacheFile)
                return@withContext decodeFileOrBytes(localFile = cacheFile, keyHex = keyHex, ivHex = ivHex)
            }

            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun decodeFileOrBytes(
        localFile: File,
        keyHex: String?,
        ivHex: String?
    ): List<Bitmap> {
        return try {
            val isEncrypted = !keyHex.isNullOrBlank() && !ivHex.isNullOrBlank()

            if (isEncrypted) {
                val rawBytes = localFile.readBytes()
                val decrypted = MangaDecryptor.decrypt(rawBytes, keyHex, ivHex)
                sliceFromStream { ByteArrayInputStream(decrypted) }
            } else {
                sliceFromStream { FileInputStream(localFile) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun sliceFromStream(streamProvider: () -> InputStream): List<Bitmap> {
        val slices = mutableListOf<Bitmap>()
        var decoder: BitmapRegionDecoder? = null

        try {
            // Try BitmapRegionDecoder
            decoder = streamProvider().use { input ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(input)
                } else {
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(input, false)
                }
            }

            if (decoder != null) {
                val width = decoder.width
                val height = decoder.height

                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inDither = true
                }

                if (height <= MAX_SLICE_HEIGHT) {
                    val bmp = decoder.decodeRegion(Rect(0, 0, width, height), opts)
                    if (bmp != null) slices.add(bmp)
                } else {
                    var top = 0
                    while (top < height) {
                        val bottom = (top + MAX_SLICE_HEIGHT).coerceAtMost(height)
                        val rect = Rect(0, top, width, bottom)
                        val slice = decoder.decodeRegion(rect, opts)
                        if (slice != null) {
                            slices.add(slice)
                        }
                        top = bottom
                    }
                }
                return slices
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                decoder?.recycle()
            } catch (e: Exception) {
                // ignore
            }
        }

        // Fallback: standard BitmapFactory decoding
        return try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val fullBmp = streamProvider().use { input ->
                BitmapFactory.decodeStream(input, null, opts)
            } ?: return emptyList()

            val w = fullBmp.width
            val h = fullBmp.height

            if (h <= MAX_SLICE_HEIGHT) {
                listOf(fullBmp)
            } else {
                val fallbackSlices = mutableListOf<Bitmap>()
                var top = 0
                while (top < h) {
                    val sliceH = (MAX_SLICE_HEIGHT).coerceAtMost(h - top)
                    val slice = Bitmap.createBitmap(fullBmp, 0, top, w, sliceH)
                    fallbackSlices.add(slice)
                    top += sliceH
                }
                fallbackSlices
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
