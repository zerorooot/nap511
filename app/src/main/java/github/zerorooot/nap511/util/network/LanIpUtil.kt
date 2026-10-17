package github.zerorooot.nap511.util.network

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 局域网网卡 IP 信息封装
 * @param interfaceName 网卡物理/虚拟名称（如 wlan0, ap0, eth0 等）
 * @param displayName 面向用户的友好显示标签
 * @param ip 该接口绑定的 IPv4 地址
 */
data class NetworkIpInfo(
    val interfaceName: String,
    val displayName: String,
    val ip: String
)

/**
 * 局域网网络接口与有效 IP 探测工具
 */
object LanIpUtil {

    /**
     * 获取当前设备所有可用于局域网通信的有效 IPv4 地址列表，
     * 自动过滤回环、虚拟接口、隧道接口（tun、ppp、rmnet等），并按网络类型优先级排序
     */
    fun getAvailableIps(): List<NetworkIpInfo> {
        val result = mutableListOf<NetworkIpInfo>()
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        for (netIf in interfaces) {
            try {
                if (!netIf.isUp || netIf.isLoopback || netIf.isVirtual) continue
                val name = netIf.name.lowercase()
                // 过滤虚拟通道和蜂窝网络移动数据接口
                if (name.startsWith("tun") ||
                    name.startsWith("ppp") ||
                    name.startsWith("p2p") ||
                    name.startsWith("rmnet") ||
                    name.startsWith("dummy")
                ) continue

                for (addr in netIf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress ?: continue
                        val label = when {
                            name.startsWith("wlan") -> "Wi-Fi (${netIf.name})"
                            name.startsWith("ap") || name.startsWith("softap") -> "热点 (${netIf.name})"
                            name.startsWith("eth") -> "以太网 (${netIf.name})"
                            name.startsWith("rndis") -> "USB 共享 (${netIf.name})"
                            else -> "局域网 (${netIf.name})"
                        }
                        result.add(NetworkIpInfo(netIf.name, label, hostAddress))
                    }
                }
            } catch (_: Exception) {
                // 忽略单个网卡查询异常，继续处理其余网卡
            }
        }

        // 排序优先级：Wi-Fi (0) > 手机热点 (1) > 以太网 (2) > USB 共享 (3) > 其他 (4)
        return result.sortedBy { info ->
            val name = info.interfaceName.lowercase()
            when {
                name.startsWith("wlan") -> 0
                name.startsWith("ap") || name.startsWith("softap") -> 1
                name.startsWith("eth") -> 2
                name.startsWith("rndis") -> 3
                else -> 4
            }
        }
    }
}
