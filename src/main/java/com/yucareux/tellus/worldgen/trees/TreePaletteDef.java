// src/main/java/com/yucareux/tellus/worldgen/trees/TreePaletteDef.java
package com.yucareux.tellus.worldgen.trees;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public record TreePaletteDef(String id, double treesPerChunk, List<WeightedSpecies> species, double totalWeight) {

    public TreePaletteDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(species, "species");
        species = List.copyOf(species);
    }

    public static TreePaletteDef fromJson(String id, JsonObject json) {
        double treesPerChunk = json.has("trees_per_chunk") ? json.get("trees_per_chunk").getAsDouble() : 0.0;
        if (!json.has("species") || !json.get("species").isJsonArray()) {
            throw new IllegalArgumentException("palette '" + id + "' missing required array 'species'");
        }
        JsonArray arr = json.getAsJsonArray("species");
        List<WeightedSpecies> entries = new ArrayList<>(arr.size());
        double total = 0.0;
        for (JsonElement el : arr) {
            JsonObject obj = el.getAsJsonObject();
            String speciesId = obj.get("id").getAsString();
            double weight = obj.has("weight") ? obj.get("weight").getAsDouble() : 1.0;
            if (weight <= 0.0) {
                throw new IllegalArgumentException(
                    "palette '" + id + "' has non-positive weight for species '" + speciesId + "'");
            }
            entries.add(new WeightedSpecies(speciesId, weight));
            total += weight;
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("palette '" + id + "' has empty species list");
        }
        return new TreePaletteDef(id, treesPerChunk, entries, total);
    }

    public WeightedSpecies pick(Random rng) {
        double r = rng.nextDouble() * totalWeight;
        double acc = 0.0;
        for (WeightedSpecies s : species) {
            acc += s.weight();
            if (r < acc) {
                return s;
            }
        }
        return species.get(species.size() - 1);
    }

    public record WeightedSpecies(String speciesId, double weight) {}
}
