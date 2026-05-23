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
