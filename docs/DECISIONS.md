# 关键决策记录（Decisions）

每条记录：**问题 → 选择 → 代价**。用于回答"为什么代码长这样"。

## D1 出图协议统一为 OpenAI Images
- **问题**：本地 ComfyUI、网关任务协议、DashScope 各有实现，按模型名猜协议经常走错。
- **选择**：只保留 `POST {baseUrl}/v1/images/generations`；枚举值保留但解析器永远返回 `OPENAI_IMAGES`。
- **代价**：失去"直连 ComfyUI 调工作流"的 App 内能力（在 ComfyUI 界面里仍可手工调）。

## D2 出图提示词统一英文 tag
- **问题**：按模型名在中/英、自然语言/tag 之间切换，规则与解析器各一套。
- **选择**：`ScenePromptFormat.forModel()` 永远返回 `ENGLISH_TAGS`。
- **代价**：中文或自然语言的后端需要自己适配（当前接入的后端都吃 tag）。

## D3 debug 签名用仓库内的一次性 keystore
- **问题**：debug 签名写死 Windows 路径，CI/Linux 无法构建。
- **选择**：仓库内带 `docs/app/debug.keystore`（一次性密钥，可公开），`localDebug` 用相对路径。
- **代价**：正式发布仍需自己的 release 签名（走环境变量）。

## D4 状态存 world_states 而非加表
- **问题**：每加一个跨会话状态就要改 Room schema + 迁移。
- **选择**：统一用 `companion_world_states`（scope 命名空间）承载，零迁移演进。
- **代价**：类型安全靠代码约束，查询需要按 scope 过滤。

## D5 拆巨型文件（A1）
- **问题**：`MainActivity.kt` 4704 行，改一处要滚很久。
- **选择**：把自包含的 Composable 逐个搬到 `ui/settings/`，共享声明改 `internal`。
- **代价**：文件变多；需要遵守"新页面就新建文件"的约定。