package github.zerorooot.nap511.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.elvishew.xlog.XLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import github.zerorooot.nap511.util.App
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * 全局解压队列管理器 (SSOT 单一数据源)
 * 职责：
 * 1. 内存高性能待办池 + 磁盘原子落盘，防止进程被杀 (LMK) 后解压任务丢失；
 * 2. 状态机互斥与 Double-Check 退出机制，彻底杜绝 Worker 退出临界点与新追加任务产生的假死竞态。
 */
object UnzipQueueManager {
    val lock = Any()
    private val queue = ConcurrentLinkedQueue<UnzipTaskItem>()
    private val totalCount = AtomicInteger(0)
    private val processedCount = AtomicInteger(0)
    private val gson = Gson()

    @Volatile
    private var currentRunningTask: UnzipTaskItem? = null

    // 标记当前是否有活跃 Worker 处于运行消费状态
    @Volatile
    var isWorkerRunning: Boolean = false
        private set

    // 持久化备份文件 (捕获测试环境无 Android Context 异常以保证单测兼容性)
    private val queueBackupFile: File? by lazy {
        try {
            File(App.instance.filesDir, "unzip_task_queue_v2.json")
        } catch (e: Throwable) {
            null
        }
    }

    init {
        restoreFromDisk()
    }

    /**
     * 任务入队并触发后台 Worker 调度（单 Worker 消费者模式，ExistingWorkPolicy.KEEP）
     */
    fun enqueueAndStartWorker(
        items: List<UnzipTaskItem>,
        context: Context = App.instance.applicationContext
    ) {
        if (items.isEmpty()) return
        enqueue(items)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequest.Builder(UnzipAllFileWorker::class.java)
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("UnzipAllFileWorkerOneTimeWorkRequest")
            .build()

        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.enqueueUniqueWork(
            "unzipAllFileWorker", ExistingWorkPolicy.KEEP, request
        )
    }

    /**
     * 任务入队：追加到内存队列并同步落盘
     */
    fun enqueue(items: List<UnzipTaskItem>) {
        synchronized(lock) {
            if (queue.isEmpty() && currentRunningTask == null && !isWorkerRunning) {
                // 前序批次全部处理完毕且无 Worker 运行时，重置计数器
                totalCount.set(0)
                processedCount.set(0)
            }
            totalCount.addAndGet(items.size)
            queue.addAll(items)
            persistToDisk()
            XLog.d("UnzipQueueManager: 入队 ${items.size} 项, 当前待办: ${queue.size}, 总计: ${totalCount.get()}")
        }
    }

    /**
     * 取出下一个待处理任务
     */
    fun pollNext(): UnzipTaskItem? = synchronized(lock) {
        val item = queue.poll()
        currentRunningTask = item
        persistToDisk()
        return@synchronized item
    }

    /**
     * 单个任务完成：步进进度、清空当前运行任务并落盘
     */
    fun onTaskCompleted(task: UnzipTaskItem) {
        synchronized(lock) {
            if (currentRunningTask?.taskId == task.taskId) {
                currentRunningTask = null
            }
            processedCount.incrementAndGet()
            persistToDisk()
        }
    }

    /**
     * Worker 标记自身已开始运行
     */
    fun markWorkerRunning() {
        synchronized(lock) {
            isWorkerRunning = true
        }
    }

    /**
     * 检查是否还有待处理任务
     */
    fun hasMore(): Boolean = synchronized(lock) {
        !queue.isEmpty() || currentRunningTask != null
    }

    /**
     * 双重检查退出判定 (Double-Check Drain Pattern)
     * 只有在 synchronized 临界区内确认队列确已为空，才允许 Worker 终止退出；
     * @return true 允许退出 (队列确为空)；false 有并发新任务追加，必须继续消费！
     */
    fun tryFinishWorker(): Boolean = synchronized(lock) {
        if (queue.isEmpty() && currentRunningTask == null) {
            isWorkerRunning = false
            persistToDisk()
            return@synchronized true
        }
        return@synchronized false
    }

    /**
     * 恢复 Worker 运行状态 (当在收尾临界区发现有新任务注入时)
     */
    fun resumeWorkerRunning() {
        synchronized(lock) {
            isWorkerRunning = true
        }
    }

    /**
     * 用户主动取消或重置：清空内存与磁盘
     */
    fun clear() {
        synchronized(lock) {
            queue.clear()
            currentRunningTask = null
            totalCount.set(0)
            processedCount.set(0)
            isWorkerRunning = false
            queueBackupFile?.let {
                if (it.exists()) it.delete()
            }
        }
    }

    fun reset() {
        clear()
    }

    /**
     * 获取当前处理进度 (已处理数, 总数)
     */
    fun getProgress(): Pair<Int, Int> {
        val total = totalCount.get().coerceAtLeast(1)
        val processed = processedCount.get().coerceAtMost(total)
        return Pair(processed, total)
    }

    // --- 持久化与恢复支持 ---

    private fun persistToDisk() {
        val file = queueBackupFile ?: return
        try {
            val list = listOfNotNull(currentRunningTask) + queue.toList()
            if (list.isEmpty()) {
                if (file.exists()) file.delete()
            } else {
                val tempFile = File(file.parentFile, "unzip_task_queue_v2.tmp")
                tempFile.writeText(gson.toJson(list))
                tempFile.renameTo(file)
            }
        } catch (e: Exception) {
            XLog.e("UnzipQueueManager persistToDisk 失败", e)
        }
    }

    private fun restoreFromDisk() {
        synchronized(lock) {
            val file = queueBackupFile ?: return
            try {
                if (file.exists()) {
                    val json = file.readText()
                    val type = object : TypeToken<List<UnzipTaskItem>>() {}.type
                    val items: List<UnzipTaskItem>? = gson.fromJson(json, type)
                    if (!items.isNullOrEmpty()) {
                        queue.clear()
                        currentRunningTask = null
                        queue.addAll(items)
                        totalCount.set(items.size)
                        processedCount.set(0)
                        XLog.i("UnzipQueueManager: 从崩溃/重启中成功恢复 ${items.size} 个未完成解压任务")
                    }
                }
            } catch (e: Exception) {
                XLog.e("UnzipQueueManager restoreFromDisk 失败", e)
            }
        }
    }
}
