# 尼康 Z 6II 传图 App 开发实施细则（STA 优先版)

> 配套文档：《尼康Z6II安卓传图App开发方案.md》（架构决策）
> 本文是实施层：所有依赖的安装命令、配置片段、代码骨架、调试步骤，按**STA 优先**的顺序组织。

## 开发顺序总览

```
Phase 0  环境安装（Windows + WSL2，一次装齐）        ← 半天
Phase 1  STA 通道：手机热点 + App 内置 FTP 服务器     ← 优先开发，纯 Windows，2~3 天出可用版本
Phase 2  USB 通道：WSL2 交叉编译 libgphoto2 + JNI     ← 3~5 天
Phase 3  双通道整合：统一落盘层 + 增量同步 + 打磨
```

**为什么 STA 优先**：零交叉编译依赖、零第三方 native 库、不需要 OTG 线和相机固件升级之外的任何硬件条件，而且开发期可以用 PC 上的 FileZilla 完全模拟相机推送——App 侧链路今天就能开始调。

---

# Phase 0：环境安装（一次装齐）

## 0.1 Windows 侧（Phase 1 立即需要）

1. **JDK 17+**（Android Studio 自带 jbr，也可单独装 Temurin 17 备用）
2. **Android Studio**（官网最新稳定版），首次启动时在 SDK Manager 勾选安装：
   - Android SDK Platform：**API 35**（compileSdk/target）
   - SDK Platform-Tools、SDK Build-Tools（默认最新）
   - （Phase 2 才需要：NDK r27+、CMake——可以先不装）
3. 模拟器可装可不装（USB/热点都要真机，模拟器仅用于写 UI）
4. 真机：手机开启「开发者选项 → USB 调试」

## 0.2 WSL2 侧（Phase 2 前装好即可，可与 Phase 1 并行）

管理员 PowerShell：

```powershell
wsl --install -d Ubuntu-22.04
# 重启后进入 Ubuntu 设置用户名密码
```

Ubuntu 内安装编译工具链：

```bash
sudo apt update
sudo apt install -y build-essential autoconf automake libtool pkg-config \
  wget bzip2 xz-utils unzip python3 make cmake
```

下载 **Linux 版** NDK（注意：不是 Windows 版，交叉编译要在 WSL 里用 Linux 工具链）：

```bash
mkdir -p ~/Android && cd ~/Android
wget https://dl.google.com/android/repository/android-ndk-r27c-linux.zip
unzip android-ndk-r27c-linux.zip
# 产物路径：~/Android/android-ndk-r27c
```

（可选，Phase 2 调试用）把相机 USB 设备转发进 WSL：

```powershell
winget install usbipd
usbipd list                      # 找到尼康相机的 BUSID
usbipd bind --busid <BUSID>
usbipd attach --wsl --busid <BUSID>
```

WSL 内再装 `sudo apt install -y gphoto2 libgphoto2-dev`，即可在 WSL 里直接用 `gphoto2 --list-files` 验证相机（不用装 Windows 版 gphoto2，Windows 上编译/运行它非常痛苦）。

---

# Phase 1：STA 通道（优先开发）

## 1.1 目标链路

```
手机开个人热点(2.4GHz) ← Z 6II 作为 STA 加入
        ↓ 相机内置 FTP 客户端主动推送
App 内置 FTP 服务器(前台服务, 21 端口 + PASV 50000-50009)
        ↓ Ftplet 上传完成钩子
MediaStore 写入相册 DCIM/NikonZ6II → 删除中转文件 → UI 刷新列表
```

## 1.2 建工程

Android Studio → New Project → **Empty Activity**（Compose 模板）

- Name: `NikonTransfer`；Language: Kotlin；Min SDK: **29**（Android 10，MediaStore scoped storage 起点线）
- Package: `com.yourname.nikontransfer`
- 用 Groovy DSL（`build.gradle.kts`）如下

`gradle/libs.versions.toml`：

```toml
[versions]
ftpserver = "1.1.1"
slf4j = "1.7.36"

[libraries]
ftpserver-core = { group = "org.apache.ftpserver", name = "ftpserver-core", version.ref = "ftpserver" }
slf4j-android = { group = "org.slf4j", name = "slf4j-android", version.ref = "slf4j" }
```

`app/build.gradle.kts` 关键行：

```kotlin
android {
    compileSdk = 35
    defaultConfig {
        applicationId = "com.yourname.nikontransfer"
        minSdk = 29
        targetSdk = 35
    }
}

dependencies {
    implementation(libs.ftpserver.core)   // Apache MINA FtpServer, Apache-2.0, 商用安全
    implementation(libs.slf4j.android)     // MINA 的日志门面
}
```

> 许可证提醒：**不要**引入 swiFTP/任何 GPL 的 FTP 实现。MINA 是 Apache 2.0。

## 1.3 AndroidManifest.xml（完整配置）

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application ...>
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".ftp.FtpServerService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />
    </application>
</manifest>
```

## 1.4 目录结构

```
app/src/main/java/com/yourname/nikontransfer/
├── MainActivity.kt            # UI：服务器开关、IP/账号展示、收图列表
├── ftp/
│   ├── FtpServerService.kt    # 前台服务：生命周期 + WifiLock + WakeLock
│   ├── FtpServerEngine.kt     # MINA 装配：监听器/用户/被动端口/Ftplet
│   └── UploadFtplet.kt        # STOR 完成钩子 → 交给 GalleryWriter
├── media/
│   └── GalleryWriter.kt       # MediaStore 落盘 + 去重
└── util/
    └── NetUtils.kt            # 本机 IPv4 枚举/热点网关推断
```

## 1.5 核心代码

### FtpServerService.kt（前台服务 + 保活三件套）

```kotlin
class FtpServerService : Service() {

    private var server: FtpServer? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("FTP 服务器已启动"))
        acquireLocks()
        server = FtpServerEngine(this).start()   // 见下
    }

    private fun acquireLocks() {
        wifiLock = (getSystemService(WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "nikonFtp").apply { acquire() }
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nikonFtp:keep").apply { acquire(2 * 60 * 60 * 1000L) }
    }

    override fun onDestroy() {
        server?.stop()
        wifiLock?.release(); wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        fun start(ctx: Context) = ctx.startForegroundService(Intent(ctx, FtpServerService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, FtpServerService::class.java))
    }
}
```

### FtpServerEngine.kt（MINA 装配）

```kotlin
class FtpServerEngine(private val context: Context) {

    fun start(): FtpServer {
        val factory = FtpServerFactory()

        factory.addListener("default", ListenerFactory().apply {
            port = 21   // Android 应用可绑定 1024 以下端口，无需 root
            dataConnectionConfig = DataConnectionConfigurationFactory().apply {
                passivePorts = "50000-50009"   // 相机端"被动模式"对应
                isPassiveEnabled = true
                isActiveEnabled = false        // 相机在私网内，主动模式会失败
            }.createDataConnectionConfiguration()
        }.createListener())

        val users = PropertiesUserManagerFactory().createUserManager()
        users.save(BaseUser().apply {
            name = "nikon"
            password = Prefs.ftpPassword(context)   // 首次生成后固定存 SharedPreferences
            homeDirectory = File(context.filesDir, "ftp_inbox").apply { mkdirs() }.absolutePath
        })
        factory.userManager = users

        factory.ftplets = mapOf("upload" to UploadFtplet(context))

        return factory.createServer().also { it.start() }
    }
}
```

> 相机只能用明文 FTP（Z 6II 不支持 FTPS），热点私网内可接受；不要暴露公网。

### UploadFtplet.kt（上传完成钩子）

```kotlin
class UploadFtplet(private val context: Context) : DefaultFtplet() {

    override fun afterCommand(
        session: FtpSession, request: FtpRequest, reply: FtpReply
    ): FtpletResult {
        if (request.command.equals("STOR", true) && reply.code == 226) {
            val name = session.argument ?: return FtpletResult.DEFAULT
            val file = File(File(context.filesDir, "ftp_inbox"), name)
            GalleryWriter(context).importFrom(file)   // 落相册 + 去重 + 删中转
        }
        return FtpletResult.DEFAULT
    }
}
```

### GalleryWriter.kt（MediaStore 落盘 + 去重）

```kotlin
class GalleryWriter(private val context: Context) {

    private val prefs = context.getSharedPreferences("dedup", MODE_PRIVATE)

    fun importFrom(file: File) {
        val key = "${file.name}:${file.length()}"      // 相机重连会重传，按 名+大小 去重
        if (prefs.getBoolean(key, false)) { file.delete(); return }

        val mime = when (file.extension.lowercase()) {
            "nef"  -> "image/x-nikon-nef"
            "mp4", "mov" -> "video/mp4"
            else   -> "image/jpeg"
        }
        val uri = context.contentResolver.insert(
            MediaStore.Files.getContentUri("external"),
            ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, file.name)
                put(MediaStore.Files.FileColumns.MIME_TYPE, mime)
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, "DCIM/NikonZ6II")
            }
        ) ?: return

        context.contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out, 1024 * 512) }
        }
        prefs.edit().putBoolean(key, true).apply()
        file.delete()
    }
}
```

### NetUtils.kt（热点网关推断）

```kotlin
object NetUtils {
    fun ipv4List(): List<String> =
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress!! }
            .toList()

    /** 热点场景：网关地址通常形如 192.168.x.1，优先展示给用户抄进相机 */
    fun bestHotspotIp(): String =
        ipv4List().firstOrNull { it.matches(Regex("""192\.168\.\d+\.1""")) }
            ?: ipv4List().firstOrNull { !it.startsWith("127.") }
            ?: "请先开启个人热点"
}
```

> 注意：各厂商热点网段不同（常见 `192.168.43.1`，部分机型用 `192.168.x.1` 其他段），少数用其他私网段。UI 上把 `ipv4List()` 全量列出并高亮 `bestHotspotIp()`，用户抄错一个数字就连不上——宁可多显示。

### MainActivity（UI 要点，Compose）

- 大字卡片：`热点 IP`（`NetUtils.bestHotspotIp()`）、端口 `21`、用户 `nikon`、密码（含复制按钮）
- 服务器开关 → `FtpServerService.start/stop`
- 运行时权限：Android 13+ 启动时请求 `POST_NOTIFICATIONS`
- 收图列表：监听 `ftp_inbox` 目录 + MediaStore 查询 `DCIM/NikonZ6II`
- 附一段引导文案：「请确认手机热点已开启且频段为 2.4GHz；在相机 设定菜单→FTP传输 中按以下信息配置」

## 1.6 开发期自测（不等相机）

**第 1 步：本机环回测试**——App 启动 FTP 服务后，用手机上的任意 FTP 客户端（如 AndFTP）连 `127.0.0.1:21` 传文件，验证 STOR → Ftplet → MediaStore 全链路。

**第 2 步：FileZilla 模拟相机**——PC 装 FileZilla（普通 FTP、被动模式），手机和 PC 连同一个 Wi-Fi，连 `NetUtils.bestHotspotIp()` 显示的地址。能传通，说明服务器侧完全就绪。Windows 防火墙首次会弹窗，放行即可。

**第 3 步：真相机联调**（见下）。

## 1.7 相机端配置（Z 6II，一次性）

1. **固件升级到 ≥1.30**（建议最新）：
   - 尼康下载中心 → `F-Z6II-V140W.exe` → 运行解压出 `Z6_2_0140.bin`
   - 文件拷贝到存储卡**根目录** → 相机：设定菜单 → 固件版本 → 升级
   - （先看「设定菜单 → 固件版本」确认当前版本）
2. **手机热点设置**：
   - 设置 → 个人热点 → 开启
   - 热点 AP 频段设为 **2.4GHz**（Z 6II 亚洲版只认 2.4GHz；若手机热点默认 5GHz-only，相机搜不到）
   - 热点名称和密码**固定**（别用「随机化热点名」功能，否则相机配置文件失效）
3. **相机网络配置文件**：
   - 设定菜单 → FTP 传输 → 网络设定 → 创建配置文件 → 搜索 Wi-Fi 网络 → 选手机热点 → 输密码 → IP 自动获取（DHCP）
4. **相机 FTP 服务器配置**：
   - FTP 服务器设定 → 地址 = App 显示的 IP；端口 21；用户名/密码按 App 显示；**被动模式开启**
5. **上传方式**：
   - 边拍边推：菜单里开启「自动上传」
   - 手动挑图：回放 → 选照片 → 按 `i` → 「选择以上传」

> 日常使用只有两步：手机开热点 → 相机开机，自动连上自动推。
> 速度预期（2.4GHz）：约 1.5~3 MB/s，24MB JPEG 约 10 秒，50MB NEF 约 20~35 秒。整卡回传请用 Phase 2 的 USB 通道。

## 1.8 STA 通道踩坑清单

| 症状 | 原因与解法 |
|---|---|
| 相机搜不到热点 | 热点是 5GHz-only → 改 2.4GHz |
| 相机连上但传不动 | 手机休眠杀服务 → 前台服务 + 引导用户关闭电池优化（`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`） |
| 连接成功后卡在 LIST/RETR | 被动端口段没开或被 Doze 拦 → 确认 50000-50009 + WifiLock |
| 换了台手机 IP 变了 | 热点网段因厂商而异 → App 每次启动都显示当前 IP 让用户核对 |
| 重传重复照片 | 正常，相机断点续传策略保守 → GalleryWriter 名+大小去重已处理 |
| 部分机型热点无客户端隔离、部分有 | 若有隔离则连不通，换厂商测试；主流机型（小米/三星/Pixel）无隔离 |

---

# Phase 2：USB 通道（libgphoto2）

## 2.1 前置验证（强烈建议先做）

用 0.2 节的 usbipd 把相机挂进 WSL，跑：

```bash
gphoto2 --auto-detect          # 应列出 Nikon Z 6_2
gphoto2 --list-files           # 列出 DCIM
gphoto2 --get-file 1000045.NEF # 拉一张验证
```

PC 上一切正常，再开始移植——**不兼容即黑盒**，先排除机型/固件问题。

## 2.2 交叉编译脚本（WSL2 内）

`build_libgphoto2.sh`（可直接运行，参考社区脚本改写）：

```bash
#!/bin/bash
set -e

NDK_PATH="${NDK_PATH:-$HOME/Android/android-ndk-r27c}"
API_LEVEL=31
ABI="arm64-v8a"
TOOLCHAIN="${NDK_PATH}/toolchains/llvm/prebuilt/linux-x86_64"
TARGET="aarch64-linux-android"
BUILD_DIR="$(pwd)/build_native"
PREFIX="${BUILD_DIR}/install/${ABI}"

LIBTOOL_VERSION="2.4.7"
LIBUSB_VERSION="1.0.27"
LIBGPHOTO2_VERSION="2.5.31"   # 以 https://github.com/gphoto/libgphoto2/releases 最新 2.5.x 为准

mkdir -p "${BUILD_DIR}/src"

export AR="${TOOLCHAIN}/bin/llvm-ar"
export CC="${TOOLCHAIN}/bin/${TARGET}${API_LEVEL}-clang"
export CXX="${TOOLCHAIN}/bin/${TARGET}${API_LEVEL}-clang++"
export RANLIB="${TOOLCHAIN}/bin/llvm-ranlib"
export STRIP="${TOOLCHAIN}/bin/llvm-strip"
export NM="${TOOLCHAIN}/bin/llvm-nm"
export CFLAGS="-fPIC -ffunction-sections -funwind-tables -fstack-protector-strong -O2 -DANDROID -DNDEBUG"
export LDFLAGS="-L${PREFIX}/lib -llog"
export PKG_CONFIG_PATH="${PREFIX}/lib/pkgconfig"

cd "${BUILD_DIR}/src"

# ---- 1) libtool（提供 libltdl：libgphoto2 运行时 dlopen 驱动必需）
[ -f "libtool-${LIBTOOL_VERSION}.tar.gz" ] || wget "https://ftp.gnu.org/gnu/libtool/libtool-${LIBTOOL_VERSION}.tar.gz"
[ -d "libtool-${LIBTOOL_VERSION}" ] || tar -xzf "libtool-${LIBTOOL_VERSION}.tar.gz"
cd "libtool-${LIBTOOL_VERSION}" && ./configure --host=${TARGET} --prefix="${PREFIX}" \
  --enable-shared --disable-static --enable-ltdl-install && make -j$(nproc) && make install && cd ..

# ---- 2) libusb（必须关 udev；1.0.27 起自带 Android backend）
[ -f "libusb-${LIBUSB_VERSION}.tar.bz2" ] || wget "https://github.com/libusb/libusb/releases/download/v${LIBUSB_VERSION}/libusb-${LIBUSB_VERSION}.tar.bz2"
[ -d "libusb-${LIBUSB_VERSION}" ] || tar -xjf "libusb-${LIBUSB_VERSION}.tar.bz2"
cd "libusb-${LIBUSB_VERSION}" && ./configure --host=${TARGET} --prefix="${PREFIX}" \
  --enable-shared --disable-static --disable-udev && make -j$(nproc) && make install && cd ..

# ---- 3) libgphoto2（只编 ptp2 驱动：尼康全部走 PTP）
[ -f "libgphoto2-${LIBGPHOTO2_VERSION}.tar.gz" ] || wget "https://github.com/gphoto/libgphoto2/releases/download/v${LIBGPHOTO2_VERSION}/libgphoto2-${LIBGPHOTO2_VERSION}.tar.gz"
[ -d "libgphoto2-${LIBGPHOTO2_VERSION}" ] || tar -xzf "libgphoto2-${LIBGPHOTO2_VERSION}.tar.gz"
cd "libgphoto2-${LIBGPHOTO2_VERSION}" && ./configure --host=${TARGET} --prefix="${PREFIX}" \
  --with-camlibs=ptp2 --enable-shared --disable-static && make -j$(nproc) && make install && cd ..

echo "产物在 ${PREFIX}/lib —— 打包拷走"
```

## 2.3 camlib 搜索路径补丁（本项目最大的坑）

libgphoto2 编译时把驱动目录写死（如 `$(PREFIX)/lib/libgphoto2/.../camlibs`），Android 上没有该路径，运行时 dlopen `ptp2.so` 必失败。

**解法**（参考 Lut2Photo-Android 的做法）：改 libgphoto2 源码中拼 camlib/iolib 路径的 1~2 处，使其优先读环境变量 `GPHOTO2_CAMLIB_DIR` / `GPHOTO2_IOLIB_DIR`；然后：

```kotlin
// JNI_OnLoad 或 init 前设置（Kotlin 侧也可用 Os.setenv）
val nativeDir = applicationInfo.nativeLibraryDir
setenv("GPHOTO2_CAMLIB_DIR", "$nativeDir", 1)   // ptp2.so 已按 jniLibs 规则平铺在 nativeLibraryDir
setenv("GPHOTO2_IOLIB_DIR", "$nativeDir", 1)
```

> LGPL 合规：该补丁必须随 App 开源发布（单独公布 patch 文件即可满足），库本身动态链接、不修改闭源 App 代码。

## 2.4 集成进 App

1. 把 `${PREFIX}/lib` 下所有 `.so`（`libgphoto2.so`、`libgphoto2_port.so`、`libusb.so`、`libltdl.so`、驱动 `ptp2.so`、iolib 的 `libgphoto2_port_usb.so` 等）**平铺**拷入 `app/src/main/jniLibs/arm64-v8a/`（重命名冲突时注意保留）
2. 写 JNI 包装模块 `gphoto-jni`（`app/src/main/cpp/`）：

`CMakeLists.txt`：

```cmake
cmake_minimum_required(VERSION 3.22)
project(gphoto_jni)
add_library(gphoto_jni SHARED gphoto_jni.c)
target_link_libraries(gphoto_jni gphoto2 gphoto2_port log)
```

（prebuilt so 已在 jniLibs，CMake 直接按名链接即可；NDK 会合并进 APK）

`gphoto_jni.c` 核心骨架：

```c
#include <gphoto2/gphoto2-camera.h>
#include <gphoto2/gphoto2-file.h>
#include <jni.h>

static Camera *cam = NULL;
static GPContext *ctx = NULL;

JNIEXPORT jint JNICALL
Java_com_yourname_nikontransfer_usb_Gphoto_nativeInit(JNIEnv *env, jobject thiz,
        jstring camlibDir, jstring iolibDir) {
    const char *c = (*env)->GetStringUTFChars(env, camlibDir, NULL);
    const char *i = (*env)->GetStringUTFChars(env, iolibDir, NULL);
    setenv("GPHOTO2_CAMLIB_DIR", c, 1);
    setenv("GPHOTO2_IOLIB_DIR", i, 1);
    (*env)->ReleaseStringUTFChars(env, camlibDir, c);
    (*env)->ReleaseStringUTFChars(env, iolibDir, i);

    gp_camera_new(&cam);
    ctx = gp_context_new();
    gp_context_set_timeout(ctx, 30000);
    return gp_camera_init(cam, ctx);
}

JNIEXPORT jint JNICALL
Java_com_yourname_nikontransfer_usb_Gphoto_nativeListFiles(JNIEnv *env, jobject thiz,
        jstring folderPath, jobject resultArrayList) { /* gp_camera_folder_list_files → 回填 Java 列表 */ }

JNIEXPORT jint JNICALL
Java_com_yourname_nikontransfer_usb_Gphoto_nativeGetFile(JNIEnv *env, jobject thiz,
        jstring folder, jstring name, jstring destPath) {
    /* gp_camera_file_get_info → gp_camera_file_get → gp_file_save 到 destPath */
}

JNIEXPORT void JNICALL
Java_com_yourname_nikontransfer_usb_Gphoto_nativeExit(JNIEnv *env, jobject thiz) {
    if (cam) { gp_camera_exit(cam, ctx); gp_camera_free(cam); cam = NULL; }
    if (ctx) { gp_context_free(ctx); ctx = NULL; }
}
```

进度回调：`gp_context_set_progress_funcs` → JNI `CallVoidMethod` 回 Kotlin 的 Flow/StateFlow。

## 2.5 USB 权限（调用任何 gphoto 函数之前必须完成）

`res/xml/device_filter.xml`（尼康 USB Vendor ID = 0x04b0）：

```xml
<resources>
    <usb-device vendor-id="1200" />   <!-- 0x04B0 = 1200 (Nikon Corp.) -->
</resources>
```

Manifest 的 activity 内：

```xml
<intent-filter>
    <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
</intent-filter>
<meta-data android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
    android:resource="@xml/device_filter" />
```

Kotlin 授权流程：

```kotlin
private val usbReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (UsbManager.ACTION_USB_PERMISSION == intent.action) {
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted) startUsbSession()   // 授权后 libusb 即可打开 /dev/bus/usb 节点
        }
    }
}

fun requestUsbPermission(device: UsbDevice) {
    val pm = PendingIntent.getBroadcast(this, 0,
        Intent(ACTION_USB_PERMISSION).setPackage(packageName),
        PendingIntent.FLAG_MUTABLE)
    usbManager.requestPermission(device, pm)
}
```

> libusb 1.0.27 的 Android backend 在拿到用户授权后可直接打开设备，不需要早期移植项目的 `libusb_open2` 补丁路线。
> 注意：不要让系统弹出的「文件传输/MTP」程序接管设备——授权给你的 App 后插线即可，若系统默认弹 MTP 选择框，选「取消/仅充电」。

## 2.6 USB 通道踩坑清单

1. camlib 路径（见 2.3，必踩，提前有心理预期）
2. 传输中把相机「自动关机」调到最长，或在 JNI 层定期发保活 PTP 命令
3. `gp_camera_init` 是独占会话：调试时反复杀进程可能让相机卡在 USB 会话态，拔插一次线即可恢复
4. NEF 用 `GP_FILE_TYPE_NORMAL` 拉原文件即可；`GP_FILE_TYPE_PREVIEW` 可拉内嵌缩略图做网格预览（速度快 10 倍以上，适合浏览界面）

---

# Phase 3：双通道整合

- **统一落盘层**：USB 下载与 FTP 接收都走 `GalleryWriter`（同一次 MediaStore 写入 + 同一套去重键 `文件名:大小`，两通道互不重复入库）
- **增量同步**（USB）：本地记录已传清单，列出相机文件后 diff 出新照片，一键「同步全部」
- **UI**：主页两个入口卡（USB / Wi-Fi），Wi-Fi 卡复用 Phase 1，USB 卡做「浏览网格 → 多选 → 下载（进度条）」
- **遥控（可选加分项）**：libgphoto2 ptp2 驱动对尼康支持 `gp_camera_trigger_capture`、读取/设置参数、Liveview（`op 0x9206`）——二期再上

# 里程碑（按 STA 优先重排）

| 阶段 | 内容 | 预估 |
|---|---|---|
| **M1** | Phase 0 环境 + 工程创建 + FTP 服务器跑通 + FileZilla 全链路自测 | 1~2 天 |
| **M2** | 真机 + 热点 + 相机联调：自动上传打通 → **STA 通道交付可用** | 1 天 |
| **M3** | 去重/通知/电池优化引导/UI 打磨 → STA 通道打磨完成 | 1 天 |
| M4 | WSL2 三库编译 + camlib 补丁（可与 M1~M3 并行） | 1~2 天 |
| M5 | USB demo：授权 → init → 列 DCIM → 单图带进度 | 2 天 |
| M6 | 双通道整合 + 增量同步 + 真机兼容测试 | 2~3 天 |

# 参考项目

- `thebino/libgphoto2android` — JNI 包装参考
- `qh7574/Lut2Photo-Android` — libgphoto2 Android 交叉编译脚本与 camlib 补丁思路
- `libusb/libusb` — android 目录自带构建说明
- Apache MINA FtpServer — https://mina.apache.org/ftpserver-project/
- 尼康下载中心（固件）— downloadcenter.nikonimglib.com
