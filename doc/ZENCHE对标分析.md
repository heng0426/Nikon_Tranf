# ZENCHE 对标分析：对 ShineMeow（Nikon_Tranf）的帮助与落地项

> 分析对象 A：`Tauber01/ZENCHE`（帧澈，v1.5.15/build 42，五端原生，MIT License）
> 分析对象 B：本项目 `com.shinemeow.app`（拾影喵，Android 单端，Kotlin/Compose + gphoto2 JNI + 自研 USB PTP）
> 结论先行：**ZENCHE 对你的价值按优先级排序 = ① Wi-Fi 断线自动重连体系 → ② 拍照控制/实时取景 opcode 捷径（你的 M5） → ③ generation 防呆 + 契约测试防回归 → ④ PTP/IP 标准实现细节 → ⑤ 档案表/device_filter 宽容化 → ⑥ 去 gphoto2 化的战略选项**。同时你在 5 个点上领先 ZENCHE，见 §4，别在借鉴中丢掉。

---

## 1. 两边快照对比

| 维度 | ShineMeow（你） | ZENCHE |
|---|---|---|
| 定位 | 纯传图：浏览/预览/下载到相册 | 相机控制 + 传输工作流（拍照/取景/参数/外录/修图） |
| 平台 | Android 单端 | iOS/Android/HarmonyOS/macOS/Windows 五端 |
| USB | 自研 PTP over bulk（`UsbPtpSession.kt`）+ 尼康扩展 0x9434/0x101B | 自研 USB Host PTP（`PtpCamera.java`）+ UsbRequest 异步降级粘滞 |
| Wi-Fi | libgphoto2 JNI（PTP/IP）+ 尼康扩展 0x9431/0x9421/0x941c | 纯 Java 自研 PTP/IP（`PtpIpCamera.java` 1237 行，双通道+事件 reader） |
| 尼康 opcode 深度 | **深**（0x9434 全量表、0x9431 偏移读、0x101B、配对握手 GUID 逆向、ZLP、句柄位段） | 标准族 + 控制族（0x9201/0x9203/0x9207/0x90c7/0x90c8/0x90c1 等），**无 0x9434/0x9431** |
| 发现机制 | 上次 IP → mDNS（`_ptp._tcp`/`_nikon._tcp`）→ 网段扫描 | 手填 IP（AP 192.168.1.1 / STA） |
| 断线恢复 | 0x941c 心跳（保活），**无自动重连** | 完整重连体系（见 §2.1） |
| 拍照控制 | 无（M5 可选） | 完整（B 门/包围/间隔/对焦） |
| License | LGPL 依赖（gphoto2 动态链接，需声明） | 全仓 MIT，**代码可自由抄** |

---

## 2. 可以直接补给你的（按优先级）

### P0-① Wi-Fi 断线自动重连体系（你目前最大的功能缺口）

你现在的 Wi-Fi 保活 = `nativePairingProbe` 握手 + `0x941c` 心跳 + 前台 Service + WifiLock（AI_CONTEXT §5）。**掉线之后怎么恢复，你的文档里没有记载重连逻辑**。ZENCHE 有一套经过测试契约锁死的完整方案，五端同口径：

| 参数 | 值 | ZENCHE 证据 |
|---|---|---|
| 心跳周期 | 5s，走 PTP/IP **event 通道** Probe Request(type 13)/Response(14) | `PtpIpCamera.java:892-923`；`docs/PTPIP_PROTOCOL.md:160-163` |
| 单次探测超时 | 3s | `PtpIpCamera.java:34` |
| 离线判定 | 连续 3 次失败 | `MainActivity.java:1520-1553` |
| 重连退避 | 1s/2s/4s/8s/16s，封顶 30s | `MainActivity.java:15110-15198` |
| 重连前动作 | 先停取景再重连（传输场景等价于先停当前下载任务） | `docs/PTPIP_PROTOCOL.md:168` |
| 手动断开 | 不触发自动重连 | 同上 |
| 重连同构 | 重连路径 = 首连路径（重绑网络 → connect → detectVendor → 恢复会话） | `MainActivity.java:15110-15198` |

**给你的落地建议**：你的 `0x941c` 心跳保留（尼康专有保活，相机侧认它），**叠加**标准 Probe 13/14 作为"链路真的还活着"的证据——`0x941c` 只证明会话在，Probe 证明 TCP 双通道通。判定逻辑照抄上表：3 次失败 → 退避重连；重连时复用你已有的 `nativeConnect` 全序列（上次 IP → mDNS → 扫描），失败回到双卡界面。UI 上加"正在自动重连（第 n 次）/停止重连"——ZENCHE 的 UX 文案由 `native-connection-recovery-ux.test.mjs` 锁定，可直接参考措辞。

### P0-② generation 防呆 + 轻量契约测试（防回归）

你的 `MainViewModel` 3336 行承载"Wi-Fi 可取消 + USB 接力 + 多通道切换"（AI_CONTEXT §4），这是**竞态高危区**——你自己踩过"接力终止出口没恢复 connPhase 导致 UI 卡转圈"。ZENCHE 的系统化方案：

- 五路 `volatile long` generation：`usbCameraConnectionGeneration` / `wifiConnectionGeneration` / `localCameraConnectionGeneration` / `previewGeneration` / `transportGeneration`；每次连接/断开自增；**任何跨线程发布状态前先复核代际**（`MainActivity.java:12911-12941`）；
- 取消后旧会话不得复活：`abortTransport()` 不拿锁直接摘 socket 关闭，让 UI 线程立即打断阻塞中的 TCP（`PtpIpCamera.java:1121-1148`）；
- 手动断开链：`generation++ → manualDisconnect=true → abortTransport() → executor 里 close()`。

**映射到你的代码**：`wifiCancelRequested` / `pendingChannel` / `connPhase` 三件套可以用一个 `wifiGeneration` + 一个 `usbGeneration` 收敛——每个连接尝试捕获代际，所有回调（mDNS 命中、扫描结果、配对探针返回、JNI 回调线程）先比对代际再动 `connPhase`，"接力终止出口恢复 connPhase"就变成代维度的自然结果而不是逐个出口打补丁。

测试方面，ZENCHE 的 `node --test` 静态契约（正则断言源码，如"sticky 会话不得再走 UsbRequest"）+ javac 编译运行的假相机测试，对你零成本可抄。**你的 USB quirks 全是回归高危**：ZLP 吸收、0x101B 32KB→1MB 升档/0x2006 减半、句柄位段 `(hi16 & 0xff00)` 判断（硬编码 0x2a1c 会丢片——AI_CONTEXT §6 原话）、ObjectInfo 自适应文件名窗口 44..80、管道自愈。写 5-10 条契约测试锁死这些，比任何注释都硬。

### P1-① 拍照控制/实时取景：你的 M5 捷径

你 dev plan 的 M5 写"拍照控制（可选加分项）"，ZENCHE 已经把尼康控制族 opcode 全跑通并给了调用序列。**关键优势：与你的下载管线天然衔接**——ZENCHE 拍完照靠 `GetEvent(0x90c7)` 轮询拿到 `ObjectAddedInSDRAM(0xc101)` 句柄，然后 `GetObject`；你拿到句柄后可以直接走你更快的 0x9431/0x101B 偏移读管线。ZENCHE 被 512MB/64MiB 单包限制（`PTPIP_PROTOCOL.md:62-66`），你没有。

| 能力 | Opcode | ZENCHE 调用序列（证据） |
|---|---|---|
| 开/关/取帧 实时取景 | 0x9201 / 0x9202 / 0x9203 | `PtpCamera.java:261/291`；USB 超时 10s/12s，帧为 JPEG（FFD8 扫描兜底） |
| 拍照到 SDRAM | 0x9207 [0xFFFFFFFF, 1] | `PtpCamera.java:607`；拍前若取景中先 `0x9202` + `0x90c8` 就绪探询 |
| 事件轮询拿新对象句柄 | 0x90c7 → 0xc101 | `PtpCamera.java:618/2134`；30s 拿不到用 0xFFFF0001 兜底 |
| B 门 | 0x90c2([1]) 进遥控态 → 0x500e=M → 快门 0xFFFFFFFF → 0x9207 → 计时 → 0x920c([0,0]) 结束 → 0x90c2([0]) 释放 | `PtpCamera.java:588-659` |
| 录像启停 | 0x920a / 0x920b（未取景先 0x9201） | `PtpCamera.java:302/316` |
| AF-ON / 手动对焦步进 | 0x90c1 / 0x9204 [direction, amount] | `PtpCamera.java:853/898` |
| 参数写（读改写） | 0x1015 读 → 0x1016 data-out 写 | `PtpIpCamera.java:437/453` |

参数族（你现在完全没有）：ISO `0x500f`、光圈 `0x5007`(F×100)、快门 `0x500d`(秒×10000)/尼康优先 `0xd100`(分子分母)、曝光补偿 `0x5010`(EV×1000)、对焦模式 `0x500a`（AF-S `0x8010`/AF-C `0x8011`/MF `0x0001`，取景态回退 `0xd061`）、拍摄模式 `0x500e`（P/M/A/S = 2/1/3/4）、白平衡 `0x5005`、优化校准 `0xd200`（8 档）。尼康值域表在 ZENCHE `docs/NIKON_TERMINOLOGY.md` 第 7-24 行，是以尼康官方说明书为基准的，**术语汉化可直接抄**。

注意边界：ZENCHE 也标了这些控制路径 `TBC-awaiting-hardware`（跨端互操作待实机）——但它的 USB 路径在 macOS/Android 两端"已接通"（README 平台表），且 opcode 字典与 libgphoto2 ptp2 camlib 同源（你的 JNI 就链着 ptp2），可信度高。

### P1-② 机内删除 + 存储信息（低成本功能补全）

- `DeleteObject 0x100B [handle, 0]` + 受保护标志（ObjectInfo 偏移 6 非 0 不可删）——你解析 ObjectInfo 的代码已有这块字段，加个删除入口即可（`PtpCamera.java` 删除路径 + `CameraStorage.java:138` protectedObject 解析）；
- `GetStorageIDs 0x1004` / `GetStorageInfo 0x1005` 展示卡容量/剩余——`CameraStorage.java:112-132` 有完整解析（capacity@6、free@14、freeImages@22、只读标志@4），抄就完事；
- 删除前确认框措辞、删除期间停取景的互斥，ZENCHE 都有成例。

### P1-③ PTP/IP 标准实现细节（补你的 JNI / 未来自研化）

即使你继续用 gphoto2，这些细节也有价值——你的配对握手是自己逆向的，标准层只有 gphoto2 兜底：

- **握手偏移量**：InitCommandAck 的 connectionNumber@**8**、CameraName@**28**（UTF-16LE）（`PTPIP_PROTOCOL.md:31`）；你的 GPhoto2Bridge 若要做"连接后显示相机名"，照此解析；
- **帧布局**：`[u32 len][u32 type][payload]` LE；OperationRequest = DataPhaseInfo(u32)@8 + opcode(u16)@12 + txid(u32)@14（你的 Z传参考整理 §3 抓包已确认同款，交叉验证 ✓）；
- **data-in 相位**：StartData(9) 带 TotalLength(u64)@12；`UINT64_MAX` = 流式未知长度；累计上限 512MB；单包 64MiB；
- **data-out 相位**（写参数用）：请求 → StartData(9) → EndData(12) → 响应，**长度字段不得写 0、不得给数据加前导 u32(0)**（`native-ptpip-connection-regressions.test.mjs:100-122`）——你自己发 0x1016 时这是硬规则；
- **响应码分级**：`0x2003 SessionNotOpen` / `0x2004 InvalidTransactionID` / `0x201E SessionAlreadyOpen` = 会话致命（必须重连）；`0x2005/0x200A/0x2009/0x2019` 是可容忍/需重试——你 USB 侧的 0x2006 减半自适应属于同类思想，可以统一成一张响应码策略表。

### P2-① device_filter 宽容化 + 相机档案表

你现在只按 vendor `0x04B0` 发现 USB 设备。ZENCHE 的做法（`device_filter.xml` + `PtpCamera.java:32-91`）：

1. 20 款 Nikon PID 精确匹配（D500/Z7/Z6/Z50/D7500/D780/D6/Z5/D850/Z7II/Z6II/Z fc/Z9/Z8/Z30/Z f/Z6III/Z50II/Z5II/ZR）；
2. **class 6/subclass 1/protocol 1（Still Image）厂商级回退**——新机型没进 PID 表也能连上；
3. 描述符归一匹配（`normalizeModelName`：lowercase → 去厂商前缀 → 世代别名 `II→2`/`III→3`）——尼康 Z 系同样有 "Z6II" vs "Z 6II" 显示差异，归一化后兼容性声明更准。

对你：加两条 class 回退即可让未实测机型（Z7III 未来机型等）至少能连；档案表可用于设置页展示"已验证/理论支持"分级（你 README 已有"实测 Z6 II"口径，档案化是自然延伸）。

### P2-② 去 gphoto2 化的战略选项（评估，不急着做）

`PtpIpCamera.java`（1237 行，MIT）是一个**完整的纯 Java PTP/IP 实现**：双 TCP 通道、握手、命令/数据相位、事件 reader、probe 心跳、流式长度校验、会话代际。如果你把 Wi-Fi 通道从 gphoto2 JNI 迁到它 + 你的尼康扩展（0x9431/0x9421/0x941c/0x9434 你全有）：

- **收益**：去掉 LGPL 合规负担；`Network.getSocketFactory()` 按 socket 绑定（你现在必须 `bindProcessToNetwork` 进程级绑定——因为 gphoto2 的 socket 在 C 层创建，Java 管不到；自研后 per-socket 绑定不影响 App 其他网络用途）；双通道统一架构（USB/Wi-Fi 同一套 generation + 同一套下载管线）。
- **代价**：失去 ptp2 camlib 的兼容性广度（冷门机型/非尼康）；配对握手你已经逆向清楚（GUID `3b4b6cbf0ea04a2d` + INIT 48 字节布局），迁移可行但需真机回归；工程量约 1-2 周（照抄 + 适配你的句柄/枚举输出格式）。
- **建议节奏**：先抄 ZENCHE 的 PTP/IP 容器层做成"影子实现"，与 gphoto2 通道并行 A/B 验证下载正确性，再切默认。USB 通道你已经是自研的，切完 Wi-Fi 后全 App 零 LGPL 依赖。

### P2-③ 工程制度借鉴

- **三文档互补**（ZENCHE `AGENTS.md:73-85`）：PROJECT_OUTLINE（产品范围/基线）+ TECHNICAL_APPROACH（工程契约）+ TASK_PROGRESS（实际工作/证据/ blocker）。你已有 AI_CONTEXT.md（技术）+ README.md（用户向），缺"任务进度/决策记录"维度——每次提交前三文档同步的制度值得抄；
- **更新分发**：GitHub Releases + 逐包 SHA-256 + 自托管更新端点回退链（`server.mjs` 的 `/api/update`）——你将来发布时可直接复用这套模式；
- 版本语义化：ZENCHE `1.5.15 / build 42` 双轨（versionName/versionCode 对应）。

---

## 3. 落地清单（按序打勾）

| # | 事项 | 抄谁的 | 预估工作量 |
|---|---|---|---|
| 1 | Wi-Fi 自动重连：3 次失败判定 + 1/2/4/8/16/30s 退避 + 重连同构 + "停止重连" UI | ZENCHE MainActivity.java:15110-15198 + wifi-monitor 契约 | 2-3 天 |
| 2 | 0x941c 心跳叠加标准 Probe 13/14 双证据 | ZENCHE PtpIpCamera.java:892-923 | 0.5 天 |
| 3 | 契约测试 5-10 条锁死 USB quirks（ZLP/0x101B 升档/句柄位段/ObjectInfo 窗口/管道自愈） | ZENCHE node --test 模式 | 1 天 |
| 4 | generation 收敛 connPhase/接力竞态 | ZENCHE 五路 generation | 1-2 天 |
| 5 | 拍照控制 M5：0x9207→0x90c7→0xc101→你的下载管线；参数 0x1015/0x1016 读改写 | ZENCHE PtpCamera.java:588-788 + NIKON_TERMINOLOGY.md | 1 周 |
| 6 | 实时取景 0x9201/0x9202/0x9203（帧率 ~10fps，JPEG 扫描兜底） | 同上 :261-316 | 2-3 天 |
| 7 | 机内删除 0x100B + 存储容量 0x1004/0x1005 | ZENCHE CameraStorage.java:112-132 | 1 天 |
| 8 | device_filter class 回退 + 20 款 PID 档案 | ZENCHE device_filter.xml | 0.5 天 |
| 9 | （可选）PTP/IP 影子实现 A/B → 去 gphoto2 | ZENCHE PtpIpCamera.java（MIT） | 1-2 周 |

---

## 4. 你领先 ZENCHE 的（借鉴时别丢）

1. **尼康扩展 opcode 字典更深**：0x9434 全量表枚举（886 张 ~1s vs ZENCHE 的 0x1007 逐层枚举）、0x9431/0x101B 偏移读 1MB 分块（ZENCHE 只有 0x1009 整对象 + 512MB/64MiB 上限）——**你的下载吞吐和枚举速度领先一个身位**；
2. **相机发现**：mDNS `_ptp._tcp`/`_nikon._tcp` 秒级主路径（ZENCHE 全靠手填 IP）；
3. **配对握手逆向**：GUID 常量 + INIT 48 字节布局 + 0x952b/0x935a 探针序列（ZENCHE 依赖 CameraName 启发式 + GetDeviceInfo）；
4. **USB 传输血泪经验**：ZLP 陷阱、管道自愈、句柄位段 `(hi16 & 0xff00)`、ObjectInfo ±字节自适应——这些 ZENCHE 文档里没有，是你的独有资产；
5. **网络路由实测**：MIUI 热点非受管黑洞、fwmark 直连路由表——ZENCHE 的 `Network.socketFactory` 方案与你的实测互为印证，但你的场景覆盖更细。

---

## 5. 一句话总结

ZENCHE 对你的核心价值是三块：**它替你验证了"标准 PTP/IP 工程化"怎么写（重连/代际/相位/上限），它把尼康控制族 opcode 的调用序例全给了（你的 M5 直接抄），它是一套可抄的 MIT 代码（含去 gphoto2 化的备选路径）**；而你的尼康扩展字典、mDNS 发现和 USB  quirks 经验是 ZENCHE 没有的——抄它的工程骨架，保留你的协议深度。
