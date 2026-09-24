package com.nanzstream.nanas.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Categories : Screen("categories?category={category}") {
        fun createRoute(category: String = "anime"): String = "categories?category=$category"
    }
    data object Detail : Screen("detail/{category}/{idOrSlug}") {
        fun createRoute(category: String, idOrSlug: String): String =
            "detail/$category/${java.net.URLEncoder.encode(idOrSlug, "UTF-8")}"
    }
    data object Player : Screen("player/{category}/{title}/{targetUrl}/{episode}") {
        fun createRoute(category: String, title: String, targetUrl: String, episode: Int = 1): String =
            "player/$category/${java.net.URLEncoder.encode(title, "UTF-8")}/${java.net.URLEncoder.encode(targetUrl, "UTF-8")}/$episode"
    }
    data object Reader : Screen("reader/{mangaId}/{chapterId}/{chapterTitle}") {
        fun createRoute(mangaId: String, chapterId: String, chapterTitle: String): String =
            "reader/$mangaId/$chapterId/${java.net.URLEncoder.encode(chapterTitle, "UTF-8")}"
    }
    data object LiveTv : Screen("livetv")
    data object Search : Screen("search")
    data object Watchlist : Screen("watchlist")
    data object Settings : Screen("settings")
}
