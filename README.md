# NikonTransfer

尼康相机 → 安卓手机的照片传输 App（对标"Z传"）：通过 **Wi-Fi（PTP/IP）** 或 **USB 数据线（PTP over bulk）** 直连尼康 Z 系相机，浏览照片网格（JPG + NEF）、全屏预览（高清大图 + EXIF 参数）、单张/批量下载到系统相册。

实测机型：Nikon Z 6_2；Android 12+（minSdk 28，targetSdk 37），仅 arm64-v8a。

## 功能

### 双通道连接
- **USB 直连**（推荐）：数据线插上即自动发现相机（vendor 0x04B0），系统弹窗授权后连接；枚举走尼康专有 0x9434 全量表（886 张 ~1 秒），表不支持时自动回退逐对象枚举
- **Wi-Fi（PTP/IP）**：连接相机热点后自动探测（上次 IP → 网段扫描），尼康配对握手；探测/扫描阶段可随时取消或直接切换 USB
- 连接状态、失败原因、扫描进度统一显示在双卡下方信息卡片

### 浏览与选择
- 照片网格按拍摄日期分组，支持**日期折叠**（只藏不见：全选/批量下载仍包含折叠天照片）
- **JPG+NEF 合并视图**：同一拍摄张成对显示（可在设置关闭）
- 全屏预览：高清大图（JPG 读 MPF 内嵌大图 / NEF 解析嵌入 JPEG）、EXIF 胶囊（光圈 · 快门 · ISO · 焦距 · 快门次数 · 镜头）、格式徽章
- 多选：**全选 / 选当天**（跟随当前滚动日期）、按格式筛选、跳过已下载
- 日期区间筛选、快速滚动条（显示阈值可设置）

### 下载
- 单张 / 多选批量下载，写入系统相册（JPG → `MediaStore.Images`，NEF → `MediaStore.Files`，支持按拍摄日期建夹）
- 下载队列：缩略图、逐任务进度+实时速度、总进度、排队/完成汇总、单任务取消 + 一键全部取消

## 技术要点

- **Wi-Fi 通道 — libgphoto2（PTP/IP）**：预编译库 `app/libs/gphoto2/arm64-v8a/`；camlib/iolib（`ptp2.so`、`ptpip.so`）随 assets 分发，首运行解包后 dlopen；JNI 封装配对探针、递归枚举、偏移读下载
- **USB 通道 — 自研 PTP over USB bulk**（`UsbPtpSession.kt`）：UsbManager → bulk 端点 → PTP 容器，支持 0x101B 尼康专有部分读取、ZLP 吸收、会话级管道自愈
- **枚举**：双通道统一采用 **递归文件夹（BFS ≤3 层）∪ 尼康 0x9434 全量表**（句柄+时间戳，16 字节/条）排序去重；文件类型按尼康句柄位段判断（hi16 高 8 位 `0x2a`=JPG / `0x0a`=NEF）
- **缩略图三层缓存**：内存 LRU + 无锁读路径 + 磁盘缓存（容量可设），串行 IO 队列杜绝线程风暴
- **Android 13+ 本地网络权限**：`ACCESS_LOCAL_NETWORK` 运行时动态申请
- **bindProcessToNetwork**：相机 Wi-Fi 无外网，必须绑定该网络否则流量被切走连接失败
- 前台服务 + WifiLock 保活（仅 Wi-Fi 通道）；ViewModel 跨 Activity 重建存活，会话不被系统杀掉

## 项目结构

```
nikon_Tranf/                          # Android Studio 工程
├── app/src/main/java/com/example/nikontransfer/
│   ├── MainActivity.kt               # 全部 Compose UI（连接双卡/网格/预览/下载队列/设置）
│   ├── MainViewModel.kt              # 业务核心：连接状态机/缩略图管线/下载队列/通道分发
│   ├── UsbPtpSession.kt              # USB 通道：自研 PTP over USB bulk
│   ├── GPhoto2Bridge.kt              # Wi-Fi 通道 JNI 声明 + assets 解包
│   └── CameraKeepAliveService.kt     # 前台服务 + WifiLock（Wi-Fi 保活）
├── app/src/main/cpp/
│   ├── gphoto2_jni.c                 # JNI 封装（CMake 构建）
│   └── CMakeLists.txt                # 链接预编译 gphoto2 库
├── app/libs/gphoto2/arm64-v8a/       # 预编译 libgphoto2 + 头文件
└── app/src/main/assets/gphoto2/      # camlibs / iolibs（ptp2.so、ptpip.so）
lib/gp-android/gp-arm64/              # 交叉编译 gphoto2 的原始产物（参考）
```

## 构建与运行

要求：Android Studio（AGP + Gradle 9.x）、NDK / CMake（≥3.22）、JDK 21（Gradle toolchain 自动提供）。

```bash
cd nikon_Tranf
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

或在 Android Studio 中直接 Run。

## 使用

**USB 通道**
1. 相机 USB 连接模式选 **MTP/PTP**（Mass Storage 不行）
2. 数据线连接手机（OTG），打开 App 自动弹授权 → 确认即可

**Wi-Fi 通道**
1. 相机开启 Wi-Fi（SnapBridge / 内置 Wi-Fi 的 PTP 模式），手机连接相机热点
2. 打开 App 自动探测相机 IP；首次连接需在相机屏幕上确认配对

## 已知限制

- 仅 arm64-v8a 设备
- 相机需支持尼康 PTP 扩展（实测 Z6 II）；不支持 0x9434 表的机型自动回退慢速枚举（每张一次往返）
- USB 通道网格缩略图约 160px（GetThumb 能力所限）；NEF 高清预览首次需整文件读取（50MB 约 5-10 秒，之后有缓存）
- 手机仅一个 Type-C 口时，接电脑（adb）与接相机互斥
- 不支持：手机↔电脑传输、OTG 读卡器、MTP 设备 API
