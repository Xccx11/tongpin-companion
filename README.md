[![CI](https://github.com/Xccx11/tongpin-companion/actions/workflows/ci.yml/badge.svg)](https://github.com/Xccx11/tongpin-companion/actions/workflows/ci.yml)
[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Android-minSdk%2026-3DDC84?logo=android)](https://developer.android.com)

## 工程亮点（2026-11 迭代）

- **可维护性**：`MainActivity` 从 **4704 行拆到 1626 行（‑65%）**，22 个自包含 Composable 迁入 `ui/settings/`；`ScenePromptCoordinator` 拆出 7 个单一职责模块。
- **可观测性**：`RequestTrace` 环形缓冲记录每次模型调用的路径/耗时/成败，`report()` 直接给出**平均 / P95 / 最慢**，主界面一点即可查看。
- **离线优先**：失败的场景照片进入**幂等队列**（同 key 不重复），同步时自动重放，进程重启后仍在（`SharedPreferences` 持久化）。
- **并发正确性**：地址缓存从 `@Volatile var` 改为 `AtomicReference`，修掉**读-改-写丢更新**与**三字段撕裂**。
- **工程化**：GitHub Actions（单元测试 + APK 构建）全绿；`docs/ARCHITECTURE.md` 与 `docs/DECISIONS.md` 记录模块地图与关键决策。
- **测试**：**39 个行为测试**覆盖 请求追踪 / 离线队列 / 地址缓存 / 提示词解析 / 出图校验 / 外貌锚点。

# 同频 · Tongpin

一个本地优先的 AI 伴侣 App（Android，Kotlin + Jetpack Compose），把「聊天 / 生活状态 / 主动联系 / 场景照片」串成一条连续的体验。

## 它是什么

- **本地优先**：对话与推理走你自建的服务（OpenAI 兼容），数据留在你自己机器上。
- **有"生活"的角色**：角色有作息、心情、在世界里的位置，会主动发消息、发照片。
- **场景照片**：由对话内容生成画面提示词，再交给本地/自建的生图服务出图。
- **可插拔**：模型、提示词规则、图片后端都在运行时配置，不写死在代码里。

## 技术栈

- Android · Kotlin · Jetpack Compose · Hilt · Room
- 构建：Gradle 8.14.4 / Kotlin 2.0.21
- 服务端：本项目只包含 App；配套的本地 AI 网关（OpenAI 兼容，转发到对话模型与生图后端）需自行部署。

## 构建

```
./gradlew :app:assembleDebug
```

> App 模块位于 `docs/app`（`settings.gradle.kts` 中配置），这是历史目录结构，不影响构建。

## 说明

- 仓库只包含 App 客户端代码与通用提示词框架，**不包含任何模型权重**。
- 与生图/对话模型的具体能力由你接入的后端决定，请自行确认所选模型与内容合规。

## 许可证

[AGPL-3.0](LICENSE)
