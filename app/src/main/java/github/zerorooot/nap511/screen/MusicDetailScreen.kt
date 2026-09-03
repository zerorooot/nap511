package github.zerorooot.nap511.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.ui.theme.Nap511Theme
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

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
                title = { Text("正在播放") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                    // 左侧：封面
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        AlbumCover(
                            fileBean = fileBean,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .fillMaxHeight()
                        )
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
                        Spacer(modifier = Modifier.height(16.dp))
                        SpeedAndVolume(
                            speed = speed,
                            volume = volume,
                            onChangeSpeed = { audioViewModel.changeSpeed(it) },
                            onChangeVolume = { audioViewModel.changeVolume(it) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        PlaybackProgress(
                            progress = progress,
                            positionText = positionText,
                            isUserSeeking = audioViewModel.isUserSeeking,
                            onSeekStart = { audioViewModel.onSeekStart() },
                            onSeekChange = { audioViewModel.onSeekChange(it) },
                            onSeekEnd = { audioViewModel.onSeekEnd() }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
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
                // 竖屏布局 (原有布局)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    AlbumCover(
                        fileBean = fileBean,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(24.dp)
                    )

                    MusicInfo(fileBean = fileBean)

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
                    windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
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
