package com.yucareux.tellus.worldgen.trees;

public final class TreePaletteRegistry {

    public static final String VANILLA_DEFAULT_ID = "tellus:vanilla_default";

    private TreePaletteRegistry() {}

    public static boolean isVanillaDefault(String paletteId) {
        return VANILLA_DEFAULT_ID.equals(paletteId);
    }

    public static TreePaletteDef palette(String id) {
        return TreeDataLoader.loadFromClasspath().palettes().get(id);
    }

    public static TreeSpeciesDef species(String id) {
        return TreeDataLoader.loadFromClasspath().species().get(id);
    }

    public static CountryTreeMapping mapping(String countryCode) {
        return TreeDataLoader.loadFromClasspath().mappings().get(countryCode);
    }
}
