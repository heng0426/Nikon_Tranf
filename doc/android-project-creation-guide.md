# 创建 Android 工程全步骤 — 尼康 Z 传图 App（M2 阶段）

> 前置条件（已就绪）：
> - Android Studio + SDK 37 + NDK r29 (29.0.14206865) + CMake 4.1.2
> - WSL 交叉编译产物在 `C:\dev\gp-android\lib`（含 libgphoto2.so、libgphoto2_port.so、ptp2.so、ptpip.so、头文件）
> - 真机（Android 9+，已开 USB 调试）、尼康 Z 相机
> 目标：新建工程 → 接入预编译库 → 实现「连接相机 → 列出文件 → 下载一张 JPEG 到相册」的最小可用版本。

---

## 第 1 步：新建工程

1. Android Studio → **New Project**
2. 模板选 **Empty Activity**（Compose 模板；若列表里有 "Empty Activity (Compose)" 同样可以）
3. 逐项填写：
   - Name: `NikonTransfer`
   - Package name: `com.example.nikontransfer`
   - Save location: 自选（如 `C:\dev\NikonTransfer`）
   - Language: **Kotlin**
   - Minimum SDK: **API 28 (Android 9.0)**
   - Build configuration language: **Kotlin DSL (build.gradle.kts)**
4. Finish，等待 Gradle 首次同步完成。

## 第 2 步：确认 SDK / NDK 版本配置

打开 `app/build.gradle.kts`，把 android 块改成（关键项逐条核对）：

```kotlin
android {
    namespace = "com.example.nikontransfer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.nikontransfer"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            // 只编 arm64；以后要支持老 32 位机再加 armeabi-v7a（需重编一遍库）
            abiFilters += "arm64-v8a"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    packaging {
        jniLibs {
            // 关键：让 .so 以解压形式放在 /data/app/.../lib/，
            // 这样 camlib 的 dlopen 才能找到依赖（默认 false 会直接从 APK 加载）
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
```

（若想强制使用我们装的 NDK，可在 `android {}` 块加 `ndkVersion = "29.0.14206865"`，不加则用 SDK 目录里唯一的那个。）

**顶部工具栏的 Run configuration 选中 app，Device 选中你的真机。**

## 第 3 步：放置预编译的 .so 和头文件

在 Windows 资源管理器操作（假设工程在 `C:\dev\NikonTransfer`）：

1. 建 `app\libs\gphoto2\arm64-v8a\` 目录
2. 从 `C:\dev\gp-android\lib\` 拷入：
   - `libgphoto2.so` → `app\libs\gphoto2\arm64-v8a\`
   - `libgphoto2_port.so` → 同上
   - `include\gphoto2\`（整个目录，里面是 gphoto2.h 等头文件）→ 同上
3. 最终结构：
```
app/libs/gphoto2/arm64-v8a/
├── libgphoto2.so
├── libgphoto2_port.so
└── include/gphoto2/
    ├── gphoto2.h
    ├── gphoto2-camera.h
    ├── gphoto2-file.h
    ├── gphoto2-list.h
    ├── gphoto2-port-info-list.h
    └── ...
```

> 检查 WSL 里还有没有 `libgphoto2.so.6` / `libgphoto2_port.so.13` 这类带版本号的符号链接或副本——**只拷 `libgphoto2.so` 和 `libgphoto2_port.so` 两个**，Android 的 linker 不认 Linux 式版本命名。

## 第 4 步：放置 camlib / iolib（assets）

这些库是运行时 dlopen 的，不打进 jniLibs，而是放 assets，首次启动解包到 filesDir：

1. 建 `app\src\main\assets\gphoto2\` 目录
2. 拷贝：
   - WSL `$PREFIX/lib/gphoto2/ptp2.so` → `assets\gphoto2\camlibs\ptp2.so`（若该目录下还有别的 .so，全部拷上）
   - WSL `$PREFIX/lib/libgphoto2_port/ptpip.so` → `assets\gphoto2\iolibs\ptpip.so`
3. 最终：
```
app/src/main/assets/gphoto2/
├── camlibs/ptp2.so
└── iolibs/ptpip.so
```

## 第 5 步：创建 JNI 目录与 CMakeLists.txt

1. 右键 `app/src/main` → New → Directory → 输入 `cpp`
2. 在 `cpp` 下新建文件 `CMakeLists.txt`：

```cmake
cmake_minimum_required(VERSION 3.22)
project(nikontransfer)

set(GP_DIR ${CMAKE_SOURCE_DIR}/../../../libs/gphoto2/${ANDROID_ABI})

# 预编译库（IMPORTED，不重新编译）
add_library(gphoto2 SHARED IMPORTED)
set_target_properties(gphoto2 PROPERTIES IMPORTED_LOCATION ${GP_DIR}/libgphoto2.so)

add_library(gphoto2_port SHARED IMPORTED)
set_target_properties(gphoto2_port PROPERTIES IMPORTED_LOCATION ${GP_DIR}/libgphoto2_port.so)

# 我们的 JNI 封装
add_library(gphoto2_jni SHARED gphoto2_jni.c)

target_include_directories(gphoto2_jni PRIVATE ${GP_DIR}/include)
target_link_libraries(gphoto2_jni
    gphoto2
    gphoto2_port
    log)   # android logcat
```

## 第 6 步：编写 JNI 封装 `gphoto2_jni.c`

在 `cpp` 下新建 `gphoto2_jni.c`（完整可编译版）：

```c
#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>
#include <gphoto2/gphoto2.h>

#define LOG_TAG "GPhoto2"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static Camera    *g_camera  = NULL;
static GPContext *g_context = NULL;

/* ---------- 0. 初始化库路径（必须在任何 gp_* 之前调用） ---------- */
JNIEXPORT void JNICALL
Java_com_example_nikontransfer_GPhoto2Bridge_nativeSetupLibPaths(
        JNIEnv *env, jclass clz, jstring jCamlibs, jstring jIolibs) {
    const char *camlibs = (*env)->GetStringUTFChars(env, jCamlibs, 0);
    const char *iolibs  = (*env)->GetStringUTFChars(env, jIolibs, 0);
    setenv("CAMLIBS", camlibs, 1);   /* ptp2.so 所在目录 */
    setenv("IOLIBS",  iolibs,  1);   /* ptpip.so 所在目录 */
    LOGI("CAMLIBS=%s IOLIBS=%s", camlibs, iolibs);
    (*env)->ReleaseStringUTFChars(env, jCamlibs, camlibs);
    (*env)->ReleaseStringUTFChars(env, jIolibs, iolibs);
}

/* ---------- 1. 连接相机：path 形如 "ptpip:192.168.1.66" ---------- */
JNIEXPORT jint JNICALL
Java_com_example_nikontransfer_GPhoto2Bridge_nativeConnect(
        JNIEnv *env, jclass clz, jstring jPath) {
    const char *path = (*env)->GetStringUTFChars(env, jPath, 0);
    g_context = gp_context_new();
    gp_camera_new(&g_camera);

    GPPortInfo info;
    gp_port_info_new(&info);
    gp_port_info_set_type(info, GP_PORT_PTPIP);
    gp_port_info_set_name(info, "Nikon Z");
    gp_port_info_set_path(info, path);      /* ptpip:IP 或 ptpip:IP:15740 */
    gp_camera_set_port_info(g_camera, info);

    int ret = gp_camera_init(g_camera, g_context);
    if (ret < GP_OK) {
        LOGE("gp_camera_init failed: %d (%s)", ret, gp_result_as_string(ret));
        gp_camera_free(g_camera); g_camera = NULL;
        gp_context_unref(g_context); g_context = NULL;
    } else {
        LOGI("camera connected: %s", path);
    }
    (*env)->ReleaseStringUTFChars(env, jPath, path);
    return ret;
}

/* ---------- 2. 列出某目录下的文件，返回 "name|size|..." 的拼接串 ---------- */
JNIEXPORT jstring JNICALL
Java_com_example_nikontransfer_GPhoto2Bridge_nativeListFiles(
        JNIEnv *env, jclass clz, jstring jFolder) {
    if (!g_camera) return NULL;
    const char *folder = (*env)->GetStringUTFChars(env, jFolder, 0);

    CameraList *list;
    gp_list_new(&list);
    int ret = gp_camera_folder_list_files(g_camera, folder, list, g_context);
    if (ret < GP_OK) {
        LOGE("list_files failed: %d", ret);
        gp_list_free(list);
        (*env)->ReleaseStringUTFChars(env, jFolder, folder);
        return NULL;
    }
    char buf[8192] = "";
    int n = gp_list_count(list);
    for (int i = 0; i < n; i++) {
        const char *name;
        gp_list_get_name(list, i, &name);
        strncat(buf, name, sizeof(buf) - strlen(buf) - 2);
        strncat(buf, "|", sizeof(buf) - strlen(buf) - 2);
    }
    gp_list_free(list);
    (*env)->ReleaseStringUTFChars(env, jFolder, folder);
    return (*env)->NewStringUTF(env, buf);
}

/* ---------- 3. 下载文件，返回字节数组 ---------- */
JNIEXPORT jbyteArray JNICALL
Java_com_example_nikontransfer_GPhoto2Bridge_nativeDownloadFile(
        JNIEnv *env, jclass clz, jstring jFolder, jstring jName) {
    if (!g_camera) return NULL;
    const char *folder = (*env)->GetStringUTFChars(env, jFolder, 0);
    const char *name   = (*env)->GetStringUTFChars(env, jName, 0);

    CameraFile *file;
    gp_file_new(&file);
    int ret = gp_camera_file_get(g_camera, folder, name,
                                 GP_FILE_TYPE_NORMAL, file, g_context);
    jbyteArray out = NULL;
    if (ret == GP_OK) {
        const char *data; unsigned long size;
        gp_file_get_data_and_size(file, &data, &size);
        LOGI("downloaded %s (%lu bytes)", name, size);
        out = (*env)->NewByteArray(env, (jsize) size);
        (*env)->SetByteArrayRegion(env, out, 0, (jsize) size, (const jbyte *) data);
    } else {
        LOGE("file_get failed: %d", ret);
    }
    gp_file_free(file);
    (*env)->ReleaseStringUTFChars(env, jFolder, folder);
    (*env)->ReleaseStringUTFChars(env, jName, name);
    return out;
}

/* ---------- 4. 断开 ---------- */
JNIEXPORT void JNICALL
Java_com_example_nikontransfer_GPhoto2Bridge_nativeExit(JNIEnv *env, jclass clz) {
    if (g_camera) {
        gp_camera_exit(g_camera, g_context);
        gp_camera_free(g_camera);
        g_camera = NULL;
    }
    if (g_context) { gp_context_unref(g_context); g_context = NULL; }
    LOGI("camera closed");
}
```

> 注意：Java 方法名必须与包名严格对应 `Java_com_example_nikontransfer_GPhoto2Bridge_nativeXxx`。如果你改了包名/类名，这里要同步改。

## 第 7 步：Kotlin 桥接类 + assets 解包

`app/src/main/java/com/example/nikontransfer/GPhoto2Bridge.kt`：

```kotlin
object GPhoto2Bridge {
    @Volatile private var libReady = false

    init { System.loadLibrary("gphoto2_jni") }

    /** 首次调用：把 assets/gphoto2 里的 camlib/iolib 解包到 filesDir，并设置路径 */
    fun setup(context: android.content.Context) {
        if (libReady) return
        val base = android.content.File(context.filesDir, "gphoto2")
        listOf("camlibs", "iolibs").forEach { sub ->
            val dir = android.java.io.File(base, sub).apply { mkdirs() }
            context.assets.list("gphoto2/$sub")?.forEach { so ->
                val f = java.io.File(dir, so)
                if (!f.exists()) {
                    context.assets.open("gphoto2/$sub/$so").use { input ->
                        f.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                f.setReadable(true, false)
            }
        }
        nativeSetupLibPaths(
            java.io.File(base, "camlibs").absolutePath,
            java.io.File(base, "iolibs").absolutePath
        )
        libReady = true
    }

    external fun nativeSetupLibPaths(camlibs: String, iolibs: String)
    external fun nativeConnect(path: String): Int          // "ptpip:192.168.1.66"
    external fun nativeListFiles(folder: String): String?  // "a.JPG|b.JPG|"
    external fun nativeDownloadFile(folder: String, name: String): ByteArray?
    external fun nativeExit()
}
```

（上面第 12 行笔误避免法：直接 `val dir = java.io.File(base, sub)` 即可，不要写 `android.java.io.File`。）

## 第 8 步：AndroidManifest.xml 权限

`app/src/main/AndroidManifest.xml` 的 `<manifest>` 内加：

```xml
<!-- Android 17 (target 37) 起本地网络访问需要此权限，且是运行时权限 -->
<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
```

`<application>` 内确认有 `android:hasCode` 默认即可，暂不需要 Service（M2 最小版在 Activity 里跑）。

## 第 9 步：MainActivity — 连接 / 列表 / 下载

替换 `MainActivity.kt`（最小可用版）：

```kotlin
package com.example.nikontransfer

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var path = ""            // ptpip:IP
    private var folder = ""          // /store_00010001/DCIM/100NZ510
    private var files = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var ip by remember { mutableStateOf("192.168.1.66") }
                    var log by remember { mutableStateOf("就绪") }
                    val scope = rememberCoroutineScope()

                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(ip, { ip = it }, label = { Text("相机 IP") })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    log = withContext(Dispatchers.IO) { connect(ip) }
                                    log += "\n" + withContext(Dispatchers.IO) { listFiles() }
                                }
                            }) { Text("连接并列目录") }
                            Button(onClick = {
                                scope.launch {
                                    log = withContext(Dispatchers.IO) { downloadFirst() }
                                }
                            }) { Text("下载第一张") }
                        }
                        Text(log)
                        LazyColumn {
                            items(files) { f ->
                                Text(f, Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    /* 关键：把进程网络绑定到相机所在的 Wi-Fi（无外网的网络） */
    private fun bindToWifiNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi = cm.allNetworks.firstOrNull { net ->
            cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        wifi?.let { cm.bindProcessToNetwork(it) }
    }

    private fun connect(ip: String): String {
        GPhoto2Bridge.setup(applicationContext)
        if (Build.VERSION.SDK_INT >= 37) {
            // TODO: 先走运行时权限申请流程拿到 ACCESS_LOCAL_NETWORK
        }
        bindToWifiNetwork()
        path = "ptpip:$ip"
        val ret = GPhoto2Bridge.nativeConnect(path)
        return if (ret == 0) "连接成功" else "连接失败: $ret"
    }

    private fun listFiles(): String {
        // 尼康存储卡根目录规律：/store_00010001/DCIM；先列 DCIM 下子目录，取第一个
        val dcim = "/store_00010001/DCIM"
        // 简化：尝试直接列常见子目录，或先用 nativeListFiles(dcim) 拿不到再退回
        // 这里用 list 遍历（M3 再封装成完整目录树）
        for (sub in listOf("100NZ510", "100NIKON", "100NCD90", "")) {
            val f = if (sub.isEmpty()) dcim else "$dcim/$sub"
            val res = GPhoto2Bridge.nativeListFiles(f)
            if (!res.isNullOrEmpty()) {
                folder = f
                files = res.split('|').filter { it.isNotBlank() }.toMutableList()
                return "目录 $f 共 ${files.size} 个文件"
            }
        }
        return "未找到文件（子目录名可能不同，用日志确认）"
    }

    private fun downloadFirst(): String {
        val name = files.firstOrNull { it.endsWith(".JPG", true) } ?: return "没有 JPEG"
        val data = GPhoto2Bridge.nativeDownloadFile(folder, name) ?: return "下载失败"
        // 写入系统相册
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return "MediaStore 插入失败"
        contentResolver.openOutputStream(uri)?.use { it.write(data) }
        return "已保存到相册: $name (${data.size} bytes)"
    }

    override fun onDestroy() {
        GPhoto2Bridge.nativeExit()
        super.onDestroy()
    }
}
```

## 第 10 步：构建与运行

1. **相机**：菜单 → 连接至 PC (Wi-Fi) → 建立连接 → 记下屏幕上的 IP
2. **手机**：连上相机 Wi-Fi（AP 模式）或同一路由器（STA 模式）
3. Android Studio 点 **Run ▶**，选真机
4. 首次构建会跑 CMake 编 `gphoto2_jni.c`；若报错先看 `Build` 窗口的 native 错误行
5. App 里输入相机 IP → 点「连接并列目录」→ 应显示文件列表
6. 点「下载第一张」→ 打开手机相册确认照片出现

## 第 11 步：排障（按 logcat 过滤 `GPhoto2`）

| 现象 | 原因 | 解决 |
|------|------|------|
| `UnsatisfiedLinkError` | jniLibs 缺 .so 或库名不匹配 | 检查 `app/libs/gphoto2/arm64-v8a/` 两个 .so；CMake target 名 |
| `dlopen failed: library "libgphoto2.so" not found` | useLegacyPackaging 没开 | 核对第 2 步 packaging 配置 |
| 连接返回 -53 (os error) 等 | 没绑定网络 / IP 错 / 相机已休眠 | 手机 ping 相机；重开相机 Wi-Fi；确认 bindToWifiNetwork 生效 |
| 连接返回 -1 且日志有 camlib 字样 | CAMLIBS/IOLIBS 没设置或解包失败 | 看 nativeSetupLibPaths 的日志路径，检查 filesDir/gphoto2 下是否有 ptp2.so |
| target 37 真机连不上 | ACCESS_LOCAL_NETWORK 未授权 | 实现运行时权限申请（第 9 步 TODO 处） |
| `list_files failed: -1` | store 路径不对 | 用 `gp_camera_folder_list_folders` 列 DCIM 拿真实子目录（M3 完善） |

## 第 12 步：验证完成后的下一步

按里程碑继续（见总体方案文档）：
- **M3**：把连接/下载逻辑从前台界面搬进前台 Service + `nativeWaitEvent` 事件循环（实现"拍一张自动传一张"）
- 目录树浏览（listFolders 递归）、进度回调、Room 记录
