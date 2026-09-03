package github.zerorooot.nap511.screen


import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import github.zerorooot.nap511.util.CaptchaChallengeData
import github.zerorooot.nap511.util.OneOneFiveAuthManager
import kotlinx.coroutines.launch

@Composable
fun CaptchaPromptDialog(
    authManager: OneOneFiveAuthManager,
    onDismiss: () -> Unit,
    onVerifySuccess: (code: String, sign: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var captchaData by remember { mutableStateOf<CaptchaChallengeData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val selectedIndices = remember { mutableStateListOf<Int>() }

    // 加载或刷新验证码
    fun loadCaptcha() {
        isLoading = true
        selectedIndices.clear()
        coroutineScope.launch {
            captchaData = authManager.fetchCaptchaChallenge()
            isLoading = false
            if (captchaData == null) {
                Toast.makeText(context, "加载验证码失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        loadCaptcha()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 标题与关闭
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "请完成安全验证", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) {
                        Text("关闭", fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                } else if (captchaData != null) {
                    val data = captchaData!!

                    // 2. 目标字符提示区域
                    Text(
                        text = "请按顺序点击图中的汉字",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8F8F8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = data.targetImage,
                            contentDescription = "Target Captcha",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // 3. 已选序列回显与刷新
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 已选序列图片预览
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("已选: ", fontSize = 12.sp, color = Color.Gray)
                            if (selectedIndices.isEmpty()) {
                                Text("暂无选择", fontSize = 12.sp, color = Color.LightGray)
                            } else {
                                selectedIndices.forEach { index ->
                                    Image(
                                        bitmap = data.candidateImages[index],
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .padding(horizontal = 2.dp)
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(4.dp)
                                            )
                                    )
                                }
                            }
                        }

                        // 删除与刷新按钮
                        Row {
                            if (selectedIndices.isNotEmpty()) {
                                Text(
                                    text = "撤销",
                                    fontSize = 12.sp,
                                    color = Color.Red,
                                    modifier = Modifier
                                        .clickable { selectedIndices.removeLastOrNull() }
                                        .padding(horizontal = 6.dp)
                                )
                            }
                            Text(
                                text = "换一张",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { loadCaptcha() }
                                    .padding(start = 6.dp)
                            )
                        }
                    }

                    // 4. 10 个候选汉字矩阵 (5列 x 2行)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(data.candidateImages.size) { index ->
                            val isSelected = selectedIndices.contains(index)
                            Card(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clickable(enabled = selectedIndices.size < 4 && !isSelected) {
                                        selectedIndices.add(index)
                                    },
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFFE3F2FD) else Color.White
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = data.candidateImages[index],
                                        contentDescription = "Key $index",
                                        modifier = Modifier.size(32.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 5. 立即验证提交按钮
                    Button(
                        onClick = {
                            if (selectedIndices.size < 4) {
                                Toast.makeText(
                                    context,
                                    "请选择 4 个汉字后再提交",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            val codeString = selectedIndices.joinToString("")
                            onVerifySuccess(codeString, data.sign)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        enabled = selectedIndices.size >= 4
                    ) {
                        Text("立即验证", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}