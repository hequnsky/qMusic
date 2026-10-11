# qMusic 更新日志（CHANGELOG）

本文件记录**每一个版本**的变更。规则如下：

| 项 | 规则 |
| --- | --- |
| `versionName` | 展示版本号，**每次打包 +0.01**（1.01 → 1.02 → 1.03 …） |
| `versionCode` | 应用市场的整数版本号，**每次打包 +1** |
| APK 文件名 | `<自定义名字>-<versionName>-<buildType>.apk`，例如 `qMusic-1.01-release.apk` |
| 配置文件 | [`android/version.properties`](../android/version.properties) |
| 自动递增 | `android/scripts/bump-version.js`（打包脚本会自动调用） |
| 一键打包 | `android/build-apk.bat`（Windows）/ `android/build-apk.sh`（macOS/Linux） |

> 打包流程会**先自增版本号再编译**，所以每个 APK 的版本号天然唯一，
> 装到手机上可以直接用「关于」里的版本号对上是哪一次构建。

---

## [1.02] — 2026-10-11

> 本轮为**真机批量测试**（10 轮 × 33 条断言，330/330 通过）驱动出的修复版本。
> 测试脚本：`android/scripts/batch-test.sh`，报告：`android/testing/report-rounds.md`。

### 🐛 修复

- **【严重】「我的」页点「我喜欢的 / 最近播放」卡片直接闪退**
  - 现象：进入「我的」页后点击这两个入口卡片，应用必崩，退回桌面。
  - 根因：`MineScreen` 里「我喜欢的」和「最近播放」是同一个 `LazyColumn` 下的
    两个 `itemsIndexed`，却共用了 `"$index-$songId"` 这套 key。
    只要同一首歌**同时出现在收藏和播放历史**里（几乎必然发生），
    就会产生两个相同的 key `0-5257138`，Compose 直接抛
    `IllegalArgumentException: Key "…" was already used`。
  - 修复：两段列表分别加上互不冲突的前缀（`fav-` / `his-`）。
  - 该 bug 属 Compose 的确定性崩溃，**冷启动后必现**，已回归验证。

- **封面全链路缺失（播放页唱片 / 通知栏 / 系统媒体控制中心 / 桌面小组件都只有占位图）**
  - 现象：默认音源（网易云）下播放任何歌曲，封面位置始终是灰色音符占位图；
    用户反馈的「媒体控制切歌图片不切换」实际是「图片压根没加载出来」。
  - 根因：网易云的 `search` / `search/suggest` 接口返回的歌曲只有
    `album.picId`（数字图片 ID），**不返回 `album.picUrl`**，
    于是 `Song.coverUrl` 恒为 `null`，MediaSession / 通知 / 小组件都拿不到图。
    （歌单详情、排行榜接口是有 `picUrl` 的，所以只有「搜索进来的歌」有问题。）
  - 修复：新增 `MusicRepository.enrichCovers()` —— 当结果里存在缺失封面的歌曲时，
    批量调用 `song/detail` 拿回 `al.picUrl` 再回填；
    全部已有封面时不产生任何额外请求，失败时静默降级不影响搜索本身。
    同时覆盖「搜索建议」下拉里的封面。
  - 验证方式：读取通知栏 `android.largeIcon` 是否非空，作为封面真正加载成功的客观判据。

### ✅ 测试

- 新增 `android/scripts/batch-test.sh`：以真实用户路径驱动真机，
  单轮覆盖 **冷启动 → 发现页 → 播放 → 进度推进 → 封面 → 暂停/继续 →
  上/下一首 → 歌词 → 收藏 → 搜索 → 音源切换 → 我的页 → 收藏列表 → 我的下载 →
  桌面小组件（含按钮联动）→ 稳定性**，并自动输出通过率报告。
- 新增 `android/scripts/env.sh`：adb / uiautomator 助手与断言库。
- 新增 `android/scripts/ui.js` 增强：按文案/描述定位控件并返回可点击坐标。

### 📊 回归结果

真机 PJA110 连续 **10 轮 × 33 条断言 = 330 条全部通过，0 失败，通过率 100.0%**
（单轮 307–354s，总计 55m35s）。本版两项修复均被回归覆盖：

| 修复项 | 覆盖阶段 | 结果 |
| --- | --- | --- |
| 切换歌曲后封面不更新 | 阶段 3 + 4（每次换曲后重新读 `android.largeIcon`） | ✅ |
| 「我喜欢的」列表闪退 | 阶段 8（连续 10 轮点入均未崩溃） | ✅ |
| 桌面小组件 | 阶段 9（封面区 + 上一首/下一首/播放暂停三键联动） | ✅ |

报告：[`android/testing/report-rounds.md`](../android/testing/report-rounds.md)

---

## [1.01] — 2026-10-11

### ✨ 新增：多音源聚合（集成 music-lib 及其他开源音乐库）

- **集成 [guohuiyuan/music-lib](https://github.com/guohuiyuan/music-lib)**（Go，AGPL-3.0）：
  新增 `server/musiclib-service`，把该库统一封装成 HTTP 服务，支持
  **网易云 / QQ 音乐 / 酷狗 / 酷我 / 咪咕 / 千千 / 汽水音乐 / Bilibili / Apple Music**
  共 9 个平台的搜索、播放直链与歌词。
  - 直链平台直接返回 CDN 地址（最省流量）；
  - 汽水音乐、咪咕 Z3D 等加密音源由服务端下载并解密后，以
    **支持 HTTP Range 的方式回源**，可边下边播、可拖动进度；
  - 搜索结果附带 `token`（完整歌曲元数据的 base64 载荷），
    客户端原样回传即可，跨平台解析所需的元数据零丢失。
- **保留并整合 [NeteaseCloudMusicApi](https://github.com/Binaryify/NeteaseCloudMusicApi)**：
  仍是默认音源，评论 / 排行榜 / 歌词翻译 / 二维码登录等功能完全不受影响。
- **新增统一网关 `server/index.js`**：对外只暴露一个端口
  （`:3000`），`/multi/*` 走多音源聚合层，其余路径反向代理到内网的网易云服务（`:3001`）。
  多音源服务不在线时自动降级，客户端**感觉不到**任何异常。
- **Android 端音源切换**：搜索页新增音源横滑切换条（网易云 / QQ / 酷狗 / 酷我 /
  咪咕 / 千千 / 汽水 / B站 / Apple），选择结果本地持久化；
  服务端多音源不可用时切换条自动隐藏，界面与旧版完全一致。
- 收藏 / 最近播放新增 `source`、`token` 两列（Room v1→v2 迁移），
  保证多音源歌曲从「我的」里重新播放时仍能正确解析地址。

### 🐛 修复

- **播放页进度条与播放按钮离得太近**
  在进度条与播放控制行之间加入 16dp 呼吸间距，避免误触与视觉拥挤。
- **媒体控制（通知栏 / 锁屏）切歌后封面不切换**
  根因：`lastArtworkBitmap` 缓存没有随歌曲切换作废，新歌会复用上一首的封面。
  修复：缓存与 `song.id` 绑定（`artworkSongId`），切歌立即作废；
  异步加载增加竞态保护（加载期间用户又切歌则丢弃过期结果）；
  `MediaSession` 元数据同步补齐标题 / 歌手 / 专辑 / 封面。
- **桌面小组件问题**
  - 布局重做：由「挤在一行的控件」改为**上排封面+歌名、下排三个控制键**，
    适配 2×2 单元格，不再出现内容被裁切；
  - 切歌封面不更新：每次更新先显式重置为默认图，再异步补新封面，并带竞态保护；
  - 未在播放时点播放/上一首/下一首**没反应**：改为直接打开 App；
  - 按钮点击无反馈：改用独立 ripple drawable（避免 RemoteViews 解析主题属性失败）；
  - 补充 `onEnabled` 刷新与 `previewLayout`（API 31+ 小组件预览）。

### 🔧 工程化

- 新增 `android/version.properties` + `android/scripts/bump-version.js`：
  版本号每次打包 +0.01、versionCode +1。
- APK 产物自动命名：`qMusic-1.01-release.apk` / `qMusic-1.01-debug.apk`。
- 新增一键打包脚本 `android/build-apk.bat` / `build-apk.sh`
  （自增版本 → 编译 → 归档到 `android/dist/`）。
- 新增 `server/musiclib-service/build.bat` / `build.sh` 编译多音源服务。
- 本文件（`docs/CHANGELOG.md`）与 README 的版本章节。

---

## [1.00] — 2026-10-09

首个交付版本。

- 高保真原型（`prototype/index.html`）+ 设计 / 开发 / 交付文档
- Node 音乐抓取服务（基于 NeteaseCloudMusicApi）
- Android 工程：Kotlin + Jetpack Compose + Media3 ExoPlayer，MVVM
- 汽水音乐风格 UI：发现页 / 全屏播放页 / 歌词视图 / 底部三栏导航
- 搜索、热搜、搜索建议、排行榜（63 榜）、歌单详情、分类音乐、歌曲评论
- 收藏 / 最近播放（Room）、播放列表弹窗、播放模式切换、定时关闭、分享
- 本地音乐（MediaStore 扫描）、歌曲下载、网易云二维码登录、歌词翻译
- 桌面小组件（2×2）、通知栏进度控制、前台播放服务
- 隐私合规弹窗、深浅色主题、中英文案、Android 12+ Splash
- R8 混淆 + 资源收缩，Release APK 约 2.5MB

---

## 版本历史一览

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| 1.02 | 2026-10-11 | 修复「我的」页闪退（LazyColumn 重复 key）；修复搜索歌曲封面全链路缺失（通知栏/锁屏/小组件）；新增真机 10 轮批量测试脚本（330/330 通过） |
| 1.01 | 2026-10-11 | 集成 music-lib 多音源（9 平台）；修复进度条间距 / 媒体封面不切换 / 桌面小组件；版本号与打包命名规范化 |
| 1.00 | 2026-10-09 | 首个交付版本（原型 + 文档 + 服务 + Android 工程 + APK） |
