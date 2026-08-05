# Aero Reformation API

Aero Reformation 为飞行控制方块提供两层 API：

1. **ComputerCraft（CC 电脑）外设** —— 玩家可在游戏里用 Lua 脚本调用。
2. **Java 模组间 API** —— 其他模组可在代码中直接调用（无 CC 依赖）。

---

## 1. ComputerCraft 外设

需要安装 [CC:Tweaked](https://modrinth.com/mod/cc-tweaked)（Forge 1.21.1）。
外设通过 NeoForge `BlockCapability` 注册，未安装 CC 时完全不影响模组运行。

| 方块 | 外设类型 | 说明 |
|---|---|---|
| RCS 推进器 | `aero_rcs` | 推力、创意模式、喷嘴、状态、绑定 |
| 驾驶员动力块（Power） | `aero_power` | 偏航/俯仰限位、座椅高度 |
| 制导弹头 | `aero_warhead` | 目标、搜索模式、PID 调参 |

### 1.1 RCS 推进器 `aero_rcs`

```lua
local rcs = peripheral.find("aero_rcs")  -- 或 peripheral.wrap(side)
```

| Lua 方法 | 参数 | 返回 | 说明 |
|---|---|---|---|
| `getThrust()` | - | number | 当前配置推力（pN） |
| `setThrust(pn)` | pn: number | boolean | 设置推力为最接近的档位（pN） |
| `getThrustIndex()` | - | number | 当前推力档位索引 |
| `setThrustIndex(idx)` | idx: number | boolean | 按索引设置推力档位 |
| `getThrustOptions()` | - | table | 全部可选推力档位（1 起） |
| `isCreative()` | - | boolean | 是否创意（免费燃料）模式 |
| `setCreative(bool)` | bool | boolean | 开关创意模式 |
| `getAngledMode()` | - | number | 斜喷衰减档位 |
| `cycleAngledMode()` | - | boolean | 切换下一档斜喷衰减 |
| `getActiveThrust()` | - | number | 当前实际输出推力（pN） |
| `getActiveNozzleMask()` | - | number | 正在喷气的喷嘴位掩码 |
| `hasFuel()` | - | boolean | 上帧是否有燃料/能量 |
| `isElectric()` | - | boolean | 是否正用电能驱动 |
| `getFuelAmount()` | - | number | 相连储罐内可用燃料（mB） |
| `getBoundSyncPos()` | - | table\|{} | 绑定的方向同步器坐标 `{x,y,z}` |
| `setBoundSyncPos(x,y,z)` | x,y,z | boolean | 绑定方向同步器 |
| `clearBoundSyncPos()` | - | boolean | 解除同步器绑定 |
| `getBoundWarheadPos()` | - | table\|{} | 绑定制导弹头坐标 |
| `isGuidanceMode()` | - | boolean | 是否处于制导模式 |

```lua
-- 示例：巡航时把推力设为 3000 pN 并打开创意模式
local rcs = peripheral.find("aero_rcs")
rcs.setThrust(3000)
rcs.setCreative(true)
while true do
  print("当前推力: " .. rcs.getActiveThrust() .. " pN")
  sleep(1)
end
```

### 1.2 驾驶员动力块 `aero_power`

```lua
local p = peripheral.find("aero_power")
```

| Lua 方法 | 参数 | 返回 | 说明 |
|---|---|---|---|
| `getYawMax()` | - | number | 最大偏航角（度） |
| `setYawMax(deg)` | deg | boolean | 设置最大偏航角（1..180） |
| `getPitchMax()` | - | number | 最大俯仰角（度） |
| `setPitchMax(deg)` | deg | boolean | 设置最大俯仰角（1..90） |
| `getSeatHeight()` | - | number | 座椅高度偏移（格） |
| `setSeatHeight(h)` | h | boolean | 设置座椅高度（-0.2..0.2） |

### 1.3 制导弹头 `aero_warhead`

```lua
local w = peripheral.find("aero_warhead")
```

| Lua 方法 | 参数 | 返回 | 说明 |
|---|---|---|---|
| `getSearchMode()` | - | number | 0=质量 1=最近 2=手动 3=雷达 |
| `setSearchMode(mode)` | mode | boolean | 设置搜索模式 |
| `getTargetPos()` | - | table\|{} | 当前锁定目标坐标 |
| `setManualTarget(x,y,z)` | x,y,z | boolean | 设手动目标并切到手动模式 |
| `getGuidanceMode()` | - | number | 0=直接 1=边沿触发开关 |
| `setGuidanceMode(mode)` | mode | boolean | 设置制导控制模式 |
| `isGuidanceEnabled()` | - | boolean | 开关模式下是否已启用 |
| `unlockTarget()` | - | boolean | 放弃当前目标重新搜索 |
| `getTuning(name)` | name: string | number | 读取调参值 |
| `setTuning(name, v)` | name, v | boolean | 写入调参值 |

可调参字段名：`kp` `ki` `kd` `maxSpeed` `sidePower` `maxThrustPN`
`cruiseAltitude` `brakeCoeff` `proximityRange` `redstoneRange`
`altitudeOffset` `minSearchRange` `maxSearchRange`

```lua
-- 示例：把导弹指向 (100, 80, -200) 并调大比例增益
local w = peripheral.find("aero_warhead")
w.setManualTarget(100, 80, -200)
w.setTuning("kp", 1.2)
w.setTuning("maxSpeed", 30)
```

> 所有方法都在服务器主线程执行（`mainThread = true`），修改会即时同步到客户端。

---

## 2. Java 模组间 API

包：`dev.simulated_team.aero_reformation.api`，无任何 CC 依赖。

- `RcsThrusterApi` —— RCS 推进器：`getThrust/setThrust`、`setCreative`、
  `cycleAngledMode`、`getActiveThrust`、`setBoundSyncPos`、`setBoundWarheadPos` 等。
- `PowerBlockApi` —— 动力块：`getYawMax/setYawMax`、`getPitchMax/setPitchMax`、
  `getSeatHeight/setSeatHeight`。
- `GuidanceWarheadApi` —— 制导弹头：`setManualTarget`、`setSearchMode`、
  `setGuidanceMode`、`unlockTarget`、`getTuning/setTuning`。

所有方法签名统一为 `方法(Level level, BlockPos pos, ...)`，服务端调用安全。

```java
// 示例：其他模组把某个 RCS 推进器的推力设为 4000 pN 并开启创意模式
RcsThrusterApi.setThrust(level, pos, 4000);
RcsThrusterApi.setCreative(level, pos, true);
```

---

## 3. 说明

- 修改类方法（`setXxx`）返回 `false` 表示目标位置没有对应方块/实体，调用方可据此容错。
- CC 外设仅在本模组加载时注册；未安装 CC 时，`ComputerCraftCompat.CC_LOADED` 为 `false`，注册步骤自动跳过。
- 外设注册位于 `dev.simulated_team.aero_reformation.compat.cc`（隔离加载，运行时安全）。
