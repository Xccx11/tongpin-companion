# Role Context, Memory, and Gateway API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** Align the Android app with the desktop OpenAI-compatible gateway while preserving independent role cognition, App-owned world state, role-scoped memory, and true fresh-window/clear-history boundaries.

**Architecture:** The App remains the source of truth for role identity, durable memory, world state, and the visible group transcript. Each character receives an independent `/v1/chat/completions` request; `messages` carries the current role-scoped conversation history and the gateway may compress it, while App-managed durable context is rebuilt into `system` for every request. Gateway sampling parameters are never overridden by the client.

**Tech Stack:** Kotlin, Jetpack Compose, Room, kotlinx.serialization, OkHttp, JUnit, Gradle Android plugin.

## Global Constraints

- Do not modify the already-completed V3 world orchestration semantics or install the APK.
- Preserve old model-profile columns for database compatibility, but do not use them to build conversation requests.
- New windows and cleared chats must not receive old short-term messages, summaries, or non-pinned conversation memories.
- Keep character persona, explicit pinned memories, and service configuration separate from chat history.
- Use test-first changes: every behavioral change needs a failing JVM test before production code.
- Run `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-configuration-cache --console=plain` at the end.

### Task 1: Lock down gateway request behavior

**Files:**
- Create: `docs/app/src/main/java/com/aster/companion/agent/ConversationRequestPolicy.kt`
- Create: `docs/app/src/test/java/com/aster/companion/agent/ConversationRequestPolicyTest.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/agent/OpenAiCompatibleClient.kt`

- [ ] Write a failing test proving conversation payload policy excludes `temperature`, `top_p`, `frequency_penalty`, `presence_penalty`, `thinking`, and `reasoning_effort`, while preserving `model`, `stream`, and `messages`.
- [ ] Run the focused test and verify it fails because the current policy still emits sampling fields.
- [ ] Implement the smallest policy/helper and route `chat`, `chatWithTools`, `chatWithToolsStream`, and `chatStream` through it.
- [ ] Run the focused test and verify it passes.

### Task 2: Implement fresh-window and clear-history context boundaries

**Files:**
- Modify: `docs/app/src/main/java/com/aster/companion/data/Entities.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/data/CompanionDatabase.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/di/AppModule.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/data/CompanionDao.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/data/CompanionRepository.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/data/DeviceTransferManager.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/agent/ConversationTurnCoordinator.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/agent/CompanionContinuityContext.kt`
- Create or modify: `docs/app/src/test/java/com/aster/companion/agent/ConversationContextBoundaryTest.kt`
- Modify: `docs/app/src/test/java/com/aster/companion/data/WorldDatabaseMigrationTest.kt`

- [ ] Write tests for a new thread excluding records older than its memory start and for clear-history advancing the boundary so old messages, summaries, semantic memories, world-state sync, and timeline facts are not returned.
- [ ] Run the focused tests and verify the expected failures.
- [ ] Add a `memorySince`/equivalent persisted thread boundary with a Room migration from v27, and add transfer defaults for old migration payloads.
- [ ] Add DAO queries/filter helpers that select only records visible after the thread boundary, without deleting character persona or pinned memory.
- [ ] Set the boundary at private/group thread creation and advance it inside `clearThreadConversationContext` together with `contextVersion`.
- [ ] Build role-scoped context from the boundary and ensure group members cannot receive another character's private memory.
- [ ] Run focused boundary and migration tests and verify they pass.

### Task 3: Make App context canonical and remove duplicate chat summaries

**Files:**
- Modify: `docs/app/src/main/java/com/aster/companion/agent/ConversationTurnCoordinator.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/agent/ConversationSummarizer.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/data/Entities.kt` and DAO summary queries as needed
- Create or modify: `docs/app/src/test/java/com/aster/companion/agent/RoleContextAssemblyTest.kt`

- [ ] Write tests proving system context contains role identity, durable role-visible memory, and current world context, while short-term history is passed only as messages and a fresh thread has no old summary.
- [ ] Run the tests and verify they fail against the current thread-wide generic-summary behavior.
- [ ] Implement role-scoped summary/context assembly; keep the visible group transcript shared but build an independent request context for each character.
- [ ] Stop injecting the App-generated 40-message summary as a second source of truth when the gateway receives the current complete role history; retain only data needed for recovery/diagnostics.
- [ ] Ensure pinned/stable memory survives a new window while thread-scoped conversation/event memory follows the reset boundary.
- [ ] Run focused role-context tests and verify they pass.

### Task 4: Update model settings UI and save API

**Files:**
- Modify: `docs/app/src/main/java/com/aster/ui/MainActivity.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/ui/CompanionHomeViewModel.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/data/CompanionRepository.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/data/Entities.kt` only if compatibility defaults need adjustment

- [ ] Write a focused presentation/persistence test for the conversation model editor exposing only Base URL, API Key, model name, fetch models, and test connection plus the server-managed-parameters notice.
- [ ] Run it and verify the current editor contract fails.
- [ ] Remove conversation sampling/thinking controls and simplify save callbacks; leave legacy database fields readable but ignored by request construction.
- [ ] Add UI copy explaining that the computer-side gateway controls sampling and thinking parameters.
- [ ] Run the focused UI/presentation test and verify it passes.

### Task 5: Align image provider protocol with the external gateway

**Files:**
- Modify: `docs/app/src/main/java/com/aster/companion/agent/OpenAiCompatibleClient.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/agent/ImageGenerationProviderPool.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/data/Entities.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/ui/CompanionHomeViewModel.kt`
- Modify: `docs/app/src/main/java/com/aster/ui/MainActivity.kt`
- Create or modify: `docs/app/src/test/java/com/aster/companion/agent/ImageProviderProtocolTest.kt`

- [ ] Write tests proving `wai-nsfw-illustrious` uses standard `/v1/images/generations` and decodes `data[].b64_json`, and that task/Comfy protocols are selected only by explicit provider protocol rather than this model name.
- [ ] Run the tests and verify the current model-name inference fails the new contract.
- [ ] Add an explicit protocol field/default for backward-compatible providers, map the documented gateway to standard OpenAI Images, and keep task/Comfy paths available for explicitly configured providers.
- [ ] Make the image settings UI state that the single gateway provider is primary and duplicate pool entries are not needed; retain queue/mutex behavior for truly distinct providers.
- [ ] Run focused image tests and verify they pass.

### Task 6: Documentation and final verification

**Files:**
- Modify: `D:/deepseek tp/tongpin-android-app-refactor-20260803-094514/APP功能与设置实现说明.md`
- Modify: `D:/deepseek tp/tongpin-android-app-refactor-20260803-094514/README-AI技术文档.md` where the old client-summary or sampling behavior is described
- Modify: `D:/deepseek tp/tongpin-android-app-refactor-20260803-094514/README-V3.0WorldDirector详细技术文档.md` only for memory/context boundary notes

- [ ] Update the implementation map with the final responsibilities: App-owned system memory/world state, gateway-compressed messages, per-character group calls, new-window reset, clear-history deletion, and documented image protocol.
- [ ] Run all unit tests and the Debug build from the project root.
- [ ] Verify the APK exists at `docs/app/build/outputs/apk/debug/app-debug.apk`; do not install it.
