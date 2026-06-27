package com.yucareux.tellus.worldgen.trees;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class TreeDataLoader {

    private static final Logger LOG = Logger.getLogger(TreeDataLoader.class.getName());
    private static final String ROOT = "data/tellus";
    private static volatile LoadedData cached;

    private TreeDataLoader() {}

    public static LoadedData loadFromClasspath() {
        LoadedData local = cached;
        if (local != null) return local;
        synchronized (TreeDataLoader.class) {
            if (cached != null) return cached;
            cached = scan();
            return cached;
        }
    }

    static void resetForTesting() {
        cached = null;
    }

    private static LoadedData scan() {
        Map<String, TreeSpeciesDef> species = new HashMap<>();
        Map<String, TreePaletteDef> palettes = new HashMap<>();
        Map<String, CountryTreeMapping> mappings = new HashMap<>();

        forEachJson("tree_species", (relPath, json) -> {
            String id = "tellus:" + relPath.substring(0, relPath.length() - ".json".length());
            try {
                species.put(id, TreeSpeciesDef.fromJson(id, json));
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "failed to load species " + id, e);
            }
        });

        forEachJson("tree_palette", (relPath, json) -> {
            String id = "tellus:" + relPath.substring(0, relPath.length() - ".json".length());
            try {
                palettes.put(id, TreePaletteDef.fromJson(id, json));
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "failed to load palette " + id, e);
            }
        });

        forEachJson("tree_mapping", (relPath, json) -> {
            try {
                CountryTreeMapping m = CountryTreeMapping.fromJson(json);
                mappings.put(m.country(), m);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "failed to load mapping " + relPath, e);
            }
        });

        return new LoadedData(
            Collections.unmodifiableMap(species),
            Collections.unmodifiableMap(palettes),
            Collections.unmodifiableMap(mappings));
    }

    @FunctionalInterface
    private interface JsonVisitor {
        void visit(String relPath, JsonObject json);
    }

    private static void forEachJson(String subdir, JsonVisitor visitor) {
        ClassLoader cl = TreeDataLoader.class.getClassLoader();
        String root = ROOT + "/" + subdir;
        try {
            Enumeration<URL> roots = cl.getResources(root);
            while (roots.hasMoreElements()) {
                URL url = roots.nextElement();
                URI uri = url.toURI();
                Path base;
                FileSystem fs = null;
                boolean ownsFs = false;
                if ("jar".equals(uri.getScheme())) {
                    // The mod jar's zip filesystem is usually already mounted by the
                    // mod loader. Creating a second one for the same URI throws
                    // FileSystemAlreadyExistsException, so reuse the open one (and
                    // don't close it, since we don't own it).
                    try {
                        fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                        ownsFs = true;
                    } catch (FileSystemAlreadyExistsException alreadyOpen) {
                        fs = FileSystems.getFileSystem(uri);
                    }
                    base = fs.getPath("/" + root);
                } else {
                    base = Paths.get(uri);
                }
                try (Stream<Path> walk = Files.walk(base)) {
                    walk.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                        String rel = base.relativize(p).toString().replace('\\', '/');
                        try (InputStream in = Files.newInputStream(p);
                             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                            JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
                            visitor.visit(rel, json);
                        } catch (IOException e) {
                            LOG.log(Level.WARNING, "failed reading " + p, e);
                        }
                    });
                } finally {
                    if (fs != null && ownsFs) fs.close();
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "failed scanning " + root, e);
        }
    }

    public record LoadedData(
        Map<String, TreeSpeciesDef> species,
        Map<String, TreePaletteDef> palettes,
        Map<String, CountryTreeMapping> mappings
    ) {}
}
