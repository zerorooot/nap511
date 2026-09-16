package github.zerorooot.nap511.screen.viewer.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import github.zerorooot.nap511.bean.FileBean

/**
 * 专辑封面组件
 *
 * 当 [FileBean.photoThumb] 不为空时使用 [AsyncImage] 加载网络封面图片；
 * 否则展示默认音符图标及次要容器背景。
 *
 * @param fileBean 当前播放的音乐文件元数据
 * @param modifier 布局修饰符
 */
@Composable
fun AlbumCover(
    fileBean: FileBean,
    modifier: Modifier = Modifier
) {
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

/**
 * 音乐基本信息组件
 *
 * 显示歌曲名称（最多 2 行，超长省略）与格式化后的文件大小。
 *
 * @param fileBean 当前播放的音乐文件元数据
 * @param modifier 布局修饰符
 */
@Composable
fun MusicInfo(
    fileBean: FileBean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
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

/**
 * 简易字幕/歌词预览条组件
 *
 * 在播放界面中展示当前正在播放的一/两行字幕文本。
 * - 若已加载字幕：显示字幕文本，点击切换为全屏/全框歌词模式；
 * - 若未加载字幕：显示提示文本，点击弹窗选择/搜索字幕。
 *
 * @param subtitleText 当前播放时间点对应的字幕文本
 * @param hasSelectedSubtitle 当前是否已选择/加载字幕
 * @param onOpenSubtitleSheet 唤起字幕设置弹窗的回调
 * @param onToggleFullLyrics 切换全屏歌词模式的回调
 * @param modifier 布局修饰符
 */
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

/**
 * 播放倍速与音量调节组件
 *
 * 左侧为倍速切换按钮（循环切换 1.0x -> 1.25x -> 1.5x -> 2.0x -> 0.5x）；
 * 右侧为系统/播放器音量控制 Slider。
 *
 * @param speed 当前播放倍速
 * @param volume 当前音量（0.0 ~ 1.0）
 * @param onChangeSpeed 修改倍速回调
 * @param onChangeVolume 修改音量回调
 * @param modifier 布局修饰符
 */
@Composable
fun SpeedAndVolume(
    speed: Float,
    volume: Float,
    onChangeSpeed: (Float) -> Unit,
    onChangeVolume: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 倍速循环按钮
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

        // 音量 Slider
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

/**
 * 播放进度条与时间文本组件
 *
 * 提供进度 Slider，支持拖拽手势：
 * - 拖拽开始时调用 [onSeekStart] 锁定非用户Seek更新；
 * - 拖拽过程中通过 [onSeekChange] 改变临时拖拽位置；
 * - 拖拽结束松开时调用 [onSeekEnd] 执行精准跳转。
 *
 * @param progress 当前播放进度比例 (0.0 ~ 1.0)
 * @param positionText 格式化的播放时间文本 (例如 "01:23/04:56")
 * @param isUserSeeking 用户当前是否正在手动拖拽进度条
 * @param onSeekStart 开始拖拽回调
 * @param onSeekChange 拖拽数值改变回调
 * @param onSeekEnd 拖拽完成松开回调
 * @param modifier 布局修饰符
 */
@Composable
fun PlaybackProgress(
    progress: Float,
    positionText: String,
    isUserSeeking: Boolean,
    onSeekStart: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Slider(
            value = progress,
            onValueChange = {
                if (!isUserSeeking) onSeekStart()
                onSeekChange(it)
            },
            onValueChangeFinished = onSeekEnd
        )
        // 显示当前播放时间点与总时长
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

/**
 * 播放控制组合按钮组件
 *
 * 包含快退、播放/暂停/缓冲加载图标、快进操作按键。
 *
 * @param isPlaying 当前是否处于播放状态
 * @param isLoading 当前音频资源是否正在加载缓冲
 * @param onRewind 快退 15 秒回调
 * @param onFastForward 快进 15 秒回调
 * @param onTogglePlayPause 播放/暂停状态切换回调
 * @param modifier 布局修饰符
 */
@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    onRewind: () -> Unit,
    onFastForward: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 快退按钮
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

        // 中央播放/暂停或缓冲指示器
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

        // 快进按钮
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
