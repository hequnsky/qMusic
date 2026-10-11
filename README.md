# qMusic · Android 在线音乐播放器

> 参考早期「汽水音乐」界面风格，基于在线音乐资源抓取服务的 Android 音乐播放器。
> 项目包含：高保真原型、设计文档、开发文档、可运行的 Android 工程、音乐抓取服务与交付说明。

| 当前版本 | 更新日志 | APK 产物名 |
| --- | --- | --- |
| **1.02** | [docs/CHANGELOG.md](docs/CHANGELOG.md) | `qMusic-1.02-release.apk` |

![原型 - 发现页](prototype/screenshots/discover.png)

---

## 版本与打包规则

- `versionName` **每次打包 +0.01**（1.01 → 1.02 → 1.03 …），`versionCode` 每次 +1；
- 版本号统一维护在 [`android/version.properties`](android/version.properties)，
  打包脚本会自动调用 [`android/scripts/bump-version.js`](android/scripts/bump-version.js) 自增；
- APK 文件名格式：`<自定义名字>-<版本号>-<构建类型>.apk`，例如 `qMusic-1.01-release.apk`；
- **一键打包**：

  ```bash
  # Windows
  cd android && build-apk.bat            # 生产包（release，已签名 + R8）
  cd android && build-apk.bat debug      # 调试包

  # macOS / Linux
  cd android && ./build-apk.sh
  ```

  脚本会依次完成：版本号 +0.01 → 编译 → 归档到 `android/dist/` → 打印产物路径。

- 每个版本的变更都记录在 [docs/CHANGELOG.md](docs/CHANGELOG.md)。

---

## 项目亮点

- 🎨 **汽水音乐风格 UI**：浅色发现页 + 深色全屏播放页 + 底部三栏导航 + 歌词视图
- 🎧 **9 个音源聚合**：网易云 / QQ / 酷狗 / 酷我 / 咪咕 / 千千 / 汽水 / Bilibili / Apple Music，
  搜索页可一键切换音源（集成开源项目 [`music-lib`](https://github.com/guohuiyuan/music-lib)）
- 🔓 **加密音源也能放**：汽水音乐、咪咕 Z3D 由服务端解密后以 HTTP Range 回源，支持拖动进度
- 🌐 **在线音乐资源**：自建 Node 音乐抓取服务，统一网关同时提供网易云全量接口与多音源聚合接口
- 📱 **纯 Kotlin + Jetpack Compose** 实现，MVVM 架构
- ▶️ **Media3 ExoPlayer 播放引擎**，支持前台服务、通知栏进度条控制
- ❤️ **本地收藏 / 最近播放**，Room 持久化
- 🎵 **播放列表弹窗**：队列展示、当前高亮、点击切歌
- 🧩 **桌面小组件**：2x2 播放控制（封面/歌名/播放暂停/上一首/下一首）
- 🌙 **多语言 + 深浅色主题**：中英文案随系统切换；「我的」页深色模式开关持久化
- 🔒 **隐私合规**：首次启动隐私政策弹窗（同意后进入），设置页可随时查看
- 🕘 **真实搜索历史**：本地持久化，支持单条删除与一键清空
- ✨ **Android 12+ Splash 启动页**：品牌色 + 音符图标
- 🔁 **播放模式切换**：顺序播放 / 列表循环 / 单曲循环
- ⏱️ **更多菜单**：分享歌曲、定时关闭（15/30/60 分钟）
- 📚 **全部热门歌单**：发现页「更多 ›」查看并进入任意歌单
- 📁 **本地音乐**：MediaStore 扫描本地音频 + 运行时权限申请；无本地歌曲时展示内置演示歌曲试听
- 🌐 **服务器地址可配置**：我的页设置区可切换音乐服务地址（真机填电脑局域网 IP）
- 🎛️ **分类音乐**：每日推荐/热歌榜/新歌速递/华语/欧美歌曲 + 纯音乐/摇滚/民谣歌单
- 💬 **歌曲评论**：播放页查看真实歌曲评论
- 🔥 **实时热搜**：搜索页展示网易云真实热门搜索词，点击即搜
- 💡 **搜索建议**：输入关键词即时联想歌曲，点击直接播放
- 🏆 **排行榜**：发现页直达 63 个真实榜单（飙升/新歌/热歌…）
- ⬇️ **歌曲下载**：播放页一键下载到应用私有目录，我的下载随时听
- 🔐 **网易云登录**：二维码登录，同步关注 / 粉丝 / 获赞
- 🎤 **歌词翻译**：有翻译的歌曲自动显示译文
- 📱 **虚拟导航栏适配**：全屏页面与底部导航不再被系统导航键遮挡
- 🔒 **R8 混淆 + 资源收缩**：Release APK 仅约 2.5MB
- 🔧 **可一键构建 APK**（Gradle + Android SDK）

## 目录结构

```
qMusic/
├── README.md                    # 项目总览（本文件）
├── docs/
│   ├── CHANGELOG.md               # 逐版本更新日志（每个版本必写）
│   ├── 设计文档.md                 # 产品需求 / UI设计 / 视觉规范 / 交互说明
│   ├── 开发文档.md                 # 技术选型 / 系统架构 / 模块设计 / API规格
│   └── 交付说明.md                 # 环境准备 / 构建运行 / 测试 / 部署
├── prototype/
│   ├── index.html                 # 高保真可交互原型（浏览器直接打开）
│   └── screenshots/              # 原型截图（发现/播放/歌词/搜索/我的）
├── server/                       # 音乐服务（Node.js）
│   ├── index.js                   # 统一网关：/multi/* 多音源 + 其余路径代理网易云
│   ├── multi-source.js            # 多音源聚合路由
│   ├── package.json
│   ├── start.bat / start.sh
│   ├── musiclib-service/          # 多音源服务（Go + music-lib）
│   │   ├── main.go
│   │   ├── build.bat / build.sh
│   │   └── third_party/music-lib/ # 内联的 music-lib 源码（AGPL-3.0）
│   └── node_modules/              # npm install 后生成（已忽略）
└── android/                       # Android 工程（Kotlin + Compose + Media3）
    ├── version.properties         # 版本号（打包自动 +0.01）
    ├── build-apk.bat / .sh        # 一键打包
    ├── scripts/bump-version.js    # 版本号自增
    └── app/
```

## 快速开始

```bash
# 1) 启动音乐服务（需要 Node.js 16+；多音源还需 Go 1.24+ 编译一次）
cd server
npm install                    # 安装 Node 依赖（首次）
cd musiclib-service && ./build.sh && cd ..   # 编译多音源服务（首次，可选）
npm start                      # 网关监听 http://localhost:3000
#   ├─ /            网易云全量接口（搜索/歌词/歌单/评论/登录…）
#   └─ /multi/*     多音源聚合接口（QQ/酷狗/酷我/咪咕/汽水/B站…）

# 2) 打开高保真原型
start prototype/index.html        # Windows
open prototype/index.html          # macOS

# 3) 打包 Android APK（需要 JDK17 + Android SDK）
cd android
./build-apk.sh                 # 一键：版本号 +0.01 → 编译 → 归档到 dist/
# 产物：android/dist/qMusic-1.02-release.apk
```

> 没有编译多音源服务也能正常使用：网关会自动降级，客户端只显示网易云一个音源。
>
> Android 模拟器访问宿主机服务请使用 `http://10.0.2.2:3000`；
> 真机调试请将 `app/src/main/res/xml/network_security_config.xml` 中的 `10.0.2.2` 改为电脑局域网 IP。

## 质量保障 · 真机批量功能测试

项目自带一套「以真实用户路径驱动真机」的自动化测试，用来回答「功能是不是真的都正常」。

```bash
cd android
bash scripts/batch-test.sh 10      # 跑 10 轮，默认参数就是 10
```

每一轮完整走一遍：

| 阶段 | 覆盖内容 |
| --- | --- |
| 1 冷启动 | 强杀 → 启动 → 隐私弹窗 → 发现页加载 → 底部导航 → 进程存活 |
| 2 播放 | 进入播放页 → 播放态为 PLAYING → 播放进度确实在推进 |
| 3 封面 | **通知栏是否真的带上了封面大图**（读 `android.largeIcon`，不是看 UI 文案） |
| 4 播放控制 | 暂停 / 继续 / 下一首 / 上一首，每步都回读 `PlaybackState` 校验 |
| 5 歌词 | 歌词页有真实歌词行（非「暂无歌词」） |
| 6 收藏 | 点 ❤️ 后变为「已喜欢」 |
| 7 搜索 | 打开搜索页 → 输入关键词 → 有结果 → **切换音源**后仍正常 |
| 8 我的 | 我的页 → 我的下载 → 点「我喜欢的」**不崩溃**（回归曾经必现的闪退） |
| 9 小组件 | 桌面小组件存在、有封面区与三个按键，**点「下一首 / 播放暂停」真的生效** |
| 10 稳定性 | 全程无崩溃，进程存活 |

**最近一次完整结果（2026-10-11，v1.02）**：10 轮 × 33 条断言 = **330 条全部通过，通过率 100.0%**，单轮耗时 307–354s，总耗时 55m35s。

| 轮次 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 通过 | 33 | 33 | 33 | 33 | 33 | 33 | 33 | 33 | 33 | 33 |
| 失败 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

产物：

- `android/testing/report-rounds.md` —— 逐轮通过数 / 失败项 / 通过率汇总 + 客观判据
- `android/testing/rounds/round-N.log` —— 每轮断言明细
- `android/testing/shots/` —— 关键步骤截图

> 测试脚本刻意采用「bash 调 adb + Node 只解析 uiautomator XML」的分工：
> 取界面树交给 adb，找控件坐标交给 Node，避免依赖任何 GUI 自动化框架。

## 文档索引

| 文档 | 内容 |
| --- | --- |
| [更新日志](docs/CHANGELOG.md) | **每个版本的变更记录**、版本号规则、打包流程 |
| [设计文档](docs/设计文档.md) | 产品定位、需求分析、信息架构、UI/UX 设计、视觉规范、交互流程、原型说明 |
| [开发文档](docs/开发文档.md) | 技术栈、系统架构、模块设计、API 规格、数据模型、构建与测试方案 |
| [交付说明](docs/交付说明.md) | 环境准备、服务端部署、Android 构建运行、真机调试、常见问题、发布清单 |
| [多音源服务说明](server/musiclib-service/README.md) | music-lib 集成方式、支持的音源矩阵、接口定义、编译运行 |

## Git 提交里程碑

1. `docs: 原型与设计/开发/交付文档`
2. `feat(server): 音乐抓取服务`
3. `feat(android): 工程骨架与架构`
4. `feat(android): 网络层与数据层`
5. `feat(android): 播放服务`
6. `feat(android): UI 页面`
7. `build(android): 产出 APK`
8. `docs: 最终交付说明`
9. `feat(android): 歌单详情页 + 歌词滚动`
10. `feat(android): 播放列表弹窗 + Release 签名`
11. `feat(android): R8 瘦身 / 小组件 / 通知进度 / 歌词调优 / 多语言 / 深浅色`
12. `feat(android): 隐私合规 / 搜索历史持久化 / Splash 启动页 / 设置区`
13. `feat(android): 播放模式 / 分享与定时关闭 / 全部热门歌单 / 我的页滚动定位 / 占位提示`
14. `feat(server): 集成 music-lib 多音源服务 + 统一网关`
15. `feat(android): 多音源切换（search 页音源条 + source/token 持久化）`
16. `fix(android): 进度条间距 / 媒体控制封面不切换 / 桌面小组件`
17. `build(android): 版本号自增 + APK 自定义命名 + 一键打包脚本`
18. `docs: CHANGELOG 逐版本说明 + README 版本章节`

---

> ⚠️ 版权说明：本项目仅用于学习与技术交流。在线音乐资源来自第三方平台公开接口，请尊重音乐版权，勿将本项目用于商业用途。