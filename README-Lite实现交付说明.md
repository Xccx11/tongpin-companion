# 同频 Lite · 实现交付说明

| 项 | 内容 |
|----|------|
| 文档版本 | v1（与三份计划书定稿同步） |
| 基线工程 | `D:\deepseek tp\tongpin-android-app-refactor-20260803-094514` |
| 基线备份 | `D:\deepseek tp\tongpin-android-app-backup-20260911-182502`（10207 文件 / 488M） |
| 构建产物 | `docs/app/build/outputs/apk/debug/app-debug.apk` · 60.8 MB · **versionCode 136 / versionName 2.2.0-lite** |
| 验证结果 | `compileDebugKotlin` ✅ · `testDebugUnitTest` **644 tests / 0 failures / 0 errors**（基线 582 + 新增 62） · `assembleDebug` ✅ |
| 安装状态 | **未安装**（按计划书 §14，装机需你明确要求） |

对应设计源：

| 分册 | 文件 |
|------|------|
| 主计划 | `docs/plans/2026-09-11-tongpin-lite-plan.md` |
| 主动消息 | `docs/plans/2026-09-11-tongpin-lite-proactive-plan.md` |
| 记忆系统 | `docs/plans/2026-09-11-tongpin-lite-memory-plan.md` |

---

## 1. 总览：Lite 是什么

Lite 是**产品变薄**，不是完整版里勾选出来的开关组合：

- 保留：**私聊 + 朋友圈 + 发照片/配图**，主动余韵，三层记忆，本地 4 并发对话
- 去掉：群聊、世界页、日记页、偷看、同步、设备长控、图像 API 池、生图规则编辑器
- 一个总开关：`LiteProductConfig.ENABLED`（默认 `true`）——关掉即回退完整版行为，所有改动可逆

---

## 2. 代码目录

### 2.1 新增：`com.aster.companion.lite`（Lite 专属包，13 个源文件）

```text
docs/app/src/main/java/com/aster/companion/lite/
├── LiteProductConfig.kt              Lite 总开关与全部常量（历史 12 条、人设 600 字、
│                                     外观锚点 600 字、Scene 重试 2、768 尺寸、底部三 tab）
├── ScenePhotoRequest.kt              一次「发照片」的状态：四态
│                                     PLANNING / READY / GENERATING / FAILED + 固定文案
│
├── proactive/                        主动消息分册
│   ├── LiteProactivePolicy.kt        纯规则层：Idle 75s、余韵 +5min、冷却 20min、
│   │                                 每拍 2 条、每日 24 条、安静时段 23:30–07:30、
│   │                                 长空闲心跳 2h、朋友圈冷却 6h、生图冷却 3h、
│   │                                 让路判定、候选排序、槽位与落库预算
│   ├── LiteProactivePromptBuilder.kt §6 三段短提示词：探活/正文、两段式内心、朋友圈动态
│   ├── LiteProactiveReplyPolicy.kt  输出闸门：SILENT / 微信体正文 / PHOTO 意图，
│   │                                 拒 markdown、列表、系统腔、超长
│   └── LiteProactiveCoordinator.kt  调度器：15s 一拍，探活 → 生图 → L1 压缩 → 朋友圈同拍
│
└── memory/                           记忆系统分册
    ├── MemoryBudgetPolicy.kt         K=5、整段 400 字、单条 40 字、角色上限 80；
    │                                 kind → importance 推导
    ├── MemoryRetrievalPolicy.kt      无向量 Top-K：关键词命中提升 → 重要度 → 更新时间
    ├── MemoryEmbeddingPolicy.kt      向量分支纯计算：余弦、排序、Top-K+预算
    ├── LiteMemorySelector.kt         统一入口：有 embedding 走向量，否则/失败回退关键词
    ├── MemoryRollupPolicy.kt         L1 解析（TYPE: 事实 / NONE）、去重、触发条件
    ├── MemoryRollupCoordinator.kt    L1 执行：出图跳过、同线程加锁、重试 1 次、去重写库、
    │                                 超软上限裁剪
    └── MemoryManualPolicy.kt         手动记忆：清理消息文本、列表文案、不再提及切换
```

### 2.2 新增：`com.aster.companion.agent`（对话与生图流水线，4 个源文件）

```text
docs/app/src/main/java/com/aster/companion/agent/
├── LiteSystemPromptBuilder.kt        Lite 私聊 system（人设/关系/此刻 + 记得的事）
├── ComfyTagPromptPolicy.kt           tag 串闸门：拒中文、JSON、```、句号、be 动词叙述句、
│                                     少于 2 个 tag、单词片段 >8 词；并做人物锚点保留检查
├── ScenePromptBuilder.kt             Scene 专用 system + user 简报（Who / Recent / Intent）
└── ScenePromptCoordinator.kt         Scene 执行：对话模型写 tag → 校验 → ≤2 次重试 → 预览
```

### 2.3 新增：测试（7 个文件，62 个用例）

```text
docs/app/src/test/java/com/aster/companion/
├── agent/
│   ├── ComfyTagPromptPolicyTest.kt            8    tag 串格式闸门
│   └── LiteSystemPromptBuilderTest.kt         6    Lite system 与「记得的事」
├── lite/proactive/
│   ├── LiteProactivePolicyTest.kt            15    Idle/余韵/让路/心跳/同拍/作息/槽位/限流
│   └── LiteProactiveReplyPolicyTest.kt       11    SILENT/正文/PHOTO/系统腔/冷却/提示词
└── lite/memory/
    ├── MemoryRetrievalPolicyTest.kt          10    关键词提升/重要度/预算/去重
    ├── MemoryRollupPolicyTest.kt              7    事实解析/NONE/去重/触发
    └── MemoryEmbeddingPolicyTest.kt           5    余弦/回退/排序/Top-K
```

### 2.4 改动（4 个既有文件）

| 文件 | 改动内容 |
|------|----------|
| `agent/ConversationTurnCoordinator.kt` | Lite 私聊 system 走 `LiteSystemPromptBuilder` 并提前返回；请求改 `client.chatStream`（无 tools）；历史截断 12 条；组装前取 L2 事实库经 `LiteMemorySelector` 注入 |
| `ui/CompanionHomeViewModel.kt` | 新增 `scenePhoto` 状态与 `requestScenePhoto`/`updateScenePhotoPrompt`/`confirmScenePhoto`/`retryScenePhoto`/`cancelScenePhoto`/`requestMomentScenePhoto`；新增 `rememberMessage`/`setMemoryBlocked`；`init` 启动 `LiteProactiveCoordinator` |
| `ui/MainActivity.kt` | 聊天页「发照片」入口 + `ScenePhotoCard` 四态预览卡；朋友圈「配图」（复用同一卡片）；Lite 设置单页（一地址两模型 + 测试 + 记得的事 + 高级入口）；底部导航三项、首屏消息、隐藏世界邀请/筛选条/建群/偷看/同步；长按气泡「让她记住 / 删除」；气泡与头像视觉收敛 |
| `docs/app/build.gradle.kts` | `versionCode 134 → 136`、`versionName 2.0.92 → 2.2.0-lite` |

> 注：`com.aster.companion.system`、`data`、`domain`、`proactive` 等既有包**未做结构性改动**，Lite 只做消费与分流。

---

## 3. 功能清单

### 3.1 对话（主计划 §4）

| 项 | 实现 |
|----|------|
| system 构成 | `你是「{name}」/ 人设(≤600字) / 关系(一句) / 此刻:地点·活动·心情 / 要求 / 记得的事(最多 5 条)` |
| 禁止项 | Comfy 英文规则、工具 schema、设备长文、家庭群规则——都不进 Lite system |
| 请求形态 | 一轮用户消息 = **1 次** `chatStream`（无 `tools`、无 planning JSON），历史 **12** 条 |
| 并发 | 不同私聊窗口可并行占 LM Studio 槽；App 侧不设全局聊天锁 |
| 多段显示 | 仍按完整版的换行拆气泡开关 |

### 3.2 生图 ScenePipeline（主计划 §5）

| 项 | 实现 |
|----|------|
| 输入 | 人物 `appearancePrompt` 锚点（锁定、≤600 字）+ 最近 6 条简报 + 本次意图 |
| 输出 | **ComfyUI tag 串**（`1girl, solo, long silver hair, ...`），非英文句子 |
| 入队校验 | 非空 / 无 ``` / 无中文 / 无 JSON / 无句号 / 无 be 动词叙述句 / ≥2 个 tag / 单词片段 ≤8 词 |
| 失败处理 | Scene 自动重试 ≤2 次；仍失败 → 状态卡可手动重试 |
| 触发 | 私聊输入栏「**发照片**」；朋友圈动态「**配图**」 |
| 预览 | 出图前卡片可编辑 tag，确认时再校验一次 |
| 落库 | `companion.generate_image`（768x768）、朋友圈 `companion.generate_moment_image`；**tag 不写回聊天记录** |

### 3.3 主动消息（主动分册）

| 项 | 参数（可在 `LiteProactiveSettings` 调） |
|----|------------------------------------------|
| Idle | 最后一条消息后静默 **75s** 记为 Idle |
| 余韵时点 | Idle 后 **+5 分钟** |
| 取消重计 | 任意新消息 → 余韵时点从新的最后消息重算 |
| 探活 | 两段式（内心 ≤15 字 → 正文）：每拍 **2 个角色**；单段式：**4 个** |
| 输出 | 只接受 `SILENT` 或一条 ≤60 字微信体；系统腔/markdown/列表/超 120 字 → 丢弃 |
| 限流 | 每拍落库 ≤2 条、单角色冷却 20min、每日全局 24 条、安静时段 23:30–07:30 |
| 让路 | 出图整拍停；角色自己作息的睡眠区间跳过该角色；用户在该线程活跃时跳过 |
| 长空闲心跳 | 同一 idle 窗口超过 **2 小时**允许再探一次（设 0 关闭） |
| 朋友圈同拍 | 每拍 ≤1 条中文短动态（无图），冷却 **6 小时** |
| 主动生图 | 探活可返回 `PHOTO: 中文意图` → Scene 出 tag → 768 出图；每拍 ≤1 张、冷却 **3 小时** |
| 通知 | 复用 `CompanionNotificationPublisher`（角色名 + 短讯，点击进对应私聊） |

### 3.4 三层记忆（记忆分册）

| 层 | 实现 |
|----|------|
| L0 工作记忆 | `messages` 最近 **12** 条；不再把「最近对话要点」格式化进 system（避免双源） |
| L1 压缩 | 线程新增 ≥**10** 条或 Idle 触发；一次短 chat 提炼 ≤3 条事实（PREFERENCE/PLAN/EVENT/RELATIONSHIP，每条 ≤40 字）；`NONE` 不写库；重复只刷新旧条；出图跳过；每拍最多 1 次 |
| L2 事实库 | system 只注入 **Top-5**、每条 ≤40 字、整段 ≤400 字；排除 `blockedFromMention` 与 `CONVERSATION` 类型 |
| 检索 | 无向量：关键词命中提升 → importance → updatedAt；有 `kind=embedding` 模型：余弦相似度（失败静默回退） |
| 手动 | 长按消息「**让她记住**」写入角色级 L2；设置页「**记得的事**」可删除、可「不再提及/恢复」 |
| 边界 | 沿用 `memorySince`；清空聊天只清短期，角色级 L2 保留 |

---

## 4. 运行时数据流

```text
【私聊一轮】
MainActivity → CompanionHomeViewModel.sendMessage
  → ConversationTurnCoordinator.sendInternal
      ├─ LiteMemorySelector.select(L2 事实, 本轮用户文本)       ← 向量 or 关键词
      ├─ chatPrompt(...) → LiteSystemPromptBuilder.build(...)   ← 短 system + 记得的事
      ├─ client.chatStream(历史 12 条, 无 tools)                ← AI Bridge :8088 → LM Studio
      └─ 落库 + 通知

【发照片】
「发照片」→ ViewModel.requestScenePhoto
  → ScenePromptCoordinator.buildTagPrompt（Scene system + 简报，对话模型另跑一次）
  → ComfyTagPromptPolicy 校验（不合格退避重试 ≤2 次）
  → 预览卡（可改 tag）→ confirmScenePhoto
  → CompanionActionCoordinator.submit(companion.generate_image, 768x768)
  → ImageGenerationCoordinator → AI Bridge /v1/images/generations → ComfyUI
  → 图片作为角色消息落库（聊天气泡只出现照片，不出现 tag）

【主动一拍】每 15s
LiteProactiveCoordinator.tick
  1. planProbes（让路 + 排序 + 槽位）
  2. 并行 probe（两段式内心→正文）→ persist（受每拍限流）/ PHOTO → Scene → 出图
  3. MemoryRollupCoordinator.rollUp（每拍 ≤1 次）
  4. shareMomentIfDue（每拍 ≤1 条动态）

【显存】App 不管理显存；出图前卸载 LLM 由电脑侧 AI Bridge 负责（本次未改动电脑侧）
```

---

## 5. 验收对照

### 5.1 主计划 §12

| 验收项 | 状态 |
|--------|------|
| Chat 请求 system 无 Comfy 英文规则 | ✅ Lite 分支直接返回短 system |
| Scene 输出为 tag 串、非叙述句 | ✅ `ComfyTagPromptPolicy` 闸门 + 8 个单测 |
| 英文 prompt 不出现在聊天气泡 | ✅ tag 只进出图链路，不写回 messages |
| 发照片：tag 预览可见可改、可重试 | ✅ 四态预览卡 |
| 朋友圈配图成功 | ✅ 复用同一流水线 |
| Lite 入口无群聊、无世界邀请/筛选条/建群 | ✅ 底部三项 + 列表过滤 |
| 设置一屏可配完并测试连接 | ✅ 单页 + 高级入口默认隐藏 |
| 私聊 20 轮不跑飞 / 双窗口并行 / 768 出图时延 | ⏳ 需真机（依赖本地 LM Studio 与 GPU） |
| 出图时 LLM 卸载、结束装回 parallel=4 / ctx=65536 | ⏳ 属电脑侧 Bridge（本次按你要求未动） |

### 5.2 主动分册 §11

| 验收项 | 状态 |
|--------|------|
| 私聊停止后约 5 分钟可能出现主动短讯 | ✅ 已实现（真机观察需实机） |
| 期间继续聊天不会出现旧余韵 | ✅ 余韵时点从最后消息重算 |
| 主动文案口语、SILENT 不落库 | ✅ 输出闸门 |
| 可见 2–4 路并发探活 | ✅ `planProbes` 并行（两段式 2 角色） |
| 生图期间无主动 | ✅ `imageBusy` 整拍停 |
| 冷却与每拍上限生效 | ✅ 15 个单测 |
| 主动 system 无 Comfy、无设备长文 | ✅ 提示词单测断言 |

### 5.3 记忆分册 §11

| 验收项 | 状态 |
|--------|------|
| 聊天超 12 条后 L2 出现与对话一致的事实 | ✅ L1 压缩（需本地模型在线） |
| 下一轮 system 含「记得的事」且总长可控 | ✅ ≤5 条 / ≤400 字 |
| 无新信息输出 NONE 不写库 | ✅ |
| 重复事实不刷屏 | ✅ 包含/重叠 ≥0.7 只更新旧条 |
| 出图期间无 L1 | ✅ |
| 与主动同拍不独占槽位 | ✅ 每拍 ≤1 次，排在探活之后 |
| 清空聊天后长期记忆仍在 | ✅ 角色级 L2 不受线程边界影响 |
| 手动记住立即进下一轮 system | ✅ 同表同 Top-K |

---

## 6. 明确的未做项与降级

| 项 | 说明 |
|----|------|
| Embedding 向量分支的端到端实测 | 代码与回退都在，但当前 AI Bridge 没有 `/v1/embeddings`，实际运行走关键词规则（设计内降级） |
| 电脑侧 Bridge | **按你要求全程未动**（模型别名、64k/parallel=4 保留、/status、显存仲裁均未改） |
| 真机验收 T9 | 未执行；APK 未安装 |
| `importance` 独立列 | 现有 `companion_memories` 表无该列，按分册默认值用 `kind` 推导，未做 Room 迁移 |
| 主动自动出图的概率化 | 由模型自行决定是否用 `PHOTO:` 意图，只加冷却与上限，不做随机概率 |

---

## 7. 构建与安装

```powershell
# 构建（项目根目录）
cd "D:\deepseek tp\tongpin-android-app-refactor-20260803-094514"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache --console=plain

# APK
docs\app\build\outputs\apk\debug\app-debug.apk

# 安装（保留数据，需你明确要求；ADB 见 AGENTS.md 的设备规则）
```

回滚两条路：

1. 代码级：把 `LiteProductConfig.ENABLED` 设为 `false` → 恢复完整版行为
2. 工程级：用基线备份 `D:\deepseek tp\tongpin-android-app-backup-20260911-182502` 覆盖
