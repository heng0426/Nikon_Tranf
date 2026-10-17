#include <jni.h>
#include <string.h>
#include <strings.h>
#include <stdlib.h>
#include <stdio.h>
#include <android/log.h>
#include <gphoto2/gphoto2.h>
#include <gphoto2/gphoto2-setting.h>

#define LOG_TAG "GPhoto2"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static Camera    *g_camera  = NULL;
static GPContext *g_context = NULL;

/* 原生传输路径（配对模式）的常驻会话：Kotlin camMutex 保证串行访问 */
static int g_tfd  = -1;      /* 传输会话命令通道 */
static int g_tefd = -1;      /* 传输会话事件通道 */
static unsigned int g_ttid = 0;
static unsigned int g_storages[4];
static int g_nstorages = 0;

/* ---------- 设置持久化：PTP/IP GUID 跨启动稳定 ----------
 * 相机按 INIT 包里的 GUID 识别已配对主机；libgphoto2 默认把 GUID 存在
 * $HOME/.config/gphoto/settings，安卓上写不进去导致每次启动随机 GUID，
 * 相机配对向导永远等不到已配对主机。这里用自定义回调 + 自己的文件接管。 */
static char s_guid[64];
static int  s_guid_loaded = 0;
static char s_guidpath[600];

static int setting_get(char *id, char *key, char *value, void *userdata) {
    (void) userdata;
    value[0] = 0;
    if (id && key && !strcmp(id, "ptp2_ip") && !strcmp(key, "guid")) {
        if (!s_guid_loaded && s_guidpath[0]) {
            FILE *f = fopen(s_guidpath, "r");
            if (f) {
                size_t n = fread(s_guid, 1, sizeof(s_guid) - 1, f);
                fclose(f);
                s_guid[n] = 0;
                if (strlen(s_guid) == 47) s_guid_loaded = 1;
            }
        }
        if (s_guid_loaded) {
            strcpy(value, s_guid);
            return 0; /* GP_OK */
        }
    }
    return 0; /* 空串：调用方按 strlen 判断，未找到则走各自默认值 */
}

static int setting_set(char *id, char *key, char *value, void *userdata) {
    (void) userdata;
    if (id && key && value && !strcmp(id, "ptp2_ip") && !strcmp(key, "guid")) {
        strncpy(s_guid, value, sizeof(s_guid) - 1);
        s_guid[sizeof(s_guid) - 1] = 0;
        s_guid_loaded = 1;
        if (s_guidpath[0]) {
            FILE *f = fopen(s_guidpath, "w");
            if (f) { fwrite(s_guid, 1, strlen(s_guid), f); fclose(f); }
        }
        return 0;
    }
    return -1;
}

/* libgphoto2 内部日志（GP_LOG_DEBUG 级）→ logcat，排障用 */
static void gp_log_to_logcat(GPLogLevel level, const char *domain,
                             const char *str, void *data) {
    (void) data;
    __android_log_print(level == GP_LOG_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_INFO,
                        LOG_TAG, "[%s] %s", domain ? domain : "gp", str);
}

/* ---------- 0. 初始化库路径（必须在任何 gp_* 之前调用） ---------- */
JNIEXPORT void JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeSetupLibPaths(
        JNIEnv *env, jclass clz, jstring jCamlibs, jstring jIolibs) {
const char *camlibs = (*env)->GetStringUTFChars(env, jCamlibs, 0);
const char *iolibs  = (*env)->GetStringUTFChars(env, jIolibs, 0);
setenv("CAMLIBS", camlibs, 1);   /* ptp2.so 所在目录 */
setenv("IOLIBS",  iolibs,  1);   /* ptpip.so 所在目录 */
LOGI("CAMLIBS=%s IOLIBS=%s", camlibs, iolibs);

/* ptp2 的设置文件（PTP/IP GUID 持久化）依赖 HOME 环境变量，
 * 安卓默认没有 HOME → GUID 每次启动随机生成 → 相机配对向导永远等不到
 * 「已配对的那台主机」。设为应用私有目录后 GUID 首次生成即持久化。 */
{
    static char homebuf[512];
    const char *marker = "/gphoto2/camlibs";
    size_t len = strlen(camlibs), mlen = strlen(marker);
    if (len > mlen && !strcmp(camlibs + len - mlen, marker) && len - mlen < sizeof(homebuf)) {
        size_t hlen = len - mlen;
        memcpy(homebuf, camlibs, hlen);
        homebuf[hlen] = 0;
        setenv("HOME", homebuf, 1);
        LOGI("HOME=%s", homebuf);
        snprintf(s_guidpath, sizeof(s_guidpath), "%s/gphoto2.guid", homebuf);
        /* 主会话与配对探针（nativePairingProbe）必须同身份：相机配对状态机
         * 按 INIT 包 GUID 识别主机，探针配对后主会话换 GUID 会被拒（-1）。
         * 这里固定为实测身份（见探针函数），并落盘保持一致。 */
        strcpy(s_guid, "33:62:34:62:36:63:62:66:30:65:61:30:34:61:32:64");
        s_guid_loaded = 1;
        FILE *gf = fopen(s_guidpath, "w");
        if (gf) { fwrite(s_guid, 1, strlen(s_guid), gf); fclose(gf); }
    }
}

/* 注册自定义设置持久化（GUID 稳定 → 相机按已配对主机识别） */
gp_setting_set_get_func(setting_get, NULL);
gp_setting_set_set_func(setting_set, NULL);

gp_log_add_func(GP_LOG_DEBUG, gp_log_to_logcat, NULL);
(*env)->ReleaseStringUTFChars(env, jCamlibs, camlibs);
(*env)->ReleaseStringUTFChars(env, jIolibs, iolibs);
}

/* ---------- 1. 连接相机：path 形如 "ptpip:192.168.1.66" ----------
 *
 * 关键：不能把 gp_port_info_new() 造的「游离」GPPortInfo 直接传给
 * gp_camera_set_port_info。gp_port_set_info 内部会
 * strdup(info->library_filename)，而游离 info 的该字段为 NULL，
 * 会 strlen(NULL) 段错误。必须从 GPPortInfoList 里取条目
 * （gp_port_info_list_load 会给每个端口填好 library_filename），
 * 这也是 gphoto2 命令行工具的标准做法。 */
JNIEXPORT jint JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeConnect(
        JNIEnv *env, jclass clz, jstring jPath) {
    const char *path = (*env)->GetStringUTFChars(env, jPath, 0);
    CameraAbilitiesList *al = NULL;
    GPPortInfoList *il = NULL;
    GPPortInfo info = NULL;   /* 属于 il，不单独释放 */
    CameraAbilities a;
    int m, p, ret = GP_ERROR;

    g_context = gp_context_new();
    gp_camera_new(&g_camera);

    do {
        /* ① 显式设置 abilities（通用 PTP，注册了 GP_PORT_PTPIP），
         *    跳过 gp_camera_init 里针对空 model 的 USB 自动探测 */
        ret = gp_abilities_list_new(&al);
        if (ret < GP_OK) break;
        ret = gp_abilities_list_load(al, g_context);          /* 依赖 CAMLIBS */
        if (ret < GP_OK) break;
        m = gp_abilities_list_lookup_model(al, "USB PTP Class Camera");
        if (m < GP_OK) { ret = m; break; }
        ret = gp_abilities_list_get_abilities(al, m, &a);
        if (ret < GP_OK) break;
        ret = gp_camera_set_abilities(g_camera, a);
        if (ret < GP_OK) break;

        /* ② 从端口列表取 ptpip 条目（^ptpip: 正则可匹配任意 IP） */
        ret = gp_port_info_list_new(&il);
        if (ret < GP_OK) break;
        ret = gp_port_info_list_load(il);                     /* 依赖 IOLIBS */
        if (ret < GP_OK) break;
        p = gp_port_info_list_lookup_path(il, path);
        if (p < GP_OK) { ret = p; break; }
        ret = gp_port_info_list_get_info(il, p, &info);
        if (ret < GP_OK) break;
        ret = gp_camera_set_port_info(g_camera, info);        /* 内部深拷贝 */
        if (ret < GP_OK) break;

        /* ③ 正式初始化连接（dlopen ptp2 并执行 camera_init） */
        ret = gp_camera_init(g_camera, g_context);
    } while (0);

    if (il) gp_port_info_list_free(il);   /* 连同内部 info 一起释放 */
    if (al) gp_abilities_list_free(al);

    if (ret < GP_OK) {
        LOGE("connect failed: %d (%s)", ret, gp_result_as_string(ret));
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
Java_com_shinemeow_app_GPhoto2Bridge_nativeListFiles(
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

/* ---------- 2b. 列出某目录下的子文件夹，返回 "name|name|..." 拼接串 ---------- */
JNIEXPORT jstring JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeListFolders(
        JNIEnv *env, jclass clz, jstring jFolder) {
    if (!g_camera) return NULL;
    const char *folder = (*env)->GetStringUTFChars(env, jFolder, 0);

    CameraList *list;
    gp_list_new(&list);
    int ret = gp_camera_folder_list_folders(g_camera, folder, list, g_context);
    if (ret < GP_OK) {
        LOGE("list_folders(%s) failed: %d", folder, ret);
        gp_list_free(list);
        (*env)->ReleaseStringUTFChars(env, jFolder, folder);
        return NULL;
    }
    char buf[4096] = "";
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
Java_com_shinemeow_app_GPhoto2Bridge_nativeDownloadFile(
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
Java_com_shinemeow_app_GPhoto2Bridge_nativeExit(JNIEnv *env, jclass clz) {
if (g_camera) {
gp_camera_exit(g_camera, g_context);
gp_camera_free(g_camera);
g_camera = NULL;
}
if (g_context) { gp_context_unref(g_context); g_context = NULL; }
LOGI("camera closed");
}

/* ---------- 5. 事件轮询：应答相机 Probe，保持连接健康 ----------
 * CIPA 规定相机在 event 通道周期发 ProbeRequest，客户端须立即回 ProbeResponse，
 * 否则相机判定发起方失联（配对状态机不前进、连接可能被丢弃）。
 * 由 Kotlin 后台线程周期调用；libgphoto2 内部锁保证与其它操作串行。 */
JNIEXPORT jint JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeWaitEvent(
        JNIEnv *env, jclass clz, jint timeoutMs) {
    if (!g_camera) return -1;
    CameraEventType etype = GP_EVENT_UNKNOWN;
    void *edata = NULL;
    int ret = gp_camera_wait_for_event(g_camera, (int) timeoutMs,
                                       &etype, &edata, g_context);
    if (edata) {
        free(edata);   /* CameraFilePath 等由库分配，调用方释放 */
        edata = NULL;
    }
    return ret;
}

/* ---------- 6. Nikon 配对探针（逆向握手序列） ----------
 * 相机向导只对以下厂商指令序列作出反应（抓包逐字节逆向）：
 *   独立短连接：INIT → OpenSession(1)
 *   → 0x941c（返回 u32 状态）→ GetStorageIDs → GetDeviceInfo
 *   → 0x952b（返回 8B 能力）→ 0x935a(0x2001)
 *   → CloseSession ×2 → 关闭
 * 收到该序列后相机向导推进到「配对完成」。本探针独立于 libgphoto2 会话，
 * 在每次连接前运行一次。 */
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>

#define PP_INIT_CMD        1
#define PP_INIT_CMD_ACK    2
#define PP_INIT_EVT        3
#define PP_INIT_EVT_ACK    4
#define PP_OP_REQ          6
#define PP_OP_RESP         7
#define PP_START_DATA      9
#define PP_DATA            10
#define PP_END_DATA        12
#define PP_PROBE_REQ       13
#define PP_PROBE_RESP      14

static int pp_socket(const char *ip, int timeout_ms) {
    struct sockaddr_in sa;
    memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET;
    sa.sin_port = htons(15740);
    if (inet_pton(AF_INET, ip, &sa.sin_addr) != 1) return -1;
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) return -1;
    struct timeval tv;
    tv.tv_sec = timeout_ms / 1000;
    tv.tv_usec = (timeout_ms % 1000) * 1000;
    setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
    setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
    if (connect(fd, (struct sockaddr *) &sa, sizeof(sa)) != 0) {
        close(fd);
        return -1;
    }
    return fd;
}

static int pp_write_all(int fd, const unsigned char *buf, int len) {
    int off = 0;
    while (off < len) {
        int w = (int) send(fd, buf + off, len - off, 0);
        if (w <= 0) return -1;
        off += w;
    }
    return 0;
}

static int pp_read_exact(int fd, unsigned char *buf, int len) {
    int off = 0;
    while (off < len) {
        int r = (int) recv(fd, buf + off, len - off, 0);
        if (r <= 0) return -1;
        off += r;
    }
    return 0;
}

/* 读一个完整 PTPIP 帧；返回 type，payload 写入 *out（调用方 free），失败返回 -1 */
static int pp_read_frame(int fd, unsigned char **out, int *outlen) {
    unsigned char hdr[8];
    if (pp_read_exact(fd, hdr, 8) != 0) return -1;
    unsigned int len = hdr[0] | (hdr[1] << 8) | (hdr[2] << 16) | ((unsigned) hdr[3] << 24);
    unsigned int type = hdr[4] | (hdr[5] << 8) | (hdr[6] << 16) | ((unsigned) hdr[7] << 24);
    if (len < 8 || len > 4 * 1024 * 1024) return -1;
    unsigned int plen = len - 8;
    unsigned char *payload = (unsigned char *) malloc(plen ? plen : 1);
    if (!payload) return -1;
    if (plen > 0 && pp_read_exact(fd, payload, (int) plen) != 0) {
        free(payload);
        return -1;
    }
    *out = payload;
    *outlen = (int) plen;
    return (int) type;
}

static int pp_cmd_frame(int fd, unsigned char *frame, int len) {
    return pp_write_all(fd, frame, len);
}

static int pp_expect(int fd, int want_type, unsigned char **payload_out, int *plen_out) {
    *payload_out = NULL;                    /* 入口即清空：失败时调用方不会拿到悬垂指针 */
    *plen_out = 0;
    unsigned char *payload = NULL;
    int plen = 0;
    int type = pp_read_frame(fd, &payload, &plen);
    if (type < 0) return -1;
    if (type == PP_PROBE_REQ) {           /* 顺手应答探测帧 */
        unsigned char resp[8] = {8, 0, 0, 0, 14, 0, 0, 0};
        pp_write_all(fd, resp, 8);
        free(payload);
        return pp_expect(fd, want_type, payload_out, plen_out);
    }
    if (type != want_type) {
        free(payload);
        return -2;
    }
    *payload_out = payload;
    *plen_out = plen;
    return 0;
}

/* data-in 事务：请求 → StartData → Data/EndData → OpResponse；数据拼接返回。
 * 帧布局（抓包逐字节确认）：
 *   [0..3]=len [4..7]=type(6) [8..11]=DataPhaseInfo(u32=1)
 *   [12..13]=opcode(u16) [14..17]=tid(u32) [18..]=params(u32×n)
 * 内存纪律：每次 free 后立即置 NULL；失败路径绝不泄漏也绝不二次释放。 */
static int pp_data_in2(int fd, unsigned short op, unsigned int tid,
                       unsigned int *params, int nparams,
                       unsigned char **data_out, int *data_len_out,
                       unsigned int *rc_out) {
    *data_out = NULL;
    *data_len_out = 0;
    if (rc_out) *rc_out = 0;
    int len = 18 + nparams * 4;
    unsigned char *req = (unsigned char *) malloc(len);
    if (!req) return -1;
    memset(req, 0, len);
    req[0] = len & 0xff; req[1] = (len >> 8) & 0xff; req[2] = (len >> 16) & 0xff; req[3] = (len >> 24) & 0xff;
    req[4] = PP_OP_REQ;                                      /* [5..7] 保持 0 */
    req[8] = 1;                                              /* DataPhaseInfo = 1 (data-in)，u32 */
    req[12] = op & 0xff; req[13] = (op >> 8) & 0xff;         /* opcode u16 */
    req[14] = tid & 0xff; req[15] = (tid >> 8) & 0xff; req[16] = (tid >> 16) & 0xff; req[17] = (tid >> 24) & 0xff;
    for (int i = 0; i < nparams; i++) {
        req[18 + i * 4 + 0] = params[i] & 0xff;
        req[18 + i * 4 + 1] = (params[i] >> 8) & 0xff;
        req[18 + i * 4 + 2] = (params[i] >> 16) & 0xff;
        req[18 + i * 4 + 3] = (params[i] >> 24) & 0xff;
    }
    int rc = pp_cmd_frame(fd, req, len);
    free(req); req = NULL;
    if (rc != 0) return -1;

    unsigned char *pl = NULL; int plen = 0;
    int type = pp_read_frame(fd, &pl, &plen);
    if (type != PP_START_DATA) {
        if (type == PP_OP_RESP && plen >= 2 && rc_out) *rc_out = pl[0] | (pl[1] << 8);
        free(pl); return -3;
    }
    unsigned long long total = 0;
    if (plen >= 12) memcpy(&total, pl + 4, 8);
    free(pl); pl = NULL;
    if (total > 64ull * 1024 * 1024) return -3;             /* 总量 sanity 上限 */

    unsigned char *acc = (unsigned char *) malloc((size_t) total + 1);
    if (!acc) return -3;
    unsigned long long got = 0;
    while (got < total) {
        type = pp_read_frame(fd, &pl, &plen);
        if (type != PP_DATA && type != PP_END_DATA) { free(pl); free(acc); return -4; }
        if (plen >= 4) {
            unsigned long long chunk = (unsigned long long) plen - 4;
            if (got + chunk > total) chunk = total - got;   /* 防止超出 total 的堆溢出 */
            memcpy(acc + got, pl + 4, (size_t) chunk);
            got += chunk;
        }
        free(pl); pl = NULL;
    }
    type = pp_read_frame(fd, &pl, &plen);
    int rtype = type;
    unsigned int rc16 = (plen >= 2) ? (pl[0] | (pl[1] << 8)) : 0;
    free(pl); pl = NULL;
    if (rtype != PP_OP_RESP) { free(acc); return -5; }
    if (rc_out) *rc_out = rc16;
    if (rc16 != 0x2001) { free(acc); return -6; }
    *data_out = acc;
    *data_len_out = (int) got;
    return 0;
}

/* 兼容封装：不关心 rc 的调用方 */
static int pp_data_in(int fd, unsigned short op, unsigned int tid,
                      unsigned int *params, int nparams,
                      unsigned char **data_out, int *data_len_out) {
    return pp_data_in2(fd, op, tid, params, nparams, data_out, data_len_out, NULL);
}

/* 无数据事务：请求 → OpResponse。rc_out 非 NULL 时回传相机响应码
 * （0x2001=OK；0x2009=对象写保护；其余见 PTP 响应码表）。 */
static int pp_cmd_in_rc(int fd, unsigned short op, unsigned int tid,
                        unsigned int *params, int nparams,
                        unsigned int *rc_out) {
    if (rc_out) *rc_out = 0;
    int len = 18 + nparams * 4;
    unsigned char *req = (unsigned char *) malloc(len);
    if (!req) return -1;
    memset(req, 0, len);
    req[0] = len & 0xff; req[1] = (len >> 8) & 0xff; req[2] = (len >> 16) & 0xff; req[3] = (len >> 24) & 0xff;
    req[4] = PP_OP_REQ;                                      /* [5..7] 保持 0 */
    req[8] = 1;                                              /* DataPhaseInfo = 1，u32 */
    req[12] = op & 0xff; req[13] = (op >> 8) & 0xff;         /* opcode u16 */
    req[14] = tid & 0xff; req[15] = (tid >> 8) & 0xff; req[16] = (tid >> 16) & 0xff; req[17] = (tid >> 24) & 0xff;
    for (int i = 0; i < nparams; i++) {
        req[18 + i * 4 + 0] = params[i] & 0xff;
        req[18 + i * 4 + 1] = (params[i] >> 8) & 0xff;
        req[18 + i * 4 + 2] = (params[i] >> 16) & 0xff;
        req[18 + i * 4 + 3] = (params[i] >> 24) & 0xff;
    }
    int rc = pp_cmd_frame(fd, req, len);
    free(req);
    req = NULL;
    if (rc != 0) return -1;
    unsigned char *pl = NULL; int plen = 0;
    int type = pp_read_frame(fd, &pl, &plen);
    int r = (type == PP_OP_RESP) ? 0 : -2;
    if (r == 0 && plen >= 2) {
        unsigned int rc16 = pl[0] | (pl[1] << 8);
        if (rc_out) *rc_out = rc16;
        if (rc16 != 0x2001) r = -6;
    }
    free(pl);
    return r;
}

/* 兼容封装：不关心 rc 的调用方 */
static int pp_cmd_in(int fd, unsigned short op, unsigned int tid,
                     unsigned int *params, int nparams) {
    return pp_cmd_in_rc(fd, op, tid, params, nparams, NULL);
}

/* data-out 事务（任务 5：0x1016 SetDevicePropValue）：请求(DataPhaseInfo=2)
 * → StartData → EndData（值很小，单包发完）→ OpResponse。
 * 帧格式与相机发来的镜像一致：StartData 载荷 = tid(u32) + 总长(u64)；
 * Data/EndData 载荷 = tid(u32) + 数据（对齐 pp_data_in2 的解析路径）。 */
static int pp_data_out(int fd, unsigned short op, unsigned int tid,
                       unsigned int *params, int nparams,
                       const unsigned char *data, int datalen,
                       unsigned int *rc_out) {
    if (rc_out) *rc_out = 0;
    int len = 18 + nparams * 4;
    unsigned char *req = (unsigned char *) malloc(len);
    if (!req) return -1;
    memset(req, 0, len);
    req[0] = len & 0xff; req[1] = (len >> 8) & 0xff; req[2] = (len >> 16) & 0xff; req[3] = (len >> 24) & 0xff;
    req[4] = PP_OP_REQ;
    req[8] = 2;                                              /* DataPhaseInfo = 2 (data-out)，u32 */
    req[12] = op & 0xff; req[13] = (op >> 8) & 0xff;
    req[14] = tid & 0xff; req[15] = (tid >> 8) & 0xff; req[16] = (tid >> 16) & 0xff; req[17] = (tid >> 24) & 0xff;
    for (int i = 0; i < nparams; i++) {
        req[18 + i * 4 + 0] = params[i] & 0xff;
        req[18 + i * 4 + 1] = (params[i] >> 8) & 0xff;
        req[18 + i * 4 + 2] = (params[i] >> 16) & 0xff;
        req[18 + i * 4 + 3] = (params[i] >> 24) & 0xff;
    }
    int rc = pp_cmd_frame(fd, req, len);
    free(req); req = NULL;
    if (rc != 0) return -1;

    /* StartData：载荷 = tid(u32) + total(u64) 共 12 字节，紧跟 8 字节帧头 →
     * 帧长 20。⚠️ 首版把 tid 放在帧偏移 12（载荷前多垫 4 个零字节、帧长 24），
     * 相机把 tid 当成 total 高位 → 写参数全部被拒（ZENCHE PtpIpCamera
     * sendCommandWithDataOut 为准：startData.u32(tx); startData.u64(len)）。 */
    unsigned long long total = (unsigned long long) datalen;
    unsigned char sd[20];
    memset(sd, 0, sizeof(sd));
    sd[0] = 20;
    sd[4] = PP_START_DATA;
    sd[8] = tid & 0xff; sd[9] = (tid >> 8) & 0xff; sd[10] = (tid >> 16) & 0xff; sd[11] = (tid >> 24) & 0xff;
    sd[12] = total & 0xff; sd[13] = (total >> 8) & 0xff; sd[14] = (total >> 16) & 0xff; sd[15] = (total >> 24) & 0xff;
    /* total 高 32 位恒 0（值 ≤ 4 字节），sd[16..19] 保持 0 */
    if (pp_write_all(fd, sd, 20) != 0) return -1;

    /* EndData：tid(u32) + 数据（值 ≤ 8 字节单包发完） */
    int elen = 8 + 4 + datalen;
    unsigned char *ed = (unsigned char *) malloc((size_t) elen);
    if (!ed) return -1;
    memset(ed, 0, elen);
    ed[0] = elen & 0xff; ed[1] = (elen >> 8) & 0xff; ed[2] = (elen >> 16) & 0xff; ed[3] = (elen >> 24) & 0xff;
    ed[4] = PP_END_DATA;
    ed[8] = tid & 0xff; ed[9] = (tid >> 8) & 0xff; ed[10] = (tid >> 16) & 0xff; ed[11] = (tid >> 24) & 0xff;
    memcpy(ed + 12, data, (size_t) datalen);
    rc = pp_write_all(fd, ed, elen);
    free(ed); ed = NULL;
    if (rc != 0) return -1;

    unsigned char *pl = NULL; int plen = 0;
    int type = pp_read_frame(fd, &pl, &plen);
    int r = (type == PP_OP_RESP) ? 0 : -2;
    if (r == 0 && plen >= 2) {
        unsigned int rc16 = pl[0] | (pl[1] << 8);
        if (rc_out) *rc_out = rc16;
        if (rc16 != 0x2001) r = -6;
    }
    free(pl);
    return r;
}

JNIEXPORT jint JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativePairingProbe(
        JNIEnv *env, jclass clz, jstring jIp) {
    const char *ip = (*env)->GetStringUTFChars(env, jIp, 0);
    int result = -100;

    /* 身份（抓包逐字节确认）：GUID 字段 = 16 字节 ASCII 十六进制串，
     * 名字 = "NikonXfer"（协议字面量，9 字节）。
     * 相机向导按该身份推进配对状态机；固定值保证跨会话一致。 */
    unsigned char guid[16];
    memcpy(guid, "3b4b6cbf0ea04a2d", 16);

    int cmdfd = pp_socket(ip, 4000);
    if (cmdfd < 0) { (*env)->ReleaseStringUTFChars(env, jIp, ip); return -101; }

    /* INIT_COMMAND_REQUEST（实测 48 字节）：
     * [0..3]=len [4..7]=type(1) [8..23]=GUID(16B ASCII) [24..]=名字 UTF-16LE+NUL
     * 末尾 [44..47]=协议版本 u32 0x00010000 (1.0) */
    const char *name = "NikonXfer";
    int namelen = (int) strlen(name);            /* 9 */
    int ilen = 8 + 16 + 2 * (namelen + 1) + 4;   /* = 48 */
    unsigned char init[64];
    memset(init, 0, sizeof(init));
    init[0] = ilen & 0xff; init[4] = PP_INIT_CMD;
    memcpy(init + 8, guid, 16);
    for (int i = 0; i < namelen; i++) {
        init[24 + i * 2] = (unsigned char) name[i];
        init[25 + i * 2] = 0;
    }
    /* [24+2*namelen .. +1] 为 NUL 终止符（已 0） */
    unsigned int ver = 0x00010000;
    int voff = 24 + 2 * (namelen + 1);           /* = 44 */
    memcpy(init + voff, &ver, 4);
    if (pp_write_all(cmdfd, init, ilen) != 0) { close(cmdfd); (*env)->ReleaseStringUTFChars(env, jIp, ip); return -102; }

    unsigned char *pl = NULL; int plen = 0;
    unsigned int conn = 0;
    if (pp_expect(cmdfd, PP_INIT_CMD_ACK, &pl, &plen) != 0 || plen < 4) {
        free(pl); pl = NULL;
        close(cmdfd);
        (*env)->ReleaseStringUTFChars(env, jIp, ip);
        return -103;
    }
    memcpy(&conn, pl, 4);
    free(pl); pl = NULL;                    /* 置 NULL：防止后续失败路径二次释放 */
    LOGI("probe: INIT ack, connectionNumber=%u", conn);

    /* event 通道 */
    int evtfd = pp_socket(ip, 4000);
    if (evtfd < 0) { close(cmdfd); (*env)->ReleaseStringUTFChars(env, jIp, ip); return -104; }
    unsigned char evtreq[12];
    memset(evtreq, 0, sizeof(evtreq));      /* 全零初始化：长度/类型高位字节不能是栈垃圾 */
    evtreq[0] = 12; evtreq[4] = PP_INIT_EVT;
    memcpy(evtreq + 8, &conn, 4);
    if (pp_write_all(evtfd, evtreq, 12) != 0) { close(evtfd); close(cmdfd); (*env)->ReleaseStringUTFChars(env, jIp, ip); return -105; }
    if (pp_expect(evtfd, PP_INIT_EVT_ACK, &pl, &plen) != 0) {
        free(pl); pl = NULL;                /* pp_expect 失败时 pl 保持 NULL，此处幂等 */
        close(evtfd); close(cmdfd);
        (*env)->ReleaseStringUTFChars(env, jIp, ip);
        return -106;
    }
    free(pl); pl = NULL;
    LOGI("probe: event channel ready");

    /* 事务序列 */
    unsigned int tid = 0;
    unsigned char *data = NULL; int dlen = 0;
    unsigned int p1 = 1;
    do {
        if (pp_cmd_in(cmdfd, 0x1002, tid++, &p1, 1) != 0) { result = -1; break; }         /* OpenSession */
        LOGI("probe: OpenSession OK");
        if (pp_data_in(cmdfd, 0x941c, tid++, NULL, 0, &data, &dlen) != 0) { result = -2; break; }
        if (dlen >= 4) {
            LOGI("probe: 0x941c OK (%d B: %02x%02x%02x%02x)", dlen, data[0], data[1], data[2], data[3]);
        } else {
            LOGI("probe: 0x941c OK (%d B)", dlen);
        }
        free(data); data = NULL;
        if (pp_data_in(cmdfd, 0x1004, tid++, NULL, 0, &data, &dlen) != 0) { result = -3; break; }  /* GetStorageIDs */
        LOGI("probe: GetStorageIDs OK (%d B)", dlen);
        free(data); data = NULL;
        if (pp_data_in(cmdfd, 0x1001, tid++, NULL, 0, &data, &dlen) != 0) { result = -4; break; }  /* GetDeviceInfo */
        LOGI("probe: GetDeviceInfo OK (%d B)", dlen);
        free(data); data = NULL;
        if (pp_data_in(cmdfd, 0x952b, tid++, NULL, 0, &data, &dlen) != 0) { result = -5; break; }
        LOGI("probe: 0x952b OK (%d B)", dlen);
        free(data); data = NULL;
        unsigned int pm = 0x2001;
        if (pp_cmd_in(cmdfd, 0x935a, tid++, &pm, 1) != 0) { result = -6; break; }
        LOGI("probe: 0x935a(0x2001) OK");
        /* CloseSession 尽力而为：相机收到 0x935a 后可能主动断开，无响应不算失败 */
        unsigned int none = 0;
        if (pp_cmd_in(cmdfd, 0x1003, tid++, &none, 0) != 0)
            LOGI("probe: CloseSession#1 no response (ok)");
        if (pp_cmd_in(cmdfd, 0x1003, tid++, &none, 0) != 0)
            LOGI("probe: CloseSession#2 no response (ok)");
        result = 0;
        LOGI("probe: pairing sequence complete");
    } while (0);
    if (data) free(data);
    close(evtfd);
    close(cmdfd);

    /* ---- 常驻传输会话（正式实现，替代实验代码）----
     * 配对后相机切换到传输模式：
     *   GetObjectHandles(all,all,root) → 真实文件句柄（0x2a1c*=JPG 0x0a1c*=NEF）
     *   0x9421(句柄) → u64 对象总大小
     *   0x9431(句柄,off_lo,off_hi,size,0) → 原始数据块（实测 64KB/次）
     *   0x9434(存储ID,0,0) → [长度][数量][句柄+0+时间+日期]×N（时间戳清单）
     * 会话保持打开，由 nativeListNative / nativeDownloadNative /
     * nativeEventPollNative / nativeTransferClose 使用。 */
    if (result == 0) {
        usleep(300 * 1000);
        if (g_tfd >= 0) { close(g_tfd); g_tfd = -1; }
        if (g_tefd >= 0) { close(g_tefd); g_tefd = -1; }
        g_ttid = 0; g_nstorages = 0;

        int tfd = pp_socket(ip, 4000);
        if (tfd >= 0) {
            unsigned int tconn = 0;
            if (pp_write_all(tfd, init, ilen) == 0 &&
                pp_expect(tfd, PP_INIT_CMD_ACK, &pl, &plen) == 0 && plen >= 4) {
                memcpy(&tconn, pl, 4);
                free(pl); pl = NULL;
                int tefd = pp_socket(ip, 4000);
                if (tefd >= 0) {
                    unsigned char tereq[12];
                    memset(tereq, 0, sizeof(tereq));
                    tereq[0] = 12; tereq[4] = PP_INIT_EVT;
                    memcpy(tereq + 8, &tconn, 4);
                    if (pp_write_all(tefd, tereq, 12) != 0 ||
                        pp_expect(tefd, PP_INIT_EVT_ACK, &pl, &plen) != 0) {
                        close(tefd); tefd = -1;
                    }
                    free(pl); pl = NULL;
                }
                if (tefd >= 0) {
                    unsigned int p1 = 1;
                    unsigned int tid2 = 0;
                    int ok = (pp_cmd_in(tfd, 0x1002, tid2++, &p1, 1) == 0);   /* OpenSession */
                    unsigned char *d2 = NULL; int d2len = 0;
                    if (ok && pp_data_in(tfd, 0x941c, tid2++, NULL, 0, &d2, &d2len) == 0)
                        LOGI("tsession: 0x941c OK (%d B)", d2len);
                    else ok = 0;
                    free(d2); d2 = NULL;
                    if (ok && pp_data_in(tfd, 0x1004, tid2++, NULL, 0, &d2, &d2len) == 0) {
                        if (d2len >= 4) {
                            int cnt = 0; memcpy(&cnt, d2, 4);
                            if (cnt > 4) cnt = 4;
                            for (int i = 0; i < cnt && d2len >= 4 + (i + 1) * 4; i++)
                                memcpy(&g_storages[i], d2 + 4 + i * 4, 4);
                            g_nstorages = cnt;
                        }
                        LOGI("tsession: GetStorageIDs OK (%d B, %d 存储)", d2len, g_nstorages);
                        free(d2); d2 = NULL;
                    } else ok = 0;
                    free(d2); d2 = NULL;
                    if (ok && pp_data_in(tfd, 0x1001, tid2++, NULL, 0, &d2, &d2len) == 0) {
                        LOGI("tsession: GetDeviceInfo OK (%d B)", d2len);
                        free(d2); d2 = NULL;
                    }
                    free(d2); d2 = NULL;
                    if (ok) {
                        g_tfd = tfd; g_tefd = tefd; g_ttid = tid2;
                        LOGI("tsession: transfer session ready");
                    } else {
                        LOGE("tsession: open failed");
                        close(tefd); close(tfd);
                    }
                } else {
                    LOGE("tsession: event channel failed");
                    close(tfd);
                }
            } else {
                free(pl); pl = NULL;
                LOGE("tsession: INIT failed");
                close(tfd);
            }
        } else {
            LOGE("tsession: connect failed");
        }
    }

    (*env)->ReleaseStringUTFChars(env, jIp, ip);
    return result;
}

/* ---------- 原生传输路径：列目录 / 下载 / 事件 / 关闭 ---------- */

static void transfer_close_locked(void) {
    if (g_tfd >= 0) { close(g_tfd); g_tfd = -1; }
    if (g_tefd >= 0) { close(g_tefd); g_tefd = -1; }
    g_ttid = 0;
}

JNIEXPORT void JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeTransferClose(JNIEnv *env, jclass clz) {
    (void) env; (void) clz;
    transfer_close_locked();
    LOGI("tsession: closed");
}

/* 列目录：句柄+时间戳（0x9434 一次拿全）→ "句柄:YYYYMMDD-HHMMSS:类型|..."，
 * 不做逐对象 0x9421（数百对象太慢），大小在下载时再取。
 * 枚举必须递归：实测部分机型 PTP-IP 上 GetObjectHandles(parent=0) 只回当前
 * 文件夹（82/886），需像 USB 侧一样逐层 association 下钻（BFS ≤3 层）。 */
static int cmp_u32(const void *a, const void *b) {
    unsigned int x = *(const unsigned int *) a, y = *(const unsigned int *) b;
    return (x < y) ? -1 : (x > y) ? 1 : 0;
}
/* 文件句柄位段：hi16 高 8 位 0x2a=JPG、0x0a=NEF；低 8 位是文件夹段
 * （实测 Z7=0x1c，新款跨文件夹 0x1c/0x1d……硬编码 0x1c 会丢整个旧文件夹）。 */
#define HI_IS_JPG(hi) (((hi) & 0xff00u) == 0x2a00u)
#define HI_IS_NEF(hi) (((hi) & 0xff00u) == 0x0a00u)
JNIEXPORT jstring JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeListNative(JNIEnv *env, jclass clz) {
    (void) clz;
    if (g_tfd < 0) return NULL;

    /* 时间戳表：句柄 → "YYYYMMDD-HHMMSS"。0x9434 全量收集（堆分配）；
     * 早期版本固定 256 截断，886 张会丢 630+ 的时间戳 → 日期分组全错。 */
    typedef struct { unsigned int handle; char stamp[24]; } StampEnt;
    const int MAX_STAMPS = 4096;
    StampEnt *stamps = (StampEnt *) malloc(sizeof(StampEnt) * MAX_STAMPS);
    const int MAX_FILES = 16384;
    unsigned int *files = (unsigned int *) malloc(sizeof(unsigned int) * MAX_FILES);
    const int MAX_QUEUE = 8192;
    unsigned int *queue = (unsigned int *) malloc(sizeof(unsigned int) * MAX_QUEUE);
    if (!stamps || !files || !queue) {
        free(stamps); free(files); free(queue);
        return NULL;
    }
    int nstamps = 0, nf = 0, qh = 0, qt = 0;

    for (int s = 0; s < g_nstorages && s < 4; s++) {
        unsigned int sp[3] = { g_storages[s], 0, 0 };
        unsigned char *sd = NULL; int sdlen = 0;
        if (pp_data_in2(g_tfd, 0x9434, g_ttid++, sp, 3, &sd, &sdlen, NULL) != 0) continue;
        if (sdlen >= 8) {
            int cnt = 0; memcpy(&cnt, sd + 4, 4);
            for (int i = 0; i < cnt && nstamps < MAX_STAMPS && sdlen >= 8 + (i + 1) * 16; i++) {
                unsigned int h, t1, t2;
                memcpy(&h,  sd + 8 + i * 16 + 0, 4);
                memcpy(&t1, sd + 8 + i * 16 + 8, 4);
                memcpy(&t2, sd + 8 + i * 16 + 12, 4);
                unsigned int sec  = (t1 >> 8) & 0xff, min = (t1 >> 16) & 0xff, hour = (t1 >> 24) & 0xff;
                unsigned int day  = t2 & 0xff, mon = (t2 >> 8) & 0xff, year = (t2 >> 16) & 0xffff;
                stamps[nstamps].handle = h;
                snprintf(stamps[nstamps].stamp, sizeof(stamps[nstamps].stamp),
                         "%04u%02u%02u-%02u%02u%02u", year, mon, day, hour, min, sec);
                nstamps++;
            }
        }
        free(sd);
    }
    LOGI("list: 时间戳表 %d 项", nstamps);

    /* 递归枚举（BFS ≤3 层）：parent=0 根层起步；非 JPG/NEF 位段句柄视为
     * association/标记，下钻取文件（标记句柄子层为空，无害）。 */
    unsigned int ohp[3] = {0xffffffffu, 0xffffffffu, 0x00000000u};
    unsigned char *d = NULL; int dlen = 0;
    if (pp_data_in2(g_tfd, 0x1007, g_ttid++, ohp, 3, &d, &dlen, NULL) != 0 || !d) {
        LOGE("list: GetObjectHandles failed");
        free(stamps); free(files); free(queue);
        return NULL;
    }
    int n = dlen / 4;
    for (int i = 0; i < n; i++) {
        unsigned int h; memcpy(&h, d + i * 4, 4);
        unsigned int hi16 = (h >> 16) & 0xffff;
        if (HI_IS_JPG(hi16) || HI_IS_NEF(hi16)) { if (nf < MAX_FILES) files[nf++] = h; }
        else if (qt < MAX_QUEUE) queue[qt++] = h;
    }
    free(d);
    int layer_end = qt, depth = 0;
    while (qh < qt && depth < 3) {
        unsigned int parent = queue[qh++];
        unsigned char *d2 = NULL; int dl2 = 0;
        unsigned int php[3] = {0xffffffffu, 0x00000000u, parent};
        if (pp_data_in2(g_tfd, 0x1007, g_ttid++, php, 3, &d2, &dl2, NULL) == 0 && d2) {
            int n2 = dl2 / 4;
            for (int i = 0; i < n2; i++) {
                unsigned int h; memcpy(&h, d2 + i * 4, 4);
                unsigned int hi16 = (h >> 16) & 0xffff;
                if (HI_IS_JPG(hi16) || HI_IS_NEF(hi16)) { if (nf < MAX_FILES) files[nf++] = h; }
                else if (qt < MAX_QUEUE) queue[qt++] = h;
            }
        }
        free(d2);
        if (qh == layer_end) { layer_end = qt; depth++; }
    }
    /* 0x9434 表兜底：它本身就是相机的全量文件清单（含时间）。
     * 实测部分机型 PTP-IP 上 0x1007 只回当前文件夹（82/886）且下钻不到
     * 其余目录——把表里 JPG/NEF 位段的句柄并入清单，与递归结果取并集。 */
    for (int k = 0; k < nstamps; k++) {
        unsigned int h = stamps[k].handle;
        unsigned int hi16 = (h >> 16) & 0xffff;
        if ((HI_IS_JPG(hi16) || HI_IS_NEF(hi16)) && nf < MAX_FILES) files[nf++] = h;
    }
    /* 去重：部分固件 parent=0 返回跨层级对象，递归收集后会重复（重复句柄会让 UI 崩溃） */
    if (nf > 1) {
        qsort(files, (size_t) nf, sizeof(unsigned int), cmp_u32);
        int w2 = 0;
        for (int i = 0; i < nf; i++)
            if (w2 == 0 || files[i] != files[w2 - 1]) files[w2++] = files[i];
        nf = w2;
    }
    LOGI("list: 最终文件 %d 个（根层 %d，递归访问 %d 层队列 %d，时间戳表 %d）",
         nf, n, depth, qh, nstamps);

    const size_t cap = (size_t) (nf > 0 ? nf : 1) * 48 + 64;
    char *out = (char *) malloc(cap);
    if (!out) { free(stamps); free(files); free(queue); return NULL; }
    size_t o = 0;
    out[0] = 0;
    for (int i = 0; i < nf; i++) {
        unsigned int h = files[i];
        const char *type = HI_IS_JPG((h >> 16) & 0xffff) ? "JPG" : "NEF";
        const char *stamp = "00000000-000000";
        for (int k = 0; k < nstamps; k++)
            if (stamps[k].handle == h) { stamp = stamps[k].stamp; break; }
        int w = snprintf(out + o, cap - o, "%s%u:%s:%s", o ? "|" : "", h, stamp, type);
        if (w < 0 || (size_t) w >= cap - o) break;
        o += (size_t) w;
    }
    free(stamps); free(files); free(queue);
    LOGI("list: 输出 %zu 字节", o);
    jstring js = (*env)->NewStringUTF(env, out);
    free(out);
    return js;
}

/* ==================== 拍摄后增量刷新（只拉新对象，不全量枚举） ==================== */

/* ObjectAdded 句柄暂存：0x941c 保活与 0x90c7 轮询都会消费相机事件队列，
 * 谁消费谁入缓冲，nativePollObjectAddedNative 汇总返回——句柄不因消费方
 * 不同而丢失。camMutex 串行保护，无需加锁。 */
#define PENDING_MAX 64
static unsigned int g_pending[PENDING_MAX];
static int g_pend_n = 0;
static int pending_has(unsigned int h) {
    for (int i = 0; i < g_pend_n; i++) if (g_pending[i] == h) return 1;
    return 0;
}
static void pending_push(unsigned int h) {
    if (pending_has(h)) return;
    if (g_pend_n < PENDING_MAX) g_pending[g_pend_n++] = h;
}
/* 事件 dataset 防御性解析：0x90c7 = u16 count、0x941c 实测空事件为 4B
 * （u32 count 布局推断）。两种布局都尝试，count 与长度不匹配即放弃；
 * 只收 ObjectAdded(0x4002)/0xC101 白名单，错位顶多漏事件不会崩。 */
static void pending_collect(const unsigned char *d, int dlen, int cnt_u32) {
    int cnt, off;
    if (cnt_u32) {
        if (dlen < 4) return;
        memcpy(&cnt, d, 4);
        off = 4;
    } else {
        if (dlen < 2) return;
        cnt = d[0] | (d[1] << 8);
        off = 2;
    }
    if (cnt < 0 || cnt > 4096 || off + cnt * 6 > dlen) return;   /* 布局不符 → 放弃 */
    for (int i = 0; i < cnt; i++, off += 6) {
        unsigned code = (unsigned) d[off] | ((unsigned) d[off + 1] << 8);
        unsigned param = (unsigned) d[off + 2] | ((unsigned) d[off + 3] << 8)
                       | ((unsigned) d[off + 4] << 16) | ((unsigned) d[off + 5] << 24);
        if (code == 0x4002 || code == 0xC101) pending_push(param);
    }
}

/* UTF-16LE PSTR（u1 长度含 NUL + N×2 字节）在 off 处读出 ASCII；返回下一偏移，-1=无效 */
static int pstr16_at(const unsigned char *d, int dlen, int off, char *out, int outsz) {
    if (off < 0 || off >= dlen) return -1;
    int n = d[off];
    if (n <= 0 || off + 1 + n * 2 > dlen) return -1;
    int w = 0;
    for (int i = 0; i < n && w < outsz - 1; i++) {
        unsigned short ch = (unsigned short) (d[off + 1 + i * 2] | (d[off + 2 + i * 2] << 8));
        out[w++] = (char) ch;
    }
    out[w] = 0;
    return off + 1 + n * 2;
}

/* 0x9434 全表兜底：单对象 0x1014 被拒/无日期时查表取 stamp。找到返回 1。 */
static int table_stamp(unsigned int handle, char *stamp, size_t sz) {
    for (int s = 0; s < g_nstorages && s < 4; s++) {
        unsigned int sp[3] = { g_storages[s], 0, 0 };
        unsigned char *sd = NULL; int sdlen = 0;
        if (pp_data_in2(g_tfd, 0x9434, g_ttid++, sp, 3, &sd, &sdlen, NULL) != 0) continue;
        if (sdlen >= 8) {
            int cnt = 0; memcpy(&cnt, sd + 4, 4);
            for (int i = 0; i < cnt && sdlen >= 8 + (i + 1) * 16; i++) {
                unsigned int h, t1, t2;
                memcpy(&h,  sd + 8 + i * 16 + 0, 4);
                memcpy(&t1, sd + 8 + i * 16 + 8, 4);
                memcpy(&t2, sd + 8 + i * 16 + 12, 4);
                if (h != handle) continue;
                unsigned int sec  = (t1 >> 8) & 0xff, min = (t1 >> 16) & 0xff, hour = (t1 >> 24) & 0xff;
                unsigned int day  = t2 & 0xff, mon = (t2 >> 8) & 0xff, year = (t2 >> 16) & 0xffff;
                snprintf(stamp, sz, "%04u%02u%02u-%02u%02u%02u", year, mon, day, hour, min, sec);
                free(sd);
                return 1;
            }
        }
        free(sd);
    }
    return 0;
}

/* 单对象信息行 "h:stamp:type"（拍摄后增量刷新）。
 * ⚠️ 不做句柄位段预判：Z6 II 新建拍摄文件夹后 JPG 段基址会漂移
 * （实测新拍 JPG = 0x2453xxxx，旧白名单 0x2a00 会误拒）。
 * 主路径 0x1014 GetObjectInfo：文件名后缀判 type、CaptureDate 取 stamp；
 * 0x1014 失败时 0x9434 表兜底 stamp + 旧位段白名单猜 type（猜不出 → NULL
 * 回退全量枚举）。 */
JNIEXPORT jstring JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeObjectInfoNative(
        JNIEnv *env, jclass clz, jint handle) {
    (void) clz;
    if (g_tfd < 0) return NULL;
    unsigned int h = (unsigned int) handle;
    char stamp[24] = "00000000-000000";
    char type[4] = "";
    unsigned char *d = NULL; int dlen = 0;
    unsigned int p[1] = { h };
    if (pp_data_in2(g_tfd, 0x1014, g_ttid++, p, 1, &d, &dlen, NULL) == 0 && d) {
        /* 尼康固件 ObjectInfo 头部偶有 ±字节差异：44..96 窗口找 ".JPG/.NEF"
         * 结尾的文件名 PSTR，其后紧跟 CaptureDate（"YYYYMMDDTHHMMSS"） */
        for (int off = 44; off <= 96 && off + 2 < dlen; off++) {
            char fname[128], cap[40];
            int next = pstr16_at(d, dlen, off, fname, sizeof(fname));
            if (next < 0) continue;
            int fl = (int) strlen(fname);
            if (fl < 4) continue;
            const char *ext = fname + fl - 4;
            if (!strcasecmp(ext, ".JPG")) snprintf(type, sizeof(type), "JPG");
            else if (!strcasecmp(ext, ".NEF")) snprintf(type, sizeof(type), "NEF");
            else continue;
            int next2 = pstr16_at(d, dlen, next, cap, sizeof(cap));
            if (next2 >= 0 && (int) strlen(cap) == 15 && cap[8] == 'T')
                snprintf(stamp, sizeof(stamp), "%.8s-%.6s", cap, cap + 9);
            break;
        }
        free(d);
    } else {
        free(d);
    }
    if (!type[0]) {
        if (!table_stamp(h, stamp, sizeof(stamp))) return NULL;
        unsigned int hi16 = (h >> 16) & 0xffff;
        if (HI_IS_JPG(hi16)) snprintf(type, sizeof(type), "JPG");
        else if (HI_IS_NEF(hi16)) snprintf(type, sizeof(type), "NEF");
        else return NULL;                     /* 段位漂移且 0x1014 也失败 → 放弃 */
    }
    char line[64];
    snprintf(line, sizeof(line), "%u:%s:%s", h, stamp, type);
    LOGI("objinfo: %s", line);
    return (*env)->NewStringUTF(env, line);
}

/* 0x9434 时间戳全表："h:YYYYMMDD-HHMMSS|..."（拍后增量差集刷新用）。
 * 单事务拉全表（千张 ~14KB），远轻于全量 list 的多事务 BFS 递归。
 * 0x9434 表是相机维护的权威清单——新照片写卡完成才入表，差集即真新照片
 * （事件 ObjectAdded 在拍进卡模式下多为 SDRAM 临时 id，0x1014 查无此对象，
 * 不可作差集依据）。NULL = 拉取失败（上层回退全量枚举）。 */
JNIEXPORT jstring JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeStampTableNative(JNIEnv *env, jclass clz) {
    (void) clz;
    if (g_tfd < 0) return NULL;
    const size_t cap = (size_t) 4096 * 32 + 64;
    char *out = (char *) malloc(cap);
    if (!out) return NULL;
    size_t o = 0; out[0] = 0;
    for (int s = 0; s < g_nstorages && s < 4; s++) {
        unsigned int sp[3] = { g_storages[s], 0, 0 };
        unsigned char *sd = NULL; int sdlen = 0;
        if (pp_data_in2(g_tfd, 0x9434, g_ttid++, sp, 3, &sd, &sdlen, NULL) != 0) continue;
        if (sdlen >= 8) {
            int cnt = 0; memcpy(&cnt, sd + 4, 4);
            for (int i = 0; i < cnt && sdlen >= 8 + (i + 1) * 16; i++) {
                unsigned int h, t1, t2;
                memcpy(&h,  sd + 8 + i * 16 + 0, 4);
                memcpy(&t1, sd + 8 + i * 16 + 8, 4);
                memcpy(&t2, sd + 8 + i * 16 + 12, 4);
                unsigned int sec  = (t1 >> 8) & 0xff, min = (t1 >> 16) & 0xff, hour = (t1 >> 24) & 0xff;
                unsigned int day  = t2 & 0xff, mon = (t2 >> 8) & 0xff, year = (t2 >> 16) & 0xffff;
                if (year < 2000 || year > 2100 || mon < 1 || mon > 12 || day < 1 || day > 31) continue;
                char stamp[24];
                snprintf(stamp, sizeof(stamp), "%04u%02u%02u-%02u%02u%02u", year, mon, day, hour, min, sec);
                int w = snprintf(out + o, cap - o, "%s%u:%s", o ? "|" : "", h, stamp);
                if (w < 0 || (size_t) w >= cap - o) break;
                o += (size_t) w;
            }
        }
        free(sd);
    }
    if (o == 0) { free(out); return NULL; }
    jstring js = (*env)->NewStringUTF(env, out);
    free(out);
    return js;
}

/* 事件轮询（0x90c7 GetEvent）：返回 ObjectAdded(0x4002)/SDRAM(0xC101) 的对象句柄数组，
 * 先取缓冲（0x941c 保活消费进来的）再发 0x90c7 补充，合并去重。
 * dataset = u16 count + N×(u16 code + u32 param)。NULL = 通道拒绝/传输失败。 */
JNIEXPORT jintArray JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativePollObjectAddedNative(JNIEnv *env, jclass clz) {
    (void) clz;
    if (g_tfd < 0) return NULL;
    jint tmp[PENDING_MAX + 256];
    int n = 0;
    int cap = (int) (sizeof(tmp) / sizeof(tmp[0]));
    for (int i = 0; i < g_pend_n && n < cap; i++) tmp[n++] = (jint) g_pending[i];
    g_pend_n = 0;
    /* tmp 去重（缓冲本身无重复，但与 0x90c7 结果可能重叠） */
    int w = 0;
    for (int i = 0; i < n; i++) {
        int dup = 0;
        for (int j = 0; j < w; j++) if (tmp[j] == tmp[i]) { dup = 1; break; }
        if (!dup) tmp[w++] = tmp[i];
    }
    n = w;
    unsigned char *d = NULL; int dlen = 0;
    int r = pp_data_in2(g_tfd, 0x90C7, g_ttid++, NULL, 0, &d, &dlen, NULL);
    if (r == 0 && d) {
        pending_collect(d, dlen, 0);          /* 本轮收的入缓冲，再整体并入返回 */
        for (int i = 0; i < g_pend_n && n < cap; i++) tmp[n++] = (jint) g_pending[i];
        g_pend_n = 0;
    }
    free(d);
    if (r != 0 && n == 0) return NULL;        /* 通道失败且无存量 → 上层回退 */
    /* 最终去重 */
    w = 0;
    for (int i = 0; i < n; i++) {
        int dup = 0;
        for (int j = 0; j < w; j++) if (tmp[j] == tmp[i]) { dup = 1; break; }
        if (!dup) tmp[w++] = tmp[i];
    }
    jintArray out = (*env)->NewIntArray(env, w);
    if (!out) return NULL;
    (*env)->SetIntArrayRegion(env, out, 0, w, tmp);
    return out;
}

/* 下载：0x9421 取总大小 → 0x9431 按 64KB 分块循环 → 完整字节数组 */
JNIEXPORT jbyteArray JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeDownloadNative(
        JNIEnv *env, jclass clz, jint jHandle) {
    (void) clz;
    if (g_tfd < 0) return NULL;
    unsigned int handle = (unsigned int) jHandle;

    unsigned char *vd = NULL; int vdlen = 0;
    unsigned long long total = 0;
    if (pp_data_in2(g_tfd, 0x9421, g_ttid++, &handle, 1, &vd, &vdlen, NULL) != 0 || vdlen < 8) {
        LOGE("download: 0x9421 failed");
        free(vd);
        return NULL;
    }
    memcpy(&total, vd, 8);
    free(vd);
    if (total == 0 || total > 200ull * 1024 * 1024) {
        LOGE("download: 大小异常 %llu", total);
        return NULL;
    }
    LOGI("download: 0x%08x 总大小 %llu B", handle, total);

    unsigned char *acc = (unsigned char *) malloc((size_t) total);
    if (!acc) return NULL;
    unsigned long long off = 0;
    int fail = 0;
    while (off < total) {
        unsigned long long chunk = 0x10000;
        if (off + chunk > total) chunk = total - off;
        unsigned int gp[5] = { handle,
                               (unsigned int)(off & 0xffffffffu),
                               (unsigned int)(off >> 32),
                               (unsigned int) chunk, 0 };
        unsigned char *gd = NULL; int gdlen = 0; unsigned int grc = 0;
        if (pp_data_in2(g_tfd, 0x9431, g_ttid++, gp, 5, &gd, &gdlen, &grc) != 0 || gdlen <= 0) {
            LOGE("download: offset %llu 失败 (rc=0x%04x len=%d)", off, grc, gdlen);
            free(gd); fail = 1; break;
        }
        unsigned long long take = (unsigned long long) gdlen;
        if (take > chunk) take = chunk;
        if (off + take > total) take = total - off;
        memcpy(acc + off, gd, (size_t) take);
        off += take;
        free(gd);
    }
    if (fail) { free(acc); return NULL; }

    jbyteArray arr = (*env)->NewByteArray(env, (jsize) total);
    if (arr) (*env)->SetByteArrayRegion(env, arr, 0, (jsize) total, (jbyte *) acc);
    free(acc);
    LOGI("download: 0x%08x 完成 (%llu B)", handle, total);
    return arr;
}

/* 事件消费（配对模式下 0x941c GetEventEx），维持会话健康 */
JNIEXPORT jint JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeEventPollNative(JNIEnv *env, jclass clz) {
    (void) env; (void) clz;
    if (g_tfd < 0) return -1;
    unsigned char *d = NULL; int dlen = 0;
    int r = pp_data_in2(g_tfd, 0x941c, g_ttid++, NULL, 0, &d, &dlen, NULL);
    if (r == 0 && d) pending_collect(d, dlen, 1);   /* 保活消费的事件不丢：ObjectAdded 入缓冲 */
    free(d);
    return (r == 0) ? 0 : -2;
}

/* 预览：取对象 [offset, offset+maxLen) 的原始数据（内含 EXIF/TIFF 嵌入的缩略图
 * JPEG），Kotlin 侧扫描完整 JPEG 段（FFD8..FFD9）解码。配对模式下没有专门的
 * 缩略图指令——预览网格就是从首块数据里抠出完整 JPEG 段。 */
JNIEXPORT jbyteArray JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativePreviewNative(
        JNIEnv *env, jclass clz, jint jHandle, jint jOffset, jint jMaxLen) {
    (void) clz;
    if (g_tfd < 0) return NULL;
    unsigned int handle = (unsigned int) jHandle;
    unsigned int offset = (unsigned int) jOffset;
    unsigned int maxLen = (unsigned int) jMaxLen;
    if (maxLen == 0 || maxLen > 1024u * 1024) return NULL;
    unsigned int gp[5] = { handle, offset, 0, maxLen, 0 };
    unsigned char *gd = NULL; int gdlen = 0; unsigned int grc = 0;
    if (pp_data_in2(g_tfd, 0x9431, g_ttid++, gp, 5, &gd, &gdlen, &grc) != 0 || gdlen <= 0) {
        LOGE("preview: 0x%08x@%u 失败 (rc=0x%04x len=%d)", handle, offset, grc, gdlen);
        free(gd);
        return NULL;
    }
    jbyteArray arr = (*env)->NewByteArray(env, (jsize) gdlen);
    if (arr) (*env)->SetByteArrayRegion(env, arr, 0, (jsize) gdlen, (jbyte *) gd);
    free(gd);
    LOGI("preview: 0x%08x@%u 取回 %d B", handle, offset, gdlen);
    return arr;
}

/* 对象总大小：0x9421 数据前 8 字节（u64）。下载分块循环用它算进度。 */
JNIEXPORT jlong JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeObjectSizeNative(
        JNIEnv *env, jclass clz, jint jHandle) {
    (void) clz;
    if (g_tfd < 0) return -1;
    unsigned int handle = (unsigned int) jHandle;
    unsigned char *d = NULL; int dlen = 0;
    if (pp_data_in2(g_tfd, 0x9421, g_ttid++, &handle, 1, &d, &dlen, NULL) != 0 || dlen < 8) {
        free(d);
        return -1;
    }
    unsigned long long sz = 0;
    memcpy(&sz, d, 8);
    free(d);
    return (jlong) sz;
}

/* 相机信息探测：只做 INIT 握手（不开会话，不打扰相机会话），读 InitCommandAck
 * 解析相机型号与序列号。返回 "型号|序列号"（如 "Z_6_2|8060212"），失败 NULL。
 * 网段扫描器用它给扫描结果显示「相机名 + IP」。 */
JNIEXPORT jstring JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeProbeCameraInfo(
        JNIEnv *env, jclass clz, jstring jIp) {
    const char *ip = (*env)->GetStringUTFChars(env, jIp, 0);
    jstring result = NULL;

    int fd = pp_socket(ip, 1500);
    if (fd < 0) { (*env)->ReleaseStringUTFChars(env, jIp, ip); return NULL; }

    /* INIT 帧（与配对探针同构） */
    const char *name = "NikonXfer";
    int namelen = (int) strlen(name);
    int ilen = 8 + 16 + 2 * (namelen + 1) + 4;
    unsigned char init[64];
    memset(init, 0, sizeof(init));
    init[0] = ilen & 0xff; init[4] = PP_INIT_CMD;
    memcpy(init + 8, "3b4b6cbf0ea04a2d", 16);
    for (int i = 0; i < namelen; i++) { init[24 + i * 2] = (unsigned char) name[i]; init[25 + i * 2] = 0; }
    unsigned int ver = 0x00010000;
    memcpy(init + 24 + 2 * (namelen + 1), &ver, 4);
    if (pp_write_all(fd, init, ilen) != 0) {
        close(fd); (*env)->ReleaseStringUTFChars(env, jIp, ip); return NULL;
    }

    unsigned char *pl = NULL; int plen = 0;
    if (pp_expect(fd, PP_INIT_CMD_ACK, &pl, &plen) != 0 || plen < 20) {
        free(pl); close(fd); (*env)->ReleaseStringUTFChars(env, jIp, ip); return NULL;
    }
    /* InitCommandAck: [conn u32][相机 GUID 16B][相机名 UTF-16LE NUL 结尾]
     * 实测相机名 = "型号_序列号" 一整串（如 "Z_6_2_8060212"），按最后一个
     * 下划线拆成 型号|序列号 显示。 */
    char cname[64] = {0}; int si = 0;
    for (int i = 20; i + 1 < plen && si < 60; i += 2) {
        unsigned char c = pl[i];
        if (c == 0) break;
        if (c >= 0x20 && c < 0x7f) cname[si++] = (char) c;
    }
    char mbuf[32] = {0};
    const char *model = "Nikon", *serial = cname;
    char *sep = strrchr(cname, '_');
    if (sep) {
        size_t ml = (size_t) (sep - cname);
        if (ml > 0 && ml < sizeof(mbuf)) { memcpy(mbuf, cname, ml); model = mbuf; }
        serial = sep + 1;
    }
    char out[128];
    snprintf(out, sizeof(out), "%s|%s", model, serial);
    result = (*env)->NewStringUTF(env, out);
    LOGI("probe-info: %s -> model=%s serial=%s", ip, model, serial);
    free(pl);
    close(fd);
    (*env)->ReleaseStringUTFChars(env, jIp, ip);
    return result;
}

/* 机内删除（0x100B DeleteObject，参数 = 句柄）。ZENCHE 未实现，本机增量功能。
 * 返回 PTP 响应码：0x2001=已删除；0x200D=对象只读/受保护（Z6 II 实测拒绝码）；
 * 0x2009=句柄无效；其他非零=相机拒绝；-1=未连接/会话已断；-2=传输失败。
 * 与 USB 侧 UsbPtpSession.deleteObject 同语义。 */
JNIEXPORT jint JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeDeleteNative(
        JNIEnv *env, jclass clz, jint jHandle) {
    (void) env; (void) clz;
    if (g_tfd < 0) return -1;
    unsigned int handle = (unsigned int) jHandle;
    unsigned int rc = 0;
    int r = pp_cmd_in_rc(g_tfd, 0x100B, g_ttid++, &handle, 1, &rc);
    if (r == -1 || r == -2) {
        LOGE("delete: 0x%08x 传输失败 r=%d", handle, r);
        return -2;
    }
    if (rc != 0x2001) LOGI("delete: 0x%08x 相机拒绝 rc=0x%04x", handle, rc);
    else LOGI("delete: 0x%08x 已删除", handle);
    return (jint) rc;
}

/* 对象写保护预检（0x1008 GetObjectInfo 前 8 字节：StorageID u32@0 /
 * ObjectFormat u16@4 / ProtectionStatus u16@6）。返回 1=受保护，0=未保护，
 * -1=未连接，-2=查询失败。删除前弹窗标注受保护张数用。 */
JNIEXPORT jint JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeObjectProtectedNative(
        JNIEnv *env, jclass clz, jint jHandle) {
    (void) env; (void) clz;
    if (g_tfd < 0) return -1;
    unsigned int handle = (unsigned int) jHandle;
    unsigned char *d = NULL; int dlen = 0;
    unsigned int rc = 0;
    int r = pp_data_in2(g_tfd, 0x1008, g_ttid++, &handle, 1, &d, &dlen, &rc);
    if (r != 0 || dlen < 8) {
        // 诊断：r=-6=相机响应码非 OK（rc 即真实拒绝码，0x2005=不支持 0x1008）；
        //      r=-3=无数据阶段；r=-4/-5=帧错位
        LOGI("prot: 0x%08x 查询失败 r=%d rc=0x%04x dlen=%d", handle, r, rc, dlen);
        free(d);
        return -2;
    }
    // 诊断：前 16 字节原样落日志（确认 ProtectionStatus 的真实偏移，
    // 尼康部分固件 ObjectInfo 头部与 PTP 标准有 ±字节差异）
    if (dlen >= 16) {
        LOGI("prot: 0x%08x dlen=%d head=%02x%02x%02x%02x %02x%02x%02x%02x %02x%02x%02x%02x %02x%02x%02x%02x",
            handle, dlen, d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7],
            d[8], d[9], d[10], d[11], d[12], d[13], d[14], d[15]);
    } else {
        LOGI("prot: 0x%08x dlen=%d head=%02x%02x%02x%02x %02x%02x%02x%02x",
            handle, dlen, d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7]);
    }
    unsigned int protection = (unsigned int) (d[6] | (d[7] << 8));
    free(d);
    return (protection != 0) ? 1 : 0;
}

/* 存储容量（0x1004 → 0x1005 GetStorageInfo）：
 * 返回 "capacity|free|freeImages|label"，失败 NULL。
 * ⚠️ Z6 II 双卡槽：0x1004 返回 2 个 sid（0x00010000/0x00020000），
 * **空槽查询 0x1005 返回 0x2013 StoreNotAvailable**（实测）——遍历全部 sid，
 * 聚合所有可用存储（空槽自动跳过）；全部不可用才返回 NULL。
 * dataset 布局同 USB 侧：MaxCapacity u64@6 / FreeSpaceInBytes u64@14 /
 * FreeSpaceInImages u32@22 / StorageDescription PSTR@26。 */
JNIEXPORT jstring JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeStorageInfoNative(
        JNIEnv *env, jclass clz) {
    (void) clz;
    if (g_tfd < 0) return NULL;
    unsigned int sids[8]; int nsid = 0;
    if (g_nstorages > 0) {
        for (int i = 0; i < g_nstorages && nsid < 8; i++)
            sids[nsid++] = g_storages[i];
        LOGI("stinfo: 用缓存 n=%d sid0=0x%08x", g_nstorages, g_storages[0]);
    } else {
        unsigned char *sd = NULL; int sdlen = 0; unsigned int rc4 = 0;
        int r4 = pp_data_in2(g_tfd, 0x1004, g_ttid++, NULL, 0, &sd, &sdlen, &rc4);
        LOGI("stinfo: 0x1004 r=%d rc=0x%04x dlen=%d", r4, rc4, sdlen);
        if (r4 != 0 || sdlen < 4) {
            free(sd);
            return NULL;
        }
        unsigned int cnt = (unsigned int) (sd[0] | (sd[1] << 8) | (sd[2] << 16) | ((unsigned) sd[3] << 24));
        if (cnt == 0 || sdlen < 4 + (int) cnt * 4) {
            LOGI("stinfo: 0x1004 空数组 cnt=%u", cnt);
            free(sd); return NULL;
        }
        for (unsigned int i = 0; i < cnt && nsid < 8; i++)
            sids[nsid++] = (unsigned int) (sd[4 + i * 4] | (sd[5 + i * 4] << 8) |
                            (sd[6 + i * 4] << 16) | ((unsigned) sd[7 + i * 4] << 24));
        LOGI("stinfo: 0x1004 cnt=%u sid0=0x%08x", cnt, sids[0]);
        free(sd);
    }
    /* 遍历全部存储：聚合可用槽（Z6 II 空槽 0x2013，跳过） */
    unsigned long long capBytes = 0, freeBytes = 0, freeImages = 0;
    char label[64] = {0};
    int hit = 0;
    for (int i = 0; i < nsid; i++) {
        unsigned char *d = NULL; int dlen = 0; unsigned int rc5 = 0;
        int r5 = pp_data_in2(g_tfd, 0x1005, g_ttid++, &sids[i], 1, &d, &dlen, &rc5);
        if (r5 != 0 || dlen < 26) {
            LOGI("stinfo: 0x1005 sid=0x%08x 失败 r=%d rc=0x%04x dlen=%d",
                 sids[i], r5, rc5, dlen);
            free(d);
            continue;
        }
        unsigned long long cap = 0, fr = 0;
        memcpy(&cap, d + 6, 8);
        memcpy(&fr, d + 14, 8);
        unsigned int fimg = (unsigned int) (d[22] | (d[23] << 8) | (d[24] << 16) | ((unsigned) d[25] << 24));
        capBytes += cap; freeBytes += fr; freeImages += fimg;
        hit++;
        LOGI("stinfo: 0x1005 sid=0x%08x ok dlen=%d cap=%llu free=%llu imgs=%u",
             sids[i], dlen, cap, fr, fimg);
        /* StorageDescription PSTR@26：u8 长度 + UTF-16LE 字符（取首个非空标签） */
        if (label[0] == '\0' && dlen > 27 && dlen >= 27 + 2 * d[26]) {
            int n = d[26], si = 0;
            for (int j = 0; j < n && si < (int) sizeof(label) - 1; j++) {
                unsigned char c = d[27 + j * 2];
                label[si++] = (c >= 0x20 && c < 0x7f) ? (char) c : ' ';
            }
        }
        free(d);
    }
    if (hit == 0) {
        LOGI("stinfo: 全部 %d 个存储不可用", nsid);
        return NULL;
    }
    char out[160];
    snprintf(out, sizeof(out), "%llu|%llu|%llu|%s",
             capBytes, freeBytes, freeImages, label);
    LOGI("storage: cap=%llu free=%llu imgs=%llu label=%s (hit=%d/%d)",
         capBytes, freeBytes, freeImages, label, hit, nsid);
    return (*env)->NewStringUTF(env, out);
}

/* ==================== 拍照控制（任务 5） ==================== */

/* 标准快门（0x100E InitiateCapture [0,0]）：照片进存储卡（决策：USB/Wi-Fi
 * 同用 0x100E，不走 ZENCHE 的 0x9207 SDRAM 路径）。
 * 返回：0x2001=已触发；其他=相机拒绝码（0x2019=DeviceBusy 等）；-1=未连接；-2=传输失败。 */
JNIEXPORT jint JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeCaptureNative(
        JNIEnv *env, jclass clz) {
    (void) clz;
    if (g_tfd < 0) return -1;
    unsigned int p[2] = {0, 0};
    unsigned int rc = 0;
    int r = pp_cmd_in_rc(g_tfd, 0x100E, g_ttid++, p, 2, &rc);
    LOGI("capture: 0x100E r=%d rc=0x%04x", r, rc);
    if (r == -2 || r == -5) return -2;
    if (r != 0) return (r == -6) ? (int) rc : -2;
    return (int) rc;
}

/* 直接快门（0x9207 尼康 CaptureRecInSdram，参数 [af, sdram]）：
 * af=0 不做 AF 直接释放快门；sdram=0 照片进存储卡。af=1 时相机先 AF 合焦后拍。
 * 返回：0x2001=已触发；其他=相机拒绝码；-1=未连接；-2=传输失败。 */
JNIEXPORT jint JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeCapture2Native(
        JNIEnv *env, jclass clz, jint jAf, jint jSdram) {
    (void) clz;
    if (g_tfd < 0) return -1;
    unsigned int p[2] = {(unsigned int) jAf, (unsigned int) jSdram};
    unsigned int rc = 0;
    int r = pp_cmd_in_rc(g_tfd, 0x9207, g_ttid++, p, 2, &rc);
    LOGI("capture2: 0x9207 af=%d sdram=%d r=%d rc=0x%04x", jAf, jSdram, r, rc);
    if (r == -2 || r == -5) return -2;
    if (r != 0) return (r == -6) ? (int) rc : -2;
    return (int) rc;
}

/* 读设备属性（0x1015 GetDevicePropValue）：原始 LE 字节。
 * 返回 jbyteArray（u16 属性 2 字节 / u32 属性 4 字节）；失败 NULL（相机拒绝码打日志）。 */
JNIEXPORT jbyteArray JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeGetPropNative(
        JNIEnv *env, jclass clz, jint jProp) {
    (void) clz;
    if (g_tfd < 0) return NULL;
    unsigned int p[1] = {(unsigned int) jProp};
    unsigned char *d = NULL; int dlen = 0; unsigned int rc = 0;
    int r = pp_data_in2(g_tfd, 0x1015, g_ttid++, p, 1, &d, &dlen, &rc);
    if (r != 0 || dlen < 1) {
        LOGI("getprop: 0x%04x r=%d rc=0x%04x dlen=%d", (unsigned) jProp, r, rc, dlen);
        free(d);
        return NULL;
    }
    jbyteArray out = (*env)->NewByteArray(env, dlen);
    if (!out) { free(d); return NULL; }
    (*env)->SetByteArrayRegion(env, out, 0, dlen, (const jbyte *) d);
    free(d);
    return out;
}

/* 写设备属性（0x1016 SetDevicePropValue，data-out 相位）。
 * 返回：0x2001=OK；其他=相机拒绝码（0x2005/0x200A/0x200F/0x201C/0x201D=
 * 属性不兼容，上层走回退链）；-1=未连接；-2=传输失败。 */
JNIEXPORT jint JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeSetPropNative(
        JNIEnv *env, jclass clz, jint jProp, jbyteArray jValue) {
    (void) clz;
    if (g_tfd < 0) return -1;
    jsize n = (*env)->GetArrayLength(env, jValue);
    if (n < 1 || n > 16) return -2;
    unsigned char val[16];
    (*env)->GetByteArrayRegion(env, jValue, 0, n, (jbyte *) val);
    unsigned int p[1] = {(unsigned int) jProp};
    unsigned int rc = 0;
    int r = pp_data_out(g_tfd, 0x1016, g_ttid++, p, 1, val, n, &rc);
    LOGI("setprop: 0x%04x n=%d r=%d rc=0x%04x", (unsigned) jProp, n, r, rc);
    if (r == -1 || r == -2 || r == -5) return -2;
    if (r != 0) return (r == -6) ? (int) rc : -2;
    return (int) rc;
}

/* ==================== 实时取景 / 录像 / 对焦（任务 6） ==================== */

/* 无数据指令的通用封装：返回 0x2001=OK，其他=相机拒绝码，-1=未连接，-2=传输失败 */
static jint cmd_rc_jni(JNIEnv *env, unsigned short op, unsigned int *params, int nparams) {
    (void) env;
    if (g_tfd < 0) return -1;
    unsigned int rc = 0;
    int r = pp_cmd_in_rc(g_tfd, op, g_ttid++, params, nparams, &rc);
    if (r == -1 || r == -2) return -2;
    if (r != 0) return (r == -6) ? (int) rc : -2;
    return (int) rc;
}

JNIEXPORT jint JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeStartLiveViewNative(JNIEnv *env, jclass clz) {
    return cmd_rc_jni(env, 0x9201, NULL, 0);
}

JNIEXPORT jint JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeEndLiveViewNative(JNIEnv *env, jclass clz) {
    return cmd_rc_jni(env, 0x9202, NULL, 0);
}

/* 取一帧取景图（0x9203 data-in）：返回原始字节（JPEG 截取在 Kotlin 侧做） */
JNIEXPORT jbyteArray JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeLiveViewFrameNative(JNIEnv *env, jclass clz) {
    (void) clz;
    if (g_tfd < 0) return NULL;
    unsigned char *d = NULL; int dlen = 0;
    int r = pp_data_in2(g_tfd, 0x9203, g_ttid++, NULL, 0, &d, &dlen, NULL);
    if (r != 0 || dlen < 2) { free(d); return NULL; }
    jbyteArray out = (*env)->NewByteArray(env, dlen);
    if (!out) { free(d); return NULL; }
    (*env)->SetByteArrayRegion(env, out, 0, dlen, (const jbyte *) d);
    free(d);
    return out;
}

JNIEXPORT jint JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeStartMovieNative(JNIEnv *env, jclass clz) {
    return cmd_rc_jni(env, 0x920A, NULL, 0);
}

JNIEXPORT jint JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeEndMovieNative(JNIEnv *env, jclass clz) {
    return cmd_rc_jni(env, 0x920B, NULL, 0);
}

/* 手动对焦驱动（0x9204 [方向, 驱动量]）：dir 1=近 / 2=远；drive 128/512/1024 */
JNIEXPORT jint JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeFocusDriveNative(
        JNIEnv *env, jclass clz, jint jDir, jint jDrive) {
    unsigned int p[2] = {(unsigned int) jDir, (unsigned int) jDrive};
    return cmd_rc_jni(env, 0x9204, p, 2);
}

/* 单次 AF（0x90c1，仅取景态） */
JNIEXPORT jint JNICALL
Java_com_shinemeow_app_GPhoto2Bridge_nativeAfDriveNative(JNIEnv *env, jclass clz) {
    return cmd_rc_jni(env, 0x90C1, NULL, 0);
}