package github.zerorooot.nap511.util

import androidx.media3.common.PlaybackException.CUSTOM_ERROR_CODE_BASE
import androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
import androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
import androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR
import androidx.media3.common.PlaybackException.ERROR_CODE_DRM_UNSPECIFIED
import androidx.media3.common.PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
import androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
import androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
import androidx.media3.common.PlaybackException.ERROR_CODE_REMOTE_ERROR
import androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT
import androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED

object VideoErrorMapper {
    val playbackErrorMessageMap: Map<Int, String> = mapOf(
        // 基础与通用错误
        ERROR_CODE_UNSPECIFIED to "发生未知错误",
        ERROR_CODE_REMOTE_ERROR to "服务器开小差了，请稍后再试",
        ERROR_CODE_BEHIND_LIVE_WINDOW to "当前直播已过期或进度太落后",
        ERROR_CODE_TIMEOUT to "操作超时，请检查网络",
        ERROR_CODE_FAILED_RUNTIME_CHECK to "系统运行环境异常",
        // IO 与网络错误 (最常见的用户网络问题)
        ERROR_CODE_IO_UNSPECIFIED to "网络或文件读取发生未知错误",
        ERROR_CODE_IO_NETWORK_CONNECTION_FAILED to "网络连接失败，请检查网络设置",
        ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT to "网络连接超时，请重试",
        ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE to "播放链接无效（服务器返回数据类型错误）",
        ERROR_CODE_IO_BAD_HTTP_STATUS to "服务器响应异常（视频可能已下架）",
        ERROR_CODE_IO_FILE_NOT_FOUND to "找不到该视频文件",
        ERROR_CODE_IO_NO_PERMISSION to "应用没有网络或文件读取权限",
        ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED to "安全限制，不允许使用非加密的 HTTP 链接",
        ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE to "视频数据读取出错",

        // 解析错误 (文件格式问题)
        ERROR_CODE_PARSING_CONTAINER_MALFORMED to "视频文件已损坏",
        ERROR_CODE_PARSING_MANIFEST_MALFORMED to "播放列表文件已损坏，可能需要验证(高级设置->视频播放验证)",
        ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED to "不支持该视频文件格式",
        ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED to "不支持该播放列表格式",

        // 解码与播放错误 (设备性能或兼容性问题)
        ERROR_CODE_DECODER_INIT_FAILED to "视频解码器初始化失败",
        ERROR_CODE_DECODER_QUERY_FAILED to "当前设备找不到合适的视频解码器",
        ERROR_CODE_DECODING_FAILED to "视频解码失败，无法播放",
        ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES to "视频规格太高，当前设备性能不足以播放",
        ERROR_CODE_DECODING_FORMAT_UNSUPPORTED to "当前设备不支持这种视频编码格式",
        ERROR_CODE_AUDIO_TRACK_INIT_FAILED to "音频播放初始化失败",
        ERROR_CODE_AUDIO_TRACK_WRITE_FAILED to "音频数据输出失败",

        // DRM (数字版权管理) 错误
        ERROR_CODE_DRM_UNSPECIFIED to "版权保护模块发生未知错误",
        ERROR_CODE_DRM_SCHEME_UNSUPPORTED to "当前设备不支持该视频的版权保护格式",
        ERROR_CODE_DRM_PROVISIONING_FAILED to "获取数字版权证书失败",
        ERROR_CODE_DRM_CONTENT_ERROR to "受版权保护的视频内容解密失败",
        ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED to "获取视频播放许可证失败",
        ERROR_CODE_DRM_DISALLOWED_OPERATION to "因版权限制，不允许此操作",
        ERROR_CODE_DRM_SYSTEM_ERROR to "设备数字版权系统底层出错",
        ERROR_CODE_DRM_DEVICE_REVOKED to "当前设备的播放权限已被吊销",
        ERROR_CODE_DRM_LICENSE_EXPIRED to "该视频的播放许可证已过期",

        // 自定义错误
        CUSTOM_ERROR_CODE_BASE to "发生自定义系统错误"
    )

    fun getErrorMessage(code: Int): String {
        return playbackErrorMessageMap.getOrDefault(code, "").ifEmpty { "发生未记录的错误 (错误码: $code)" }
    }
}
