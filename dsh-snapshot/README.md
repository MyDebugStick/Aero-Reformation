# dsh-snapshot

给 DSH 的**容错快照仓库**：每一轮工作结束自动记一条笔记，把被改动文件的旧版本
按内容哈希存进本地对象库，随时可以一键回退。**不依赖 git**，也不需要仓库本身
是 git 工作区。

## 它解决什么

工作区没有版本控制、或改动没提交时，一次手滑就是永久损失。这个插件在**每一轮
对话边界**自动打点：

- 这一轮**改了什么文件**、**为什么改**（从 tool call 与回复里提取的工作日志）；
- 每个被改文件的**改动前内容**；
- 什么时候可以**撤销这一轮**，什么时候可以**回到某一轮结束时的状态**。

## 安装

```powershell
dsh plugin --profile web add link:D:\users\21656\Desktop\idea\aero-reformation\dsh-snapshot
```

然后**重启 dsh web**（双击桌面 `启动DeepSeekHarness.bat`），再刷新页面。

停用：在 `cordis.patch.yml` 里加 `disabled: true`，或删掉 `insert` 项。

## 存储布局

仓库默认放在 **`D:\users\21656\Desktop\harness\.dsh-snap\`**——放在 harness 卷上
而不是系统盘，因为一个大工作区的基线能到几百 MB，先撑爆的总是 C 盘。

覆盖方式：设 `DSH_SNAPSHOT_ROOT` 环境变量（优先），或在构造 `SnapshotEngine`
时传 `snapRoot`。

每个工作区一个目录，按「目录名 + 路径哈希」命名，互不干扰：

```
<DSH_SNAPSHOT_ROOT>/<workspace-slug>-<hash>/
  meta.json                仓库元信息（分块参数）
  objects/<aa>/<sha256>    内容寻址对象库（chunk 与整文件 blob 共用）
  manifests/<sha256>.json  每轮一份树清单（gc 的扫描依据）
  notes/<noteId>.json      每条笔记：时间、轮次、工作日志、文件清单
  state.json               内容索引与基线
```

## 三层增量

这是"为什么它不会把硬盘吃光"的答案，也是经过实测验证的：

| 层 | 机制 | 效果 |
|---|---|---|
| **Blob 层** | 内容寻址存储 | 相同字节只存一份，跨文件跨轮次自动去重 |
| **Chunk 层** | ≥128 KiB 的文件按 64 KiB 固定分块存储 | 改一行只重写一个 chunk |
| **Note 层** | 清单只存本轮增删改，父指针串链 | 每轮一份小 JSON，而不是整棵树列表 |

实测（`tests/engine.probe.mjs`）：**编辑一个 192 KiB 文件里的一个 64 KiB 块，
只增加约 64 KiB 存储**，另外两个 chunk 直接复用。

## 怎么用

### 命令行

```powershell
node lib/index.js status              # 仓库概况：文件数、对象数、占用、最近一条笔记
node lib/index.js list --limit 20     # 最近的笔记
node lib/index.js show --id <NOTE>    # 某条笔记的详情与它记录的完整树
node lib/index.js revert --id <NOTE>  # 回到那一轮【结束】时的状态
node lib/index.js revert --id <NOTE> --before   # 撤销那一轮（回到它开始前）
node lib/index.js revert --id <NOTE> --dry-run  # 只看计划，不动磁盘
node lib/index.js revert --id <NOTE> --path src/Main.java   # 只回退指定文件
node lib/index.js gc                  # 回收无人引用的对象
```

在别的目录操作时加 `--root <工作区路径>`。

### HTTP（供界面调用）

```
GET  /dsh-snapshot/status.json?root=<dir>    仓库概况
GET  /dsh-snapshot/diag.json                 诊断：最近 80 条生命周期决策 + 挂起轮次
GET  /dsh-snapshot/notes.json?root=<dir>&limit=30
GET  /dsh-snapshot/note.json?root=<dir>&id=<noteId>
POST /dsh-snapshot/revert.json   { "root": "...", "id": "...", "paths": [...], "dryRun": false }
```

**`diag.json` 是排查"为什么没记笔记"的第一站**：它逐轮列出
`baseline ok / NO NOTE / FAILED`、`fs/observed` 收到的工具名，以及挂起轮次里
累积的候选文件。宿主日志被重定向或截断时，这是唯一可靠的观测点。

### 自动记录

插件挂在 DSH 的会话事件上，无需手动触发：

- `turn/start` → 扫描基线（mtime+size 快路径，内容没动就不重算哈希）
- `fs/observed` → 记下本轮被动过的文件，把轮末的比对范围收窄
- `turn/end` → 比对内容 → 存旧版本 → 落一条笔记

## 两种回退语义

这是最容易搞混、也是实测中反复修正过的地方：

- **`revert --id N`**：把工作区恢复成**第 N 轮结束时**的样子。
- **`revert --id N --before`**：**撤销第 N 轮**，恢复成它**开始前**的样子。
  新建的文件会被删除，被删除的文件会从对象库原样复活。

第一轮之前没有可恢复的状态，`--before` 对第一条笔记会明确拒绝并说明原因，
而不是猜一个状态出来。

## 设计取舍（为什么不是另一种做法）

- **不用 `fs/observed` 做备份**：它在写入**落盘之后**才触发，拿不到旧内容。
  它在这里只用来收窄轮末的比对范围。真正的备份靠 `turn/start` 的基线扫描。
- **清单为什么默认存增量**：早期实现每轮存整棵树清单，结果清单本身成了最大
  开销（2000 个文件就是上千条 JSON）。改成增量 + 父指针后，每轮只有几百字节。
- **`scan` 与轮末共用一套分层策略**：曾经两处各存一次（一处整存、一处分块），
  同一个文件被存了两份。现在分层决策只在 `detectDescriptor` 一处。
- **`manifests/` 目录不能省**：清单同时以对象形式存在与目录文件存在。只存对象
  的话，gc 扫不到引用，会把笔记还在用的 chunk 当垃圾删掉——这是实测中抓到的
  最危险的一个 bug，所以 `gc` 后有专门的回退验证。

## 配置

编辑 `lib/index.js` 顶部的 `DEFAULT_CONFIG`，或构造 `SnapshotEngine` 时传
`config` 覆盖：

| 键 | 默认 | 说明 |
|---|---|---|
| `enabled` | `true` | 总开关 |
| `chunkThresholdBytes` | `131072` | 达到此大小改用分块存储 |
| `chunkSize` | `65536` | 分块粒度 |
| `maxFileBytes` | `16777216` | 超过则不存内容，只记指纹 |
| `keepNotes` | `300` | 笔记保留上限，超出删最旧 |
| `maxFilesPerScan` | `20000` | 单次扫描文件数上限 |
| `excludes` | 见源码 | 跳过的目录名（`.git`、`build`、`node_modules` 等） |
| `skipSuffixes` | 见源码 | 跳过的后缀（`.jar`、`.png` 等二进制） |

## 测试

```powershell
node tests/engine.probe.mjs        # 12 项引擎断言：增量、清单链、两种回退、gc 后回退
node tests/plugin.probe.mjs        # 插件面：apply() 装配、事件序列、端到端落笔记
node tests/multiturn.probe.mjs     # 多轮链式回退：10 轮回退/前进/撤销，目录与删除的复原
node tests/stress.probe.mjs 2000   # 压力：2000 文件工程的耗时与存储
node tests/debug.mtime.mjs         # 验证本机文件系统 mtime 精度（快路径的前提）
```

全部跑在系统临时目录，不碰真实工程。**改动引擎后至少跑前三个**。

### 多轮回退实测（新增，10 轮历史）

单次回退是测不出链式问题的。`multiturn.probe.mjs` 建 10 轮历史（改动/新建/删目录/删除/重命名），
然后来回走：

| 场景 | 结果 |
|---|---|
| 回退到中间某轮 | 后续轮次新建的文件被删掉 ✓ |
| 回退到更早 | 中间轮次的删除被复活、重命名被撤销 ✓ |
| 回退到最早 | 后续轮次建的整个目录树消失 ✓ |
| **再往前走**回到最新 | 完全复现，字节级一致 ✓ |
| 回退 → 继续工作 → 再回退 | 新笔记仍可达，链没被污染 ✓ |
| `--before` 连撤三轮 | 每步都落在正确的前一状态 ✓ |
| 清单链完整性 | 每个回退目标都能解析，depth 10 ✓ |

### 多轮压测暴露并修掉的四个真 bug

这四个都**过不了单次回退的测试**，只有链式回退才现形：

1. **回退不删后续轮次新建的文件** —— `revert` 调用 `applyManifest` 时漏传了
   `previousEntries`，删除分支永远跳过。改为传入实时索引，由"索引 − 目标树"决定删什么。
2. **从未被修改过的文件在链里没有记录** —— 回退到它被删除之前的状态时，
   它无法复活。修法：每轮把"索引里有、清单链里从没提过"的路径**固化**进本轮清单
   （记 `pinned`，不计入 `changedCount`，所以不会凭空造出空笔记）。
3. **回退不清空目录** —— 只删文件，留下 `pkg/deep/nested/` 这种空壳。
   修法：删完文件后自深向浅清理空目录（`ENOTEMPTY` 就保留）。
4. **跳过"已经一致"的文件时信了过期的索引** —— 为省 I/O 加了"内容没变就不重写"，
   但判断依据取自 `state.files` 的哈希。只要别的东西（agent 的编辑工具、人、别的进程）
   动过文件，索引就过期，于是**该恢复的文件被跳过、工作区留在错的状态**。
   修法：**读磁盘真实内容**比对（先比 size，相同再算哈希），永远不信索引。

> **第 4 条是最值得记住的一课**：性能优化如果建立在一个可被外部操作破坏的假设上，
> 它就不是优化，是数据损坏。压测立刻抓到了它——`stress.probe` 的两项断言当场变红。

### 第五个 bug：真实回退时误删文件（2026-09-11）

前四个是压测抓的，这一个是**在真实项目上跑一次回退**抓的——而且它**静默删掉了一个源码文件**。

**现象**：对 DIG 工作区跑 `revert --id <某轮>`，`restored = 0`、`alreadyCorrect = 50`，
但 `removed = 1`，删掉的是 `UsageTracker.java`。它明明在那一轮就存在。

**两个机制叠加造成：**

1. **`beginTurn` 先 `scan` 再建 `baseline`**（`lib/index.js:524`）。于是任何**在轮次之间**
   出现在磁盘上的文件，会被这一轮的 baseline 直接吸收；`endTurn` 比对时它"没动过"，
   **既不记为 created，也不记为 modified**。
2. **`endTurn` 在没有改动时提前返回**，而 pin 步骤排在提前返回**之后**：

   ```js
   if (fileNotes.length === 0) {
     repo.state.baseline = null
     return null          // ← pin 在 601 行，永远到不了
   }
   ```

   于是这样的文件**在索引里、却不在清单链里**。回退的删除分支是
   "索引 − 目标树"，它就被当成多余文件删掉了。

**第 2 条 bug（pin 机制）本是为第 1 种情况写的**——修得对，但被一个提前返回挡在门外，
只在"这一轮恰好还有别的改动"时才生效。**一个只在特定条件下才执行的修复，
和没有修复的区别，只在那个条件不成立时才看得出来。**

**修法**：把 pin 步骤移到提前返回**之前**，并把条件放宽为
`fileNotes.length === 0 && pinnedNotes.length === 0`。
`pinned` 不计入 `changedCount`，所以"只 pin 不改"的笔记依然报告 0 处编辑。

**回归断言**（`engine.probe.mjs`）：在轮次之间造一个文件 → 跑一轮无编辑的 turn →
断言笔记被切出、`changedCount === 0`、该文件出现在 `pinned` 里，
**并且回退之后它还在**。

> **这一课**：前四个 bug 都在引擎自己的沙箱里现形了，第五个没有——
> 因为沙箱里的每一轮都有改动。**测试要覆盖"什么都没发生"的那一轮。**

### 压力测试实测（2000 文件 / 5 轮）

| 场景 | 结果 |
|---|---|
| 冷基线扫描（首次） | 7356 ms，2000 文件入索引 |
| 热基线扫描（无改动） | 843 ms |
| 改 1 个文件的一轮 | 851 ms，**+291 B** |
| 改 200 个文件的一轮 | 1634 ms，+208218 B |
| 等长内容重写（mtime 可能不变） | 靠内容哈希抓到 |
| 1 MiB 文件改一个 64 KiB 块 | 876 ms，**+66923 B**（整存需 1048576 B，省 93.6%） |
| gc | 306 ms，收回 201 个对象 |
| gc 之后回退 200 个路径 | 636 ms，**字节级精确** |
| 汇总 | 源树 2763 KiB → 5 轮后仓库 1.85 MiB |

首次基线扫描是主要开销（每个文件要读内容并入库），之后每一轮只花"改动量"的钱。
基线扫描在 `turn/start` 异步发起，不阻塞对话。

`engine.probe.mjs` 覆盖：笔记增删改记录、分块增量、清单链解析、两种回退语义、
dry-run 不落盘、gc 可达性、以及 **gc 之后回退仍然可用**。
