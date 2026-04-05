package github.zerorooot.nap511.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.size.Scale
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.smarttoolfactory.zoom.enhancedZoom
import com.smarttoolfactory.zoom.rememberEnhancedZoomState
import github.zerorooot.nap511.bean.ImageBean
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.viewmodel.FileViewModel

@Composable
fun MyPhotoScreen(
    fileViewModel: FileViewModel,
) {
    fileViewModel.getImage(fileViewModel.photoFileBeanList, fileViewModel.photoIndexOf)
    val imageBeanList = fileViewModel.imageBeanList

    val systemUiController = rememberSystemUiController()
    var controlsVisible by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        systemUiController.isSystemBarsVisible = false
        onDispose {
            systemUiController.isSystemBarsVisible = true
        }
    }

    ImageBrowserScreen(
        images = imageBeanList,
        currentIndex = fileViewModel.photoIndexOf,
        controlsVisible = controlsVisible,
        onToggleControls = { controlsVisible = !controlsVisible },
        onBack = {
            App.selectedItem = ConfigKeyUtil.MY_FILE
        })
}

/**
 * 大图预览
 */
@OptIn(ExperimentalPagerApi::class)
@Composable
private fun ImageBrowserScreen(
    images: List<ImageBean>,
    currentIndex: Int = 0,
    controlsVisible: Boolean = true,
    onToggleControls: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val pageState = rememberPagerState(initialPage = currentIndex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            count = images.size,
            state = pageState,
            contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = Modifier.fillMaxSize()
        ) { page ->
            FullScreenImage(
                image = images[page], onClick = onToggleControls
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
                title = if (images.isNotEmpty()) images[pageState.currentPage].fileName else "",
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
                currentIndex = pageState.currentPage + 1, totalCount = images.size
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
    ImageBrowserScreen(
        images = images, currentIndex = 0
    )
}
