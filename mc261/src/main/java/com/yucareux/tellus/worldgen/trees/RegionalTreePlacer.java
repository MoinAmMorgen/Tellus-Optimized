// mc261/src/main/java/com/yucareux/tellus/worldgen/trees/RegionalTreePlacer.java
package com.yucareux.tellus.worldgen.trees;

import com.yucareux.tellus.worldgen.EarthGeneratorSettings;
import com.yucareux.tellus.worldgen.trees.containment.CountryContainments;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
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
        Identifier nbtId = Identifier.parse(species.nbt());
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
