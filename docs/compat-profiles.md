# Compatibility profiles (introduced in Alex's Butchery 0.2.0; schema v1 remains current in 0.2.2)

How a carcass decides whose items it yields, and how a datapack changes that. Player-facing summary: README,
"Cooking addons". Working datapacks for every example below: `docs/compat-examples/` (each folder is a datapack; the
GameTests parse every one of them with the real loader).

## How a stage chooses its items

Every butchering stage table (`alexsbutchery:carcass/<mob>/<stage>`) is an ordinary loot table. Side products (fat,
hides, fur, scutes, hooves, bones, heads) are ordinary pools. A creature's replaceable products (its meat, a turtle's
shell, a mantis shrimp's tail, a mimic octopus's tentacles, a whole fish) are each one pool holding an
`alexsbutchery:compat_yield` entry:

```json
{"type": "alexsbutchery:compat_yield", "entity": "alexsmobs:bison", "group": "meat", "stage": "cut_1",
 "fallback": "alexsbutchery:carcass_native/bison/cut_1/meat"}
```

When the stage is cut, the entry asks the server's rules for that creature, group and stage. The first provider whose
profile applies gives its item; otherwise the `fallback` table (the native drops, exactly as 0.1.2 generated them) is
rolled in the same loot context. Global loot modifiers apply once, to the stage table. A pack that replaces a stage
table with its own table without this entry gets exactly what its table says: nothing is rewritten afterwards
(`compat-examples/plain_stage_table`).

Groups per creature: `meat` for every creature with a meat drop (every cut where it has meat; the bison's five steaks
are one group), `shell` (terrapin, alligator snapping turtle), `tail` (mantis shrimp), `tentacles` (mimic
octopus), `fish` (fish carcasses). `/alexsbutchery compat explain <creature>` lists them with their native fallback.

## Provider order

For each creature, group and stage the server builds one chain, then appends the native drops:

1. A selection file for that creature (and group), if any; `"native"` in it ends the chain there.
2. `compat.providerPriority` from the server config (default `["alexsmobsdelight", "alexsdelight"]`).
3. A provider not listed anywhere, if exactly one such provider has a profile here. Two unlisted providers at the same
   point are ambiguous: neither is used, and `/alexsbutchery compat validate` names them.

Two profile files supplying the same provider for the same creature, group and stage are ambiguous too, as are two
selection files for the same creature and group. A provider in `compat.disabledProviders` is ignored entirely.
`compat.preferExternalProducts = false` gives every creature its native items.

A profile applies only while all its `required_mods` are loaded and every item it can yield is registered (not air).
A missing item in an active profile is an error naming the file, creature, stages and item; that profile is skipped
and the next link answers. A profile never yields part of a group while its other outputs are missing.

## File kinds and schema (`schema_version` 1)

Files live in `data/<namespace>/alexsbutchery/compat_profiles/<path>.json`; the resource id is
`<namespace>:<path>`. Normal datapack priority decides which file wins for the same id. Unknown keys are errors;
`comment` is allowed everywhere.

### Yield profile

```json
{
  "schema_version": 1,
  "entity": "alexsmobs:crocodile",
  "provider": "alexsmobsdelight",
  "required_mods": ["alexsmobsdelight"],
  "groups": {
    "meat": {
      "item": "alexsmobsdelight:raw_crocodile_meat",
      "count_mode": "native_group",
      "death_equivalents": ["alexsmobsdelight:raw_crocodile_meat", "alexsmobsdelight:cooked_crocodile_meat"]
    }
  }
}
```

| Key | Meaning |
|---|---|
| `entity` | The creature's entity id (one of this mod's carcasses). |
| `provider` | Any id (`[a-z0-9_.-]`), normally the supplying mod's id; `native` is reserved. |
| `required_mods` | Mod ids that must all be loaded; otherwise the profile is inactive (not an error). |
| `groups.<group>.item` | The item yielded, or instead: |
| `groups.<group>.variants` | `{"nbt": "Variant", "items": {"0": "...", "1": "..."}}`: an integer tag of the carcass's saved mob snapshot chooses the item. A carcass without the tag, or with an unlisted value, falls to the next link (old carcasses get the native item; no colour is invented or rolled). |
| `count_mode: native_group` | The group's native fallback is rolled exactly as without the profile (same counts, chances, functions and loot context) and its total count is given as this item. Optional `stages` (list) restricts the stages; default: every stage where the group has native drops. |
| `count_mode: per_stage` | `counts` per stage: a number, or `{"min", "max", "chance"}` (0-64, `min <= max`, `0 < chance <= 1`). The stages are the keys of `counts`; a count of `0` replaces the native drops of that stage with nothing. |
| `death_equivalents` | Items an ordinary kill of this creature drops for the same product. A carcass kill removes them from the death drops (see below). Cooked forms produced by a burning death are added automatically from the server's smelting recipes. |

Stages: `head`, `skin`, `pluck`, `cut_1`, `cut_2`, `cut_3`; only stages where the group has native drops are valid.

### Selection

```json
{"schema_version": 1, "entity": "alexsmobs:bison", "groups": ["meat"], "provider_priority": ["alexsdelight"]}
```

`groups` is optional (default: all of the creature's groups). Listed providers come first, then the server order;
`"native"` stops the list (`compat-examples/keep_native_crocodile`).

### Disable

```json
{"schema_version": 1, "disabled": true}
```

Placed at a built-in profile's path (for example
`data/alexsbutchery/alexsbutchery/compat_profiles/alexsmobsdelight/grizzly_bear.json`), it removes that profile. A pack
higher in the datapack order always wins, so a disable survives the defaults (`compat-examples/disable_grizzly`).

## Reloads and malformed data

Profiles are read with every data reload. A file that is not valid JSON or breaks the schema is an error with its
path; if any file of a reload is malformed, the whole reload's profile set is rejected and the server keeps the rules
it had (at first start: native items for every creature), so a half-applied set never reaches players. The rules are
rebuilt atomically after each successful reload, before the reload's recipes and tags are sent to players, and are
dropped when the server stops. Config edits take effect on restart (Forge reloads config files live; the rules keep
the settings the server started with and log that a restart is needed).

## Death drops

With `replaceMobDrops = true` (default), a kill that produced a carcass removes from the death drops, per creature:
the creature's own native replaced parts that are still in `#alexsbutchery:replaced_by_carcass`, the
`death_equivalents` of every active profile for that creature (the preferred addon's and the other one's), their
cooked forms, and any item a pack added to that tag (removed for every carcass kill, as before). Specialty parts no
profile names are kept: crocodile claws and tails, bunfungus ears and hats, mantis shrimp tools, effect-holding
seagulls, eggs, dusts. A stack carrying item data is never removed. Nothing is removed when no carcass was made
(babies, refused weapons, disabled or deferred creatures, no room for a floor carcass) or with `replaceMobDrops =
false`. The filter runs in the event's last phase, after other mods' additions (Alex's Mobs Delight's Dimensional
Slicer), and never touches items already on the ground.

Packs that removed an entry from `#alexsbutchery:replaced_by_carcass` (with `replace: true`) keep that drop as before;
entries packs add apply to every carcass kill as before.

## Built-in profiles and budgets

Audited against Alex's Mobs Delight 1.1.0 (`alexsmobsdelight`) and Alex's Delight 1.5 (`alexsdelight`) for Forge
1.20.1. Food values are hunger points raw/cooked.

| Creature | Group | Alex's Mobs Delight | Alex's Delight | Budget |
|---|---|---|---|---|
| Grizzly bear, dropbear, sea bear | meat | `raw_bear_meat` | native | native counts |
| Crocodile, caiman | meat | `raw_crocodile_meat` | native | native counts |
| Anaconda | meat | `raw_anaconda` | native | native counts |
| Bison | meat (5 steaks) | `raw_bison_meat` | `raw_bison` | 1-2, 2-3, 1-2 (4-7) |
| Emu, seagull, hummingbird | meat | `raw_emu`, `raw_seagull`, `raw_hummingbird` | native | one whole bird |
| Froststalker | meat | `raw_froststalker_meat` | native | native counts |
| Tusklin | meat | `raw_tusklin_meat` | native | native counts |
| Seal | meat | `seal_meat` | native | native counts |
| Orca, cachalot whale | meat | `whale_meat` | native | native counts |
| Bunfungus | meat | `raw_bunfungus_leg` | `raw_bunfungus` | AMD 1, 1, 0-1 legs; AD native counts |
| Terrapin, alligator snapping turtle | meat / shell | `raw_turtle_meat` / `terrapin_shell`, `alligator_snapping_turtle_shell` | native | native counts |
| Mantis shrimp | tail | `mantis_shrimp_tail_green/red/lime/white` by `Variant` 0/1/2/3 | native | one tail |
| Mimic octopus | tentacles | `mimic_octopus_tentacle` | native | native counts |
| Devil's Hole pupfish, mudskipper | fish | `raw_devils_hole_pupfish`, `raw_mudskipper` | native | native counts |

Kangaroo meat, moose ribs, catfish, lobster tails, centipede legs, maggots and the lost tentacle stay Alex's Mobs items:
both addons cook and cut those directly.

Why the explicit budgets:

- Bison: the native assortment is 8-14 Butchery steaks (chuck 2/8, ribeye 3/8, sirloin 2/7, T-bone 5/10, rump 4/9;
  22-37 raw, 66-114 cooked). Alex's Mobs Delight's bison meat is 6/12 and cuts into two cubes; Alex's Delight's raw
  bison is 4/10 and cuts into two mince (patties 5). 4-7 whole pieces give 24-42/48-84 and 16-28/40-70, at least each
  addon's own death drop (3-4 and 2-5) and without multiplying their cuts. The budget is the plan's proposal, kept
  after this audit.
- Emu, seagull, hummingbird: the addon item is the whole bird (the emu cuts into two legs and three chicken cuts). A
  one-for-one swap would turn one bird into two or three; one per carcass matches the addon's own death drop.
- Bunfungus: an Alex's Mobs Delight leg is 5/9 against our portion's 3/7; two to three legs per creature (its own
  drop is 1-2) instead of four to seven. Alex's Delight's raw bunfungus is a portion (4/7) and keeps native counts.
- Mantis shrimp: one shrimp, one tail, coloured by its saved Variant; ours gives 1-2 portions.

Where the addon item is a portion like ours the native counts stay (bear 4/8 against 3/8, crocodile 2/6 equal,
anaconda 4/8 against 2/6 with Alex's Mobs Delight's own drop 3-5, turtle, tentacles, whole fish). Tusklin (6/12 against
3/8) and whale (10/16 against 4/10) keep the one-for-one counts 0.1.x already gave with Alex's Mobs Delight.

## Recipes

- One-way conversions `alexsbutchery:compat/<provider>/<item>` (crafting, 1:1, raw to raw, cooked to cooked) for our
  items that are the same product whatever creature they came from: bear, froststalker, tusklin, seal and whale meat
  and both turtle shells into Alex's Mobs Delight's; bunfungus meat into Alex's Delight's. Nothing converts reptile
  meat, game bird, tropical fish, Butchery steaks or a mantis shrimp tail: a loose stack no longer knows its creature.
  A stack carrying item data does not match (Forge's strict `forge:nbt` ingredient), so no data is silently lost.
  Removed for disabled providers and in native mode. No recipe turns an addon item back into ours.
- Tag entries (optional, `replace: false`): Alex's Delight's kangaroo shank and loose moose rib join Alex's Mobs
  Delight's raw/cooked kangaroo and moose meat tags; Alex's Mobs Delight's cooked kangaroo slice joins
  `alexsdelight:cooked_kangaroo`; its cooked bunfungus leg joins `alexsdelight:cooked_bunfungus`.
- With both addons installed (`compat.resolveKnownRecipeConflicts = true`): both cut `alexsmobs:kangaroo_meat`,
  `moose_ribs` and `raw_catfish` and both cook `alexsmobs:centipede_leg`. The provider first in the server order owns
  the shared input; the other's recipe is removed by its known id, only while it is still exactly the recipe that addon
  ships, compared through the recipe's own serializer (tool, every output with its count, chance and item data,
  ingredient item data, cooking time and experience). A pack's or script's change to any of these keeps the recipe, and
  the conflict is reported. The other addon's product stays reachable through
  `alexsbutchery:compat_conflict/...`: its cut from the owner's cut of the same size on the cutting board (1:1), or its
  cooked centipede leg from the cooking pot with the same experience. A pack's own recipe at that id is never replaced or
  duplicated: it stands in for the alternate if it already makes the same product from the same input, and otherwise
  the conflict is left unresolved. A conflict is applied only as a whole: if its alternate cannot be built, its id is
  taken, or it would loop back through other single-ingredient recipes, both recipes stay and `/alexsbutchery compat
  status` reports it unresolved. Every application starts from the recipe list as loaded. With the option off both
  recipes stay and the conflict is reported.

## Diagnostics

`/alexsbutchery compat status`, `/alexsbutchery compat validate` and `/alexsbutchery compat explain <creature>`
(operators, read only) print installed providers and versions, settings, loaded profile files and their packs, the
chain per group and stage, the native fallback, rejected profiles with reasons, recipe conflicts, and the death drops
removed or kept. The server logs one summary line per build and each error once; `compat.debugLogging` adds the rule
list and up to 20 death-drop removals a minute.
