# Nikon_Tranf

尼康相机 Wi-Fi 传照片 App。基于 libgphoto2（PTP/IP），通过 JNI 在 Android 上直连相机、列目录并下载照片（JPG / NEF）到系统相册。

## 功能

- 输入相机 IP，通过 `ptpip:<IP>` 连接相机（需相机与手机在同一 Wi-Fi）
- 自动尝试常见 DCIM 子目录（`100NZ510` / `100NIKON` / `100NCZ9` 等）并列出文件
- 下载第一张 JPG / NEF：
  - JPEG 写入系统相册（`MediaStore.Images`）
  - NEF 以 `image/x-nikon-nef` 写入 `MediaStore.Files`

## 技术要点

- **libgphoto2 预编译库**：`app/libs/gphoto2/arm64-v8a/` 提供 `libgphoto2.so`、`libgphoto2_port.so` 及头文件；仅支持 **arm64-v8a**
- **camlib/iolib 随 assets 分发**：`ptp2.so`、`ptpip.so` 打包在 `assets/gphoto2/`，首次运行时解包到 `filesDir` 再由 dlopen 加载（见 `GPhoto2Bridge.setup`）
- **useLegacyPackaging = true**：`.so` 以解压形式落盘，保证 camlib 的 dlopen 能找到依赖
- **Android 17（API 37）本地网络权限**：`ACCESS_LOCAL_NETWORK` 为运行时权限，连接前动态申请
- **bindProcessToNetwork**：相机 Wi-Fi 通常无外网，必须把进程绑定到该 Wi-Fi，否则流量会被切到移动数据导致连接失败

## 项目结构

```
nikon_Tranf/                      # Android Studio 工程
├── app/src/main/java/com/example/nikontransfer/
│   ├── MainActivity.kt           # Compose UI + 连接/列目录/下载逻辑
│   └── GPhoto2Bridge.kt          # JNI 桥接 + assets 解包
├── app/src/main/cpp/
│   ├── gphoto2_jni.c             # JNI 封装（CMake 构建）
│   └── CMakeLists.txt            # 链接预编译 gphoto2 库
├── app/libs/gphoto2/arm64-v8a/   # 预编译 libgphoto2 + 头文件
└── app/src/main/assets/gphoto2/  # camlibs / iolibs（ptp2.so、ptpip.so）
lib/gp-android/gp-arm64/          # 交叉编译 gphoto2 的原始产物（参考）
```

## 构建与运行

要求：Android Studio（AGP + Gradle 9.x）、NDK / CMake（≥3.22）、JDK 17。

```bash
cd nikon_Tranf
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

或在 Android Studio 中直接 Run。

## 使用

1. 相机开启 Wi-Fi（尼康 SnapBridge / 内置 Wi-Fi 的 PTP 模式），手机连接相机热点
2. 打开 App，输入相机 IP（默认 `192.168.1.66`，实际可在相机 Wi-Fi 菜单中查看）
3. 点击「连接并列目录」→「下载第一张」

## 已知限制

- 仅 arm64-v8a 设备
- 仅支持「下载第一张」，暂无批量选择下载
- DCIM 子目录按常见命名枚举，若目录名不同需看 logcat 中 `GPhoto2` 日志排查
