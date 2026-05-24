package com.yucareux.tellus.worldgen.trees.containment;

import com.yucareux.tellus.worldgen.trees.CountryRegistry;

public final class CountryContainments {

    private CountryContainments() {}

    public static CountryRegistry buildDefault() {
        CountryRegistry reg = new CountryRegistry();
        reg.register(new CanadaContainment());
        reg.register(new StubCountryContainment("usa"));
        reg.register(new StubCountryContainment("russia"));
        reg.register(new StubCountryContainment("brazil"));
        reg.register(new StubCountryContainment("china"));
        reg.register(new StubCountryContainment("australia"));
        return reg;
    }
}
