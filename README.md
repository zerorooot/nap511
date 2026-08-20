#  nap511
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/zerorooot/nap511) 
[![Latest Release](https://img.shields.io/github/v/release/zerorooot/nap511?label=Latest%20Release)](https://github.com/zerorooot/nap511/releases)
[![License](https://img.shields.io/github/license/zerorooot/nap511.svg)](https://github.com/zerorooot/nap511/blob/main/LICENSE)


一个Android自用的[115网盘](https://115.com/)客户端，用于[Jetpack Compose](https://developer.android.com/jetpack/compose)练手

# 截图

<table>
  <tr style="text-align: center; vertical-align: middle;">
    <td><a href="./assets/01.jpg"><img src="./assets/01.jpg?raw=true" width="300" alt="Screenshot 001"/></a></td>
    <td><a href="./assets/02.jpg"><img src="./assets/02.jpg?raw=true" width="300" alt="Screenshot 002"/></a></td>
    <td><a href="./assets/03.jpg"><img src="./assets/03.jpg?raw=true" width="300" alt="Screenshot 003"/></a></td>
  </tr>
  <tr style="text-align: center; vertical-align: middle;">
    <td><a href="./assets/04.jpg"><img src="./assets/04.jpg?raw=true" width="300" alt="Screenshot 004"/></a></td>
    <td><a href="./assets/05.jpg"><img src="./assets/05.jpg?raw=true" width="300" alt="Screenshot 005"/></a></td>
    <td><a href="./assets/06.jpg"><img src="./assets/06.jpg?raw=true" width="300" alt="Screenshot 006"/></a></td>
  </tr>
</table>


# 功能说明

本工具提供以下核心功能模块：

1. **登录模块**
    - 支持网页端账号密码登录、Cookie 登录，以及主动登出。

2. **网盘文件管理**
    - 基础操作：剪切、删除、重命名、新建文件夹
    - 批量操作：多选
    - 其他：回收站、获取下载链接、文件搜索、在线解压

3. **离线任务管理**
    - 查看离线列表，支持跳转至对应网盘文件夹
    - 对离线视频文件可在线查看
    - 支持单个删除及清空全部离线任务

4. **文件预览**
    - 支持小文本、音频、照片、视频等常见格式的在线查看

5. **离线下载方式**
    - 支持磁力链接离线下载
    - 支持种子文件离线下载

6. **自定义设置**
    - 可调整单次文件请求数量
    - 可设置默认离线保存位置
    - 等等

7. **快捷跳转与唤起**
    - 磁力链接自动唤起
    - 支持 URL Scheme 唤起（格式：`nap511://command/addTask?param=${encodeURIComponent(text)}`）

> **不支持功能**：
> - 文件的上传与下载
> - 两步验证（2FA）
> - 安全密钥相关操作

# 下载

https://github.com/zerorooot/nap511/releases