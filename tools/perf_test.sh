#!/usr/bin/env bash
# 离线 UI 流畅度自动化测试（相机连着时没有 adb 通道，所以全部用演示数据跑）
#
# 用法： bash tools/perf_test.sh [场景...]
#   不传场景 = 跑全部。场景：idle scroll select download settings
#
# 依赖：adb（本脚本按常见 SDK 路径找，找不到就用 PATH 里的）
# 读数：logcat -s Perf，一行一条 PERF[tag]

set -u
PKG=com.example.nikontransfer
APK="$(dirname "$0")/../nikon_Tranf/app/build/outputs/apk/debug/app-debug.apk"

ADB="${ADB:-}"
if [ -z "$ADB" ]; then
  for c in /d/Android/Sdk/platform-tools/adb.exe "${ANDROID_HOME:-}/platform-tools/adb.exe" \
           "${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe" adb; do
    [ -n "$c" ] && command -v "$c" >/dev/null 2>&1 && ADB="$c" && break
    [ -n "$c" ] && [ -x "$c" ] && ADB="$c" && break
  done
fi
[ -n "$ADB" ] || { echo "找不到 adb"; exit 1; }

perf() {
  if [ $# -ge 2 ]; then
    "$ADB" shell am broadcast -a $PKG.PERF --es cmd "$1" --es tag "$2" >/dev/null 2>&1
  else
    "$ADB" shell am broadcast -a $PKG.PERF --es cmd "$1" >/dev/null 2>&1
  fi
}
perfn() { "$ADB" shell am broadcast -a $PKG.PERF --es cmd "$1" --ei n "$2" >/dev/null 2>&1; }

echo "== 安装 =="
"$ADB" install -r "$APK" | tail -1

"$ADB" shell input keyevent 224 >/dev/null 2>&1        # 亮屏
"$ADB" shell wm dismiss-keyguard >/dev/null 2>&1
# 必须强杀再起：Activity 在栈顶时 am start 走 onNewIntent，容易漏掉启动参数
"$ADB" shell am force-stop $PKG
"$ADB" logcat -c
"$ADB" shell am start -n $PKG/.MainActivity --ez demo true -W 2>&1 | grep -E "Status|Activity" | head -2
sleep 4
echo "rows=$("$ADB" shell am broadcast -a $PKG.PERF --es cmd dump --es tag boot >/dev/null 2>&1; "$ADB" logcat -d -s PerfTrace | grep 'PERF\[boot\]' | sed 's/.*rows=/rows=/;s/ visible.*//')"

run() {   # run <场景名> <前置动作> <持续时间>
  echo "---- 场景: $1 ----"
  perf reset
  "$ADB" shell dumpsys gfxinfo $PKG reset >/dev/null 2>&1
  sleep 0.5
  eval "$2"
  sleep "$3"
  perf dump "$1"
  # 系统侧独立读数（JankTracker）：与 App 内采样互相验证
  "$ADB" shell dumpsys gfxinfo $PKG 2>/dev/null \
    | grep -E "Total frames rendered|^Janky frames|^50th percentile|^90th percentile|^95th percentile" \
    | tr -d '\r' | paste -sd' ' - | sed 's/  */ /g;s/^/  gfx: /'
  sleep 0.3
}

SCENARIOS="${*:-idle scroll select dlstatic dlrun settings}"

for s in $SCENARIOS; do
  case $s in
    idle)
      run idle "true" 4
      ;;
    scroll)
      # 10 次快速上滑（每次 100ms 的 flick），间隔 0.4s 让惯性滚完
      run scroll 'for i in 1 2 3 4 5 6 7 8 9 10; do "$ADB" shell input swipe 600 2150 600 450 100; sleep 0.4; done' 2
      ;;
    select)
      run select 'perf clear; sleep 0.3; perfn select 300' 3
      ;;
    dlstatic)
      # 只入队不跑：隔离"页面本身开销"与"下载活动开销"
      run dlstatic 'perf stopdl; sleep 0.3; perf openDownloads; sleep 0.8; perfn enqueue 20' 6
      ;;
    dlrun)
      run dlrun 'perf stopdl; sleep 0.3; perf openDownloads; sleep 0.8; perfn download 20' 8
      ;;
    settings)
      run settings 'perf stopdl; sleep 0.3; perf openSettings' 3
      ;;
  esac
done

echo
echo "==== 结果（fps / 掉帧% / P50 / P95 / 最长帧 / 派生重算次数）===="
"$ADB" logcat -d -s PerfTrace | grep "PERF\[" | sed 's/.*PERF\[/[/'
