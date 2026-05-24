// src/main/java/com/yucareux/tellus/worldgen/trees/CountryTreeMapping.java
package com.yucareux.tellus.worldgen.trees;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record CountryTreeMapping(String country, List<Rule> rules, String fallbackPaletteId) {

    public CountryTreeMapping {
        Objects.requireNonNull(country, "country");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(fallbackPaletteId, "fallbackPaletteId");
        rules = List.copyOf(rules);
    }

    public static CountryTreeMapping fromJson(JsonObject json) {
        if (!json.has("country")) {
            throw new IllegalArgumentException("tree mapping missing required field 'country'");
        }
        if (!json.has("fallback")) {
            throw new IllegalArgumentException("tree mapping missing required field 'fallback'");
        }
        String country = json.get("country").getAsString();
        String fallback = json.get("fallback").getAsString();
        List<Rule> rules = new ArrayList<>();
        if (json.has("rules") && json.get("rules").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("rules");
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                rules.add(new Rule(
                    obj.get("koppen").getAsString(),
                    obj.get("esa").getAsString(),
                    obj.get("palette").getAsString()
                ));
            }
        }
        return new CountryTreeMapping(country, rules, fallback);
    }

    public String resolve(String koppen, String esa) {
        for (Rule r : rules) {
            if (r.koppen().equals(koppen) && r.esa().equals(esa)) {
                return r.paletteId();
            }
        }
        return fallbackPaletteId;
    }

    public record Rule(String koppen, String esa, String paletteId) {}
}
