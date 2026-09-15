#  nap511
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/zerorooot/nap511) 
[![Latest Release](https://img.shields.io/github/v/release/zerorooot/nap511?label=Latest%20Release)](https://github.com/zerorooot/nap511/releases)
[![License](https://img.shields.io/github/license/zerorooot/nap511.svg)](https://github.com/zerorooot/nap511/blob/main/LICENSE)


一个Android自用的[115网盘](https://115.com/)客户端，用于[Jetpack Compose](https://developer.android.com/jetpack/compose)练手

# 截图

<table style="text-align: center; vertical-align: middle; width: 1200px; table-layout: fixed;">
  <tr><td colspan="2">
      <a href="./assets/01.jpg"><img src="./assets/01.jpg?raw=true" width="300" alt="Screenshot 001"/></a>
    </td>
    <td colspan="2">
      <a href="./assets/02.jpg"><img src="./assets/02.jpg?raw=true" width="300" alt="Screenshot 002"/></a>
    </td>
    <td colspan="2">
      <a href="./assets/03.jpg"><img src="./assets/03.jpg?raw=true" width="300" alt="Screenshot 003"/></a>
    </td>
  </tr>
  <tr><td colspan="2">
      <a href="./assets/04.jpg"><img src="./assets/04.jpg?raw=true" width="300" alt="Screenshot 004"/></a>
    </td>
    <td colspan="2">
      <a href="./assets/05.jpg"><img src="./assets/05.jpg?raw=true" width="300" alt="Screenshot 005"/></a>
    </td>
    <td colspan="2">
      <a href="./assets/06.jpg"><img src="./assets/06.jpg?raw=true" width="300" alt="Screenshot 006"/></a>
    </td>
  </tr>
  <tr><td colspan="3">
      <a href="./assets/07.jpg"><img src="./assets/07.jpg?raw=true" width="600" alt="Screenshot 007"/></a>
    </td>
    <td colspan="3">
      <a href="./assets/08.jpg"><img src="./assets/08.jpg?raw=true" width="600" alt="Screenshot 008"/></a>
    </td>
  </tr>
</table>


# 功能说明

本工具基于 Jetpack Compose 与 Material 3 打造，提供以下核心功能模块：

1. **登录与身份验证**
    - 支持账号密码登录、Cookie 手动/自动解析登录及主动登出。
    - 内置验证码（Captcha）人机校验辅助机制，解决磁力/视频播放账号校验限制。

2. **网盘文件管理**
    - **基础操作**：剪切/移动、重命名、新建文件夹、移入回收站、文件详情查看（大小、修改时间、PickCode 等）。
    - **批量与选择**：支持多选、全选/取消选择，批量移动或删除。
    - **检索与视图**：文件关键字搜索，支持列表与网格双视图及路径面包屑快速导航。
    - **云端解压**：支持云端压缩包目录预览、加密压缩包解密，以及后台批量云解压任务。
    - **重复文件清理**：支持全盘重复文件检测、分组查看与一键清理。
    - **下载推送**：支持一键推送文件下载链接至远程 Aria2 RPC 服务。

3. **离线下载管理**
    - **多协议支持**：支持磁力链接（magnet）、FTP、ED2K 离线下载，以及上传本地 `.torrent` 种子文件解析下载。
    - **任务管理**：离线任务状态/进度实时查看、按类型（全部/进行中/已完成等）筛选，支持跳转至对应的网盘保存文件夹。
    - **在线预览**：离线视频任务无需全套完成即可直接在线预览播放。
    - **批量操作**：支持单个删除或一键清空全部离线任务。

4. **快捷唤起与系统集成**
    - **链接自动唤起**：支持响应磁力/FTP/ED2K 链接外部唤起并添加离线任务。
    - **系统菜单唤起**：支持通过系统选中文本长按菜单（`ACTION_PROCESS_TEXT`）快捷触发离线下载。
    - **URL Scheme 唤起**：支持标准 URL 唤起（格式：`nap511://command/addTask?param=${encodeURIComponent(text)}`）。
    - **后台极速任务**：支持通过 WorkManager 带有前台通知提示的后台离线下载任务极速队列。

5. **多媒体在线预览与播放**
    - **视频播放器（GSYVideoPlayer + ExoPlayer2）**：
      - 支持在线流式播放、原画/高清解析切换，以及解析失败自动重试机制。
      - 支持挂载/加载在线字幕，自定义字幕样式（字号、颜色及 Default / Sans / Serif / Monospace 等字体类型）。
      - 支持倍速播放、画面比例旋转调整、后台播放及横竖屏自动旋转。
    - **音频播放器**：支持全局 Mini 播放条与全屏音乐播放器，配合后台 `AudioService` 保持后台流畅播放。
    - **图片查看器**：支持照片预览与手势缩放，支持高清模式（HD Preview）及基于文件数自动切换预览模式。
    - **文本阅读器**：支持在线预览读取文本文件，并可自定义最大文件加载上限。

6. **最近删除（回收站）**
    - 查看已删除文件列表，支持单文件/批量还原与彻底删除（可配置独立二级安全密码防护）。

7. **系统设置与个性化**
    - **个性化 UI**：Material 3 响应式设计，支持动态配色（Dynamic Color）、跟随系统/亮/暗色主题，支持悬浮按钮（FAB）位置调整及大屏/宽屏（Expanded Screen）自适应。
    - **请求与缓存偏好**：可自定义单次文件请求数量上限、目录预加载策略及默认离线保存位置。
    - **配置备份与恢复**：支持将所有应用配置一键导出为 JSON 文件或导入恢复，支持一键重置为默认设置。
    - **日志中心**：内置日志系统，可随时在线查看运行日志与排查故障。

> **不支持功能**：
> - 文件的本地直接上传与下载（下载推荐推送至 Aria2）
> - 两步验证（2FA）
> - 安全密钥相关敏感操作

# 下载

https://github.com/zerorooot/nap511/releases