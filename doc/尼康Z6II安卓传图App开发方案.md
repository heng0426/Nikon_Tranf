# 尼康 Z 6II → 安卓传图 App 开发方案（Windows 环境定制版）

> 目标机型：**Nikon Z 6II**（亚洲版）
> 开发环境：Windows 10/11
> 交付形态：安卓 App，支持 USB 与 Wi-Fi 双通道把照片/NEF 传入手机

---

## 0. Z 6II 能力盘点（方案的事实边界）

| 能力 | Z 6II 实际情况 | 对方案的影响 |
|---|---|---|
| USB | USB-C（SuperSpeed USB），**固定 PTP/MTP 协议，无大容量存储模式** | libgphoto2 ptp2 驱动、Android `MtpDevice` 均直接可用，无需相机端设置 |
| USB 实测速度 | PTP 传输约 20~35 MB/s | 50MB NEF 约 2 秒，主力通道 |
| Wi-Fi 频段 | **亚洲版仅 2.4GHz**（802.11b/g/n；5GHz a/ac 仅欧美版） | 无线实测约 1.5~3 MB/s，只适合少量/自动推送 |
| FTP 传输功能 | **固件 ≥1.30 新增**（建议升级到最新 1.4x） | 先升固件再动手 |
| FTP 工作模式 | **仅 STA**：相机作为客户端加入现有 Wi-Fi 网络 | 不存在"相机当热点推 FTP"（那是 Z 8/Z 9/Z 6III 的功能）；免路由器方案 = **手机开热点** |
| 加密 FTP | 不支持 FTPS/SFTP | 手机端跑纯 FTP，热点私网内可接受 |
| SnapBridge | 私有协议，未公开 | 自研 App 不可用，排除 |
| WT-7 外接发射器 | 支持，但价格不菲 | 不推荐，USB/Wi-Fi 已覆盖需求 |

**结论：推荐双通道架构，二者互补**

- **USB + libgphoto2**：浏览相机文件树、手动选中拉取、增量同步、（可选）遥控拍摄 —— 快、全功能
- **Wi-Fi FTP（手机热点 STA）**：拍摄现场免线自动推送新照片 —— 便携、无控制能力

> FTP 是"相机往外推"的单向模式，App 无法主动浏览/挑选相机里的文件；浏览与选图能力由 USB 通道补齐。两通道共用同一套落盘/去重/相册写入层。

---

## 1. 开发环境搭建（Windows）

| 组件 | 说明 |
|---|---|
| Windows 10/11 + Android Studio（最新稳定版） | 装 SDK、NDK r26+、CMake、Platform-Tools |
| **WSL2 + Ubuntu 22.04/24.04** | libgphoto2 是 autotools 构建，Windows 原生编译极痛苦；通行做法是 WSL2 里用 **Linux 版 NDK** 交叉编译出 `.so`，产物经 `/mnt/c/...` 直接给 Android Studio |
| 交叉编译源码包 | `libtool 2.4.7`（提供 libltdl，必须）、`libusb ≥1.0.27`（必须，自带 Android backend）、`libgphoto2 ≥2.5.30` |
| 测试硬件 | 支持 OTG 的安卓真机（模拟器测不了 USB）+ USB-OTG 线 + Z 6II |

---

## 2. 通道一：USB + libgphoto2

### 2.1 架构

```
Kotlin App（浏览/下载/保存）
   ↓ JNI（自己写的 gphoto-jni.so）
libgphoto2 + ptp2 驱动（Z 6_2 在支持列表）
   ↓
libusb（自带 Android backend）
   ↓
Android USB Host API（UsbManager 授权放行）
   ↓
Z 6II（USB 固定 PTP，插线即用）
```

### 2.2 交叉编译（WSL2 内执行，顺序固定）

参考社区项目 `qh7574/Lut2Photo-Android` 的 `build_libgphoto2.sh`，要点：

```bash
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
export CC="$TOOLCHAIN/bin/aarch64-linux-android31-clang"   # arm64-v8a, API 31
export AR=llvm-ar RANLIB=llvm-ranlib STRIP=llvm-strip

# 1) libtool：提供 libltdl（libgphoto2 运行时加载驱动必须）
./configure --host=aarch64-linux-android --enable-shared --enable-ltdl-install

# 2) libusb：必须关掉 udev
./configure --host=aarch64-linux-android --disable-udev --enable-shared

# 3) libgphoto2：只编 ptp2 驱动（尼康走 PTP），体积和坑都最小
./configure --host=aarch64-linux-android --with-camlibs=ptp2 \
  --enable-shared --disable-static

# make install 产出：libgphoto2.so / libgphoto2_port.so / libusb.so / libltdl.so
# 以及 camlibs/ptp2.so、iolibs/*.so（运行时 dlopen 的驱动模块）
```

全部 `.so` 拷入 `app/src/main/jniLibs/arm64-v8a/`（只发应用商店一个 arm64 ABI 即可）。

### 2.3 USB 权限（Java/Kotlin 侧，先于任何 gphoto 调用）

- Manifest：`<uses-feature android:name="android.hardware.usb.host" />`，注册 `USB_DEVICE_ATTACHED` intent-filter（插线自动唤起 App）
- `UsbManager.requestPermission()` 弹授权；授权后 Android 放行 `/dev/bus/usb` 设备节点，libusb 1.0.27 的 Android backend 可直接打开，无需早期移植项目的 `libusb_open2` 补丁

### 2.4 JNI 核心调用序列

```c
gp_camera_new(&camera);
gp_context_new();                 // 设超时 30s+、注册进度回调
gp_camera_init(camera, context);  // 独占会话，相机屏幕显示 USB 连接
gp_camera_folder_list_files(camera, "/store_00010001/DCIM/...", list, context);
gp_camera_file_get_info(...);     // 文件大小
gp_camera_file_get(camera, folder, name, GP_FILE_TYPE_NORMAL, file, context);
                                  // 进度回调 → JNI 回调 Kotlin 刷 UI
gp_camera_exit / gp_camera_free;
```

### 2.5 已知坑

1. **camlib 动态加载是最大的坑**：libgphoto2 运行时从编译期固定路径 dlopen `camlibs/ptp2.so`，Android 上没有该路径。解法：驱动 so 一起打进 jniLibs，并小改 libgphoto2 一两行，让搜索路径从环境变量读取，初始化前 `setenv` 指向 `applicationInfo.nativeLibraryDir`（Lut2Photo-Android 有现成处理）
2. **传输中把相机自动关机调到最长**，或 JNI 层定期发保活命令
3. **别和系统抢 USB**：授权给你的 App 后，不要让系统弹出的「文件传输/MTP」接管设备
4. **许可证**：libgphoto2 是 LGPL 2.1 —— 动态链接商用 OK，但对库的修改（上面那个路径补丁）必须开源；不要静态链进闭源 App
5. **先用电脑验证**：PC 上 `gphoto2 --list-files / --get-file xxx.NEF` 验证 Z 6II 一切正常，再动手移植

### 2.6 Plan B：系统自带 MtpDevice（如果暂时不需要遥控）

Z 6II 是标准 PTP/MTP 设备，纯 Kotlin 用 `android.mtp.MtpDevice` 就能列目录、拉文件，零 NDK 零第三方库，2~3 天出 demo。**建议先做这个跑通产品闭环，libgphoto2 作为二期升级**（遥控、参数读取、进度更细）。

---

## 3. 通道二：Wi-Fi 传图（Z 6II = 手机热点 STA 方案）

### 3.1 原理

Z 6II 固件 ≥1.30 内置 **FTP 客户端**：加入一个 Wi-Fi 网络后，把照片主动上传到指定 FTP 服务器。
**你的 App 就是这个 FTP 服务器** —— 在手机上监听 21 端口等相机来推即可，STA/AP 两种组网下手机端代码完全一致。

组网形态对比（Z 6II 可用的两种）：

| | 形态 A：路由器局域网 | 形态 B：**手机热点（推荐）** |
|---|---|---|
| 谁当 AP | 路由器 | **手机**（相机作为 STA 加入手机热点） |
| 需要的硬件 | 路由器 | 无，手机即可 |
| 手机 IP | 随 DHCP 变动，需静态绑定 | **固定为热点网关地址**（常见 192.168.x.1），配置一次永远有效 |
| 手机是否保留互联网 | 是 | 是（蜂窝同时可用） |
| 适用场景 | 家里/工作室批传 | 外拍现场自动推送 |

> 注意：**"相机自己当热点（AP 模式）推 FTP"在 Z 6II 上不存在**，该功能仅 Z 8/Z 9/Z 6III 有。想要免路由器的无线体验，就用形态 B（手机开热点），效果等价。
> 另：不建议用 `startLocalOnlyHotspot`（本地-only 热点）——它每次生成的 SSID/密码随机，相机配一次就失效，还得重配网；用系统「个人热点」保持固定名称密码。

### 3.2 手机端使用流程（App 内实现）

1. 打开系统「个人热点」；部分机型需把热点频段设为 **2.4GHz 兼容**（Z 6II 亚洲版只认 2.4GHz，若热点默认 5GHz-only 相机搜不到）
2. 打开 App → 启动前台服务 + FTP 服务器（监听 21，被动端口段 50000~50009）→ 界面大字显示：
   - 本机 IP（热点网关地址，如 `192.168.43.1`）
   - 账号/密码（建议 App 自动生成固定强密码）
   - 一段"抄到相机里"的配置说明
3. 等相机连接、收图、写相册、列表展示

### 3.3 相机端一次性配置（Z 6II）

1. 固件升级到 ≥1.30（建议最新 1.4x）：官网下载中心 → `F-Z6II-V140W.exe` → 解压出的 `Z6_2_0140.bin` 拷到卡根目录 → 相机内升级
2. 设定菜单 → **「FTP传输」**（入口名称随固件版本可能显示为「无线传输器(Wi-Fi)」下的子项）：
   - **网络设定** → 创建配置文件 → 搜索 Wi-Fi 网络 → 选手机热点 → 输入密码 → IP 自动获取
   - **FTP 服务器设定**：服务器地址 = 手机 IP（如 `192.168.43.1`），端口 21，用户/密码按 App 显示填写，**被动模式开启**
3. 上传方式二选一（可并存）：
   - 拍摄菜单/网络菜单里开启 **「自动上传」**：边拍边推
   - 回放照片按 `i` → **「选择以上传」**：手动挑图推

> 日常使用只需：手机开热点 → 开 App → 相机开机自动连上自动推。相机配置文件会记住网络和服务器。
> 2.4GHz 实测 1.5~3 MB/s：24MB 的 JPEG 约 10 秒，50MB NEF 约 20~35 秒。NEF 全卡回传请走 USB。

### 3.4 App 端 FTP 服务器实现

**依赖**（Apache 2.0 许可，商用安全；勿用 GPL 的 swiFTP）：

```gradle
implementation("org.apache.ftpserver:ftpserver-core:1.1.1")
implementation("org.slf4j:slf4j-android:1.7.36")
```

**核心代码**：

```kotlin
fun startFtpServer(port: Int = 21): FtpServer {
    val factory = FtpServerFactory()
    factory.addListener("default", ListenerFactory().apply {
        setPort(port)   // Android 绑定 1024 以下端口无需 root
        dataConnectionConfiguration = DataConnectionConfigurationFactory().apply {
            passivePorts = "50000-50009"
            activeEnabled = false
            passiveEnabled = true   // 相机端"被动模式"必须开，与此对应
        }.createDataConnectionConfiguration()
    }.createListener())

    val userManager = PropertiesUserManagerFactory().createUserManager()
    userManager.save(BaseUser().apply {
        name = "nikon"
        password = prefs.ftpPassword()          // App 生成并固定
        homeDirectory = filesDir.resolve("ftp_inbox").absolutePath
    })
    factory.userManager = userManager
    factory.ftplets = mapOf("hook" to NikonFtplet())   // 见下

    return factory.createServer().also { it.start() }
}
```

**上传完成 → 写入系统相册**（Ftplet 钩子 + MediaStore，免存储权限）：

```kotlin
class NikonFtplet : DefaultFtplet() {
    override fun afterCommand(session: FtpSession, request: FtpRequest, reply: FtpReply): FtpletResult {
        if (request.command.equals("STOR", true)) {
            val file = File(session.user.homeDirectory, session.requestArgument ?: return FtpletResult.SKIP)
            saveToGallery(file)
        }
        return FtpletResult.DEFAULT
    }
}

fun saveToGallery(file: File) {
    val mime = when (file.extension.lowercase()) {
        "nef" -> "image/x-nikon-nef"
        "mp4", "mov" -> "video/mp4"
        else -> "image/jpeg"
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
        put(MediaStore.Images.Media.MIME_TYPE, mime)
        put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/NikonZ6II")
    }
    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
    contentResolver.openOutputStream(uri)!!.use { it.write(file.readBytes()) }
    file.delete()   // 转存后清掉中转文件
}
```

**显示本机 IP（热点网关地址）**：

```kotlin
fun localIpv4(): List<String> =
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .map { it.hostAddress!! }
        .toList()
// 热点场景下 App 应优先展示热点网段的网关地址（通常 192.168.x.1）
```

**保活三件套**：

- **前台服务**（`foregroundServiceType="dataSync"`）承载 FTP 服务器，锁屏不断
- **Wi-Fi 锁**：`wifiManager.createWifiLock(WIFI_MODE_FULL_HIGH_PERF, "nikonFtp").acquire()`
- **WakeLock（PARTIAL）**：批量上传期间防止休眠

**去重**：相机断线重连会重传未完成文件，按 `文件名+大小` 记录已入库集合，重复到达直接丢弃/覆盖。

### 3.5 开发期测试技巧（不用等相机就能调 App）

在 PC 上装 FileZilla，连到手机的 FTP 服务器手动拖文件进去，模拟相机推送，把接收链路（Ftplet→MediaStore→列表）全部调通后，再接相机联调。

---

## 4. 里程碑

| 阶段 | 内容 | 预估 |
|---|---|---|
| D1~2 | WSL2 三库交叉编译通过（最大不确定项）；PC 上 gphoto2 命令行验证 Z 6II | 2 天 |
| D3~4 | USB demo：授权 → `gp_camera_init` → 列 DCIM → 单图下载带进度 | 2 天 |
| D5 | App 内 FTP 服务器 + MediaStore 落盘（FileZilla 模拟推送调试） | 1 天 |
| D6 | 相机固件升级、配网（手机热点 STA）、FTP 打通 → 自动上传演示 | 1 天 |
| D7+ | 前台服务、增量去重、断线重试、双通道 UI 整合、真机兼容测试 | 持续 |

---

## 5. 许可证与合规速查

| 组件 | 许可证 | 注意 |
|---|---|---|
| libgphoto2 | LGPL 2.1 | 动态链接商用 OK；**对库的修改必须开源**（camlib 路径补丁） |
| libusb / libtool | LGPL 2.1 | 同上 |
| Apache MINA FtpServer | Apache 2.0 | 商用无忧 |
| swiFTP | GPL | 不要用 |

## 6. 参考项目

- `thebino/libgphoto2android`（JNI 包装参考）
- `qh7574/Lut2Photo-Android`（libgphoto2 Android 交叉编译脚本，含 camlib 路径补丁思路）
- `libusb/libusb`（android 目录自带构建说明）
- 尼康官方：Z 6II 固件下载（downloadcenter.nikonimglib.com）、Z 6II 参考手册「网络连接」章节
