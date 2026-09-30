# Windows 开发详细方案 — 依赖安装与配置全流程

> 配套文档：《nikon-z-transfer-android-dev-plan.md》（总体方案）
> 本文档给出 Windows 上从零到"手机 App 连上相机下载第一张照片"的每一步命令与配置。
> 总体分工：**Android Studio**（App 开发）+ **MSYS2**（Windows 桌面原型验证）+ **WSL2**（交叉编译 libgphoto2）。

---

## 阶段〇：总览

| 工具 | 装在哪 | 用途 | 对应里程碑 |
|------|--------|------|-----------|
| Android Studio | Windows 本体 | App 工程、JNI、真机调试 | M2~M5 |
| MSYS2 (MINGW64) | Windows 本体 | 桌面 C 原型，先验证相机通信 | M0/M1 提前验证 |
| WSL2 (Ubuntu 24.04) | Windows 子系统 | 交叉编译出 Android 用的 .so | M1 |
| 真机 + 相机 | — | Wi-Fi PTP/IP 联调 | M2+ |

建议顺序：先装 Android Studio（后台下载时）→ 装 MSYS2 并跑通桌面原型 → WSL2 交叉编译 → 回 Android Studio 搭 JNI。

---

## 阶段一：Android Studio 基础环境（Windows 本体）

### 1.1 安装 Android Studio
1. 到 https://developer.android.com/studio 下载安装包（或用国内镜像站），一路默认安装。
2. 首次启动向导中选 **Standard** 安装类型，让它自动装好最新 Platform SDK、Platform-Tools（含 adb）、模拟器。
3. Android Studio 自带 JBR（JetBrains Runtime，相当于 JDK 17/21），Gradle 构建直接可用，**无需单独装 JDK**。

### 1.2 通过 SDK Manager 安装 NDK 与 CMake
打开 `Settings → Languages & Frameworks → Android SDK`：

- **SDK Platforms** 标签页：勾选 `Android 14 (API 34)` 和 `Android 15 (API 35)`。
- **SDK Tools** 标签页（勾选右下角 "Show Package Details" 以便选具体版本）：
  - `NDK (Side by side)` → 勾选 **29.x**（当前最新稳定版 29.0.14206865；27.3.13750724 为 LTS 也可用；r30 是 beta 不要选。注意 NDK 版本号（r29）与 API level（37）是两套编号，勿混淆）
  - `CMake` → 勾选 **4.1.2**（SDK Tools 里列出的 4.x 版本；3.22.1 等旧版也可用但建议新版）
  - `Android SDK Platform-Tools`（默认已装）
- 点 Apply 下载。

### 1.3 环境变量（可选但建议）
系统设置 → 环境变量 → 新建用户变量：
```
ANDROID_HOME = C:\Users\heng\AppData\Local\Android\Sdk
Path 中追加: %ANDROID_HOME%\platform-tools
```
验证：新开终端执行 `adb version` 能出版本号。

### 1.4 手机开启开发者模式
设置 → 关于手机 → 连点"版本号"7 次 → 开发者选项 → 打开 USB 调试 → 数据线连接后 `adb devices` 确认。真机要求 Android 8.0+（项目 minSdk 建议 26）。

---

## 阶段二：MSYS2 桌面原型（先在 Windows 上连上相机）

目的：不改任何 Android 代码，先写一个小 C 工具在 Windows 桌面上通过 Wi-Fi 连相机、列文件、下载照片，验证「你的相机 + 你的网络 + gphoto2 API 调用序列」都没问题。

### 2.1 安装 MSYS2
1. 到 https://www.msys2.org/ 下载 `msys2-x86_64-latest.exe`，装到默认路径 `C:\msys64`。
2. 从开始菜单打开 **MSYS2 MINGW64**（注意是 MINGW64，不是 MSYS）——后续命令都在这个 shell 里执行。
3. 先更新包数据库：
   ```bash
   pacman -Syu    # 可能要求关闭窗口重开，重开后继续
   pacman -Su
   ```

### 2.2 安装工具链与 libgphoto2
```bash
pacman -S --needed mingw-w64-x86_64-toolchain \
                   mingw-w64-x86_64-pkgconf \
                   mingw-w64-x86_64-libgphoto2
```
> 若包名不对，用 `pacman -Ss libgphoto2` 搜索实际名称。这条会自动带上 libusb 等依赖。

### 2.3 编写原型程序
新建 `C:\dev\gphoto-proto\test.c`（完整可编译版）：

```c
/* test.c — Windows 桌面原型：连接尼康 Z (Wi-Fi PTP/IP)，列出文件并下载一张 */
#include <gphoto2/gphoto2.h>
#include <stdio.h>
#include <stdlib.h>

static void err(GPContext *c, const char *fmt, va_list ap, void *d) {
    vfprintf(stderr, fmt, ap); fprintf(stderr, "\n");
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "用法: %s ptpip:192.168.1.66:15740\n", argv[0]); return 1; }

    GPContext *ctx = gp_context_new();
    gp_context_set_error_func(ctx, err, NULL);

    Camera *cam; gp_camera_new(&cam);
    GPPortInfo info; gp_port_info_new(&info);
    gp_port_info_set_type(info, GP_PORT_PTP);
    gp_port_info_set_path(info, argv[1]);
    gp_camera_set_port_info(cam, info);

    int ret = gp_camera_init(cam, ctx);
    if (ret < GP_OK) { fprintf(stderr, "gp_camera_init 失败: %d\n", ret); return 1; }
    printf("相机连接成功！\n");

    /* 列出根存储目录下的文件 */
    CameraList *list; gp_list_new(&list);
    const char *folder = "/store_00010001/DCIM";
    gp_camera_folder_list_folders(cam, folder, list, ctx);
    int n = gp_list_count(list);
    for (int i = 0; i < n; i++) {
        const char *name; gp_list_get_name(list, i, &name);
        printf("目录: %s/%s\n", folder, name);
    }
    /* 遍历第一个子目录并下载第一张 JPEG */
    if (n > 0) {
        const char *sub; gp_list_get_name(list, 0, &sub);
        char subpath[256]; snprintf(subpath, sizeof subpath, "%s/%s", folder, sub);
        CameraList *files; gp_list_new(&files);
        gp_camera_folder_list_files(cam, subpath, files, ctx);
        int fn = gp_list_count(files);
        printf("%s 共 %d 个文件\n", subpath, fn);
        for (int i = 0; i < fn; i++) {
            const char *fname; gp_list_get_name(files, i, &fname);
            printf("  文件: %s\n", fname);
            if (strstr(fname, ".JPG") || strstr(fname, ".jpg")) {
                CameraFile *f; gp_file_new(&f);
                gp_camera_file_get(cam, subpath, fname, GP_FILE_TYPE_NORMAL, f, ctx);
                char out[256]; snprintf(out, sizeof out, "dl_%s", fname);
                gp_file_save(f, out);
                printf("  已下载 -> %s\n", out);
                gp_file_free(f);
                break;
            }
        }
        gp_list_free(files);
    }
    gp_list_free(list);
    gp_camera_exit(cam, ctx);
    gp_camera_free(cam);
    gp_context_unref(ctx);
    return 0;
}
```

### 2.4 编译与运行
在 MINGW64 shell 中：
```bash
cd /c/dev/gphoto-proto
gcc test.c -o test.exe $(pkg-config --cflags --libs libgphoto2)
```

### 2.5 相机端与网络配置（关键）
1. 相机菜单：**网络 → 连接至 PC (Wi-Fi) → 建立连接**，选 AP（直连）或 STA（经路由器）均可；屏幕上会显示 **IP 地址**（如 `192.168.1.66`）。
2. 手机/电脑连入同一网络（STA 模式下电脑连路由器；AP 模式下电脑连相机热点）。
3. **Windows 防火墙**：第一次运行若被拦截，放行 `test.exe`；相机是服务端（监听 15740），电脑是客户端主动连接，出站默认放行，一般无需额外配置。
4. 运行：
   ```bash
   ./test.exe ptpip:192.168.1.66:15740
   ```
   预期输出相机连接成功并列出 DCIM 目录，下载一张 JPG 到当前目录。

### 2.6 原型阶段常见问题
| 现象 | 原因与解决 |
|------|-----------|
| `gp_camera_init` 超时 | 相机 Wi-Fi 已断开（空闲自动断）；重新在相机菜单建立连接后立即运行 |
| 连接被拒 | IP 不对（相机每次建立 Wi-Fi IP 可能变化）；ping 相机 IP 确认可达 |
| 能连上但列不出文件 | 相机还在「等待 PC 确认」状态，部分机型需在相机屏幕上按确定 |
| 测试一半断开 | 正常，尼康空闲会踢会话；正式 App 里靠事件轮询心跳 |

**这一步跑通了，说明你的调用序列 100% 正确，剩下的只是把同样的代码搬到 Android。**

---

## 阶段三：WSL2 交叉编译 libgphoto2（产出 Android 的 .so）

### 3.1 安装 WSL2 + Ubuntu
管理员 PowerShell 执行：
```powershell
wsl --install -d Ubuntu-24.04
```
重启后按提示创建 Linux 用户名密码。之后所有命令在 Ubuntu 终端里执行。

### 3.2 安装构建依赖
```bash
sudo apt update
sudo apt install -y build-essential autoconf automake libtool pkg-config \
                    curl unzip xz-utils file
```

### 3.3 下载并解压 Linux 版 NDK
注意：**必须用 Linux 版 NDK**（Windows 的 NDK 里是 .exe，WSL 里不能用）：
```bash
mkdir -p ~/android && cd ~/android
curl -LO https://dl.google.com/android/repository/android-ndk-r29-linux.zip
unzip -q android-ndk-r29-linux.zip    # 解压为 ~/android/android-ndk-r29（r27c 亦可，已编完的产物无需重编）
```

### 3.4 设置交叉编译环境变量
```bash
export NDK=~/android/android-ndk-r29   # 若你之前已用 r27c 装好，保留原路径也完全可以
export TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64
export CC=$TOOLCHAIN/bin/aarch64-linux-android28-clang
# 注意：必须用 android-28 而不是 26！Android bionic 从 API 28 起才内置 iconv，
# 用 26 会在 libxml2/libgphoto2 的 iconv 检查时报 "Could NOT find Iconv"。
# App 的 minSdk 相应设为 28（Android 9+，2026 年覆盖率约 99%）。
export AR=$TOOLCHAIN/bin/llvm-ar
export RANLIB=$TOOLCHAIN/bin/llvm-ranlib
export HOST=aarch64-linux-android
export PREFIX=~/android/gp-arm64       # 所有产物汇总在这里
mkdir -p $PREFIX
```
> 建议把这段写进 `~/android/env.sh`，每次 `source ~/android/env.sh`。

### 3.5 编译 libxml2（静态库）
libxml2 2.13+ 已移除 autotools，统一用 CMake（NDK 自带工具链文件）：
```bash
cd ~/android
curl -LO https://download.gnome.org/sources/libxml2/2.13/libxml2-2.13.6.tar.xz
tar xf libxml2-2.13.6.tar.xz && cd libxml2-2.13.6
cmake -B build -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DCMAKE_INSTALL_PREFIX=$PREFIX \
  -DBUILD_SHARED_LIBS=OFF \
  -DLIBXML2_WITH_PYTHON=OFF -DLIBXML2_WITH_ZLIB=OFF \
  -DLIBXML2_WITH_LZMA=OFF -DLIBXML2_WITH_HTTP=OFF -DLIBXML2_WITH_TESTS=OFF \
  -DLIBXML2_WITH_PROGRAMS=OFF
# 报 "Could NOT find Iconv" 的两个原因：① ANDROID_PLATFORM 低于 28（bionic 无 iconv）
# ② 改过平台后没删 build 目录（CMake 缓存了旧平台）。删掉 build 重跑即可。
# 若仍想绕开 iconv，可加 -DLIBXML2_WITH_ICONV=OFF（本项目够用，但 libgphoto2 不行）。
cmake --build build -j8 && cmake --install build
```

### 3.6 编译 libltdl（libgphoto2 动态加载 camlib 依赖）
```bash
cd ~/android
curl -LO https://ftp.gnu.org/gnu/libtool/libtool-2.4.7.tar.xz
tar xf libtool-2.4.7.tar.xz && cd libtool-2.4.7
./configure --host=$HOST --prefix=$PREFIX --enable-static --disable-shared
make -j8 && make install
```

### 3.7 编译 libgphoto2（核心步骤）

> **国内网络下载加速（GitHub 拒连/reset 时按序尝试）：**
> 1. **Windows 浏览器下载最稳**：浏览器访问 release 页下载 tar.gz，然后 WSL 里
>    `cp /mnt/c/Users/heng/Downloads/libgphoto2-2.5.34.tar.gz ~/android/`
> 2. **WSL 走 Windows 代理**（若 Windows 开着 Clash 等混合端口 7890）：
>    ```bash
>    export hostip=$(ip route show default | awk '{print $3}')
>    export https_proxy=http://$hostip:7890 http_proxy=http://$hostip:7890
>    ```
>    （若代理开了 TUN 模式，WSL 流量通常已自动走代理，无需设置）
> 3. **加速镜像前缀**：`curl -LO https://ghfast.top/https://github.com/gphoto/libgphoto2/releases/download/v2.5.34/libgphoto2-2.5.34.tar.gz`（此类镜像站经常更换，失效就换一个：gh-proxy.com、ghproxy.net 等）
> 4. **git clone 兜底**：`git clone --depth 1 --branch v2.5.34 https://github.com/gphoto/libgphoto2.git`——注意 clone 出来的源码没有 configure，需先 `./autogen.sh` 或 `autoreconf -i` 生成。
>
> **GNU 官方源码包（libtool 等）国内直连慢时用 TUNA 镜像**：
> `https://mirrors.tuna.tsinghua.edu.cn/gnu/libtool/libtool-2.4.7.tar.xz`

```bash
cd ~/android
curl -LO https://github.com/gphoto/libgphoto2/releases/download/v2.5.34/libgphoto2-2.5.34.tar.gz
tar xf libgphoto2-2.5.34.tar.gz && cd libgphoto2-2.5.34

# 关键：交叉编译时 configure 找不到系统里的 libltdl/libxml2，
# 必须用 CPPFLAGS/LDFLAGS/LTDLINCL/LIBLTDL 显式指向 $PREFIX 里自己编的产物。
# 若报 "libgphoto2 requires libltdl"，先确认 $PREFIX/lib/libltdl.a 和
# $PREFIX/include/ltdl.h 存在（3.6 步完成），再按下述方式传入变量。
# 不要用 apt install libltdl-dev —— 那是 x86 宿主机的库，对交叉编译无效。
PKG_CONFIG_PATH=$PREFIX/lib/pkgconfig \
CPPFLAGS="-I$PREFIX/include" \
LDFLAGS="-L$PREFIX/lib" \
./configure \
  --host=$HOST --prefix=$PREFIX \
  --with-camlibs=ptp2 \
  --with-ports=ptpip \
  --without-libusb \
  --disable-nls --disable-internal-docs --disable-examples \
  LTDLINCL="-I$PREFIX/include" \
  LIBLTDL="$PREFIX/lib/libltdl.a"
make -j8 && make install
```

产物检查：
```bash
file $PREFIX/lib/libgphoto2.so          # 应显示 "ELF 64-bit LSB shared object, ARM aarch64"
find $PREFIX/lib -name "*.so*"          # 记下：libgphoto2.so / libgphoto2_port.so
find $PREFIX/lib -path "*camlibs*"      # camlib: ptp2.so
find $PREFIX/lib -path "*iolibs*"       # 端口库: ptpip.so
```

### 3.8 把产物拷回 Windows
```bash
mkdir -p /mnt/c/dev/gp-android
cp -r ~/android/gp-arm64/lib /mnt/c/dev/gp-android/
```
之后在 Windows 资源管理器里 `C:\dev\gp-android\lib` 即可看到所有 `.so`。

> 若 WSL 交叉编译环节反复报错，退路：改用方案 B（把 libgphoto2 源码 + 手写 config.h 放进 Android 工程用 NDK CMake 编），或直接在 WSL 里 `apt install libgphoto2-dev` 先在 Linux 上跑熟 API 再回头解决交叉编译。

---

## 阶段四：接入 Android Studio 工程

### 4.1 摆放 .so 文件
在 App 模块下建目录，放三类东西（注意 camlib/iolib 不是被 System.loadLibrary 加载的，而是 dlopen，**不放进 jniLibs**）：

```
app/src/main/
├── jniLibs/arm64-v8a/
│   ├── libgphoto2.so
│   ├── libgphoto2_port.so
│   └── (libxml2 若为动态版也放这里；静态版不用)
└── cpp/
    ├── CMakeLists.txt
    └── gphoto2_jni.c        # 你的 JNI 封装（方案文档 3.3 节的代码）
```

camlib 与 iolib 作为资产打进 APK，运行时解包：
- 把 `$PREFIX/lib/gphoto2/`（camlibs）和 `$PREFIX/lib/libgphoto2_port/`（iolibs）整个目录拷到 `app/src/main/assets/camlibs/`、`app/src/main/assets/iolibs/`。
- App 首次启动时把它们解到 `filesDir`，然后**在调用任何 gp_* 函数之前**设置环境变量：
  ```kotlin
  // JNI 层或 Kotlin 均可，C 写法：
  setenv("CAMLIBS", "${filesDir}/camlibs", 1)
  setenv("IOLIBS",  "${filesDir}/iolibs", 1)
  ```

### 4.2 CMakeLists.txt（链接预编译的 libgphoto2）
```cmake
cmake_minimum_required(VERSION 3.22)
project(nikontransfer)
set(GP_DIR ${CMAKE_SOURCE_DIR}/../../../libs/gphoto2/${ANDROID_ABI})

add_library(gphoto2 SHARED IMPORTED)
set_target_properties(gphoto2 PROPERTIES IMPORTED_LOCATION ${GP_DIR}/libgphoto2.so)
add_library(gphoto2_port SHARED IMPORTED)
set_target_properties(gphoto2_port PROPERTIES IMPORTED_LOCATION ${GP_DIR}/libgphoto2_port.so)

add_library(gphoto2_jni SHARED gphoto2_jni.c)
target_include_directories(gphoto2_jni PRIVATE ${GP_DIR}/include)
target_link_libraries(gphoto2_jni gphoto2 gphoto2_port log)
```
（把 `lib/` 里的 include 和 .so 拷到 `app/libs/gphoto2/arm64-v8a/`。）

### 4.3 Gradle 配置
`app/build.gradle.kts` 关键项：
```kotlin
android {
    namespace = "com.example.nikontransfer"
    defaultConfig {
        minSdk = 28   // 与 NDK 平台一致：bionic 的 iconv 需要 API 28+
        ndk { abiFilters += "arm64-v8a" }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "4.1.2" } }
    packagingOptions { jniLibs { useLegacyPackaging = true } }  // 保证 so 从 lib 解包可 dlopen
}
```

### 4.4 真机联调流程
1. 相机开「连接至 PC (Wi-Fi)」，记下 IP；
2. 手机 Wi-Fi 连到相机热点（AP 模式）或同一路由器（STA 模式）；
3. App 里实现 `bindProcessToNetwork`（方案文档第四节坑 1）；
4. 点"连接"→ JNI 调 `nativeConnect("ptpip:192.168.1.66:15740")` → 列目录 → 下载一张 JPEG 到 MediaStore；
5. 用 `adb logcat -s GPhoto2` 过滤日志排障（JNI 里加 `__android_log_print`）。

---

## 附：Windows 全流程排错速查

| 阶段 | 症状 | 处理 |
|------|------|------|
| MSYS2 | gcc 找不到 | 用的是 MSYS shell 而非 MINGW64；重开 MINGW64 |
| MSYS2 | 连不上相机 | 防火墙放行；重连相机 Wi-Fi 拿新 IP；ping 相机 |
| WSL2 | curl GitHub 报 Connection reset by peer | 国内网络问题，见 3.7 节前的「下载加速」说明：浏览器下载后 cp 进 WSL / 走 Windows 代理 / 用加速镜像前缀；GNU 源码包改用 TUNA 镜像 |
| WSL2 | CMake 报 Could NOT find Iconv | ANDROID_PLATFORM 低于 28（bionic 28 才有 iconv），且删掉 build 目录清缓存后重跑 |
| WSL2 | configure 报 requires libltdl | 3.6 步产物缺失，或未传 LTDLINCL/LIBLTDL/CPPFLAGS/LDFLAGS 指向 $PREFIX（见 3.7 节注释）；apt 装的 libltdl-dev 是 x86 的，无效 |
| WSL2 | configure 报 cannot find libxml2 | 确认 `PKG_CONFIG_PATH=$PREFIX/lib/pkgconfig` 且 3.5 步安装成功 |
| WSL2 | configure 报 ltdl 缺失 | 完成 3.6 步 libltdl 编译并重跑 |
| Android | dlopen camlib 失败 | CAMLIBS/IOLIBS 环境变量没指向解包后的目录；在 gp_camera_init 前设置 |
| Android | `UnsatisfiedLinkError` | jniLibs 里缺 .so，或 System.loadLibrary 名字与 CMake target 不一致 |
| Android | 连接超时 | 忘了 bindProcessToNetwork；或手机把相机 Wi-Fi 判为"无互联网"自动切回移动数据 |
| Android 17 真机 | 权限齐全仍连不上相机 | target API 37 默认阻止本地网络访问：需声明并运行时请求 ACCESS_LOCAL_NETWORK 权限 |
