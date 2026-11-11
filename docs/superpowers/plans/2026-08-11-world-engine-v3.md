# World Engine V3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a persistent V3 WorldEngine that turns planned world opportunities into cognition-approved activity and scene-ready state.

**Architecture:** Extend the existing Room data model and preserve V2 coordinators. A scheduler owns rolling planning and recovery, candidate events are evaluated by the cognition engine, and a scene projection separates frontend data from the execution model.

**Tech Stack:** Kotlin, Android, Room/SQLCipher, Hilt, Coroutines Flow, JUnit/Robolectric.

## Global Constraints

- Work in `docs/app`; preserve existing app package and Gradle versions.
- Do not add Flutter, Ktor routes, new UI, image features, or dependencies.
- Keep WorldDirector as a candidate planner and CharacterCognitionEngine as the only character behavior decision maker.
- Add a v26-to-v27 Room migration and register it in AppModule.
- New world behavior must be covered by focused JVM tests.
- Do not use destructive database migration or real credentials in tests.

---

### Task 1: Candidate Event Domain and Lifecycle

**Files:**
- Create: `docs/app/src/main/java/com/aster/companion/agent/world/WorldEventModels.kt`
- Create: `docs/app/src/main/java/com/aster/companion/agent/world/WorldEventLifecycle.kt`
- Test: `docs/app/src/test/java/com/aster/companion/agent/world/WorldEventLifecycleTest.kt`

**Interfaces:**
- Produces `WorldOpportunity`, `WorldEventStatus`, `WorldEventImpact`, and `WorldEventLifecycle.transition`.
- Later tasks consume stable participant IDs, locations, dependencies, and lifecycle state.

- [ ] Write tests proving a planned opportunity can enter evaluation, cannot finish before confirmation, and cannot leave a terminal state.
- [ ] Run the focused test and confirm it fails before implementation.
- [ ] Implement immutable opportunity data and a deterministic transition table.
- [ ] Run the focused test and confirm it passes.

### Task 2: Room v27 World Persistence

**Files:**
- Modify: `docs/app/src/main/java/com/aster/companion/data/Entities.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/data/CompanionDatabase.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/data/CompanionDao.kt`
- Modify: `docs/app/src/main/java/com/aster/di/AppModule.kt`
- Test: `docs/app/src/test/java/com/aster/companion/data/WorldDatabaseMigrationTest.kt`

**Interfaces:**
- Produces v27 tables and DAO methods for user state, locations, timeline events, and planner metadata.
- Migration is `CompanionDatabase.MIGRATION_26_27`.

- [ ] Write an in-memory migration test asserting v26 data remains and v27 tables exist.
- [ ] Run the test to observe the absent migration failure.
- [ ] Add the v27 entities, DAO queries, migration, and Hilt registration.
- [ ] Run the migration test and focused DAO tests.

### Task 3: World State Repository and Scene Projection

**Files:**
- Create: `docs/app/src/main/java/com/aster/companion/agent/world/WorldStateRepository.kt`
- Create: `docs/app/src/main/java/com/aster/companion/agent/world/WorldSceneProjection.kt`
- Test: `docs/app/src/test/java/com/aster/companion/agent/world/WorldSceneProjectionTest.kt`

**Interfaces:**
- Produces `suspend fun snapshot(now: Long): WorldSnapshot` and `fun observeScene(): Flow<WorldSceneProjection>`.
- Projection contains no hidden model, cognitive, or numeric-need information.

- [ ] Write tests for legacy location fallback, first-person projection, and hidden-state exclusion.
- [ ] Implement repository aggregation and seed default virtual locations idempotently.
- [ ] Verify focused tests pass.

### Task 4: WorldDirector Candidate Planning

**Files:**
- Modify: `docs/app/src/main/java/com/aster/companion/agent/world/WorldDirector.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/agent/world/TimelineManager.kt`
- Test: `docs/app/src/test/java/com/aster/companion/agent/world/WorldDirectorParseTest.kt`
- Test: `docs/app/src/test/java/com/aster/companion/agent/world/TimelineCodecTest.kt`

**Interfaces:**
- `WorldDirector.generateOpportunities(snapshot, window): List<WorldOpportunity>`.
- `TimelineManager` persists candidates but does not call `upsertLivingState`.

- [ ] Replace hand-written string tests with tests that execute public parsing/codec behavior.
- [ ] Test rejection of names-as-IDs, unknown locations, user-command events, and malformed dependency JSON.
- [ ] Implement structured JSON parsing through kotlinx.serialization, planning profile fallback, and DAO persistence.
- [ ] Verify candidate persistence does not mutate character activity.

### Task 5: Cognition-Gated Event Decision

**Files:**
- Create: `docs/app/src/main/java/com/aster/companion/agent/world/WorldEventDecisionCoordinator.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/agent/CharacterCognitionEngine.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/agent/ActivityManager.kt`
- Test: `docs/app/src/test/java/com/aster/companion/agent/world/WorldEventDecisionCoordinatorTest.kt`

**Interfaces:**
- `suspend fun evaluate(eventId: String, now: Long): WorldDecisionOutcome`.
- Candidates expose `accept`, `reject`, `delay`, and `substitute` options through the existing cognition engine.

- [ ] Write tests for one-person accept/reject/delay, required multi-party rejection, and no direct state mutation before acceptance.
- [ ] Add a small cognition decision adapter rather than duplicating prompt logic.
- [ ] Create true activities only after all required decisions succeed.
- [ ] Verify focused tests pass.

### Task 6: User Interactions and Impact Propagation

**Files:**
- Create: `docs/app/src/main/java/com/aster/companion/agent/world/WorldImpactAnalyzer.kt`
- Create: `docs/app/src/main/java/com/aster/companion/agent/world/WorldInteractionService.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/agent/world/InterruptManager.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/ui/CompanionHomeViewModel.kt`
- Test: `docs/app/src/test/java/com/aster/companion/agent/world/WorldImpactAnalyzerTest.kt`

**Interfaces:**
- `suspend fun record(interaction: WorldInteraction, now: Long): WorldImpactReport`.
- Impact classification is `KEEP`, `RESCHEDULE`, `REEVALUATE`, or `CANCEL`.

- [ ] Test direct participant/location conflicts and a dependency chain such as meal preparation invalidating dinner.
- [ ] Implement deterministic impact traversal with cycle protection.
- [ ] Route chat messages into user interaction recording without changing existing conversation delivery.
- [ ] Verify focused tests pass.

### Task 7: Scheduler, Offline Recovery, and Memory Recording

**Files:**
- Create: `docs/app/src/main/java/com/aster/companion/agent/world/WorldScheduler.kt`
- Create: `docs/app/src/main/java/com/aster/companion/agent/world/WorldMemoryRecorder.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/proactive/CompanionSystemService.kt`
- Modify: `docs/app/src/main/java/com/aster/companion/ui/CompanionHomeViewModel.kt`
- Test: `docs/app/src/test/java/com/aster/companion/agent/world/WorldSchedulerTest.kt`
- Test: `docs/app/src/test/java/com/aster/companion/agent/world/WorldMemoryRecorderTest.kt`

**Interfaces:**
- `suspend fun tick(now: Long)` and `suspend fun recoverOnAppStart(now: Long)`.
- Scheduler maintains 30-minute minimum / 60-minute target coverage.

- [ ] Test horizon thresholds, retry backoff, process-restart recovery, and 24-hour catch-up boundary.
- [ ] Test that a settled event creates objective history and only significant participant-scoped memory.
- [ ] Implement scheduler and recorder, then inject scheduler into the service and startup path.
- [ ] Verify focused tests pass and static search shows `WorldScheduler.tick` is called from the service.

### Task 8: Regression and Documentation

**Files:**
- Modify: `README-V3.0WorldDirector详细技术文档.md`
- Modify: `README-AI技术文档.md`
- Test: existing V3 and affected world-engine test suites

**Interfaces:**
- Documentation describes candidate planning, cognition gating, offline recovery, and the local scene projection accurately.

- [ ] Re-read implementation against the design acceptance criteria.
- [ ] Run focused V3, cognition, social, activity, and migration tests.
- [ ] Update technical documents to remove claims about direct event execution and nonexistent tick wiring.
- [ ] Run the project-prescribed unit-test and debug-assemble command only after focused tests pass and user authorizes a full build.
