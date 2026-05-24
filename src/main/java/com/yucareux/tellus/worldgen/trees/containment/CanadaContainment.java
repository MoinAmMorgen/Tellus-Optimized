package com.yucareux.tellus.worldgen.trees.containment;

import com.yucareux.tellus.world.data.elevation.CanElevationCoverageIndex;
import com.yucareux.tellus.worldgen.trees.CountryContainment;

public final class CanadaContainment implements CountryContainment {

    private final CanElevationCoverageIndex index;

    public CanadaContainment() {
        this(CanElevationCoverageIndex.create());
    }

    CanadaContainment(CanElevationCoverageIndex index) {
        this.index = index;
    }

    @Override public String code() { return "canada"; }

    @Override
    public boolean contains(double lat, double lon) {
        return index.containsCanada(lat, lon);
    }
}
