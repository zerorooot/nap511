package github.zerorooot.nap511.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale
import com.google.accompanist.pager.*
import com.google.accompanist.systemuicontroller.SystemUiController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.smarttoolfactory.zoom.enhancedZoom
import com.smarttoolfactory.zoom.rememberEnhancedZoomState
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.ImageBean
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.viewmodel.FileViewModel

//todo image cache; image loading animation
@Composable
fun MyPhotoScreen(
    fileViewModel: FileViewModel,
    photoFileBeanList: List<FileBean>,
    indexOf: Int
) {

    fileViewModel.getImage(photoFileBeanList,indexOf)
    val imageBeanList = fileViewModel.imageBeanList
    ImageBrowserScreen(imageBeanList, indexOf)
    rememberSystemUiController().apply {
        isSystemBarsVisible = false
    }

}

/**
 * 大图预览
 */
@OptIn(ExperimentalPagerApi::class)
@Composable
private fun ImageBrowserScreen(images: List<ImageBean>, currentIndex: Int = 0) {
    /**
     * 界面状态变更
     */
    val pageState = rememberPagerState(initialPage = currentIndex)

    Box {
        HorizontalPager(
            count = images.size,
            state = pageState,
            contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = Modifier
                .fillMaxSize()
        ) { page ->
            FullScreenImage(images[page])
        }


        HorizontalPagerIndicator(
            pagerState = pageState,
            activeColor = Color.White,
            inactiveColor = Purple80,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
        )
    }
}

/**
 * https://developer.android.com/jetpack/compose/gestures?hl=zh-cn
 */
@Composable
private fun FullScreenImage(image: ImageBean) {
    Surface(
        modifier = Modifier
            .fillMaxSize(),
        color = Color.Black,
    ) {
        Text(
            text = image.fileName,
            textAlign = TextAlign.Center,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        Image(
            painter = rememberAsyncImagePainter(
                ImageRequest.Builder(LocalContext.current)
                    .data(data = image.url)
                    .apply(block = fun ImageRequest.Builder.() {
                        scale(Scale.FILL)
                        placeholder(R.drawable.png)
                    })
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            ),
            contentDescription = "",
            contentScale = ContentScale.Fit,
            modifier = Modifier.enhancedZoom(
                clip = true,
                enhancedZoomState = rememberEnhancedZoomState(
                    minZoom = .5f,
                    imageSize = IntSize(1080, 1920),
                    limitPan = true,
                    moveToBounds = true
                ),
                enabled = { zoom, _, qq_ ->
                    (zoom > 1f)
                }
            ),

            )

    }
}

/**
 * https://blog.csdn.net/sinat_38184748/article/details/121858073
 * 全屏图片查看
 */


@Preview
@Composable
fun ImageBrowserScreenPreview() {
    val url =
        "http://192.168.123.159/hdd/tele/%e5%b0%81%e7%96%86%e7%96%86v%20-%20%e9%9f%b6%e5%8d%8e%e6%97%97%e8%a2%8d%e7%96%86%20%5b37P-376M%5d/"
    val images = mutableListOf<ImageBean>()
    for (i in 10..15) {
        images.add(ImageBean("$url$i.jpg", "$i.jpg"))
    }
    ImageBrowserScreen(
        images,
//        selectImage = ImageBean(url = "http://192.168.123.159/hdd/tele/%e5%b0%81%e7%96%86%e7%96%86v%20-%20%e9%9f%b6%e5%8d%8e%e6%97%97%e8%a2%8d%e7%96%86%20%5b37P-376M%5d/01.jpg")
    )
}
