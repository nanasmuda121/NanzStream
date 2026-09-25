package com.nanzstream.nanas.ui.navigation

import android.util.Base64

object RouteEncoder {
    fun encode(value: String): String {
        return try {
            Base64.encodeToString(
                value.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        } catch (e: Exception) {
            java.net.URLEncoder.encode(value, "UTF-8")
        }
    }

    fun decode(encoded: String): String {
        return try {
            var padded = encoded.trim()
            val rem = padded.length % 4
            if (rem > 0) {
                padded += "=".repeat(4 - rem)
            }
            val bytes = Base64.decode(
                padded,
                Base64.URL_SAFE or Base64.NO_WRAP
            )
            val decodedStr = String(bytes, Charsets.UTF_8)
            val allowedChars = "-_.~%/?&=:+"
            if (decodedStr.isNotBlank() && (decodedStr.startsWith("http") || decodedStr.all { it.isLetterOrDigit() || it in allowedChars })) {
                decodedStr
            } else {
                java.net.URLDecoder.decode(encoded, "UTF-8")
            }
        } catch (e: Exception) {
            try {
                java.net.URLDecoder.decode(encoded, "UTF-8")
            } catch (ex: Exception) {
                encoded
            }
        }
    }
}

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Categories : Screen("categories?category={category}") {
        fun createRoute(category: String = "anime"): String = "categories?category=$category"
    }
    data object Detail : Screen("detail/{category}/{idOrSlug}") {
        fun createRoute(category: String, idOrSlug: String): String =
            "detail/$category/${RouteEncoder.encode(idOrSlug)}"
    }
    data object Player : Screen("player/{category}/{title}/{targetUrl}/{episode}") {
        fun createRoute(category: String, title: String, targetUrl: String, episode: Int = 1): String =
            "player/$category/${RouteEncoder.encode(title)}/${RouteEncoder.encode(targetUrl)}/$episode"
    }
    data object Reader : Screen("reader/{mangaId}/{chapterId}/{chapterTitle}") {
        fun createRoute(mangaId: String, chapterId: String, chapterTitle: String): String =
            "reader/${RouteEncoder.encode(mangaId)}/${RouteEncoder.encode(chapterId)}/${RouteEncoder.encode(chapterTitle)}"
    }
    data object LiveTv : Screen("livetv")
    data object Search : Screen("search")
    data object Watchlist : Screen("watchlist")
    data object Settings : Screen("settings")
}
