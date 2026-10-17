package com.shinemeow.app

/** 尼康 USB 机型注册表（20 款，ZENCHE 对标任务 8）。
 *  字段 = 机型名 / 代际 / minISO / maxISO——ISO 范围为参数远控预留
 *  （任务 5 起消费），当前仅机型名展示与代际分组使用。
 *  数据源：ZENCHE `README.md:201-232` 三代分组（EXPEED 5/6/7），PID 全部实测于 vendor 0x04b0。
 *
 *  策略约定（2026-10-17 用户确认）：未知尼康设备**不拒绝连接**（传输工具宽容策略，
 *  与 ZENCHE 的硬报错不同）——本表只影响机型名显示：命中 → 档案名；
 *  未命中 → 「未知尼康 (0xXXXX)」；连接能力不受影响。 */
internal object NikonRegistry {
    data class Profile(val name: String, val generation: String, val minIso: Int, val maxIso: Int)

    private val byPid: Map<Int, Profile> = buildMap {
        // ---- EXPEED 5（单反）----
        put(0x043a, Profile("Nikon D500", "EXPEED 5", 100, 51200))
        put(0x0445, Profile("Nikon D7500", "EXPEED 5", 100, 51200))
        put(0x044a, Profile("Nikon D850", "EXPEED 5", 64, 25600))
        // ---- EXPEED 6（微单 + 单反）----
        put(0x0442, Profile("Nikon Z7", "EXPEED 6", 64, 25600))
        put(0x0443, Profile("Nikon Z6", "EXPEED 6", 100, 51200))
        put(0x0444, Profile("Nikon Z50", "EXPEED 6", 100, 51200))
        put(0x0446, Profile("Nikon D780", "EXPEED 6", 100, 51200))
        put(0x0447, Profile("Nikon D6", "EXPEED 6", 100, 102400))
        put(0x0448, Profile("Nikon Z5", "EXPEED 6", 100, 51200))
        put(0x044b, Profile("Nikon Z7II", "EXPEED 6", 64, 25600))
        put(0x044c, Profile("Nikon Z6II", "EXPEED 6", 100, 51200))
        put(0x044f, Profile("Nikon Z fc", "EXPEED 6", 100, 51200))
        put(0x0452, Profile("Nikon Z30", "EXPEED 6", 100, 51200))
        // ---- EXPEED 7（微单）----
        put(0x0450, Profile("Nikon Z9", "EXPEED 7", 64, 25600))
        put(0x0451, Profile("Nikon Z8", "EXPEED 7", 64, 25600))
        put(0x0453, Profile("Nikon Z f", "EXPEED 7", 100, 64000))
        put(0x0454, Profile("Nikon Z6III", "EXPEED 7", 100, 64000))
        put(0x0455, Profile("Nikon Z50II", "EXPEED 7", 100, 51200))
        put(0x0456, Profile("Nikon Z5II", "EXPEED 7", 100, 64000))
        put(0x0457, Profile("Nikon ZR", "EXPEED 7", 100, 51200))
    }

    fun profileFor(pid: Int): Profile? = byPid[pid]

    /** 机型显示名：档案命中 → 档案名（比系统 productName "NIKON DSC Z 6_2" 干净）；
     *  尼康未收录 PID → 「未知尼康 (0xXXXX)」标注（宽容连接，不断开）；
     *  非尼康/无 PID → 系统上报名兜底。 */
    fun modelLabel(pid: Int?, reported: String?): String {
        if (pid != null) byPid[pid]?.let { return it.name }
        if (!reported.isNullOrBlank()) return reported
        return if (pid != null) "未知尼康 (0x%04x)".format(pid) else "Nikon"
    }
}
