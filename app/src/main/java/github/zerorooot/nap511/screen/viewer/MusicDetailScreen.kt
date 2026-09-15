package github.zerorooot.nap511.screen.viewer

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.SubtitleSourceType
import github.zerorooot.nap511.screen.components.BaseTopAppBar
import github.zerorooot.nap511.ui.theme.Nap511Theme
import github.zerorooot.nap511.util.SubtitleEntry
import github.zerorooot.nap511.viewmodel.AudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicDetailScreen(
    audioViewModel: AudioViewModel,
    onBack: () -> Unit
) {
    val fileBean = audioViewModel.currentMusic ?: return
    val isPlaying = audioViewModel.isPlaying
    val isLoading = audioViewModel.isLoading
    val progress =
        if (audioViewModel.isUserSeeking) audioViewModel.userSeekProgress else audioViewModel.progress
    val positionText = audioViewModel.currentPositionText
    val speed = audioViewModel.playbackSpeed
    val volume = audioViewModel.volume

    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showFullLyrics by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BaseTopAppBar(
                title = { Text("正在播放") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSubtitleSheet = true }) {
                        Icon(
                            Icons.Default.Subtitles,
                            contentDescription = "Subtitles",
                            tint = if (audioViewModel.selectedSubtitle != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding()
                )
        ) {
            val isLandscape = maxWidth > maxHeight

            if (isLandscape) {
                // 横屏布局
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：封面或全屏歌词（使用 Crossfade 平滑渐变过渡）
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Crossfade(
                            targetState = showFullLyrics,
                            label = "LyricsCrossfadeLandscape"
                        ) { isLyrics ->
                            if (isLyrics) {
                                FullLyricsView(
                                    entries = audioViewModel.subtitleEntries,
                                    currentIndex = audioViewModel.currentSubtitleIndex,
                                    onEntryClick = { audioViewModel.seekToSubtitleEntry(it) },
                                    onClose = { showFullLyrics = false },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AlbumCover(
                                    fileBean = fileBean,
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .fillMaxHeight()
                                        .clickable {
                                            if (audioViewModel.selectedSubtitle != null) {
                                                showFullLyrics = true
                                            } else {
                                                showSubtitleSheet = true
                                            }
                                        }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    // 右侧：信息与控制
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center
                    ) {
                        MusicInfo(fileBean = fileBean)
                        Spacer(modifier = Modifier.height(12.dp))

                        SubtitleDisplay(
                            subtitleText = audioViewModel.currentSubtitleText,
                            hasSelectedSubtitle = audioViewModel.selectedSubtitle != null,
                            onOpenSubtitleSheet = { showSubtitleSheet = true },
                            onToggleFullLyrics = { showFullLyrics = !showFullLyrics }
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        SpeedAndVolume(
                            speed = speed,
                            volume = volume,
                            onChangeSpeed = { audioViewModel.changeSpeed(it) },
                            onChangeVolume = { audioViewModel.changeVolume(it) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PlaybackProgress(
                            progress = progress,
                            positionText = positionText,
                            isUserSeeking = audioViewModel.isUserSeeking,
                            onSeekStart = { audioViewModel.onSeekStart() },
                            onSeekChange = { audioViewModel.onSeekChange(it) },
                            onSeekEnd = { audioViewModel.onSeekEnd() }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PlaybackControls(
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            onRewind = { audioViewModel.onRewind() },
                            onFastForward = { audioViewModel.onFastForward() },
                            onTogglePlayPause = { audioViewModel.togglePlayPause() }
                        )
                    }
                }
            } else {
                // 竖屏布局
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Crossfade(
                            targetState = showFullLyrics,
                            label = "LyricsCrossfadePortrait"
                        ) { isLyrics ->
                            if (isLyrics) {
                                FullLyricsView(
                                    entries = audioViewModel.subtitleEntries,
                                    currentIndex = audioViewModel.currentSubtitleIndex,
                                    onEntryClick = { audioViewModel.seekToSubtitleEntry(it) },
                                    onClose = { showFullLyrics = false },
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AlbumCover(
                                    fileBean = fileBean,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            if (audioViewModel.selectedSubtitle != null) {
                                                showFullLyrics = true
                                            } else {
                                                showSubtitleSheet = true
                                            }
                                        }
                                )
                            }
                        }
                    }

                    MusicInfo(fileBean = fileBean)

                    SubtitleDisplay(
                        subtitleText = audioViewModel.currentSubtitleText,
                        hasSelectedSubtitle = audioViewModel.selectedSubtitle != null,
                        onOpenSubtitleSheet = { showSubtitleSheet = true },
                        onToggleFullLyrics = { showFullLyrics = !showFullLyrics },
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    SpeedAndVolume(
                        speed = speed,
                        volume = volume,
                        onChangeSpeed = { audioViewModel.changeSpeed(it) },
                        onChangeVolume = { audioViewModel.changeVolume(it) }
                    )

                    PlaybackProgress(
                        progress = progress,
                        positionText = positionText,
                        isUserSeeking = audioViewModel.isUserSeeking,
                        onSeekStart = { audioViewModel.onSeekStart() },
                        onSeekChange = { audioViewModel.onSeekChange(it) },
                        onSeekEnd = { audioViewModel.onSeekEnd() }
                    )

                    PlaybackControls(
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        onRewind = { audioViewModel.onRewind() },
                        onFastForward = { audioViewModel.onFastForward() },
                        onTogglePlayPause = { audioViewModel.togglePlayPause() }
                    )
                }
            }
        }

        if (showSubtitleSheet) {
            SubtitleSelectionSheet(
                audioViewModel = audioViewModel,
                categoryId = fileBean.categoryId,
                onDismiss = { showSubtitleSheet = false }
            )
        }
    }
}

@Composable
fun SubtitleDisplay(
    subtitleText: String,
    hasSelectedSubtitle: Boolean,
    onOpenSubtitleSheet: () -> Unit,
    onToggleFullLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                if (hasSelectedSubtitle) {
                    onToggleFullLyrics()
                } else {
                    onOpenSubtitleSheet()
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (hasSelectedSubtitle) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Subtitles,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "点击选择字幕 / 歌词",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun FullLyricsView(
    entries: List<SubtitleEntry>,
    currentIndex: Int,
    onEntryClick: (SubtitleEntry) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex in entries.indices) {
            listState.animateScrollToItem(
                index = (currentIndex - 2).coerceAtLeast(0)
            )
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClose() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无有效字幕数据，点击切回封面")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(entries) { index, entry ->
                    val isSelected = index == currentIndex
                    val textColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    val fontSize = if (isSelected) 18.sp else 14.sp

                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = fontWeight,
                            fontSize = fontSize,
                            textAlign = TextAlign.Center
                        ),
                        color = textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEntryClick(entry) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSelectionSheet(
    audioViewModel: AudioViewModel,
    categoryId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchInput by remember {
        val musicName = audioViewModel.currentMusic?.name ?: ""
        mutableStateOf(if (musicName.contains(".")) musicName.substringBeforeLast(".") else musicName)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "字幕与歌词设置",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 搜索框
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    label = { Text("搜索字幕关键字") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    trailingIcon = {
                        IconButton(onClick = {
                            audioViewModel.loadSubtitles(searchKeyword = searchInput)
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 时间偏移量控制
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "偏移: ${audioViewModel.subtitleOffsetMs}ms",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row {
                    TextButton(onClick = { audioViewModel.addSubtitleOffset(-500L) }) {
                        Text("-0.5s")
                    }
                    TextButton(onClick = { audioViewModel.addSubtitleOffset(-100L) }) {
                        Text("-0.1s")
                    }
                    TextButton(onClick = { audioViewModel.setSubtitleOffset(0L) }) {
                        Text("重置")
                    }
                    TextButton(onClick = { audioViewModel.addSubtitleOffset(100L) }) {
                        Text("+0.1s")
                    }
                    TextButton(onClick = { audioViewModel.addSubtitleOffset(500L) }) {
                        Text("+0.5s")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 字幕候选列表 Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "候选字幕列表 (${audioViewModel.subtitles.size})",
                    style = MaterialTheme.typography.titleMedium
                )

                if (audioViewModel.selectedSubtitle != null) {
                    TextButton(onClick = {
                        audioViewModel.removeSubtitle()
                    }) {
                        Text("清除字幕", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (audioViewModel.isSubtitleSearchLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (audioViewModel.subtitles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("未检索到可用字幕", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    itemsIndexed(audioViewModel.subtitles) { _, item ->
                        val isSelected = item.id == audioViewModel.selectedSubtitle?.id
                        val containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    audioViewModel.selectSubtitle(context.cacheDir, item)
                                },
                            colors = CardDefaults.cardColors(containerColor = containerColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.simpleName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "来源: ${item.sourceType.label} | 格式: ${item.ext.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isSelected && audioViewModel.isSubtitleLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else if (item.sourceType == SubtitleSourceType.XUNLEI) {
                                    IconButton(
                                        onClick = {
                                            audioViewModel.uploadSubtitleTo115(
                                                context.cacheDir,
                                                item,
                                                categoryId
                                            )
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.CloudUpload,
                                            contentDescription = "Upload to 115",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AlbumCover(fileBean: FileBean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        if (fileBean.photoThumb.isNotEmpty()) {
            AsyncImage(
                model = fileBean.photoThumb,
                contentDescription = "Album Cover",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun MusicInfo(fileBean: FileBean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = fileBean.name,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "文件大小: ${fileBean.sizeString}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SpeedAndVolume(
    speed: Float,
    volume: Float,
    onChangeSpeed: (Float) -> Unit,
    onChangeVolume: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = {
            val nextSpeed = when (speed) {
                1.0f -> 1.25f
                1.25f -> 1.5f
                1.5f -> 2.0f
                2.0f -> 0.5f
                0.5f -> 1.0f
                else -> 1.0f
            }
            onChangeSpeed(nextSpeed)
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("${speed}x")
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Slider(
                value = volume,
                onValueChange = onChangeVolume,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun PlaybackProgress(
    progress: Float,
    positionText: String,
    isUserSeeking: Boolean,
    onSeekStart: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekEnd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Slider(
            value = progress,
            onValueChange = {
                if (!isUserSeeking) onSeekStart()
                onSeekChange(it)
            },
            onValueChangeFinished = onSeekEnd
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = positionText.split("/").getOrElse(0) { "00:00" },
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = positionText.split("/").getOrElse(1) { "00:00" },
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onTogglePlayPause: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onRewind,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Default.FastRewind,
                contentDescription = "Rewind",
                modifier = Modifier.size(32.dp)
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                strokeWidth = 4.dp
            )
        } else {
            FilledIconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        IconButton(
            onClick = onFastForward,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Default.FastForward,
                contentDescription = "Forward",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Preview(name = "Phone", device = "spec:width=411dp,height=891dp")
@Preview(name = "Landscape", device = "spec:width=891dp,height=411dp,orientation=landscape")
@Preview(name = "Tablet", device = "spec:width=1280dp,height=800dp,orientation=landscape")
@Composable
fun MusicDetailScreenPreview() {
    val mockFile = FileBean(
        name = "测试歌曲.mp3",
        sizeString = "10.5 MB",
        photoThumb = ""
    )
    Nap511Theme {
        Scaffold(
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    windowInsets = TopAppBarDefaults.windowInsets.only(androidx.compose.foundation.layout.WindowInsetsSides.Top),
                    title = { Text("正在播放") },
                    navigationIcon = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                )
            }
        ) { paddingValues ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val isLandscape = maxWidth > maxHeight
                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            AlbumCover(
                                fileBean = mockFile,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxHeight()
                            )
                        }
                        Spacer(modifier = Modifier.width(32.dp))
                        Column(
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center
                        ) {
                            MusicInfo(fileBean = mockFile)
                            Spacer(modifier = Modifier.height(16.dp))
                            SpeedAndVolume(1.0f, 0.5f, {}, {})
                            Spacer(modifier = Modifier.height(16.dp))
                            PlaybackProgress(0.5f, "02:30/05:00", false, {}, {}, {})
                            Spacer(modifier = Modifier.height(16.dp))
                            PlaybackControls(true, false, {}, {}, {})
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AlbumCover(
                            fileBean = mockFile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(24.dp)
                        )
                        MusicInfo(fileBean = mockFile)
                        SpeedAndVolume(1.0f, 0.5f, {}, {})
                        PlaybackProgress(0.5f, "02:30/05:00", false, {}, {}, {})
                        PlaybackControls(true, false, {}, {}, {})
                    }
                }
            }
        }
    }
}
