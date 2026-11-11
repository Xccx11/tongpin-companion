# 同频 Lite · 记忆系统计划书

| 项 | 内容 |
|----|------|
| 文档 | 独立分册 |
| 状态 | 设计定稿 |
| 主计划 | `2026-09-11-tongpin-lite-plan.md` |
| 主动消息 | `2026-09-11-tongpin-lite-proactive-plan.md` |
| 运行时 | Qwen 9B 本地免费 · parallel=4 · 总 ctx 65536 · 出图卸载 LLM |
| 当前桥 | 无 `/v1/embeddings`（语义检索不可用） |

---

## 1. 目标

在 Lite 下解决「只记得最近 12 条」与「完整版记忆塞爆 system」两头问题：

1. 刚聊过的事不丢（工作记忆）。  
2. 这一阵的关键事实能留下（压缩记忆）。  
3. 长期记得的事以 **少量、可进 system** 的方式回来（事实库）。  
4. **不依赖 Embedding**；有则增强。  
5. 与 4 并发、Idle 余韵、出图独占共存。

---

## 2. 范围

### In Scope

- 三层记忆：L0 工作 / L1 压缩 / L2 事实库  
- Lite system 注入「记得的事」Top-K  
- Idle 时自动压缩（与主动消息同拍可并行）  
- 手动「让她记住」、删除、禁止提及  
- 无 Embedding 的关键词/重要度排序  
- `memorySince` 窗口边界（沿用）  

### Out of Scope

- 完整版世界状态 / 群派生记忆 / 家庭事实链  
- 每轮实时叙事摘要再进 system  
- 强制配置 Embedding 模型才能聊天  
- 把 Scene 英文 tag 或主动 SILENT 写入长期记忆  

---

## 3. 问题诊断（基线）

| 层 | 现状 | Lite 问题 |
|----|------|-----------|
| L0 | `CHAT_HISTORY_LIMIT=12` | 窗口外失忆 |
| 线程摘要 | 文档：网关 >8000 字压缩 | Lite 只发 12 条，网关摘要几乎用不上；且不得再叠 App 叙事摘要 |
| 明确记忆 | 全量可进 system | 9B + 短 system 预算会爆 |
| 语义 | Embedding 或降级 | 本地无 embeddings，永远降级 |
| 边界 | `memorySince` | 正确保留 |

**原则：** 压缩只产出 **事实行**，不是聊天缩写；system 只吃 **Top-K 事实**。

---

## 4. 三层架构

```text
┌─────────────────────────────────────────┐
│ L2 事实库（角色级长期）                    │
│   PREFERENCE / PLAN / EVENT / RELATIONSHIP│
│   → system 仅注入 Top-K（≤5 条，≤400 字）  │
└──────────────────▲──────────────────────┘
                   │ 写入（去重）
┌──────────────────┴──────────────────────┐
│ L1 压缩记忆（Idle / 每 N 条触发）          │
│   1 次短 chat → 0–3 条事实                │
└──────────────────▲──────────────────────┘
                   │ 摘录最近窗口
┌──────────────────┴──────────────────────┐
│ L0 工作记忆 messages 最近 12 条            │
└─────────────────────────────────────────┘
```

### 4.1 L0 — 工作记忆

- 保持最近 **12** 条进入 `messages`。  
- **禁止**再把「最近对话要点」格式化进 system（避免与 L0 双源）。  
- 流式占位 `stream:` 不进 L0/L1。

### 4.2 L1 — 压缩记忆

**触发**

| 条件 | 动作 |
|------|------|
| 线程自上次压缩后新增 ≥ 8–12 条 | 压缩一次 |
| 或线程进入 Idle（可与主动 +5min 同拍） | 压缩一次 |
| Bridge `mode=IMAGE` / Comfy 队列非空 | **跳过** |
| 用户正在该线程 ACTIVE | **推迟到 Idle** |

**一次调用（占 1 槽，可与探活并行）**

```text
System:
只提炼「以后还用得上的事实」。最多 3 条，每条 ≤40 字中文。
类型只能是：PREFERENCE | PLAN | EVENT | RELATIONSHIP
禁止：复述整段聊天、日记体、编造用户没说过的事、提系统/模型。
无新事实则只输出 NONE

User:
角色：{name}
最近对话摘录：{L0 的 8–12 条，每条截断}
```

**入库**

- `MemoryEntity`：`characterId` 有值；`threadId` 可空（角色级）或先线程后晋升（Lite 建议 **角色级**）。  
- **不要**使用 `MemoryKind.CONVERSATION` 作为注入 system 的类型。  
- `importance`：PLAN/PREFERENCE 默认 3；EVENT 默认 2；RELATIONSHIP 默认 2。  
- `blockedFromMention=false` 默认。

**去重**

- 与该角色已有 L2：子串包含或高重叠 → **更新 `updatedAt`/`importance`**，不新增。  
- 每角色 L2 条数软上限（如 80），超出丢弃低重要度且最旧。

**失败**

- 输出含 markdown/过长/无法解析 → 重试 1 次 → 放弃本拍（不写库）。

### 4.3 L2 — 事实库与 system 注入

Lite `LiteSystemPromptBuilder` 在末尾追加（计划书 §总预算仍受控）：

```text
记得的事（最多 {K} 条）：
- {fact}
- {fact}
```

**Top-K（无 Embedding）**

1. 排除 `blockedFromMention`  
2. 排序：`importance` 降序 → `updatedAt` 降序  
3. 与本轮 user 文本做简单关键词/token 交集，命中的提升  
4. 截断单条至 ~40 字；**K=5**；整段 ≤ **400** 汉字  

**有 Embedding（可选后期）**

- 桥提供 `/v1/embeddings` 时用向量 Top-K，替换第 3 步；失败回退本节规则。

**清空聊天**

- 继续：推进 `memorySince`、删线程数据；**保留**角色级 L2（含明确长期记忆）。

---

## 5. 与 4 并发 / 主动消息 / 出图

### 5.1 同拍布局（Idle / +5min）

```text
槽0 角色A 主动探活（SILENT / 短讯）
槽1 角色B 探活（可选）
槽2 角色A（或刚结束的线程）L1 压缩
槽3 备用 / 角色C 探活
```

两段式主动开启时：探活占 2 槽 → L1 仍用剩余槽，**每拍最多 1 次 L1**。

### 5.2 优先级

```text
用户对话  >  已开始的出图  >  主动探活  ≥  L1 压缩
```

- 出图：L0 可继续发用户请求；**L1/探活全部停**。  
- L1 不得阻塞用户下一轮 chat。

---

## 6. 手动记忆（质量优先）

| 操作 | 行为 |
|------|------|
| 长按消息 →「让她记住」 | 写入 L2，默认 PREFERENCE/EVENT，importance=3 |
| 角色「记得的事」列表 | 只读展示；可删除、可「不再提及」 |
| 自动压缩开关 | 设置默认开；可关仅用手动 |

手动与自动同一张表、同一套 Top-K；手动可提高 importance。

---

## 7. 明确禁止

| 禁止 | 原因 |
|------|------|
| 每轮生成叙事摘要进 system | 双重事实、system 变厚 |
| L1 输出直接当 assistant 消息 | 污染对话 |
| 主动 SILENT / Scene tag 入 L2 | 非用户事实 |
| 全量 Memory 注入 | 9B 被淹没 |
| 聊天强依赖 Embedding | 本地桥暂无 |
| CONVERSATION 记忆注入 | 防循环自引用（沿用既有策略） |

---

## 8. 数据与边界（沿用 + 增量）

| 项 | 说明 |
|----|------|
| `ThreadEntity.memorySince` | 新窗口/清空边界，不改语义 |
| `MemoryEntity` | 复用 kind/content/importance/时间/blocked |
| 新增标记（可选） | `source=manual\|compress` 便于列表与调试 |
| 压缩游标 | 每线程 `lastCompressedMessageId` 或时间戳，避免重复压同一段 |

---

## 9. 模块

| 模块 | 职责 |
|------|------|
| `MemoryBudgetPolicy` | K、字符上限、importance |
| `MemoryRollupCoordinator` | 触发 L1、调 chat、解析、去重写库 |
| `MemoryRetrievalPolicy` | Top-K + 关键词提升（无向量） |
| `LiteSystemPromptBuilder` | 追加「记得的事」 |
| `MemoryManualActions` | 长按记住 / 列表 / 屏蔽 |
| （可选）`EmbeddingMemoryRetriever` | 桥支持后启用 |

---

## 10. 参数默认

| 参数 | 默认 |
|------|------|
| L0 | 12 |
| L1 触发条数 | 10 |
| L1 每次最多事实 | 3 |
| L1 重试 | 1 |
| L2 注入 K | 5 |
| L2 注入预算 | ≤400 汉字 |
| 角色 L2 条数上限 | 80 |
| 单事实长度 | ≤40 字 |
| 出图中 | 跳过 L1 |
| 自动压缩 | 开 |

---

## 11. 验收

- [ ] 聊天超过 12 条后，L2 中出现与对话一致的事实（非编造）  
- [ ] 下一轮 system 含「记得的事」且总长可控  
- [ ] 无新信息时输出 NONE，不写库  
- [ ] 重复事实不刷屏堆条  
- [ ] 出图期间无 L1 请求  
- [ ] 与主动同拍时 chat 槽不被 L1 独占导致用户卡死  
- [ ] 清空聊天：短期没了，长期「记得的事」仍在  
- [ ] 手动记住立即出现在下一轮 system（在 K 内）  

---

## 12. 实施顺序

| 步 | 内容 | 验收 |
|----|------|------|
| M0 | Lite system 注入现有 L2 Top-K（只读） | 人工加的记忆能进 system |
| M1 | MemoryRollup + Idle 触发 + 去重 | 自动出现事实 |
| M2 | 关键词提升 + 设置「记得的事」+ 手动记住 | 可用可管 |
| M3 | （可选）Embedding Top-K | 有 embeddings 时启用 |

---

## 13. 与其它分册关系

| 文档 | 关系 |
|------|------|
| Lite 主计划 | 对话 system 构成、12 条窗口 |
| 主动消息分册 | Idle +5min 拍可搭载 L1 |
| 本分册 | L1/L2/检索/注入唯一设计源 |

若实现与文档冲突，以本分册记忆行为为准。

---

## 14. 一句话

**L0 管刚说的；L1 在 Idle 用空闲槽提炼成最多 3 条事实；L2 每次只把最多 5 条高价值事实放进 Lite system；无 Embedding 可用；出图让路；手动记忆优先。**
