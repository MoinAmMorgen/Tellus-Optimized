package com.yucareux.tellus.worldgen.trees;

public interface CountryContainment {
    String code();
    boolean contains(double lat, double lon);
}
