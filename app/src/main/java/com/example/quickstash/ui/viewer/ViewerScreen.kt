package com.example.quickstash.ui.viewer

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import com.example.quickstash.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.example.quickstash.data.nas.NasFile
import com.example.quickstash.ui.browser.sharedMediaModifier
import com.example.quickstash.ui.components.ActionBarItem
import com.example.quickstash.ui.components.TagDialog
import com.example.quickstash.ui.components.TierDialog
import com.example.quickstash.media.coil.NasFull
import com.example.quickstash.media.coil.NasThumb
import com.example.quickstash.media.coil.SmbThumbKeyer
import net.engawapg.lib.zoomable.ScrollGesturePropagation
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

@Composable
fun ViewerScreen(
    folderPath: String,
    imagePath: String,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: ViewerViewModel = viewModel(
        key = "viewer:$folderPath:$imagePath",
        factory = ViewerViewModel.factory(folderPath, imagePath),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            withTimeoutOrNull(1500) { snackbarHostState.showSnackbar(it) }
            viewModel.consumeMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (val current = state) {
            is ViewerUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            is ViewerUiState.Error -> {
                Text(
                    text = current.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }

            is ViewerUiState.Content -> {
                ViewerPager(
                    content = current,
                    folderPath = folderPath,
                    onBack = onBack,
                    viewModel = viewModel,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private enum class SlideshowMode { SEQUENTIAL, RANDOM }

private fun buildSlideshowUnits(
    images: List<NasFile>,
    groupOf: Map<String, Long>,
): List<List<Int>> {
    val groupUnits = LinkedHashMap<Long, MutableList<Int>>()
    val units = ArrayList<List<Int>>()
    images.forEachIndexed { index, file ->
        val gid = groupOf[file.path]
        if (gid == null) {
            units.add(listOf(index))
        } else {
            val existing = groupUnits[gid]
            if (existing == null) {
                val created = mutableListOf(index)
                groupUnits[gid] = created
                units.add(created)
            } else {
                existing.add(index)
            }
        }
    }
    return units
}

@Composable
private fun ViewerPager(
    content: ViewerUiState.Content,
    folderPath: String,
    onBack: () -> Unit,
    viewModel: ViewerViewModel,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val pagerState = rememberPagerState(initialPage = content.initialIndex) {
        content.images.size
    }
    var showPicker by remember { mutableStateOf(false) }
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var tierDialogOpen by remember { mutableStateOf(false) }
    var curationTarget by remember { mutableStateOf<NasFile?>(null) }
    val tagDialog by viewModel.tagDialog.collectAsStateWithLifecycle()
    val rotations = remember { mutableStateMapOf<String, Float>() }
    var slideshowMode by remember { mutableStateOf<SlideshowMode?>(null) }
    var slideshowMenuOpen by remember { mutableStateOf(false) }
    var slideshowRemainingMillis by remember { mutableLongStateOf(0L) }
    val slideshowSeconds by viewModel.slideshowSeconds.collectAsStateWithLifecycle()
    val groupOf by viewModel.groupOf.collectAsStateWithLifecycle()
    val view = LocalView.current
    val currentImage = content.images.getOrNull(
        pagerState.currentPage.coerceIn(content.images.indices),
    )

    var viewedPage by remember { mutableIntStateOf(-1) }
    var viewEnteredAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(pagerState.settledPage) {
        val now = SystemClock.elapsedRealtime()
        if (viewedPage >= 0 && viewedPage != pagerState.settledPage) {
            viewModel.recordView(content.images.getOrNull(viewedPage), now - viewEnteredAt)
        }
        viewedPage = pagerState.settledPage
        viewEnteredAt = now
        content.images.getOrNull(pagerState.settledPage + 2)
            ?.let(viewModel::prefetchOriginal)
        content.images.getOrNull(pagerState.settledPage - 2)
            ?.let(viewModel::prefetchOriginal)
    }
    DisposableEffect(Unit) {
        onDispose {
            if (viewedPage >= 0) {
                viewModel.recordView(
                    content.images.getOrNull(viewedPage),
                    SystemClock.elapsedRealtime() - viewEnteredAt,
                )
            }
        }
    }

    DisposableEffect(slideshowMode) {
        view.keepScreenOn = slideshowMode != null
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(slideshowMode, content.images.size, slideshowSeconds, groupOf) {
        val mode = slideshowMode ?: return@LaunchedEffect
        val size = content.images.size
        if (size <= 1) return@LaunchedEffect
        val intervalMillis = slideshowSeconds * 1000L
        val randomQueue = ArrayDeque<Int>()
        while (true) {
            val next = when (mode) {
                SlideshowMode.SEQUENTIAL -> (pagerState.currentPage + 1) % size
                SlideshowMode.RANDOM -> {
                    if (randomQueue.isEmpty()) {
                        randomQueue.addAll(
                            buildSlideshowUnits(content.images, groupOf).shuffled().flatten(),
                        )
                    }
                    randomQueue.removeFirst()
                }
            }
            viewModel.prefetchOriginal(content.images[next])
            slideshowRemainingMillis = intervalMillis
            while (slideshowRemainingMillis > 0) {
                delay(33)
                slideshowRemainingMillis -= 33
            }
            pagerState.animateScrollToPage(next)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val image = content.images[page]
            ViewerPage(
                image = image,
                settled = pagerState.settledPage == page,
                rotation = rotations[image.path] ?: 0f,
                onTap = { if (slideshowMode != null) slideshowMode = null },
                sharedModifier = if (pagerState.settledPage == page) {
                    sharedMediaModifier(
                        sharedTransitionScope,
                        animatedVisibilityScope,
                        image.path,
                    )
                } else {
                    Modifier
                },
            )
        }

        if (slideshowMode != null) {
            val intervalMillis = (slideshowSeconds * 1000L).coerceAtLeast(1L)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = {
                        (slideshowRemainingMillis.toFloat() / intervalMillis).coerceIn(0f, 1f)
                    },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    strokeWidth = 3.dp,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    text = "${(slideshowRemainingMillis + 999) / 1000}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        AnimatedVisibility(
            visible = slideshowMode == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "戻る",
                        tint = Color.White,
                    )
                }
                Text(
                    text = currentImage?.name.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Box {
                    IconButton(onClick = { slideshowMenuOpen = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play),
                            contentDescription = "スライドショー",
                            tint = Color.White,
                        )
                    }
                    DropdownMenu(
                        expanded = slideshowMenuOpen,
                        onDismissRequest = { slideshowMenuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("順番に再生") },
                            onClick = {
                                slideshowMenuOpen = false
                                slideshowMode = SlideshowMode.SEQUENTIAL
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("ランダム再生") },
                            onClick = {
                                slideshowMenuOpen = false
                                slideshowMode = SlideshowMode.RANDOM
                            },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = slideshowMode == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        ),
                    )
                    .navigationBarsPadding()
                    .padding(vertical = 4.dp),
            ) {
                ActionBarItem(
                    iconRes = R.drawable.ic_star,
                    label = "Tier",
                    enabled = currentImage != null,
                    tint = Color.White,
                    modifier = Modifier.weight(1f),
                ) {
                    curationTarget = currentImage
                    tierDialogOpen = true
                }
                ActionBarItem(
                    iconRes = R.drawable.ic_tag,
                    label = "タグ",
                    enabled = currentImage != null,
                    tint = Color.White,
                    modifier = Modifier.weight(1f),
                ) {
                    currentImage?.let {
                        curationTarget = it
                        viewModel.openTagDialog(it)
                    }
                }
                ActionBarItem(
                    iconRes = R.drawable.ic_rotate,
                    label = "回転",
                    enabled = currentImage != null,
                    tint = Color.White,
                    modifier = Modifier.weight(1f),
                ) {
                    currentImage?.let {
                        rotations[it.path] = ((rotations[it.path] ?: 0f) + 90f) % 360f
                    }
                }
                ActionBarItem(
                    iconRes = R.drawable.ic_move_to_folder,
                    label = "移動",
                    enabled = currentImage != null,
                    tint = Color.White,
                    modifier = Modifier.weight(1f),
                ) {
                    showPicker = true
                }
                ActionBarItem(
                    iconRes = R.drawable.ic_delete,
                    label = "削除",
                    enabled = currentImage != null,
                    tint = Color.White,
                    modifier = Modifier.weight(1f),
                ) {
                    deleteDialogOpen = true
                }
            }
        }
    }

    if (tierDialogOpen) {
        curationTarget?.let { target ->
            TierDialog(
                onSelect = { tier ->
                    tierDialogOpen = false
                    viewModel.setTier(target, tier)
                },
                onDismiss = { tierDialogOpen = false },
            )
        } ?: run { tierDialogOpen = false }
    }

    tagDialog?.let { rows ->
        curationTarget?.let { target ->
            TagDialog(
                rows = rows,
                onToggle = { tagId, apply -> viewModel.toggleTag(tagId, apply, target) },
                onCreate = { name -> viewModel.createTagAndApply(name, target) },
                onDismiss = viewModel::closeTagDialog,
            )
        }
    }

    if (deleteDialogOpen && currentImage != null) {
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text("削除の確認") },
            text = { Text("${currentImage.name} を NAS 上から完全に削除します。元に戻せません。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDialogOpen = false
                        viewModel.deleteImage(currentImage)
                    },
                ) {
                    Text("削除する", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogOpen = false }) {
                    Text("キャンセル")
                }
            },
        )
    }

    if (showPicker && currentImage != null) {
        FolderPickerDialog(
            startPath = folderPath,
            loadDirectories = viewModel::listDirectories,
            onDismiss = { showPicker = false },
            onSelect = { destDir ->
                showPicker = false
                viewModel.moveImage(currentImage, destDir)
            },
        )
    }
}

@Composable
private fun ViewerPage(
    image: NasFile,
    settled: Boolean,
    rotation: Float,
    onTap: () -> Unit,
    sharedModifier: Modifier = Modifier,
) {
    val zoomState = rememberZoomState()
    val context = LocalPlatformContext.current

    LaunchedEffect(settled) {
        if (!settled) {
            zoomState.reset()
        }
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(NasFull.from(image))
            .placeholderMemoryCacheKey(
                MemoryCache.Key(SmbThumbKeyer.key(NasThumb.from(image))),
            )
            .build(),
        contentDescription = image.name,
        onState = { imageState ->
            if (imageState is AsyncImagePainter.State.Success) {
                zoomState.setContentSize(imageState.painter.intrinsicSize)
            }
        },
        modifier = sharedModifier
            .fillMaxSize()
            .rotate(rotation)
            .zoomable(
                zoomState = zoomState,
                scrollGesturePropagation = ScrollGesturePropagation.ContentEdge,
                onTap = { onTap() },
            ),
    )
}
