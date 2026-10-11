#!/usr/bin/env node
/**
 * qMusic 版本号自增脚本
 *
 *   versionName  每次 +0.01   （1.01 -> 1.02 -> 1.03 ...）
 *   versionCode  每次 +1
 *
 * 用法：
 *   node scripts/bump-version.js            # 自增并写入 version.properties
 *   node scripts/bump-version.js --dry-run  # 只打印结果，不写入
 *
 * 结果同时写入 .build-version，供打包脚本读取。
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.join(__dirname, '..')
const FILE = path.join(ROOT, 'version.properties')
const OUT = path.join(ROOT, '.build-version')

function readProps(file) {
  const map = {}
  if (!fs.existsSync(file)) return map
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const t = line.trim()
    if (!t || t.startsWith('#')) continue
    const i = t.indexOf('=')
    if (i < 0) continue
    map[t.slice(0, i).trim()] = t.slice(i + 1).trim()
  }
  return map
}

/** 把 1.01 / 1.0.0 这类版本号统一换算成「百分之一版本」的整数 */
function toHundredths(name) {
  const parts = String(name).split('.').map((n) => parseInt(n, 10) || 0)
  if (parts.length >= 3) return parts[0] * 100 + parts[1] * 10 + parts[2]
  return parts[0] * 100 + (parts[1] || 0)
}

function formatHundredths(v) {
  return `${Math.floor(v / 100)}.${String(v % 100).padStart(2, '0')}`
}

const args = process.argv.slice(2)
const dryRun = args.includes('--dry-run')

const props = readProps(FILE)
const currentName = props.versionName || '1.00'
const currentCode = parseInt(props.versionCode || '0', 10) || 0

const nextName = formatHundredths(toHundredths(currentName) + 1)
const nextCode = Math.max(currentCode + 1, toHundredths(nextName))

console.log(`[version] ${currentName} (${currentCode})  ->  ${nextName} (${nextCode})`)

if (!dryRun) {
  const lines = [
    '# ------------------------------------------------------------------',
    '# qMusic Android 版本配置',
    '#',
    '#   appName      APK 文件名前缀（自定义打包名字）',
    '#   versionName  展示版本号，每次打包自动 +0.01',
    '#   versionCode  应用市场的整数版本号，每次打包自动 +1',
    '#',
    '# 打包前会自动更新本文件：见 android/scripts/bump-version.js',
    '# 也可以手动修改后直接打包。',
    '# ------------------------------------------------------------------',
    `appName=${props.appName || 'qMusic'}`,
    `versionName=${nextName}`,
    `versionCode=${nextCode}`,
    '',
  ]
  fs.writeFileSync(FILE, lines.join('\n'), 'utf8')
  fs.writeFileSync(OUT, nextName, 'utf8')
  console.log(`[version] 已写入 ${path.basename(FILE)}`)
}

// 供 shell 脚本直接取值
process.stdout.write(`VERSION_NAME=${nextName}\nVERSION_CODE=${nextCode}\n`)
