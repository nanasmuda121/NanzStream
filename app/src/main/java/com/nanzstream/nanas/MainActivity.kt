package com.nanzstream.nanas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.ui.navigation.RouteEncoder
import com.nanzstream.nanas.ui.navigation.Screen
import com.nanzstream.nanas.ui.screens.*
import com.nanzstream.nanas.ui.screens.portal.*
import com.nanzstream.nanas.ui.theme.CanvasBlack
import com.nanzstream.nanas.ui.theme.NanzStreamTheme

class MainActivity : ComponentActivity() {

    private val repository = MediaRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            NanzStreamTheme {
                val navController = rememberNavController()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CanvasBlack)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route
                    ) {
                        // 1. Halaman Portal (Home): Polos, Landing Page Utama, 0 Navbar
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onNavigateToCategory = { categoryId ->
                                    when (categoryId) {
                                        "anime" -> navController.navigate("universe_anime")
                                        "manga", "komik" -> navController.navigate("universe_komik")
                                        "donghua" -> navController.navigate("universe_donghua")
                                        else -> navController.navigate("universe_anime")
                                    }
                                },
                                onNavigateToLiveTv = {
                                    navController.navigate("universe_livetv")
                                }
                            )
                        }

                        // 2. Sub-App Kategori: Anime Universe (Samehadaku) dengan Navbar Khusus
                        composable("universe_anime") {
                            AnimePortalScreen(
                                repository = repository,
                                onBackToPortal = { navController.popBackStack() },
                                onAnimeClick = { item ->
                                    navController.navigate(
                                        Screen.Detail.createRoute("anime", item.slug ?: item.id)
                                    )
                                }
                            )
                        }

                        // 3. Sub-App Kategori: Komik Universe (Line Webtoon) dengan Navbar Khusus
                        composable("universe_komik") {
                            KomikPortalScreen(
                                repository = repository,
                                onBackToPortal = { navController.popBackStack() },
                                onKomikClick = { item ->
                                    navController.navigate(
                                        Screen.Detail.createRoute("manga", item.slug ?: item.id)
                                    )
                                }
                            )
                        }

                        // 4. Sub-App Kategori: Donghua Universe (Anichin 3D) dengan Navbar Khusus
                        composable("universe_donghua") {
                            DonghuaPortalScreen(
                                repository = repository,
                                onBackToPortal = { navController.popBackStack() },
                                onDonghuaClick = { item ->
                                    navController.navigate(
                                        Screen.Detail.createRoute("donghua", item.slug ?: item.id)
                                    )
                                }
                            )
                        }

                        // 5. Sub-App Kategori: Live TV Universe (TV Indonesia) dengan Navbar Khusus
                        composable("universe_livetv") {
                            LiveTvPortalScreen(
                                repository = repository,
                                onBackToPortal = { navController.popBackStack() }
                            )
                        }

                        // Unified Detail Screen
                        composable(
                            route = Screen.Detail.route,
                            arguments = listOf(
                                navArgument("category") { type = NavType.StringType },
                                navArgument("idOrSlug") { type = NavType.StringType }
                            )
                        ) { backStack ->
                            val categoryStr = backStack.arguments?.getString("category") ?: "anime"
                            val rawIdOrSlug = backStack.arguments?.getString("idOrSlug") ?: ""
                            val idOrSlug = RouteEncoder.decode(rawIdOrSlug)
                            val category = CategoryType.fromId(categoryStr)

                            DetailScreen(
                                category = category,
                                idOrSlug = idOrSlug,
                                repository = repository,
                                onBackClick = { navController.popBackStack() },
                                onPlayEpisode = { detail, ep ->
                                    navController.navigate(
                                        Screen.Player.createRoute(
                                            category = detail.category.id,
                                            title = "${detail.title} - ${ep.title}",
                                            targetUrl = ep.url,
                                            episode = ep.episodeNumber.toIntOrNull() ?: 1
                                        )
                                    )
                                },
                                onReadChapter = { detail, ch ->
                                    navController.navigate(
                                        Screen.Reader.createRoute(
                                            mangaId = detail.id,
                                            chapterId = ch.id,
                                            chapterTitle = ch.title
                                        )
                                    )
                                }
                            )
                        }

                        // Video Player Screen (Anime & Donghua)
                        composable(
                            route = Screen.Player.route,
                            arguments = listOf(
                                navArgument("category") { type = NavType.StringType },
                                navArgument("title") { type = NavType.StringType },
                                navArgument("targetUrl") { type = NavType.StringType },
                                navArgument("episode") { type = NavType.IntType }
                            )
                        ) { backStack ->
                            val categoryStr = backStack.arguments?.getString("category") ?: "anime"
                            val rawTitle = backStack.arguments?.getString("title") ?: ""
                            val rawTargetUrl = backStack.arguments?.getString("targetUrl") ?: ""
                            val title = RouteEncoder.decode(rawTitle)
                            val targetUrl = RouteEncoder.decode(rawTargetUrl)
                            val episode = backStack.arguments?.getInt("episode") ?: 1

                            VideoPlayerScreen(
                                category = CategoryType.fromId(categoryStr),
                                title = title,
                                targetUrl = targetUrl,
                                initialEpisode = episode,
                                repository = repository,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        // Manga / Webtoon Reader Screen
                        composable(
                            route = Screen.Reader.route,
                            arguments = listOf(
                                navArgument("mangaId") { type = NavType.StringType },
                                navArgument("chapterId") { type = NavType.StringType },
                                navArgument("chapterTitle") { type = NavType.StringType }
                            )
                        ) { backStack ->
                            val rawMangaId = backStack.arguments?.getString("mangaId") ?: "1"
                            val rawChapterId = backStack.arguments?.getString("chapterId") ?: "1"
                            val rawChapterTitle = backStack.arguments?.getString("chapterTitle") ?: ""
                            val mangaId = RouteEncoder.decode(rawMangaId)
                            val chapterId = RouteEncoder.decode(rawChapterId)
                            val chapterTitle = RouteEncoder.decode(rawChapterTitle)

                            MangaReaderScreen(
                                mangaId = mangaId,
                                initialChapterId = chapterId,
                                chapterTitle = chapterTitle,
                                repository = repository,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
