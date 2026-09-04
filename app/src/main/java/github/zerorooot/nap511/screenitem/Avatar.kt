package github.zerorooot.nap511.screenitem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.gson.Gson
import github.zerorooot.nap511.R
import github.zerorooot.nap511.bean.AvatarBean
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.viewmodel.FileViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 头像、网名、uid、已用空间
 */
@Composable
fun Avatar(fileViewModel: FileViewModel) {
    val remainingSpaceBean = fileViewModel.remainingSpace

    val avatarJson by DataStoreUtil.getDataFlow(ConfigKeyUtil.AVATAR_BEAN, "{}")
        .collectAsStateWithLifecycle(initialValue = "{}")

    val avatarBean = remember(avatarJson) {
        try {
            Gson().fromJson(avatarJson, AvatarBean::class.java) ?: AvatarBean()
        } catch (_: Exception) {
            AvatarBean()
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally, // 水平居中
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
//            Spacer(Modifier.height(6.dp))
        //已用空间
        Text(
            text = "总计${remainingSpaceBean.total.sizeFormat}，已用${remainingSpaceBean.use.sizeFormat}，剩余${remainingSpaceBean.remain.sizeFormat}",
            style = MaterialTheme.typography.titleSmall
        )
//            //进度条
//            LinearProgressIndicator(
//                progress = (remainingSpaceBean.value.allUse.toDouble() / remainingSpaceBean.value.allTotal).toFloat(),
//                color = Color.Cyan,
//                modifier = Modifier
//                    .fillMaxWidth(0.7f)
//                    .clip(shape = RoundedCornerShape(100.dp))
//            )
    }
}