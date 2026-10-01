# 16KB 页对齐完整重编指南（从零，修复「连接并列目录」闪退）

> 适用场景：WSL 里的 `~/android` 已不存在或想彻底重来。从头下载、建目录、
> 编全部依赖，产出 4 个 16KB 对齐的 .so 并拷回 Android 工程。
> 全程约 30-60 分钟（含下载）。

## 0. 问题与原理（30 秒版）

- 闪退日志：`dlopen failed: "...libgphoto2.so" program alignment (4096)
  cannot be smaller than system page size (16384)`
- 当前模拟器是 **16KB 页内核**镜像（`sdk_gphone16k_x86_64`，Android 17），要求
  .so 的 ELF `LOAD` 段对齐 ≥ 16384；此前 WSL 编的 4 个库全是 4096 对齐：
  `libgphoto2.so` / `libgphoto2_port.so` / `camlibs/ptp2.so` / `iolibs/ptpip.so`
- 段布局非 16K 同余，**改 ELF 头无效，必须加 `-Wl,-z,max-page-size=16384` 重新链接**
- NDK 在工程里编的 `gphoto2_jni.so` 已是 0x4000 对齐，不用动
- 16KB 页也是真机方向（Pixel 16K 模式、Play 兼容要求），修好一劳永逸

## 省时捷径（可选，跳过 §7/§8）

libxml2 / libltdl 是**静态库**，链进最终 .so，无页对齐问题。Windows 侧已有一份
可用的，直接恢复进 WSL 即可跳过这两个的编译：

```bash
mkdir -p ~/android
cp -r /mnt/e/Code/GitHub/Nikon_Tranf/lib/gp-android/gp-arm64 ~/android/
```

> 走此捷径：§2 可不装 cmake，§4 只需下载 libgphoto2 一个包，直接从 §5 开始。

---

## 1. 检查 WSL

PowerShell 里 `wsl -l -v` 确认 Ubuntu 在运行。没有就（管理员 PowerShell）：

```powershell
wsl --install -d Ubuntu-24.04
```

## 2. 装构建依赖

```bash
sudo apt update
sudo apt install -y build-essential cmake autoconf automake libtool \
                    pkg-config curl unzip xz-utils file git
```

## 3. 建目录 + 下载 Linux 版 NDK

```bash
mkdir -p ~/android && cd ~/android
curl -LO https://dl.google.com/android/repository/android-ndk-r29-linux.zip
unzip -q android-ndk-r29-linux.zip
# 确认解压 OK（应能看到 aarch64-linux-android28-clang 一类文件）：
ls android-ndk-r29/toolchains/llvm/prebuilt/linux-x86_64/bin/ | grep aarch64 | head -3
```

约 1GB，`dl.google.com` 国内一般可直连。**必须用 Linux 版 NDK**（Windows 的 NDK
是 .exe，WSL 里用不了）。

## 4. 下载三个源码包并解压

```bash
cd ~/android

# libxml2（GNOME 源）
curl -LO https://download.gnome.org/sources/libxml2/2.13/libxml2-2.13.6.tar.xz

# libtool（GNU 源；慢就换 TUNA 镜像）
curl -LO https://ftp.gnu.org/gnu/libtool/libtool-2.4.7.tar.xz
#   备用: https://mirrors.tuna.tsinghua.edu.cn/gnu/libtool/libtool-2.4.7.tar.xz

# libgphoto2（GitHub release；被 reset 就加镜像前缀）
curl -LO https://github.com/gphoto/libgphoto2/releases/download/v2.5.34/libgphoto2-2.5.34.tar.gz
#   备用: curl -LO https://ghfast.top/https://github.com/gphoto/libgphoto2/releases/download/v2.5.34/libgphoto2-2.5.34.tar.gz

tar xf libxml2-2.13.6.tar.xz
tar xf libtool-2.4.7.tar.xz
tar xf libgphoto2-2.5.34.tar.gz
```

GitHub 兜底：Windows 浏览器打开 release 页下载，再
`cp /mnt/c/Users/heng/Downloads/libgphoto2-2.5.34.tar.gz ~/android/`。

## 5. 环境变量（一次写进 env.sh，以后 source 即可）

```bash
cat > ~/android/env.sh <<'EOF'
export NDK=~/android/android-ndk-r29
export TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64
export CC=$TOOLCHAIN/bin/aarch64-linux-android28-clang
export AR=$TOOLCHAIN/bin/llvm-ar
export RANLIB=$TOOLCHAIN/bin/llvm-ranlib
export HOST=aarch64-linux-android
export PREFIX=~/android/gp-arm64
EOF
source ~/android/env.sh
mkdir -p $PREFIX
```

> `android-28` 是刻意的：bionic 从 API 28 起才内置 iconv，用 26 会在 libxml2
> 的 iconv 检查上失败。

## 6. 编译 libxml2（静态库）

```bash
cd ~/android/libxml2-2.13.6
cmake -B build -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DCMAKE_INSTALL_PREFIX=$PREFIX \
  -DBUILD_SHARED_LIBS=OFF \
  -DLIBXML2_WITH_PYTHON=OFF -DLIBXML2_WITH_ZLIB=OFF \
  -DLIBXML2_WITH_LZMA=OFF -DLIBXML2_WITH_HTTP=OFF \
  -DLIBXML2_WITH_TESTS=OFF -DLIBXML2_WITH_PROGRAMS=OFF
cmake --build build -j8 && cmake --install build
```

> 改过平台后如果报 "Could NOT find Iconv"，删掉 `build` 目录重跑（CMake 缓存
> 了旧平台）。

## 7. 编译 libltdl（静态库，libgphoto2 动态加载 camlib 依赖它）

```bash
cd ~/android/libtool-2.4.7
./configure --host=$HOST --prefix=$PREFIX --enable-static --disable-shared
make -j8 && make install
```

## 8. 编译 libgphoto2（核心步骤，16K 对齐 flag ★ 绝不能省）

```bash
cd ~/android/libgphoto2-2.5.34

PKG_CONFIG_PATH=$PREFIX/lib/pkgconfig \
CPPFLAGS="-I$PREFIX/include" \
LDFLAGS="-L$PREFIX/lib -Wl,-z,max-page-size=16384" \
./configure \
  --host=$HOST --prefix=$PREFIX \
  --with-camlibs=ptp2 \
  --with-ports=ptpip \
  --without-libusb \
  --disable-nls --disable-internal-docs --disable-examples \
  LTDLINCL="-I$PREFIX/include" \
  LIBLTDL="$PREFIX/lib/libltdl.a"

make clean 2>/dev/null || true
make -j8 && make install
```

★ `LDFLAGS` 里的 **`-Wl,-z,max-page-size=16384`** 就是本次修复的全部意义。
没有它，编出来的还是 4096 对齐，闪退照旧。

产物速览（应看到）：

```bash
find $PREFIX -name "*.so"
# $PREFIX/lib/libgphoto2.so
# $PREFIX/lib/libgphoto2_port.so
# $PREFIX/lib/libgphoto2/2.5.34/ptp2.so
# $PREFIX/lib/libgphoto2_port/0.12.2/ptpip.so
# $PREFIX/lib/libgphoto2_port/0.12.2/disk.so   （不打包进 App，无所谓）
```

## 9. 验证（WSL 里就地检查，全过再拷贝）

```bash
for so in \
  $PREFIX/lib/libgphoto2.so \
  $PREFIX/lib/libgphoto2_port.so \
  $PREFIX/lib/libgphoto2/2.5.34/ptp2.so \
  $PREFIX/lib/libgphoto2_port/0.12.2/ptpip.so; do
  echo "=== $so ==="
  $TOOLCHAIN/bin/llvm-readelf -l "$so" | grep LOAD
done
```

**判定标准**：每个文件的所有 LOAD 行末尾都是 `0x4000`（而不是 `0x1000`）。
有任何一行还是 `0x1000` → configure 没带上 flag，回 §8 检查后重来。

## 10. 拷回 Windows 工程（4 个文件 + 同步主副本）

```bash
E=/mnt/e/Code/GitHub/Nikon_Tranf

# JNI 侧主库 → 工程的预编译库目录（CMake IMPORTED 用）
cp $PREFIX/lib/libgphoto2.so      $E/nikon_Tranf/app/libs/gphoto2/arm64-v8a/
cp $PREFIX/lib/libgphoto2_port.so $E/nikon_Tranf/app/libs/gphoto2/arm64-v8a/

# camlib / iolib → assets（运行时解包、dlopen 加载的插件）
cp $PREFIX/lib/libgphoto2/2.5.34/ptp2.so       $E/nikon_Tranf/app/src/main/assets/gphoto2/camlibs/
cp $PREFIX/lib/libgphoto2_port/0.12.2/ptpip.so $E/nikon_Tranf/app/src/main/assets/gphoto2/iolibs/

# 同步顶层主副本，保持三处一致（建议）
cp $PREFIX/lib/libgphoto2.so $PREFIX/lib/libgphoto2_port.so $E/lib/gp-android/gp-arm64/lib/
cp $PREFIX/lib/libgphoto2/2.5.34/ptp2.so       $E/lib/gp-android/gp-arm64/lib/libgphoto2/2.5.34/
cp $PREFIX/lib/libgphoto2_port/0.12.2/ptpip.so $E/lib/gp-android/gp-arm64/lib/libgphoto2_port/0.12.2/
```

> 若 make install 后版本号子目录与上面不同，用
> `find $PREFIX -name "ptp2.so" -o -name "ptpip.so"` 找到实际路径再拷。

## 11. 之后

拷贝完成后回来说一声，剩下由我在 Windows 侧完成：
1. `gradlew assembleDebug` 重新构建
2. `llvm-readelf` 复核 APK 合并产物里 4 个库都是 0x4000
3. 安装到模拟器、点按「连接并列目录」验证 `UnsatisfiedLinkError` 消失

## 常见问题

| 现象 | 原因与处理 |
|------|-----------|
| `configure: error: libgphoto2 requires libltdl` | §7 没完成（`$PREFIX/lib/libltdl.a` 不存在），或 configure 命令漏了 LTDLINCL/LIBLTDL——完整复制 §8 的整段命令 |
| libxml2 报 `Could NOT find Iconv` | ANDROID_PLATFORM 必须 android-28；改过平台要删 build 目录重跑（CMake 缓存旧平台） |
| WSL 里 curl GitHub 被重置 | 浏览器下载中转 / ghfast.top 镜像前缀 / 走 Windows 代理 |
| 验证里 LOAD 仍是 0x1000 | LDFLAGS 没带 flag（变量被覆盖、漏复制整段命令），重跑 §8 |
| make 几乎没编任何东西 | 忘了 `make clean`，时间戳让 make 以为一切最新；clean 后重跑 |
| `requires libltdl` 之外 configure 找不到 libxml2 | 确认 `PKG_CONFIG_PATH=$PREFIX/lib/pkgconfig` 已带上、§6 已 `--install` |
| 模拟器疑问：x86_64 镜像为什么跑 arm64 库？ | `emu64xa` 镜像带 ARM 翻译层，App 以 arm64 翻译模式运行——正常，对齐才是本次问题 |
