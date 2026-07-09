package com.example.quickstash.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.content.res.Configuration
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import com.example.quickstash.R
import kotlinx.coroutines.delay

private fun Context.findActivity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()

private data class SkipFeedback(val forward: Boolean, val id: Long)

private data class SeekPreview(val targetMs: Long, val deltaMs: Long)


@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    folderPath: String,
    videoPath: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(
        key = "player:$videoPath",
        factory = PlayerViewModel.factory(folderPath, videoPath),
    ),
) {
    val player by viewModel.player.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val skipSeconds by viewModel.skipSeconds.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    var controlsVisible by remember { mutableStateOf(true) }
    var skipFeedback by remember { mutableStateOf<SkipFeedback?>(null) }
    var abRange by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
    var seekPreview by remember { mutableStateOf<SeekPreview?>(null) }

    val currentPlayer = player
    if (currentPlayer != null) {
        LaunchedEffect(currentPlayer, abRange) {
            val range = abRange ?: return@LaunchedEffect
            while (true) {
                val pos = currentPlayer.currentPosition
                if (range.endInclusive > range.start &&
                    (pos >= range.endInclusive.toLong() || pos < range.start.toLong() - 500)
                ) {
                    currentPlayer.seekTo(range.start.toLong())
                    if (!currentPlayer.isPlaying) currentPlayer.play()
                }
                delay(100)
            }
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.let {
                it.attributes = it.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(skipFeedback) {
        if (skipFeedback != null) {
            delay(700)
            skipFeedback = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(player, skipSeconds) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        val target = player ?: return@detectTapGestures
                        val delta = skipSeconds * 1000L
                        val forward = offset.x >= size.width / 2
                        if (forward) {
                            target.seekTo(target.currentPosition + delta)
                        } else {
                            target.seekTo((target.currentPosition - delta).coerceAtLeast(0))
                        }
                        skipFeedback = SkipFeedback(
                            forward = forward,
                            id = (skipFeedback?.id ?: 0) + 1,
                        )
                    },
                )
            }
            .pointerInput(player) {
                var startPos = -1L
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        startPos = player?.currentPosition ?: -1L
                        accumulated = 0f
                        if (startPos >= 0) controlsVisible = true
                    },
                    onDragEnd = {
                        val preview = seekPreview
                        val target = player
                        if (preview != null && target != null) {
                            target.seekTo(preview.targetMs)
                        }
                        seekPreview = null
                        startPos = -1L
                    },
                    onDragCancel = {
                        seekPreview = null
                        startPos = -1L
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        val target = player
                        if (target == null || startPos < 0) return@detectHorizontalDragGestures
                        val duration = target.duration
                        if (duration <= 0) return@detectHorizontalDragGestures
                        change.consume()
                        accumulated += dragAmount
                        val deltaMs =
                            (accumulated / size.width * duration).toLong()
                        val targetMs = (startPos + deltaMs).coerceIn(0L, duration)
                        seekPreview = SeekPreview(
                            targetMs = targetMs,
                            deltaMs = targetMs - startPos,
                        )
                    },
                )
            },
    ) {
        when {
            error != null -> {
                Text(
                    text = error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            }

            player == null -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            else -> {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            useController = false
                            keepScreenOn = true
                        }
                    },
                    update = { view -> view.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    PlayerControls(
                        player = player as ExoPlayer,
                        activity = activity,
                        skipSeconds = skipSeconds,
                        abRange = abRange,
                        onAbRangeChange = { abRange = it },
                        previewPositionMs = seekPreview?.targetMs,
                        onBack = onBack,
                    )
                }
            }
        }

        seekPreview?.let { preview ->
            val sign = if (preview.deltaMs >= 0) "+" else "-"
            Text(
                text = "$sign${formatTime(kotlin.math.abs(preview.deltaMs))}  (${formatTime(preview.targetMs)})",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        skipFeedback?.let { feedback ->
            Text(
                text = if (feedback.forward) "▶▶ ${skipSeconds}秒" else "◀◀ ${skipSeconds}秒",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                    .padding(top = 16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        if (error != null || player == null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "戻る",
                    tint = Color.White,
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerControls(
    player: ExoPlayer,
    activity: Activity?,
    skipSeconds: Int,
    abRange: ClosedFloatingPointRange<Float>?,
    onAbRangeChange: (ClosedFloatingPointRange<Float>?) -> Unit,
    previewPositionMs: Long?,
    onBack: () -> Unit,
) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var title by remember { mutableStateOf(currentTitle(player)) }
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var buffered by remember { mutableLongStateOf(player.bufferedPosition) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0)) }
    var hasPrevious by remember { mutableStateOf(player.hasPreviousMediaItem()) }
    var hasNext by remember { mutableStateOf(player.hasNextMediaItem()) }
    var repeatOn by remember { mutableStateOf(player.repeatMode == Player.REPEAT_MODE_ONE) }
    var speed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var brightnessVisible by remember { mutableStateOf(false) }
    var brightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f,
        )
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onEvents(p: Player, events: Player.Events) {
                title = currentTitle(p)
                hasPrevious = p.hasPreviousMediaItem()
                hasNext = p.hasNextMediaItem()
            }

            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int,
            ) {
                onAbRangeChange(null)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val currentPreview = rememberUpdatedState(previewPositionMs)
    LaunchedEffect(player) {
        while (true) {
            position = currentPreview.value ?: player.currentPosition.coerceAtLeast(0)
            buffered = player.bufferedPosition.coerceAtLeast(0)
            duration = player.duration.coerceAtLeast(0)
            delay(100)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { player.seekToPreviousMediaItem() },
                    enabled = hasPrevious,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_previous),
                        contentDescription = "前の動画",
                        tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(
                    onClick = {
                        player.seekTo((player.currentPosition - skipSeconds * 1000L).coerceAtLeast(0))
                    },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_fast_rewind),
                        contentDescription = "${skipSeconds}秒戻る",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(
                    onClick = { if (player.isPlaying) player.pause() else player.play() },
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape),
                ) {
                    Icon(
                        painter = painterResource(
                            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                        ),
                        contentDescription = if (isPlaying) "一時停止" else "再生",
                        tint = Color.White,
                        modifier = Modifier.size(56.dp),
                    )
                }
                IconButton(
                    onClick = { player.seekTo(player.currentPosition + skipSeconds * 1000L) },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_fast_forward),
                        contentDescription = "${skipSeconds}秒進む",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                IconButton(
                    onClick = { player.seekToNextMediaItem() },
                    enabled = hasNext,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next),
                        contentDescription = "次の動画",
                        tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                    ),
                )
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            if (brightnessVisible) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_brightness),
                        contentDescription = "明るさ",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Slider(
                        value = brightness,
                        onValueChange = { value ->
                            brightness = value
                            activity?.window?.let {
                                it.attributes = it.attributes.apply { screenBrightness = value }
                            }
                        },
                        valueRange = 0.05f..1f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )
                }
            }

            abRange?.let { range ->
                val maxValue = duration.toFloat().coerceAtLeast(1f)
                RangeSlider(
                    value = range,
                    onValueChange = { updated ->
                        onAbRangeChange(
                            updated.start.coerceAtLeast(0f)..updated.endInclusive.coerceAtMost(maxValue),
                        )
                    },
                    valueRange = 0f..maxValue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                )
                Text(
                    text = "区間: ${formatTime(range.start.toLong())} 〜 ${formatTime(range.endInclusive.toLong())}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            AndroidView(
                factory = { context ->
                    DefaultTimeBar(context).apply {
                        addListener(object : TimeBar.OnScrubListener {
                            override fun onScrubStart(timeBar: TimeBar, positionMs: Long) {}

                            override fun onScrubMove(timeBar: TimeBar, positionMs: Long) {}

                            override fun onScrubStop(
                                timeBar: TimeBar,
                                positionMs: Long,
                                canceled: Boolean,
                            ) {
                                if (!canceled) player.seekTo(positionMs)
                            }
                        })
                    }
                },
                update = { bar ->
                    bar.setDuration(duration)
                    bar.setPosition(position)
                    bar.setBufferedPosition(buffered)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "${formatTime(position)} / ${formatTime(duration)}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        onAbRangeChange(
                            if (abRange == null) {
                                val end = duration.toFloat().coerceAtLeast(1f)
                                val start = position.toFloat().coerceAtMost(end - 1f)
                                start..end
                            } else {
                                null
                            },
                        )
                    },
                ) {
                    Text(
                        text = if (abRange != null) "A-B解除" else "A-B",
                        color = if (abRange != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White
                        },
                    )
                }
                IconButton(
                    onClick = {
                        repeatOn = !repeatOn
                        player.repeatMode =
                            if (repeatOn) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_repeat),
                        contentDescription = "ループ再生",
                        tint = if (repeatOn) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
                Box {
                    TextButton(onClick = { speedMenuOpen = true }) {
                        Text("${formatSpeed(speed)}x", color = Color.White)
                    }
                    DropdownMenu(
                        expanded = speedMenuOpen,
                        onDismissRequest = { speedMenuOpen = false },
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text("${formatSpeed(candidate)}x") },
                                onClick = {
                                    speed = candidate
                                    player.setPlaybackSpeed(candidate)
                                    speedMenuOpen = false
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { brightnessVisible = !brightnessVisible }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_brightness),
                        contentDescription = "明るさ調整",
                        tint = if (brightnessVisible) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White
                        },
                    )
                }
                val isLandscape =
                    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                IconButton(
                    onClick = {
                        activity?.requestedOrientation = if (isLandscape) {
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_screen_rotation),
                        contentDescription = "画面回転",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

private fun currentTitle(player: Player): String =
    player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}.0" else speed.toString()

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
