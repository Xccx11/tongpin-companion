# 家庭群聊天 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade Tongpin group conversations into privacy-bounded family groups with directed multi-character turns, private-memory-safe sharing, and globally serialized group image generation.

**Architecture:** Keep the existing encrypted Room database, `CompanionRepository`, `ConversationTurnCoordinator`, and Compose screens as the system of record. Add group-only persisted facts and member state, then drive each user group turn through a structured `GroupTurnDirector`; private chat reads only a source-labelled summary/fact view, never another character's source messages. Reuse `PendingActionEntity` as the durable global image queue, but centralize arbitration so private chats, groups, and Moments have one FIFO executor.

**Tech Stack:** Kotlin, Android Compose Material 3, Room + SQLCipher, Hilt, Kotlin coroutines/Flow, OpenAI-compatible conversation client, existing private image store.

## Global Constraints

- Product and user-visible copy remain Tongpin only.
- Never uninstall, clear application data, or use destructive database migration; database version upgrades must preserve existing data.
- Family groups may create a natural in-group proactive turn when the system mode is enabled. It uses the same pause, silence, cooldown, de-duplication, and grounding limits as private relationships, but sends no external notification.
- Never pass raw private-chat messages, private photos, device sensing payloads, or role secrets to a group model prompt.
- A group-to-private context item must carry explicit group source semantics and is visible only to a character who remains a member of that group.
- Clearing a group deletes its messages, facts, group-derived semantic links, queued group tasks, and group shared world state. Clearing a private chat does not delete group facts.
- All private, group, and Moments images share one global FIFO lane. A group image always requires the user to confirm.
- Do not install builds until the user explicitly asks. Any later install must use `adb install -r` only.

---

## File Structure

- `app/src/main/java/com/aster/companion/data/CompanionDatabase.kt`: version 14 schema, DAO queries, destructive-clear transactions, and migration 13 to 14.
- `app/src/main/java/com/aster/companion/domain/FamilyGroupModels.kt`: group fact/state/atmosphere values and pure privacy rules.
- `app/src/main/java/com/aster/companion/domain/GroupContextPolicy.kt`: approved shareable fact validation and private-context visibility rules.
- `app/src/main/java/com/aster/companion/agent/GroupTurnDirector.kt`: structured speaker plan construction and validation.
- `app/src/main/java/com/aster/companion/agent/GroupTurnPlanParser.kt`: strict JSON parser for main speaker plus no more than two follow-ups.
- `app/src/main/java/com/aster/companion/agent/FamilyGroupContextAssembler.kt`: creates group-only and per-member private context blocks.
- `app/src/main/java/com/aster/companion/agent/ConversationTurnCoordinator.kt`: runs group turns sequentially, updates member state, stores facts, and never injects private raw text into a group.
- `app/src/main/java/com/aster/companion/agent/GlobalImageGenerationQueue.kt`: picks the oldest eligible image action across every source and owns the one-task image lane.
- `app/src/main/java/com/aster/companion/system/CompanionActionCoordinator.kt`: delegates image approval and execution to the global queue.
- `app/src/main/java/com/aster/companion/data/CompanionRepository.kt`: group creation/default settings, group member updates, and clear/delete orchestration.
- `app/src/main/java/com/aster/companion/ui/CompanionHomeViewModel.kt`: exposes group settings, facts, member state, queue state, and management actions.
- `app/src/main/java/com/aster/ui/MainActivity.kt`: group management UI, atmosphere selector, shareable-summary manager, `@` chooser, and originating-chat image states.
- `app/src/test/java/com/aster/companion/...`: focused unit and Room migration tests listed per task.

## Data Contracts

```kotlin
enum class GroupAtmosphere { QUIET, DAILY, LIVELY }

data class GroupTurnPlan(
    val mainSpeakerId: String?,
    val followUpSpeakerIds: List<String>
)

data class GroupContextBlock(
    val groupThreadId: String,
    val facts: List<String>,
    val recentGroupMessages: List<String>
)
```

`FamilyGroupFactEntity` is a shareable summary, never a message copy:

```kotlin
@Entity(indices = [Index("groupThreadId"), Index("ownerCharacterId")])
data class FamilyGroupFactEntity(
    @PrimaryKey val id: String,
    val groupThreadId: String,
    val ownerCharacterId: String?,
    val summary: String,
    val sourceThreadId: String,
    val sourceKind: String,
    val createdAt: Long,
    val expiresAt: Long?,
    val sensitivity: String,
    val allowedInGroup: Boolean
)

@Entity(primaryKeys = ["groupThreadId", "characterId"])
data class GroupMemberStateEntity(
    val groupThreadId: String,
    val characterId: String,
    val attention: Int,
    val affection: Int,
    val jealousy: Int,
    val unspokenIntent: String,
    val lastSpokeAt: Long?
)

@Entity(tableName = "companion_group_settings")
data class GroupSettingsEntity(
    @PrimaryKey val groupThreadId: String,
    val atmosphere: GroupAtmosphere,
    val updatedAt: Long
)

@Entity(primaryKeys = ["factId", "characterId", "semanticMemoryId"])
data class GroupDerivedMemoryLinkEntity(
    val factId: String,
    val characterId: String,
    val semanticMemoryId: String
)
```

### Task 1: Persist family-group boundaries safely

**Files:**
- Modify: `app/src/main/java/com/aster/companion/data/CompanionDatabase.kt`
- Modify: `app/src/main/java/com/aster/di/AppModule.kt`
- Create: `app/src/main/java/com/aster/companion/domain/FamilyGroupModels.kt`
- Test: `app/src/test/java/com/aster/companion/data/FamilyGroupMigrationTest.kt`
- Test: `app/src/test/java/com/aster/companion/domain/FamilyGroupModelsTest.kt`

**Interfaces:**
- Produces `FamilyGroupFactEntity`, `GroupMemberStateEntity`, `GroupSettingsEntity`, `GroupDerivedMemoryLinkEntity`, `GroupAtmosphere`.
- Produces DAO functions `factsForGroup`, `memberStatesForGroup`, `groupSettings`, `deleteGroupFacts`, and `deleteGroupDerivedSemanticMemories`.

- [ ] **Step 1: Write failing migration and model tests**

```kotlin
@Test fun migration13To14_preservesExistingThreadAndAddsFamilyGroupTables() { /* create v13 SQLCipher schema, migrate, assert old thread and new tables */ }
@Test fun atmosphere_limitsFollowUps() = assertEquals(2, GroupAtmosphere.LIVELY.maxFollowUps)
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*FamilyGroupMigrationTest' --tests '*FamilyGroupModelsTest'`

Expected: failure because the v14 entities and migration do not exist.

- [ ] **Step 3: Add v14 entities and migration**

Add the four entities above, register their DAOs, define `MIGRATION_13_14`, increase Room `version` from `13` to `14`, and create only additive tables/indexes. Add `.addMigrations(CompanionDatabase.MIGRATION_13_14)` in `AppModule`.

- [ ] **Step 4: Add transactional deletion primitives**

Implement a DAO transaction equivalent to:

```kotlin
@Transaction
suspend fun clearFamilyGroupContext(groupThreadId: String) {
    val factIds = factIdsForGroup(groupThreadId)
    deleteSemanticMemoriesLinkedToFacts(factIds)
    deleteGroupDerivedMemoryLinks(factIds)
    deleteGroupFacts(groupThreadId)
    deleteGroupMemberStates(groupThreadId)
    deleteGroupSettings(groupThreadId)
}
```

Use an empty-list guard so Room never emits `IN ()` SQL.

- [ ] **Step 5: Re-run focused tests**

Run: `./gradlew testDebugUnitTest --tests '*FamilyGroupMigrationTest' --tests '*FamilyGroupModelsTest'`

Expected: PASS.

### Task 2: Define shareable facts and private-context isolation

**Files:**
- Modify: `app/src/main/java/com/aster/companion/domain/GroupContextPolicy.kt`
- Create: `app/src/main/java/com/aster/companion/agent/FamilyGroupContextAssembler.kt`
- Modify: `app/src/main/java/com/aster/companion/data/CompanionDatabase.kt`
- Test: `app/src/test/java/com/aster/companion/domain/GroupContextPolicyTest.kt`
- Test: `app/src/test/java/com/aster/companion/agent/FamilyGroupContextAssemblerTest.kt`

**Interfaces:**
- Consumes `FamilyGroupFactEntity`, `ThreadParticipantEntity`, `MessageEntity`.
- Produces `FamilyGroupContextAssembler.forGroup(threadId)` and `.forPrivateCharacter(characterId)`.

- [ ] **Step 1: Write failing isolation tests**

```kotlin
@Test fun groupPrompt_omitsRawPrivateMessageAndPrivateImageUri() { /* assert neither appears */ }
@Test fun privatePrompt_includesOnlyMemberGroupsWithSourceLabel() { /* assert [家庭群：...] block */ }
@Test fun roleOutsideGroup_cannotReadAnyFactOrMessageFromIt() { /* assert empty */ }
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*GroupContextPolicyTest' --tests '*FamilyGroupContextAssemblerTest'`

Expected: failure because an assembler and visibility rules do not exist.

- [ ] **Step 3: Implement closed-by-default fact validation**

`GroupContextPolicy.canShare` must return `false` for source text, image URI/content, device event payloads, blocked memories, role secrets, and facts with `allowedInGroup=false`. It may return `true` only for a short structured summary classified as schedule, stated plan, public promise, or non-sensitive relationship progress.

- [ ] **Step 4: Assemble source-labelled contexts**

Group context contains member cards, group messages, currently valid `allowedInGroup` facts, settings, and member state. Private context contains that character's private memories plus only member-group blocks in this format:

```text
[家庭群「{title}」中发生]
{safe group message or shareable fact}
这不是你们的私聊经历；只能作为群内共同发生的事提及。
```

- [ ] **Step 5: Re-run focused tests**

Run: `./gradlew testDebugUnitTest --tests '*GroupContextPolicyTest' --tests '*FamilyGroupContextAssemblerTest'`

Expected: PASS.

### Task 3: Direct one natural group turn at a time

**Files:**
- Create: `app/src/main/java/com/aster/companion/agent/GroupTurnDirector.kt`
- Create: `app/src/main/java/com/aster/companion/agent/GroupTurnPlanParser.kt`
- Modify: `app/src/main/java/com/aster/companion/agent/ConversationTurnCoordinator.kt`
- Modify: `app/src/main/java/com/aster/companion/agent/GroupReplySelectionParser.kt`
- Test: `app/src/test/java/com/aster/companion/agent/GroupTurnDirectorTest.kt`
- Test: `app/src/test/java/com/aster/companion/agent/GroupTurnPlanParserTest.kt`

**Interfaces:**
- `GroupTurnDirector.plan(input: GroupDirectorInput): GroupTurnPlan`
- `GroupTurnPlanParser.parse(raw, validMembers, maxFollowUps): GroupTurnPlan`
- `ConversationTurnCoordinator.send` dispatches to `sendGroupTurn` for `ConversationType.GROUP`.

- [ ] **Step 1: Write failing turn-selection tests**

```kotlin
@Test fun explicitMention_isAlwaysMainSpeaker() { /* @岚 makes 岚 main */ }
@Test fun dailyMode_allowsAtMostOneFollowUp() { /* invalid extras removed */ }
@Test fun quietMode_neverAddsFollowUp() { /* followUps empty */ }
@Test fun unknownOrDuplicateIds_areRemoved() { /* only current members survive */ }
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*GroupTurnDirectorTest' --tests '*GroupTurnPlanParserTest'`

Expected: failure because no director/plan parser exists.

- [ ] **Step 3: Implement structured direction and validation**

Ask the conversation model for JSON only:

```json
{"main_speaker_id":"member-id-or-null","follow_up_speaker_ids":["member-id"]}
```

The director injects only group-safe context. `@name` wins main-speaker priority. Clamp follow-ups to 0/1/2 for quiet/daily/lively respectively, never repeat the main speaker, and return an empty plan on parsing/model failure rather than making every member answer.

- [ ] **Step 4: Execute sequential responses**

Persist the user group message once. Generate the main speaker reply first; append it before generating each follow-up, so a follow-up can respond naturally to what actually appeared. Before each write, re-check `contextVersion` and group membership; on failure, skip only that speaker. Update that member's `lastSpokeAt` and state after a persisted response.

- [ ] **Step 5: Re-run focused tests**

Run: `./gradlew testDebugUnitTest --tests '*GroupTurnDirectorTest' --tests '*GroupTurnPlanParserTest' --tests '*GroupReplySelectionParserTest'`

Expected: PASS.

### Task 4: Persist, return, and erase group-derived memory correctly

**Files:**
- Modify: `app/src/main/java/com/aster/companion/agent/ConversationTurnCoordinator.kt`
- Modify: `app/src/main/java/com/aster/companion/data/CompanionRepository.kt`
- Modify: `app/src/main/java/com/aster/companion/data/CompanionDatabase.kt`
- Test: `app/src/test/java/com/aster/companion/data/FamilyGroupClearTest.kt`
- Test: `app/src/test/java/com/aster/companion/agent/GroupPrivateRecallTest.kt`

**Interfaces:**
- Produces `CompanionRepository.clearGroupHistory(threadId)`.
- Produces fact-to-semantic links through `GroupDerivedMemoryLinkEntity`.

- [ ] **Step 1: Write failing lifecycle tests**

```kotlin
@Test fun clearingGroup_removesMessagesFactsLinkedMemoriesAndQueuedTasks() { /* assert all absent */ }
@Test fun clearingPrivateChat_keepsItsMemberGroupFact() { /* group fact remains */ }
@Test fun removingMember_revokesTheirPrivateGroupRecallOnly() { /* other members retain group history */ }
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*FamilyGroupClearTest' --tests '*GroupPrivateRecallTest'`

Expected: failure because group-derived facts and links are absent.

- [ ] **Step 3: Store only validated family facts**

At group-turn reflection time, accept a model-proposed fact only after `GroupContextPolicy.canShare` succeeds. Save a compact summary, expiry/sensitivity metadata, and its source thread id; do not save original text or image paths. When making it retrievable in a member's private context, create one semantic-memory record and one link row per eligible member.

- [ ] **Step 4: Make clear/delete semantics exact**

`clearThreadHistory` branches on thread type. For groups it increments `contextVersion`, cancels group tasks, removes group messages/timeline/shared world/facts/linked semantic memories/member state, but retains the group title and members. Group deletion calls the same clear transaction before deleting thread membership/settings. Member removal deletes that member's state and derived-memory links only.

- [ ] **Step 5: Re-run focused tests**

Run: `./gradlew testDebugUnitTest --tests '*FamilyGroupClearTest' --tests '*GroupPrivateRecallTest' --tests '*ConversationResetGuardTest'`

Expected: PASS.

### Task 5: Make every image source use one durable FIFO lane

**Files:**
- Create: `app/src/main/java/com/aster/companion/agent/GlobalImageGenerationQueue.kt`
- Modify: `app/src/main/java/com/aster/companion/agent/ImageGenerationCoordinator.kt`
- Modify: `app/src/main/java/com/aster/companion/system/CompanionActionCoordinator.kt`
- Modify: `app/src/main/java/com/aster/companion/data/CompanionDatabase.kt`
- Test: `app/src/test/java/com/aster/companion/agent/GlobalImageGenerationQueueTest.kt`
- Test: `app/src/test/java/com/aster/companion/domain/GroupImageConsentPolicyTest.kt`

**Interfaces:**
- `GlobalImageGenerationQueue.enqueue(actionId)` returns one of `QUEUED`, `RUNNING`, `REJECTED`.
- `GlobalImageGenerationQueue.resume()` always selects the minimum `createdAt` eligible action globally.

- [ ] **Step 1: Write failing queue tests**

```kotlin
@Test fun oldestPendingImageWinsAcrossPrivateGroupAndMomentSources() { /* group cannot jump queue */ }
@Test fun onlyOneTaskCanBeRunningAfterProcessRecovery() { /* single RUNNING retained */ }
@Test fun groupImageNeedsExplicitConfirmation() { /* no generation before approve */ }
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*GlobalImageGenerationQueueTest' --tests '*GroupImageConsentPolicyTest'`

Expected: failure because queue arbitration is per caller.

- [ ] **Step 3: Add DAO queue queries and central arbitration**

Query all image capabilities from `companion_pending_actions` ordered by `createdAt, id`, ignoring rejected/completed tasks. Transactionally promote exactly one `QUEUED` action to `RUNNING`; on process recovery demote stale `RUNNING` actions back to `QUEUED` unless a verified remote gateway task id exists.

- [ ] **Step 4: Wire private, group, and Moments producers**

All image proposals continue to create `PendingActionEntity` records; no caller invokes image generation directly. Group-generated or user-requested group image actions use `companion.propose_image` and require confirmation. The originating chat observes the same action id and renders `待确认`, `排队中`, `正在生成`, `已发送`, or the true failure summary in place.

- [ ] **Step 5: Add multi-character prompt assembly**

For a group image, require an explicit non-empty `participant_character_ids` list. Build each selected role's appearance anchor/reference image plus the current group scene; reject a request with unknown or non-member ids. Do not include personality prompt text in visual anchors.

- [ ] **Step 6: Re-run focused tests**

Run: `./gradlew testDebugUnitTest --tests '*GlobalImageGenerationQueueTest' --tests '*GroupImageConsentPolicyTest' --tests '*ImageGenerationTaskStateTest'`

Expected: PASS.

### Task 6: Create and manage family groups through the repository and view model

**Files:**
- Modify: `app/src/main/java/com/aster/companion/data/CompanionRepository.kt`
- Modify: `app/src/main/java/com/aster/companion/ui/CompanionHomeViewModel.kt`
- Test: `app/src/test/java/com/aster/companion/data/FamilyGroupRepositoryTest.kt`
- Test: `app/src/test/java/com/aster/companion/ui/FamilyGroupViewModelTest.kt`

**Interfaces:**
- `createGroupThread(characterIds, title, atmosphere)` creates settings and state rows atomically.
- `updateGroupThread(threadId, title, characterIds, atmosphere)` preserves valid members and removes revoked recall.
- `updateFamilyGroupFact(id, summary, allowedInGroup)` and `deleteFamilyGroupFact(id)`.

- [ ] **Step 1: Write failing repository tests**

```kotlin
@Test fun createGroup_initializesDailySettingsAndOneStatePerMember() { /* assert rows */ }
@Test fun updateGroup_removesStateAndRecallForRemovedMember() { /* assert revocation */ }
@Test fun groupHistoryClear_keepsTitleAndParticipants() { /* assertions */ }
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*FamilyGroupRepositoryTest' --tests '*FamilyGroupViewModelTest'`

Expected: failure because settings/state flows are not exposed.

- [ ] **Step 3: Implement repository transactions**

Create group thread, participants, default `DAILY` setting, and zeroed member state in a single Room transaction. On update, retain state for surviving members; delete revoked member's group-derived links/state before writing the new participant list. Snapshot before clear/delete using the existing snapshot manager.

- [ ] **Step 4: Expose immutable UI state and mutations**

Add `familyGroupFacts`, `familyGroupSettings`, and `groupMemberStates` to the existing home state. ViewModel commands must only call repository methods and must refresh the selected group's state without closing the chat.

- [ ] **Step 5: Re-run focused tests**

Run: `./gradlew testDebugUnitTest --tests '*FamilyGroupRepositoryTest' --tests '*FamilyGroupViewModelTest'`

Expected: PASS.

### Task 7: Deliver group management and chat affordances on the existing phone-first UI

**Files:**
- Modify: `app/src/main/java/com/aster/ui/MainActivity.kt`
- Modify: `app/src/main/java/com/aster/companion/ui/ThreadPreviewBuilder.kt`
- Test: `app/src/test/java/com/aster/companion/ui/FamilyGroupPresentationTest.kt`

**Interfaces:**
- Group management receives `GroupSettingsEntity`, facts, state, and queued image actions.
- Chat composer receives `onMentionSelected(characterId: String)`.

- [ ] **Step 1: Write failing presentation tests**

```kotlin
@Test fun groupPreview_showsLastSpeakerAndUnreadCount() { /* given participant message */ }
@Test fun groupImageTask_mapsQueuedAndRunningToOriginatingThread() { /* given action */ }
@Test fun mentionInsertion_preservesCursorAndFormatsAtName() { /* assert text */ }
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*FamilyGroupPresentationTest' --tests '*ThreadPreviewBuilderTest'`

Expected: failure because group-specific presentation models do not exist.

- [ ] **Step 3: Implement group management sections**

In the existing group management dialog/screen add: group title/avatar, member add/remove/reorder, a three-choice atmosphere selector, shareable-summary list with edit/delete/disable controls, and group image task list. Keep the current cream/pink Compose system, vertical scrolling, bottom IME insets, and no horizontal overflow.

- [ ] **Step 4: Add lightweight conversation affordances**

Show member avatars/names in the group header; insert `@角色名 ` through a member picker. In the conversation directory show last speaker, preview, and unread count. Do not add a group proactive toggle or a background group-notification UI.

- [ ] **Step 5: Render originating group image status inline**

Reuse the existing photo confirmation card presentation for group proposals; card copy names the proposing role and keeps status visible after confirmation through success/failure. The image task viewer filters by source but includes group tasks.

- [ ] **Step 6: Re-run focused tests**

Run: `./gradlew testDebugUnitTest --tests '*FamilyGroupPresentationTest' --tests '*ThreadPreviewBuilderTest' --tests '*PhotoGenerationChatPresentationTest'`

Expected: PASS.

### Task 8: Keep Moments social, but prevent cross-boundary leaks and queue starvation

**Files:**
- Modify: `app/src/main/java/com/aster/companion/agent/MomentsAgentCoordinator.kt`
- Modify: `app/src/main/java/com/aster/companion/agent/MomentConversationContext.kt`
- Modify: `app/src/main/java/com/aster/companion/agent/GlobalImageGenerationQueue.kt`
- Test: `app/src/test/java/com/aster/companion/agent/FamilyGroupMomentsPolicyTest.kt`

**Interfaces:**
- Moments participant context receives only public Moments content and the actor's own safe facts.

- [ ] **Step 1: Write failing integration policy tests**

```kotlin
@Test fun aMomentReaction_neverAddsPrivateOrGroupRawTextToAnotherRolePrompt() { /* assert boundary */ }
@Test fun queuedMomentImage_cannotOvertakeOlderGroupImage() { /* FIFO */ }
@Test fun aGroupFact_canBeMentionedInPrivateOnlyWithItsGroupSourceLabel() { /* label */ }
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*FamilyGroupMomentsPolicyTest'`

Expected: failure because group boundaries are not part of Moments assembly.

- [ ] **Step 3: Route generated Moment media through the global queue**

Replace any direct image coordinator calls with the same pending-action creation path and keep Moments at normal FIFO priority. Do not fabricate a relation between a Moment and an unrelated group/private chat.

- [ ] **Step 4: Enforce public-only Moments context**

Moments may react/comment based on their visible post/comment thread. Do not feed group messages, group state, other roles' private facts, or private image references into a Moments prompt unless the user explicitly posts that material.

- [ ] **Step 5: Re-run focused tests**

Run: `./gradlew testDebugUnitTest --tests '*FamilyGroupMomentsPolicyTest' --tests '*MomentConversationContextTest' --tests '*MomentParticipantPolicyTest'`

Expected: PASS.

### Task 9: Verify migration, behavior, and build without touching device data

**Files:**
- Modify: `docs/superpowers/specs/2026-07-31-family-group-chat-design.md` only if implementation exposes a spec mismatch.
- Create: `docs/superpowers/verification/2026-07-31-family-group-chat.md`

- [ ] **Step 1: Run the complete unit suite**

Run: `./gradlew testDebugUnitTest`

Expected: PASS with all existing and family-group tests passing.

- [ ] **Step 2: Build the debug APK**

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: Write a manual verification record**

Record checks for: old v13 data migration; group clear vs private clear; role removal; @ priority; quiet/daily/lively limits; group response sequencing; grounded in-app-only proactive group turns with no system notification; group confirmation card; FIFO across private/group/Moments; offline resume; and no private raw text in a group request capture.

- [ ] **Step 4: Perform device verification only after explicit install permission**

Use `adb -s 10AEB934JA0013U install -r app/build/outputs/apk/debug/app-debug.apk`; never uninstall or clear data. Verify the current characters/messages before and after opening the updated App.

## Plan Self-Review

- Spec coverage: Tasks 1-4 cover family facts, state, bidirectional recall, strict deletion, and private isolation. Task 3 covers natural directed replies and three atmosphere levels. Tasks 5 and 8 cover one image queue, group confirmation, recovery, and Moments. Tasks 6-7 cover management, list preview, mentions, and inline state. Task 9 covers migration, build, and non-destructive device verification.
- Ambiguity resolved: a group is a closed membership boundary; only current members may retrieve source-labelled group content. Removed members lose derived recall. Group raw messages are visible to current members only through their group history and are never promoted to a non-member's context.
- Contradictions resolved: "group -> private" allows a current member to receive labelled group snippets/facts, while "private -> group" allows only validated summaries, never raw private text. Image ordering is strictly global FIFO; no source has priority over an older request.
- Placeholder scan: no deferred design decisions remain; all required state, interfaces, migration, deletion, queue, UI, and verification behavior has a task.
