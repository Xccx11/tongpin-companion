# World Engine V3 Design

## Goal

Upgrade Tongpin from an AI companion interaction system to a continuously
evolving virtual-life world without replacing the existing conversation,
character cognition, social, activity, or memory systems.

## Scope

This phase implements the Android-local WorldEngine domain and its data
contracts. It does not add Flutter, an HTTP server, a new WorldScene UI,
complex 3D, or new image-generation behavior.

## Existing System Constraints

- The app module is `docs/app`; the source package is `com.tongpin.companion`.
- `CharacterCognitionEngine` remains the only model-backed decision maker for
  character behavior.
- `WorldBatchSimulationCoordinator` remains responsible for its current
  post-conversation state synchronization. The V3 scheduler must not let that
  coordinator overwrite an active confirmed world activity.
- Existing Room data is encrypted with SQLCipher and must migrate forward from
  database version 26. No destructive migration is permitted.
- The planning profile is preferred for world planning and falls back to the
  configured conversation profile.
- User-facing text must not expose prompts, API keys, model workflow details,
  or hidden cognitive state.

## Domain Boundary

WorldDirector produces structured *opportunities*, never commands. An
opportunity can say that dinner preparation is plausible at a time and place;
it cannot force a named character to cook. At maturity, the runtime separates
four states:

1. Candidate opportunity: a possible event proposed by WorldDirector.
2. Decision: each involved character accepts, rejects, delays, or substitutes
   an action through CharacterCognitionEngine.
3. Confirmed activity: ActivityManager creates and maintains only activity
   instances whose required decisions have succeeded.
4. History: WorldEvent records an immutable outcome, while the existing memory
   system records per-character subjective memory only when warranted.

## Components

### WorldStateRepository

Owns the aggregate state used by planning and scene projection: world time,
time phase, weather, environment, user presence and location, registered
virtual locations, character living states, active activities, and the last
world advancement metadata. It hides direct DAO composition from schedulers
and presentation consumers.

### WorldDirector

Builds an input snapshot from WorldStateRepository and returns candidate
opportunities for the next rolling window. It uses the planning model when
available. Output uses stable character IDs and registered `locationId` values.
Invalid, unregistered, or user-commanding events are discarded.

### TimelineManager

Persists and queries a rolling future timeline. It owns candidate event state
and never changes a character living state directly. Events transition through
`planned`, `evaluating`, `confirmed`, `finished`, `cancelled`, and `superseded`.

### WorldScheduler

Runs from the existing foreground service every 30 seconds and at cold start.
It restores persisted work after process death, evaluates due opportunities,
and asks WorldDirector to maintain a 60-minute horizon whenever fewer than 30
minutes of viable opportunity coverage remains. Planner errors use persisted
retry/backoff metadata and do not block the sensing loop.

### WorldEventDecisionCoordinator

Converts a due opportunity to real behavior. A single-person opportunity is
offered to that character through CharacterCognitionEngine. A multi-person
opportunity first asks the initiator whether to invite, then asks each required
participant to accept, reject, or delay. A user participant is never decided
by a model; the event waits for explicit user acceptance or resolves according
to its timeout policy. Confirmed character activities are created through the
existing ActivityManager path.

### WorldImpactAnalyzer

Processes world interactions from user chat and future scene actions. It
calculates direct time, location, participant, and prerequisite conflicts, then
propagates impact through event dependencies. It classifies every affected
candidate as `keep`, `reschedule`, `reevaluate`, or `cancel`; only affected
windows are replanned.

### WorldMemoryRecorder

Writes a factual WorldEvent for every settled candidate. It creates ordinary
MemoryEntity and optional SemanticMemoryEntity records only for event outcomes
that have participants, significance, or a relationship consequence. This
prevents trivial routine events from generating shared omniscient memories.

## Data Model

Database version 27 adds data without replacing v26 tables.

- `companion_world_state`: extend the singleton row with `timePhase`,
  `environment`, `variablesJson`, `lastAdvancedAt`, `lastPlanAttemptAt`,
  `planRetryAfter`, and `worldVersion`.
- `companion_world_user_state`: singleton user participant state with
  `locationId`, `visibility`, `lastAction`, `lastActionAt`, and `updatedAt`.
- `companion_world_locations`: registered virtual location IDs, display names,
  parent IDs, and metadata. Initial seed locations are home, living room,
  kitchen, bedroom, bathroom, and outside.
- `companion_character_living_states`: add nullable `locationId`; legacy free
  text remains the user-facing activity description.
- `companion_world_timeline_events`: normalized candidate opportunity rows
  containing schedule, stable IDs, dependency and condition JSON, decision
  state, flexibility, and cancellation strategy.
- `companion_world_events`: remains factual history, with a reference to the
  originating candidate and a structured result payload.

`companion_future_timelines.eventsJson` remains a compatibility snapshot for
the V3.0 data already present, but new behavior reads normalized event rows.

## User Interaction

The user is a world participant, not a CharacterEntity and not an input to
CharacterCognitionEngine. `WorldInteractionService` records user chat, future
scene actions, movement, waits, rest, and invitation responses. The service
updates user state, invokes WorldImpactAnalyzer, and wakes WorldScheduler to
replan only the affected window.

## Offline Behavior

While System Mode is active, CompanionSystemService drives the scheduler in
its 30-second loop. If the service or process is absent, state remains
persisted. On startup, the scheduler catches up events chronologically for at
most 24 hours. Older routine intervals are summarized into factual history and
significant character memories. Android force-stop cannot be bypassed; opening
the app resumes and catches up the world.

## Frontend Contract

WorldEngine exposes an in-process `Flow<WorldSceneProjection>` rather than an
HTTP endpoint in this phase. It supplies first-person-safe data: current
location, time, environment, visible characters, public activity descriptions,
available scene actions, and notification counts. It intentionally excludes
hidden prompts, numeric needs, cognitive chains, and unpublished candidates.
Compose can consume the projection directly; a future Flutter/API layer can
serialize the same projection for `GET /world/state` and submit
`WorldInteraction` equivalents for `POST /world/action`.

## Acceptance Criteria

1. The scheduler maintains a 30-to-60-minute candidate window without model
   calls on every tick.
2. A candidate cannot alter character activity until cognition and, where
   necessary, participant agreement succeed.
3. User interactions cause direct and dependency-based event reevaluation.
4. World state and timeline recovery survive process death and support bounded
   offline catch-up.
5. Every settled event has objective history; character memory is selective and
   role-scoped.
6. Existing conversation, group, image, and proactive behavior remains intact.
7. The local scene projection meets the future frontend document's data needs
   without exposing hidden AI state.
