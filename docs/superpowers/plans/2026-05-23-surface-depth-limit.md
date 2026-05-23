# Surface Depth Limit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a world-creation option that limits underground terrain generation to a configurable depth below the local surface, leaving everything deeper as air. Auto-suppresses cave/ore/lava feature placement while active.

**Architecture:** Two new fields on `EarthGeneratorSettings` (`surfaceDepthLimitEnabled`, `surfaceDepthLimit`). Wired through codec as standalone `MapCodec`s (matching the existing `DEEP_DARK_CODEC` / `GEODES_CODEC` pattern — no `BASE_CODEC` or `SettingsBase` changes). New `effectiveCaveGeneration()` / `effectiveOreDistribution()` / `effectiveLavaPools()` accessors return `raw && !surfaceDepthLimitEnabled`; all worldgen *consumption* sites switch to the `effective*` variants. `EarthChunkGenerator`'s per-column fill loop clamps its starting `y` to `max(chunkMinY, surface − surfaceDepthLimit + 1)` when the limit is on. Pure-function helper extracted to `SurfaceDepthLimit` in the shared `src/main` tree so all three MC version trees reuse the math and the test runs once.

**Tech Stack:** Java 17, Fabric (Minecraft 1.20.1, 1.21.1, 26.1.2), Mojang DataFixerUpper codecs, JUnit Jupiter 5, Gradle multi-project build.

---

## Spec Reference

This plan implements `docs/superpowers/specs/2026-05-23-surface-shell-depth-design.md`. The spec uses the working name "surface shell"; this plan uses **"surface depth limit"** in code/UI because `fillTerrainShellColumns` already exists in `EarthChunkGenerator` and means something unrelated (chunk-edge cover-class sampling).

---

## File Map

**Created:**
- `src/main/java/com/yucareux/tellus/worldgen/SurfaceDepthLimit.java` — pure-function helper (cutoff math + constants), shared across all MC versions.
- `src/test/java/com/yucareux/tellus/worldgen/SurfaceDepthLimitTest.java` — unit test for the helper.
- `src/test/java/com/yucareux/tellus/worldgen/EarthGeneratorSettingsSurfaceDepthLimitTest.java` — codec round-trip + accessor behavior test (one shared test file; relies on `mc1211` being on the test classpath via the existing test config).

**Modified, in each of `mc1201/`, `mc1211/`, `mc261/`:**
- `src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java`
  - Two new record components, constructor parameters, DEFAULT entries, `with*` mutators, codec entries, three `effective*` accessors.
- `src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java`
  - Replace existing `settings.caveGeneration()` / `oreDistribution()` / `lavaPools()` calls at consumption sites with `effective*` variants (lines 593, 664, 8076, 8080, 8084, 8108, 8118, 8123–8124).
  - Add column-fill short-circuit around the per-column loop (lines 1032–1085) and the bulk-solid-sections fill (around line 1021).
- `src/main/java/com/yucareux/tellus/worldgen/EarthBiomeSource.java`
  - Replace `settings.caveGeneration()` at lines 170, 276 with `effectiveCaveGeneration()`. (Note: only `mc1201` and the shared `src/main` copy of this file. `mc1211` and `mc261` do not have their own copy — confirm with `git grep` before editing.)
- `src/client/java/com/yucareux/tellus/client/screen/EarthCustomizeScreen.java`
  - Add toggle + slider rows to the `geological` category. Add init/extract calls. Keep raw-value bindings (UI shows what user chose, not what's effectively in play).

**Modified, shared:**
- `src/main/java/com/yucareux/tellus/worldgen/EarthBiomeSource.java` — same two replacements as the per-version copy.
- `src/main/resources/assets/tellus/lang/en_us.json` — four new lang keys.

---

## Naming Conventions Used Below

- Record fields: `surfaceDepthLimitEnabled` (boolean), `surfaceDepthLimit` (int).
- JSON keys (codec): `"surface_depth_limit_enabled"`, `"surface_depth_limit"`.
- Lang category key: existing `geological`; new property keys: `surface_depth_limit_enabled`, `surface_depth_limit`.
- Helper class: `SurfaceDepthLimit` (constants `MIN_DEPTH = 2`, `MAX_DEPTH = 256`, `DEFAULT_DEPTH = 16`; function `int cutoffY(int localSurfaceY, int depth)`).

These names appear verbatim in every task that references them; do not alter casing or spelling.

---

## Task 1: Shared `SurfaceDepthLimit` helper

**Files:**
- Create: `src/main/java/com/yucareux/tellus/worldgen/SurfaceDepthLimit.java`
- Create: `src/test/java/com/yucareux/tellus/worldgen/SurfaceDepthLimitTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/yucareux/tellus/worldgen/SurfaceDepthLimitTest.java`:

```java
package com.yucareux.tellus.worldgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurfaceDepthLimitTest {
   @Test
   void cutoffYIsSurfaceMinusDepthPlusOne() {
      // depth 16 below surface 100 means y=85..100 are filled (16 blocks),
      // and y<=84 is the air zone. cutoffY returns the first filled y.
      assertEquals(85, SurfaceDepthLimit.cutoffY(100, 16));
   }

   @Test
   void cutoffYHandlesMinDepth() {
      assertEquals(99, SurfaceDepthLimit.cutoffY(100, SurfaceDepthLimit.MIN_DEPTH));
   }

   @Test
   void cutoffYHandlesMaxDepth() {
      assertEquals(100 - SurfaceDepthLimit.MAX_DEPTH + 1, SurfaceDepthLimit.cutoffY(100, SurfaceDepthLimit.MAX_DEPTH));
   }

   @Test
   void cutoffYWorksForNegativeSurface() {
      // Tellus can have negative surface elevations (ocean floors).
      assertEquals(-50 - 16 + 1, SurfaceDepthLimit.cutoffY(-50, 16));
   }

   @Test
   void clampDepthRejectsBelowMin() {
      assertEquals(SurfaceDepthLimit.MIN_DEPTH, SurfaceDepthLimit.clampDepth(0));
      assertEquals(SurfaceDepthLimit.MIN_DEPTH, SurfaceDepthLimit.clampDepth(-1));
   }

   @Test
   void clampDepthRejectsAboveMax() {
      assertEquals(SurfaceDepthLimit.MAX_DEPTH, SurfaceDepthLimit.clampDepth(10_000));
   }

   @Test
   void clampDepthPassesValidValuesThrough() {
      assertEquals(16, SurfaceDepthLimit.clampDepth(16));
      assertEquals(SurfaceDepthLimit.DEFAULT_DEPTH, SurfaceDepthLimit.clampDepth(SurfaceDepthLimit.DEFAULT_DEPTH));
   }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :mc1211:test --tests com.yucareux.tellus.worldgen.SurfaceDepthLimitTest
```

Expected: compile error, "cannot find symbol SurfaceDepthLimit".

(If the test runner is at root instead, use `./gradlew test --tests ...`. Tests live in the shared `src/test` tree, so any subproject's `test` task should pick them up. If only one subproject runs the shared tests, that's fine — use whichever one works.)

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/yucareux/tellus/worldgen/SurfaceDepthLimit.java`:

```java
package com.yucareux.tellus.worldgen;

/**
 * Pure-function helper for the Surface Depth Limit feature. Shared across all
 * Minecraft version trees.
 */
public final class SurfaceDepthLimit {
   public static final int MIN_DEPTH = 2;
   public static final int MAX_DEPTH = 256;
   public static final int DEFAULT_DEPTH = 16;

   private SurfaceDepthLimit() {}

   /**
    * Returns the lowest Y at which terrain should still be placed when the
    * limit is active. Blocks with {@code y < cutoffY} are left as air.
    */
   public static int cutoffY(int localSurfaceY, int depth) {
      return localSurfaceY - depth + 1;
   }

   public static int clampDepth(int depth) {
      if (depth < MIN_DEPTH) {
         return MIN_DEPTH;
      }
      if (depth > MAX_DEPTH) {
         return MAX_DEPTH;
      }
      return depth;
   }
}
```

- [ ] **Step 4: Run test to verify it passes**

```
./gradlew :mc1211:test --tests com.yucareux.tellus.worldgen.SurfaceDepthLimitTest
```

Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```
git add src/main/java/com/yucareux/tellus/worldgen/SurfaceDepthLimit.java \
        src/test/java/com/yucareux/tellus/worldgen/SurfaceDepthLimitTest.java
git commit -m "feat(worldgen): add SurfaceDepthLimit helper"
```

---

## Task 2: Lang keys

**Files:**
- Modify: `src/main/resources/assets/tellus/lang/en_us.json`

- [ ] **Step 1: Add four new keys**

Open `src/main/resources/assets/tellus/lang/en_us.json`. After the existing `"property.tellus.lava_pools.tooltip"` entry (around line 149), insert four new entries — two `name` lines beside the other property names, and two `tooltip` lines beside the other tooltips. The file is sorted by group (names together, tooltips together), so follow that grouping.

Add (near the other `property.tellus.*.name` entries, e.g. after `property.tellus.lava_pools.name`):

```json
  "property.tellus.surface_depth_limit_enabled.name": "Surface Depth Limit",
  "property.tellus.surface_depth_limit.name": "Surface Depth (blocks)",
```

Add (near the other `property.tellus.*.tooltip` entries, after `property.tellus.lava_pools.tooltip`):

```json
  "property.tellus.surface_depth_limit_enabled.tooltip": "When on, generates only a thin layer of terrain below the surface to save compute. Caves, ores, and lava pools are suppressed while this is on.",
  "property.tellus.surface_depth_limit.tooltip": "How many blocks below the surface to generate. Lower is faster. Range 2-256.",
```

Mind the trailing commas: every line except the last property in a JSON object needs one. Run a quick `python -m json.tool en_us.json > /dev/null` to confirm it still parses.

- [ ] **Step 2: Verify JSON validity**

```
python -m json.tool src/main/resources/assets/tellus/lang/en_us.json > /dev/null
```

Expected: no output, exit 0.

- [ ] **Step 3: Commit**

```
git add src/main/resources/assets/tellus/lang/en_us.json
git commit -m "lang(en_us): add surface depth limit strings"
```

---

## Task 3: Add fields, codec, and accessors to `EarthGeneratorSettings` (all three MC versions)

This task is the same set of edits applied verbatim to three files:

- `mc1201/src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java`
- `mc1211/src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java`
- `mc261/src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java`

Make the edits to **all three** files in this task. Commit at the end.

**Files:**
- Modify: `mc1201/src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java`
- Modify: `mc1211/src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java`
- Modify: `mc261/src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java`
- Create: `src/test/java/com/yucareux/tellus/worldgen/EarthGeneratorSettingsSurfaceDepthLimitTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/yucareux/tellus/worldgen/EarthGeneratorSettingsSurfaceDepthLimitTest.java`:

```java
package com.yucareux.tellus.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EarthGeneratorSettingsSurfaceDepthLimitTest {
   @Test
   void defaultsAreOffAndSixteen() {
      EarthGeneratorSettings d = EarthGeneratorSettings.DEFAULT;
      assertFalse(d.surfaceDepthLimitEnabled());
      assertEquals(16, d.surfaceDepthLimit());
   }

   @Test
   void effectiveAccessorsPassThroughWhenLimitOff() {
      EarthGeneratorSettings s = EarthGeneratorSettings.DEFAULT
         .withSurfaceDepthLimitEnabled(false)
         .withCaveGeneration(true)
         .withOreDistribution(true)
         .withLavaPools(true);
      assertTrue(s.effectiveCaveGeneration());
      assertTrue(s.effectiveOreDistribution());
      assertTrue(s.effectiveLavaPools());
   }

   @Test
   void effectiveAccessorsForceOffWhenLimitOn() {
      EarthGeneratorSettings s = EarthGeneratorSettings.DEFAULT
         .withSurfaceDepthLimitEnabled(true)
         .withCaveGeneration(true)
         .withOreDistribution(true)
         .withLavaPools(true);
      assertFalse(s.effectiveCaveGeneration());
      assertFalse(s.effectiveOreDistribution());
      assertFalse(s.effectiveLavaPools());
      // Raw values are preserved so toggling the limit off restores them.
      assertTrue(s.caveGeneration());
      assertTrue(s.oreDistribution());
      assertTrue(s.lavaPools());
   }

   @Test
   void depthIsClampedOnConstruction() {
      assertEquals(SurfaceDepthLimit.MIN_DEPTH,
         EarthGeneratorSettings.DEFAULT.withSurfaceDepthLimit(0).surfaceDepthLimit());
      assertEquals(SurfaceDepthLimit.MAX_DEPTH,
         EarthGeneratorSettings.DEFAULT.withSurfaceDepthLimit(10_000).surfaceDepthLimit());
   }

   @Test
   void codecRoundTripsNewFields() {
      EarthGeneratorSettings original = EarthGeneratorSettings.DEFAULT
         .withSurfaceDepthLimitEnabled(true)
         .withSurfaceDepthLimit(8);
      DataResult<JsonElement> encoded = EarthGeneratorSettings.CODEC.encodeStart(JsonOps.INSTANCE, original);
      assertTrue(encoded.result().isPresent(), "encode succeeded");
      JsonObject json = encoded.result().get().getAsJsonObject();
      assertTrue(json.get("surface_depth_limit_enabled").getAsBoolean());
      assertEquals(8, json.get("surface_depth_limit").getAsInt());

      DataResult<com.mojang.datafixers.util.Pair<EarthGeneratorSettings, JsonElement>> decoded =
         EarthGeneratorSettings.CODEC.decode(JsonOps.INSTANCE, json);
      assertTrue(decoded.result().isPresent(), "decode succeeded");
      EarthGeneratorSettings roundTripped = decoded.result().get().getFirst();
      assertTrue(roundTripped.surfaceDepthLimitEnabled());
      assertEquals(8, roundTripped.surfaceDepthLimit());
   }

   @Test
   void codecDecodesOldPayloadWithoutNewFields() {
      // Same as roundTripsCurrentSettingsPayload in EarthGeneratorSettingsCodecTest,
      // but verifies the new fields fall back to defaults when absent.
      String oldPayload = "{\"world_scale\":18.5,\"terrestrial_height_scale\":1.0,"
         + "\"oceanic_height_scale\":1.0,\"height_offset\":64,\"spawn_latitude\":0.0,"
         + "\"spawn_longitude\":0.0,\"min_altitude\":-64,\"max_altitude\":-2147483648,"
         + "\"river_lake_shoreline_blend\":5,\"ocean_shoreline_blend\":5,"
         + "\"shoreline_blend_cliff_limit\":true,\"cave_generation\":false,"
         + "\"ore_distribution\":false,\"lava_pools\":false}";
      JsonElement input = JsonParser.parseString(oldPayload);
      DataResult<com.mojang.datafixers.util.Pair<EarthGeneratorSettings, JsonElement>> decoded =
         EarthGeneratorSettings.CODEC.decode(JsonOps.INSTANCE, input);
      assertTrue(decoded.result().isPresent(),
         "decode of pre-feature payload should succeed; error=" + decoded.error().map(e -> e.message()).orElse(""));
      EarthGeneratorSettings s = decoded.result().get().getFirst();
      assertFalse(s.surfaceDepthLimitEnabled());
      assertEquals(16, s.surfaceDepthLimit());
   }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```
./gradlew :mc1211:test --tests com.yucareux.tellus.worldgen.EarthGeneratorSettingsSurfaceDepthLimitTest
```

Expected: compile errors — `withSurfaceDepthLimitEnabled`, `surfaceDepthLimitEnabled()`, `effectiveCaveGeneration()`, etc. not found.

- [ ] **Step 3: Add the two record components and constructor params**

In each of the three `EarthGeneratorSettings.java` files, locate the record header (around lines 19–70). Append two new components at the **end** of the parameter list, just before the closing `)`. Existing last line is currently `boolean enableWater`. Change it to `boolean enableWater,` and add after it:

```java
   boolean surfaceDepthLimitEnabled,
   int surfaceDepthLimit
```

Locate the canonical constructor (around lines 471–522). It takes the same parameter list. Append the same two new parameters at the end. In the constructor body (lines ~523–579), append after `this.enableWater = enableWater;`:

```java
      this.surfaceDepthLimitEnabled = surfaceDepthLimitEnabled;
      this.surfaceDepthLimit = SurfaceDepthLimit.clampDepth(surfaceDepthLimit);
```

Add an import at the top of the file (just after the existing `package`/`import` block, alphabetically with the other `com.yucareux.tellus.worldgen` imports — or, since this file *is* in that package, no import is needed at all; remove this step if your IDE confirms the helper class is in the same package).

- [ ] **Step 4: Add DEFAULT entries**

Locate `public static final EarthGeneratorSettings DEFAULT = new EarthGeneratorSettings(...)` (starts around line 90, ends around line 141). The current last two literals are `false, false, false` (the three `enable*` flags). Change the trailing `false` to `false,` and add:

```java
      false,
      SurfaceDepthLimit.DEFAULT_DEPTH
```

so the constructor call now passes 51 arguments instead of 49. Match the indentation of surrounding lines.

- [ ] **Step 5: Add `with*` mutators**

Scroll to the existing `withEnableWater(boolean enableWater)` method (search for `public EarthGeneratorSettings withEnableWater`). Immediately after it, add:

```java
   public EarthGeneratorSettings withSurfaceDepthLimitEnabled(boolean surfaceDepthLimitEnabled) {
      return new EarthGeneratorSettings(
         this.worldScale, this.terrestrialHeightScale, this.oceanicHeightScale,
         this.heightOffset, this.seaLevel, this.spawnLatitude, this.spawnLongitude,
         this.minAltitude, this.maxAltitude, this.riverLakeShorelineBlend,
         this.oceanShorelineBlend, this.shorelineBlendCliffLimit,
         this.caveGeneration, this.oreDistribution, this.lavaPools,
         this.addStrongholds, this.addVillages, this.addMineshafts,
         this.addOceanMonuments, this.addWoodlandMansions, this.addDesertTemples,
         this.addJungleTemples, this.addPillagerOutposts, this.addRuinedPortals,
         this.addShipwrecks, this.addOceanRuins, this.addBuriedTreasure,
         this.addIgloos, this.addWitchHuts, this.addAncientCities,
         this.addTrialChambers, this.addTrailRuins, this.deepDark, this.geodes,
         this.distantHorizonsWaterResolver, this.distantHorizonsOsmFeatures,
         this.distantHorizonsOsmRoadMaxDetail, this.distantHorizonsOsmBuildingMaxDetail,
         this.distantHorizonsOsmNonBlockingFetch, this.realtimeTime,
         this.realtimeWeather, this.historicalSnow, this.voxyChunkPregenEnabled,
         this.voxyChunkPregenMaxRadius, this.voxyChunkPregenChunksPerTick,
         this.distantHorizonsRenderMode, this.demSelection,
         this.enableRoads, this.enableBuildings, this.enableWater,
         surfaceDepthLimitEnabled, this.surfaceDepthLimit
      );
   }

   public EarthGeneratorSettings withSurfaceDepthLimit(int surfaceDepthLimit) {
      return new EarthGeneratorSettings(
         this.worldScale, this.terrestrialHeightScale, this.oceanicHeightScale,
         this.heightOffset, this.seaLevel, this.spawnLatitude, this.spawnLongitude,
         this.minAltitude, this.maxAltitude, this.riverLakeShorelineBlend,
         this.oceanShorelineBlend, this.shorelineBlendCliffLimit,
         this.caveGeneration, this.oreDistribution, this.lavaPools,
         this.addStrongholds, this.addVillages, this.addMineshafts,
         this.addOceanMonuments, this.addWoodlandMansions, this.addDesertTemples,
         this.addJungleTemples, this.addPillagerOutposts, this.addRuinedPortals,
         this.addShipwrecks, this.addOceanRuins, this.addBuriedTreasure,
         this.addIgloos, this.addWitchHuts, this.addAncientCities,
         this.addTrialChambers, this.addTrailRuins, this.deepDark, this.geodes,
         this.distantHorizonsWaterResolver, this.distantHorizonsOsmFeatures,
         this.distantHorizonsOsmRoadMaxDetail, this.distantHorizonsOsmBuildingMaxDetail,
         this.distantHorizonsOsmNonBlockingFetch, this.realtimeTime,
         this.realtimeWeather, this.historicalSnow, this.voxyChunkPregenEnabled,
         this.voxyChunkPregenMaxRadius, this.voxyChunkPregenChunksPerTick,
         this.distantHorizonsRenderMode, this.demSelection,
         this.enableRoads, this.enableBuildings, this.enableWater,
         this.surfaceDepthLimitEnabled, surfaceDepthLimit
      );
   }
```

(If the existing `with*` mutators in this file use a different formulation — e.g. a builder helper — match that style instead. The constructor argument list must include all current fields plus the two new ones at the end.)

- [ ] **Step 6: Add the codec MapCodecs**

Search for `DEEP_DARK_CODEC` (around line 291). Just after the `GEODES_CODEC` line (around line 292), add two new MapCodecs:

```java
   private static final MapCodec<Boolean> SURFACE_DEPTH_LIMIT_ENABLED_CODEC = Codec.BOOL
      .fieldOf("surface_depth_limit_enabled")
      .orElse(DEFAULT.surfaceDepthLimitEnabled());
   private static final MapCodec<Integer> SURFACE_DEPTH_LIMIT_CODEC = Codec.intRange(SurfaceDepthLimit.MIN_DEPTH, SurfaceDepthLimit.MAX_DEPTH)
      .fieldOf("surface_depth_limit")
      .orElse(DEFAULT.surfaceDepthLimit());
```

- [ ] **Step 7: Wire the codecs into MAP_CODEC encode/decode/keys**

In the `MAP_CODEC` encode method (around lines 327–353), the last line before `return ... TRAIL_RUINS_CODEC.encode(...)` writes `STRUCTURE_CODEC`. Just before the final `return` line, add:

```java
            builder = EarthGeneratorSettings.SURFACE_DEPTH_LIMIT_ENABLED_CODEC.encode(input.surfaceDepthLimitEnabled(), ops, builder);
            builder = EarthGeneratorSettings.SURFACE_DEPTH_LIMIT_CODEC.encode(input.surfaceDepthLimit(), ops, builder);
```

In the `keys` method (around lines 355–377), before the final `Stream.concat(... TRAIL_RUINS_CODEC.keys(ops))`:

```java
            structureKeys = Stream.concat(structureKeys, EarthGeneratorSettings.SURFACE_DEPTH_LIMIT_ENABLED_CODEC.keys(ops));
            structureKeys = Stream.concat(structureKeys, EarthGeneratorSettings.SURFACE_DEPTH_LIMIT_CODEC.keys(ops));
```

(If the local variable is `baseKeys` rather than `structureKeys` in the version you're editing, use whatever variable is in scope on the final concatenation line.)

In the `decode` method (around lines 379–442), after the existing `DataResult<Boolean> trailRuins = ...` line and before the cascading `apply2(...)` chain, add:

```java
            DataResult<Boolean> surfaceDepthLimitEnabled = EarthGeneratorSettings.SURFACE_DEPTH_LIMIT_ENABLED_CODEC.decode(ops, input);
            DataResult<Integer> surfaceDepthLimit = EarthGeneratorSettings.SURFACE_DEPTH_LIMIT_CODEC.decode(ops, input);
```

Then, at the very end of the chain (after the existing `return settings.apply2(EarthGeneratorSettings::applyTrailRuins, trailRuins);`), change that line to:

```java
            settings = settings.apply2(EarthGeneratorSettings::applyTrailRuins, trailRuins);
            settings = settings.apply2(EarthGeneratorSettings::applySurfaceDepthLimitEnabled, surfaceDepthLimitEnabled);
            return settings.apply2(EarthGeneratorSettings::applySurfaceDepthLimit, surfaceDepthLimit);
```

In the second `keys` method on the decoder side (lines ~444–466), add the same two concat lines you added to the encoder side, at the same position.

- [ ] **Step 8: Add the two `apply*` private static helpers**

Search for `private static EarthGeneratorSettings applyTrailRuins`. Immediately after that method, add:

```java
   private static EarthGeneratorSettings applySurfaceDepthLimitEnabled(EarthGeneratorSettings settings, Boolean enabled) {
      return settings.withSurfaceDepthLimitEnabled(Objects.requireNonNull(enabled, "surfaceDepthLimitEnabled"));
   }

   private static EarthGeneratorSettings applySurfaceDepthLimit(EarthGeneratorSettings settings, Integer depth) {
      return settings.withSurfaceDepthLimit(Objects.requireNonNull(depth, "surfaceDepthLimit"));
   }
```

- [ ] **Step 9: Add the three `effective*` accessors**

Anywhere in the body of `EarthGeneratorSettings` (after the `with*` mutators is fine), add:

```java
   /** Cave generation effective at runtime — suppressed while the surface depth limit is on. */
   public boolean effectiveCaveGeneration() {
      return this.caveGeneration && !this.surfaceDepthLimitEnabled;
   }

   public boolean effectiveOreDistribution() {
      return this.oreDistribution && !this.surfaceDepthLimitEnabled;
   }

   public boolean effectiveLavaPools() {
      return this.lavaPools && !this.surfaceDepthLimitEnabled;
   }
```

- [ ] **Step 10: Repeat steps 3–9 for the other two files**

Apply identical edits to:
- `mc1201/src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java`
- `mc261/src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java`

The three files have nearly identical structure; line numbers may drift by ±5.

- [ ] **Step 11: Run the tests to verify they pass**

```
./gradlew :mc1211:test --tests com.yucareux.tellus.worldgen.EarthGeneratorSettingsSurfaceDepthLimitTest \
                       --tests com.yucareux.tellus.worldgen.EarthGeneratorSettingsCodecTest
```

Expected: the new tests pass. **The pre-existing `EarthGeneratorSettingsCodecTest::roundTripsCurrentSettingsPayload` may now fail** if it asserts on the exact encoded JSON: after this change, every encoded settings instance includes the two new keys. If that test fails:

- If it does a strict equality check on the full encoded JSON, add `"surface_depth_limit_enabled": false, "surface_depth_limit": 16` to its expected JSON literal.
- If it only asserts on specific fields, no edit needed.

Open the test file, read the assertions, and update the expected JSON literal in the same commit.

Then run the full test target to catch any other regressions:

```
./gradlew :mc1211:test :mc1201:test :mc261:test
```

Expected: all green.

- [ ] **Step 12: Commit**

```
git add mc1201/src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java \
        mc1211/src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java \
        mc261/src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java \
        src/test/java/com/yucareux/tellus/worldgen/EarthGeneratorSettingsSurfaceDepthLimitTest.java
git commit -m "feat(worldgen): add surface depth limit fields, codec, and effective accessors"
```

---

## Task 4: Switch worldgen consumers to `effective*` accessors

This swaps `settings.caveGeneration()` → `settings.effectiveCaveGeneration()` (and the ore/lava equivalents) at every **consumption** site so that turning on the limit suppresses cave/ore/lava features. UI binding sites and codec sites keep the raw accessors.

**Files (per MC version):**
- Modify: `mc1201/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java`
- Modify: `mc1211/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java`
- Modify: `mc261/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java`
- Modify: `mc1201/src/main/java/com/yucareux/tellus/worldgen/EarthBiomeSource.java`
- Modify: `src/main/java/com/yucareux/tellus/worldgen/EarthBiomeSource.java`

**Sites to change (from `grep -n` on a fresh checkout — confirm before editing):**

In each `EarthChunkGenerator.java`:
- L593: `if (!SharedConstants.DEBUG_DISABLE_CARVERS && this.settings.caveGeneration())` → `effectiveCaveGeneration()`
- L664: `if (this.settings.caveGeneration())` → `effectiveCaveGeneration()`
- L8076: `if (settings.caveGeneration())` → `effectiveCaveGeneration()`
- L8080: `if (settings.oreDistribution())` → `effectiveOreDistribution()`
- L8084: `if (settings.lavaPools())` → `effectiveLavaPools()`
- L8108: `return settings.caveGeneration() || !path.equals(...)` → `effectiveCaveGeneration()`
- L8118: `else if (!settings.oreDistribution() && ...)` → `effectiveOreDistribution()`
- L8123: `return settings.caveGeneration() || !path.contains(...)` → `effectiveCaveGeneration()`
- L8124: `? settings.lavaPools() || ...` → `effectiveLavaPools()`

In each `EarthBiomeSource.java` (only `mc1201` and shared `src/main` have one):
- L170: `if (!this.settings.caveGeneration())` → `effectiveCaveGeneration()`
- L276: `if (this.settings.caveGeneration())` → `effectiveCaveGeneration()`

- [ ] **Step 1: Write the failing test**

Append to `src/test/java/com/yucareux/tellus/worldgen/EarthGeneratorSettingsSurfaceDepthLimitTest.java`:

(Already covered by `effectiveAccessorsForceOffWhenLimitOn` in Task 3. No additional test needed for this task — the swap is a mechanical safety improvement and is validated by the existing test plus the manual verification in Task 7.)

Skip to Step 2.

- [ ] **Step 2: Apply the replacements**

For each file listed above, use a find-and-replace **with surrounding context** to avoid accidental hits in comments or future fields. Concretely, for an `EarthChunkGenerator.java`:

```
sed -i 's/this\.settings\.caveGeneration()/this.settings.effectiveCaveGeneration()/g' EarthChunkGenerator.java
sed -i 's/settings\.caveGeneration()/settings.effectiveCaveGeneration()/g' EarthChunkGenerator.java
sed -i 's/this\.settings\.oreDistribution()/this.settings.effectiveOreDistribution()/g' EarthChunkGenerator.java
sed -i 's/settings\.oreDistribution()/settings.effectiveOreDistribution()/g' EarthChunkGenerator.java
sed -i 's/this\.settings\.lavaPools()/this.settings.effectiveLavaPools()/g' EarthChunkGenerator.java
sed -i 's/settings\.lavaPools()/settings.effectiveLavaPools()/g' EarthChunkGenerator.java
```

(On Windows / PowerShell, do these via your editor's project find-and-replace scoped to the `mc*/src/main/java/com/yucareux/tellus/worldgen/` directories.)

Then run `git diff` on each file and confirm:
- All hits are inside conditionals or boolean expressions deciding *whether to emit a feature* (caves, ores, lava).
- Nothing inside `EarthGeneratorSettings.java` was touched (those calls are codec wiring / mutators, which must stay raw).
- Nothing inside a builder / `with*` chain was touched.

If you spot an unintended replacement, revert that one line and document why (e.g. a logging line, a settings serializer).

Repeat for `EarthBiomeSource.java` (lines 170, 276 in both copies).

- [ ] **Step 3: Compile and test**

```
./gradlew :mc1211:test :mc1201:test :mc261:test
```

Expected: all green. (The existing codec round-trip test ensures raw accessors weren't accidentally removed.)

- [ ] **Step 4: Commit**

```
git add mc1201/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java \
        mc1211/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java \
        mc261/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java \
        mc1201/src/main/java/com/yucareux/tellus/worldgen/EarthBiomeSource.java \
        src/main/java/com/yucareux/tellus/worldgen/EarthBiomeSource.java
git commit -m "feat(worldgen): consume effective cave/ore/lava accessors so depth limit suppresses them"
```

---

## Task 5: Add column-fill short-circuit in `EarthChunkGenerator`

This is the perf-saving change. When the limit is on, the per-column terrain fill starts at `cutoffY = surface - surfaceDepthLimit + 1` instead of `chunkMinY`, and the bulk solid-sections fill is skipped (those sections are far below the cutoff). Apply to all three MC versions.

**Files:**
- Modify: `mc1201/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java`
- Modify: `mc1211/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java`
- Modify: `mc261/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java`

**Reference: `mc1211/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java` lines 1018–1085** is the per-column fill block. The structure is:

```java
if (solidMaxIndex >= 0) {
   // bulk solid section fill from chunk floor up to a section boundary near minSurface
   fillSolidSections(...);
}
endFullChunkProfiling(...);

for (int localZ = 0; localZ < CHUNK_SIDE; localZ++) {
   for (int localX = 0; localX < CHUNK_SIDE; localX++) {
      ...
      int surface = terrainSurfaces[index];
      ...
      int y = chunkMinY;        // <-- column fill start
      ...
      if (mountainMassFill != null) {
         // fill from chunkMinY to surface with one block
         sectionWriter.fillColumnConstant(localX, localZ, chunkMinY, surface, mountainMassFill);
      } else {
         while (y <= surface) {
            // stone/deepslate span fill from y upward
         }
      }
      ...
   }
}
```

- [ ] **Step 1: Add the cutoff computation and clamp `y`**

In `EarthChunkGenerator.java`, locate the per-column loop (around line 1032 in mc1211). Inside the inner loop, immediately after `int surface = terrainSurfaces[index];` (around line 1039), add:

```java
               int columnFloorY;
               if (this.settings.surfaceDepthLimitEnabled()) {
                  columnFloorY = Math.max(chunkMinY, SurfaceDepthLimit.cutoffY(surface, this.settings.surfaceDepthLimit()));
               } else {
                  columnFloorY = chunkMinY;
               }
```

Then change every appearance of `chunkMinY` *inside this inner loop* that controls the **bottom of the column fill** to use `columnFloorY` instead. Specifically:

- `int y = chunkMinY;` (line ~1043) → `int y = columnFloorY;`
- `sectionWriter.fillColumnConstant(localX, localZ, chunkMinY, surface, mountainMassFill);` (line ~1055) → `sectionWriter.fillColumnConstant(localX, localZ, columnFloorY, surface, mountainMassFill);`
- `fillColumnConstant(sections, columnFilledSections, chunkMinY, localX, localZ, chunkMinY, surface, mountainMassFill);` (line ~1057) — only the **second** `chunkMinY` (the one that's the per-column starting Y, not the section-index reference). Result: `fillColumnConstant(sections, columnFilledSections, chunkMinY, localX, localZ, columnFloorY, surface, mountainMassFill);`

**Do NOT change**:
- `chunkMinY` references that compute section indices (e.g. `chunk.getSectionIndex(y)`).
- `chunkMinY` references passed to `fillStoneColumnSpan(... chunkMinY, ...)` *if* that parameter is used as the section-anchor (read `fillStoneColumnSpan`'s signature to confirm; the first `chunkMinY` argument is typically the section anchor and must stay as-is).
- `bedrockY` handling at line 1087 — bedrock stays where it is even when the limit is on. (It's a single block at `minAltitude` and is not the perf cost we care about.)

When in doubt, leave `chunkMinY` alone and add a comment `// section anchor — not the column fill floor`.

- [ ] **Step 2: Short-circuit the bulk solid-sections fill**

The bulk fill at lines ~1019–1030 fills entire 16-block-tall sections of stone for sections that are entirely below `minSurface`. When the limit is on, sections below `minSurface - surfaceDepthLimit` shouldn't be filled either. The simplest correct change: pass an effective `minSurface` into `resolveSolidSectionMaxIndex`. But that helper considers the whole chunk's min surface, and we don't want to recompute per-column here.

Wrap the bulk fill in a limit check. Around line 1021, change:

```java
         int solidMaxIndex = resolveSolidSectionMaxIndex(chunk, chunkMinY, minSurface, sectionCount);
         solidSectionProfiler.maxIndexNs += elapsedFullChunkProfilingSince(solidSectionsSubPhaseStartNs);
         if (solidMaxIndex >= 0) {
            // ... fillSolidSections call ...
         }
```

to:

```java
         int effectiveMinSurfaceForBulk;
         if (this.settings.surfaceDepthLimitEnabled()) {
            // Bulk-fill any section that lies entirely below the chunk's deepest cutoff line.
            effectiveMinSurfaceForBulk = SurfaceDepthLimit.cutoffY(minSurface, this.settings.surfaceDepthLimit()) - 1;
         } else {
            effectiveMinSurfaceForBulk = minSurface;
         }
         int solidMaxIndex = resolveSolidSectionMaxIndex(chunk, chunkMinY, effectiveMinSurfaceForBulk, sectionCount);
         solidSectionProfiler.maxIndexNs += elapsedFullChunkProfilingSince(solidSectionsSubPhaseStartNs);
         if (solidMaxIndex >= 0) {
            // ... unchanged ...
         }
```

The intent: the bulk fill only operates on sections that are entirely below the lowest cutoff in the chunk. Sections that straddle the cutoff get handled by the per-column loop (which already clamps to `columnFloorY`).

- [ ] **Step 3: Apply identical edits to mc1201 and mc261**

The per-column fill loop is structurally identical in all three files. Line numbers may differ by ±20. Locate by searching for `int y = chunkMinY;` followed by the `while (y <= surface)` block.

- [ ] **Step 4: Build all three**

```
./gradlew :mc1211:build :mc1201:build :mc261:build
```

Expected: all green.

(There is no easy unit test for the column-fill change because it requires constructing a full chunk-generation context. Manual verification is in Task 7.)

- [ ] **Step 5: Commit**

```
git add mc1201/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java \
        mc1211/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java \
        mc261/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java
git commit -m "perf(worldgen): clamp column fill to surface depth limit when enabled"
```

---

## Task 6: Wire UI into `EarthCustomizeScreen`

Add a toggle and a slider under the existing **Geological Settings** category. Apply to all three MC versions.

**Files:**
- Modify: `mc1201/src/client/java/com/yucareux/tellus/client/screen/EarthCustomizeScreen.java`
- Modify: `mc1211/src/client/java/com/yucareux/tellus/client/screen/EarthCustomizeScreen.java`
- Modify: `mc261/src/client/java/com/yucareux/tellus/client/screen/EarthCustomizeScreen.java`

Reference structure (`mc1211/src/client/java/com/yucareux/tellus/client/screen/EarthCustomizeScreen.java`):
- Line ~828–836: `geological` category definition, currently three `toggle(...)` calls.
- Line ~398–400: `findToggleValue` extraction (used when reading from screen back to settings).
- Line ~544–546: `setToggleValue` initialization (used when populating screen from settings).

- [ ] **Step 1: Add the two rows to the `geological` category**

Locate the `geological` category (search for `"geological"`). Change the inner list to:

```java
            List.of(
               toggle("surface_depth_limit_enabled", EarthGeneratorSettings.DEFAULT.surfaceDepthLimitEnabled()),
               slider("surface_depth_limit",
                  EarthGeneratorSettings.DEFAULT.surfaceDepthLimit(),
                  SurfaceDepthLimit.MIN_DEPTH,
                  SurfaceDepthLimit.MAX_DEPTH,
                  1.0),
               toggle("cave_generation", EarthGeneratorSettings.DEFAULT.caveGeneration()),
               toggle("ore_distribution", EarthGeneratorSettings.DEFAULT.oreDistribution()),
               toggle("lava_pools", EarthGeneratorSettings.DEFAULT.lavaPools())
            )
```

(If the `slider(...)` factory in this file has a different signature, match it; the four positional args mean default / min / max / step. Look at the nearby `slider("min_altitude", ..., -2048.0, 2031.0, 16.0)` call for the exact signature.)

Add the matching `import com.yucareux.tellus.worldgen.SurfaceDepthLimit;` at the top of the file if it isn't already present.

- [ ] **Step 2: Add extract calls**

Locate the block around lines ~398–400 that reads:

```java
      boolean caveGeneration = this.findToggleValue("cave_generation", EarthGeneratorSettings.DEFAULT.caveGeneration());
      boolean oreDistribution = this.findToggleValue("ore_distribution", EarthGeneratorSettings.DEFAULT.oreDistribution());
      boolean lavaPools = this.findToggleValue("lava_pools", EarthGeneratorSettings.DEFAULT.lavaPools());
```

Immediately above those, add:

```java
      boolean surfaceDepthLimitEnabled = this.findToggleValue("surface_depth_limit_enabled", EarthGeneratorSettings.DEFAULT.surfaceDepthLimitEnabled());
      int surfaceDepthLimit = (int) this.findSliderValue("surface_depth_limit", EarthGeneratorSettings.DEFAULT.surfaceDepthLimit());
```

(If the slider value getter is named differently in this file — e.g. `findNumericValue` or `findIntSliderValue` — match the existing pattern used for `min_altitude`. Look for `findSliderValue(...)` or any `(int) this.find...` cast in nearby code.)

Then find the `new EarthGeneratorSettings(...)` constructor call later in the same method (this is how the screen returns its settings instance). Append the two new arguments at the end of the argument list, matching the order of constructor parameters:

```java
      ..., enableRoads, enableBuildings, enableWater,
      surfaceDepthLimitEnabled, surfaceDepthLimit
```

- [ ] **Step 3: Add init calls**

Locate the block around lines ~544–546:

```java
      this.setToggleValue("cave_generation", initialSettings.caveGeneration());
      this.setToggleValue("ore_distribution", initialSettings.oreDistribution());
      this.setToggleValue("lava_pools", initialSettings.lavaPools());
```

Immediately above those, add:

```java
      this.setToggleValue("surface_depth_limit_enabled", initialSettings.surfaceDepthLimitEnabled());
      this.setSliderValue("surface_depth_limit", initialSettings.surfaceDepthLimit());
```

(Again, match the existing pattern. The setter for `min_altitude` will show the right method name.)

- [ ] **Step 4: Repeat for mc1201 and mc261**

The three screen files share the same structure; line numbers may differ.

- [ ] **Step 5: Build**

```
./gradlew :mc1211:build :mc1201:build :mc261:build
```

Expected: green.

- [ ] **Step 6: Commit**

```
git add mc1201/src/client/java/com/yucareux/tellus/client/screen/EarthCustomizeScreen.java \
        mc1211/src/client/java/com/yucareux/tellus/client/screen/EarthCustomizeScreen.java \
        mc261/src/client/java/com/yucareux/tellus/client/screen/EarthCustomizeScreen.java
git commit -m "feat(ui): add Surface Depth Limit toggle and slider to Geological Settings"
```

---

## Task 7: Manual verification in-client

There is no automated test for the rendered world. Validate by running the client and creating a world.

- [ ] **Step 1: Launch the 1.21.1 client**

```
./gradlew runClient1211
```

(Or `runClient1201` / `runClient261` per preference. 1.21.1 is the most-tested version.)

- [ ] **Step 2: Create a new Tellus world with the limit off**

- Click *Singleplayer* → *Create New World* → *More* → choose Tellus world type → *Customize World Generation*.
- Scroll to **Geological Settings**. Confirm the two new rows appear at the top: **Surface Depth Limit** (toggle, off) and **Surface Depth (blocks)** (slider, 16).
- Leave both at default. Create the world.
- Once in-world, dig straight down with `/setblock ~ ~-1 ~ air` repeatedly or use creative-mode digging. Confirm terrain extends hundreds of blocks downward as before.
- Quit to title.

- [ ] **Step 3: Create a new Tellus world with the limit on**

- New world; same screen; turn on **Surface Depth Limit**, set **Surface Depth** to 8.
- Confirm: Cave Generation, Ore Distribution, Lava Pools toggles remain visible (they still show their stored values). Their effective behavior is suppressed, which we'll verify in-world.
- Create the world.
- In-world, dig down. Confirm:
  - Only ~8 blocks of stone below the surface, then air.
  - No caves intersecting your dig.
  - No ores in the 8-block shell (or very few, since `effectiveOreDistribution` is `false`).
  - You can fall freely below the shell — there is no bedrock floor at the cutoff (by design; the existing bedrock at `minAltitude` is still present).
- Use `/tp ~ ~-200 ~` to confirm the region below the shell is air.
- Quit, save, reopen the world. Confirm the limit is still active (codec round-trip works end-to-end).

- [ ] **Step 4: Test with a previously-saved (pre-feature) world**

If you have any older Tellus save lying around (from before this feature was added), copy it to the saves folder and open it. The world should load fine and generate as before — the missing JSON keys default to `enabled=false, depth=16`.

If you don't have a pre-feature save, simulate one by opening `level.dat` in NBT inspector and deleting the `surface_depth_limit_enabled` / `surface_depth_limit` keys from the generator settings; reload and confirm the world still loads.

- [ ] **Step 5: Commit the test-mode finding notes (only if anything surprising surfaces)**

If the manual test reveals an issue, file it as a follow-up in the project tracker and fix in a separate commit on this branch. If everything passes, no commit needed for this step.

---

## Self-Review Notes

The spec section "Generator behavior" calls out three things to verify in code:

1. ✅ "Skip block placement for any `y < cutoffY`" — Task 5 Step 1.
2. ✅ "Same rule under oceans" — Task 5's change applies to *all* columns regardless of ocean/land classification (the `mountainMassFill` and stone-span branches both use `columnFloorY`).
3. ✅ "Skip cave/ore/lava feature placement entirely when shell mode active" — Task 4 swaps all consumption sites to `effective*` accessors.

The spec section "Auto-disable of geological features" was revised during spec review to use `effective*` accessors instead of constructor mutation. Tasks 3 and 4 implement that revised approach. Raw stored values are preserved (verified by `effectiveAccessorsForceOffWhenLimitOn` test).

Spec section "Codec compatibility" — old saves missing the new keys → defaults applied. Verified by `codecDecodesOldPayloadWithoutNewFields` test in Task 3 and Step 4 of Task 7.

### Deferred from spec

The spec described a UI affordance: when **Surface Depth Limit** is on, grey out the Cave Generation / Ore Distribution / Lava Pools rows with a tooltip `"Disabled while Surface Depth Limit is active."` This plan **does not** implement the grey-out. The effective-suppression behavior is correct (Task 4 ensures the toggles have no runtime effect when the limit is on), but the user must read the **Surface Depth Limit** tooltip to learn why their cave toggle "isn't working."

Reason for deferral: live cross-row state in `EarthCustomizeScreen` requires wiring change-listeners between rows, which is materially more code than the rest of this feature. Worth a follow-up issue but not blocking.
