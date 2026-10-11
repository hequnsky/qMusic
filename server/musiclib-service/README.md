# musiclib-service · 多音源聚合服务

把开源项目 [`guohuiyuan/music-lib`](https://github.com/guohuiyuan/music-lib)（Go，AGPL-3.0）
封装成一个 HTTP 服务，为 qMusic 提供**跨平台的搜索 / 播放直链 / 歌词**能力。

## 支持的音源

| 音源 id | 名称 | 搜索 | 播放 | 歌词 | 备注 |
| --- | --- | --- | --- | --- | --- |
| `netease` | 网易云音乐 | ✅ | ✅ | ✅ | 客户端默认音源，仍走 NeteaseCloudMusicApi（功能更全） |
| `qq` | QQ 音乐 | ✅ | ✅ | ✅ | 支持无损 |
| `kugou` | 酷狗音乐 | ✅ | ✅ | ✅ | |
| `kuwo` | 酷我音乐 | ✅ | ✅ | ✅ | |
| `migu` | 咪咕音乐 | ✅ | ✅ | ✅ | Z3D 加密流由本服务解密后回源 |
| `qianqian` | 千千音乐 | ✅ | ✅ | ✅ | |
| `soda` | 汽水音乐 | ✅ | ✅ | ✅ | 音频加密，由本服务解密后回源 |
| `bilibili` | Bilibili | ✅ | ✅ | ✅ | |
| `apple` | Apple Music | ✅ | ⚠️ | ✅ | 仅 preview 音源 |

> 「回源」= 客户端播放地址指向本服务的 `/stream`，由本服务下载（必要时解密）
> 后以支持 HTTP Range 的方式转发，因此可以边下边播、拖动进度。

## 目录

```
musiclib-service/
├── main.go                 # HTTP 服务（搜索/直链/歌词/回源）
├── go.mod / go.sum
├── build.bat / build.sh    # 一键编译
├── qmusic-musiclib.exe     # 编译产物（Windows）
└── third_party/music-lib/  # 内联的 music-lib 源码（AGPL-3.0，见其 LICENSE）
```

`third_party/music-lib` 是从上游 `main` 分支内联的源码，import 路径已改写为本模块
路径（`qmusic-musiclib/third_party/music-lib/...`），因此**编译不需要访问 GitHub**，
只需要能访问 Go 模块代理（默认 `https://goproxy.cn`）。

## 编译

需要 Go 1.24+（上游 `golang.org/x/text` 的依赖链要求较新的工具链）。

```bash
# Windows
build.bat

# macOS / Linux
./build.sh

# 手动
go env -w GOPROXY=https://goproxy.cn,direct
go build -o qmusic-musiclib.exe .
```

## 运行

```bash
# 默认监听 0.0.0.0:3100，缓存目录 %TEMP%/qmusic-musiclib-cache
./qmusic-musiclib.exe

# 可配置
MUSICLIB_PORT=3100 MUSICLIB_CACHE=D:/cache ./qmusic-musiclib.exe
```

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/health` | 健康检查 + 版本 |
| GET | `/sources` | 音源列表 |
| GET | `/search?source=qq&keyword=周杰伦&limit=30` | 跨平台搜索 |
| GET | `/song/url?s=<token>` | 取播放地址（直链或回源地址） |
| GET | `/stream?s=<token>` | 音频回源（支持 Range） |
| GET | `/lyric?s=<token>` | 歌词（LRC） |
| GET | `/playlist/search?source=qq&keyword=xxx` | 歌单搜索 |
| GET | `/playlist/songs?source=qq&id=xxx` | 歌单歌曲 |

`token` 是搜索接口返回的 `songs[].token`，里面 base64 编码了 music-lib 的完整
`Song` 结构，保证跨平台解析所需的元数据不丢失。客户端只要原样回传即可。

## 与网关的关系

Android 客户端**不直连**本服务，而是访问 Node 网关的 `/multi/*`：

```
Android ──► Node 网关 :3000 ──► /multi/search ──► 本服务 :3100
```

网关会把本服务生成的回源地址改写成对客户端可达的
`http://<网关地址>/multi/stream?s=...`。

## 许可证

- 本服务代码：随 qMusic 项目（MIT）
- `third_party/music-lib`：**AGPL-3.0**，版权归上游作者所有，详见该目录下 `LICENSE`。
  如果你要分发本服务，需要一并遵守 AGPL-3.0 的条款。
