package github.zerorooot.nap511.player

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.worker.UnzipQueueManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 视频画面旋转与合成算法单元测试
 * 验证：顺时针/逆时针角度步进归一化、元数据原生角度叠加、复位归零逻辑
 */
class VideoRotationTest {

    @Before
    fun setUp() {
        try {
            XLog.init()
        } catch (_: Throwable) {}
        UnzipQueueManager.clear()
    }

    /**
     * 模拟 MyGSYVideoPlayer 内部的旋转计算状态机
     */
    private class RotationStateSimulator {
        var intrinsicRotation: Int = 0
            private set
        var customRotation: Int = 0
            private set

        fun onIntrinsicRotationReceived(extra: Int) {
            intrinsicRotation = extra
        }

        fun rotate(deltaDegrees: Int) {
            customRotation = (customRotation + deltaDegrees) % 360
            if (customRotation < 0) {
                customRotation += 360
            }
        }

        fun reset() {
            customRotation = 0
        }

        fun resetAll() {
            intrinsicRotation = 0
            customRotation = 0
        }

        fun getEffectiveRotation(): Int {
            val effective = (intrinsicRotation + customRotation) % 360
            return if (effective < 0) effective + 360 else effective
        }
    }

    @Test
    fun testClockwiseRotationSequence() {
        val sim = RotationStateSimulator()
        assertEquals(0, sim.customRotation)
        assertEquals(0, sim.getEffectiveRotation())

        // 顺时针旋转 90° -> 90°
        sim.rotate(90)
        assertEquals(90, sim.customRotation)
        assertEquals(90, sim.getEffectiveRotation())

        // 顺时针旋转 90° -> 180°
        sim.rotate(90)
        assertEquals(180, sim.customRotation)
        assertEquals(180, sim.getEffectiveRotation())

        // 顺时针旋转 90° -> 270°
        sim.rotate(90)
        assertEquals(270, sim.customRotation)
        assertEquals(270, sim.getEffectiveRotation())

        // 顺时针旋转 90° -> 0° (循环一圈)
        sim.rotate(90)
        assertEquals(0, sim.customRotation)
        assertEquals(0, sim.getEffectiveRotation())
    }

    @Test
    fun testCounterClockwiseRotationSequence() {
        val sim = RotationStateSimulator()
        assertEquals(0, sim.customRotation)

        // 逆时针旋转 90° -> 270°
        sim.rotate(-90)
        assertEquals(270, sim.customRotation)
        assertEquals(270, sim.getEffectiveRotation())

        // 逆时针旋转 90° -> 180°
        sim.rotate(-90)
        assertEquals(180, sim.customRotation)
        assertEquals(180, sim.getEffectiveRotation())

        // 逆时针旋转 90° -> 90°
        sim.rotate(-90)
        assertEquals(90, sim.customRotation)
        assertEquals(90, sim.getEffectiveRotation())

        // 逆时针旋转 90° -> 0°
        sim.rotate(-90)
        assertEquals(0, sim.customRotation)
        assertEquals(0, sim.getEffectiveRotation())
    }

    @Test
    fun testResetRotation() {
        val sim = RotationStateSimulator()
        sim.rotate(90)
        sim.rotate(90)
        assertEquals(180, sim.customRotation)

        sim.reset()
        assertEquals(0, sim.customRotation)
        assertEquals(0, sim.getEffectiveRotation())
    }

    @Test
    fun testIntrinsicMetadataRotationHandling() {
        val sim = RotationStateSimulator()
        // 模拟竖屏手机录像，视频自带 90° 旋转元数据
        sim.onIntrinsicRotationReceived(90)
        assertEquals(90, sim.intrinsicRotation)
        assertEquals(0, sim.customRotation)
        // 初始有效角度应为原生 90°
        assertEquals(90, sim.getEffectiveRotation())

        // 用户顺时针旋转 90° -> 有效角度变为 180°
        sim.rotate(90)
        assertEquals(90, sim.customRotation)
        assertEquals(180, sim.getEffectiveRotation())

        // 再次顺时针旋转 90° -> 有效角度变为 270°
        sim.rotate(90)
        assertEquals(180, sim.customRotation)
        assertEquals(270, sim.getEffectiveRotation())

        // 用户重置旋转 -> 自定义归 0，有效角度安全保留原生 90°
        sim.reset()
        assertEquals(0, sim.customRotation)
        assertEquals(90, sim.getEffectiveRotation())
    }

    @Test
    fun testIntrinsicMetadata270Degrees() {
        val sim = RotationStateSimulator()
        sim.onIntrinsicRotationReceived(270)
        assertEquals(270, sim.getEffectiveRotation())

        // 顺时针旋转 90° -> 270 + 90 = 360 = 0°
        sim.rotate(90)
        assertEquals(0, sim.getEffectiveRotation())

        // 逆时针旋转 90° -> 回到 270°
        sim.rotate(-90)
        assertEquals(270, sim.getEffectiveRotation())
    }

    @Test
    fun testResetAllOnVideoReload() {
        val sim = RotationStateSimulator()
        sim.onIntrinsicRotationReceived(90)
        sim.rotate(180)
        assertEquals(270, sim.getEffectiveRotation())

        // 切集或重新装载 setUp() 时复位所有角度
        sim.resetAll()
        assertEquals(0, sim.intrinsicRotation)
        assertEquals(0, sim.customRotation)
        assertEquals(0, sim.getEffectiveRotation())
    }
}
