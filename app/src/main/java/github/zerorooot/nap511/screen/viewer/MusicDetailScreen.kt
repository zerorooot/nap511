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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
    val fileBean = audioViewModel.uiState.playback.currentMusic ?: return
    val hasSelectedSubtitle = audioViewModel.uiState.subtitle.selectedSubtitle != null

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
                            tint = if (hasSelectedSubtitle) {
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
            val onToggleLyrics: (Boolean) -> Unit = { showFullLyrics = it }
            val openSubtitleSheet = { showSubtitleSheet = true }

            if (isLandscape) {
                LandscapeMusicContent(
                    audioViewModel = audioViewModel,
                    fileBean = fileBean,
                    showFullLyrics = showFullLyrics,
                    onShowFullLyricsChange = onToggleLyrics,
                    onOpenSubtitleSheet = openSubtitleSheet
                )
            } else {
                PortraitMusicContent(
                    audioViewModel = audioViewModel,
                    fileBean = fileBean,
                    showFullLyrics = showFullLyrics,
                    onShowFullLyricsChange = onToggleLyrics,
                    onOpenSubtitleSheet = openSubtitleSheet
                )
            }
        }
        LaunchedEffect(Unit) {
            audioViewModel.loadSubtitles()
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
/* -------------------------------------------------------------------------- */
/*                              布局层：横屏 / 竖屏                             */
/* -------------------------------------------------------------------------- */

/**
 * 横屏布局：左侧封面/歌词，右侧信息与控制面板。
 */
@Composable
private fun LandscapeMusicContent(
    audioViewModel: AudioViewModel,
    fileBean: FileBean,
    showFullLyrics: Boolean,
    onShowFullLyricsChange: (Boolean) -> Unit,
    onOpenSubtitleSheet: () -> Unit,
) {
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
            MusicCoverArea(
                audioViewModel = audioViewModel,
                fileBean = fileBean,
                showFullLyrics = showFullLyrics,
                onShowFullLyricsChange = onShowFullLyricsChange,
                onOpenSubtitleSheet = onOpenSubtitleSheet,
                contentModifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxHeight(),
                crossfadeLabel = "LyricsCrossfadeLandscape"
            )
        }

        Spacer(modifier = Modifier.width(32.dp))

        MusicControlPanel(
            audioViewModel = audioViewModel,
            fileBean = fileBean,
            onOpenSubtitleSheet = onOpenSubtitleSheet,
            onToggleFullLyrics = { onShowFullLyricsChange(!showFullLyrics) },
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            itemSpacing = 12.dp
        )
    }
}

/**
 * 竖屏布局：单列纵向排列。
 */
@Composable
private fun PortraitMusicContent(
    audioViewModel: AudioViewModel,
    fileBean: FileBean,
    showFullLyrics: Boolean,
    onShowFullLyricsChange: (Boolean) -> Unit,
    onOpenSubtitleSheet: () -> Unit,
) {
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
            MusicCoverArea(
                audioViewModel = audioViewModel,
                fileBean = fileBean,
                showFullLyrics = showFullLyrics,
                onShowFullLyricsChange = onShowFullLyricsChange,
                onOpenSubtitleSheet = onOpenSubtitleSheet,
                contentModifier = Modifier.fillMaxSize(),
                crossfadeLabel = "LyricsCrossfadePortrait"
            )
        }

        MusicControlPanel(
            audioViewModel = audioViewModel,
            fileBean = fileBean,
            onOpenSubtitleSheet = onOpenSubtitleSheet,
            onToggleFullLyrics = { onShowFullLyricsChange(!showFullLyrics) },
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Top,
            itemSpacing = 8.dp
        )
    }
}

/* -------------------------------------------------------------------------- */
/*                           复用组件：封面区 / 控制面板                          */
/* -------------------------------------------------------------------------- */

/**
 * 封面与全屏歌词的 [Crossfade] 切换区。
 *
 * 点击封面时：若有已选字幕则进入全屏歌词，否则打开字幕选择弹窗。
 */
@Composable
private fun MusicCoverArea(
    audioViewModel: AudioViewModel,
    fileBean: FileBean,
    showFullLyrics: Boolean,
    onShowFullLyricsChange: (Boolean) -> Unit,
    onOpenSubtitleSheet: () -> Unit,
    contentModifier: Modifier,
    crossfadeLabel: String,
) {
    val subtitleState = audioViewModel.uiState.subtitle

    Crossfade(
        targetState = showFullLyrics,
        label = crossfadeLabel
    ) { isLyrics ->
        if (isLyrics) {
            FullLyricsView(
                entries = subtitleState.entries,
                currentIndex = subtitleState.currentIndex,
                onEntryClick = { audioViewModel.seekToSubtitleEntry(it) },
                onClose = { onShowFullLyricsChange(false) },
                modifier = contentModifier
            )
        } else {
            AlbumCover(
                fileBean = fileBean,
                modifier = contentModifier.clickable {
                    if (subtitleState.selectedSubtitle != null) {
                        onShowFullLyricsChange(true)
                    } else {
                        onOpenSubtitleSheet()
                    }
                }
            )
        }
    }
}

/**
 * 音乐信息与控制面板：包含 [MusicInfo]、[SubtitleDisplay]、[SpeedAndVolume]、
 * [PlaybackProgress]、[PlaybackControls]。
 *
 * 通过 [verticalArrangement] 与 [itemSpacing] 适配横竖屏差异，避免重复组合。
 */
@Composable
private fun MusicControlPanel(
    audioViewModel: AudioViewModel,
    fileBean: FileBean,
    onOpenSubtitleSheet: () -> Unit,
    onToggleFullLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    itemSpacing: Dp = 12.dp,
) {
    val playbackState = audioViewModel.uiState.playback
    val subtitleState = audioViewModel.uiState.subtitle

    Column(
        modifier = modifier,
        verticalArrangement = verticalArrangement
    ) {
        MusicInfo(fileBean = fileBean)
        Spacer(Modifier.height(itemSpacing))

        SubtitleDisplay(
            subtitleText = subtitleState.currentText,
            hasSelectedSubtitle = subtitleState.selectedSubtitle != null,
            onOpenSubtitleSheet = onOpenSubtitleSheet,
            onToggleFullLyrics = onToggleFullLyrics
        )
        Spacer(Modifier.height(itemSpacing))

        SpeedAndVolume(
            speed = playbackState.playbackSpeed,
            volume = playbackState.volume,
            onChangeSpeed = { audioViewModel.changeSpeed(it) },
            onChangeVolume = { audioViewModel.changeVolume(it) }
        )
        Spacer(Modifier.height(itemSpacing))

        PlaybackProgress(
            progress = playbackState.displayProgress,
            positionText = playbackState.currentPositionText,
            isUserSeeking = playbackState.isUserSeeking,
            onSeekStart = { audioViewModel.onSeekStart() },
            onSeekChange = { audioViewModel.onSeekChange(it) },
            onSeekEnd = { audioViewModel.onSeekEnd() }
        )
        Spacer(Modifier.height(itemSpacing))

        PlaybackControls(
            isPlaying = playbackState.isPlaying,
            isLoading = playbackState.isLoading,
            onRewind = { audioViewModel.onRewind() },
            onFastForward = { audioViewModel.onFastForward() },
            onTogglePlayPause = { audioViewModel.togglePlayPause() }
        )
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
                            SpeedAndVolume(
                                speed = 1.0f,
                                volume = 0.5f,
                                onChangeSpeed = {},
                                onChangeVolume = {})
                            Spacer(modifier = Modifier.height(16.dp))
                            PlaybackProgress(
                                progress = 0.5f,
                                positionText = "02:30/05:00",
                                isUserSeeking = false,
                                onSeekStart = {},
                                onSeekChange = {},
                                onSeekEnd = {})
                            Spacer(modifier = Modifier.height(16.dp))
                            PlaybackControls(
                                isPlaying = true,
                                isLoading = false,
                                onRewind = {},
                                onFastForward = {},
                                onTogglePlayPause = {})
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
                        SpeedAndVolume(
                            speed = 1.0f,
                            volume = 0.5f,
                            onChangeSpeed = {},
                            onChangeVolume = {})
                        PlaybackProgress(
                            progress = 0.5f,
                            positionText = "02:30/05:00",
                            isUserSeeking = false,
                            onSeekStart = {},
                            onSeekChange = {},
                            onSeekEnd = {})
                        PlaybackControls(
                            isPlaying = true,
                            isLoading = false,
                            onRewind = {},
                            onFastForward = {},
                            onTogglePlayPause = {})
                    }
                }
            }
        }
    }
}
