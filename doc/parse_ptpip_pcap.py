#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""parse_ptpip_pcap.py v3 — 解析 PCAPdroid 抓的 PTP/IP 流量
用法: python3 parse_ptpip_pcap.py <pcap文件> [--max N]
- 按 SYN 分段（每条 TCP 连接独立重组）
- 连续 Data 帧压缩输出"""
import struct, sys, collections

OPS = {0x1001:'GetDeviceInfo',0x1002:'OpenSession',0x1003:'CloseSession',
 0x1004:'GetStorageIDs',0x1005:'GetStorageInfo',0x1006:'GetNumObjects',
 0x1007:'GetObjectHandles',0x1008:'GetObjectInfo',0x1009:'GetObject',
 0x100A:'GetThumb',0x100B:'DeleteObject',0x1014:'GetDevicePropDesc',
 0x1015:'GetDevicePropValue',0x1016:'SetDevicePropValue',
 0x9201:'Nikon_LiveViewOn',0x9202:'Nikon_LiveViewOff',0x9203:'Nikon_LiveViewImg',
 0x920a:'Nikon_RecStart',0x920b:'Nikon_RecStop',
 0x952b:'Nikon_952b',0x935a:'Nikon_935a',
 0x9407:'Nikon_CheckEvent',0x90c0:'Nikon_GetProfileAll',0x90c1:'Nikon_SendProfileData'}
EVT = {0x4001:'Cancel',0x4002:'ObjectAdded',0x4003:'ObjectRemoved',0x4004:'StoreAdded',
 0x4005:'StoreRemoved',0x4006:'DevicePropChanged',0x4007:'ObjectInfoChanged',
 0x4008:'DeviceInfoChanged',0x4009:'RequestObjectTransfer',0x400a:'StoreFull',
 0x400c:'StorageInfoChanged',0x400d:'CaptureComplete'}
TYPES = {1:'InitCommandReq',2:'InitCommandAck',3:'InitEventReq',4:'InitEventAck',
 6:'OpRequest',7:'OpResponse',8:'Event',9:'StartData',10:'Data',12:'EndData',
 13:'PROBE_REQ',14:'PROBE_RESP'}

def read_pcap(path):
    data = open(path,'rb').read()
    magic = data[:4]
    if magic == b'\xd4\xc3\xb2\xa1': endian='<'
    elif magic == b'\xa1\xb2\xc3\xd4': endian='>'
    else: raise SystemExit('非经典 pcap')
    linktype = struct.unpack(endian+'I', data[20:24])[0]
    off = 24; pkts=[]
    while off + 16 <= len(data):
        ts_s, ts_f, caplen, origlen = struct.unpack(endian+'IIII', data[off:off+16])
        off += 16
        pkt = data[off:off+caplen]; off += caplen
        if linktype == 101: ip = pkt
        elif linktype == 1 and len(pkt) > 14:
            ethertype = struct.unpack('>H', pkt[12:14])[0]
            if ethertype != 0x0800: continue
            ip = pkt[14:]
        else: continue
        if len(ip) < 20: continue
        ihl = (ip[0] & 0xf) * 4
        if ip[9] != 6: continue
        src = '.'.join(map(str, ip[12:16])); dst = '.'.join(map(str, ip[16:20]))
        tcp = ip[ihl:]
        if len(tcp) < 20: continue
        sport, dport = struct.unpack('>HH', tcp[0:4])
        seq = struct.unpack('>I', tcp[4:8])[0]
        flags = tcp[13]
        doff = (tcp[12] >> 4) * 4
        pkts.append((src, sport, dst, dport, seq, flags, tcp[doff:]))
    return pkts

def reassemble(pkts):
    finished = collections.defaultdict(list)
    cur = {}
    for src, sport, dst, dport, seq, flags, payload in pkts:
        if 15740 not in (sport, dport): continue
        if dport == 15740:
            key = (src, sport, dst, dport, 'A2C')
        else:
            key = (dst, dport, src, sport, 'C2A')
        if flags & 0x02:
            if cur.get(key) is not None:
                finished[key].append(cur[key])
            cur[key] = {'data': bytearray(), 'seq0': (seq + 1) & 0xffffffff, 'syn': True}
            continue
        st = cur.get(key)
        if st is None:
            st = cur[key] = {'data': bytearray(), 'seq0': seq, 'syn': False}
        rel = (seq - st['seq0']) & 0xffffffff
        if rel >= 0x20000000: continue
        end = rel + len(payload)
        if end > len(st['data']):
            if end > 256*1024*1024: continue
            st['data'].extend(b'\x00' * (end - len(st['data'])))
        if payload:
            st['data'][rel:end] = payload
    for key, seg in cur.items():
        if seg is not None:
            finished[key].append(seg)
    return finished

def frames(buf):
    off = 0; n = len(buf)
    ln0, tp0 = struct.unpack('<II', buf[0:8])
    if not (8 <= ln0 <= 64*1024*1024 and 1 <= tp0 <= 14):
        found = -1
        for probe in range(1, min(n - 8, 2*1024*1024)):
            ln, tp = struct.unpack('<II', buf[probe:probe+8])
            if 8 <= ln <= 64*1024*1024 and 1 <= tp <= 14 and probe + ln <= n:
                found = probe; break
        if found < 0:
            print('  (找不到合法帧起点，跳过)')
            return
        print(f'  !! 重同步：跳过前 {found} 字节（抓包始于连接中段）')
        off = found
    while off + 8 <= n:
        ln, tp = struct.unpack('<II', buf[off:off+8])
        if ln < 8 or off + ln > n:
            print(f'  !! 帧边界异常 @{off} (len={ln})，停止')
            return
        yield off, tp, buf[off+8:off+ln]
        off += ln

def main():
    args = sys.argv[1:]
    maxfull = 500
    if '--max' in args: maxfull = int(args[args.index('--max')+1])
    pkts = read_pcap(args[0])
    streams = reassemble(pkts)
    print('== 连接概览 ==')
    for key, segs in sorted(streams.items()):
        for i, seg in enumerate(segs):
            print(f'  {key[0]}:{key[1]} <-> {key[2]}:{key[3]}  段#{i} {len(seg["data"])} 字节')
    total = 0
    for key, segs in sorted(streams.items()):
        for i, seg in enumerate(segs):
            if len(seg['data']) < 8: continue
            print(f'\n===== {key[0]}:{key[1]} <-> {key[2]}:{key[3]} 段#{i} ({len(seg["data"])} bytes) =====')
            idx = 0; data_acc = 0; data_cnt = 0
            for off, tp, payload in frames(bytes(seg['data'])):
                idx += 1
                name = TYPES.get(tp, f'type{tp}')
                direction = key[4]
                verbose = idx <= maxfull or tp not in (9, 10, 12)
                if tp == 6:
                    if len(payload) < 10:
                        print(f'{idx:5d} [{direction}] OpRequest(短帧) {payload.hex()}'); continue
                    dpi, op = struct.unpack('<IH', payload[0:6])
                    tid = struct.unpack('<I', payload[6:10])[0]
                    params = struct.unpack('<' + 'I' * ((len(payload)-10)//4), payload[10:])
                    ps = ','.join(f'0x{p:08x}' for p in params)
                    print(f'{idx:5d} [{direction}] OpRequest  {OPS.get(op, hex(op)):26} tid={tid} params=[{ps}]')
                elif tp == 7:
                    rc, tid = struct.unpack('<HI', payload[0:6])
                    extra = struct.unpack('<' + 'I' * ((len(payload)-6)//4), payload[6:])
                    es = ','.join(f'0x{p:08x}' for p in extra)
                    tag = 'OK' if rc == 0x2001 else f'rc=0x{rc:04x}'
                    print(f'{idx:5d} [{direction}] OpResponse {tag:10} tid={tid}' + (f' params=[{es}]' if es else ''))
                elif tp == 8:
                    ec, tid = struct.unpack('<HI', payload[0:6])
                    print(f'{idx:5d} [{direction}] Event      {EVT.get(ec, hex(ec)):26} tid={tid}')
                elif tp == 1:
                    guid = payload[0:16].hex(':')
                    print(f'{idx:5d} [{direction}] InitCommandReq GUID={guid}')
                elif tp == 2:
                    conn = struct.unpack('<I', payload[0:4])[0]
                    print(f'{idx:5d} [{direction}] InitCommandAck connectionNumber={conn}')
                elif tp in (13, 14):
                    print(f'{idx:5d} [{direction}] {name} ★')
                elif tp in (9, 10, 12):
                    data_acc += len(payload); data_cnt += 1
                    if idx <= maxfull or tp == 12:
                        print(f'{idx:5d} [{direction}] {name:13} {len(payload)} B (该事务累计 {data_acc} B)')
                    continue
                else:
                    print(f'{idx:5d} [{direction}] {name:13} {len(payload)} B')
            if data_cnt:
                print(f'      （数据帧共 {data_cnt} 包 / {data_acc} B）')
            total += idx
    print(f'\n共 {total} 帧')

if __name__ == '__main__':
    main()
