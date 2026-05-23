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
