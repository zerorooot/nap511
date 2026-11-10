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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import github.zerorooot.nap511.bean.SubtitleSourceType
import github.zerorooot.nap511.bean.SubtitleUiState
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
import github.zerorooot.nap511.util.subtitle.SubtitleEntry
import github.zerorooot.nap511.viewmodel.AudioPlaybackUiState
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
/**
 * 音乐详情界面交互动作集合 (MusicDetailActions)
 *
 * 聚合播放暂停、快进快退、进度滑动、倍速音量调节、歌词切换与跳转、字幕弹窗显隐与返回等操作。
 */
@Immutable
data class MusicDetailActions(
    val onTogglePlayPause: () -> Unit,
    val onRewind: () -> Unit,
    val onFastForward: () -> Unit,
    val onSeekStart: () -> Unit,
    val onSeekChange: (Float) -> Unit,
    val onSeekEnd: () -> Unit,
    val onChangeSpeed: (Float) -> Unit,
    val onChangeVolume: (Float) -> Unit,
    val onLyricClick: (SubtitleEntry) -> Unit,
    val onToggleFullLyrics: (Boolean) -> Unit,
    val onOpenSubtitleSheet: () -> Unit,
    val onBack: () -> Unit
)

/**
 * 纯无状态音乐详情 / 播放器内容组件 (MusicDetailContent)
 *
 * 100% 剥离 ViewModel 依赖，仅依赖不可变领域状态 [AudioPlaybackUiState]、[SubtitleUiState] 与 [MusicDetailActions]，
 * 支持 Android Studio @Preview 与无桩 Compose UI 测试。
 *
 * @param playbackState 播放器状态（播放中、进度、音量、倍速等）
 * @param subtitleState 字幕/歌词状态（当前行、行列表、已选字幕等）
 * @param showFullLyrics 当前是否全屏展开歌词
 * @param actions 用户交互动作集合
 * @param modifier 外部修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicDetailContent(
    playbackState: AudioPlaybackUiState,
    subtitleState: SubtitleUiState,
    showFullLyrics: Boolean,
    actions: MusicDetailActions,
    modifier: Modifier = Modifier
) {
    val fileBean = playbackState.currentMusic ?: return
    val hasSelectedSubtitle = subtitleState.selectedSubtitle != null

    Scaffold(
        topBar = {
            BaseTopAppBar(
                title = { Text("正在播放") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = actions.onOpenSubtitleSheet) {
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
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
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
                LandscapeMusicContent(
                    playbackState = playbackState,
                    subtitleState = subtitleState,
                    fileBean = fileBean,
                    showFullLyrics = showFullLyrics,
                    actions = actions
                )
            } else {
                PortraitMusicContent(
                    playbackState = playbackState,
                    subtitleState = subtitleState,
                    fileBean = fileBean,
                    showFullLyrics = showFullLyrics,
                    actions = actions
                )
            }
        }
    }
}

/**
 * 音乐详情 / 正在播放全屏界面 (Stateful Container)
 *
 * 负责组合音乐播放器的各大无状态 UI 组件（来自 `screen.viewer.music` 包）：
 * 1. 响应式布局：通过 [BoxWithConstraints] 判断 `maxWidth > maxHeight` 自动切换横屏/竖屏布局；
 * 2. 模式平滑切换：封面与全屏歌词之间使用 [Crossfade] 动画渐变过渡；
 * 3. 状态管理：绑定 [AudioViewModel] 的播放进度、控制指令、字幕选择与偏置。
 *
 * @param audioViewModel 音频播放 ViewModel
 * @param onBack 点击返回上一页回调
 */
@Composable
fun MusicDetailScreen(
    audioViewModel: AudioViewModel,
    onBack: () -> Unit
) {
    val playbackState = audioViewModel.uiState.playback
    val subtitleState = audioViewModel.uiState.subtitle
    val fileBean = playbackState.currentMusic ?: return

    // 控制字幕选择 BottomSheet 显隐
    var showSubtitleSheet by remember { mutableStateOf(false) }
    // 控制是否在封面位置展开全屏歌词列表
    var showFullLyrics by rememberSaveable { mutableStateOf(false) }
    // 记录已加载过字幕的歌曲标识，防止同一首歌曲重复自动请求加载字幕
    var loadedSubtitleMusicKey by rememberSaveable { mutableStateOf<String?>(null) }

    val currentMusicKey = fileBean.fileId.ifEmpty { fileBean.name }

    LaunchedEffect(showSubtitleSheet, currentMusicKey) {
        if (showSubtitleSheet && loadedSubtitleMusicKey != currentMusicKey) {
            loadedSubtitleMusicKey = currentMusicKey
            audioViewModel.loadSubtitles()
        }
    }

    val actions = remember(audioViewModel, onBack) {
        MusicDetailActions(
            onTogglePlayPause = { audioViewModel.togglePlayPause() },
            onRewind = { audioViewModel.onRewind() },
            onFastForward = { audioViewModel.onFastForward() },
            onSeekStart = { audioViewModel.onSeekStart() },
            onSeekChange = { audioViewModel.onSeekChange(it) },
            onSeekEnd = { audioViewModel.onSeekEnd() },
            onChangeSpeed = { audioViewModel.changeSpeed(it) },
            onChangeVolume = { audioViewModel.changeVolume(it) },
            onLyricClick = { audioViewModel.seekToSubtitleEntry(it) },
            onToggleFullLyrics = { showFullLyrics = it },
            onOpenSubtitleSheet = { showSubtitleSheet = true },
            onBack = onBack
        )
    }


    MusicDetailContent(
        playbackState = playbackState,
        subtitleState = subtitleState,
        showFullLyrics = showFullLyrics,
        actions = actions
    )

    // 字幕/歌词设置底部弹窗
    if (showSubtitleSheet) {
        SubtitleSelectionSheet(
            audioViewModel = audioViewModel,
            categoryId = fileBean.categoryId,
            onDismiss = { showSubtitleSheet = false }
        )
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
    playbackState: AudioPlaybackUiState,
    subtitleState: SubtitleUiState,
    fileBean: FileBean,
    showFullLyrics: Boolean,
    actions: MusicDetailActions,
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
                subtitleState = subtitleState,
                fileBean = fileBean,
                showFullLyrics = showFullLyrics,
                onShowFullLyricsChange = actions.onToggleFullLyrics,
                onOpenSubtitleSheet = actions.onOpenSubtitleSheet,
                onLyricClick = actions.onLyricClick,
                contentModifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxHeight(),
                crossfadeLabel = "LyricsCrossfadeLandscape"
            )
        }

        Spacer(modifier = Modifier.width(32.dp))

        MusicControlPanel(
            playbackState = playbackState,
            subtitleState = subtitleState,
            fileBean = fileBean,
            actions = actions,
            showFullLyrics = showFullLyrics,
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
    playbackState: AudioPlaybackUiState,
    subtitleState: SubtitleUiState,
    fileBean: FileBean,
    showFullLyrics: Boolean,
    actions: MusicDetailActions,
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
                subtitleState = subtitleState,
                fileBean = fileBean,
                showFullLyrics = showFullLyrics,
                onShowFullLyricsChange = actions.onToggleFullLyrics,
                onOpenSubtitleSheet = actions.onOpenSubtitleSheet,
                onLyricClick = actions.onLyricClick,
                contentModifier = Modifier.fillMaxSize(),
                crossfadeLabel = "LyricsCrossfadePortrait"
            )
        }

        MusicControlPanel(
            playbackState = playbackState,
            subtitleState = subtitleState,
            fileBean = fileBean,
            actions = actions,
            showFullLyrics = showFullLyrics,
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
    subtitleState: SubtitleUiState,
    fileBean: FileBean,
    showFullLyrics: Boolean,
    onShowFullLyricsChange: (Boolean) -> Unit,
    onOpenSubtitleSheet: () -> Unit,
    onLyricClick: (SubtitleEntry) -> Unit,
    contentModifier: Modifier,
    crossfadeLabel: String,
) {
    Crossfade(
        targetState = showFullLyrics,
        label = crossfadeLabel
    ) { isLyrics ->
        if (isLyrics) {
            FullLyricsView(
                entries = subtitleState.entries,
                currentIndex = subtitleState.currentIndex,
                onEntryClick = onLyricClick,
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
    playbackState: AudioPlaybackUiState,
    subtitleState: SubtitleUiState,
    fileBean: FileBean,
    actions: MusicDetailActions,
    showFullLyrics: Boolean,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    itemSpacing: Dp = 12.dp,
) {
    Column(
        modifier = modifier,
        verticalArrangement = verticalArrangement
    ) {
        MusicInfo(fileBean = fileBean)
        Spacer(Modifier.height(itemSpacing))

        SubtitleDisplay(
            subtitleText = subtitleState.currentText,
            hasSelectedSubtitle = subtitleState.selectedSubtitle != null,
            onOpenSubtitleSheet = actions.onOpenSubtitleSheet,
            onToggleFullLyrics = { actions.onToggleFullLyrics(!showFullLyrics) }
        )
        Spacer(Modifier.height(itemSpacing))

        SpeedAndVolume(
            speed = playbackState.playbackSpeed,
            volume = playbackState.volume,
            onChangeSpeed = actions.onChangeSpeed,
            onChangeVolume = actions.onChangeVolume
        )
        Spacer(Modifier.height(itemSpacing))

        PlaybackProgress(
            progress = playbackState.displayProgress,
            positionText = playbackState.currentPositionText,
            isUserSeeking = playbackState.isUserSeeking,
            onSeekStart = actions.onSeekStart,
            onSeekChange = actions.onSeekChange,
            onSeekEnd = actions.onSeekEnd
        )
        Spacer(Modifier.height(itemSpacing))

        PlaybackControls(
            isPlaying = playbackState.isPlaying,
            isLoading = playbackState.isLoading,
            onRewind = actions.onRewind,
            onFastForward = actions.onFastForward,
            onTogglePlayPause = actions.onTogglePlayPause
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
    val mockPlayback = AudioPlaybackUiState(
        currentMusic = mockFile,
        isPlaying = true,
        progress = 0.35f,
        currentPositionText = "01:23 / 03:45",
        playbackSpeed = 1.0f,
        volume = 1.0f
    )
    val mockSubtitle = SubtitleUiState(
        currentText = "这是一句正在高亮展示的歌词文本",
        selectedSubtitle = github.zerorooot.nap511.bean.SubtitleItem(
            id = "sub_001",
            name = "测试歌曲.lrc",
            simpleName = "测试歌曲.lrc",
            sourceType = SubtitleSourceType.ONE_ONE_FIVE
        )
    )
    val mockActions = MusicDetailActions(
        onTogglePlayPause = {},
        onRewind = {},
        onFastForward = {},
        onSeekStart = {},
        onSeekChange = {},
        onSeekEnd = {},
        onChangeSpeed = {},
        onChangeVolume = {},
        onLyricClick = {},
        onToggleFullLyrics = {},
        onOpenSubtitleSheet = {},
        onBack = {}
    )

    Nap511Theme {
        MusicDetailContent(
            playbackState = mockPlayback,
            subtitleState = mockSubtitle,
            showFullLyrics = false,
            actions = mockActions
        )
    }
}
