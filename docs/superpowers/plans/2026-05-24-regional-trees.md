# Regional Trees Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace vanilla tree placement in already-forested biomes with country/climate-keyed palettes of NBT-defined trees. Canada gets a real implementation; USA/Russia/Brazil/China/Australia get data + class stubs; everywhere else falls back to vanilla.

**Architecture:** A shared (version-agnostic) data layer in `src/main/java` loads JSON palettes/species/mappings from classpath and answers `palette(lat, lon, koppen, esa)`. A per-version `RegionalTreePlacer` in each mc* subproject hooks into `EarthChunkGenerator`'s existing per-cell tree placement: when a palette applies it places NBT structures; otherwise the existing vanilla path runs unchanged. The locked `trees_density` UI slider is unlocked and read as a global multiplier.

**Tech Stack:** Java 21, Fabric (mc1201=1.20.1, mc1211=1.21.1, mc261=1.21.5), Gson for JSON, JUnit 5 for tests.

---

## File Structure

### New files (shared — `src/main/java/com/yucareux/tellus/worldgen/trees/`)
- `TreeSpeciesDef.java` — record `{ String id; ResourceLocationLite nbt; boolean rotate; boolean mirror; String saplingId; }`
- `TreePaletteDef.java` — record `{ String id; double treesPerChunk; List<WeightedSpecies> species; }` with `pick(RandomSource)` helper
- `TreePaletteDef.WeightedSpecies` — record `{ String speciesId; double weight; }`
- `CountryTreeMapping.java` — record `{ String country; List<Rule> rules; String fallbackPaletteId; }` with `resolve(koppen, esa)` returning a palette id; nested `Rule` record
- `TreeDataLoader.java` — static loader: reads all JSON under `data/tellus/tree_species/**`, `data/tellus/tree_palette/**`, `data/tellus/tree_mapping/**` at first use; reports validation errors via log; caches results
- `TreePaletteRegistry.java` — front door: `palette(String id)`, `species(String id)`, both backed by `TreeDataLoader`. Exposes the sentinel id `tellus:vanilla_default`.
- `CountryContainment.java` — interface `{ String code(); boolean contains(double lat, double lon); }`
- `CountryRegistry.java` — registers `CountryContainment` instances in deterministic order; `palette(double lat, double lon, String koppen, String esa)` returns a palette id (always non-null — falls back to `tellus:vanilla_default`)
- `containment/CanadaContainment.java` — wraps `CanElevationCoverageIndex.containsCanada`
- `containment/StubCountryContainment.java` — shared stub: `contains()` always returns false; used by USA/Russia/Brazil/China/Australia
- `containment/CountryContainments.java` — utility that registers Canada + the five stubs into `CountryRegistry`

### Modified files (shared)
- `src/main/java/com/yucareux/tellus/world/data/elevation/CanElevationCoverageIndex.java` — make `containsCanada(lat, lon)` public (currently package-private) so `CanadaContainment` can call it from a different package
- `src/main/resources/assets/tellus/lang/en_us.json` — append placeholder-removal note tooltip for `trees_density` (the lock is removed; tooltip text already exists)

### New resource files (shared — `src/main/resources/data/tellus/`)
- `tree_mapping/canada.json` — Canada mapping with `Dfc/TreeCover → canadian_boreal`, `Dfb/TreeCover → canadian_mixed_wood`, fallback `tellus:vanilla_default`
- `tree_mapping/usa.json`, `russia.json`, `brazil.json`, `china.json`, `australia.json` — empty rules, fallback `tellus:vanilla_default`
- `tree_palette/canadian_boreal.json` — palette listing black_spruce, jack_pine, paper_birch with weights, `trees_per_chunk: 9`
- `tree_palette/canadian_mixed_wood.json` — palette listing balsam_fir, sugar_maple, paper_birch, black_spruce with weights, `trees_per_chunk: 9`
- `tree_species/canadian/black_spruce.json`, `jack_pine.json`, `paper_birch.json`, `balsam_fir.json`, `tamarack.json`, `sugar_maple.json` — each points at an NBT under `tellus:tree/canadian/<name>` (NBTs are not part of this plan)

### New test files (shared — `src/test/java/com/yucareux/tellus/worldgen/trees/`)
- `TreePaletteDefTest.java`
- `CountryTreeMappingTest.java`
- `TreeDataLoaderTest.java`
- `CountryRegistryTest.java`

### Per-version modifications (each of `mc1211/`, `mc1201/`, `mc261/`)
- `src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java` — add `double treeDensity` field + codec entry + default `1.0` + `effectiveTreeDensity()` accessor
- `src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java` — at the existing tree-placement site (around `treeFeaturesForBiome` call), short-circuit to `RegionalTreePlacer` when palette applies
- `src/main/java/com/yucareux/tellus/worldgen/trees/RegionalTreePlacer.java` — **new**, version-specific: holds `EarthProjection`, `koppen` source, `cover` source; `tryPlace(level, settings, worldX, worldZ, ground, random)` returns true if it placed an NBT (so chunk gen skips vanilla feature for that position)
- `src/client/java/com/yucareux/tellus/client/screen/EarthCustomizeScreen.java` — remove `.locked(true)` from the `trees_density` slider; wire the slider value into the settings codec

### New per-version test files
- `mc<ver>/src/test/java/com/yucareux/tellus/worldgen/EarthGeneratorSettingsTreeDensityTest.java` (each version)

---

## Phase 1 — Shared data layer

All Phase 1 work happens on the `feature/regional-trees` branch off `main`.

### Task 1: TreeSpeciesDef record + JSON parsing

**Files:**
- Create: `src/main/java/com/yucareux/tellus/worldgen/trees/TreeSpeciesDef.java`
- Test: `src/test/java/com/yucareux/tellus/worldgen/trees/TreeSpeciesDefTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/yucareux/tellus/worldgen/trees/TreeSpeciesDefTest.java
package com.yucareux.tellus.worldgen.trees;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class TreeSpeciesDefTest {
    @Test
    void parsesAllFields() {
        String json = """
            { "nbt": "tellus:tree/canadian/black_spruce_01",
              "rotate": true,
              "mirror": false,
              "sapling": "minecraft:spruce_sapling" }
            """;
        TreeSpeciesDef def = TreeSpeciesDef.fromJson(
            "tellus:canadian/black_spruce", JsonParser.parseString(json).getAsJsonObject());

        assertEquals("tellus:canadian/black_spruce", def.id());
        assertEquals("tellus:tree/canadian/black_spruce_01", def.nbt());
        assertTrue(def.rotate());
        assertFalse(def.mirror());
        assertEquals("minecraft:spruce_sapling", def.sapling());
    }

    @Test
    void defaultsApplyWhenFieldsOmitted() {
        String json = """{ "nbt": "tellus:tree/x" }""";
        TreeSpeciesDef def = TreeSpeciesDef.fromJson(
            "tellus:x", JsonParser.parseString(json).getAsJsonObject());

        assertTrue(def.rotate(), "rotate defaults to true");
        assertFalse(def.mirror(), "mirror defaults to false");
        assertNull(def.sapling(), "sapling defaults to null");
    }

    @Test
    void missingNbtThrows() {
        String json = "{}";
        assertThrows(IllegalArgumentException.class, () ->
            TreeSpeciesDef.fromJson("tellus:x", JsonParser.parseString(json).getAsJsonObject()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :test --tests "*TreeSpeciesDefTest*"` (note: shared tests live in the root `src/test`; if the root project has no `test` task, run with `./gradlew :mc1211:test --tests "*TreeSpeciesDefTest*"` after adding the test source set — verify which works in the repo before continuing).
Expected: FAIL — class does not exist.

- [ ] **Step 3: Create the record**

```java
// src/main/java/com/yucareux/tellus/worldgen/trees/TreeSpeciesDef.java
package com.yucareux.tellus.worldgen.trees;

import com.google.gson.JsonObject;
import java.util.Objects;

public record TreeSpeciesDef(String id, String nbt, boolean rotate, boolean mirror, String sapling) {

    public TreeSpeciesDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nbt, "nbt");
    }

    public static TreeSpeciesDef fromJson(String id, JsonObject json) {
        if (!json.has("nbt")) {
            throw new IllegalArgumentException("tree species '" + id + "' missing required field 'nbt'");
        }
        String nbt = json.get("nbt").getAsString();
        boolean rotate = !json.has("rotate") || json.get("rotate").getAsBoolean();
        boolean mirror = json.has("mirror") && json.get("mirror").getAsBoolean();
        String sapling = json.has("sapling") ? json.get("sapling").getAsString() : null;
        return new TreeSpeciesDef(id, nbt, rotate, mirror, sapling);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :test --tests "*TreeSpeciesDefTest*"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yucareux/tellus/worldgen/trees/TreeSpeciesDef.java \
        src/test/java/com/yucareux/tellus/worldgen/trees/TreeSpeciesDefTest.java
git commit -m "feat(trees): add TreeSpeciesDef record with JSON parsing"
```

---

### Task 2: TreePaletteDef record + weighted pick

**Files:**
- Create: `src/main/java/com/yucareux/tellus/worldgen/trees/TreePaletteDef.java`
- Test: `src/test/java/com/yucareux/tellus/worldgen/trees/TreePaletteDefTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/yucareux/tellus/worldgen/trees/TreePaletteDefTest.java
package com.yucareux.tellus.worldgen.trees;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TreePaletteDefTest {
    @Test
    void parsesPaletteWithWeightedSpecies() {
        String json = """
            { "trees_per_chunk": 9,
              "species": [
                { "id": "tellus:canadian/black_spruce", "weight": 50 },
                { "id": "tellus:canadian/jack_pine",   "weight": 30 },
                { "id": "tellus:canadian/paper_birch", "weight": 20 }
              ] }
            """;
        TreePaletteDef def = TreePaletteDef.fromJson(
            "tellus:canadian_boreal", JsonParser.parseString(json).getAsJsonObject());

        assertEquals("tellus:canadian_boreal", def.id());
        assertEquals(9.0, def.treesPerChunk(), 0.0001);
        assertEquals(3, def.species().size());
        assertEquals("tellus:canadian/black_spruce", def.species().get(0).speciesId());
        assertEquals(50.0, def.species().get(0).weight(), 0.0001);
    }

    @Test
    void weightedPickRoughlyMatchesWeights() {
        String json = """
            { "trees_per_chunk": 1,
              "species": [
                { "id": "a", "weight": 70 },
                { "id": "b", "weight": 30 }
              ] }
            """;
        TreePaletteDef def = TreePaletteDef.fromJson(
            "tellus:test", JsonParser.parseString(json).getAsJsonObject());

        Random rng = new Random(42L);
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 10_000; i++) {
            counts.merge(def.pick(rng).speciesId(), 1, Integer::sum);
        }
        assertTrue(counts.getOrDefault("a", 0) > 6500 && counts.get("a") < 7500,
            "expected ~7000 picks of 'a', got " + counts.get("a"));
        assertTrue(counts.getOrDefault("b", 0) > 2500 && counts.get("b") < 3500,
            "expected ~3000 picks of 'b', got " + counts.get("b"));
    }

    @Test
    void missingSpeciesArrayThrows() {
        String json = """{ "trees_per_chunk": 5 }""";
        assertThrows(IllegalArgumentException.class, () ->
            TreePaletteDef.fromJson("tellus:x", JsonParser.parseString(json).getAsJsonObject()));
    }

    @Test
    void zeroOrNegativeWeightRejected() {
        String json = """
            { "trees_per_chunk": 1,
              "species": [ { "id": "a", "weight": 0 } ] }
            """;
        assertThrows(IllegalArgumentException.class, () ->
            TreePaletteDef.fromJson("tellus:x", JsonParser.parseString(json).getAsJsonObject()));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :test --tests "*TreePaletteDefTest*"`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Create the record**

```java
// src/main/java/com/yucareux/tellus/worldgen/trees/TreePaletteDef.java
package com.yucareux.tellus.worldgen.trees;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public record TreePaletteDef(String id, double treesPerChunk, List<WeightedSpecies> species, double totalWeight) {

    public TreePaletteDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(species, "species");
        species = List.copyOf(species);
    }

    public static TreePaletteDef fromJson(String id, JsonObject json) {
        double treesPerChunk = json.has("trees_per_chunk") ? json.get("trees_per_chunk").getAsDouble() : 0.0;
        if (!json.has("species") || !json.get("species").isJsonArray()) {
            throw new IllegalArgumentException("palette '" + id + "' missing required array 'species'");
        }
        JsonArray arr = json.getAsJsonArray("species");
        List<WeightedSpecies> entries = new ArrayList<>(arr.size());
        double total = 0.0;
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            String speciesId = obj.get("id").getAsString();
            double weight = obj.has("weight") ? obj.get("weight").getAsDouble() : 1.0;
            if (weight <= 0.0) {
                throw new IllegalArgumentException(
                    "palette '" + id + "' has non-positive weight for species '" + speciesId + "'");
            }
            entries.add(new WeightedSpecies(speciesId, weight));
            total += weight;
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("palette '" + id + "' has empty species list");
        }
        return new TreePaletteDef(id, treesPerChunk, entries, total);
    }

    public WeightedSpecies pick(Random rng) {
        double r = rng.nextDouble() * totalWeight;
        double acc = 0.0;
        for (WeightedSpecies s : species) {
            acc += s.weight();
            if (r < acc) {
                return s;
            }
        }
        return species.get(species.size() - 1);
    }

    public record WeightedSpecies(String speciesId, double weight) {}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :test --tests "*TreePaletteDefTest*"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yucareux/tellus/worldgen/trees/TreePaletteDef.java \
        src/test/java/com/yucareux/tellus/worldgen/trees/TreePaletteDefTest.java
git commit -m "feat(trees): add TreePaletteDef with weighted species pick"
```

---

### Task 3: CountryTreeMapping record + rule resolution

**Files:**
- Create: `src/main/java/com/yucareux/tellus/worldgen/trees/CountryTreeMapping.java`
- Test: `src/test/java/com/yucareux/tellus/worldgen/trees/CountryTreeMappingTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/yucareux/tellus/worldgen/trees/CountryTreeMappingTest.java
package com.yucareux.tellus.worldgen.trees;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class CountryTreeMappingTest {
    @Test
    void parsesMappingWithRules() {
        String json = """
            { "country": "canada",
              "rules": [
                { "koppen": "Dfc", "esa": "TreeCover", "palette": "tellus:canadian_boreal" },
                { "koppen": "Dfb", "esa": "TreeCover", "palette": "tellus:canadian_mixed_wood" }
              ],
              "fallback": "tellus:vanilla_default" }
            """;
        CountryTreeMapping m = CountryTreeMapping.fromJson(JsonParser.parseString(json).getAsJsonObject());

        assertEquals("canada", m.country());
        assertEquals("tellus:vanilla_default", m.fallbackPaletteId());
        assertEquals(2, m.rules().size());
    }

    @Test
    void resolveReturnsFirstMatchingRule() {
        String json = """
            { "country": "canada",
              "rules": [
                { "koppen": "Dfc", "esa": "TreeCover", "palette": "tellus:canadian_boreal" },
                { "koppen": "Dfb", "esa": "TreeCover", "palette": "tellus:canadian_mixed_wood" }
              ],
              "fallback": "tellus:vanilla_default" }
            """;
        CountryTreeMapping m = CountryTreeMapping.fromJson(JsonParser.parseString(json).getAsJsonObject());

        assertEquals("tellus:canadian_boreal", m.resolve("Dfc", "TreeCover"));
        assertEquals("tellus:canadian_mixed_wood", m.resolve("Dfb", "TreeCover"));
    }

    @Test
    void resolveReturnsFallbackWhenNoMatch() {
        String json = """
            { "country": "canada", "rules": [], "fallback": "tellus:vanilla_default" }
            """;
        CountryTreeMapping m = CountryTreeMapping.fromJson(JsonParser.parseString(json).getAsJsonObject());

        assertEquals("tellus:vanilla_default", m.resolve("ET", "Grassland"));
    }

    @Test
    void missingFallbackThrows() {
        String json = """{ "country": "x", "rules": [] }""";
        assertThrows(IllegalArgumentException.class, () ->
            CountryTreeMapping.fromJson(JsonParser.parseString(json).getAsJsonObject()));
    }

    @Test
    void missingCountryThrows() {
        String json = """{ "rules": [], "fallback": "tellus:vanilla_default" }""";
        assertThrows(IllegalArgumentException.class, () ->
            CountryTreeMapping.fromJson(JsonParser.parseString(json).getAsJsonObject()));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :test --tests "*CountryTreeMappingTest*"`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Create the record**

```java
// src/main/java/com/yucareux/tellus/worldgen/trees/CountryTreeMapping.java
package com.yucareux.tellus.worldgen.trees;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record CountryTreeMapping(String country, List<Rule> rules, String fallbackPaletteId) {

    public CountryTreeMapping {
        Objects.requireNonNull(country, "country");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(fallbackPaletteId, "fallbackPaletteId");
        rules = List.copyOf(rules);
    }

    public static CountryTreeMapping fromJson(JsonObject json) {
        if (!json.has("country")) {
            throw new IllegalArgumentException("tree mapping missing required field 'country'");
        }
        if (!json.has("fallback")) {
            throw new IllegalArgumentException("tree mapping missing required field 'fallback'");
        }
        String country = json.get("country").getAsString();
        String fallback = json.get("fallback").getAsString();
        List<Rule> rules = new ArrayList<>();
        if (json.has("rules") && json.get("rules").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("rules");
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                rules.add(new Rule(
                    obj.get("koppen").getAsString(),
                    obj.get("esa").getAsString(),
                    obj.get("palette").getAsString()
                ));
            }
        }
        return new CountryTreeMapping(country, rules, fallback);
    }

    public String resolve(String koppen, String esa) {
        for (Rule r : rules) {
            if (r.koppen().equals(koppen) && r.esa().equals(esa)) {
                return r.paletteId();
            }
        }
        return fallbackPaletteId;
    }

    public record Rule(String koppen, String esa, String paletteId) {}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :test --tests "*CountryTreeMappingTest*"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yucareux/tellus/worldgen/trees/CountryTreeMapping.java \
        src/test/java/com/yucareux/tellus/worldgen/trees/CountryTreeMappingTest.java
git commit -m "feat(trees): add CountryTreeMapping with rule resolution"
```

---

### Task 4: TreeDataLoader — classpath scan + registry

**Files:**
- Create: `src/main/java/com/yucareux/tellus/worldgen/trees/TreeDataLoader.java`
- Create: `src/main/java/com/yucareux/tellus/worldgen/trees/TreePaletteRegistry.java`
- Test: `src/test/java/com/yucareux/tellus/worldgen/trees/TreeDataLoaderTest.java`
- Create test fixtures under: `src/test/resources/data/tellus/tree_species/test/spruce.json`, `tree_palette/test_boreal.json`, `tree_mapping/testland.json`

- [ ] **Step 1: Create test fixture files**

```json
// src/test/resources/data/tellus/tree_species/test/spruce.json
{ "nbt": "tellus:tree/test/spruce_01", "rotate": true, "mirror": false,
  "sapling": "minecraft:spruce_sapling" }
```

```json
// src/test/resources/data/tellus/tree_palette/test_boreal.json
{ "trees_per_chunk": 9,
  "species": [ { "id": "tellus:test/spruce", "weight": 100 } ] }
```

```json
// src/test/resources/data/tellus/tree_mapping/testland.json
{ "country": "testland",
  "rules": [ { "koppen": "Dfc", "esa": "TreeCover", "palette": "tellus:test_boreal" } ],
  "fallback": "tellus:vanilla_default" }
```

- [ ] **Step 2: Write the failing tests**

```java
// src/test/java/com/yucareux/tellus/worldgen/trees/TreeDataLoaderTest.java
package com.yucareux.tellus.worldgen.trees;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TreeDataLoaderTest {
    @Test
    void loadsFixtureSpecies() {
        TreeDataLoader.LoadedData data = TreeDataLoader.loadFromClasspath();
        TreeSpeciesDef spruce = data.species().get("tellus:test/spruce");
        assertNotNull(spruce, "fixture species 'tellus:test/spruce' should load");
        assertEquals("tellus:tree/test/spruce_01", spruce.nbt());
    }

    @Test
    void loadsFixturePalette() {
        TreeDataLoader.LoadedData data = TreeDataLoader.loadFromClasspath();
        TreePaletteDef pal = data.palettes().get("tellus:test_boreal");
        assertNotNull(pal);
        assertEquals(9.0, pal.treesPerChunk(), 0.0001);
    }

    @Test
    void loadsFixtureMapping() {
        TreeDataLoader.LoadedData data = TreeDataLoader.loadFromClasspath();
        CountryTreeMapping m = data.mappings().get("testland");
        assertNotNull(m);
        assertEquals("tellus:test_boreal", m.resolve("Dfc", "TreeCover"));
    }

    @Test
    void vanillaDefaultPaletteAvailableEvenWithoutFile() {
        TreeDataLoader.LoadedData data = TreeDataLoader.loadFromClasspath();
        assertEquals(TreePaletteRegistry.VANILLA_DEFAULT_ID, "tellus:vanilla_default");
        // VANILLA_DEFAULT is a runtime sentinel; loader does not need a JSON for it.
        // Registry has a separate isVanillaDefault() check.
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :test --tests "*TreeDataLoaderTest*"`
Expected: FAIL — classes do not exist.

- [ ] **Step 4: Create TreeDataLoader**

```java
// src/main/java/com/yucareux/tellus/worldgen/trees/TreeDataLoader.java
package com.yucareux.tellus.worldgen.trees;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class TreeDataLoader {

    private static final Logger LOG = Logger.getLogger(TreeDataLoader.class.getName());
    private static final String ROOT = "data/tellus";
    private static volatile LoadedData cached;

    private TreeDataLoader() {}

    public static LoadedData loadFromClasspath() {
        LoadedData local = cached;
        if (local != null) return local;
        synchronized (TreeDataLoader.class) {
            if (cached != null) return cached;
            cached = scan();
            return cached;
        }
    }

    static void resetForTesting() {
        cached = null;
    }

    private static LoadedData scan() {
        Map<String, TreeSpeciesDef> species = new HashMap<>();
        Map<String, TreePaletteDef> palettes = new HashMap<>();
        Map<String, CountryTreeMapping> mappings = new HashMap<>();

        forEachJson("tree_species", (relPath, json) -> {
            // relPath like "canadian/black_spruce.json"
            String id = "tellus:" + relPath.substring(0, relPath.length() - ".json".length());
            try {
                species.put(id, TreeSpeciesDef.fromJson(id, json));
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "failed to load species " + id, e);
            }
        });

        forEachJson("tree_palette", (relPath, json) -> {
            String id = "tellus:" + relPath.substring(0, relPath.length() - ".json".length());
            try {
                palettes.put(id, TreePaletteDef.fromJson(id, json));
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "failed to load palette " + id, e);
            }
        });

        forEachJson("tree_mapping", (relPath, json) -> {
            try {
                CountryTreeMapping m = CountryTreeMapping.fromJson(json);
                mappings.put(m.country(), m);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "failed to load mapping " + relPath, e);
            }
        });

        return new LoadedData(
            Collections.unmodifiableMap(species),
            Collections.unmodifiableMap(palettes),
            Collections.unmodifiableMap(mappings));
    }

    @FunctionalInterface
    private interface JsonVisitor {
        void visit(String relPath, JsonObject json);
    }

    private static void forEachJson(String subdir, JsonVisitor visitor) {
        ClassLoader cl = TreeDataLoader.class.getClassLoader();
        String root = ROOT + "/" + subdir;
        try {
            Enumeration<URL> roots = cl.getResources(root);
            while (roots.hasMoreElements()) {
                URL url = roots.nextElement();
                URI uri = url.toURI();
                Path base;
                FileSystem fs = null;
                if ("jar".equals(uri.getScheme())) {
                    fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                    base = fs.getPath("/" + root);
                } else {
                    base = Paths.get(uri);
                }
                try (Stream<Path> walk = Files.walk(base)) {
                    walk.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                        String rel = base.relativize(p).toString().replace('\\', '/');
                        try (InputStream in = Files.newInputStream(p);
                             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                            JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
                            visitor.visit(rel, json);
                        } catch (IOException e) {
                            LOG.log(Level.WARNING, "failed reading " + p, e);
                        }
                    });
                } finally {
                    if (fs != null) fs.close();
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "failed scanning " + root, e);
        }
    }

    public record LoadedData(
        Map<String, TreeSpeciesDef> species,
        Map<String, TreePaletteDef> palettes,
        Map<String, CountryTreeMapping> mappings
    ) {}
}
```

- [ ] **Step 5: Create TreePaletteRegistry**

```java
// src/main/java/com/yucareux/tellus/worldgen/trees/TreePaletteRegistry.java
package com.yucareux.tellus.worldgen.trees;

public final class TreePaletteRegistry {

    public static final String VANILLA_DEFAULT_ID = "tellus:vanilla_default";

    private TreePaletteRegistry() {}

    public static boolean isVanillaDefault(String paletteId) {
        return VANILLA_DEFAULT_ID.equals(paletteId);
    }

    public static TreePaletteDef palette(String id) {
        return TreeDataLoader.loadFromClasspath().palettes().get(id);
    }

    public static TreeSpeciesDef species(String id) {
        return TreeDataLoader.loadFromClasspath().species().get(id);
    }

    public static CountryTreeMapping mapping(String countryCode) {
        return TreeDataLoader.loadFromClasspath().mappings().get(countryCode);
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :test --tests "*TreeDataLoaderTest*"`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/yucareux/tellus/worldgen/trees/TreeDataLoader.java \
        src/main/java/com/yucareux/tellus/worldgen/trees/TreePaletteRegistry.java \
        src/test/java/com/yucareux/tellus/worldgen/trees/TreeDataLoaderTest.java \
        src/test/resources/data/tellus/
git commit -m "feat(trees): classpath loader + palette registry"
```

---

### Task 5: CountryContainment + CountryRegistry

**Files:**
- Create: `src/main/java/com/yucareux/tellus/worldgen/trees/CountryContainment.java`
- Create: `src/main/java/com/yucareux/tellus/worldgen/trees/CountryRegistry.java`
- Test: `src/test/java/com/yucareux/tellus/worldgen/trees/CountryRegistryTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/yucareux/tellus/worldgen/trees/CountryRegistryTest.java
package com.yucareux.tellus.worldgen.trees;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CountryRegistryTest {

    private static CountryContainment always(String code, boolean value) {
        return new CountryContainment() {
            @Override public String code() { return code; }
            @Override public boolean contains(double lat, double lon) { return value; }
        };
    }

    @Test
    void emptyRegistryReturnsVanillaDefault() {
        CountryRegistry reg = new CountryRegistry();
        assertEquals(TreePaletteRegistry.VANILLA_DEFAULT_ID,
            reg.palette(45.0, -75.0, "Dfb", "TreeCover"));
    }

    @Test
    void registeredCountryWithoutMappingReturnsVanillaDefault() {
        CountryRegistry reg = new CountryRegistry();
        reg.register(always("nowhere", true));
        // no mapping JSON exists for "nowhere"
        assertEquals(TreePaletteRegistry.VANILLA_DEFAULT_ID,
            reg.palette(0.0, 0.0, "Dfb", "TreeCover"));
    }

    @Test
    void fixtureMappingResolves() {
        CountryRegistry reg = new CountryRegistry();
        reg.register(always("testland", true));
        // testland mapping (loaded from test fixtures) returns canadian_boreal for Dfc/TreeCover
        assertEquals("tellus:test_boreal",
            reg.palette(0.0, 0.0, "Dfc", "TreeCover"));
    }

    @Test
    void firstHitWins() {
        CountryRegistry reg = new CountryRegistry();
        reg.register(always("testland", true));    // matches and has rules
        reg.register(always("anothercountry", true)); // would match, but registered later
        assertEquals("tellus:test_boreal",
            reg.palette(0.0, 0.0, "Dfc", "TreeCover"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :test --tests "*CountryRegistryTest*"`
Expected: FAIL — classes do not exist.

- [ ] **Step 3: Create the interface and registry**

```java
// src/main/java/com/yucareux/tellus/worldgen/trees/CountryContainment.java
package com.yucareux.tellus.worldgen.trees;

public interface CountryContainment {
    String code();
    boolean contains(double lat, double lon);
}
```

```java
// src/main/java/com/yucareux/tellus/worldgen/trees/CountryRegistry.java
package com.yucareux.tellus.worldgen.trees;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CountryRegistry {

    private final List<CountryContainment> countries = new ArrayList<>();

    public void register(CountryContainment country) {
        countries.add(Objects.requireNonNull(country, "country"));
    }

    public String palette(double lat, double lon, String koppen, String esa) {
        for (CountryContainment c : countries) {
            if (c.contains(lat, lon)) {
                CountryTreeMapping m = TreePaletteRegistry.mapping(c.code());
                if (m != null) {
                    return m.resolve(koppen, esa);
                }
                return TreePaletteRegistry.VANILLA_DEFAULT_ID;
            }
        }
        return TreePaletteRegistry.VANILLA_DEFAULT_ID;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :test --tests "*CountryRegistryTest*"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/yucareux/tellus/worldgen/trees/CountryContainment.java \
        src/main/java/com/yucareux/tellus/worldgen/trees/CountryRegistry.java \
        src/test/java/com/yucareux/tellus/worldgen/trees/CountryRegistryTest.java
git commit -m "feat(trees): country registry with mapping resolution"
```

---

### Task 6: Make CanElevationCoverageIndex.containsCanada public + CanadaContainment

**Files:**
- Modify: `src/main/java/com/yucareux/tellus/world/data/elevation/CanElevationCoverageIndex.java:38`
- Create: `src/main/java/com/yucareux/tellus/worldgen/trees/containment/CanadaContainment.java`

- [ ] **Step 1: Change `containsCanada` visibility from package-private to public**

In `CanElevationCoverageIndex.java`, change the existing line 38 from:

```java
   boolean containsCanada(double lat, double lon) {
```

to:

```java
   public boolean containsCanada(double lat, double lon) {
```

Also change `create()` (line 26) from package-private to public if it is not already:

```java
   public static CanElevationCoverageIndex create() {
      return new CanElevationCoverageIndex(DEFAULT_RESOURCE_PATH);
   }
```

- [ ] **Step 2: Create CanadaContainment**

```java
// src/main/java/com/yucareux/tellus/worldgen/trees/containment/CanadaContainment.java
package com.yucareux.tellus.worldgen.trees.containment;

import com.yucareux.tellus.world.data.elevation.CanElevationCoverageIndex;
import com.yucareux.tellus.worldgen.trees.CountryContainment;

public final class CanadaContainment implements CountryContainment {

    private final CanElevationCoverageIndex index;

    public CanadaContainment() {
        this(CanElevationCoverageIndex.create());
    }

    CanadaContainment(CanElevationCoverageIndex index) {
        this.index = index;
    }

    @Override public String code() { return "canada"; }

    @Override
    public boolean contains(double lat, double lon) {
        return index.containsCanada(lat, lon);
    }
}
```

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew :mc1211:compileJava`
Expected: success — `CanElevationCoverageIndex` is now public-callable from the new package.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/yucareux/tellus/world/data/elevation/CanElevationCoverageIndex.java \
        src/main/java/com/yucareux/tellus/worldgen/trees/containment/CanadaContainment.java
git commit -m "feat(trees): expose Canada containment via CountryContainment"
```

---

### Task 7: Stub countries + CountryContainments wiring

**Files:**
- Create: `src/main/java/com/yucareux/tellus/worldgen/trees/containment/StubCountryContainment.java`
- Create: `src/main/java/com/yucareux/tellus/worldgen/trees/containment/CountryContainments.java`

- [ ] **Step 1: Create the stub class**

```java
// src/main/java/com/yucareux/tellus/worldgen/trees/containment/StubCountryContainment.java
package com.yucareux.tellus.worldgen.trees.containment;

import com.yucareux.tellus.worldgen.trees.CountryContainment;

// Placeholder containment for countries that have a mapping file but no
// polygon source yet. contains() always returns false so the mapping is
// never selected at runtime; the file still loads and validates.
public final class StubCountryContainment implements CountryContainment {

    private final String code;

    public StubCountryContainment(String code) {
        this.code = code;
    }

    @Override public String code() { return code; }
    @Override public boolean contains(double lat, double lon) { return false; }
}
```

- [ ] **Step 2: Create the wiring helper**

```java
// src/main/java/com/yucareux/tellus/worldgen/trees/containment/CountryContainments.java
package com.yucareux.tellus.worldgen.trees.containment;

import com.yucareux.tellus.worldgen.trees.CountryRegistry;

public final class CountryContainments {

    private CountryContainments() {}

    public static CountryRegistry buildDefault() {
        CountryRegistry reg = new CountryRegistry();
        reg.register(new CanadaContainment());
        reg.register(new StubCountryContainment("usa"));
        reg.register(new StubCountryContainment("russia"));
        reg.register(new StubCountryContainment("brazil"));
        reg.register(new StubCountryContainment("china"));
        reg.register(new StubCountryContainment("australia"));
        return reg;
    }
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew :mc1211:compileJava`
Expected: success.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/yucareux/tellus/worldgen/trees/containment/
git commit -m "feat(trees): stub country containments + registry wiring"
```

---

### Task 8: Seed resource data files

**Files:**
- Create: `src/main/resources/data/tellus/tree_mapping/canada.json`
- Create: `src/main/resources/data/tellus/tree_mapping/{usa,russia,brazil,china,australia}.json`
- Create: `src/main/resources/data/tellus/tree_palette/{canadian_boreal,canadian_mixed_wood}.json`
- Create: `src/main/resources/data/tellus/tree_species/canadian/{black_spruce,jack_pine,paper_birch,balsam_fir,tamarack,sugar_maple}.json`

- [ ] **Step 1: Create Canada mapping**

```json
// src/main/resources/data/tellus/tree_mapping/canada.json
{
  "country": "canada",
  "rules": [
    { "koppen": "Dfc", "esa": "TreeCover", "palette": "tellus:canadian_boreal" },
    { "koppen": "Dfb", "esa": "TreeCover", "palette": "tellus:canadian_mixed_wood" },
    { "koppen": "Dfc", "esa": "Shrubland", "palette": "tellus:canadian_boreal" }
  ],
  "fallback": "tellus:vanilla_default"
}
```

- [ ] **Step 2: Create stub mappings (identical schema, empty rules)**

Create each of `usa.json`, `russia.json`, `brazil.json`, `china.json`, `australia.json` with this template (change `country` per file):

```json
{
  "country": "usa",
  "rules": [],
  "fallback": "tellus:vanilla_default"
}
```

- [ ] **Step 3: Create canadian_boreal palette**

```json
// src/main/resources/data/tellus/tree_palette/canadian_boreal.json
{
  "trees_per_chunk": 9,
  "species": [
    { "id": "tellus:canadian/black_spruce", "weight": 50 },
    { "id": "tellus:canadian/jack_pine",   "weight": 30 },
    { "id": "tellus:canadian/paper_birch", "weight": 20 }
  ]
}
```

- [ ] **Step 4: Create canadian_mixed_wood palette**

```json
// src/main/resources/data/tellus/tree_palette/canadian_mixed_wood.json
{
  "trees_per_chunk": 9,
  "species": [
    { "id": "tellus:canadian/balsam_fir",   "weight": 30 },
    { "id": "tellus:canadian/sugar_maple",  "weight": 30 },
    { "id": "tellus:canadian/paper_birch",  "weight": 20 },
    { "id": "tellus:canadian/black_spruce", "weight": 20 }
  ]
}
```

- [ ] **Step 5: Create the 6 canadian species files**

For each species, create a file at `src/main/resources/data/tellus/tree_species/canadian/<name>.json` using this template:

```json
{
  "nbt": "tellus:tree/canadian/<name>_01",
  "rotate": true,
  "mirror": false,
  "sapling": "minecraft:<vanilla_sapling>"
}
```

Specific files (use the species name in both the path and the `nbt` value):

| File                      | nbt                                         | sapling                       |
|---------------------------|---------------------------------------------|-------------------------------|
| `black_spruce.json`       | `tellus:tree/canadian/black_spruce_01`      | `minecraft:spruce_sapling`    |
| `jack_pine.json`          | `tellus:tree/canadian/jack_pine_01`         | `minecraft:spruce_sapling`    |
| `paper_birch.json`        | `tellus:tree/canadian/paper_birch_01`       | `minecraft:birch_sapling`     |
| `balsam_fir.json`         | `tellus:tree/canadian/balsam_fir_01`        | `minecraft:spruce_sapling`    |
| `tamarack.json`           | `tellus:tree/canadian/tamarack_01`          | `minecraft:spruce_sapling`    |
| `sugar_maple.json`        | `tellus:tree/canadian/sugar_maple_01`       | `minecraft:oak_sapling`       |

- [ ] **Step 6: Sanity check — load via TreeDataLoader**

Add (and then delete after verification) a one-off main:

```bash
./gradlew :test --tests "*TreeDataLoaderTest*"
```

Then verify by writing a temporary throwaway test that asserts `TreeDataLoader.loadFromClasspath().mappings()` contains all 6 country keys. Delete the test after confirming. Alternatively, just inspect the loader logs during the next phase 2 build.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/data/tellus/
git commit -m "feat(trees): seed Canada palettes, species, and stub country mappings"
```

---

## Phase 2 — Per-version worldgen integration

Implement on **mc1211 first** (Tasks 9–13). Then port to mc1201 and mc261 in Tasks 14 and 15.

### Task 9: Add `treeDensity` field to EarthGeneratorSettings (mc1211)

**Files:**
- Modify: `mc1211/src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java`
- Create: `mc1211/src/test/java/com/yucareux/tellus/worldgen/EarthGeneratorSettingsTreeDensityTest.java`

- [ ] **Step 1: Write a failing test**

```java
// mc1211/src/test/java/com/yucareux/tellus/worldgen/EarthGeneratorSettingsTreeDensityTest.java
package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class EarthGeneratorSettingsTreeDensityTest {

    @Test
    void defaultsToOne() {
        assertEquals(1.0, EarthGeneratorSettings.DEFAULT.treeDensity(), 0.0001);
    }

    @Test
    void roundTripsThroughCodec() {
        EarthGeneratorSettings withCustom = EarthGeneratorSettings.DEFAULT.withTreeDensity(1.5);
        var encoded = EarthGeneratorSettings.CODEC.codec().encodeStart(JsonOps.INSTANCE, withCustom)
            .result().orElseThrow();
        EarthGeneratorSettings decoded = EarthGeneratorSettings.CODEC.codec().parse(JsonOps.INSTANCE, encoded)
            .result().orElseThrow();
        assertEquals(1.5, decoded.treeDensity(), 0.0001);
    }

    @Test
    void absentFieldUsesDefault() {
        // Encode DEFAULT, strip tree_density, decode, expect treeDensity == 1.0.
        var encoded = EarthGeneratorSettings.CODEC.codec().encodeStart(JsonOps.INSTANCE, EarthGeneratorSettings.DEFAULT)
            .result().orElseThrow().getAsJsonObject();
        encoded.remove("tree_density");
        EarthGeneratorSettings decoded = EarthGeneratorSettings.CODEC.codec().parse(JsonOps.INSTANCE, encoded)
            .result().orElseThrow();
        assertEquals(1.0, decoded.treeDensity(), 0.0001);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :mc1211:test --tests "*TreeDensityTest*"`
Expected: FAIL — `treeDensity()` and `withTreeDensity()` do not exist.

- [ ] **Step 3: Add the field**

Edit `mc1211/src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java`:

(a) Add `double treeDensity` as the **last** field in the record header (after `boolean enableWater`):

```java
public record EarthGeneratorSettings(
   // ...existing fields...
   boolean enableWater,
   double treeDensity
) {
```

(b) Find the existing `CODEC` definition (use the surface_depth_limit codec entry as the precedent — search for `surface_depth_limit` or `surfaceDepthLimit` in this file to locate the codec block). Add a new entry **at the end** of the codec field chain:

```java
Codec.DOUBLE.optionalFieldOf("tree_density", 1.0).forGetter(EarthGeneratorSettings::treeDensity)
```

(c) Find every existing constructor / factory / `with*` method that constructs `EarthGeneratorSettings` and add `1.0` (or the incoming `treeDensity`) at the new last position. The compiler will guide you — every call site that previously took N args now takes N+1.

(d) Add the `DEFAULT` constant update — locate the `public static final EarthGeneratorSettings DEFAULT = new EarthGeneratorSettings(...)` and append `1.0`.

(e) Add `withTreeDensity`:

```java
public EarthGeneratorSettings withTreeDensity(double treeDensity) {
    return new EarthGeneratorSettings(
        worldScale, terrestrialHeightScale, oceanicHeightScale, heightOffset,
        seaLevel, spawnLatitude, spawnLongitude, minAltitude, maxAltitude,
        riverLakeShorelineBlend, oceanShorelineBlend, shorelineBlendCliffLimit,
        caveGeneration, oreDistribution, lavaPools,
        addStrongholds, addVillages, addMineshafts, addOceanMonuments,
        addWoodlandMansions, addDesertTemples, addJungleTemples, addPillagerOutposts,
        addRuinedPortals, addShipwrecks, addOceanRuins, addBuriedTreasure,
        addIgloos, addWitchHuts, addAncientCities, addTrialChambers, addTrailRuins,
        deepDark, geodes,
        distantHorizonsWaterResolver, distantHorizonsOsmFeatures,
        distantHorizonsOsmRoadMaxDetail, distantHorizonsOsmBuildingMaxDetail,
        distantHorizonsOsmNonBlockingFetch,
        realtimeTime, realtimeWeather, historicalSnow,
        voxyChunkPregenEnabled, voxyChunkPregenMaxRadius, voxyChunkPregenChunksPerTick,
        distantHorizonsRenderMode, demSelection,
        enableRoads, enableBuildings, enableWater,
        treeDensity);
}
```

(f) Add an `effectiveTreeDensity()` accessor (returns `Mth.clamp(treeDensity, 0.0, 5.0)` to keep insane slider values bounded):

```java
public double effectiveTreeDensity() {
    return net.minecraft.util.Mth.clamp(treeDensity, 0.0, 5.0);
}
```

- [ ] **Step 4: Run the failing test plus the existing codec test**

Run: `./gradlew :mc1211:test --tests "*EarthGeneratorSettings*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add mc1211/src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java \
        mc1211/src/test/java/com/yucareux/tellus/worldgen/EarthGeneratorSettingsTreeDensityTest.java
git commit -m "feat(worldgen): add treeDensity setting field (mc1211)"
```

---

### Task 10: Unlock the Tree Density slider (mc1211)

**Files:**
- Modify: `mc1211/src/client/java/com/yucareux/tellus/client/screen/EarthCustomizeScreen.java:821`

- [ ] **Step 1: Change the slider definition**

In `EarthCustomizeScreen.java`, find the ecological category (around line 821):

```java
slider("trees_density", 100.0, 0.0, 200.0, 5.0).withDisplay(EarthCustomizeScreen::formatPercent).locked(true),
```

Change to:

```java
slider("trees_density", EarthGeneratorSettings.DEFAULT.treeDensity() * 100.0, 0.0, 200.0, 5.0)
    .withDisplay(EarthCustomizeScreen::formatPercent),
```

- [ ] **Step 2: Wire the slider value into the settings codec**

Locate the spot where ecological-category slider values are read into `EarthGeneratorSettings` builders/factories. The pattern should mirror how `surface_depth_limit_blocks` is read (the surface-depth-limit branch's `EarthCustomizeScreen` changes show the precedent — search for `surface_depth_limit` in this file).

When constructing the modified `EarthGeneratorSettings`, divide the slider value by 100.0:

```java
double treeDensity = sliderValue("trees_density") / 100.0;
// then pass `treeDensity` to .withTreeDensity(...) or the constructor
```

- [ ] **Step 3: Compile**

Run: `./gradlew :mc1211:compileJava`
Expected: success.

- [ ] **Step 4: Run the client to verify the slider is unlocked**

Run: `./gradlew runClient1211`
Manually: create a Tellus world → Customize → Ecological → confirm "Tree Density" is enabled and movable.
Expected: slider responds to mouse drag.

- [ ] **Step 5: Commit**

```bash
git add mc1211/src/client/java/com/yucareux/tellus/client/screen/EarthCustomizeScreen.java
git commit -m "feat(ui): unlock Tree Density slider and wire to treeDensity setting (mc1211)"
```

---

### Task 11: RegionalTreePlacer (mc1211)

**Files:**
- Create: `mc1211/src/main/java/com/yucareux/tellus/worldgen/trees/RegionalTreePlacer.java`

This class is version-specific because it touches `WorldGenLevel`, `StructureTemplateManager`, `StructurePlaceSettings`, `Rotation`, `Mirror`, `RandomSource`. The Minecraft API for these classes differs across 1.20.1 / 1.21.1 / 1.21.5.

- [ ] **Step 1: Create the class**

```java
// mc1211/src/main/java/com/yucareux/tellus/worldgen/trees/RegionalTreePlacer.java
package com.yucareux.tellus.worldgen.trees;

import com.yucareux.tellus.worldgen.EarthGeneratorSettings;
import com.yucareux.tellus.worldgen.trees.containment.CountryContainments;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class RegionalTreePlacer {

    private static final Logger LOG = Logger.getLogger(RegionalTreePlacer.class.getName());
    private static final CountryRegistry COUNTRIES = CountryContainments.buildDefault();

    public static String resolvePaletteId(double lat, double lon, String koppen, String esa) {
        return COUNTRIES.palette(lat, lon, koppen, esa);
    }

    public static boolean isVanillaDefault(String paletteId) {
        return TreePaletteRegistry.isVanillaDefault(paletteId);
    }

    /**
     * Place a single tree from `paletteId` at `position`.
     * Returns true if a tree was placed (caller should skip the vanilla feature).
     * Returns false if placement failed (caller may fall back to vanilla).
     */
    public static boolean placeOne(
        WorldGenLevel level,
        BlockPos position,
        String paletteId,
        RandomSource random,
        EarthGeneratorSettings settings
    ) {
        TreePaletteDef palette = TreePaletteRegistry.palette(paletteId);
        if (palette == null) {
            return false;
        }
        // Apply global density as per-attempt probability.
        double accept = Math.min(1.0, settings.effectiveTreeDensity());
        if (random.nextDouble() >= accept) {
            return true; // treated as "we owned this attempt and chose to skip"
        }
        TreePaletteDef.WeightedSpecies pick = palette.pick(new Random(random.nextLong()));
        TreeSpeciesDef species = TreePaletteRegistry.species(pick.speciesId());
        if (species == null) {
            LOG.log(Level.WARNING, "missing species def '" + pick.speciesId() + "' for palette '" + paletteId + "'");
            return false;
        }
        StructureTemplateManager mgr = ((ServerLevel) level.getLevel()).getStructureManager();
        ResourceLocation nbtId = ResourceLocation.parse(species.nbt());
        StructureTemplate template = mgr.getOrCreate(nbtId);
        Rotation rot = species.rotate() ? Rotation.getRandom(random) : Rotation.NONE;
        Mirror mir = species.mirror() && random.nextBoolean() ? Mirror.FRONT_BACK : Mirror.NONE;
        StructurePlaceSettings ps = new StructurePlaceSettings()
            .setRotation(rot)
            .setMirror(mir)
            .setRandom(random);
        return template.placeInWorld(level, position, position, ps, random, 2);
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :mc1211:compileJava`
Expected: success. If `StructureTemplateManager.getOrCreate` signature differs in 1.21.1, replace with `mgr.get(nbtId).orElse(null)` and a null check.

- [ ] **Step 3: Commit (no tests yet — runtime integration test comes after wiring)**

```bash
git add mc1211/src/main/java/com/yucareux/tellus/worldgen/trees/RegionalTreePlacer.java
git commit -m "feat(trees): RegionalTreePlacer (mc1211)"
```

---

### Task 12: Hook EarthChunkGenerator tree placement to RegionalTreePlacer (mc1211)

**Files:**
- Modify: `mc1211/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java:3879-3892`

- [ ] **Step 1: Locate the tree placement block**

Find the existing block (around line 3879):

```java
BlockPos position = ground.above();
Holder<Biome> biome = decorationContext != null ? decorationContext.biome(localX, localZ) : level.getBiome(position);
if (!biome.is(Biomes.MANGROVE_SWAMP)) {
   List<ConfiguredFeature<?, ?>> features = treeFeaturesForBiome(biome);
   if (!features.isEmpty()) {
      if (!groundState.is(BlockTags.DIRT)) {
         level.setBlock(ground, GRASS_BLOCK_STATE, 260);
      }

      ConfiguredFeature<?, ?> feature = features.get(random.nextInt(features.size()));
      feature.place(level, this, random, position);
   }
}
```

- [ ] **Step 2: Add a static ESA code → name lookup**

The mapping JSON uses string ESA names ("TreeCover", "Shrubland"). The `TellusLandCoverSource` returns int codes. Add a small static helper near the top of `EarthChunkGenerator`:

```java
private static String esaName(int code) {
    return switch (code) {
        case 10 -> "TreeCover";
        case 20 -> "Shrubland";
        case 30 -> "Grassland";
        case 40 -> "Cropland";
        case 50 -> "BuiltUp";
        case 60 -> "BareSparseVegetation";
        case 70 -> "SnowAndIce";
        case 80 -> "PermanentWaterBodies";
        case 90 -> "HerbaceousWetland";
        case 95 -> "Mangroves";
        case 100 -> "MossAndLichen";
        default -> "Unknown";
    };
}
```

(Codes come from `biome_classification_system.csv` — names are CamelCase variants of the `esa_name` column.)

- [ ] **Step 3: Wrap the existing tree placement with the regional check**

Find the existing block (around line 3879) and replace with:

```java
BlockPos position = ground.above();
Holder<Biome> biome = decorationContext != null ? decorationContext.biome(localX, localZ) : level.getBiome(position);
if (!biome.is(Biomes.MANGROVE_SWAMP)) {
   double worldScale = this.settings.worldScale();
   double lat = EarthProjection.blockZToLat((double) worldZ, worldScale);
   double lon = (double) worldX / EarthProjection.blocksPerDegree(worldScale);
   String koppen = KOPPEN_SOURCE.sampleDitheredCode((double) worldX, (double) worldZ, worldScale);
   int esaCode = LAND_COVER_SOURCE.sampleCoverClass((double) worldX, (double) worldZ, worldScale);
   String esa = esaName(esaCode);
   String paletteId = (koppen == null)
       ? TreePaletteRegistry.VANILLA_DEFAULT_ID
       : RegionalTreePlacer.resolvePaletteId(lat, lon, koppen, esa);

   boolean placedRegionally = false;
   if (!RegionalTreePlacer.isVanillaDefault(paletteId)) {
      if (!groundState.is(BlockTags.DIRT)) {
         level.setBlock(ground, GRASS_BLOCK_STATE, 260);
      }
      placedRegionally = RegionalTreePlacer.placeOne(level, position, paletteId, random, this.settings);
   }

   if (!placedRegionally) {
      List<ConfiguredFeature<?, ?>> features = treeFeaturesForBiome(biome);
      if (!features.isEmpty()) {
         if (!groundState.is(BlockTags.DIRT)) {
            level.setBlock(ground, GRASS_BLOCK_STATE, 260);
         }
         ConfiguredFeature<?, ?> feature = features.get(random.nextInt(features.size()));
         feature.place(level, this, random, position);
      }
   }
}
```

Add the imports (Gradle's compile error will tell you exactly which are missing — typical additions):

```java
import com.yucareux.tellus.worldgen.EarthProjection;
import com.yucareux.tellus.worldgen.trees.RegionalTreePlacer;
import com.yucareux.tellus.worldgen.trees.TreePaletteRegistry;
```

`KOPPEN_SOURCE` and `LAND_COVER_SOURCE` are not currently defined in `EarthChunkGenerator` — only in `EarthBiomeSource`. Add them as `private static final` fields near the existing source-class field declarations (search the file for `TellusWorldgenSources` to find where they should go):

```java
private static final TellusLandCoverSource LAND_COVER_SOURCE = TellusWorldgenSources.landCover();
private static final TellusKoppenSource KOPPEN_SOURCE = TellusWorldgenSources.koppen();
```

Also apply the same edit at the second tree placement site (around line 4002 — `grep -n "treeFeaturesForBiome" mc1211/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java` shows both sites).

- [ ] **Step 4: Compile**

Run: `./gradlew :mc1211:compileJava`
Expected: success.

- [ ] **Step 5: Manual playtest**

Run: `./gradlew runClient1211`
Create a Tellus world with default spawn at Everest (latitude 27.99 — not in Canada, so palette = vanilla_default), confirm forests still look like vanilla.

Then use `/tellus map` to teleport to Canada (e.g. latitude 55, longitude -100), let some chunks generate, confirm no exceptions in logs. Trees will look like vanilla until NBT files are added (warning logs are expected).

- [ ] **Step 6: Commit**

```bash
git add mc1211/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java
git commit -m "feat(worldgen): hook RegionalTreePlacer into chunk gen tree placement (mc1211)"
```

---

### Task 13: Verify mc1211 build + tests

- [ ] **Step 1: Full test suite**

Run: `./gradlew :mc1211:test`
Expected: all green.

- [ ] **Step 2: Full build**

Run: `./gradlew :mc1211:build`
Expected: success.

- [ ] **Step 3: No commit if green** — proceed to port phase. If issues, fix and commit.

---

### Task 14: Port Phase 2 changes to mc1201

Apply the same edits from Tasks 9–12 to the mc1201 subproject. Use `git diff feature/regional-trees~5..feature/regional-trees -- mc1211/` as a reference diff to copy.

- [ ] **Step 1: Port `EarthGeneratorSettings.java`**

Apply the same field-addition + codec + `with*` + `effectiveTreeDensity()` changes to `mc1201/src/main/java/com/yucareux/tellus/worldgen/EarthGeneratorSettings.java`. The record header and codec block are structurally identical to mc1211 (the surface-depth-limit branch shows this pattern — see commit `682d7e9 feat(worldgen): add surface depth limit fields, codec, and effective accessors`).

- [ ] **Step 2: Port `EarthCustomizeScreen.java`**

Apply the same slider-unlock + value-wiring change to `mc1201/src/client/java/com/yucareux/tellus/client/screen/EarthCustomizeScreen.java`.

- [ ] **Step 3: Port `RegionalTreePlacer.java`**

Copy `mc1211/src/main/java/com/yucareux/tellus/worldgen/trees/RegionalTreePlacer.java` to `mc1201/src/main/java/com/yucareux/tellus/worldgen/trees/RegionalTreePlacer.java`. Compile to find any 1.20.1-specific API differences (likely `ResourceLocation.parse` → `new ResourceLocation(...)` and `getStructureManager` location). Adjust.

- [ ] **Step 4: Port `EarthChunkGenerator.java` hook**

Apply the same wrap-with-regional-check edit at the equivalent line numbers in `mc1201/src/main/java/com/yucareux/tellus/worldgen/EarthChunkGenerator.java`. Search for `treeFeaturesForBiome` to locate the sites — they should be analogous to mc1211.

- [ ] **Step 5: Port the codec test**

Copy the new `EarthGeneratorSettingsTreeDensityTest.java` to `mc1201/src/test/java/...` (same path).

- [ ] **Step 6: Build and test**

Run: `./gradlew :mc1201:build :mc1201:test`
Expected: green.

- [ ] **Step 7: Commit**

```bash
git add mc1201/
git commit -m "feat(trees): port regional tree integration to mc1201"
```

---

### Task 15: Port Phase 2 changes to mc261

Apply the same edits from Tasks 9–12 to the mc261 subproject (Minecraft 1.21.5 / NeoForge 1.21.5).

- [ ] **Step 1: Port settings, screen, placer, chunk gen (mirror Task 14)**

Repeat each sub-step from Task 14, replacing `mc1201` with `mc261`. The 1.21.5 API may differ slightly (e.g. `StructureTemplate.placeInWorld` signature, registry access patterns) — compile errors will surface these; adjust per Minecraft version.

- [ ] **Step 2: Build and test**

Run: `./gradlew :mc261:build :mc261:test`
Expected: green.

- [ ] **Step 3: Commit**

```bash
git add mc261/
git commit -m "feat(trees): port regional tree integration to mc261"
```

---

### Task 16: Final cross-version verification

- [ ] **Step 1: Full build of all versions**

Run: `./gradlew build`
Expected: all three subproject builds succeed.

- [ ] **Step 2: Full test suite**

Run: `./gradlew check`
Expected: all tests pass.

- [ ] **Step 3: Manual smoke test (mc1211 only — representative)**

Run: `./gradlew runClient1211`. Create a Tellus world. Verify:
- Customize screen → Ecological → "Tree Density" slider is unlocked.
- Spawn at default coordinates (Everest, outside Canada). Trees look like vanilla forest.
- `/tellus map` teleport to lat=55, lon=-100 (central Canada). Generate ~50 chunks. Logs show one-time "missing NBT" warnings for canadian species (expected — no NBTs shipped). No exceptions. Tree placement falls back to vanilla via the `placeOne` returning false path.
- Set Tree Density slider to 0%, regenerate a chunk in Canada — expect zero regional placement attempts.
- Set Tree Density to 200%, regenerate a chunk in Canada — expect ~2× per-attempt acceptance rate (still capped at one tree per 5×5 cell by the existing sampler — this is the v1 limitation noted in the spec).

- [ ] **Step 4: Commit anything fixed during smoke test**

If smoke test surfaced bugs, commit fixes. Otherwise no commit.

---

## Done

At this point:
- `feature/regional-trees` is a complete framework.
- Canada has data in place; once NBT files are dropped at `assets/tellus/tree/canadian/*.nbt` (out-of-plan), Canadian forests render with the regional palette automatically.
- USA/Russia/Brazil/China/Australia have data + class stubs; flipping each one on means (a) replacing the stub `contains()` with a real polygon check and (b) filling in `rules` in the country mapping JSON. No further code work needed for those countries.
- Everywhere else falls through to the existing vanilla tree placement path.

The branch is ready for PR review. Open as draft with a checklist mirroring the v2 add-ons noted in the spec (per-species elevation clamps, noise-driven species stands, sub-cell sampling for `trees_per_chunk > 9`).
