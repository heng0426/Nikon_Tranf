#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
apply-ptp2-fixes.py — 让 libgphoto2 2.5.34 支持 Nikon Z 相机 Wi-Fi (PTP/IP) 列目录
用法：在 WSL 里 cd 到 libgphoto2-2.5.34 源码根目录后执行
    python3 /mnt/e/Code/GitHub/Nikon_Tranf/doc/apply-ptp2-fixes.py

共 3 处修改（全部针对 v2.5.34 源码，改过一次后再跑会提示已应用）：
 1. camlibs/ptp2/library.c  folder_list_func：GetStorageIDs 未在操作列表里报告时，
    仍主动尝试一次；失败则用 store_ffffffff（storage=all）伪目录替代 store_deadbeef
 2. camlibs/ptp2/ptp.c      ptp_list_folder：缓存按 (storage,parent) 过滤为空时，
    回退到实时 GetObjectHandles 查询，而不是直接返回空
 3. camlibs/ptp2/library.c  generic_list_func：打印每个对象的
    文件名/format/storage/parent 调试日志，便于排查
"""
import sys

fixes = [
    ("camlibs/ptp2/library.c",
     '''		} else {
			gp_list_append (list, STORAGE_FOLDER_PREFIX"deadbeef", NULL);
		}''',
     '''		} else {
			/* Nikon Z over WiFi does not advertise GetStorageIDs (0x1004)
			 * but answers it. Probe anyway; if that fails, use a
			 * "store_ffffffff" pseudostore (storage=all) so that object
			 * queries are not filtered by an unknown storage id. */
			PTPStorageIDs sids;
			int listed = 0;
			if (ptp_getstorageids (params, &sids) == PTP_RC_OK) {
				char fname[PTP_MAXSTRLEN];
				for_each (uint32_t*, psid, sids) {
					snprintf (fname, sizeof(fname),
						  STORAGE_FOLDER_PREFIX"%08x", *psid);
					CR (gp_list_append (list, fname, NULL));
					listed = 1;
				}
			}
			if (!listed)
				gp_list_append (list, STORAGE_FOLDER_PREFIX"ffffffff", NULL);
		}'''),
    ("camlibs/ptp2/ptp.c",
     '''	if (!handle && children && params->objects.len != 0) {
		for_each (PTPObject*, pob, params->objects)
			if (pob->oi.ParentObject == 0 && pob->oi.StorageID == storage)
				array_push_back(children, pob->oid);
		return PTP_RC_OK;
	}''',
     '''	if (!handle && children && params->objects.len != 0) {
		for_each (PTPObject*, pob, params->objects)
			if (pob->oi.ParentObject == 0 && pob->oi.StorageID == storage)
				array_push_back(children, pob->oid);
		if (children->len != 0)
			return PTP_RC_OK;
		/* cache found nothing for this storage: fall through to a live
		 * GetObjectHandles query (Nikon Z WiFi reports objects under
		 * storage ids different from the pseudostore). */
	}'''),
    ("camlibs/ptp2/library.c",
     '''		/* only looking for directories or files */
		if (is_directory != (ob->oi.ObjectFormat == PTP_OFC_Association))
			continue;''',
     '''		GP_LOG_D ("object 0x%08x: '%s' format=0x%04x storage=0x%08x parent=0x%08x",
			  (unsigned)ob->oid, ob->oi.Filename ? ob->oi.Filename : "?",
			  ob->oi.ObjectFormat, ob->oi.StorageID, ob->oi.ParentObject);

		/* only looking for directories or files */
		if (is_directory != (ob->oi.ObjectFormat == PTP_OFC_Association))
			continue;'''),
    ("camlibs/ptp2/ptpip.c",
     '''		if (dtoh32(hdr.type) == PTPIP_EVENT) {
			break;
		}

		/* TODO: Handle cancel transaction and ping/pong
		 * If not PTPIP_EVENT, process it and wait for next PTPIP_EVENT
		 */''',
     '''		if (dtoh32(hdr.type) == PTPIP_EVENT) {
			break;
		}

		/* CIPA DC-X005: the camera sends ProbeRequest (13) on the event
		 * channel and expects an immediate ProbeResponse (14). Nikon Z
		 * over WiFi only advances its connection/pairing state machine
		 * when the initiator answers these probes; without this the
		 * camera stays on its connection wizard and eventually drops
		 * the link. */
		if (dtoh32(hdr.type) == 13 /* PTPIP_PROBE_REQUEST */) {
			unsigned char probe[8];
			htod32a(&probe[0], 8);
			htod32a(&probe[4], 14 /* PTPIP_PROBE_RESPONSE */);
			ret = ptpip_write_with_timeout (params->evtfd, probe, 8,
				PTPIP_DEFAULT_TIMEOUT_S, PTPIP_DEFAULT_TIMEOUT_MS);
			if (ret == PTPSOCK_ERR) {
				GP_LOG_E ("probe response write failed");
				free (data);
				return PTP_ERROR_IO;
			}
			GP_LOG_D ("answered camera ProbeRequest (13) with ProbeResponse (14)");
			free (data);
			data = NULL;
			continue;
		}

		/* TODO: Handle cancel transaction and ping/pong
		 * If not PTPIP_EVENT, process it and wait for next PTPIP_EVENT
		 */'''),
]

def main():
    ok = True
    for path, old, new in fixes:
        try:
            with open(path, encoding="utf-8") as f:
                content = f.read()
        except FileNotFoundError:
            print(f"[失败] 找不到 {path} —— 请在 libgphoto2-2.5.34 源码根目录运行本脚本")
            return 1
        if new in content:
            print(f"[跳过] {path} 已应用过该修改")
            continue
        if content.count(old) != 1:
            print(f"[失败] {path} 中锚点出现 {content.count(old)} 次（期望 1 次）——源码版本可能不是 2.5.34")
            ok = False
            continue
        with open(path, "w", encoding="utf-8") as f:
            f.write(content.replace(old, new))
        print(f"[完成] {path}")
    if not ok:
        return 1
    print("\n全部修改完成。接下来：\n"
          "  make -j8 && make install\n"
          "  然后按 doc/16k-page-realignment-wsl.md 第 9-10 节验证并拷回 Windows 工程")
    return 0

if __name__ == "__main__":
    sys.exit(main())
