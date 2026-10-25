'use strict'

/**
 * qMusic 音乐抓取服务 · 统一网关
 *
 *   客户端 (Android) ──►  :3000  本网关
 *                          ├── /multi/*  → musiclib-service (:3100, Go + music-lib 多音源)
 *                          └── 其余路径   → NeteaseCloudMusicApi (:3001, 网易云全量接口)
 *
 * 拆两个端口的原因：NeteaseCloudMusicApi 自带完整的 express 应用与静态页面，
 * 直接在其上挂路由需要侵入第三方包；用内网端口 + 反向代理既保持原行为不变，
 * 又能在前面叠加多音源聚合层。
 *
 * 环境变量：
 *   PORT             网关端口，默认 3000
 *   NCM_PORT         网易云服务内网端口，默认 3001
 *   MUSICLIB_URL     多音源服务地址，默认 http://127.0.0.1:3100
 *   MUSICLIB_AUTOSTART=1  由本进程顺带拉起多音源服务（默认不拉起）
 */

const path = require('path')
const http = require('http')
const { spawn } = require('child_process')

const express = require('express')
const { router: multiRouter } = require('./multi-source')
const { router: sodaRouter } = require('./soda-chart')
const { router: sodaDiscoverRouter, warmup: warmupSodaPlaylists } = require('./soda-discover')

const PORT = Number(process.env.PORT || 3000)
const NCM_PORT = Number(process.env.NCM_PORT || 3001)

const NCM_DIR = path.join(__dirname, 'node_modules', 'NeteaseCloudMusicApi')

// ---------- 1. 启动网易云接口服务（内网端口，行为与原来完全一致） ----------
async function startNetease() {
  const generateConfig = require(path.join(NCM_DIR, 'generateConfig'))
  try {
    await generateConfig()
  } catch (e) {
    console.warn('[ncm] 匿名 token 生成失败（通常不影响搜索）：', e.message)
  }
  const { serveNcmApi } = require(path.join(NCM_DIR, 'server'))
  await serveNcmApi({ port: NCM_PORT, checkVersion: false })
}

// ---------- 2. 可选：自动拉起 Go 多音源服务 ----------
function maybeStartMusicLib() {
  if (process.env.MUSICLIB_AUTOSTART !== '1') return
  const fs = require('fs')
  const dir = path.join(__dirname, 'musiclib-service')
  // 跨平台：Windows 下是 qmusic-musiclib.exe，Linux/macOS 容器里是 qmusic-musiclib
  const candidates = ['qmusic-musiclib.exe', 'qmusic-musiclib']
    .map((name) => path.join(dir, name))
    .filter((p) => fs.existsSync(p))
  if (candidates.length === 0) {
    console.warn('[musiclib] 未找到可执行文件，跳过自动启动：', dir)
    return
  }
  try {
    const exe = candidates[0]
    // Linux 容器内 COPY --from 会保留可执行位；兜底显式 chmod，防偶发丢权限
    try { fs.chmodSync(exe, 0o755) } catch (_) { /* Windows 下无意义，忽略 */ }
    const child = spawn(exe, [], { stdio: 'inherit', detached: false, cwd: dir })
    child.on('error', (e) => console.warn('[musiclib] 启动失败：', e.message))
    console.log('[musiclib] 已顺带拉起多音源服务 pid=%s (%s)', child.pid, path.basename(exe))
  } catch (e) {
    console.warn('[musiclib] 启动异常：', e.message)
  }
}

// ---------- 3. 网关 ----------
const app = express()
app.disable('x-powered-by')

app.use('/multi', multiRouter)
// 汽水音乐榜单（直连 api.qishui.com，不依赖 musiclib-service，失败不影响其它接口）
app.use('/soda', sodaRouter)
app.use('/soda', sodaDiscoverRouter)

// 其余请求原样转发给网易云服务
function proxyToNetease(req, res) {
  const options = {
    hostname: '127.0.0.1',
    port: NCM_PORT,
    path: req.originalUrl,
    method: req.method,
    headers: { ...req.headers, host: `127.0.0.1:${NCM_PORT}` },
  }
  const upstream = http.request(options, (up) => {
    const headers = { ...up.headers, 'access-control-allow-origin': '*' }
    res.writeHead(up.statusCode || 502, headers)
    up.pipe(res)
  })
  upstream.on('error', (e) => {
    res.writeHead(502, { 'content-type': 'application/json; charset=utf-8' })
    res.end(JSON.stringify({ ok: false, error: '网易云服务尚未就绪：' + e.message }))
  })
  req.pipe(upstream)
}

app.use((req, res, next) => {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'access-control-allow-origin': '*',
      'access-control-allow-headers': '*',
      'access-control-allow-methods': 'GET,POST,OPTIONS',
    })
    return res.end()
  }
  next()
})

app.use(proxyToNetease)

// ---------- 启动 ----------
async function main() {
  await startNetease()
  maybeStartMusicLib()
  app.listen(PORT, '0.0.0.0', () => {
    console.log('')
    console.log('  qMusic 音乐服务已启动')
    console.log('  ------------------------------------------')
    console.log(`  网关（客户端填这个）  http://<本机IP>:${PORT}/`)
    console.log(`  网易云接口（内网）    http://127.0.0.1:${NCM_PORT}/`)
    console.log(`  多音源接口            http://<本机IP>:${PORT}/multi/sources`)
    console.log('  ------------------------------------------')
    console.log('')
    // 歌单池后台预热：磁盘有缓存时只是增量补几十个，没缓存则慢慢建起来
    warmupSodaPlaylists()
  })
}

main().catch((e) => {
  console.error('启动失败：', e)
  process.exit(1)
})
