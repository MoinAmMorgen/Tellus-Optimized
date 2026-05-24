# Regional Tree Palettes — Design

## Problem

Tellus places trees by relying on whatever vanilla Minecraft tree features the
selected biome happens to ship. That has three consequences players notice:

1. **Wrong species mix.** Canadian boreal currently fills with generic vanilla
   taiga spruce. Real Canada has black spruce, jack pine, balsam fir,
   tamarack, paper birch, sugar maple, etc., and the mix shifts north–south.
2. **Wrong tree shapes/sizes.** Vanilla procedural trees look uniform and
   generic. We want author-controlled NBT trees that match real silhouettes.
3. **Wrong density/distribution.** Vanilla feels uniform and salt-and-pepper.
   We want a tunable trees-per-chunk rate per region.

Tree placement (which biomes are forested at all) is *not* a problem — the
ESA × Köppen biome assignment is fine. The fix is what trees spawn inside
already-forested biomes.

## Goal

Add a regional tree system that selects a tree palette per (country, Köppen
climate, ESA landcover) tuple and places NBT-defined trees from that palette.
Canada gets a real implementation. The five biggest forested countries get
data-file stubs so the framework is exercised and the next palette pass is
just "fill in the stub". Uncovered regions and the open ocean fall back to
vanilla tree behavior so nothing regresses.

Also unlocks the existing locked **Tree Density** UI slider, since the new
feature reads it as a global multiplier.

## Non-goals

- **No noise-driven species stands** in v1. Species are picked per-tree by
  weight; clumpiness comes from natural placement noise, not from biasing one
  species across a region. Easy v2 add-on.
- **No per-species elevation or slope clamping.** Real species have tree
  lines and slope preferences; deferred to v2.
- **No real polygon data for stub countries.** Stub data files exist and
  load, but their containment check returns false until polygon data is
  added per country. This keeps stub authoring honest (no fake bounding
  boxes mis-attributing tiles).
- **No custom saplings, leaf-fall scheduling, or per-species biome tinting.**
  Species reference an existing vanilla sapling so player-planted survival
  trees still work; that's the extent of survival integration in v1.
- **No retroactive change to existing saved worlds.** Worlds created before
  this feature continue to generate the same way: when no country mapping
  applies, the feature delegates to vanilla biome tree features.

## Behavior

### Data model — three layers

**Layer A: Tree species** — one file per species variant, under
`src/main/resources/data/tellus/tree_species/<country>/<species>.json`:

```json
{
  "nbt": "tellus:tree/canadian/black_spruce_01",
  "rotate": true,
  "mirror": false,
  "sapling": "minecraft:spruce_sapling"
}
```

- `nbt` — resource ID of an NBT structure under
  `src/main/resources/assets/tellus/tree/<country>/<name>.nbt`. The origin
  block of the NBT is treated as the sapling root.
- `rotate` — if true, the placer picks a random 0°/90°/180°/270° rotation
  per placement. If false, orientation is locked.
- `mirror` — if true, the placer may also apply a random X mirror.
- `sapling` — vanilla sapling block that grows into a similar tree in
  survival. Cosmetic/compat only; not used by the worldgen placer.

Multiple variants of the same conceptual tree (e.g. `black_spruce_01`,
`black_spruce_02`) are expressed as separate species entries listed
separately in the palette with their own weights. There is no per-species
sub-list of NBTs.

**Layer B: Tree palette** — one file per named palette, under
`src/main/resources/data/tellus/tree_palette/<name>.json`:

```json
{
  "trees_per_chunk": 18,
  "species": [
    { "id": "tellus:canadian/black_spruce", "weight": 50 },
    { "id": "tellus:canadian/jack_pine",   "weight": 30 },
    { "id": "tellus:canadian/paper_birch", "weight": 20 }
  ]
}
```

- `trees_per_chunk` — target average count per 16×16 chunk *before* the
  global Tree Density slider is applied.
- `species[].id` — fully-qualified species ID (`<namespace>:<country>/<name>`).
- `species[].weight` — relative weight; weights are normalized at load time,
  they do not need to sum to 100 or 1.
- A palette named `vanilla_default` is a reserved sentinel: when selected,
  the runtime delegates to vanilla biome tree features instead of placing
  anything itself. Its `trees_per_chunk` and `species` are ignored.

**Layer C: Country mapping** — one file per country, under
`src/main/resources/data/tellus/tree_mapping/<country>.json`:

```json
{
  "country": "canada",
  "rules": [
    { "koppen": "Dfc", "esa": "TreeCover",  "palette": "tellus:canadian_boreal" },
    { "koppen": "Dfb", "esa": "TreeCover",  "palette": "tellus:canadian_mixed_wood" },
    { "koppen": "Dfb", "esa": "Shrubland",  "palette": "tellus:canadian_mixed_wood" }
  ],
  "fallback": "tellus:vanilla_default"
}
```

- `country` — short string country code matching a registered
  `CountryContainment` entry (see *Country detection*).
- `rules` — ordered list; first matching `(koppen, esa)` wins.
- `koppen` uses the same codes as `biome_classification_system.csv`
  (e.g. `Af`, `Dfc`, `ET`).
- `esa` uses the human-readable ESA names from the same CSV
  (e.g. `TreeCover`, `Shrubland`, `Grassland`). Internally normalized to
  the CSV's `esa_code` for lookup.
- `palette` — fully-qualified palette ID; the file
  `data/tellus/tree_palette/<name>.json` must exist (validated at load).
- `fallback` — palette used when no rule matches. Typically
  `tellus:vanilla_default`.

### Country detection

A new `CountryRegistry` holds a list of `CountryContainment` entries, each
exposing `String code()` and `boolean contains(double lat, double lon)`.
Lookup `palette(lat, lon, koppen, esa)`:

1. Iterate registered countries in registration order; first
   `contains(lat, lon) == true` wins.
2. Load that country's mapping file (cached); apply rules in order until a
   `(koppen, esa)` match; otherwise use its `fallback`.
3. If no country contains the point, return the global default
   (`tellus:vanilla_default`).

Canada's `CountryContainment` reuses `CanElevationCoverageIndex.containsCanada`
verbatim — no new polygon data needed, the elevation index already covers
the same footprint.

Stub countries (**USA, Russia, Brazil, China, Australia**) ship a
`CountryContainment` whose `contains()` returns `false` always. Their
mapping file loads and validates so the registry sees them, but their
palettes are never actually selected at runtime. When a real polygon
source is added later (e.g. a per-country coverage index analogous to
`CanElevationCoverageIndex`), only the stub class flips from returning
false to a real polygon test — no other code changes.

### Runtime placement

A new worldgen feature, `RegionalTreeFeature`, replaces the vanilla tree
placement step for biomes that currently spawn trees. It runs once per
chunk during feature generation:

1. Sample lat/lon at chunk center via the same projection
   `EarthBiomeSource` uses.
2. Look up `(koppen, esa)` at that sample point using the existing
   classification pipeline.
3. Ask `CountryRegistry.palette(lat, lon, koppen, esa)` for the palette.
4. If palette is `vanilla_default`, invoke the original vanilla biome
   tree features for this chunk and stop.
5. Otherwise compute `count = round(palette.trees_per_chunk *
   globalTreeDensity)`, where `globalTreeDensity` is the (now-unlocked)
   UI slider value normalized to 1.0 at its default.
6. For each of `count` attempts:
   - Pick a random `(x, z)` inside the chunk using the chunk's worldgen
     random.
   - Weighted-pick a species from the palette.
   - Load the species NBT (cached).
   - Pick a rotation if `rotate`, a mirror if `mirror`.
   - Find the surface block at `(x, z)`; reject if it's water, ice, or
     non-grass/dirt-like (reuse vanilla `Feature.isSoil`-style checks).
   - Place the NBT structure with `Placement` settings honoring rotation
     and mirror, anchored at the surface block.

The placer does *not* enforce tree-to-tree spacing in v1 — overlaps are
fine and are how vanilla feels too. Stand-clumping noise is a v2 add.

### UI: unlocking Tree Density

`property.tellus.trees_density` is currently locked in the Ecological
Settings section. The slider is already wired through to the codec but
not read. This branch:

- Removes the locked flag on `trees_density` only (not the rest of the
  Ecological Settings group).
- Wires the slider value into `EarthGeneratorSettings` and exposes it as
  `effectiveTreeDensity()` so the feature can read it without a static
  global.
- Default value stays at the existing default (whatever the codec
  currently emits — verify during implementation).

Other Ecological Settings (Land Vegetation, Land Vegetation Density,
Aquatic Vegetation, Crops in Villages) stay locked.

## Files Canada needs from the author

Before this is usable end-to-end, the author drops in:

1. **NBT structure files** for each Canadian species variant, under
   `src/main/resources/assets/tellus/tree/canadian/<name>.nbt`.
2. **A species list with weights per palette** — the author confirms or
   amends the seeded palettes (`canadian_boreal`, `canadian_mixed_wood`,
   and any others they want for `Dfc`/`Dfb`/`Dwc`/`ET`/etc.).

The branch ships:

- `data/tellus/tree_mapping/canada.json` with seeded rules covering at
  minimum `Dfc/TreeCover → canadian_boreal` and `Dfb/TreeCover →
  canadian_mixed_wood`, and `fallback: tellus:vanilla_default`.
- `data/tellus/tree_palette/canadian_boreal.json` and
  `canadian_mixed_wood.json` containing the expected species IDs with
  placeholder weights and a sensible `trees_per_chunk`.
- `data/tellus/tree_species/canadian/*.json` for each species referenced
  by the seeded palettes (black_spruce, jack_pine, paper_birch, balsam_fir,
  tamarack, sugar_maple as the initial set), each pointing at an NBT path
  that may not exist yet.

Missing NBTs at runtime log a one-time warning and that species is skipped
during placement, so the framework loads and runs end-to-end before any
NBT exists.

## Stub scope

Country mapping files created with `fallback: "tellus:vanilla_default"`
and an empty `rules` list:

- `data/tellus/tree_mapping/usa.json`
- `data/tellus/tree_mapping/russia.json`
- `data/tellus/tree_mapping/brazil.json`
- `data/tellus/tree_mapping/china.json`
- `data/tellus/tree_mapping/australia.json`

Each gets a `CountryContainment` class returning `false` from `contains()`
with a TODO comment pointing at the eventual polygon source. No NBT files,
no palettes.

## Error handling and validation

At load time:
- Each mapping file is parsed; missing `palette` IDs are errors that prevent
  the mapping from registering (logged with file path and rule index).
- Each palette file is parsed; missing `species` IDs are errors that prevent
  the palette from registering.
- Each species file is parsed; the referenced NBT is *not* required at load
  time (NBTs are loaded lazily on first placement and missing NBTs degrade
  to "skip this placement" with a one-time warning).
- `vanilla_default` is built in to the runtime and does not need a file.

At runtime:
- Surface block rejection (water/ice/non-soil) is silent and skips that
  placement attempt without retrying.
- If country lookup or palette load throws, the placer falls back to
  vanilla biome tree features for that chunk and logs once per session.

## Testing

- **Unit tests** for `CountryRegistry`: registration order, fallback when no
  containment hits, mapping rule ordering, `vanilla_default` sentinel
  handling.
- **Unit tests** for palette weighted picking: deterministic given a seeded
  random, weights normalize correctly, single-species palettes work.
- **Unit tests** for mapping loader: malformed JSON rejected, missing
  palette ID rejected, unknown koppen/esa codes rejected.
- **Integration test** generating a chunk inside the Canada polygon and
  asserting trees-from-the-canadian-boreal-palette are placed (using a
  seeded RNG and stub NBTs that are 1×1×1 marker blocks).
- **Integration test** generating a chunk outside any country: asserts
  vanilla tree features ran and our placer produced no extra placements.

## Migration / compatibility

- Existing saves on `feature/regional-trees` regenerate new chunks through
  the new feature; previously-generated chunks are unaffected.
- The `vanilla_default` delegate is a true delegate — any biome that
  doesn't end up under a country mapping behaves *identically* to today.
- No new world-gen settings fields beyond unlocking `trees_density`; the
  codec doesn't gain or lose any keys, so save/load is unchanged.
