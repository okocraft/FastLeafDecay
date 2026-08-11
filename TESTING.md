# Game Test Guidelines

## Scope

These in-game tests verify that FastLeafDecay integrates correctly with a real Paper server, including vanilla leaf and log classification, leaf block data, events, region scheduling, and world operations.

A passing result means:

- Naturally generated, non-persistent leaves decay quickly after losing log support.
- Leaves that are still supported by logs and player-placed persistent leaves are not removed.
- Decay uses the normal block-breaking path and configured visual/audio feedback works on a real server.
- World filtering is honored against actual `World` identity.
- Another plugin can cancel `LeavesDecayEvent` and prevent FastLeafDecay from removing the leaf.

Configuration parsing, delay clamping, world name/key matching, persistent/distance decision logic, pending-position deduplication, world-unload/plugin-disable queue cleanup, and effect boolean branching are delegated to the existing automated tests.

Those tests use mocked Bukkit/Paper objects, so they do not guarantee real-runtime behavior for tags, block data, the RegionScheduler, event dispatch, or world mutation. Baseline game tests therefore start at those platform boundaries.

## Core Behaviors

### CB-001: Unsupported leaves decay promptly

Description:
When a player removes the logs supporting naturally generated leaves, unsupported leaves begin decaying quickly and the decay propagates through the unsupported canopy.

Importance:
This is the plugin's primary user-facing behavior. If it fails, there is no release value in FastLeafDecay.

Related code:
- `src/main/java/com/cavetale/fastleafdecay/decay/LeafDecayHandler.java`
- `src/main/java/com/cavetale/fastleafdecay/decay/LeafDecayExecutor.java`

### CB-002: Leaves that should remain are not destroyed

Description:
Leaves that remain connected to logs, and player-placed leaves marked persistent by Minecraft, are not removed by FastLeafDecay.

Importance:
Incorrect removal can damage builds or living trees and is a release-blocking regression.

Related code:
- `src/main/java/com/cavetale/fastleafdecay/decay/LeafDecayHandler.java`
- `src/main/java/com/cavetale/fastleafdecay/decay/LeafDecayExecutor.java`

### CB-003: World filtering controls fast decay

Description:
Fast decay does not start in worlds disabled by configuration and continues to work in enabled worlds.

Importance:
Applying FastLeafDecay in a world that an administrator explicitly excluded violates a configuration guarantee.

Related code:
- `src/main/java/com/cavetale/fastleafdecay/config/FastLeafDecayConfig.java`
- `src/main/java/com/cavetale/fastleafdecay/config/WorldFilter.java`
- `src/main/java/com/cavetale/fastleafdecay/decay/LeafDecayHandler.java`

### CB-004: Decay integrates with other plugins and vanilla breaking

Description:
FastLeafDecay fires `LeavesDecayEvent` before removing a leaf and aborts removal when another listener cancels the event. Actual removal uses the natural block-breaking path.

Importance:
This preserves compatibility with protection/gameplay plugins and vanilla leaf loot behavior. Ignoring cancellation could bypass another plugin's protection rules.

Related code:
- `src/main/java/com/cavetale/fastleafdecay/decay/LeafDecayExecutor.java`

## Platform Dependency Points

### DP-001: Vanilla log/leaves classification and leaf block state

Platform:
Minecraft | Bukkit API

Usage:
The plugin uses Minecraft log/leaves tags to identify relevant blocks and checks `Leaves` block data at execution time to determine persistence and distance from supporting logs.

Affected behaviors:
- CB-001
- CB-002

Regression risks:
- New log or leaf variants are omitted from tag-based detection and never enter fast decay.
- Leaf block-state semantics or update timing changes cause supported leaves to be removed or unsupported leaves to remain.
- Distance updates no longer settle before the scheduled check runs.

Relevant APIs / concepts:
- `Tag.LOGS`
- `Tag.LEAVES`
- `Leaves#isPersistent()`
- `Leaves#getDistance()`
- vanilla leaf distance updates

Related code:
- `src/main/java/com/cavetale/fastleafdecay/FastLeafDecayPlugin.java`
- `src/main/java/com/cavetale/fastleafdecay/decay/LeafDecayHandler.java`
- `src/main/java/com/cavetale/fastleafdecay/decay/LeafDecayExecutor.java`

### DP-002: Block events and region scheduling

Platform:
Paper | Bukkit API

Usage:
The plugin detects log/leaf removal through block events, schedules delayed checks at leaf locations with Paper's RegionScheduler, and uses subsequent leaf-decay events to continue the cascade.

Affected behaviors:
- CB-001
- CB-002
- CB-003

Regression risks:
- Block-break or leaf-decay events no longer arrive at the expected time, preventing the cascade from starting or continuing.
- Region tasks do not run or fail due to scheduler/threading changes.
- Checks run before vanilla block-state updates settle and incorrectly preserve unsupported leaves.

Relevant APIs / concepts:
- `BlockBreakEvent`
- `LeavesDecayEvent`
- `RegionScheduler#runDelayed`
- region ownership
- block update timing

Related code:
- `src/main/java/com/cavetale/fastleafdecay/decay/LeafDecayHandler.java`

### DP-003: World identity and runtime filtering

Platform:
Bukkit API

Usage:
Configured world entries are matched against Bukkit world names or namespaced world keys. Disabled worlds are rejected both when work is scheduled and when the delayed task executes.

Affected behaviors:
- CB-003

Regression risks:
- Changes to world name/key handling cause intended filters not to match.
- A delayed task still mutates a world after it becomes disabled.

Relevant APIs / concepts:
- `World#getName()`
- `World#getKey()`
- `NamespacedKey`

Related code:
- `src/main/java/com/cavetale/fastleafdecay/config/WorldFilter.java`
- `src/main/java/com/cavetale/fastleafdecay/config/FastLeafDecayConfig.java`
- `src/main/java/com/cavetale/fastleafdecay/decay/LeafDecayHandler.java`

### DP-004: LeavesDecayEvent cancellation

Platform:
Bukkit API

Usage:
Immediately before removal, FastLeafDecay fires `LeavesDecayEvent`. If another listener cancels it, effects and natural breaking are both skipped.

Affected behaviors:
- CB-004

Regression risks:
- The synthetic event is no longer delivered correctly to other plugins.
- Cancellation semantics change and the leaf is still removed after cancellation.

Relevant APIs / concepts:
- `LeavesDecayEvent`
- `PluginManager#callEvent`
- cancellable event semantics

Related code:
- `src/main/java/com/cavetale/fastleafdecay/decay/LeafDecayExecutor.java`

### DP-005: Natural block breaking and decay feedback

Platform:
Minecraft | Paper | Bukkit API

Usage:
For a leaf that is allowed to decay, the plugin emits block particles and a break sound according to configuration, then removes the block with `breakNaturally()`.

Affected behaviors:
- CB-001
- CB-004

Regression risks:
- Leaves disappear without following normal loot behavior.
- Particle or sound API changes throw at runtime and abort the decay operation.
- Configured feedback is no longer visible/audible in game.

Relevant APIs / concepts:
- `Block#breakNaturally()`
- `Particle.BLOCK`
- Adventure `Sound`
- block loot

Related code:
- `src/main/java/com/cavetale/fastleafdecay/decay/LeafDecayExecutor.java`

## Baseline Smoke Tests

### BT-001: Natural tree fast-decay path

Purpose:
Verify the plugin's primary real-server path: log break -> event -> region task -> leaf-state recheck -> decay event -> natural break -> cascading neighbor checks.

Setup:
1. Start the server with FastLeafDecay enabled and its default configuration.
2. Confirm there is no FastLeafDecay load/enable exception in the server log.
3. Grow one normal tree from a sapling with bone meal. Do not substitute manually placed leaves.
4. Use a location where no other tree or log touches the canopy.

Operation:
1. Break the complete trunk from the bottom upward.
2. Watch the canopy immediately after removing the trunk.
3. Continue watching until the unsupported canopy has finished decaying.

Pass criteria:
- Naturally generated leaves that lost log support begin decaying promptly.
- The decay does not stop after only the first few leaves; it propagates through the unsupported canopy.
- Decaying leaves show the configured block particle and quiet break sound.
- Leaves are naturally broken; if items drop, they appear as normal world drops.
- No FastLeafDecay-related runtime exception occurs.
- No non-leaf neighboring block is removed.

Covers:
- CB-001
- CB-004
- DP-001
- DP-002
- DP-005

Typical duration:
About 1-2 minutes

Notes:
Leaf drops are probabilistic. Do not require a specific item to drop in one run. The useful observation is that any generated item is a normal world drop and decay completes successfully.

### BT-002: Supported and persistent leaves remain intact

Purpose:
Verify on real Minecraft block data that FastLeafDecay does not remove leaves that are not valid decay targets.

Setup:
1. Enable FastLeafDecay.
2. Grow one natural tree from a sapling.
3. Nearby, place several leaf blocks manually as a player.
4. Place one log next to the manually placed leaves to act as a block-break trigger.

Operation:
1. Remove only part of the natural tree's trunk, leaving some leaves connected to remaining logs.
2. Break the trigger log next to the manually placed leaves.
3. Observe for longer than the normal FastLeafDecay delay.

Pass criteria:
- Natural leaves that remain supported by the remaining logs are not removed by FastLeafDecay.
- Player-placed persistent leaves are not removed after the adjacent log is broken.
- No FastLeafDecay decay particle or sound is emitted for persistent leaves.
- No FastLeafDecay-related runtime exception occurs.

Covers:
- CB-002
- DP-001
- DP-002

Typical duration:
About 1 minute

### BT-003: Excluded world does not fast-decay

Purpose:
Verify that an administrator can disable FastLeafDecay in one world without disabling it in another world on the same server.

Setup:
1. Make two test worlds available.
2. Add one world name to `ExcludeWorlds`.
3. Restart the server/plugin in the supported way so the configuration is applied.
4. Grow one isolated tree from a sapling with bone meal in each world.

Operation:
1. In the excluded world, break the entire trunk and observe the leaves immediately afterward.
2. Move to the non-excluded world.
3. Break the entire trunk of the second tree.
4. Compare the two behaviors.

Pass criteria:
- The excluded world does not start FastLeafDecay's prompt decay cascade.
- FastLeafDecay's additional particle/sound feedback is not emitted in the excluded world.
- The non-excluded world behaves as in BT-001 and starts fast cascading decay.
- Filtering of one world does not leak into the other world.
- No FastLeafDecay-related runtime exception occurs.

Covers:
- CB-003
- DP-002
- DP-003

Typical duration:
About 2-3 minutes

Notes:
Vanilla random leaf decay can still happen later in the excluded world. Do not require the leaves to remain forever; judge whether FastLeafDecay's prompt cascade and extra feedback are absent.

### BT-004: LeavesDecayEvent cancellation prevents plugin decay

Purpose:
Verify that FastLeafDecay respects cancellation by another plugin through Paper's real event dispatch path.

Setup:
1. Prepare a minimal test listener that receives `LeavesDecayEvent` and can cancel events for the test leaves.
2. Enable FastLeafDecay and the test listener.
3. Grow one isolated tree from a sapling with bone meal.
4. Make the listener record event receipt in a way visible to the tester, such as the server log.

Operation:
1. Enable cancellation in the test listener.
2. Break the entire trunk.
3. Wait for FastLeafDecay to attempt decay.
4. Inspect the listener's event record and the target leaves.

Pass criteria:
- The test listener receives `LeavesDecayEvent` for leaves FastLeafDecay attempts to decay.
- A leaf whose event is cancelled is not removed by FastLeafDecay.
- FastLeafDecay's decay particle/sound is not emitted for a cancelled leaf.
- No event recursion, abnormal event storm, or runtime exception occurs.

Covers:
- CB-004
- DP-004
- DP-005

Typical duration:
About 1-2 minutes

Notes:
This test requires a small external listener. The cancellation branch itself is already covered by Mockito tests; this baseline test exists only to verify integration with Paper's real event bus.

## Conditional Test Areas

### CT-001: Leaf block state or vanilla decay rule changes

Trigger:
Minecraft changes leaf distance, persistence state, log/leaf tags, or vanilla leaf update/decay rules.

Related dependency points:
- DP-001
- DP-002

Possible regressions:
- A new leaf variant never fast-decays.
- Leaf distance never reaches the plugin's decay condition after the trunk is removed.
- Supported leaves are removed incorrectly.
- Player-placed leaves lose their protection.

Recommended verification:
Run BT-001 and BT-002 using the affected leaf/log variant. If the upstream change only adds a new variant, substitute that variant into the existing operations rather than adding a new baseline test.

### CT-002: Paper region scheduling changes

Trigger:
Paper/Folia changes RegionScheduler behavior, region ownership, scheduled-task lifecycle, or task handling around world unload.

Related dependency points:
- DP-002

Possible regressions:
- Decay tasks never execute.
- Scheduler/threading checks fail at runtime.
- A stale task accesses a world after it unloads.

Recommended verification:
Start with BT-001 for the normal path. Only when upstream changes touch world-unload scheduling should an additional observation unload a world while decay tasks are pending and confirm no decay operation or exception occurs afterward.

### CT-003: LeavesDecayEvent semantics changes

Trigger:
Bukkit/Paper changes `LeavesDecayEvent` dispatch, cancellation semantics, event ordering, or leaf-decay lifecycle.

Related dependency points:
- DP-002
- DP-004

Possible regressions:
- The FastLeafDecay cascade no longer starts/continues correctly or is processed twice.
- Other plugins can no longer veto FastLeafDecay removal.

Recommended verification:
Run BT-004. If needed, record event counts in the test listener to confirm that one decay attempt does not create abnormal recursive event delivery.

### CT-004: Natural block breaking, loot, particle, or sound changes

Trigger:
Paper/Minecraft changes `breakNaturally()`, leaf loot behavior, block particle data, or Adventure sound playback.

Related dependency points:
- DP-005

Possible regressions:
- Leaves disappear but no longer behave like naturally broken leaves for loot.
- An effect call throws and prevents the block from being removed.
- Particle or sound feedback disappears.

Recommended verification:
Use BT-001 and focus its existing observations on drops and feedback. If loot tables themselves changed, compare behavior with the current version's vanilla leaf break instead of hard-coding an expected item count.

### CT-005: World identity or configuration integration changes

Trigger:
Bukkit/Paper changes world keys, world lifecycle, or configuration-library integration relevant to world selection.

Related dependency points:
- DP-003

Possible regressions:
- A configured world name/key no longer matches.
- FastLeafDecay runs in a disabled world.

Recommended verification:
Run BT-003. If the upstream change is specifically about world keys, use a namespaced key in `ExcludeWorlds` instead of a world name while keeping the rest of the test unchanged.

## Test Design Notes

- Use trees grown from saplings and bone meal for natural-leaf tests so they are not confused with player-placed persistent leaves.
- Keep test trees separated from other trees and logs; nearby support can preserve leaf distance and make the result ambiguous.
- Leaf item drops are probabilistic. Do not use the presence of one specific drop in one run as a pass condition.
- Vanilla random decay can still occur in an excluded world. Judge world filtering by the absence of FastLeafDecay's prompt cascade and additional effects, not by leaves remaining forever.
- Use an external test listener only for the real-runtime `LeavesDecayEvent` cancellation check. Do not add instrumentation to the other baseline tests.
- Delay boundaries, world-filter string parsing, and pending-queue deduplication are automated-test responsibilities; do not manually enumerate those combinations in game.

## Maintenance Rules

Update this file when:

- A Core Behavior is added, removed, or changed.
- The way FastLeafDecay depends on Minecraft/Paper changes.
- A new regression is discovered.
- A baseline test proves unstable or redundant.
- An important issue is found that the baseline suite cannot detect.
