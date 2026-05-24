package com.yucareux.tellus.worldgen.trees;

import com.google.gson.JsonObject;
import java.util.Objects;

public record TreeSpeciesDef(String id, String nbt, boolean rotate, boolean mirror, String sapling) {

    public TreeSpeciesDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nbt, "nbt");
    }

    public static TreeSpeciesDef fromJson(String id, JsonObject json) {
        if (!json.has("nbt")) {
            throw new IllegalArgumentException("tree species '" + id + "' missing required field 'nbt'");
        }
        String nbt = json.get("nbt").getAsString();
        boolean rotate = !json.has("rotate") || json.get("rotate").getAsBoolean();
        boolean mirror = json.has("mirror") && json.get("mirror").getAsBoolean();
        String sapling = json.has("sapling") ? json.get("sapling").getAsString() : null;
        return new TreeSpeciesDef(id, nbt, rotate, mirror, sapling);
    }
}
