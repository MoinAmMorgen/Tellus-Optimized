package com.yucareux.tellus.worldgen;

import static org.junit.jupiter.api.Assertions.*;
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
        var encoded = EarthGeneratorSettings.CODEC.encodeStart(JsonOps.INSTANCE, withCustom)
            .result().orElseThrow();
        EarthGeneratorSettings decoded = EarthGeneratorSettings.CODEC.parse(JsonOps.INSTANCE, encoded)
            .result().orElseThrow();
        assertEquals(1.5, decoded.treeDensity(), 0.0001);
    }

    @Test
    void absentFieldUsesDefault() {
        var encoded = EarthGeneratorSettings.CODEC.encodeStart(JsonOps.INSTANCE, EarthGeneratorSettings.DEFAULT)
            .result().orElseThrow().getAsJsonObject();
        encoded.remove("tree_density");
        EarthGeneratorSettings decoded = EarthGeneratorSettings.CODEC.parse(JsonOps.INSTANCE, encoded)
            .result().orElseThrow();
        assertEquals(1.0, decoded.treeDensity(), 0.0001);
    }

    @Test
    void effectiveTreeDensityClampsToRange() {
        assertEquals(0.0, EarthGeneratorSettings.DEFAULT.withTreeDensity(-1.0).effectiveTreeDensity(), 0.0001);
        assertEquals(5.0, EarthGeneratorSettings.DEFAULT.withTreeDensity(99.0).effectiveTreeDensity(), 0.0001);
        assertEquals(1.5, EarthGeneratorSettings.DEFAULT.withTreeDensity(1.5).effectiveTreeDensity(), 0.0001);
    }
}
