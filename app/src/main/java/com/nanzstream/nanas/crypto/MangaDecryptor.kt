package com.nanzstream.nanas.crypto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object MangaDecryptor {

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun decrypt(encryptedBytes: ByteArray, keyHex: String?, ivHex: String?): ByteArray {
        if (keyHex.isNullOrBlank() || ivHex.isNullOrBlank()) {
            return encryptedBytes
        }
        return try {
            val keyBytes = hexStringToByteArray(keyHex.trim())
            val ivBytes = hexStringToByteArray(ivHex.trim())

            val secretKey = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(ivBytes)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            cipher.doFinal(encryptedBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            encryptedBytes
        }
    }

    suspend fun loadAndDecryptBitmap(imageUrl: String, keyHex: String?, ivHex: String?): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(imageUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                conn.setRequestProperty("Referer", "https://global.manga-up.com/")
                conn.connect()

                if (conn.responseCode == 200) {
                    val rawBytes = conn.inputStream.use { it.readBytes() }
                    val decrypted = decrypt(rawBytes, keyHex, ivHex)
                    BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
}
