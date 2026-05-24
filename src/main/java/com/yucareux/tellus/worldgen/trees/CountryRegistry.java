package com.yucareux.tellus.worldgen.trees;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CountryRegistry {

    private final List<CountryContainment> countries = new ArrayList<>();

    public void register(CountryContainment country) {
        countries.add(Objects.requireNonNull(country, "country"));
    }

    public String palette(double lat, double lon, String koppen, String esa) {
        for (CountryContainment c : countries) {
            if (c.contains(lat, lon)) {
                CountryTreeMapping m = TreePaletteRegistry.mapping(c.code());
                if (m != null) {
                    return m.resolve(koppen, esa);
                }
                return TreePaletteRegistry.VANILLA_DEFAULT_ID;
            }
        }
        return TreePaletteRegistry.VANILLA_DEFAULT_ID;
    }
}
