# Z传参考整理（专项归档）

> 说明：2026-10-14 起项目文档与代码注释中不再直接提及竞品「Z传」，
> 原先散落的对标/实证/逆向出处统一归档到本文档，便于追溯技术结论的来源。
> 各处原表述已改为中性描述（「实证」「抓包逐字节确认」等），技术内容不变。

## 1. 总体架构参考

- **USB 直连路线**：`UsbPtpSession.kt` 的自研 PTP over USB bulk 实现照搬 Z传 的路线
  （UsbManager 发现尼康 → claimInterface → bulkTransfer 读写 PTP 容器）。
- **Wi-Fi 分层网络策略**：出接口选择方案（受管网段走 `Network.socketFactory`、
  热点网段绑本机源地址出包，全程不做进程级绑定）为 z传 实证方案；
  mDNS 服务发现（`_ptp._tcp` / `_nikon._tcp`）为其主路径；
  热点直连（未标记 socket 命中规则链 fwmark 0x0/0x10000 → 直连路由表）为其同款路径。
- **线程调度**：后台解码线程设 `THREAD_PRIORITY_BACKGROUND`（照搬 Z传）——
  把线程放进 background cgroup → 调度到小核，不与 UI 线程争抢大核。

## 2. 操作码字典实证（尼康专有协议）

- **0x101B GetPartialObject（部分读取）**：尼康 USB 专有部分读取操作码，
  Z传 操作码字典实证。标准 0x1014 在尼康 USB 固件上被解释为其他操作，
  参数对不上必返 0x2006。参数 [handle, offset, len]。
- **0x9434 对象清单表**：句柄+时间戳一次拿全，免逐对象 0x1008（PTP-IP/USB 均可用）。
- **0x9431 定向读（Wi-Fi 部分读取）**：单次上限 1MB（C 层校验）；
  Z传 高清预览 0.7~0.9MB 单次成功实证 → 项目把下载块从 128KB 加大到 1MB。
- **0x9421**：取对象大小。**0x941c**：心跳/事件。

## 3. 配对握手抓包逆向（gphoto2_jni.c 探针）

从 Z传 的抓包（capture-hires.pcap 等记录）逐字节逆向的握手序列：

```
独立短连接：INIT → OpenSession(1)
→ 0x941c（返回 u32 状态）→ GetStorageIDs → GetDeviceInfo
→ 0x952b（返回 8B 能力）→ 0x935a(0x2001)
→ CloseSession ×2 → 关闭
```

- 相机向导收到该序列后推进到「配对完成」。
- **主机身份（逐字节复刻，代码中保留为协议常量）**：
  - GUID 字段 = 16 字节 ASCII 十六进制串 `"3b4b6cbf0ea04a2d"`
    （s_guid 形式 `"33:62:34:62:36:63:62:66:30:65:61:30:34:61:32:64"`，主会话与探针必须同身份并落盘）。
  - INIT 包主机名字符串 = **`"ZTransfer"`**（协议字面量，仍在 gphoto2_jni.c 中——
    它是发往相机的 INIT_COMMAND_REQUEST 内容，非注释；如需更换须实测配对）。
- **INIT_COMMAND_REQUEST 帧布局（实测 48 字节）**：
  `[0..3]=len [4..7]=type(1) [8..23]=GUID(16B ASCII) [24..]=名字 UTF-16LE+NUL [44..47]=协议版本 u32 0x00010000`
- **data-in 事务帧布局（对照 Z传 抓包逐字节确认）**：
  `[0..3]=len [4..7]=type(6) [8..11]=DataPhaseInfo(u32=1) [12..13]=opcode [14..17]=tid [18..]=params(u32×n)`

## 4. 高清预览行为（复刻 Z传 的解析方案）

抓包实证（capture-hires.pcap，2026-10-02）：

- **JPG**：头 128KB 内 APP2 MPF 元数据列出各内嵌图（size+offset），
  取最大条目一次读出即完整 JPEG（实测 0.7~0.9MB，offset+len 恰为文件尾）。
- **NEF**：补读 0x20000..0x80000（TIFF IFD0 的 SubIFD 链），解析压缩=7（JPEG）
  SubIFD 的 StripOffsets/ByteCounts，定向读出全尺寸嵌入预览（实测 0.67~0.83MB），
  RAW 数据一个字节不传。
- **预览挑选**：取长度最小者 = Z传 实际行为（669KB 小预览而非 1.34MB 大预览）。
- **Z传 自身无缓存、翻页重拉** —— 本项目在此之上加 LRU 缓存与 ±1 页预取（改进项）。

## 5. 缩略图

配对模式下没有专门的缩略图指令：Z传 的预览网格是从对象首块原始数据
（内含 EXIF/TIFF 嵌入缩略图 JPEG）里扫描 FFD8..FFD9 完整 JPEG 段抠出来的，
本项目同法。

## 6. 原出处索引（已改为中性表述的位置）

| 文件 | 位置 | 原表述要点 |
|---|---|---|
| AI_CONTEXT.md | §1 简介 | 对标市面"Z传"App |
| AI_CONTEXT.md | §3 UsbPtpSession 行 | 照搬 Z传 架构 |
| AI_CONTEXT.md | §5 发现/分层策略/连接/下载表 | z传 实证方案重写、直连同款、复刻配对握手、操作码字典实证 |
| README.md | 简介 | 对标"Z传" |
| UsbPtpSession.kt | 类头/0x101B 常量/partialRead | 照搬 Z传 路线、Z传 字典实证 |
| MainViewModel.kt | USB 通道注释/Wi-Fi 网络注释×8/下载块/线程优先级/高清预览头/NEF 挑选 | 照搬 Z传、z传 同款（直连/主路径/实证方案）、复刻 Z传、Z传 实际行为 |
| GPhoto2Bridge.kt | nativePairingProbe | 复刻 Z传 握手 |
| gphoto2_jni.c | GUID 常量/配对探针头/data-in 帧布局/身份与 INIT 包/预览注释 | Z传 实测身份、复刻 Z 传握手序列、抓包逐字节逆向/确认、预览网格 |
