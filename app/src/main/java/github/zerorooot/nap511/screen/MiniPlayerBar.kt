package github.zerorooot.nap511.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.viewmodel.AudioViewModel

/**
 * 有状态版本：直接绑定 MusicViewModel，供 Scaffold bottomBar 使用
 */
@Composable
fun MiniPlayerBar(
    audioViewModel: AudioViewModel, modifier: Modifier = Modifier, musicOnClick: () -> Unit
) {
    val fileBean = audioViewModel.currentMusic ?: return

    // 如果处于用户拖动状态，优先显示用户拖拽的进度，否则显示播放器真实进度
    val displayProgress = if (audioViewModel.isUserSeeking) {
        audioViewModel.userSeekProgress
    } else {
        audioViewModel.progress
    }
    val onClickEvent: ((String) -> Unit) = {
        when (it) {
            "onSeekStart" -> audioViewModel.onSeekStart()
            "onSeekEnd" -> audioViewModel.onSeekEnd()
            "onTogglePlay" -> audioViewModel.togglePlayPause()
            "onClose" -> audioViewModel.stopAudioAndService()
            "onRewind" -> audioViewModel.onRewind()
            "onFastForward" -> audioViewModel.onFastForward()
        }
    }

    MiniPlayerBarContent(
        fileBean = fileBean,
        isPlaying = audioViewModel.isPlaying,
        isLoading = audioViewModel.isLoading,
        progress = displayProgress,
        isUserSeeking = audioViewModel.isUserSeeking,
        positionText = audioViewModel.currentPositionText,
        onSeekChange = { audioViewModel.onSeekChange(it) },
        modifier = modifier,
        musicOnClick = musicOnClick,
        onClickEvent = onClickEvent
    )
}

/**
 * 纯 UI 渲染版本（无状态），方便 Preview 预览和单元测试
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayerBarContent(
    fileBean: FileBean,
    isPlaying: Boolean,
    isLoading: Boolean,
    progress: Float,
    isUserSeeking: Boolean,
    positionText: String,
    onSeekChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    musicOnClick: () -> Unit,
    onClickEvent: (String) -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(), tonalElevation = 6.dp, shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. 顶部进度条 / 拖动快进快退条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!isUserSeeking) {
                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }

                Slider(
                    value = progress.coerceIn(0f, 1f),
                    onValueChange = {
                        if (!isUserSeeking) {
                            onClickEvent.invoke("onSeekStart")
                        }
                        onSeekChange(it)
                    },
                    onValueChangeFinished = {
                        onClickEvent.invoke("onSeekEnd")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    colors = if (isUserSeeking) {
                        SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        SliderDefaults.colors(
                            thumbColor = androidx.compose.ui.graphics.Color.Transparent,
                            activeTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                            inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    }
                )
            }

            // 2. 播放器主体控制区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = {
                            musicOnClick.invoke()
                        }), verticalAlignment = Alignment.CenterVertically
                ) {
                    // 音频图标
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = "Music",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // 曲目名称与时间点
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileBean.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isLoading) "正在加载音频..." else positionText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 快退
                IconButton(onClick = { onClickEvent.invoke("onRewind") }) {
                    Icon(
                        Icons.Default.FastRewind,
                        contentDescription = "onRewind",
                        modifier = Modifier.size(24.dp)
                    )
                }
                // 播放 / 暂停 / Loading 状态切换
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(onClick = { onClickEvent.invoke("onTogglePlay") }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                // 快进
                IconButton(onClick = { onClickEvent.invoke("onFastForward") }) {
                    Icon(
                        Icons.Default.FastForward,
                        contentDescription = "onFastForward",
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // 关闭播放器
                IconButton(onClick = { onClickEvent.invoke("onClose") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}