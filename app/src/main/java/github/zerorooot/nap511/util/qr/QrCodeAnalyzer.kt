package github.zerorooot.nap511.util.qr

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * CameraX 实时相机预览帧二维码解析器
 * 使用 ZXing MultiFormatReader 高效识别 YUV 灰度明度平面
 */
class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.CHARACTER_SET to "UTF-8"
            )
        )
    }

    override fun analyze(imageProxy: ImageProxy) {
        val yPlane = imageProxy.planes.getOrNull(0)
        if (yPlane == null) {
            imageProxy.close()
            return
        }

        try {
            val buffer = yPlane.buffer
            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
            val width = imageProxy.width
            val height = imageProxy.height

            // 基于 Y 通道构建 PlanarYUVLuminanceSource
            val source = PlanarYUVLuminanceSource(
                bytes, width, height,
                0, 0, width, height,
                false
            )
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val result = reader.decodeWithState(binaryBitmap)
            if (!result.text.isNullOrBlank()) {
                onQrCodeScanned(result.text)
            }
        } catch (_: Exception) {
            // 每帧分析未检测到二维码属于正常预期，忽略异常保持流转
        } finally {
            reader.reset()
            imageProxy.close()
        }
    }
}
