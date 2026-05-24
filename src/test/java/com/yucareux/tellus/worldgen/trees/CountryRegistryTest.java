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
        assertEquals(TreePaletteRegistry.VANILLA_DEFAULT_ID,
            reg.palette(0.0, 0.0, "Dfb", "TreeCover"));
    }

    @Test
    void fixtureMappingResolves() {
        CountryRegistry reg = new CountryRegistry();
        reg.register(always("testland", true));
        assertEquals("tellus:test_boreal",
            reg.palette(0.0, 0.0, "Dfc", "TreeCover"));
    }

    @Test
    void firstHitWins() {
        CountryRegistry reg = new CountryRegistry();
        reg.register(always("testland", true));
        reg.register(always("anothercountry", true));
        assertEquals("tellus:test_boreal",
            reg.palette(0.0, 0.0, "Dfc", "TreeCover"));
    }
}
