# Surface Shell Depth — Design

## Problem

Tellus's `EarthChunkGenerator` fills full vertical columns from `minAltitude`
up to local surface elevation for every chunk. On Earth-scale worlds the
distance between `minAltitude` and the local surface is often hundreds or
thousands of blocks (oceans especially), and almost none of that subterranean
volume is ever seen or used. Computing and writing those blocks is currently
the dominant cost of chunk generation for many players.

## Goal

Add a world-creation option that limits underground generation to a thin
"shell" of terrain following the local surface. When enabled, only `depth`
blocks below the local surface are filled; everything below that is left as
air. The cave/ore/lava systems are auto-disabled while shell mode is active
because they have no meaningful volume to operate in.

This is a performance/sandbox setting and intentionally breaks survival
balance. The UI labels it as such.

## Non-goals

- No bedrock floor or void barrier. Players can fall out of the world below
  the shell. This is acceptable for the intended use case.
- No per-biome or per-region depth control. One global depth value.
- No changes to the Distant Horizons LOD generator. The LOD path samples
  surface elevation directly and never iterates the underground column, so
  there is no win to chase there.
- No retroactive change to existing saved worlds. The setting is off by
  default and absent from older save metadata; codec uses `orElse(false)` so
  old worlds keep generating exactly as before.

## Behavior

### Setting

Two new fields on `EarthGeneratorSettings`:

| Field                  | Type    | Range     | Default |
|------------------------|---------|-----------|---------|
| `surfaceShellEnabled`  | boolean | —         | `false` |
| `surfaceShellDepth`    | int     | `[2, 256]`| `16`    |

When `surfaceShellEnabled` is `false`, `surfaceShellDepth` is ignored and the
generator behaves exactly as today.

### Generator behavior

In `EarthChunkGenerator`'s per-column terrain fill path, when shell mode is
on, compute `cutoffY = localSurfaceY - surfaceShellDepth` once per `(x, z)`
column using the elevation already sampled for that column. Skip block
placement for any `y < cutoffY`:

- For solid land columns: only the top `surfaceShellDepth` blocks below
  surface receive stone/dirt/etc.
- For ocean columns: only the top `surfaceShellDepth` blocks below the
  seafloor receive blocks. Water still fills from seafloor up to sea level
  as today.
- Surface block selection (grass, sand, gravel, etc.) is unchanged.

The skip is a short-circuit, not a post-pass: noise sampling and block-state
assignment below `cutoffY` are not performed at all. This is where the
compute savings come from.

### Auto-disable of geological features

When `surfaceShellEnabled` is `true`, cave generation, ore distribution, and
lava pools are suppressed at the *consumption* points — not by mutating the
stored settings values. The record continues to hold whatever the user set
for `caveGeneration`, `oreDistribution`, and `lavaPools`, so toggling shell
mode off restores those preferences exactly.

Three accessor helpers are added to `EarthGeneratorSettings`:

- `effectiveCaveGeneration()` → `caveGeneration && !surfaceShellEnabled`
- `effectiveOreDistribution()` → `oreDistribution && !surfaceShellEnabled`
- `effectiveLavaPools()` → `lavaPools && !surfaceShellEnabled`

All worldgen code that currently reads `settings.caveGeneration()`,
`settings.oreDistribution()`, or `settings.lavaPools()` to decide whether to
*emit* a feature is updated to read the `effective*` variant instead. UI
binding still reads the raw field so the user sees their stored choice when
shell mode is later disabled.

The grep for current usage of those three accessors defines the plan's
edit scope.

### UI

In the "Customize World Generation" screen, **Geological Settings** section
(where Cave Generation, Ore Distribution, Lava Pools already live):

- Add a new toggle row: **Surface Shell** (off by default).
- Add a numeric slider/field directly under it: **Shell Depth (blocks)**,
  range 2–256, default 16. Disabled when Surface Shell is off.
- When Surface Shell is on, grey out the Cave Generation, Ore Distribution,
  and Lava Pools rows and show a tooltip:
  *"Disabled while Surface Shell is active — no underground volume to
  generate in."*

Lang keys added to `en_us.json` only; other locales will fall back to
English until translated.

## Architecture

### File touch list

Per Minecraft version (mc1201, mc1211, mc261):

- `worldgen/EarthGeneratorSettings.java`
  - Add two record fields, constructor params, `with*` mutators, codec
    entries, DEFAULT entries, `SettingsBase` fields, force-override logic.
- `worldgen/EarthChunkGenerator.java`
  - Add short-circuit in the column fill loop using
    `settings.surfaceShellEnabled()` and `settings.surfaceShellDepth()`.
- World-creation customization screen (existing screen; exact class name TBD
  during plan phase — search for the screen that already renders Cave
  Generation / Ore Distribution / Lava Pools rows).
  - Add toggle + depth field + grey-out logic.

Shared across versions:

- `src/main/java/.../worldgen/SurfaceShell.java` (new): tiny helper with the
  `cutoffY(localSurfaceY, depth)` function and the `MIN_DEPTH`/`MAX_DEPTH`
  constants. Lives in shared `src/main/java` so all three version trees
  reuse it; only the codec wiring and screen edits are duplicated per
  version.
- `src/main/resources/assets/tellus/lang/en_us.json`: new lang keys.

### Lang keys

```
"tellus.config.surface_shell": "Surface Shell"
"tellus.config.surface_shell.tooltip":
  "Generates only a thin layer of terrain below the surface to save compute.
   Caves, ores, and lava pools are disabled while this is on."
"tellus.config.surface_shell_depth": "Shell Depth (blocks)"
"tellus.config.surface_shell_depth.tooltip":
  "How many blocks below the surface to generate. Lower is faster."
"tellus.config.geological.disabled_by_shell":
  "Disabled while Surface Shell is active."
```

### Codec compatibility

Both new fields use `Codec.BOOL` / `Codec.intRange(2, 256)` with
`.fieldOf(...).orElse(DEFAULT.surfaceShellEnabled())` /
`.orElse(DEFAULT.surfaceShellDepth())`. Worlds saved before this change have
neither key present; decode falls back to defaults and the world generates
identically to today.

## Tests

New tests under `src/test/java/com/yucareux/tellus/worldgen/`:

1. **`SurfaceShellTest`** — pure-function tests on the cutoff helper:
   - `cutoffY(100, 16) == 84`
   - depth at min boundary (2) and max (256) returns expected values
2. **`EarthGeneratorSettingsShellTest`** — settings-level tests:
   - With `surfaceShellEnabled=true` and `caveGeneration=true`,
     `caveGeneration()` returns `true` (raw value preserved) but
     `effectiveCaveGeneration()` returns `false`. Same for ore/lava.
   - With `surfaceShellEnabled=false`, all three `effective*` accessors
     pass the raw value through.
   - Codec round-trip with shell fields present preserves values
   - Codec round-trip on a settings map missing both shell keys yields
     `surfaceShellEnabled=false` and `surfaceShellDepth=16`
3. **`EarthChunkGeneratorShellTest`** (or integration-style) — verify that
   with shell mode on, a sampled column has air at `y < surface - depth` and
   the same blocks as the unshelled generator at `y >= surface - depth`.
   Uses a small synthetic elevation source so the test is fast and
   deterministic.

Tests are added under `src/test`, not duplicated per MC version, since the
helper and settings-level logic they cover are shared.

## Open questions resolved during brainstorming

- "2 blocks below the surface" interpreted as a **configurable** depth, not
  a literal 2. Default 16; user can crank it down to 2 for the literal
  ask, or up to 256 for a more generous shell.
- Below the cutoff: **air**. No bedrock floor.
- Cave/ore/lava: **auto-disabled** when shell mode is on (greyed out in UI).
- Oceans: **same rule**. Maximizes compute savings.
