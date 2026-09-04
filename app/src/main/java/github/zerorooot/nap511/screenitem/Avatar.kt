package github.zerorooot.nap511.screenitem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.AvatarBean
import github.zerorooot.nap511.bean.RemainingSpaceBean

/**
 * 头像、网名、uid、已用空间
 */
@Composable
fun Avatar(remainingSpaceBean: RemainingSpaceBean, avatarBean: AvatarBean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally, // 水平居中
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(108.dp) // 外层容器略大于头像尺寸
        ) {
            //头像
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(avatarBean.face)
                    .memoryCachePolicy(CachePolicy.ENABLED).diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED).scale(coil.size.Scale.FILL)
                    .memoryCacheKey(avatarBean.userId).diskCacheKey(avatarBean.userId)
                    .placeholder(R.drawable.avatar).build(),
                modifier = Modifier
                    .size(100.dp)
                    //圆形裁剪
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                contentDescription = "Avatar",
            )
            // 环形进度条
            CircularProgressIndicator(
                progress = { (remainingSpaceBean.use.size.toDouble() / remainingSpaceBean.total.size.toDouble()).toFloat() },
                modifier = Modifier.size(108.dp),
                color = MaterialTheme.colorScheme.primary, // 进度条颜色
                trackColor = MaterialTheme.colorScheme.surfaceVariant, // 进度条底色槽（若不需要可设为 Color.Transparent）
                strokeWidth = 3.dp, // 进度线条粗细
                strokeCap = StrokeCap.Round // 圆角笔触，视觉质感更柔和
            )
        }
        Spacer(Modifier.height(6.dp))
        //用户名
        Text(
            text = avatarBean.userName, style = MaterialTheme.typography.titleMedium
        )
        //uid
        Text(text = avatarBean.userId)
        //会员到期时间
        Text(
            text = "会员到期时间：${
                avatarBean.expireString
            }", style = MaterialTheme.typography.titleSmall
        )
        //已用空间
        Text(
            text = "总计${remainingSpaceBean.total.sizeFormat}，已用${remainingSpaceBean.use.sizeFormat}，剩余${remainingSpaceBean.remain.sizeFormat}",
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(6.dp))
    }
}