#!/usr/bin/env node
/**
 * qMusic 服务端一键自检
 *
 * 用法：node scripts/server-health.mjs
 *
 * 依次检查：本地网关(3300) / 多音源(3100) / 线上隧道域名，
 * 并对每个失败项给出「可能原因 + 修复命令」。
 */

import { readFileSync } from 'node:fs'
import { execSync } from 'node:child_process'

const GATEWAY = process.env.QM_GATEWAY || 'http://127.0.0.1:3300'
const MUSICLIB = process.env.QM_MUSICLIB || 'http://127.0.0.1:3100'

function readOnlineUrl() {
  // 优先读仓库根 server-address.json（这才是真正下发给 App 的地址）
  try {
    const cfg = JSON.parse(readFileSync('server-address.json', 'utf8'))
    if (typeof cfg.online === 'string' && cfg.online.startsWith('http')) return cfg.online
  } catch {
    /* 回退到 ServerConfig.kt */
  }
  try {
    const src = readFileSync('android/app/src/main/java/com/qmusic/data/local/ServerConfig.kt', 'utf8')
    const m = src.match(/const val ONLINE_BASE_URL\s*=\s*"([^"]+)"/)
    return m ? m[1] : null
  } catch {
    return null
  }
}

/** 拼接基址与路径，避免基址尾斜杠导致出现 //path 被网关判 404 */
function joinUrl(base, path) {
  return base.replace(/\/+$/, '') + '/' + path.replace(/^\/+/, '')
}

async function probe(name, url, opts = {}) {
  const t0 = Date.now()
  try {
    const r = await fetch(url, { signal: AbortSignal.timeout(opts.timeout || 12000) })
    const text = opts.expect ? await r.text() : ''
    const ok = r.ok && (!opts.expect || opts.expect(text))
    return { name, ok, status: r.status, ms: Date.now() - t0, detail: text.slice(0, 100) }
  } catch (e) {
    return { name, ok: false, status: '-', ms: Date.now() - t0, error: e.message }
  }
}

function listeningPorts() {
  try {
    const out = execSync('netstat -ano -p tcp', { encoding: 'utf8' })
    const ports = new Set()
    for (const line of out.split('\n')) {
      const m = line.match(/:(\d+)\s+\S+\s+LISTENING/)
      if (m) ports.add(Number(m[1]))
    }
    return ports
  } catch {
    return new Set()
  }
}

const online = readOnlineUrl()
const ports = listeningPorts()

const results = []
results.push(await probe('网关首页', GATEWAY + '/', { text: true }))
results.push(await probe('推荐接口', GATEWAY + '/personalized?limit=1', { expect: (t) => t.includes('"code":200') }))
results.push(await probe('搜索接口', GATEWAY + '/search?keywords=test&limit=1', { expect: (t) => t.includes('"result"') }))
results.push(await probe('多音源', GATEWAY + '/multi/sources', { expect: (t) => t.includes('"available":true') }))
if (online) {
  results.push(await probe('线上隧道', joinUrl(online, 'multi/sources'), { expect: (t) => t.includes('"available":true'), timeout: 20000 }))
}

// 局域网地址（手机同 WiFi 直连，隧道挂掉时的备用入口）
const lanMatch = (() => {
  try {
    return JSON.parse(readFileSync('server-address.json', 'utf8')).lan || null
  } catch {
    return null
  }
})()
if (lanMatch && lanMatch !== online) {
  results.push(await probe('局域网入口', joinUrl(lanMatch, 'multi/sources'), { expect: (t) => t.includes('"available":true') }))
}

console.log('\n=== qMusic 服务端自检 ===\n')
for (const r of results) {
  const mark = r.ok ? 'OK  ' : 'FAIL'
  const extra = r.error ? r.error : `HTTP ${r.status} ${r.ms}ms`
  console.log(`[${mark}] ${r.name.padEnd(10)} ${extra}`)
}

console.log('\n--- 端口监听 ---')
for (const [port, label] of [[3300, '网关'], [3301, '网易云上游'], [3100, '多音源'], [3000, '（旧网关位，易被其它项目占用）'], [3001, '（旧上游位）']]) {
  console.log(`${ports.has(port) ? '监听中' : '未监听'}  :${port}  ${label}`)
}

console.log('\n--- 后台地址（App「我的 → 服务器地址」填这个）---')
if (online) console.log(`  公网（任意网络）  ${online}`)
if (lanMatch) console.log(`  局域网（同一 WiFi）${lanMatch}`)
console.log(`  本机调试          ${GATEWAY}/`)

const failed = results.filter((r) => !r.ok)
if (failed.length) {
  console.log('\n--- 修复建议 ---')
  if (!ports.has(3300)) {
    console.log('网关未启动：cd server && PORT=3300 NCM_PORT=3301 MUSICLIB_URL=http://127.0.0.1:3100 node index.js')
  }
  if (!ports.has(3100)) {
    console.log('多音源未启动：cd server/musiclib-service && ./qmusic-musiclib.exe')
  }
  if (ports.has(3000) && !ports.has(3300)) {
    console.log('提示：:3000 可能被其它项目占用（如 WorkBuddy 预览服务 node server.js），用 netstat -ano 查 PID')
  }
  if (online && failed.some((r) => r.name === '线上隧道')) {
    console.log('隧道失效：Cloudflare Quick Tunnel 每次重启换域名。重启命令：')
    console.log('  .workbuddy/cloudflared.exe tunnel --url http://127.0.0.1:3300 --no-autoupdate --logfile .workbuddy/tunnel-<date>.log')
    console.log('  然后从日志 grep trycloudflare 取新域名，更新 ServerConfig.ONLINE_BASE_URL 并重新打包')
  }
  process.exitCode = 1
} else {
  console.log('\n全部正常 ✅')
}
