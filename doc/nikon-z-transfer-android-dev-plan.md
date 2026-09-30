# 用 libgphoto2 开发尼康 Z 系列传图 Android App — 环境与开发方案

> 目标：做一个自己的 Android App，通过 Wi-Fi (PTP/IP) 或 USB 连接尼康 Z 系列相机，浏览、监听并自动下载照片到手机。
> 核心思路：把 libgphoto2（C 库）用 NDK 交叉编译到 Android，写一层 JNI 封装，上层用 Kotlin 做界面与服务。

---

## 一、总体架构

```
┌─────────────────────────────────────────────┐
│  Kotlin 层 (Android App)                    │
│  UI (Compose) / 前台 Service / Room 图库    │
│  MediaStore 保存 / 网络绑定 / USB 权限管理   │
└──────────────┬──────────────────────────────┘
               │ JNI (C++ bridge, libgphoto2_jni.so)
┌──────────────▼──────────────────────────────┐
│  JNI 封装层：相机初始化 / 列目录 / 下载 /    │
│  事件监听 (ObjectAdded) / 拍照控制           │
└──────────────┬──────────────────────────────┘
               │ C API
┌──────────────▼──────────────────────────────┐
│  libgphoto2 (libgphoto2.so + ptp2 camlib)   │
│  端口驱动: ptpip (Wi-Fi) / usb (libusb1)    │
└──────────────┬──────────────────────────────┘
               │ PTP/IP (TCP :15740)  或  USB PTP
        ┌──────▼──────┐
        │  尼康 Z 相机  │
        └─────────────┘
```

**两条连接路线，难度差异很大，建议按顺序做：**

| 路线 | 协议 | 难度 | 说明 |
|------|------|------|------|
| ① Wi-Fi | PTP/IP（标准协议，TCP 15740 端口） | ★★☆ | 相机开「连接至 PC (Wi-Fi)」模式即为标准 PTP/IP，libgphoto2 原生支持 `ptpip` 端口，**不需要任何 USB 权限和 root**，强烈建议从这里起步 |
| ② USB | PTP over USB | ★★★★ | 需要 Android USB Host API + libusb 特殊处理（见第六节），有系统层坑，作为二期 |

---

## 二、需要的环境清单

### 开发机（宿主机）
| 组件 | 版本要求 | 用途 |
|------|----------|------|
| Android Studio | Ladybug 及以上 | IDE、SDK 管理 |
| JDK | 17 | Android 构建 |
| Android SDK | API 34/35 | 编译目标 |
| **NDK** | r26 / r27 | 交叉编译 C 库 + JNI |
| **CMake** | ≥ 3.22（实测 4.1.2） | App 内原生构建 |
| 一台 Linux / WSL2 / macOS | — | 跑 autotools 交叉编译 libgphoto2（App 内的 CMake 构建也可以做，但首次搭环境在 Linux 上最省事） |
| autotools | autoconf/automake/libtool/pkg-config | 生成 libgphoto2 的 configure 构建 |

### 需要交叉编译的开源库
| 库 | 推荐版本 | 说明 |
|----|----------|------|
| **libgphoto2** | ≥ 2.5.34 | 主库，LGPL-2.1+（ptp2 camlib 是核心） |
| libxml2 | 2.12.x | ptp2 camlib 解析相机能力描述（device description）必需 |
| libusb-1.0 | 1.0.27 | 仅 USB 路线需要，且需 ≥1.0.23（提供 `libusb_wrap_sys_device`） |
| libltdl (libtool 运行时) | 2.4.7 | libgphoto2 动态加载 camlib 用（也可静态内联） |

### Windows 开发环境（三种做法，按推荐顺序）

**做法 A：Windows + WSL2（最省事，推荐）**
Windows 上装 WSL2（`wsl --install`，选 Ubuntu 24.04），在 WSL 里安装 `build-essential autoconf automake libtool pkg-config`，把 NDK 解压到 WSL 可访问目录，然后按第三节的步骤在 WSL 里交叉编译。产出 `.so` 后拷回 Windows 的工程 `jniLibs/`，其余开发全在 Android Studio 里进行。体验等同于原生 Linux，坑最少。

**做法 B：纯 Windows + NDK CMake 源码编译（不碰 Linux）**
不用 autotools，把 libgphoto2 和 libxml2 源码直接作为子模块放进 App 工程，用 NDK 自带的 CMake（`externalNativeBuild`）在 Android Studio 里构建。代价是要手工准备 autoconf 本来会生成的东西：`config.h`、`gphoto2/gphoto2-config.h`、`gphoto2/gphoto2-porting.h` 等（可以取 libgphoto2 仓库里的模板手改，或参考社区现成的 Android CMake 工程）。一次性工作约 1~2 天，之后就在 Android Studio 里一键构建，全程 Windows。

**做法 C：MSYS2 原生原型验证（可与 A/B 并用，强烈推荐用于 M1 之前）**
MSYS2 的包仓库里有现成的 libgphoto2（`pacman -S mingw-w64-x86_64-libgphoto2`），也就是说你可以**先在 Windows 桌面上写 C 程序验证相机通信逻辑**：Wi-Fi 路线下 `ptpip` 端口在 Windows 桌面上直接可用，`gcc test.c -lgphoto2` 编个小工具连相机列文件/下载，逻辑跑通后再把同一段代码搬进 JNI——这能把"M1 交叉编译"和"搞懂 gphoto2 API"两件事解耦，极大降低前期风险。注意：桌面阶段**只走 Wi-Fi**，不要在 Windows 上折腾 USB（Windows 的 USB 驱动要 Zadig 换驱动，和 Android 的 USB Host 机制完全是两套，没有迁移价值）。

> 结论：**Windows 完全可以开发**。最平滑的组合是 A + C（WSL2 出产物，MSYS2 做前期原型）；如果不想碰任何 Linux，用 B 也完全可行。

### 测试硬件
- 尼康 Z 系列机身一台（Z6/Z7/Z30/Zf/Z8 等；注意确认你的机型支持「连接至 PC (Wi-Fi)」——大多数 Z 系列都支持）
- 支持 OTG 的 Android 手机（USB 路线）
- 同一路由器或直接用相机热点（Wi-Fi 路线推荐手机直连相机 AP，现场可用）

### App 侧技术栈（建议）
- Kotlin + Jetpack Compose（UI）
- 前台 Service（保持 PTP 会话不被杀）+ WakeLock/WifiLock
- Room（照片记录）+ MediaStore（保存进系统相册）
- CameraX 不需要（不是用手机拍照）；协程 + JNI 回调线程

---

## 三、路线①：Wi-Fi PTP/IP 开发方案（推荐主线）

### 3.1 相机端准备
1. 相机菜单 → 网络连接 → **「连接至 PC (Wi-Fi)」** → 建立连接（AP 模式或加入路由器）。
2. 记下相机显示的 **IP 地址**（PTP/IP 固定走 TCP **15740** 端口）。
3. 部分机型首次连接需在相机上确认配对。

### 3.2 交叉编译 libgphoto2（精简版，只留 ptp2 + ptpip）

在 Linux/WSL 中，用 NDK toolchain 编出 `aarch64` 的 `.so`：

```bash
export NDK=$ANDROID_NDK_HOME
export TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64
export CC=$TOOLCHAIN/bin/aarch64-linux-android26-clang
export AR=$TOOLCHAIN/bin/llvm-ar
export HOST=aarch64-linux-android
export PREFIX=/tmp/gphoto2-arm64

# 1) 先编 libxml2（Android 精简版）
cd libxml2 && mkdir build && cd build
../configure --host=$HOST --prefix=$PREFIX \
  --without-python --without-zlib --without-lzma --without-http --disable-shared --enable-static
make -j8 && make install

# 2) 编 libgphoto2：只启用 ptp2 camlib 和 ptpip 端口（不编 libusb、串口等）
cd ../libgphoto2
PKG_CONFIG_PATH=$PREFIX/lib/pkgconfig ./configure \
  --host=$HOST --prefix=$PREFIX \
  --with-camlibs=ptp2 \
  --with-ports=ptpip \
  --without-libusb \
  --disable-nls --disable-internal-docs --disable-examples
make -j8 && make install
# 产出：$PREFIX/lib/libgphoto2.so, libgphoto2_port.so 及 ptp2 camlib
```

要点：
- `--with-ports=ptpip --without-libusb`：Wi-Fi 阶段不需要 USB 端口驱动，能省掉 libusb 的 Android 特殊处理。
- camlib 是动态加载的（`.so`），打包 APK 时要把 `libgphoto2.so`、`libgphoto2_port.so`、`ptp2.so`、`libxml2.a/.so` 全部放进 `jniLibs/arm64-v8a/`。注意 libgphoto2 用路径查找 camlib，必要时用 `gp_set_cameralib_...`/环境变量或干脆把 camlib 静态编进主库（社区常见做法）。
- 若 autotools 折腾太久，也可以走 CMake：把 libgphoto2 源码作为子目录加进你自己工程的 `CMakeLists.txt`（需要手工写少量 config.h 替代 autoconf 产物）。

### 3.3 JNI 封装层设计

新建 `app/src/main/cpp/gphoto2_jni.c`，核心 API 调用序列：

```c
#include <gphoto2/gphoto2.h>

static Camera    *g_camera;
static GPContext *g_context;

// 1. 连接：Wi-Fi 时 path = "ptpip:192.168.1.66:15740"
JNIEXPORT jint JNICALL
Java_com_example_nikontransfer_GPhoto2Bridge_nativeConnect(
        JNIEnv *env, jobject thiz, jstring jpath) {
    const char *path = (*env)->GetStringUTFChars(env, jpath, 0);

    g_context = gp_context_new();
    gp_camera_new(&g_camera);

    GPPortInfo info;
    gp_port_info_new(&info);
    gp_port_info_set_type(info, GP_PORT_PTP);       // ptpip 由 path 前缀决定
    gp_port_info_set_name(info, "Nikon Z");
    gp_port_info_set_path(info, path);
    gp_camera_set_port_info(g_camera, info);

    int ret = gp_camera_init(g_camera, g_context);  // 内部完成 PTP OpenSession
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return ret;   // GP_OK == 0
}

// 2. 列出存储卡上的文件
JNIEXPORT jobjectArray JNICALL
Java_com_example_nikontransfer_GPhoto2Bridge_nativeListFiles(
        JNIEnv *env, jobject thiz, jstring jfolder) {
    // gp_camera_folder_list_files(camera, "/store_00010001/DCIM/100NZ510", &list, ctx)
    // 转成 String[] 返回
}

// 3. 下载一张照片（JPEG 或 NEF）
JNIEXPORT jbyteArray JNICALL
Java_com_example_nikontransfer_GPhoto2Bridge_nativeDownloadFile(
        JNIEnv *env, jobject thiz,
        jstring jfolder, jstring jname) {
    CameraFile *file;
    gp_file_new(&file);
    gp_camera_file_get(g_camera, folder, name, GP_FILE_TYPE_NORMAL, file, g_context);
    const char *data; unsigned long size;
    gp_file_get_data_and_size(file, &data, &size);   // 注意：不要释放 data 本身
    jbyteArray out = (*env)->NewByteArray(env, size);
    (*env)->SetByteArrayRegion(env, out, 0, size, (jbyte *) data);
    gp_file_free(file);
    return out;   // Kotlin 侧拿到字节后写入 MediaStore
}

// 4. 事件循环：等"相机拍了新照片"（自动传图的核心）
JNIEXPORT jobjectArray JNICALL   // 返回 [event_type, folder, name]，简化示意
Java_com_example_nikontransfer_GPhoto2Bridge_nativeWaitEvent(
        JNIEnv *env, jobject thiz, jint timeout_ms) {
    CameraEventType ev; void *data = NULL;
    int ret = gp_camera_wait_for_event(g_camera, timeout_ms, &ev, &data, g_context);
    if (ret == GP_OK && ev == GP_EVENT_FILE_ADDED) {
        CameraFilePath *fp = data;   // 新增文件的 folder + name
        // 转成 Java 字符串数组返回，Kotlin 侧调用 nativeDownloadFile 下载
    }
    return NULL;
}

// 5. 断开
// gp_camera_exit(g_camera, g_context); gp_camera_free(g_camera); gp_context_unref(g_context);
```

Kotlin 侧骨架：

```kotlin
object GPhoto2Bridge {
    init { System.loadLibrary("gphoto2_jni") }
    external fun nativeConnect(path: String): Int          // ptpip:IP:15740
    external fun nativeListFiles(folder: String): Array<String>
    external fun nativeDownloadFile(folder: String, name: String): ByteArray
    external fun nativeWaitEvent(timeoutMs: Int): Array<String>?
    external fun nativeExit()
}
```

### 3.4 Android 端工程结构

```
app/
 ├─ src/main/
 │   ├─ cpp/               # JNI + 预编译的 libgphoto2 .so（或 CMake 源码编译）
 │   ├─ java/com/example/nikontransfer/
 │   │   ├─ GPhoto2Bridge.kt        # JNI 声明
 │   │   ├─ service/CameraService.kt# 前台服务：连接保活 + 事件循环
 │   │   ├─ data/PhotoRepository.kt  # Room 记录 + MediaStore 写入
 │   │   └─ ui/                      # Compose 界面
 │   └─ jniLibs/arm64-v8a/  # libgphoto2.so, libgphoto2_port.so, libxml2.so ...
```

### 3.5 关键功能流程

**自动传图（监听拍摄）：**
1. 前台 Service 建立 PTP/IP 连接。
2. 循环调用 `gp_camera_wait_for_event(camera, 1000, ...)`。
3. 收到 `GP_EVENT_FILE_ADDED` → 取 CameraFilePath → `gp_camera_file_get` 下载 → 写 MediaStore → Room 记录 → 发通知。
4. 尼康在会话空闲一段时间会断开，事件轮询本身就是心跳；如仍掉线，可定时 `gp_camera_get_summary` 保活，并做自动重连。

**手动浏览下载：** `gp_camera_folder_list_folders` → `list_files` → 按需 `file_get`（缩略图可用 `GP_FILE_TYPE_PREVIEW` 拿 M 索览图，速度快很多）。

**拍照控制（可选加分项）：** `gp_camera_capture(camera, GP_CAPTURE_IMAGE, &path, ctx)`，配合 `gp_widget` 树可调光圈/快门/ISO（ptp2 camlib 支持的尼康配置项很多）。

---

## 四、Android 侧必须处理的平台细节（坑清单）

0. **Android 17 (API 37) 的本地网络权限（重要）**：target API 37 时系统默认阻止本地网络访问，手机连相机 Wi-Fi 传图必须声明并请求 `ACCESS_LOCAL_NETWORK` 运行时权限，否则 PTP/IP 连接会被系统拦截。SDK 配置建议：compileSdk/targetSdk = 37（Play 商店 2027-08 起强制 target 37），minSdk = 28 与 NDK `ANDROID_PLATFORM=android-28`（iconv 需要）保持独立设置。

1. **网络绑定（最重要的坑）**：手机连上相机 Wi-Fi 后没有外网，Android 默认会把流量走路由/移动数据。必须：
   ```kotlin
   val cm = getSystemService(ConnectivityManager::class.java)
   val net = cm.allNetworks.first { /* capability NOT internet & transport WIFI */ }
   cm.bindProcessToNetwork(net)          // 或对每个 socket 调 network.bindSocket()
   ```
2. **前台 Service + WifiLock**：相机热点容易因省电断开；`startForeground` + `WifiManager.WifiLock`（Wi-Fi 模式）+ 电池白名单引导。
3. **分区存储**：Android 10+ 用 MediaStore.Downloads/Images API 保存，NEF 原片建议存到 App 专属目录或让用户 SAF 选目录。
4. **NEF/JPEG 过滤**：尼康「拍摄后自动传输」通常只推 JPEG；`ObjectAdded` 事件对 NEF 的行为因机型而异，浏览下载 NEF 走列目录更稳。
5. **大文件与进度**：NEF + 4K 视频很大，JNI 里用 `gp_context_set_progress_funcs` 注册进度回调 → 回调 Java 层更新通知栏。
6. **LGPL 合规**：libgphoto2 是 LGPL-2.1+（部分 camlib GPL）。以动态链接 `.so` 方式分发、在 About 页声明来源与授权即可；若静态链接需提供目标文件以便用户重链接。

---

## 五、里程碑计划（建议节奏）

| 阶段 | 内容 | 验收标准 |
|------|------|----------|
| M1（1~2 周） | 交叉编译 libgphoto2 (ptp2+ptpip)，在手机上跑一个 native CLI demo | adb shell 里执行二进制，能连相机并列出文件 |
| M2（1~2 周） | JNI 封装 + 最小 App：连接、列目录、下载单张、显示 | 能把一张 JPEG 从相机弄进手机相册 |
| M3（2 周） | 前台 Service + 事件循环自动下载 + 通知 + Room 图库 | 拍一张 → 手机自动收到一张 |
| M4（1~2 周） | 浏览/多选/缩略图、NEF 下载、断线重连、进度通知 | 现场真实拍摄流程可用 |
| M5（可选） | 拍照控制（光圈/快门/ISO/实时取景）或 USB 路线 | — |

---

## 六、路线②：USB 连接方案（二期，难点说明）

USB 在非 root Android 上不能直接访问 `/dev/bus/usb`，做法：

1. **Kotlin 层拿 fd**：`UsbManager.requestPermission(device)` → `usbDeviceConnection.getFileDescriptor()`。
2. **libusb 包装 fd**：libusb ≥ 1.0.23 专门为 Android 加了
   ```c
   libusb_wrap_sys_device(ctx, fd, &devh);   // 把 Java 层的 fd 包成 libusb 设备句柄
   ```
3. **libgphoto2 的 usb 端口驱动默认走 libusb 枚举**，在 Android 上枚举会失败 —— 这是 USB 路线最大的工程量所在，两种解法：
   - 给 libgphoto2 写一个自定义 port driver（如 `usbfd:`），直接接收已 wrap 的 `libusb_device_handle`（社区有类似补丁思路可参考）；
   - 或者干脆不走 libgphoto2 的 USB 端口：**Kotlin 直接用 `UsbDeviceConnection.bulkTransfer` 实现 PTP 传输层**，把 libgphoto2 ptp2 camlib 的协议知识当参考（之前的调研里 Camera_Bridge 就是纯 Kotlin 实现 PTP/IP + MTP 的活例子）。
4. 需要 `USB host` 特性声明、`device_filter.xml` 匹配尼康 VID (0x04b0)，插线时弹权限框。

结论：**Wi-Fi 路线能覆盖 90% 的「传图」需求且工程量小得多，USB 留给需要高速批量拉 NEF/视频的场景再做。**

---

## 七、参考资料

- libgphoto2 官方源码与文档：https://github.com/gphoto/libgphoto2 （`examples/` 里有 sample-autodetect.c、sample-capture.c，JNI 层基本照抄改）
- PTP/IP 协议：ISO 15740 + PTP/IP 白皮书（port 15740）
- 可参考的同类开源实现（调研所得）：
  - RealCaCl2/ZTransfer — Flutter + Kotlin 实现 PTP/IP / PTP-USB 自动传图（尼康 Z 专用，协议层可对照）
  - Tauber01/ZENCHE — macOS 端用 libgphoto2 + libgphoto2_port 的真实工程案例
  - HuiLiYiImpl/Camera_Bridge — 纯 Kotlin 的尼康 PTP/IP + MTP 实现（不想碰 C 的话可以参考它绕开 libgphoto2）
- 尼康官方：SnapBridge 是闭源私有协议（BLE+Wi-Fi），不要试图仿它；走标准 PTP/IP（连 PC 模式）才有公开协议可依。
