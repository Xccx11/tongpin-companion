# Family Group Chat Verification

Date: 2026-07-31

## Scope

- Group settings are observable in the home state and editable from group management.
- Group atmosphere is persisted as `QUIET`, `DAILY`, or `LIVELY`.
- Shared group facts are scoped by group, retain their allow/deny flag, and can be edited or deleted.
- Group member state is filtered to the current group and current member set.
- Persisted image actions take precedence over compatibility moment-image rows with the same ID.
- Character-to-user relationship text is editable, migrated from database version 14 to 15, backed up, transferred, and injected into conversational prompts.
- Relationship text is not used by image prompt generation, which continues to read appearance data only.
- Group prompts receive each member's relationship setting; private-chat context is not copied into group history.
- Group proactive turns exclude paused members, keep each group's delivery ledger independent, and record a silent model decision for the current three-minute bucket without consuming delivery quota. The planner distinguishes `CONTINUE`, `START_TOPIC`, and `SILENT`; `START_TOPIC` may invite one current member, and later speakers read messages persisted earlier in the same turn.
- When more than one group member speaks in a turn, later speakers receive the messages already generated earlier in that same turn.
- User-initiated and proactive group turns read all visible messages belonging to that group; private chats retain their existing recent-message window.
- Group user messages are persisted before speaker-selection model planning, so the user's own message appears immediately and is present in the planner's context.

## Verification Commands

```text
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Both commands completed successfully on JDK 21.

The final verification also ran the combined test/build command after the full-history change:

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

## Artifact

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Application ID: `com.tongpin.companion`
- Version: `2.0.0` (`versionCode 42`)
- SHA-256 (latest group-send fix): `1B7DAE4C7384F5A021416E6D3B087E224E24F8C8D0B6E8C9D4B5EF6D68368F1E`
- Size (latest group-send fix): `64,278,489` bytes

No device installation or data cleanup was performed during this verification.
