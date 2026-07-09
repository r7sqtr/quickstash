package com.example.quickstash.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.quickstash.QuickStashApp
import com.example.quickstash.ui.browser.BrowserScreen
import com.example.quickstash.ui.pdf.PdfScreen
import com.example.quickstash.ui.player.PlayerScreen
import com.example.quickstash.ui.search.SearchScreen
import com.example.quickstash.ui.settings.SettingsScreen
import com.example.quickstash.ui.tags.TagListScreen
import com.example.quickstash.ui.tags.TagMediaScreen
import com.example.quickstash.ui.viewer.ViewerScreen
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

@Serializable
data class BrowserRoute(val path: String)

@Serializable
data class ViewerRoute(val folderPath: String, val imagePath: String)

@Serializable
data class PlayerRoute(val folderPath: String, val videoPath: String)

@Serializable
data class PdfRoute(val pdfPath: String)

@Serializable
data object TagsRoute

@Serializable
data object SearchRoute

@Serializable
data class TagMediaRoute(val tagId: Long, val tagName: String)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as QuickStashApp
    var startDestination by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(Unit) {
        val settings = app.container.settingsStore.settings.first()
        startDestination = if (settings.isConfigured) BrowserRoute("") else SettingsRoute
    }

    val start = startDestination
    if (start == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    SharedTransitionLayout {
    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable<SettingsRoute> {
            SettingsScreen(
                onSaved = {
                    navController.navigate(BrowserRoute("")) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable<BrowserRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<BrowserRoute>()
            BrowserScreen(
                path = route.path,
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
                onOpenFolder = { path -> navController.navigate(BrowserRoute(path)) },
                onOpenImage = { imagePath ->
                    navController.navigate(ViewerRoute(route.path, imagePath))
                },
                onOpenVideo = { videoPath ->
                    navController.navigate(PlayerRoute(route.path, videoPath))
                },
                onOpenPdf = { pdfPath ->
                    navController.navigate(PdfRoute(pdfPath))
                },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenTags = { navController.navigate(TagsRoute) },
                onOpenSearch = { navController.navigate(SearchRoute) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                onOpenImage = { folderPath, imagePath ->
                    navController.navigate(ViewerRoute(folderPath, imagePath))
                },
                onOpenVideo = { folderPath, videoPath ->
                    navController.navigate(PlayerRoute(folderPath, videoPath))
                },
                onOpenPdf = { pdfPath -> navController.navigate(PdfRoute(pdfPath)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<TagsRoute> {
            TagListScreen(
                onOpenTag = { tagId, tagName ->
                    navController.navigate(TagMediaRoute(tagId, tagName))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<TagMediaRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<TagMediaRoute>()
            TagMediaScreen(
                tagId = route.tagId,
                tagName = route.tagName,
                onOpenImage = { folderPath, imagePath ->
                    navController.navigate(ViewerRoute(folderPath, imagePath))
                },
                onOpenVideo = { folderPath, videoPath ->
                    navController.navigate(PlayerRoute(folderPath, videoPath))
                },
                onOpenPdf = { pdfPath -> navController.navigate(PdfRoute(pdfPath)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<PlayerRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PlayerRoute>()
            PlayerScreen(
                folderPath = route.folderPath,
                videoPath = route.videoPath,
                onBack = { navController.popBackStack() },
            )
        }
        composable<PdfRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PdfRoute>()
            PdfScreen(
                pdfPath = route.pdfPath,
                onBack = { navController.popBackStack() },
            )
        }
        composable<ViewerRoute>(
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ViewerRoute>()
            ViewerScreen(
                folderPath = route.folderPath,
                imagePath = route.imagePath,
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
                onBack = { navController.popBackStack() },
            )
        }
    }
    }
}
