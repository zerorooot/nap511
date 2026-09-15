package github.zerorooot.nap511.screen.viewer

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.screen.components.BaseTopAppBar
import github.zerorooot.nap511.screen.viewer.music.AlbumCover
import github.zerorooot.nap511.screen.viewer.music.FullLyricsView
import github.zerorooot.nap511.screen.viewer.music.MusicInfo
import github.zerorooot.nap511.screen.viewer.music.PlaybackControls
import github.zerorooot.nap511.screen.viewer.music.PlaybackProgress
import github.zerorooot.nap511.screen.viewer.music.SpeedAndVolume
import github.zerorooot.nap511.screen.viewer.music.SubtitleDisplay
import github.zerorooot.nap511.screen.viewer.music.SubtitleSelectionSheet
import github.zerorooot.nap511.ui.theme.Nap511Theme
import github.zerorooot.nap511.viewmodel.AudioViewModel

/**
 * 音乐详情 / 正在播放全屏界面
 *
 * 负责组合音乐播放器的各大无状态 UI 组件（来自 `screen.viewer.music` 包）：
 * 1. 响应式布局：通过 [BoxWithConstraints] 判断 `maxWidth > maxHeight` 自动切换横屏/竖屏布局；
 * 2. 模式平滑切换：封面与全屏歌词之间使用 [Crossfade] 动画渐变过渡；
 * 3. 状态管理：绑定 [AudioViewModel] 的播放进度、控制指令、字幕选择与偏置。
 *
 * @param audioViewModel 音频播放 ViewModel
 * @param onBack 点击返回上一页回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicDetailScreen(
    audioViewModel: AudioViewModel,
    onBack: () -> Unit
) {
    val playbackState = audioViewModel.uiState.playback
    val subtitleState = audioViewModel.uiState.subtitle
    val fileBean = playbackState.currentMusic ?: return
    val isPlaying = playbackState.isPlaying
    val isLoading = playbackState.isLoading
    val progress = playbackState.displayProgress
    val positionText = playbackState.currentPositionText
    val speed = playbackState.playbackSpeed
    val volume = playbackState.volume

    // 控制字幕选择 BottomSheet 显隐
    var showSubtitleSheet by remember { mutableStateOf(false) }
    // 控制是否在封面位置展开全屏歌词列表
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
                            tint = if (subtitleState.selectedSubtitle != null) {
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
                // 横屏布局：左侧封面/歌词，右侧信息与控制面板
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：封面与全屏歌词 Crossfade 过渡
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
                                    entries = subtitleState.entries,
                                    currentIndex = subtitleState.currentIndex,
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
                                            if (subtitleState.selectedSubtitle != null) {
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

                    // 右侧：音乐信息、字幕条、倍速音量、进度条、播放控制
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
                            subtitleText = subtitleState.currentText,
                            hasSelectedSubtitle = subtitleState.selectedSubtitle != null,
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
                            isUserSeeking = playbackState.isUserSeeking,
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
                // 竖屏布局：单列纵向排列
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
                                    entries = subtitleState.entries,
                                    currentIndex = subtitleState.currentIndex,
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
                                            if (subtitleState.selectedSubtitle != null) {
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
                        subtitleText = subtitleState.currentText,
                        hasSelectedSubtitle = subtitleState.selectedSubtitle != null,
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
                        isUserSeeking = playbackState.isUserSeeking,
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

        // 字幕/歌词设置底部弹窗
        if (showSubtitleSheet) {
            SubtitleSelectionSheet(
                audioViewModel = audioViewModel,
                categoryId = fileBean.categoryId,
                onDismiss = { showSubtitleSheet = false }
            )
        }
    }
}

/**
 * 界面 Preview 示例：使用 mock 数据预览横竖屏播放界面
 */
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
                            SpeedAndVolume(speed = 1.0f, volume = 0.5f, onChangeSpeed = {}, onChangeVolume = {})
                            Spacer(modifier = Modifier.height(16.dp))
                            PlaybackProgress(progress = 0.5f, positionText = "02:30/05:00", isUserSeeking = false, onSeekStart = {}, onSeekChange = {}, onSeekEnd = {})
                            Spacer(modifier = Modifier.height(16.dp))
                            PlaybackControls(isPlaying = true, isLoading = false, onRewind = {}, onFastForward = {}, onTogglePlayPause = {})
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
                        SpeedAndVolume(speed = 1.0f, volume = 0.5f, onChangeSpeed = {}, onChangeVolume = {})
                        PlaybackProgress(progress = 0.5f, positionText = "02:30/05:00", isUserSeeking = false, onSeekStart = {}, onSeekChange = {}, onSeekEnd = {})
                        PlaybackControls(isPlaying = true, isLoading = false, onRewind = {}, onFastForward = {}, onTogglePlayPause = {})
                    }
                }
            }
        }
    }
}
