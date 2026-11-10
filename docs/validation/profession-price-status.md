# Profession filtering, price coverage and scan status

Validated on 2026-11-09 with Java 25 and Minecraft 26.3. All game integration tests ran in the isolated Gradle test directory. The user's Prism instance, configuration and saved data were only read; its installed JAR was not replaced.

## Diagnosis

The running client connected to a multiplayer server, so the relevant evidence was the mod's persisted world JSON rather than a local singleplayer save. Its configuration enabled automatic scanning, used a 30-minute cache and had no custom trade targets.

The installed optimizer used commodity-only coverage to select traders, then price-sensitive replacement checks to classify the remaining traders. This could select a librarian selling Thorns III for 32 emeralds while leaving a 12-emerald trader outside the retained set. The latter then received REVIEW, translated as “待检查”, even though its offers were successfully captured. Unlocked future levels reduced confidence but did not directly cause REVIEW.

A read-only offline replay compared the installed JAR and the modified main classes using the same saved data. All recorded UUIDs were treated as loaded, and evaluation time was fixed to the newest saved snapshot timestamp to isolate coverage from availability and expiry. The replay contained 66 records, including 52 snapshots: scanned REVIEW results fell from 9 to 0, with no coverage gaps. This is an offline algorithm check, not a live multiplayer validation of the new JAR.

## Behavior

- Settings offer profession switches, select all and select none. Choices remain a draft until the parent settings form is saved. Disabled professions retain their records but are excluded from scanning, markers, navigation, current lists, statistics and retention analysis.
- Disabling an active profession cancels its probe and invalidates old asynchronous analysis. A loaded entity's current profession is checked against its cached record before that record can participate.
- Commodity backup requirements are supplemented with one price guarantee for every nondominated relevant trade vector. Both selection and replacement evidence use the same replacement rule. More expensive backups remain valid; target scopes and manual overrides remain in effect.
- Presentation distinguishes unscanned, expired, analyzing and scanned-but-needing-review states. Future unlocks remain a completeness/confidence warning. Markers, list symbols/colors and details share the display-state resolver.

## Verification

Command: `JAVA_HOME=<Java 25> ./gradlew test runClientGameTest build --console=plain`

- 142 unit tests passed, with no failures, errors or skipped tests. Added regression scenarios cover cheap specialists versus generalists, removal after price coverage is restored, unique goods, incomparable material costs, backups, target scopes, deterministic ordering, display states and serialized profession settings.
- Client integration passed: active-probe cancellation; retained snapshots; exclusion from navigation and statistics; rejection of stale analysis; settings draft cancellation; select all/none; no automatic probes when all professions are disabled; and cached re-enable. See `VV_PROFESSION_FILTER_PASS` in `profession-price-integration.log`.
- Existing packet, held-item, rendering, input, persistence, world-transition and cleanup integration scenarios also passed.
- The 500-villager / 4,000-trade benchmark completed in approximately 182 ms in the recorded unit run; see the accompanying PerformanceTest XML. This is a local measurement, not a timing guarantee.
- Visually inspected `profession-filter-zh.png`: profession switches and select/save/cancel controls fit the test window and use Chinese labels.
- Verified the release JAR contains the new screen and display model, and excludes the client integration test mod. `git diff --check` passed.

Output: `build/libs/VillagerVerdict-1.0.0.jar` (177,271 bytes).

SHA-256: `31523a5cfae04542b594163d4864cd6c56e320e676cd5e7ccdc51a9cbb1737f6`.
