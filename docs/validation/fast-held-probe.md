# Fast probing with held items

Validated on 2026-11-09 with Java 25 and Minecraft 26.3 in the isolated Gradle client test directory. The user's Prism instance was read only.

The old implementation timed out waiting for the first automatic snapshot when a sword was held. After the fix, the real integrated-server test captures an unopened merchant with a sword and shield equipped, closes the hidden menu, and preserves both items.

An additional scenario disables periodic scanning and introduces three new workers. With a named name tag and spawn egg equipped, no automatic interaction starts. Replacing the offhand egg with a shield allows all three snapshots to be collected through the offhand, without manual scanning, applying the name tag, or trading. The unit test verifies the 100 ms interval boundary while preserving one outstanding request and the successful-villager cooldown.

Validation command: `./gradlew test runClientGameTest build` with `JAVA_HOME` pointing to Prism's Java 25 runtime. All tasks passed; see `fast-held-probe-integration.log` and `TEST-cn.ericcraft.villagerverdict.ProbeTest.xml`. The release JAR excludes the integration test mod.

Existing installations retain their configured interval; change `intervalMillis` from 2000 to 100 to use the faster setting. Actual completion still depends on the server returning offers and menu contents. This has not yet been validated against the user's live multiplayer server with the new JAR.
