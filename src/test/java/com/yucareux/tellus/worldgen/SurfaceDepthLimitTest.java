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
