package github.zerorooot.nap511.platform

/**
 * 平台通用常量定义
 *
 * 集中管理系统级 Notification ID、Channel 等跨层常量，
 * 消除 Worker、Service 与 Activity/UI 之间的双向逆向依赖。
 */
object PlatformConstants {
    /** 离线下载待处理通知通道/通知 ID (解耦 UI Activity 与 Worker) */
    const val NOTIFICATION_ID_OFFLINE_PENDING = 1000
}
