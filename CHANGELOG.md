# Changelog / 更新日志

## [1.3.3.2] - 2026-08-12

### English

**Aero Reformation 1.3.3.2 — Physics Anchor orphan-marker cleanup**

- **Orphan marker cleanup** — `AnchorMarkerEntity` instances that are no longer referenced
  by any anchor entry or warmup entry are now force-discarded automatically every 5 seconds,
  instead of lingering until the next server restart. This prevents leftover marker entities
  (e.g. from lost map entries, dimension switches, or duplicates created on relog) from
  accumulating, keeping the marker↔anchor relation strictly 1:1.
- **Performance** — the cleanup is a low-frequency (5s) scan of entities vs. referenced
  markers; overhead is negligible during normal play.

### 中文

**Aero Reformation 1.3.3.2 — 物理锚点孤儿标记清理**

- **孤儿标记清理** — 不再被任何锚点条目或恢复（warmup）条目引用的 `AnchorMarkerEntity`
  现在每 5 秒自动强制删除，而不是一直保留到下一次服务器重启。这防止了残留标记实体
  （如地图条目丢失、维度切换、重进存档产生的重复实体）不断累积，让标记与锚点保持
  严格一一对应。
- **性能** — 清理为低频（5 秒一次）扫描，正常游玩开销可忽略。

---

## [1.3.3.1] - 2026-08-05

### English

**Aero Reformation 1.3.3.1 — Cross-Mod & ComputerCraft API**

New in this release:

- **ComputerCraft peripherals** — RCS thruster, power block and guidance warhead are now
  usable from CC:Tweaked computers (no ComputerCraft required to run the mod; integration
  auto-disables when CC is absent):
  - `aero_rcs` — thrust get/set, thrust index, creative mode, angled-nozzle mode,
    live thrust/nozzle/fuel/electric status, sync/warhead binding.
  - `aero_power` — yaw/pitch limits and seat height.
  - `aero_warhead` — target acquisition, search mode, guidance mode, PID tuning.
- **Cross-mod Java API** — new `dev.simulated_team.aero_reformation.api` package for other
  mods: `RcsThrusterApi`, `PowerBlockApi`, `GuidanceWarheadApi` (all `Level` + `BlockPos`
  based, server-safe, auto-synced).
- **RCS thruster public getters** — `getThrustIndex`, `setThrustIndex`, `getThrustOptions`,
  `getActiveNozzleMask`, `getCurrentThrustPN`, `isFuelAvailable`, `isElectricMode`.
- **Automated deploy** — `./gradlew build` now auto-cleans and deploys the built jar into
  the game mods folder (single latest version only).
- **Safety** — all ComputerCraft registration is isolated and try/catch guarded; the mod
  runs unchanged without CC installed.

Full API reference: see `docs/api.md`.

### 中文

**Aero Reformation 1.3.3.1 — 跨模组与 ComputerCraft API**

本次更新内容：

- **ComputerCraft 外设** — RCS 推进器、驾驶员动力块与制导弹头现可被 CC:Tweaked 电脑调用
  （未安装 CC 时模组照常运行，集成自动禁用）：
  - `aero_rcs` — 推力获取/设置、推力档位、创意模式、斜喷衰减档位、实时推力/喷嘴/燃料/电力
    状态、同步器与弹头绑定。
  - `aero_power` — 偏航/俯仰限位与座椅高度。
  - `aero_warhead` — 目标获取、搜索模式、制导模式、PID 调参。
- **跨模组 Java API** — 新增 `dev.simulated_team.aero_reformation.api` 包供其他模组调用：
  `RcsThrusterApi`、`PowerBlockApi`、`GuidanceWarheadApi`（统一 `Level` + `BlockPos`
  签名，服务端安全，自动同步）。
- **RCS 推进器公开方法** — `getThrustIndex`、`setThrustIndex`、`getThrustOptions`、
  `getActiveNozzleMask`、`getCurrentThrustPN`、`isFuelAvailable`、`isElectricMode`。
- **自动部署** — `./gradlew build` 现在会自动清理并部署构建产物到游戏 mods 目录
  （仅保留最新单一版本）。
- **安全性** — 所有 ComputerCraft 注册均已隔离并带异常保护；未安装 CC 时模组不受影响。

完整 API 参考文档见 `docs/api.md`。

---

## [1.3.3.0] - 2026-08-05

### English

- HUD preset saved 1-to-1 per helmet; placeholder types; distribution-safe packets;
  HUD performance and horizon rendering fixes.

### 中文

- HUD 预设按头盔一一对应保存；新增占位符类型；网络包分发安全修复；HUD 性能与地平线渲染修复。
