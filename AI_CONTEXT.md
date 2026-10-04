# Nikon Transfer — AI 开发上下文文档

> **本文档的目的**：任何 AI 工具（或新开发者）读取本文件后，无需重新探索即可继续本项目的开发工作。
> 所有内容基于代码实证与真机调试记录，最后更新：2026-10-04。

---

## 1. 项目一句话

**尼康相机 → 安卓手机的照片传输 App**（对标市面"Z传"App）：
通过 **Wi-Fi（PTP/IP）** 或 **USB 线（PTP over bulk）** 连接尼康 Z 系相机，浏览照片网格（JPG+NEF）、
全屏预览（高清大图 + EXIF 参数胶囊）、单张/批量下载到系统相册。

- 包名：`com.example.nikontransfer`
- 实测相机：**Nikon Z 6_2**（序列号 0000008060212），固件 USB PTP 有 **83 个操作、无任何部分读取能力**（详见 §6 quirks）
- 实测手机：小米 24129PN74C，Android 16（API 37），**120Hz 屏**

## 2. 技术栈与构建

| 项 | 值 |
|---|---|
| 语言/UI | Kotlin 2.2.10 + Jetpack Compose（BOM 2024.12.01，Material3） |
| AGP / Gradle | 9.4.1 / Gradle 9.7.1（wrapper） |
| SDK | minSdk 28 · targetSdk/compileSdk 37 |
| NDK | 有（`gphoto2_jni.c` + CMake），链接**预编译** libgphoto2/libgphoto2_port（`lib/gp-android/gp-arm64/`） |
| 关键三方 | `androidx.exifinterface`（EXIF）、`com.github.nanihadesuka:LazyColumnScrollbar:2.2.0`（JitPack，网格滚动条） |
| 版本目录 | `gradle/libs.versions.toml` |

**构建命令**（Windows Git Bash 实测可用）：

```bash
JAVA_HOME="D:/Program Files/Android/Android Studio/jbr" \
  E:/Code/GitHub/Nikon_Tranf/nikon_Tranf/gradlew -p E:/Code/GitHub/Nikon_Tranf/nikon_Tranf assembleDebug --console=plain
D:/Android/Sdk/platform-tools/adb.exe install -r E:/Code/GitHub/Nikon_Tranf/nikon_Tranf/app/build/outputs/apk/debug/app-debug.apk
```

- 编译期只看 `^e: |FAILED|BUILD` 三种行即可。
- 实测设备 adb 序列号 `4e465073`；**注意手机只有一个 USB-C 口**——接电脑（adb）和接相机（OTG）互斥，调试 USB 通道时 adb 会断开。
- `screen_off_timeout` 曾被改为 10 分钟（`settings put system screen_off_timeout 600000`），`svc power stayon usb` 已开启。

## 3. 源码地图（全部在 `app/src/main/java/com/example/nikontransfer/`）

| 文件 | 行数 | 职责 |
|---|---|---|
| `MainActivity.kt` | ~2800 | 全部 UI：主界面（双模式网格/筛选/多选）、设置页、全屏预览（沉浸模式/EXIF 胶囊/毛玻璃背景）、快速滚动条 |
| `MainViewModel.kt` | ~2200 | **业务核心**：连接状态机、缩略图管线（LRU/磁盘缓存/串行队列）、高清预览管线（hiresCache）、下载队列、通道分发（USB/Wi-Fi） |
| `UsbPtpSession.kt` | ~505 | **USB 通道**：自研 PTP over USB bulk（照搬 Z传 架构），见 §6 |
| `GPhoto2Bridge.kt` | 65 | Wi-Fi 通道的 JNI 声明（native 实现在 `cpp/gphoto2_jni.c`） |
| `CameraKeepAliveService.kt` | 101 | 前台服务 + WifiLock（仅 Wi-Fi 连接用，USB 不需要） |
| `cpp/gphoto2_jni.c` | 960 | JNI 封装：`nativeConnect("ptpip:IP")`、尼康配对探针、句柄式列表/下载/偏移读 |
| `AndroidManifest.xml` | — | 权限 + `uses-feature usb.host (required=false)` |

## 4. 双通道架构（核心设计）

```
                    ┌─────────────── UI（MainActivity）───────────────┐
                    │   photoRows / pairSections / 预览 / 下载队列      │
                    └──────────────────────┬──────────────────────────┘
                                           │ 4 个数据原语（通道分发，在 MainViewModel）
                    ┌──────────────────────┴──────────────────────────┐
                    │ enumerate / objectSize / partialRead·getObject / 下载│
                    └──────────┬──────────────────────────┬──────────┘
                        Wi-Fi 通道                │          USB 通道
              GPhoto2Bridge（JNI→libgphoto2）     │     UsbPtpSession（纯 Kotlin）
              ptpip:IP，尼康配对探针，保活心跳      │     UsbManager→bulk 端点→PTP 容器
```

**通道选择逻辑**（`connectionFlow()` 开头）：
1. USB 优先：`UsbPtpSession.findCamera()`（vendor `0x04B0`）找到且已授权 → 直接 USB 连接（跳过全部 Wi-Fi 流程）
2. 无 USB → 走 Wi-Fi 三步发现：热点网关 IP → 上次 IP → 网段扫描
3. **插线**：`ACTION_USB_DEVICE_ATTACHED` → 请求权限（自定义广播 `ACTION_USB_PERMISSION`，PendingIntent FLAG_MUTABLE）→ 授权后自动切换（先断 Wi-Fi 会话）
4. **拔线**：`ACTION_USB_DEVICE_DETACHED` → 关 USB 会话 → 自动回退 Wi-Fi 流程
5. receiver 注册必须带 `Context.RECEIVER_NOT_EXPORTED`（Android 13+ 混合系统+自定义广播强制）

## 5. Wi-Fi 通道要点（gphoto2 PTP/IP）

- 预编译库的 IO 后端**只有 ptpip.so 和 disk.so**——没有 USB 端口，所以 Wi-Fi 走 gphoto2、USB 走自研，别想着给 gphoto2 补 libusb（Android fd 注入是硬骨头，已评估放弃）。
- 连接：`nativePairingProbe(ip)` 复刻 Z传 的尼康配对握手 → 成功后常驻传输会话。
- 连接后必须 `bindToWifiNetwork(ip)`（网络绑定）+ `startEventPolling()`（0x941c 心跳）+ `startKeepAlive()`（前台服务防冻结）。
- 数据原语（JNI）：`nativeListNative()` / `nativeObjectSizeNative(handle)` / `nativePreviewNative(handle, offset, len)`（**偏移读**，gphoto2 自动截断越界块）/ `nativeDownloadNative(handle)`。
- **USB 模式全部跳过**：配对、心跳、保活、网络绑定（USB 会话本身稳定）。

## 6. USB 通道要点（UsbPtpSession.kt）⚠️ 固件 quirks 血泪清单

实测相机 **Z6 II 的 USB PTP 能力**（GetDeviceInfo.OperationsSupported，共 83 个操作）：

| 能力 | 状态 | 说明 |
|---|---|---|
| 0x1014 GetPartialObject（标准） | ❌ 0x2006 | 尼康 USB 把 0x1014 解释为 GetDevicePropDesc 类操作，参数对不上 |
| 0x95C1 GetPartialObject64 (MTP) | ❌ 0x2005 | 不在能力表（83 操作）中 |
| **0x101B GetPartialObject（尼康专有）** | ✅ | **部分读取唯一通道**（Z传 操作码字典实证），参数 [handle, offset, len]，单次上限自适应（32KB 起步升档至 1MB，0x2006 减半） |
| 0x1009 GetObject 整对象 | ✅ | 偏移读失败时的兜底 |
| 0x100A GetThumb | ✅ | 网格缩略图（~160×120，网格上略糊属预期） |
| 0x1008 GetObjectInfo | ✅ | 文件名/时间/大小/格式 |

**UsbPtpSession 关键实现**：
- 枚举：`GetObjectHandles(-1, 0, 0)` root 起 → association（0x3001）递归 → **全程按句柄去重**（尼康 parent=0 会返回跨层级对象，不去重会让 LazyGrid key 冲突直接崩溃）。
- **ObjectInfo 解析的自适应文件名搜索**：标准偏移 52 处读文件名失败（尼康头部有 ±字节差异）时，在 44..80 窗口搜索"像文件名"的 PSTR（`.JPG`/`.NEF` 结尾或文件夹名）。
- **PTP 容器**：Command `[u4 len][u2 type=1][u2 opcode][u4 txid][params]`；Data `[u4 len][u2 type=2][payload]`；Response `[u4 len][u2 type=3][u2 code][u4 txid][params]`。Response 的 params 从**偏移 12**（txid 之后）读。
- **transaction 的数据阶段**：预分配 `ByteArray(payload)` 零拷贝直填（50MB NEF 峰值内存 1×）；每轮 bulkIn `BULK_CHUNK=64KB`（失败自动降 16KB）。
- **ZLP 陷阱**：数据 payload 恰为 512 字节整数倍时，设备在数据阶段结束发**零长度包**——响应读取前必须吸收（`n==0` 重读），否则响应残留管道 → 错位 → **USB 管道永久死亡（全部 w=-1）**。
- **管道自愈**：`withRecovery{}` 包装所有原语——传输异常（超时/错位/-1）→ `closeDevice+openDevice+OpenSession` 重建会话（约 2 秒）→ 重试一次当前操作。`recoveryLock` 互斥。
- 超时 `TIMEOUT=15000`（相机读卡+生成缩略图可能明显慢）。
- 枚举输出格式必须与 `nativeListNative()` 一致：**`"句柄:YYYYMMDD-HHMMSS:JPG|..."`**（`|` 分隔行，`:` 分隔字段，type 大写 JPG/NEF）。
- 相机侧前提：插线时相机屏幕选 **MTP/PTP** 连接模式（Mass Storage 不行）。

## 7. 数据模型与缓存体系（MainViewModel）

- `PhotoRow(name, handle, type, stamp)`：`preview: State<Bitmap?>`、`selected`、`downloaded`、`onScreen`（USB 滚出屏读回只进缓存不触发重组）。`handle` = PTP object id，**双通道语义一致**。
- 文件名合成规则：`IMG_${stamp}_${handle后4位}.${type小写}`——保存/去重都靠 name。
- **缩略图三层**：内存 LRU `thumbMem`（堆/8 ≤64MB）+ 无锁读路径 `thumbFast`（ConcurrentHashMap，主线程不抢 LruCache 锁）+ 磁盘缓存（`cacheDir/thumbs`，JPEG 85%，软上限 `set_thumb_cache_mb`）。LRU 逐出 → 清对应行 preview → 格子滑回时 `ensureThumb` 从磁盘读回（**串行队列** thumbIoQueue，绝不开线程风暴）。`putThumb` 是唯一写入口：仅 `row.onScreen` 时才写 preview（防滑动中位图填充重组风暴）。
- **高清预览**：`hiresCache`（LRU 6 张，采样 ≤2048px）。JPG=MPF 大图偏移读；NEF=TIFF 解析嵌入 JPEG（`nefPreviewRange` 探测窗 512KB）。USB：整对象下载后内存切片，逻辑复用。
- **EXIF**：**必须从主文件头部解析**（高清嵌入大图的 EXIF 只有尺寸！）。解析用 `getAttributeDouble/Int`（`getAttribute` 返回有理数字符串直接 toDouble 会全灭）；ISO 兼容新旧标签（0x8830/0x8827）。
- 置顶日期胶囊计算**必须** `derivedStateOf`（直接读 `layoutInfo` 会每帧重组整个 MainScreen → 滑动卡顿）。

## 8. 性能调试工具箱（实测有效的命令）

```bash
# 帧数据（程序化滑动后）：聚合 percentile + 最近 120 帧分解
adb shell dumpsys gfxinfo com.example.nikontransfer reset
adb shell input swipe 400 1800 400 500 120     # fling；慢滑改 duration=600
adb shell dumpsys gfxinfo com.example.nikontransfer framestats > frames.txt
# framestats 列名（小米 API37）：Flags,FrameTimelineVsyncId,IntendedVsync,...,FrameCompleted,...
# 分段：anim=PerformTraversalsStart-AnimationStart（重组所在）；解析脚本见 doc/analyze_gfx2.py

# 方法级采样（simpleperf 在小米内核被拒 perf_event，用这个替代）
adb shell am profile start --sampling 1000 com.example.nikontransfer /data/local/tmp/perf.trace
adb shell am profile stop com.example.nikontransfer
# ART trace v3 格式：数据段在 "*end\n" 后有 "SLOW" 4 字节 magic 要跳过；解析脚本 doc/parse_art_trace.py

# 常用日志过滤
adb logcat -d | grep -E "UsbPtp|GPhoto2"
```

- **性能排查铁律**：程序化滑动复现 + framestats 分段（anim/layout/draw）定位，不靠用户体感猜。
- 本机简单perf（simpleperf）**不可用**（perf_event 权限拒绝）；atrace 可以但 E 行格式是 `E|pid`（无第二个竖线）且行首有空格。

## 9. 环境坑清单（每个都真实踩过）

1. **Kotlin init 块按源码顺序执行**——init 里引用后面声明的字段 = 运行时 NPE（编译不报错）。VM 构造崩溃表现为"App 打开即闪退"。
2. **Android 13+ registerReceiver** 混合系统+自定义广播必须指定 `RECEIVER_EXPORTED/NOT_EXPORTED`，否则 SecurityException。
3. `0xFFFFFFFF` 字面量是 **Long**，进 `intArrayOf` 要写 `-1` 或 `.toInt()`。
4. `Collections.newSetFromMap` 返回 **Set**（没有 `[]=` 下标操作符）。
5. **LazyColumnScrollbar 陷阱**：全系列（2.2.0/3.0.0）的拖动映射和拖块位置都是"行号等高假设"——日期头+大图混排行高列表会交界跳动，且 internal 无注入点。当前用法：库只做显示+滚动条（禁其手势 `selectionMode=Disabled`），拖动自管（右缘窄条+像素级 scrollBy）。3.0.0 是 Kotlin 2.4 编译的，本工程 Kotlin 2.2.10 读不了，必须用 2.2.0。
6. **无障碍服务（GKD/小米钱包AI记账）会全树扫描语义节点**，坐标计算发生在 App 主线程——重内容网格必须 `clearAndSetSemantics{}` 免疫（已在两个网格容器上实施），否则每帧 6-17ms 语义计算 = 滑动必卡。
7. Z6 II USB PTP **GetObjectInfo 的 ObjectInfo 头部与 PTP 标准 ±字节差异**——文件名解析已做自适应搜索，动这块前先看 `infoFailDetail` 诊断。
8. **手机单 USB-C 口**：接电脑 vs 接相机互斥；USB 通道测试时 adb 断开，诊断靠 UI 上的 `lastDiag` 或来回换线。

## 10. 当前状态与待办（截至 2026-10-04 17:30）

**已完成**：Wi-Fi 全功能（成熟）；USB 直连全链路（发现/权限/枚举/去重/自适应文件名/整对象下载/GetThumb 缩略图/NEF 内存切片高清/EXIF/拔线自愈/管道自愈）；网格性能优化四件套。

**USB 通道最新一版装机待验证**（ZLP 吸收 + 管道自愈版，17:20 装机）：
- [ ] 缩略图全量加载（此前 20 张后管道死）
- [ ] 高清预览 + EXIF（整对象下载 → 内存切片/直接解码）
- [ ] 下载保存成功（末块截断修复后）
- [ ] 拔线自动回退 Wi-Fi
- 若仍有失败：接电脑抓 `adb logcat -d | grep UsbPtp`——失败码、limit、自愈记录都在日志里。

**已知取舍**：USB 缩略图用 GetThumb（~160px，网格上略糊）；USB 高清预览首开需整文件下载（NEF 50MB 约 5-10 秒，之后 hiresCache 缓存）；无下载进度细分（整块到达）。

**明确不做/已否决**：gphoto2 补 libusb USB 端口（fd 注入硬骨头）；MtpDevice API（黑盒、厂商差异）；手机↔电脑方向；OTG 读卡器。

## 11. 设置项（SharedPreferences "cfg"）

`set_auto_preview` · `set_dark_mode` · `set_date_folder`（按拍摄日期建夹）· `set_hires_preview` · `set_merge_pairs`（JPG+NEF 合并格）· `set_notify_done` · `set_skip_downloaded` · `set_thumb_cache_mb` · `camera_ip`（上次 Wi-Fi 相机 IP）。
