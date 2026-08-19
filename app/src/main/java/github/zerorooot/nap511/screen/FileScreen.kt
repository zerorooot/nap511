package github.zerorooot.nap511.screen

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.nativeClipboardManager
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import github.zerorooot.nap511.R
import github.zerorooot.nap511.activity.VideoActivity
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.bean.VideoInfoBean
import github.zerorooot.nap511.screenitem.FileCellItem
import github.zerorooot.nap511.ui.theme.Purple80
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.DataStoreUtil
import github.zerorooot.nap511.viewmodel.AudioViewModel
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.cancelCut
import github.zerorooot.nap511.viewmodel.cut
import github.zerorooot.nap511.viewmodel.delete
import github.zerorooot.nap511.viewmodel.downloadText
import github.zerorooot.nap511.viewmodel.getFileInfo
import github.zerorooot.nap511.viewmodel.getVideoInfo
import github.zerorooot.nap511.viewmodel.getZipListFile
import github.zerorooot.nap511.viewmodel.openAria2Dialog
import github.zerorooot.nap511.viewmodel.openCreateFolderDialog
import github.zerorooot.nap511.viewmodel.openCreateSelectTorrentFileDialog
import github.zerorooot.nap511.viewmodel.openRenameFileDialog
import github.zerorooot.nap511.viewmodel.removeFile
import github.zerorooot.nap511.viewmodel.startSendAria2Service
import github.zerorooot.nap511.viewmodel.updateVideoFileBean
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import kotlin.time.Duration.Companion.milliseconds

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun FileScreen(
    fileViewModel: FileViewModel,
    offlineFileViewModel: OfflineFileViewModel,
    audioViewModel: AudioViewModel,
    onNav: (Route) -> Unit,
    appBarOnClick: (String) -> Unit,
    drawerState: () -> Boolean
) {
    val fabPosition by remember {
        mutableStateOf(
            when (DataStoreUtil.getData(ConfigKeyUtil.FLOATING_ACTION_BUTTON_POSITION, "End")) {
                "Start" -> FabPosition.Start
                "Center" -> FabPosition.Center
                "End" -> FabPosition.End
                "EndOverlay" -> FabPosition.EndOverlay
                else -> FabPosition.End
            }
        )
    }

    val fileBeanList = fileViewModel.fileBeanList
    val path by fileViewModel.currentPath.collectAsState()

    val listLocation = fileViewModel.getListLocation(path)
    val listState = key(path) {
        rememberLazyListState(
            listLocation.firstVisibleItemIndex,
            listLocation.firstVisibleItemScrollOffset
        )
    }
    // 1. 定义 Animatable 状态
    val animProgress = remember { Animatable(1f) }

    val density = LocalDensity.current
    // 1. 设置 35dp 的防抖阈值
    val thresholdPx = remember(density) { with(density) { 35.dp.toPx() } }
    var isBottomBarShow by remember { mutableStateOf(true) }
    // 使用动画控制缩放和透明度（只作用于 Draw 阶段，不影响 Layout 测量）
    // 2. 监听 isVisible 变化并执行动画
    LaunchedEffect(isBottomBarShow) {
        animProgress.animateTo(
            targetValue = if (isBottomBarShow) 1f else 0f,
            animationSpec = if (isBottomBarShow) {
                // 显示（展开）：250ms，迅速展现
                tween(durationMillis = 250, easing = LinearOutSlowInEasing)
            } else {
                // 隐藏（收起）：500ms，慢慢缩小消失，减少视觉上的突兀感
                tween(durationMillis = 500, easing = FastOutLinearInEasing)
            },
        )
    }
    // 2. 嵌套滚动监听
    val nestedScrollConnection = remember {
        var accumulatedDelta = 0f

        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y

                // 方向改变，重置滑动累加值
                if ((delta > 0 && accumulatedDelta < 0) || (delta < 0 && accumulatedDelta > 0)) {
                    accumulatedDelta = 0f
                }

                accumulatedDelta += delta

                // 【关键点】增加状态判断 (`&& isBottomBarShow` / `&& !isBottomBarShow`)，防止重复更新状态引发卡顿
                if (accumulatedDelta < -thresholdPx && isBottomBarShow) {
                    isBottomBarShow = false
                } else if (accumulatedDelta > thresholdPx && !isBottomBarShow) {
                    isBottomBarShow = true
                }
                return Offset.Zero
            }
        }
    }

    val refreshing by fileViewModel.isRefreshing.collectAsState()

    val context = LocalContext.current

    val videoActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val index = data?.getIntExtra("fileBeanIndex", -1) ?: -1
            val duration = data?.getIntExtra("current_time", 0) ?: 0
            val pickCode = data?.getStringExtra("pickCode") ?: "0"
            fileViewModel.updateVideoFileBean(fileViewModel.currentCid, index, duration, pickCode)

            val nav = data?.getStringExtra("nav") ?: ""
            if (nav == "VerifyVideoAccount") {
                onNav.invoke(Route.VerifyVideoAccount)
            }
            val message = data?.getStringExtra("toast") ?: ""
            if (message.isNotEmpty()) {
                App.instance.toast(message)
            }
        }
    }

    LaunchedEffect(Unit) {
        fileViewModel.launchVideoEvent.collect { videoDate ->
            val videoInfoBeanJson = Gson().toJson(videoDate, VideoInfoBean::class.java)
            val intent = Intent(context, VideoActivity::class.java).apply {
                putExtra("fileBeanIndex", videoDate.index)
                putExtra("bean", videoInfoBeanJson)
            }
            videoActivityLauncher.launch(intent)
        }
    }

    fun handleMultiSelectClick(i: Int) {
        fileViewModel.select(i)
    }

    fun handleFolderClick(i: Int, fileBean: FileBean) {
        if (DataStoreUtil.getData(ConfigKeyUtil.EARLY_LOADING, false)) {
            listOf(i - 1, i + 1)
                .mapNotNull { fileBeanList.getOrNull(it) }
                .filter { it.isFolder }
                .forEach { fileViewModel.updateFileCache(it.categoryId) }
        }
        fileViewModel.getFiles(fileBean.categoryId)
    }

    fun handleVideoClick(i: Int, fileBean: FileBean) {
        audioViewModel.pause()
        fileViewModel.getVideoInfo(fileBean.pickCode, i, fileBean.name)
    }

    fun handleAudioClick(fileBean: FileBean) {
        isBottomBarShow = true
        fileViewModel.setRefreshingStatus(false)
        audioViewModel.playAudio(fileBean)
    }

    fun handlePhotoClick(fileBean: FileBean) {
        audioViewModel.pause()
        val photoList = fileBeanList.filter { it.photoThumb != "" }
        fileViewModel.photoFileBeanList.clear()
        fileViewModel.photoFileBeanList.addAll(photoList)
        fileViewModel.photoIndexOf = photoList.indexOf(fileBean)
        onNav.invoke(Route.Photo)
        fileViewModel.setRefreshingStatus(false)
    }


    fun handleTorrentClick(fileBean: FileBean) {
        offlineFileViewModel.getTorrentTask(fileBean.sha1)
        fileViewModel.openCreateSelectTorrentFileDialog()
    }

    fun handleZipClick(i: Int) {
        fileViewModel.selectIndex = i
        fileViewModel.getZipListFile()
    }

    fun handleTextClick(i: Int, fileBean: FileBean) {
        val txtSize = DataStoreUtil.getData(ConfigKeyUtil.MAX_TXT_SIZE, "200").toInt()
        if (fileBean.size.toLong() < txtSize * 1024) {
            fileViewModel.selectIndex = i
            fileViewModel.downloadText(fileBean, onNav)
        } else {
            fileViewModel.setRefreshingStatus(false)
            App.instance.toast("仅支持打开${txtSize}kb以下的文件")
        }
    }

// 记录上次点击时间，使用 longArrayOf 避免无意义的重组
    val lastClickTime = remember { longArrayOf(0L) }

    // Assembled myItemOnClick — routes to focused handlers
    fun myItemOnClick(i: Int) {
        if (fileViewModel.isLongClickState) {
            handleMultiSelectClick(i)
        } else {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastClickTime[0] < 200L) { // 200ms 内的连点会被忽略
                return
            }
            lastClickTime[0] = currentTime


            fileViewModel.setRefreshingStatus(true)

            //记录上级目录当前的位置
            fileViewModel.setListLocationAndClickCache(i, listState)
            val fileBean = fileBeanList[i]

            when {
                fileBean.isFolder -> handleFolderClick(i, fileBean)
                fileBean.isVideo == 1 -> handleVideoClick(i, fileBean)
                fileBean.fileIco == R.drawable.torrent -> handleTorrentClick(fileBean)
                fileBean.fileIco == R.drawable.zip -> handleZipClick(i)
                fileBean.fileIco == R.drawable.txt -> handleTextClick(i, fileBean)
                fileBean.fileIco == R.drawable.mp3 -> handleAudioClick(fileBean)
                fileBean.photoThumb.isNotEmpty() -> handlePhotoClick(fileBean)
                else -> fileViewModel.setRefreshingStatus(false)
            }

        }
    }

    // ============================================================
    // Phase 2.1: Eliminate FAB string dispatch
    // ============================================================
    fun onCutPasteClick() {
        fileViewModel.removeFile()
    }

    fun onAddFolderClick() {
        fileViewModel.openCreateFolderDialog()
    }

    fun onCancelCutClick() {
        fileViewModel.cancelCut()
    }

    // ============================================================
    // Phase 2.2: Eliminate menu string dispatch
    // ============================================================
    fun onMenuCut(index: Int) {
        fileViewModel.cut(index)
    }

    fun onMenuDelete(index: Int) {
        fileViewModel.delete(index)
    }

    fun onMenuRename(index: Int) {
        fileViewModel.selectIndex = index
        fileViewModel.openRenameFileDialog()
    }

    fun onMenuFileInfo(index: Int) {
        fileViewModel.selectIndex = index
        fileViewModel.getFileInfo(index)
    }

    fun onMenuAria2Download(index: Int) {
        val aria2Url =
            DataStoreUtil.getData(ConfigKeyUtil.ARIA2_URL, ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE)
        if (aria2Url == ConfigKeyUtil.ARIA2_URL_DEFAULT_VALUE) {
            fileViewModel.openAria2Dialog()
        } else {
            fileViewModel.startSendAria2Service(index)
        }
    }

    val scope = rememberCoroutineScope()

    // ============================================================
    // Phase 2.3: Extract onBackClick
    // ============================================================
    fun onBack() {
        if (drawerState.invoke()) {
            return
        }
        if (path != "/根目录" && !fileViewModel.isLongClickState) {
            fileViewModel.setListLocation(path, listState)
        }
        //触发路径和数据源的改变，重组后交由上方滚动
        fileViewModel.back()
    }



    BackHandler(
        path != "/根目录" || fileViewModel.isLongClickState || fileViewModel.isSearchState,
        ::onBack
    )

    fun myAppBarOnClick(name: String) {
        when (name) {
            "back" -> {
                onBack()
            }

            "视频时间" -> {
                scope.launch {
                    fileViewModel.fileBeanList.sortByDescending { fileBean -> fileBean.playLong }
                    delay(10.milliseconds)
                    listState.requestScrollToItem(0, 0)
                }

            }

            "缓存清空" -> {
                fileViewModel.refresh(true)
            }

            else -> {
                appBarOnClick(name)
            }
        }
    }

    val clipboardManager = LocalClipboard.current

    // ============================================================
    // Phase 3: Extract path bar click callbacks
    // ============================================================
    fun onPathClick() {
        clipboardManager.nativeClipboardManager.setPrimaryClip(
            ClipData.newPlainText("path", path)
        )
        App.instance.toast("$path 已复制到剪切板")
    }

    fun onPathDoubleClick() {
        listState.requestScrollToItem(0, 0)
    }

    fun onPathLongClick() {
        DataStoreUtil.putData(ConfigKeyUtil.DEFAULT_OFFLINE_CID, fileViewModel.currentCid)
        App.instance.toast("已设置默认离线位置")
    }

    // ============================================================
    // Phase 4: inline itemOnLongClick (no remember needed)
    // ============================================================
    fun itemOnLongClick(i: Int) {
        fileViewModel.isLongClickState = !fileViewModel.isLongClickState
        if (fileViewModel.isLongClickState) {
            fileViewModel.select(i)
        } else {
            fileViewModel.appBarTitle = "nap511"
        }
    }

    Column {
        AnimatedContent(
            targetState = fileViewModel.isLongClickState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = ""
        ) {
            if (it) {
                AppTopBarMultiple(fileViewModel.appBarTitle, ::myAppBarOnClick)
            } else {
                AppTopBarNormal(fileViewModel.appBarTitle, ::myAppBarOnClick)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = ::onPathClick,
                    onDoubleClick = ::onPathDoubleClick,
                    onLongClick = ::onPathLongClick,
                ),
        ) {
            MiddleEllipsisText(
                text = path, modifier = Modifier.padding(8.dp, 4.dp)
            )
        }

        Scaffold(
            modifier = Modifier.nestedScroll(nestedScrollConnection),
            bottomBar = {
                AnimatedVisibility(
                    visible = audioViewModel.currentMusic != null,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                    // 利用 graphicsLayer 进行 GPU 缩放与透明度变换，零布局开销
                    modifier = Modifier.graphicsLayer {
                        /// 【核心】：完全不触发 Recompose / Layout，仅 GPU 绘图层平移
                        val progress = animProgress.value
                        translationY = (1f - progress) * size.height
                        alpha = progress
                    }
                ) {
                    MiniPlayerBar(audioViewModel = audioViewModel)
                }
            },
            floatingActionButton = {
                AnimatedContent(
                    targetState = fileViewModel.isCutState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    // 利用 graphicsLayer 进行 GPU 缩放与透明度变换，零布局开销
                    modifier = Modifier.graphicsLayer {
                        val progress = animProgress.value
                        scaleX = progress
                        scaleY = progress
                        alpha = progress
                        translationY = (1f - progress) * 80.dp.toPx()
                    }
                ) {
                    if (it) {
                        Column {
                            FloatingActionButton(onClick = ::onCancelCutClick) {
                                Icon(Icons.Filled.Close, "close")
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            FloatingActionButton(onClick = ::onCutPasteClick) {
                                Icon(
                                    Icons.Default.ContentPaste,
                                    "cut"
                                )
                            }
                        }
                    } else {
                        FloatingActionButton(onClick = ::onAddFolderClick) {
                            Icon(Icons.Filled.Add, "add")
                        }
                    }
                }

            },
            floatingActionButtonPosition = fabPosition
        ) { _ ->
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { fileViewModel.refresh() }
            ) {
                if (fileBeanList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()), // 添加垂直滚动支持以分发下拉手势,
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无文件")
                    }
                } else {
                    //当 path 改变时，强制销毁并重新创建 Scrollbar，让它正确绑定新传入的 listState
                    key(path) {
                        LazyColumnScrollbar(
                            state = listState,
                            settings = ScrollbarSettings.Default.copy(
                                thumbUnselectedColor = Purple80
                            )
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState
                            ) {
                                itemsIndexed(
                                    items = fileBeanList,
                                    key = { _, item ->
                                        item.fileId.ifEmpty { item.pickCode.ifEmpty { item.uuid.toString() } }
                                    },
                                    //  区分类型：
                                    contentType = { _, item ->
                                        item.fileIco
                                    }
                                ) { index, item ->
                                    FileCellItem(
                                        item,
                                        index,
                                        fileViewModel.clickMap.getOrDefault(path, -1),
                                        Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                        ::myItemOnClick,
                                        itemOnLongClick = ::itemOnLongClick,
                                        onCut = ::onMenuCut,
                                        onDelete = ::onMenuDelete,
                                        onRename = ::onMenuRename,
                                        onFileInfo = ::onMenuFileInfo,
                                        onAria2Download = ::onMenuAria2Download,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
