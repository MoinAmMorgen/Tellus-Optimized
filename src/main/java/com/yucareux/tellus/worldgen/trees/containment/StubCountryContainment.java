package com.yucareux.tellus.worldgen.trees.containment;

import com.yucareux.tellus.worldgen.trees.CountryContainment;

// Placeholder containment for countries that have a mapping file but no
// polygon source yet. contains() always returns false so the mapping is
// never selected at runtime; the file still loads and validates.
public final class StubCountryContainment implements CountryContainment {

    private final String code;

    public StubCountryContainment(String code) {
        this.code = code;
    }

    @Override public String code() { return code; }
    @Override public boolean contains(double lat, double lon) { return false; }
}
