#!/usr/bin/env bash
# ==================================================================
# qMusic 一键打包（macOS / Linux）
#   1) 版本号 +0.01（versionName）/ +1（versionCode）
#   2) 构建 APK
#   3) 复制到 android/dist/ 并打印产物路径
#
# 用法：
#   ./build-apk.sh            生产包（release，已签名 + R8 混淆）
#   ./build-apk.sh debug      调试包
# ==================================================================
set -e
cd "$(dirname "$0")"

BUILD_TYPE="${1:-release}"
case "$BUILD_TYPE" in
  release|debug) ;;
  *) echo "[ERROR] 未知构建类型：$BUILD_TYPE（只能是 release 或 debug）"; exit 1 ;;
esac

echo
echo "[1/3] 版本号自增（+0.01）"
node scripts/bump-version.js

VER="$(cat .build-version)"
APPNAME="$(grep -E '^appName=' version.properties | cut -d= -f2 | tr -d '\r')"
APK="${APPNAME}-${VER}-${BUILD_TYPE}.apk"

echo
echo "[2/3] 构建 ${BUILD_TYPE} APK（版本 ${VER}）..."
if [ "$BUILD_TYPE" = "release" ]; then
  ./gradlew assembleRelease --console=plain
else
  ./gradlew assembleDebug --console=plain
fi

echo
echo "[3/3] 复制产物到 dist/"
mkdir -p dist
cp -f "app/build/outputs/apk/${BUILD_TYPE}/${APK}" "dist/${APK}"

echo
echo "=================================================================="
echo "  打包完成"
echo "    APK    : $(pwd)/dist/${APK}"
echo "    版本   : ${VER}  (${BUILD_TYPE})"
echo "=================================================================="
