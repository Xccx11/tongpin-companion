# 同频 Lite APK 改造计划书

| 项 | 内容 |
|----|------|
| 版本 | v1（设计定稿） |
| 基线工程 | `D:\deepseek tp\tongpin-android-app-refactor-20260803-094514` |
| 本地算力 | RTX 4070 12GB · Qwen3.5-9B（Q5） · Illustrious SDXL |
| 网关 | AI Bridge `:8088`（显存仲裁）· LM Studio `:1234` · ComfyUI `:8188` |

---

## 1. 目标

在本地 12GB 显卡上交付可用的 **Lite 产品变体**（不是完整版里勾选开关），满足：

1. 私聊 RP 稳定、提示词短、可吃满 LM Studio **4 并发**。
2. 生图仍由模型产出 **完整 ComfyUI 格式提示词**（tag 串，非英文句子），且 **结合人物 + 最近上下文**。
3. 聊天与生图 **流水线分离**：Comfy 规则不进对话 system，英文 prompt 不写回聊天记录。
4. 保留 **朋友圈**；**不要群聊**。
5. 设置极简；UI 克制、美观。

---

## 2. 范围

### 2.1 做（In Scope）

| 模块 | 内容 |
|------|------|
| 产品 | Lite 路径：私聊 + 朋友圈 + 发照片/配图 |
| 对话 | Lite system、单次 chat、无 tools、短历史 |
| 生图 | 独立 ScenePipeline：人物锚点 + 最近简报 → 英文 **tag 串** → 预览/确认 → `/v1/images/generations` |
| 显存 | Bridge 卸载/装回；出图全局串行 |
| 并发 | Chat 多窗口/多槽；Chat ∥ Scene 写 tag；出图前排空 |
| UI | 聊天/列表/生图状态/设置视觉收敛 |
| 设置 | 一地址两模型 + 测试 + 备份 |
| 电脑桥 | 模型别名、保留 64k/parallel 4、768 生图、system 置顶 |

### 2.2 不做（Out of Scope）

- 家庭群、多角色接话、群图、同步私聊进群  
- 偷看（可二期）、主动出图、设备感知、Shizuku、系统模式  
- 规划模型 / Embedding / 视觉模型独立配置  
- 图像 API 池多 Provider、生图总规则编辑器（ImagePromptDebug）  
- 重写 World Director  

---

## 3. 架构

```text
手机 App (Lite)
    │ OpenAI 兼容
    ▼
AI Bridge :8088 ──(ngrok / 局域网)── 外网同一 Base URL
    ├─ POST /v1/chat/completions  → LM Studio :1234
    │     Qwen 9B · ctx=65536 · parallel=4 · q8 KV
    ├─ POST /v1/images/generations → ComfyUI :8188
    │     Illustrious checkpoint
    └─ 显存仲裁：IMAGE 时卸 LLM / CHAT 时卸 SDXL

App 内两条流水线（系统、入参、出参分离）：
  ChatPipeline   Lite 人设 + messages → 中文台词
  ScenePipeline  人物锚点 + 最近简报 + 意图
                 → 英文 tag 串（Comfy 格式）
                 → 确认后 images/generations
```

**显存铁律**

| 模式 | 占用 |
|------|------|
| 对话 | Comfy 卸净；LLM ~11–12GB（贴边） |
| 生图 | LLM 卸净；SDXL；**全局 1 路** |
| 禁止 | 对话与生图同时占卡 |

---

## 4. 对话 Lite

### 4.1 System（约 800–1200 汉字）

```text
你是「{name}」。
人设（截断约 600 字）：{personalityPrompt}
关系（一句）：{relationshipPrompt}
此刻：{location} · {activity} · 心情 {mood}
要求：中文、第一人称、短；可带一条 ≤20 字动作；
不编造用户未说过的事实；不提及模型/系统/提示词。
```

**禁止出现：** Comfy 英文规则、工具 schema、设备长文、家庭/群规则。

### 4.2 请求形态

- `messages`：最近约 **12** 条（超长截断，App 不做第二套摘要）  
- `stream=true`（SSE）  
- **无** `tools` / `scene`  
- 一轮用户消息 = **1 次** chat（Scene 另计）

### 4.3 并发

- 同一 `characterId`：同时最多 1 条 in-flight  
- 不同私聊窗口：可并行占 LM Studio 多槽（最多 4）  
- Bridge 在 CHAT 态 **不**全局锁 chat；仅 `enter_image_mode` 排空后卸载  

---

## 5. 生图 ScenePipeline

### 5.1 原则

| 原则 | 说明 |
|------|------|
| 结合上下文与人物 | Scene 读 **人物锚点 + 最近 3–6 轮简报 + 本次意图**，保证贴剧情、自然 |
| 不进对话 | Comfy 规则只在 Scene system；tag 结果不写回 messages 正文 |
| 格式 | **Comfy tag 串**：`tag, tag, tag`，禁止英文叙述句/JSON/Markdown |
| 可控 | 出图前可预览/编辑；你的关键要求可校验必须保留 |

### 5.2 Scene 请求（独立）

**System（仅此流水线）**

- 完整 Comfy 正向硬规则（tag 列表输出）  
- 角色 `appearancePrompt` 锚点（锁定，截断 400–600 字，尽量 tag 化）  
- 固定输出约束：仅英文 tag、逗号分隔、无句号段落  

**User 简报（不是整段聊天）**

```text
Who: {name}，外观要点
Recent: 最近 3–6 轮 user/角色 发言压缩（数百字内）
Intent: 用户要什么照片 / 动态配什么图（一句话）
Return only ComfyUI tag-style positive prompt.
```

**示例输出（目标形态）**

```text
1girl, solo, long silver hair, golden tiara, kitchen, cooking,
smile, looking at viewer, warm lighting, anime style,
masterpiece, best quality
```

**禁止**

```text
A beautiful girl is standing in the kitchen and smiling...
```

### 5.3 入队前校验（App）

- 非空；无 ```；无中文；无 JSON  
- 含逗号分隔的多个 tag；无成段英文句（可用启发式：无连续 `a/an/the …` 长句）  
- 人物锚点关键 tag 尽量保留  
- 不合格：Scene 自动重试 ≤2 次 → 仍失败则状态卡可手动重试  

### 5.4 触发与确认

| 入口 | 行为 |
|------|------|
| 私聊「发照片」 | 可选一句意图 → 生成 tag → **预览（可改）** → 确认出图 |
| 朋友圈「配图」 | 同上，意图来自动态正文/上下文 |
| 确认后 | Bridge 卸 LLM → `POST /v1/images/generations`（`768x768`，`b64_json`）→ 装回 LLM |

聊天里只出现照片气泡；可留一句「已发照片」系统事件，**不带完整 tag**。

### 5.5 与 4 并发

```text
Chat 槽 1 ∥ Scene 写 tag 槽 2  （可并行）
两边就绪后 → 卸 LLM → Comfy 出图（1 路）→ 装回 4 槽
```

---

## 6. 朋友圈

- 动态正文：中文 RP（可走 Lite Chat 或专用短文逻辑）  
- 配图：复用 ScenePipeline（人物 + 动态相关上下文 + tag 格式）  
- UI：无外框卡片；配图进度与私聊生图状态组件共用  
- 出图全局串行，避免与私聊同时生图  

---

## 7. 前端视觉（Lite）

### 7.1 方向

深色石墨 + 玫瑰点缀；**减少 accentWash 粉框**；少描边、多底色分层；信息密度下降。

### 7.2 聊天详情（P0）

| 项 | 规格方向 |
|----|----------|
| 顶栏 | 返回 / 角色名 / 更多；无偷看、无同步 |
| 对方气泡 | `surface2`、无边、圆角 18–20 |
| 用户气泡 | `primary` 实底；字色用主题 onPrimary（去掉硬编码） |
| 头像 | 对方 32dp |
| 输入栏 | 胶囊输入框；圆形发送；左侧「发照片」主操作 |
| 生图状态 | 独立卡片：排队 / 整理画面 / 生成中 / 失败重试；不与设置区块同风格 |

### 7.3 列表 / 朋友圈 / 设置

- 列表：无筛选条、无世界邀请；大头像行  
- 朋友圈：无外框卡片  
- 设置：单页分区、无边框行、主按钮 primary  

### 7.4 组件收敛

统一 `ChatBubble`、`PhotoStatusCard`、`PrimaryIconButton`；字号与间距 token 化。

---

## 8. 设置（Lite 单页）

```text
连接
  Base URL          （必填，一个）
  API Key           （可选，默认 local）
  对话模型          获取模型 → 下拉
  图片模型          获取模型 → 下拉
  图片尺寸          默认 768×768
  [测试连接] [测一句] [测一张小图(可选)]

显示
  多段回复拆气泡    开关

数据
  导出/导入备份     折叠

关于
  版本 · [高级入口(默认隐藏)]
```

**固定 / 不暴露：** 图片协议 = 标准 OpenAI Images；无 API 池；无生图规则编辑器；无规划/Embedding/视觉模型。

**建议默认模型名（与桥别名对齐）**

| 用途 | 展示名 | 实际 |
|------|--------|------|
| 对话 | `qwen35-nothink:latest` | Qwen 9B |
| 图片 | `wai-nsfw-illustrious` | Illustrious checkpoint |

---

## 9. 电脑侧 Bridge

| 项 | 要求 |
|----|------|
| 别名 | 上表两个展示名映射到真实模型 |
| Reload | 保留 `context_length=65536`、`parallel=4`；**禁止**默认压成 8192 |
| 聊天 | system 消息自动置顶；多槽并发转发不加全局锁 |
| 生图 | 接受 tag 长 prompt；默认/尊重 `768x768`；`b64_json` |
| 状态 | `/status`：mode、inflight、ctx、parallel |
| 外网 | ngrok 单隧道 `:8088`；桌面启动脚本打印 Base URL |

---

## 10. 实施任务拆分

| ID | 任务 | 验收 |
|----|------|------|
| T1 | Bridge：模型别名 + reload 保留 64k/4 + 768 生图 | 用文档名可 chat/生图；unload/load 后 parallel=4 |
| T2 | `LiteSystemPromptBuilder` + Chat 分流（无 tools、无 Comfy） | 抓包 system 无 Comfy 长文；一轮 1 次 chat |
| T3 | `ScenePromptCoordinator`：锚点+简报→tag 串+校验 | 输出为 tag 列表；失败重试；不写回 messages |
| T4 | 私聊「发照片」→ 预览 → 出图闭环 | 768 成功；状态卡四态；失败可重试 |
| T5 | 朋友圈配图复用 T3/T4 | 动态可出图；不与私聊并发出图 |
| T6 | 设置单页化 + 测试连接 | 一地址两模型可保存并测通 |
| T7 | UI P0：气泡/顶栏/底栏/状态卡 | 视觉按 §7；Lite 无群/偷看/同步入口 |
| T8 | 隐藏/不编译群聊与重型设置路径 | Lite 入口不可进群聊/池/debug |
| T9 | 真机验收 | §12 清单通过 |

依赖：T1 → T2/T3；T3 → T4 → T5；T6/T7 可并行；T8 收尾；T9 最后。

---

## 11. 风险与对策

| 风险 | 对策 |
|------|------|
| 64k×4 贴边 OOM | 生图前必卸 LLM；失败则总 ctx 降为 32768 |
| 9B 偶发长句当 prompt | 入队硬校验 + ≤2 次自动重写 |
| 外观锚点质量差 | 角色编辑里强制短英文/中英 tag 锚点 |
| ngrok 域名变化 | 设置单 Base URL；LAN IP 优先 |
| 桥 reload 丢 parallel | T1 验收项 |
| 同角色双请求 | 每角色锁 |

---

## 12. 验收清单

**功能**

- [ ] 私聊连续 20 轮不跑飞、无 system 顺序 500  
- [ ] 两私聊窗口可并行回复（`chat_inflight` 可达 ≥2）  
- [ ] 发照片：tag 预览可见、可改；出图成功  
- [ ] 出图时 LLM 卸载，结束后装回且 parallel=4、ctx=65536  
- [ ] 朋友圈配图成功  
- [ ] Lite 入口无群聊  

**质量**

- [ ] Chat 请求 system 无 Comfy 英文规则  
- [ ] Scene 输出为 tag 串，非叙述句  
- [ ] 英文 prompt 不出现在聊天气泡正文  
- [ ] 设置一屏可配完并测试连接  

**性能**

- [ ] 768 出图（含首次切换）在可接受时延内  
- [ ] 生图与聊天不同时占卡  

---

## 13. 建议排期（粗）

| 阶段 | 内容 |
|------|------|
| D1 | T1 桥 + T6 设置骨架 |
| D2 | T2 Lite 对话 |
| D3–D4 | T3–T4 Scene + 发照片 |
| D5 | T5 朋友圈 + T7 UI P0 |
| D6 | T8 收口 + T9 真机 |

---

## 14. 交付物

1. Lite 代码路径（或 flavor）与默认配置  
2. Bridge 更新与桌面启动脚本  
3. 本计划书 + 真机验收记录  
4. （按你要求再决定）是否安装 APK  

---

## 15. 已定决策摘要

1. **Lite = 产品变薄**，不是完整版开关堆砌。  
2. **不要群聊；保留朋友圈。**  
3. 生图：**结合人物 + 最近上下文**；输出 **Comfy tag 串**。  
4. 聊天与 Scene **进程/请求分离**；Comfy 不进对话。  
5. **4 并发**用于多窗口短对话与 Chat∥Scene；**出图独占**。  
6. 设置：**一个 Base URL + 两个模型 + 测试**。  
7. 视觉：石墨+玫瑰、减粉框、聊天与状态卡优先。

---

*实现前若需改排期或砍 T5/T7，以本文件为基线做一次修订即可。*
