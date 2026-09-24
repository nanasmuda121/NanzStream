package com.nanzstream.nanas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.ui.components.GlassBottomBar
import com.nanzstream.nanas.ui.components.GlassTopBar
import com.nanzstream.nanas.ui.navigation.RouteEncoder
import com.nanzstream.nanas.ui.navigation.Screen
import com.nanzstream.nanas.ui.screens.*
import com.nanzstream.nanas.ui.theme.DarkBg
import com.nanzstream.nanas.ui.theme.NanzStreamTheme
import java.net.URLDecoder

class MainActivity : ComponentActivity() {

    private val repository = MediaRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            NanzStreamTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

                val isPlayerOrReader = currentRoute.startsWith("player/") || currentRoute.startsWith("reader/")
                val isDetail = currentRoute.startsWith("detail/")

                Scaffold(
                    topBar = {
                        if (!isPlayerOrReader && !isDetail) {
                            GlassTopBar(
                                onSearchClick = { navController.navigate(Screen.Search.route) },
                                onWatchlistClick = { navController.navigate(Screen.Watchlist.route) },
                                onSettingsClick = { navController.navigate(Screen.Settings.route) }
                            )
                        }
                    },
                    bottomBar = {
                        if (!isPlayerOrReader) {
                            GlassBottomBar(
                                currentRoute = currentRoute,
                                onTabSelected = { route ->
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    },
                    containerColor = DarkBg
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkBg)
                            .padding(top = if (!isPlayerOrReader && !isDetail) innerPadding.calculateTopPadding() else androidx.compose.ui.unit.Dp.Unspecified)
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Home.route
                        ) {
                            // Home Screen (100% Pure Portal Gateway)
                            composable(Screen.Home.route) {
                                HomeScreen(
                                    onNavigateToCategory = { categoryId ->
                                        navController.navigate(Screen.Categories.createRoute(categoryId))
                                    },
                                    onNavigateToLiveTv = {
                                        navController.navigate(Screen.LiveTv.route)
                                    }
                                )
                            }

                            // Category Browse Screen
                            composable(
                                route = Screen.Categories.route,
                                arguments = listOf(navArgument("category") { defaultValue = "anime" })
                            ) { backStack ->
                                val catParam = backStack.arguments?.getString("category") ?: "anime"
                                CategoryScreen(
                                    initialCategory = CategoryType.fromId(catParam),
                                    repository = repository,
                                    onMediaClick = { item ->
                                        navController.navigate(
                                            Screen.Detail.createRoute(item.category.id, item.slug ?: item.id)
                                        )
                                    }
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

                            // Video Player Screen
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

                            // Live TV Screen
                            composable(Screen.LiveTv.route) {
                                LiveTvScreen(repository = repository)
                            }

                            // Search Screen
                            composable(Screen.Search.route) {
                                SearchScreen(
                                    repository = repository,
                                    onBackClick = { navController.popBackStack() },
                                    onMediaClick = { item ->
                                        navController.navigate(
                                            Screen.Detail.createRoute(item.category.id, item.slug ?: item.id)
                                        )
                                    }
                                )
                            }

                            // Watchlist Screen
                            composable(Screen.Watchlist.route) {
                                WatchlistScreen(
                                    onItemClick = { categoryId, slugOrUrl ->
                                        navController.navigate(
                                            Screen.Detail.createRoute(categoryId, slugOrUrl)
                                        )
                                    }
                                )
                            }

                            // Settings Screen
                            composable(Screen.Settings.route) {
                                SettingsScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}
