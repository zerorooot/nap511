package github.zerorooot.nap511.screenitem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed as staggeredItemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import github.zerorooot.nap511.bean.FileDisplayConfig
import github.zerorooot.nap511.bean.FileItemActions
import github.zerorooot.nap511.bean.FileListDataState
import github.zerorooot.nap511.bean.FileListScrollState
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.LazyVerticalGridScrollbar
import my.nanihadesuka.compose.LazyVerticalStaggeredGridScrollbar
import my.nanihadesuka.compose.ScrollbarSettings

@Composable
fun FileEmptyContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Text("暂无文件")
    }
}

@Composable
fun FileStaggeredGridList(
    dataState: FileListDataState,
    displayConfig: FileDisplayConfig,
    staggeredGridState: LazyStaggeredGridState,
    itemActions: FileItemActions,
    modifier: Modifier = Modifier
) {
    LazyVerticalStaggeredGridScrollbar(
        state = staggeredGridState,
        settings = ScrollbarSettings.Default.copy(
            thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
        ),
        modifier = modifier
    ) {
        LazyVerticalStaggeredGrid(
            state = staggeredGridState,
            columns = StaggeredGridCells.Adaptive(minSize = displayConfig.gridCellMinSize),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 8.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            staggeredItemsIndexed(
                items = dataState.fileBeanList,
                key = { _, item ->
                    item.fileId.ifEmpty { item.categoryId.ifEmpty { item.pickCode } }
                },
            ) { index, item ->
                val imageBean = dataState.imageCache?.get(item.pickCode)
                ImageCellItem(
                    fileBean = item,
                    index = index,
                    clickIndex = dataState.clickIndex,
                    imageBean = imageBean,
                    isImageHdPreview = displayConfig.isImageHdPreview,
                    modifier = Modifier, // 瀑布流快速滑动时不施加 animateItem 动画，防止布局重新计算时元素跳动
                    itemActions = itemActions,
                )
            }
        }
    }
}

@Composable
fun FileGridList(
    dataState: FileListDataState,
    displayConfig: FileDisplayConfig,
    gridState: LazyGridState,
    itemActions: FileItemActions,
    modifier: Modifier = Modifier
) {
    LazyVerticalGridScrollbar(
        state = gridState,
        settings = ScrollbarSettings.Default.copy(
            thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
        ),
        modifier = modifier
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = displayConfig.gridCellMinSize),
            modifier = Modifier.fillMaxSize()
        ) {
            gridItemsIndexed(
                items = dataState.fileBeanList,
                key = { _, item ->
                    item.fileId.ifEmpty { item.categoryId.ifEmpty { item.pickCode } }
                },
            ) { index, item ->
                FileCellItem(
                    fileBean = item,
                    index = index,
                    clickIndex = dataState.clickIndex,
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        fadeOutSpec = null
                    ),
                    itemActions = itemActions,
                )
            }
        }
    }
}

@Composable
fun FileColumnList(
    dataState: FileListDataState,
    listState: LazyListState,
    itemActions: FileItemActions,
    modifier: Modifier = Modifier
) {
    LazyColumnScrollbar(
        state = listState,
        settings = ScrollbarSettings.Default.copy(
            thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
        ),
        modifier = modifier
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            itemsIndexed(
                items = dataState.fileBeanList,
                key = { _, item ->
                    item.fileId.ifEmpty { item.categoryId.ifEmpty { item.pickCode } }
                },
            ) { index, item ->
                FileCellItem(
                    fileBean = item,
                    index = index,
                    clickIndex = dataState.clickIndex,
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        fadeOutSpec = null
                    ),
                    itemActions = itemActions,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListContent(
    dataState: FileListDataState,
    displayConfig: FileDisplayConfig,
    scrollState: FileListScrollState,
    itemActions: FileItemActions,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = dataState.refreshing,
        onRefresh = itemActions.onRefresh,
        modifier = modifier
    ) {
        if (dataState.fileBeanList.isEmpty()) {
            FileEmptyContent()
        } else {
            key(dataState.path, displayConfig.isPreviewActive) {
                if (displayConfig.isPreviewActive) {
                    FileStaggeredGridList(
                        dataState = dataState,
                        displayConfig = displayConfig,
                        staggeredGridState = scrollState.staggeredGridState,
                        itemActions = itemActions
                    )
                } else if (displayConfig.isExpandedScreen) {
                    FileGridList(
                        dataState = dataState,
                        displayConfig = displayConfig,
                        gridState = scrollState.gridState,
                        itemActions = itemActions
                    )
                } else {
                    FileColumnList(
                        dataState = dataState,
                        listState = scrollState.listState,
                        itemActions = itemActions
                    )
                }
            }
        }
    }
}
