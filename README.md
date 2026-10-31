# 谷歌生草机 Android

桌面版 [谷歌生草机 4](README.desktop.md)（PyQt5，作者 @MC着火的冰块）的 **Android 原生移植版**。

把一段话经过多轮小语种翻译再翻回中文，让机器翻译的失真效果以可复制、可朗读、可下载音频的形式呈现。

> **直接下载安装**：进入本仓库右侧 **Releases**，下载最新 `app-release.apk` 安装即可，无需 root。
> 桌面版源码仍完整保留在仓库中（`src/`、`requirements.txt`、`README.desktop.md`）。

---

## 与桌面版的关系

| | 桌面版 | Android 移植版 |
|---|---|---|
| 界面 | PyQt5 + Fluent Widgets | Kotlin + Jetpack Compose（Material 3） |
| 翻译 | aiogoogletrans（多协程） | OkHttp + 协程并发，gtx 端点 / 可选 Cloud API Key |
| Google IP 检查 | 下载 `GoogleTranslateIpCheck` 二进制 + `sudo` 写 hosts | **内置两阶段扫描器 + DNS 注入**（无需 root，功能等价） |
| 音频 | gTTS 存本地 mp3 | Google 在线 TTS：完整 MP3 + 分段 MP3 压缩包，存到 `Download` |
| 朗读 | 无 | 系统 TTS（优先 Google TTS 引擎） |
| 历史 | 无 | 自动保存、搜索、收藏、导出 |

## 功能

- **多轮生草**：翻译次数、单次超时、重试次数、并发线程、请求间隔、最终语言全部可调；随机 / 固定语种顺序可选；设置自动持久化
- **全语种池**：Google 翻译支持的全部语种（110+），支持搜索、全选、清空、只看常用
- **切句**：自定义分隔符 + 换行切分，逐行一一对照，超长文本用 POST 提交不受 URL 长度限制
- **复制**：每行可复制译文，另有「复制原文 / 复制译文 / 复制对照」三个全局按钮，支持系统分享与导出 txt 到 Download
- **音频下载**：Google 在线 TTS 合成（音色与网页版一致），同时产出
  - 一个完整 MP3
  - 一个 zip（内含逐行分段 MP3）
  
  保存在手机存储 `Download/`（Android 10+ 走 MediaStore，不需要存储权限）
- **朗读**：系统 TTS，引擎优先级可选（Google TTS 优先 → 系统引擎），语速 / 音调可调，支持逐行朗读与整段朗读
- **IP 检查（关键功能）**：
  - 阶段 1：并发对 Google 边缘网段取样 IP 做 TCP + TLS(SNI) 握手，按延迟排序
  - 阶段 2：对靠前 IP 发真实翻译请求校验（必须 200 且返回合法 JSON），排除「能握手但返回 404 / 证书不匹配」的假可用 IP
  - 扫描结果直接作为 OkHttp 的 DNS 解析结果注入，翻译与 TTS 全走最优 IP —— **不需要 root、不改 hosts**
  - 支持手动添加 IP、复测已保存 IP、网络诊断、日志复制
  - 额外提供「写入系统 hosts」实验开关（仅 root 设备，供其它 App 共用，可一键还原）
- **界面**：Material 3，跟随系统 / 浅色 / 深色 / **纯黑 OLED**（全容器色压到 #000000），动态取色（Material You，Android 12+），字体大小可调
- **保活**：前台服务（通知栏进度）+ 保活引导页（通知权限、忽略电池优化、厂商自启动管理、最近任务锁定、无障碍服务提权），首次启动弹窗引导
- **历史记录**：完成自动保存，支持搜索、收藏、删除、复制、导出（最多 300 条）
- **数据**：设置 JSON 导出/导入、历史导出、崩溃日志查看与导出

## 安装与使用

1. Releases 下载 `app-release.apk` 安装（允许「安装未知来源应用」）
2. 首次启动按提示完成保活设置（可选，但长任务建议做）
3. 国内网络：先到 **IP 检查** → **开始扫描**（约 10~40 秒），扫到可用 IP 会自动启用
4. 回到 **生草主页**：粘贴文本 → **切句** → **开始生草**
5. 结果可逐行复制 / 朗读，或 **导出 txt** / **音频下载**

### 常见问题

- **全部行失败 / HTTP 302、403**：IP 失效或被风控 → 回 IP 检查重新扫描，或降低并发（设置里改）。
- **扫描不到 IP**：调大取样数量（IP 检查页），或换成移动数据再试。
- **音频下载失败**：TTS 同样走 Google 接口，需要可用 IP；失败时先确认 IP 正常。
- **后台被杀**：按「保活引导」逐项设置；长任务建议插电并降低并发。

## 自行编译

```bash
cd android
./gradlew assembleRelease     # 需要 JDK 17 与 Android SDK 35
```

签名密钥在 `android/keystore/release.jks`（密码 `grassmachine`，开源自签，仅用于让各版本可互相覆盖安装）。

CI：`.github/workflows/android.yml`，推送到 `main`（改动 `android/**`）或打 `v*` 标签会自动构建 APK 并发布到 Releases。

## 第三方与致谢

- 上游桌面版：<https://github.com/zhdbk3/GoogleGrassGrowingMachine>（作者 @MC着火的冰块）
- IP 检查思路：<https://github.com/Ponderfly/GoogleTranslateIpCheck>
- 翻译与 TTS 使用 Google 翻译公开接口

## 免责声明

仅用于语言学习与娱乐。翻译内容来自 Google 翻译公开接口，请勿用于违反当地法律法规或平台条款的用途。软件按「现状」提供，不附带任何担保。桌面版原始许可证见 [LICENSE](LICENSE)。
