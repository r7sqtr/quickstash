package com.example.quickstash.ui.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.example.quickstash.R
import com.example.quickstash.data.local.TagWithCount
import com.example.quickstash.data.nas.MediaType
import com.example.quickstash.data.nas.NasFile
import com.example.quickstash.domain.MediaFilter
import com.example.quickstash.domain.SortKey
import com.example.quickstash.domain.SortSpec
import com.example.quickstash.domain.TierInfo
import com.example.quickstash.media.coil.NasPdfThumb
import com.example.quickstash.media.coil.NasThumb
import com.example.quickstash.media.coil.NasVideoThumb
import com.example.quickstash.ui.components.ActionBarItem
import com.example.quickstash.ui.components.GridScrollbar
import com.example.quickstash.ui.components.shimmerBackground
import com.example.quickstash.ui.components.TagDialog
import com.example.quickstash.ui.components.TierDialog
import com.example.quickstash.ui.components.tierColor
import com.example.quickstash.ui.viewer.FolderPickerDialog
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    path: String,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onOpenFolder: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenPdf: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenSearch: () -> Unit,
    onBack: () -> Unit,
    viewModel: BrowserViewModel = viewModel(
        key = "browser:$path",
        factory = BrowserViewModel.factory(path),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sortSpec by viewModel.sortSpec.collectAsStateWithLifecycle()
    val folderInfo by viewModel.folderInfo.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val groupBadges by viewModel.groupBadges.collectAsStateWithLifecycle()
    val tierInfo by viewModel.tierInfo.collectAsStateWithLifecycle()
    val tagDialog by viewModel.tagDialog.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val filterInfo by viewModel.filterInfo.collectAsStateWithLifecycle()
    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
    val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val showTierBadges by viewModel.showTierBadges.collectAsStateWithLifecycle()
    val title = path.substringAfterLast('/').ifEmpty { "QuickStash" }
    var sortFilterOpen by remember { mutableStateOf(false) }
    var tierDialogOpen by remember { mutableStateOf(false) }
    var deleteDialogOpen by remember { mutableStateOf(false) }
    var movePickerOpen by remember { mutableStateOf(false) }
    var folderToDelete by remember { mutableStateOf<NasFile?>(null) }
    var renameDialogOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var fabMenuOpen by remember { mutableStateOf(false) }
    var createFolderOpen by remember { mutableStateOf(false) }
    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        viewModel.uploadFiles(uris)
    }
    val fileUploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        viewModel.uploadFiles(uris)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshIfLoaded()
        onPauseOrDispose { }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            val currentSelection = selection
            if (currentSelection != null) {
                TopAppBar(
                    title = { Text("${currentSelection.size}件選択") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::cancelSelection) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "選択解除")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::toggleSelectAll) {
                            Icon(
                                painter = painterResource(R.drawable.ic_select_all),
                                contentDescription = "すべて選択",
                            )
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        if (path.isNotEmpty()) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "戻る",
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenTags) {
                            Icon(
                                painter = painterResource(R.drawable.ic_tag),
                                contentDescription = "タグ一覧",
                            )
                        }
                        val sortFilterActive = !filter.isEmpty || sortSpec != SortSpec.DEFAULT
                        IconButton(
                            onClick = {
                                viewModel.loadAvailableTags()
                                sortFilterOpen = true
                            },
                            colors = if (sortFilterActive) {
                                IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                IconButtonDefaults.iconButtonColors()
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_filter),
                                contentDescription = "並べ替え・フィルタ",
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "設定")
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val currentSelection = selection
            if (currentSelection != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ActionBarItem(
                            iconRes = R.drawable.ic_group_add,
                            label = "グループ",
                            enabled = currentSelection.size >= 2,
                            onClick = viewModel::groupSelected,
                            modifier = Modifier.weight(1f),
                        )
                        ActionBarItem(
                            iconRes = R.drawable.ic_tag,
                            label = "タグ",
                            onClick = viewModel::openTagDialog,
                            modifier = Modifier.weight(1f),
                        )
                        ActionBarItem(
                            iconRes = R.drawable.ic_star,
                            label = "Tier",
                            onClick = { tierDialogOpen = true },
                            modifier = Modifier.weight(1f),
                        )
                        ActionBarItem(
                            iconRes = R.drawable.ic_move_to_folder,
                            label = "移動",
                            onClick = { movePickerOpen = true },
                            modifier = Modifier.weight(1f),
                        )
                        ActionBarItem(
                            iconRes = R.drawable.ic_delete,
                            label = "削除",
                            onClick = { deleteDialogOpen = true },
                            modifier = Modifier.weight(1f),
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            ActionBarItem(
                                iconRes = R.drawable.ic_more_vert,
                                label = "その他",
                                onClick = { overflowOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            DropdownMenu(
                                expanded = overflowOpen,
                                onDismissRequest = { overflowOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("一括リネーム") },
                                    onClick = {
                                        overflowOpen = false
                                        renameDialogOpen = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("グループ解除") },
                                    onClick = {
                                        overflowOpen = false
                                        viewModel.ungroupSelected()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        when (val current = state) {
            is BrowserUiState.Loading -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    userScrollEnabled = false,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    items(24) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .shimmerBackground(),
                        )
                    }
                }
            }

            is BrowserUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "読み込みに失敗しました",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Button(onClick = viewModel::load) {
                        Text("再試行")
                    }
                }
            }

            is BrowserUiState.Content -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    SearchBarRow(onClick = onOpenSearch)
                    uploadProgress?.let { (current, total) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "アップロード中 $current / $total 件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    filterInfo?.let { (visibleCount, totalCount) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            Text(
                                text = "フィルタ適用中: $visibleCount / $totalCount 件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { viewModel.setFilter(MediaFilter.EMPTY) }) {
                                Text("クリア")
                            }
                        }
                    }
                    PullToRefreshBox(
                        isRefreshing = false,
                        onRefresh = viewModel::load,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        BrowserContent(
                            directories = current.directories,
                            media = current.media,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            columns = gridColumns,
                            onAdjustColumns = viewModel::adjustGridColumns,
                            sectionByMonth = sortSpec.entries.firstOrNull()?.key == SortKey.CREATED,
                            folderInfo = folderInfo,
                            selection = selection,
                            groupBadges = groupBadges,
                            tierInfo = if (showTierBadges) tierInfo else emptyMap(),
                            onFolderVisible = viewModel::ensureFolderInfo,
                            onOpenFolder = onOpenFolder,
                            onLongPressFolder = { folderToDelete = it },
                            onOpenImage = onOpenImage,
                            onOpenVideo = onOpenVideo,
                            onOpenPdf = onOpenPdf,
                            onLongPressMedia = viewModel::startSelection,
                            onToggleSelect = viewModel::toggleSelect,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        )
                    }
                }
            }
        }
    }

    if (sortFilterOpen) {
        SortFilterSheet(
            spec = sortSpec,
            filter = filter,
            tags = availableTags,
            onToggleKey = viewModel::toggleSortKey,
            onFilterChange = viewModel::setFilter,
            onDismiss = { sortFilterOpen = false },
        )
    }

    tagDialog?.let { rows ->
        TagDialog(
            rows = rows,
            onToggle = viewModel::toggleTag,
            onCreate = viewModel::createTagAndApply,
            onDismiss = viewModel::closeTagDialog,
        )
    }

    AnimatedVisibility(
        visible = fabMenuOpen,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) {
                    detectTapGestures { fabMenuOpen = false }
                },
        )
    }
    val fabState = state
    if (selection == null && fabState is BrowserUiState.Content) {
        val fabRotation by animateFloatAsState(
            targetValue = if (fabMenuOpen) 45f else 0f,
            label = "fabRotation",
        )
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            AnimatedVisibility(
                visible = fabMenuOpen,
                enter = fadeIn() + scaleIn(initialScale = 0.7f) + slideInVertically { it / 3 },
                exit = fadeOut() + scaleOut(targetScale = 0.7f) + slideOutVertically { it / 3 },
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                            ExtendedFloatingActionButton(
                                text = { Text("新規フォルダ") },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_folder_empty),
                                        contentDescription = null,
                                        tint = androidx.compose.ui.graphics.Color.Unspecified,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    fabMenuOpen = false
                                    createFolderOpen = true
                                },
                            )
                            ExtendedFloatingActionButton(
                                text = { Text("ファイル") },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_upload),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    fabMenuOpen = false
                                    if (uploadProgress == null) {
                                        fileUploadLauncher.launch(arrayOf("*/*"))
                                    }
                                },
                            )
                            ExtendedFloatingActionButton(
                                text = { Text("写真・動画") },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_upload),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    fabMenuOpen = false
                                    if (uploadProgress == null) {
                                        uploadLauncher.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                                            ),
                                        )
                                    }
                                },
                            )
                    }
            }
            FloatingActionButton(onClick = { fabMenuOpen = !fabMenuOpen }) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = if (fabMenuOpen) "メニューを閉じる" else "追加",
                    modifier = Modifier.rotate(fabRotation),
                )
            }
        }
    }
    }

    if (renameDialogOpen) {
        val count = selection?.size ?: 0
        var baseName by remember { mutableStateOf("") }
        val digits = count.toString().length.coerceAtLeast(2)
        val firstExt = selection?.firstOrNull()
            ?.substringAfterLast('.', "")
            .orEmpty()
        val previewName = buildString {
            append(baseName.ifBlank { "名前" })
            append('_')
            append("1".padStart(digits, '0'))
            if (firstExt.isNotEmpty()) {
                append('.')
                append(firstExt)
            }
        }
        AlertDialog(
            onDismissRequest = { renameDialogOpen = false },
            title = { Text("一括リネーム") },
            text = {
                Column {
                    Text(
                        text = "選択した${count}件を選択順に連番でリネームします。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    OutlinedTextField(
                        value = baseName,
                        onValueChange = { baseName = it },
                        label = { Text("ベース名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "例: $previewName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameDialogOpen = false
                        viewModel.renameSelection(baseName)
                    },
                    enabled = baseName.isNotBlank(),
                ) {
                    Text("リネーム")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogOpen = false }) {
                    Text("キャンセル")
                }
            },
        )
    }

    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("フォルダの削除") },
            text = {
                Text("フォルダ「${folder.name}」を中のファイルごと NAS 上から完全に削除します。元に戻せません。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        folderToDelete = null
                        viewModel.deleteFolder(folder.path)
                    },
                ) {
                    Text("削除する", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text("キャンセル")
                }
            },
        )
    }

    if (createFolderOpen) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createFolderOpen = false },
            title = { Text("新規フォルダ") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("フォルダ名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        createFolderOpen = false
                        viewModel.createFolder(folderName)
                    },
                    enabled = folderName.isNotBlank(),
                ) {
                    Text("作成")
                }
            },
            dismissButton = {
                TextButton(onClick = { createFolderOpen = false }) {
                    Text("キャンセル")
                }
            },
        )
    }

    if (movePickerOpen) {
        FolderPickerDialog(
            startPath = path,
            loadDirectories = viewModel::listDirectories,
            onDismiss = { movePickerOpen = false },
            onSelect = { destDir ->
                movePickerOpen = false
                viewModel.moveSelection(destDir)
            },
        )
    }

    if (tierDialogOpen) {
        TierDialog(
            onSelect = { tier ->
                tierDialogOpen = false
                viewModel.setTierForSelection(tier)
            },
            onDismiss = { tierDialogOpen = false },
        )
    }

    if (deleteDialogOpen) {
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text("削除の確認") },
            text = {
                Text("${selection?.size ?: 0}件のファイルを NAS 上から完全に削除します。元に戻せません。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDialogOpen = false
                        viewModel.deleteSelection()
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
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SortFilterSheet(
    spec: SortSpec,
    filter: MediaFilter,
    tags: List<TagWithCount>,
    onToggleKey: (SortKey) -> Unit,
    onFilterChange: (MediaFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            var sortHelpOpen by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Text(
                    text = "並べ替え",
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = { sortHelpOpen = !sortHelpOpen }) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "並べ替えの説明",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (sortHelpOpen) {
                Text(
                    text = "タップしたキーが最優先(①)になる。①を再タップで昇順⇄降順、降順からもう一度で解除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            SortKey.entries.chunked(2).forEach { rowKeys ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    rowKeys.forEach { key ->
                        SortKeyCell(
                            key = key,
                            spec = spec,
                            onToggleKey = onToggleKey,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowKeys.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "フィルタ",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onFilterChange(MediaFilter.EMPTY) },
                    enabled = !filter.isEmpty,
                ) {
                    Text("クリア")
                }
            }
            Text(
                text = "タイプ",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    MediaType.IMAGE to "画像",
                    MediaType.VIDEO to "動画",
                    MediaType.PDF to "PDF",
                ).forEach { (type, label) ->
                    FilterChip(
                        selected = type in filter.types,
                        onClick = {
                            onFilterChange(filter.copy(types = toggleElement(filter.types, type)))
                        },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                text = "手動Tier",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("S", "A", "B", "C").forEach { tier ->
                    FilterChip(
                        selected = tier in filter.manualTiers,
                        onClick = {
                            onFilterChange(
                                filter.copy(
                                    manualTiers = toggleElement(filter.manualTiers, tier),
                                    autoTiers = emptySet(),
                                ),
                            )
                        },
                        label = { Text(tier) },
                    )
                }
            }
            Text(
                text = "自動Tier",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("S", "A", "B", "C").forEach { tier ->
                    FilterChip(
                        selected = tier in filter.autoTiers,
                        onClick = {
                            onFilterChange(
                                filter.copy(
                                    autoTiers = toggleElement(filter.autoTiers, tier),
                                    manualTiers = emptySet(),
                                ),
                            )
                        },
                        label = { Text(tier) },
                    )
                }
            }
            if (tags.isNotEmpty()) {
                Text(
                    text = "タグ（いずれかを含む）",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in filter.tagIds,
                            onClick = {
                                onFilterChange(
                                    filter.copy(tagIds = toggleElement(filter.tagIds, tag.id)),
                                )
                            },
                            label = { Text("${tag.name} (${tag.count})") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortKeyCell(
    key: SortKey,
    spec: SortSpec,
    onToggleKey: (SortKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = spec.entries.indexOfFirst { it.key == key }
    val entry = spec.entries.getOrNull(index).takeIf { index >= 0 }
    val selected = entry != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
            )
            .clickable { onToggleKey(key) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${index + 1}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
        }
        Text(
            text = key.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (entry != null && key != SortKey.SIMILAR) {
            Text(
                text = if (entry.ascending) "↑" else "↓",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun BrowserContent(
    directories: List<NasFile>,
    media: List<NasFile>,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    columns: Int,
    onAdjustColumns: (Int) -> Unit,
    sectionByMonth: Boolean,
    folderInfo: Map<String, FolderPreview>,
    selection: List<String>?,
    groupBadges: Map<String, String>,
    tierInfo: Map<String, TierInfo>,
    onFolderVisible: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onLongPressFolder: (NasFile) -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenPdf: (String) -> Unit,
    onLongPressMedia: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    if (directories.isEmpty() && media.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text("このフォルダは空です")
        }
        return
    }
    val gridState = rememberLazyGridState()
    val currentAdjust = rememberUpdatedState(onAdjustColumns)
    val sections = if (sectionByMonth) {
        remember(media) { monthSections(media) }
    } else {
        emptyList()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .pointerInput(Unit) {
                awaitEachGesture {
                    var zoom = 1f
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.count { it.pressed }
                        if (pressed == 0) break
                        if (pressed >= 2) {
                            zoom *= event.calculateZoom()
                            event.changes.forEach { it.consume() }
                            if (zoom > PINCH_STEP) {
                                currentAdjust.value(-1)
                                zoom = 1f
                            } else if (zoom < 1f / PINCH_STEP) {
                                currentAdjust.value(1)
                                zoom = 1f
                            }
                        }
                    }
                }
            },
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(
                items = directories,
                key = { it.path },
            ) { dir ->
                FolderTile(
                    dir = dir,
                    info = folderInfo[dir.path],
                    onVisible = onFolderVisible,
                    onClick = { onOpenFolder(dir.path) },
                    onLongClick = { onLongPressFolder(dir) },
                )
            }
            val mediaCell: @Composable (NasFile) -> Unit = { file ->
                val selecting = selection != null
                val selectionNumber = selection?.indexOf(file.path)
                    ?.takeIf { it >= 0 }
                    ?.plus(1)
                val openFile = {
                    when (file.mediaType) {
                        MediaType.IMAGE -> onOpenImage(file.path)
                        MediaType.VIDEO -> onOpenVideo(file.path)
                        MediaType.PDF -> onOpenPdf(file.path)
                        MediaType.OTHER -> Unit
                    }
                }
                MediaCell(
                    selectionNumber = selectionNumber,
                    groupBadge = groupBadges[file.path],
                    tierInfo = tierInfo[file.path],
                    onClick = {
                        if (selecting) onToggleSelect(file.path) else openFile()
                    },
                    onLongClick = {
                        if (selecting) openFile() else onLongPressMedia(file.path)
                    },
                ) {
                    when (file.mediaType) {
                        MediaType.IMAGE -> ImageTileContent(
                            image = file,
                            modifier = sharedMediaModifier(
                                sharedTransitionScope,
                                animatedVisibilityScope,
                                file.path,
                            ),
                        )
                        MediaType.VIDEO -> VideoTileContent(file)
                        MediaType.PDF -> PdfTileContent(file)
                        MediaType.OTHER -> Unit
                    }
                }
            }
            if (sectionByMonth) {
                sections.forEach { section ->
                    item(
                        key = "header:${section.label}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Text(
                            text = section.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = 12.dp,
                                top = 12.dp,
                                bottom = 4.dp,
                            ),
                        )
                    }
                    items(
                        items = section.files,
                        key = { it.path },
                    ) { file ->
                        mediaCell(file)
                    }
                }
            } else {
                items(
                    items = media,
                    key = { it.path },
                ) { file ->
                    mediaCell(file)
                }
            }
        }
        GridScrollbar(
            gridState = gridState,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MediaCell(
    selectionNumber: Int?,
    groupBadge: String?,
    tierInfo: TierInfo?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val selected = selectionNumber != null
    val scale by animateFloatAsState(
        targetValue = if (selected) 0.92f else 1f,
        label = "selectScale",
    )
    val corner by animateDpAsState(
        targetValue = if (selected) 14.dp else 0.dp,
        label = "selectCorner",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .clip(RoundedCornerShape(corner))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
        content()
        if (tierInfo?.manual != null || tierInfo?.auto != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            ) {
                tierInfo.manual?.let { tier ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tierColor(tier))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = tier,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                tierInfo.auto?.let { tier ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tierColor(tier).copy(alpha = 0.75f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = tier,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        if (groupBadge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = groupBadge,
                    color = MaterialTheme.colorScheme.onTertiary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        }
        if (selectionNumber != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$selectionNumber",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun sharedMediaModifier(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    path: String,
): Modifier =
    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = "media:$path"),
                animatedVisibilityScope,
            )
        }
    } else {
        Modifier
    }

@Composable
private fun BoxScope.ImageTileContent(
    image: NasFile,
    modifier: Modifier = Modifier,
) {
    var loading by remember(image.path) { mutableStateOf(false) }
    AsyncImage(
        model = NasThumb.from(image),
        contentDescription = image.name,
        contentScale = ContentScale.Crop,
        onState = { state -> loading = state is AsyncImagePainter.State.Loading },
        modifier = modifier.fillMaxSize(),
    )
    if (loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shimmerBackground(),
        )
    }
}

@Composable
private fun BoxScope.VideoTileContent(video: NasFile) {
    var loading by remember(video.path) { mutableStateOf(false) }
    AsyncImage(
        model = NasVideoThumb.from(video),
        contentDescription = video.name,
        contentScale = ContentScale.Crop,
        onState = { state -> loading = state is AsyncImagePainter.State.Loading },
        modifier = Modifier.fillMaxSize(),
    )
    if (loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shimmerBackground(),
        )
    } else {
        Text(
            text = "▶",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.Center),
        )
    }
    NameBadge(video.name)
}

@Composable
private fun BoxScope.PdfTileContent(pdf: NasFile) {
    AsyncImage(
        model = NasPdfThumb.from(pdf),
        contentDescription = pdf.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Text(
        text = "PDF",
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(6.dp)
            .background(Color(0xCCD32F2F), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
    NameBadge(pdf.name)
}

@Composable
private fun BoxScope.NameBadge(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(
    dir: NasFile,
    info: FolderPreview?,
    onVisible: (String) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    LaunchedEffect(dir.path) {
        onVisible(dir.path)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        val preview = info?.preview
        when {
            preview != null -> {
                AsyncImage(
                    model = if (preview.isVideo) {
                        NasVideoThumb.from(preview)
                    } else {
                        NasThumb.from(preview)
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.75f),
                            ),
                        ),
                )
            }

            info == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shimmerBackground(),
                )
            }

            else -> {
                Image(
                    painter = painterResource(
                        if (info.hasContent) R.drawable.ic_folder_full else R.drawable.ic_folder_empty,
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.Center),
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = dir.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (preview != null) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (info != null && info.mediaCount > 0) {
                Text(
                    text = "${info.mediaCount}件",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (preview != null) {
                        Color.White.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private const val PINCH_STEP = 1.2f

private data class MonthSection(val label: String, val files: List<NasFile>)

private fun monthSections(media: List<NasFile>): List<MonthSection> {
    val sections = mutableListOf<MonthSection>()
    var currentLabel: String? = null
    var currentFiles = mutableListOf<NasFile>()
    for (file in media) {
        val date = Instant.ofEpochMilli(file.ctime).atZone(ZoneId.systemDefault())
        val label = "${date.year}年${date.monthValue}月"
        if (label != currentLabel) {
            currentLabel?.let { sections.add(MonthSection(it, currentFiles)) }
            currentLabel = label
            currentFiles = mutableListOf()
        }
        currentFiles.add(file)
    }
    currentLabel?.let { sections.add(MonthSection(it, currentFiles)) }
    return sections
}

private fun <T> toggleElement(set: Set<T>, value: T): Set<T> =
    if (value in set) set - value else set + value

@Composable
private fun SearchBarRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "ファイル名で検索",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
