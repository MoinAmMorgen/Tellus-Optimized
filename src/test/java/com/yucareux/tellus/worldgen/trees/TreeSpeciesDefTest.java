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
        String json = "{ \"nbt\": \"tellus:tree/x\" }";
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
