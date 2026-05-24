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
