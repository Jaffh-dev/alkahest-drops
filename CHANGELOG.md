# Changelog

All notable changes to Alkahest Drops. Versions follow `<minecraft>-<major>.<minor>.<patch>`; the API
(`com.jaffh.alkahestdrops.api`) follows semantic versioning.

## [1.20.1-1.0.0] - Unreleased

First release.

- Drop files (`mob_drops/`, `block_drops/`, format version 1): chance, count, `uniform` / `fixed_rolls` /
  `chained_rolls` / `max_health` / `hardness` / `damage_taken` behaviors, NBT, vanilla loot conditions and functions,
  experience drops, `enchant_bonus` for any enchantment.
- Removal files: remove items, tags, everything (`*`) or experience, with global and local conditions; drops of other
  namespaces only.
- Targets by id, `#tag`, `mod:*` and `*`.
- Bad-luck protection (`pity`), one-time drops (`first_kill` / `first_break`), per-player drops, and loot conditions
  `alkahestdrops:killed_by_real_player` / `broken_by_real_player`.
- Triggers beyond death: `shear`, `hit`, `lightning`, `periodic`.
- `on_drop`: glow and `run_function` when a drop lands.
- `/alkdrops` commands: `validate`, `files`, `sources`, `drops`, `simulate`, `reset`, `help`.
- JEI pages for every mob and block with their full drops (optional).
- API 1.0.0: `AlkahestDropsApi` (read drops, read and reset player progress), `OnDropEvent`, `DropFilesLoadedEvent`.
- English and Brazilian Portuguese.
