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
## D6 A2 拆分路线（实测依赖）

用"扫方法体标识符"的方式量出真实依赖（**不要凭签名猜**，吃过亏）：

| 方法 | 真实依赖 | 能否单独搬 |
|---|---|---|
| `decideShot`（行 99-133） | `transport` `cipher` `inputsFor` `character` `conversationProfile` `situationOf` | ✗ 必须连 `inputsFor`/`situationOf` 一起 |
| `inputsFor`（行 68-75） | `dao` `imageRuleResolver` `character` `conversationProfile` | 可（与 decideShot 同簇） |
| `buildTagPrompt`（行 186-323） | `dao` `transport` `cipher` `imageRuleResolver` `inputsFor` `character` `conversationProfile` `situationOf` `LiteProductConfig` | ✗ 依赖最多（9 个） |
| `buildTagPromptForThread`（行 172-183） | 仅转调 `buildTagPrompt` | 随 buildTagPrompt |
| `transcribeAppearance`（行 342-366） | `transport` `imageRuleResolver` `renderPromptBuilder` | 可（较干净） |
| `validateEditedPrompt`（行 389-403） | **无**（纯函数） | ✓ **最容易，可先做** |

**结论**：`ScenePromptCoordinator` 的拆分应该按"簇"来，而不是按方法：

1. **簇 A（决策）**：`decideShot` + `inputsFor` + 相关私有字段 → `SceneDecider`（依赖 `dao` + `cipher`）
2. **簇 B（组装）**：`buildTagPrompt*` + `transcribeAppearance` → `SceneComposer`（依赖 `dao` + `transport` + `renderPromptBuilder` + `imageRuleResolver`）
3. **纯函数**：`validateEditedPrompt` → 独立 `ScenePromptValidation`

每搬一簇就编译 + 全量测试 + 推送（CI 会再验一遍）。
### D6 补充：`inputsFor` 的搬迁阻塞点（实测）

`inputsFor` 只依赖 `dao` + `imageRuleResolver`（都可注入 ✓），但它的**返回类型 `ScenePromptInputs` 是
`ScenePromptCoordinator` 的嵌套类型** ✗ —— 直接搬到新类会报 `Unresolved reference 'ScenePromptInputs'`。

**解法（下一轮）**：搬迁时把新类里的类型引用写成 `ScenePromptCoordinator.ScenePromptInputs`，
或先把该 data class 提升为顶层类型（更干净，但会牵动调用点）。
### D6 补充：`decideShot` 的传递依赖（实测）

`decideShot`（行 94-128）**直接**引用：`transport` `cipher` `inputsFor` `situationOf` `character` `conversationProfile`；
**还通过 `recentBrief(...)` 间接受 `dao` 影响** —— 这类**传递依赖**必须连同被调方法一起搬，否则编译必失败。

**结论**：`decideShot` 不能单独搬，得连同 `recentBrief`（+ `dao` 注入）作为一个**簇**搬进 `SceneDecider`。
搬迁时的新类需要：`dao` `transport` `cipher` `imageRuleResolver`（给 inputsReader）+ 内建 `SceneInputsReader`/`SceneSituationReader` 实例。
### D6 补充：`EndpointResolver` 类化必须同时处理的 3 件事（实测）

把 `object` 直接改成 `class` 会踩三个编译错误：

1. **`NetworkMode` 枚举重复声明** —— 枚举在 object 之前的"头部"里，两处都带就会 `Redeclaration`。
   → 把 `NetworkMode` 抽到独立文件 `NetworkMode.kt`（同包，引用不用改）。
2. **`const val` 不能进 class** —— 原 object 里有一批 `private const val`（`MODE_ID` / `LAN_PREFIX` / `PROBE_*`）。
   → 搬进 `private companion object { ... }`。
3. **`internal` 类型暴露** —— 构造参数 `EndpointCache` 是 `internal`，而 Hilt 注入要求 public 构造。
   → 把 `EndpointCache` 改成 `public`（它本身无副作用，暴露无害）。

这三点解决后，`object EndpointResolver` 才能安全降级成薄委托。
### D6 补充：Hilt 拒绝 `@Inject` 构造函数的默认参数（实测）

把 `OpenAiCompatibleClient` 的构造函数加上 `endpointRegistry: EndpointRegistry` 后，
**Kotlin 编译通过**但 `hiltJavaCompileDebug` 失败 —— 因为 `EndpointRegistry` 的构造函数写了
`private val cache: EndpointCache = EndpointCache()`：**Dagger 不支持默认参数**。

**修法（两处）**：
1. `EndpointRegistry` 去掉默认值：`private val cache: EndpointCache`；
2. `EndpointCache` 加 `@Inject constructor()`（它有无参构造，但要显式标注才能被注入）。
### D6 补充：簇 A 的最后一处阻塞 —— 外部调用者

把 `decideShot` + `recentBrief` 搬进 `SceneDecider` 后，`SceneDecider` 本身能编译（修完
`LiteProductConfig` import、`WHITESPACE` 复制、`inputsReader` 内建之后），
但 **`ConversationTurnCoordinator.kt:853`** 仍在调 `scenePromptCoordinator.decideShot(...)`。

**解法**：在 `ScenePromptCoordinator` 上保留一个**委托方法**（签名不变，方法体一行转给 `decider`），
这样外部调用点不用改；等调用点都迁完后连委托一起删。
