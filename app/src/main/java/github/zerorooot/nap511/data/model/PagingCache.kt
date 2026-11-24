package github.zerorooot.nap511.data.model

import com.elvishew.xlog.XLog

/**
 * 分页缓存的构造与判定工具（纯函数，无副作用）。
 *
 * 设计目标：把 Folder 源「物理游标 + 完成态 + 流身份」的构造与门禁判定收口到一处，
 * 供 FileBrowserModule（进场/触底/返回）与 DirectoryPagingCoordinatorImpl（增量/全量/流式）共用，
 * 杜绝散落各处的 setOffset(cache.size) 之类的游标漂移写法。
 *
 * 游标权威来源：[FilesBean.physicalOffset]，与缓存同生共死；PagingStateStore 的 physicalOffsets
 * 仅服务 Search/Filter/Star 等非 Folder 源。
 */
object PagingCache {
    private const val TAG = "PagingCache"

    /**
     * 从头消费出一份可续拉缓存（首屏 / 全量 / 流式 / 静默预拉 / join-batch 均走这里）。
     *
     * @param base 仅作为 copy 基底（非游标字段随后由调用方按需覆盖），传空壳 FilesBean(cid=...) 即可
     * @param rawTotalConsumed 本流从头累计消费的服务端原始条数（format/distinct 之前），即下一页起点
     * @param list 已格式化/去重后的展示列表
     */
    fun fromHead(base: FilesBean, rawTotalConsumed: Int, list: List<FileBean>): FilesBean {
        XLog.d("$TAG.fromHead cid=${base.cid}, rawTotalConsumed=$rawTotalConsumed, listSize=${list.size}")
        return base.copy(fileBeanList = ArrayList(list), physicalOffset = rawTotalConsumed)
    }

    /**
     * 在一份已通过 [resumable] 的基底缓存上追加一页。游标由缓存自身推进，不依赖外部传参。
     *
     * @param rawBatchSize 本次服务端原始返回条数（去重前，非 merged.size）；用 merged.size 会导致游标漂移
     */
    fun append(
        base: FilesBean,
        rawBatchSize: Int,
        merged: List<FileBean>,
        count: Int,
        order: OrderBean,
        fcMix: Int
    ): FilesBean {
        val nextOffset = base.physicalOffset + rawBatchSize
        XLog.d(
            "$TAG.append cid=${base.cid}, baseOffset=${base.physicalOffset}, rawBatchSize=$rawBatchSize, " +
                    "nextOffset=$nextOffset, mergedSize=${merged.size}, count=$count, order=${order.type}, asc=${order.asc}, fcMix=$fcMix"
        )
        return base.copy(
            fileBeanList = ArrayList(merged),
            count = count,
            order = order.type,
            isAsc = order.asc,
            fcMix = fcMix,
            physicalOffset = nextOffset
        )
    }

    /**
     * 缓存是否「自洽可信」：完整，或有续拉锚点（physicalOffset>0 且去重后 size 不超过游标）。
     *
     * 链3 残卷（旧版本无 physicalOffset=0 且非 complete）在此被拒；新版本残卷由写入侧
     * [append] 的 base 门禁（§3.2）杜绝，形成双防线。
     */
    private fun anchored(c: FilesBean): Boolean =
        c.isComplete || (c.physicalOffset > 0 && c.fileBeanList.size <= c.physicalOffset)

    /**
     * 展示门禁（进场/乐观导航/返回）：缓存自洽即可，不强制匹配当前 UI 流。
     * back 时 UI 处于 query 态，其 currentFcMix 不代表目标目录的历史身份，故此处只验自洽。
     */
    fun displayable(cache: FilesBean?, cid: String): Boolean {
        val ok = cache != null && cache.cid == cid && anchored(cache)
        XLog.d("$TAG.displayable cid=$cid, result=$ok, cacheCid=${cache?.cid}, offset=${cache?.physicalOffset}, complete=${cache?.isComplete}")
        return ok
    }

    /**
     * 续拉门禁（触底 / coordinator / 进场主路径）：必须属于指定流且自洽。
     *
     * 流身份 = order + asc + fcMix。folderKey 不含这三者，所以流身份只能靠缓存元数据比对，
     * 失配时调用方必须先 remove(cid) 再回源，否则宽松的 displayable 会命中旧缓存造成假回源/设置回退。
     */
    fun resumable(cache: FilesBean?, cid: String, order: OrderBean, fcMix: Int): Boolean {
        val ok = cache != null && displayable(cache, cid) &&
                cache.order == order.type && cache.isAsc == order.asc && cache.fcMix == fcMix
        XLog.d(
            "$TAG.resumable cid=$cid, result=$ok, " +
                    "cacheStream=${cache?.order}/${cache?.isAsc}/${cache?.fcMix}, " +
                    "wantStream=${order.type}/${order.asc}/$fcMix"
        )
        return ok
    }
}
