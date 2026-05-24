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
