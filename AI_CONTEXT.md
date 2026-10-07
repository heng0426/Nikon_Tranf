# Nikon Transfer — AI 开发上下文文档

> **本文档的目的**：任何 AI 工具（或新开发者）读取本文件后，无需重新探索即可继续本项目的开发工作。
> 所有内容基于代码实证与真机调试记录，最后更新：2026-10-07。

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
- **daemon JVM 已固定 JDK21**（`gradle/gradle-daemon-jvm.properties` `toolchainVersion=21`）：JDK25.0.4 编译时 MainScreen 超大方法会产生 d8/ART VerifyError 运行时闪退；JDK 下载源不可达时把该文件 `toolchainUrl` 切清华镜像（文件内 URL 是 `https\://` 转义格式）。
- **部署约定**：改完代码 → `assembleDebug` → 手机 adb 连着就 `install -r` → 回报。⚠️ 编译失败时 adb install 会装上**上一次的旧 APK**——"Success" ≠ 最新代码上机，务必确认 BUILD SUCCESSFUL 再装。
- 实测设备 adb 序列号 `4e465073`；**注意手机只有一个 USB-C 口**——接电脑（adb）和接相机（OTG）互斥，调试 USB 通道时 adb 会断开。
- `screen_off_timeout` 曾被改为 10 分钟（`settings put system screen_off_timeout 600000`），`svc power stayon usb` 已开启。

## 3. 源码地图（全部在 `app/src/main/java/com/example/nikontransfer/`）

**UI 层按界面拆成扩展函数文件**（`internal fun MainActivity.Xxx`，共享 Activity 的 internal 成员；改 UI 先定位文件再动手）：

| 文件 | 行数 | 职责 |
|---|---|---|
| `MainActivity.kt` | ~1360 | Activity 本体 + `MainScreen`（全屏双卡连接/紧凑重连条/双模式网格骨架/筛选/多选+选当天/日期折叠）+ TopBarDownloadButton/ConnInfoCard/RollingText |
| `UiSpec.kt` | ~120 | **设计令牌中心**：动画三档（QUICK/STANDARD/EMPHASIS）、圆角双档（ROUND_SMALL/LARGE）、浅深成对主题色（accent/accentDeep/error/amber/usbBlue/connChip/卡片面/选中遮罩/已下载标/徽章/蒙层/占位灰）、布局尺寸（PILL_SLOT/CONN_INFO_H/QUEUE_ROW_H/PREVIEW_SLOT_H/CHECK_ICON/QUEUE_THUMB/DRAG_STRIP_W/SEL_BORDER_W/SCROLLBAR_THICKNESS）、行为参数（CHUNK_TRIGGER）。**新代码一律取令牌，不写散值** |
| `GridParts.kt` | ~800 | 双格 PairCell/单格 GridCell/格式徽章/已下载标、置顶日期胶囊与槽位、快速滚动条覆盖层（GridScrollbarOverlay，合并/文件共用）、日期节头胶囊、筛选面板 FilterSheet |
| `PreviewPagers.kt` | ~790 | 合并/单格式全屏预览 Pager、PreviewZoomState 缩放状态与手势、EXIF/下载槽位 |
| `SettingsScreens.kt` | ~730 | 设置页 + 全部设置组件（SettingsSection/SettingSwitch/ChannelChip/ActionPill/两个选择器） |
| `DownloadsQueue.kt` | ~560 | 下载队列侧板 + 队列行/缩略图/下载槽位 |
| `CardsIcons.kt` | ~530 | USB/Wi-Fi 连接卡（双卡与紧凑条共用）+ 全部 Canvas 自绘图标 |
| `MainViewModel.kt` | ~2480 | **业务核心**：连接状态机（Wi-Fi 取消+USB 接力）、缩略图管线、高清预览管线、配对缓存 pairRowsCache、分块加载、筛选/折叠/多选状态；下载队列已抽到 DownloadManager（本类保留传输 IO runQueueItem） |
| `DownloadManager.kt` | ~120 | 下载队列状态与串行调度（QueueItem/QStatus/入队去重/取消/worker）；传输 IO 由 VM 注入 `runItem` 回调，收尾 `onWorkerExit` 回调做批量提醒 |
| `UsbPtpSession.kt` | ~610 | **USB 通道**：自研 PTP over USB bulk（照搬 Z传 架构，0x9434 快路径枚举），见 §6 |
| `GPhoto2Bridge.kt` | 65 | Wi-Fi 通道的 JNI 声明（native 实现在 `cpp/gphoto2_jni.c`） |
| `CameraKeepAliveService.kt` | 101 | 前台服务 + WifiLock（仅 Wi-Fi 连接用，USB 不需要） |
| `cpp/gphoto2_jni.c` | ~1020 | JNI 封装：`nativeConnect("ptpip:IP")`、尼康配对探针、递归+0x9434 并集枚举、句柄式下载/偏移读 |
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
3. **插线**：`ACTION_USB_DEVICE_ATTACHED` → 请求权限（自定义广播 `ACTION_USB_PERMISSION`，PendingIntent FLAG_MUTABLE）→ 授权后自动连接（Wi-Fi 进行中则等其退出后接力）
4. **拔线**：`ACTION_USB_DEVICE_DETACHED` → 关 USB 会话 → **仅清理状态回双卡（不自动回退 Wi-Fi，连接方式由用户手动选）**
5. receiver 注册必须带 `Context.RECEIVER_NOT_EXPORTED`（Android 13+ 混合系统+自定义广播强制）
6. **Wi-Fi 可取消 + USB 接力**：探测上次 IP / 网段扫描 / 相机信息探测阶段可打断（`wifiCancelRequested` 检查点），进入配对后跑完；扫描/探测中点 USB 卡 = 取消 Wi-Fi + USB 卡动画展开成整条（`pendingChannel` 标记尝试通道），USB 接力线程**无上限**等 Wi-Fi 退出再连；**接力所有终止出口（未检测到相机/未授权）必须把 connPhase 恢复 disconnected**，否则 UI 卡在转圈；USB 连接中不支持反向切换。

**连接 UI 形态**（MainActivity）：
- **无照片一律全屏双卡**（冷启动/连接中/枚举中/断开回退）：USB/Wi-Fi 两张连接卡 + 下方 ConnInfoCard 信息卡片（连接中=进度+转圈，断开=失败原因红字，48dp 锁高（`UiSpec.CONN_INFO_H`），淡入展开 `UiSpec.STANDARD`/淡出收起 `UiSpec.QUICK`）；标题随状态三态（「正在连接相机…」/「正在读取照片列表…」/「选择连接方式」）。
- **有照片断开才显示紧凑重连条**（60dp 锁高）：点某卡 weight 1f→2.4f 动画展开成整条、另一卡收 0 淡出，失败自动恢复双卡（`UiSpec.EMPHASIS` FastOutSlowIn）。
- 顶栏：无照片只显示设置齿轮；有照片显示齿轮+筛选+连接状态+下载队列。

## 5. Wi-Fi 通道要点（gphoto2 PTP/IP）

- 预编译库的 IO 后端**只有 ptpip.so 和 disk.so**——没有 USB 端口，所以 Wi-Fi 走 gphoto2、USB 走自研，别想着给 gphoto2 补 libusb（Android fd 注入是硬骨头，已评估放弃）。
- 连接：`nativePairingProbe(ip)` 复刻 Z传 的尼康配对握手 → 成功后常驻传输会话。
- 连接后必须 `bindToWifiNetwork(ip)`（网络绑定）+ `startEventPolling()`（0x941c 心跳）+ `startKeepAlive()`（前台服务防冻结）。
- 数据原语（JNI）：`nativeListNative()` / `nativeObjectSizeNative(handle)` / `nativePreviewNative(handle, offset, len)`（**偏移读**，gphoto2 自动截断越界块）/ `nativeDownloadNative(handle)`。
- **枚举（nativeListNative 已重写）**：不能假设 GetObjectHandles(根层) 一次返回全部（实测只回当前文件夹 82/886 张）。现方案 = 递归文件夹 BFS（≤3 层，非 JPG/NEF 位段句柄视为文件夹下钻）**∪ 0x9434 尼康专有全量表**（每条 16 字节小端：句柄+保留+时间+日期，时间戳表 4096 项）排序去重；输出格式不变 `"句柄:YYYYMMDD-HHMMSS:类型|..."`。诊断日志：`list: 时间戳表 %d 项`、`list: 最终文件 %d 个（根层 %d…）`。
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
- **枚举（0x9434 快路径，886 张 ~1 秒）**：`0x9434`（尼康专有，参数 `[sid,0,0]`）一次返回全量句柄+时间戳（每条 16 字节小端：handle+保留+t1+t2，时间字节序 sec/min/hour/day/mon/year 带有效范围校验），**类型按句柄位段分类：hi16 高 8 位 `0x2a`=JPG、`0x0a`=NEF**（低 8 位是文件夹段，Z7=0x1c、新款跨 0x1c/0x1d——**必须用 `(hi16 & 0xff00)` 判断**，硬编码 0x2a1c 会丢其它文件夹段全部照片）；表为空自动回退旧路径（`GetObjectHandles(-1,0,0)` root 起 → association 0x3001 递归 → 逐对象 GetObjectInfo，886 张=886 次往返十几秒 → 全程按句柄去重，尼康 parent=0 会返回跨层级对象，不去重 LazyGrid key 冲突直接崩溃）。
- **ObjectInfo 解析的自适应文件名搜索**：标准偏移 52 处读文件名失败（尼康头部有 ±字节差异）时，在 44..80 窗口搜索"像文件名"的 PSTR（`.JPG`/`.NEF` 结尾或文件夹名）。
- **PTP 容器**：Command `[u4 len][u2 type=1][u2 opcode][u4 txid][params]`；Data `[u4 len][u2 type=2][payload]`；Response `[u4 len][u2 type=3][u2 code][u4 txid][params]`。Response 的 params 从**偏移 12**（txid 之后）读。
- **transaction 的数据阶段**：预分配 `ByteArray(payload)` 零拷贝直填（50MB NEF 峰值内存 1×）；每轮 bulkIn `BULK_CHUNK=64KB`（失败自动降 16KB）。
- **ZLP 陷阱**：数据 payload 恰为 512 字节整数倍时，设备在数据阶段结束发**零长度包**——响应读取前必须吸收（`n==0` 重读），否则响应残留管道 → 错位 → **USB 管道永久死亡（全部 w=-1）**。
- **管道自愈**：`withRecovery{}` 包装所有原语——传输异常（超时/错位/-1）→ `closeDevice+openDevice+OpenSession` 重建会话（约 2 秒）→ 重试一次当前操作。`recoveryLock` 互斥。
- 超时 `TIMEOUT=15000`（相机读卡+生成缩略图可能明显慢）。
- 枚举输出格式必须与 `nativeListNative()` 一致：**`"句柄:YYYYMMDD-HHMMSS:JPG|..."`**（`|` 分隔行，`:` 分隔字段，type 大写 JPG/NEF）。
- 相机侧前提：插线时相机屏幕选 **MTP/PTP** 连接模式（Mass Storage 不行）。

## 7. 数据模型与缓存体系（MainViewModel）

- `PhotoRow(name, handle, type, stamp, fileNo?)`：`preview: State<Bitmap?>`、`selected`、`downloaded`、`onScreen`（USB 滚出屏读回只进缓存不触发重组）。`handle` = PTP object id，**双通道语义一致**。`fileNo`（文件名去扩展名）本意是合并配对精确键，但 **0x9434 重写后双通道枚举均不输出文件名（fileNo=null）**，合并配对实际按 stamp。
- `PairRow(key, stamp, jpg, nef)`：合并视图一格 = 同拍摄张 JPG+NEF 成对（孤儿单格式也成项）。**选中态存在 VM 的 `pairSelection`（键 = key 非 stamp，连拍同秒会产生多格）**；实例每次派生时重建。
- **日期折叠**：`collapsedDates`（dateKey 集合，会话内有效，切视图/重连保留、退 App 重置）。节头日期胶囊点击折叠/展开，语义**"只藏不见"**：仅隐藏网格显示，全选/批量下载/统计口径仍含折叠天照片。
- **多选「选当天」**：多选操作条「全选」右侧勾选项。"当前日期" = 顶部置顶胶囊所示分节（`derivedStateOf` 由活动网格 firstVisibleItemIndex 映射分节，折叠节只算 1 个 item；仅跨分节输出新值）。口径与全选一致（只含筛选后可见照片）。**节头胶囊永远只负责折叠，不做多选让位**（点节头当全选用的第一版被用户否决）。
- **配对缓存**：`pairRowsAll` 走 `pairRowsCache`（仅 `listFiles` 在枚举线程预构建，`buildPairRows()`）——此前每次读取都 O(n log n) 全量重配对，是"合并模式比文件模式卡"的根因。
- **分块加载**：`chunkedLoad`/`chunkSize`（设置项，默认关）→ `displayLimit` 对 `visiblePhotos`/`visiblePairs` 做 take 截断；网格 `snapshotFlow` 监听滚动，距已加载末尾 `UiSpec.CHUNK_TRIGGER` 项内自动 `loadMoreChunks()`；全选/选当天/统计/预览翻页口径 = 已加载可见项；重新枚举自动重置进度。
- **下载队列**：状态与调度在 `DownloadManager`（入队去重/取消/串行 worker），传输 IO `runQueueItem` 留在 VM（持 camMutex），通过注入回调解耦；VM 用 `downloadQueue`/`activeDownloadCount` 等委托保持 UI 兼容。
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
9. **MainScreen 巨型方法风险**：曾达 ~1130 行，JDK25 编译时触发 d8/ART VerifyError 闪退（daemon 已固定 JDK21；UI 已按界面拆到 6 个扩展函数文件，但 MainScreen 仍有 ~900 行——新增大段 Compose 代码仍优先抽独立 fun）。
10. **Compose 类型/判空陷阱**：两个不同 data class 的 List（如 `List<PairSection>`/`List<DateSection>`）用 if 表达式赋同一变量会取 LUB=`List<Any>`，成员访问编译失败 → map 成统一元组（如 `(dateKey to rowsCount)`）再循环；`val x by derivedStateOf{}` 委托属性无法 smart cast，先固化局部值再判空。
11. **HyperOS「智能刷新率」按应用类别限帧**：`dumpsys display` 里 `frameRateCategoryRate {normal=60.0, high=90.0}`——无帧率声明的 App 被归入 normal=**60Hz** 档，帧距被 vsync 节拍钉在 ~20ms（体感"发闷"，janky 却≈0，极具迷惑性）。修复：`onCreate` 里 `window.attributes.preferredDisplayModeId = 同分辨率最高刷新模式`（实测帧距中位 20ms→5ms，janky 0.1%→0.24% 但基线是 120fps）。诊断三件套：`mActiveRenderFrameRate`（是否 120）、`frameRateCategoryRate`（类别限档）、gfxinfo percentile。
12. **滚动条快拖卡顿根因**：拖动事件里逐事件 `scope.launch { scrollBy }` 会协程排队堆积 + 逐事件物化 `layoutInfo.visibleItemsInfo`。修复模式：`onDragStart` 算一次缩放比，事件用 `LazyGridState.dispatchRawDelta` **同步**滚动（无协程/无锁排队）；指示气泡的分节列表提升到组合期取一次复用。

## 10. 当前状态与待办（截至 2026-10-07）

**已完成**：Wi-Fi 全功能（成熟；递归枚举+0x9434 并集后 886/886 张全可见）；USB 直连全链路（0x9434 快路径枚举 ~1 秒、表空回退旧路径/发现/权限/去重/自适应文件名/整对象下载/GetThumb 缩略图/NEF 内存切片高清/EXIF/管道自愈）；连接 UI 重构（全屏双卡/紧凑重连条/ConnInfoCard 信息卡片带动画、Wi-Fi 可取消+USB 接力、USB 读取进度并入信息卡片不再跳独立页）；网格性能优化四件套 + 合并模式配对缓存 + 滚动条快拖 dispatchRawDelta 重写 + 强制 120Hz（HyperOS 类别限频规避，见 §9#11）；日期折叠（合并+文件模式，"只藏不见"）；滚动条显示阈值设置（开关+展开阈值选择器）；**分块加载**（可关，每块 50/100/200/500，滑近末尾自动追加，置顶胶囊显示"已加载 X/Y"）；下载队列改版（缩略图行、卡片化进度填充铺卡、汇总双胶囊+总进度条、全部取消、顶栏角标替换图标+数字滚动、tnum 等宽数字）；预览页重构（图层分离、EXIF 淡入、图片名称+日期胶囊、下载进度进按钮、40dp 锁高、配色与队列同步）；多选「选当天」；**UI 设计令牌中心 UiSpec.kt**（动画三档/圆角双档/成对主题色/布局尺寸/行为参数，约 70 处散值已接线）；**UI 按界面拆文件**（GridParts/PreviewPagers/SettingsScreens/DownloadsQueue/CardsIcons，MainActivity 从 3964 行瘦到 1363 行）；设置页卡片化（分组色块图标/动作胶囊 ActionPill/行标题 onSurface）。

**已知取舍**：USB 缩略图用 GetThumb（~160px，网格上略糊）；USB 高清预览首开需整文件下载（NEF 50MB 约 5-10 秒，之后 hiresCache 缓存）；无下载进度细分（整块到达）；下载耗时统计功能代码存在但入口已按用户要求移除；强制 120Hz 仅前台生效（后台系统自动回落）。

**明确不做/已否决**：gphoto2 补 libusb USB 端口（fd 注入硬骨头）；MtpDevice API（黑盒、厂商差异）；手机↔电脑方向；OTG 读卡器；相机热点网关探测（用户明确留待以后）；多选时点节头日期胶囊当全选用（否决——会废掉折叠功能，改为多选操作条「选当天」）；网格整体淡入/已下载标记弹入/多选勾选弹跳（用户逐一否决——"闪一下"或多余，微交互仅保留队列/角标数字滚动）。

**返回键优先级**（BackHandler）：多选退出（预览未开时）→ 合并预览 → 单格式预览 → 下载侧板 → 设置页。

## 11. 设置项（SharedPreferences "cfg"）

`set_auto_preview` · `set_auto_conn_channel`（启动自动连接优先通道 usb/wifi，**仅影响启动顺序**，手动点卡和插线自动连接不受影响；非可观察状态，设置页选择器须读 `vm.autoConnChannel.value` 触发重组）· `set_chunked_load`（分块加载开关，默认关）· `set_chunk_size`（每块加载量 50/100/200/500，默认 100；合并模式按合并格计）· `set_dark_mode` · `set_date_folder`（按拍摄日期建夹）· `set_hires_preview` · `set_merge_pairs`（JPG+NEF 合并格）· `set_notify_done` · `set_scrollbar_threshold`（快速滚动条：-1=关（开关承担）/30/50/100/200，默认 50；计数口径=未折叠照片数）· `set_skip_downloaded` · `set_thumb_cache_mb` · `save_dir_uri`（自定义保存目录）· `camera_ip`（上次 Wi-Fi 相机 IP）。
