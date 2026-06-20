package github.zerorooot.nap511.screen

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.size.Scale
import com.smarttoolfactory.zoom.enhancedZoom
import com.smarttoolfactory.zoom.rememberEnhancedZoomState
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.ImageBean
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.getImage

@Composable
fun MyPhotoScreen(
    fileViewModel: FileViewModel,
) {
    var controlsVisible by remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(Unit) {
        // 获取 Window 实例（注意：需要确保 context 是 Activity）
        val window = (view.context as? Activity)?.window
            ?: throw Exception("Not in an Activity - unable to get Window reference")

        // 创建控制器
        val insetsController = WindowCompat.getInsetsController(window, view)

        // 隐藏状态栏和导航栏
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        // 设置交互模式：滑动边缘时临时显示（Immersive Sticky 模式）
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            // 退出时恢复显示
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

// 从 ViewModel 提取当前页面的状态数据
    val photoList = fileViewModel.photoFileBeanList
    // 提取当前相册的缓存字典
    val imageCache = fileViewModel.imageBeanCache[fileViewModel.currentCid] ?: emptyMap()

    ImageBrowserScreen(
        photoList = photoList,
        imageCache = imageCache,
        currentIndex = fileViewModel.photoIndexOf,
        onLoadImage = { pageIndex ->
            fileViewModel.getImage(photoList, pageIndex)
        },
        onToggleControls = { controlsVisible = !controlsVisible },
        onBack = {
            fileViewModel.selectedItem = ConfigKeyUtil.MY_FILE
        })
}

/**
 * 大图预览
 */

@Composable
private fun ImageBrowserScreen(
    // 1. 纯数据传入
    photoList: List<FileBean>,
    imageCache: Map<Int, ImageBean>,
    currentIndex: Int = 0,
    controlsVisible: Boolean = true,
    // 2. 动作回调传出
    onLoadImage: (pageIndex: Int) -> Unit, // 替换原来的 viewModel.getImage
    onToggleControls: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val rememberPagerState = rememberPagerState(
        initialPage = currentIndex,
        pageCount = { photoList.size })


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            rememberPagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            LaunchedEffect(page) {
                onLoadImage(page)
            }
            // 直接从传入的 Map 中取数据，不需要知道 currentCid 是什么
            val pageImage = imageCache[page] ?: ImageBean()
            FullScreenImage(
                image = pageImage,
                onClick = onToggleControls
            )
        }

        // Top Bar
        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            PhotoTopBar(
                title = imageCache.getOrDefault(
                    rememberPagerState.currentPage,
                    ImageBean()
                ).fileName.ifEmpty { "" },
                onBack = onBack
            )
        }

        // Bottom Bar (Index Indicator)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PhotoBottomBar(
                currentIndex = rememberPagerState.currentPage + 1,
                totalCount = rememberPagerState.pageCount
            )
        }
    }
}

@Composable
private fun PhotoTopBar(title: String, onBack: () -> Unit) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth(), contentAlignment = Alignment.CenterStart
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 48.dp, end = 16.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun PhotoBottomBar(currentIndex: Int, totalCount: Int) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 16.dp, top = 16.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$currentIndex / $totalCount",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

/**
 * 全屏图片查看
 */
@Composable
private fun FullScreenImage(image: ImageBean, onClick: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(image.url)
                .crossfade(true).scale(Scale.FIT).build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onState = { state ->
                isLoading = state is AsyncImagePainter.State.Loading
            },
            modifier = Modifier
                .fillMaxSize()
                .enhancedZoom(
                    clip = true, enhancedZoomState = rememberEnhancedZoomState(
                        minZoom = 0.8f,
                        maxZoom = 5f,
                        imageSize = IntSize(1080, 1920),
                        limitPan = true,
                        moveToBounds = true
                    ), enabled = { zoom, _, _ ->
                        (zoom > 1f)
                    })
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() }
                    )
                },
        )

        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.7f),
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Preview
@Composable
fun ImageBrowserScreenPreview() {
    val images = mutableListOf<ImageBean>()
    for (i in 1..5) {
        images.add(ImageBean("url", "Image $i.jpg"))
    }
//    ImageBrowserScreen(
//        images = images, currentIndex = 0
//    )
}
