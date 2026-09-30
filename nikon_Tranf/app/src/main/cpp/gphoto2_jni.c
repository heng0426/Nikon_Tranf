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