package com.example.quickstash.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.quickstash.R
import com.example.quickstash.data.nas.MediaType
import com.example.quickstash.data.nas.NasFile
import com.example.quickstash.media.coil.NasPdfThumb
import com.example.quickstash.media.coil.NasThumb
import com.example.quickstash.media.coil.NasVideoThumb

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagListScreen(
    onOpenTag: (Long, String) -> Unit,
    onBack: () -> Unit,
    viewModel: TagsViewModel = viewModel(factory = TagsViewModel.Factory),
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("タグ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { innerPadding ->
        val current = tags
        when {
            current == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            current.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("タグはまだありません。\n一覧で画像を長押し選択 → タグから作成できます。")
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    items(current, key = { it.id }) { tag ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenTag(tag.id, tag.name) }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_tag),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${tag.count}件",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagMediaScreen(
    tagId: Long,
    tagName: String,
    onOpenImage: (folderPath: String, imagePath: String) -> Unit,
    onOpenVideo: (folderPath: String, videoPath: String) -> Unit,
    onOpenPdf: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: TagMediaViewModel = viewModel(
        key = "tagmedia:$tagId",
        factory = TagMediaViewModel.factory(tagId),
    ),
) {
    val media by viewModel.media.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tagName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { innerPadding ->
        val current = media
        when {
            current == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            current.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("このタグのコンテンツはありません")
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(current, key = { it.path }) { file ->
                        TagMediaTile(
                            file = file,
                            onClick = {
                                viewModel.prepareSequence()
                                val parent = file.path.substringBeforeLast('/', "")
                                when (file.mediaType) {
                                    MediaType.IMAGE -> onOpenImage(parent, file.path)
                                    MediaType.VIDEO -> onOpenVideo(parent, file.path)
                                    MediaType.PDF -> onOpenPdf(file.path)
                                    MediaType.OTHER -> Unit
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagMediaTile(file: NasFile, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        val model: Any = when (file.mediaType) {
            MediaType.VIDEO -> NasVideoThumb.from(file)
            MediaType.PDF -> NasPdfThumb.from(file)
            else -> NasThumb.from(file)
        }
        AsyncImage(
            model = model,
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (file.mediaType == MediaType.VIDEO) {
            Text(
                text = "▶",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Text(
            text = file.name,
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
}
