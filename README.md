# Alkahest Drops

[![Build and test](https://github.com/Jaffh-dev/alkahest-drops/actions/workflows/build.yml/badge.svg)](https://github.com/Jaffh-dev/alkahest-drops/actions/workflows/build.yml)

Data-driven mob and block drops for Minecraft Forge. Datapacks and mods add, remove and tune drops with JSON files:
chances, roll-based counts, enchantment bonuses, bad-luck protection, one-time and per-player drops, drops on shear,
hit, lightning or over time, and actions when a drop lands. Everything is checked when the files load, can be
simulated in game, and shows in JEI.

- **Minecraft** 1.20.1, **Forge** 47.4 or newer
- **Runs on the server**: drops, commands and checks all happen server side; players need the mod (with JEI) only to
  see the JEI pages
- **Optional**: JEI, which shows every mob and block with its full drops

## A first drop file

`data/mypack/mob_drops/zombie_diamond.json` in a datapack gives zombies a 2% diamond, +1% per Looting level up to 5%,
and guarantees it to a player after 50 kills without one:

```json
{
  "version": 1,
  "entity": "minecraft:zombie",
  "drops": [
    {
      "item": "minecraft:diamond",
      "chance": 0.02,
      "count": { "min": 1, "max": 1 },
      "drop_behavior": { "type": "uniform" },
      "enchant_bonus": [
        { "enchant": "minecraft:looting",
          "on_enchant": { "bonus_chance": { "bonus_chance_per_level": 0.01, "max_bonus_chance": 0.05 } } }
      ],
      "pity": 50
    }
  ]
}
```

Block drops work the same way in `block_drops/` with `"block"` instead of `"entity"`. Removal files (`"removals"`)
take drops away, from the loot table, other mods or other datapacks.

## Commands

`/alkdrops` (permission level 2): `validate` lists problems in the loaded files, `files` and `sources` show what is
loaded and what adds an item, `drops` shows everything that applies to a mob or block, `simulate` rolls drops many
times without killing or breaking anything, and `reset` clears a player's progress. `/alkdrops help` explains each one.

## For mod developers

- **No dependency needed** to ship drops: put `mob_drops/` and `block_drops/` files in your mod's resources. They are
  ignored when Alkahest Drops is not installed.
- **API** (`com.jaffh.alkahestdrops.api`, semantic versioning from 1.0.0): `AlkahestDropsApi` reads what drop files do
  to a mob or block and reads or resets a player's pity and one-time drops; `OnDropEvent` fires when a drop spawns;
  `DropFilesLoadedEvent` fires after every reload. Classes outside `api` are internal.
- Loot conditions and loot functions registered by any mod work inside drop files.

## Building

```
./gradlew build              # compiles, checks formatting (Spotless) and lint (Checkstyle)
./gradlew runGameTestServer  # runs every GameTest headless
./gradlew spotlessApply      # formats the code
```

Enable the pre-push hook (runs both before every push) once per clone with `git config core.hooksPath .githooks`.

## License

Copyright (C) 2026 Jaffh.

Alkahest Drops is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
Public License, version 3 ([COPYING.LESSER](COPYING.LESSER)), which builds on the GNU General Public License, version 3
([COPYING](COPYING)). Other mods may depend on it under any license; changed versions of Alkahest Drops itself must stay
under the LGPL, keep this copyright notice and publish their source.
