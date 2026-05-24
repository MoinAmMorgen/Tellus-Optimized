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
    void vanillaDefaultPaletteIdIsSentinel() {
        assertEquals("tellus:vanilla_default", TreePaletteRegistry.VANILLA_DEFAULT_ID);
        assertTrue(TreePaletteRegistry.isVanillaDefault("tellus:vanilla_default"));
        assertFalse(TreePaletteRegistry.isVanillaDefault("tellus:something_else"));
    }
}
