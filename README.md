# Alex's Butchery

[Download 0.2.2](https://github.com/otectus/AlexsButchery/releases/tag/v0.2.2) · [Changelog](CHANGELOG.md)

Butchery add-on for Alex's Mobs (Forge 1.20.1), covering 82 of its 90 creatures: every one with something organic to
butcher (Spectre, Sunbird, Guster, Rocky Roller, Underminer, Enderiophage, Comb Jelly and the plant-like Flutter are left
out). Cleaver kills drop a carcass, hang it on Butchery's hook, bleed it into a blood grate, take the head, skin or pluck
it, cut it up for meat and Butchery's organs, mount the head at the taxidermy table and cure the skin on the skin rack. Carcasses are drawn with Alex's
Mobs' models and saved appearance, including terrapin colors and patterns. Skinned stages use separate tissue
materials; skeletons use skull, rib, spine and limb geometry fitted to the animal's model.

Acid dissolves a whole carcass into its skeleton, and tiger, snow leopard, grizzly and bison pelts make rugs. A harvested
head keeps the creature's look (a white tiger, a tusked elephant, a moose with or without antlers), and so do the
trophy mounted from it at Butchery's taxidermy table and the rug crafted with it; a white tiger's rug lies with a white
pelt. Trophies hang on a wall, facing out, and drop when the wall goes. Heads and trophies are selected by the head as
drawn, so a trunk or a whale's jaw reaching past its block can be targeted. A tuskless elephant gives no tusks and a moose
that has shed its antlers none. Jade shows the next step for these carcasses and for Butchery's own (below). Butchery's
Patchouli guide gains an "Alex's Mobs" category, advancements join Butchery's tree, and Farmer's Delight's cutting board
takes the new meats. With a cooking addon installed, carcasses yield that addon's meats (below).

In 0.2.2, trophy contact is measured from the first solid skull/neck anatomy and the actual wood face, not antlers,
ears, tusks, flat detail planes or plaque trim. Small trophies use a wall plaque rather than the native tilted tabletop
stand; plaque collision and selection include the real size and trim. The current bounds export is geometry revision 5;
the [0.2.2 validation report](docs/0.2.2-head-mount-validation.md) separates its production-logic runtime checks from
the subsequent package check.

A carcass keeps what was done to it: picked up while it bleeds or while acid works on it, it carries on where it was put
down (a carcass requiring a hook waits on the ground until it is hung again), so its blood and acid are paid once. Acid works
alone: while it dissolves a carcass, no tool cuts, bleeds or skins it. Instant Bleeding drains the same blood as waiting
(250, 1000 or 2000 mB), with Butchery 5.2 and 5.3 alike. Hanging a carcass and putting a skin on Butchery's rack follow
the usual placement rules (Adventure mode, spawn protection, protection mods), and a skin rack destroyed any way gives
back its skin or leather, and Butchery's rack as any block would (an explosion destroys some).

Which weapons turn a kill into a carcass follows Butchery's own options. With "Only Cleavers Drop Carcasses" on, a
cleaver or Butcher's Touch is needed. Farmer's Delight's knives (and anything else in `farmersdelight:tools/knives`)
count only when Butchery 5.3's "Farmers Delight Knives Drop Carcasses" is on; that option is in `config/Butchery.toml`
under `[Farmers Delight Compatibility]`, off by default. Butchery 5.2 has no such option and treats those knives as
cleavers, so with 5.2 they always butcher. A refused kill leaves the creature's ordinary drops alone.

## Carcass size, carrying and death placement

Dropped carcass items retain the dimensions of their placed lying form, including size variants. Inventory icons,
item frames and head attachments fit the complete visible form; ordinary hands and Carry On use a body grip with
separate first-person framing. Small creatures remain small. The client option `groundItemMaxSpan` is an optional
visual cap in blocks (`0` preserves world size); it changes no weight, blood, loot or selection geometry.
Display contexts added by other Forge mods inherit their declared fallback's presentation.

Carry On is optional. Its Forge 1.20.1 integration reads the carried player's saved block state and `tile` appearance,
including cut stages, without ticking a preview. Explicit Carry On replacement models take precedence. Set the client
option `carryOnPresentation = false` to disable the built-in adapter. Existing giant items remain recoverable: being
placed at death and being unable to hang do not prohibit item recovery or override Carry On's pickup rules.

Per-world settings live in `serverconfig/alexsbutchery-server.toml`. Restart/reopen the world after changing them;
client resource reload does not change server gameplay. Defaults preserve the eight existing block-at-death species.
This optional large-game preset changes only death representation:

```toml
[carcasses]
deathSpawnMode = "SPECIES_DEFAULT"
deathSpawnOverrides = ["alexsmobs:moose=PLACED_BLOCK", "alexsmobs:bison=PLACED_BLOCK", "alexsmobs:rhinoceros=PLACED_BLOCK"]
groundBleedingMobs = []
```

To also allow those animals to bleed on the ground, set
`groundBleedingMobs = ["alexsmobs:moose", "alexsmobs:bison", "alexsmobs:rhinoceros"]`.
They retain their blood yield, hookability, weight and trophy size. With placement alone, move a moose to a hook to
bleed it. `deathSpawnMode = "PLACED_BLOCK"` requests blocks for every eligible species; an explicit `=ITEM` exception
wins over that setting. Kill eligibility and official-addon deferral always take precedence.

Entries use full entity registry IDs: the cave centipede is `alexsmobs:centipede_head`, not the pose/definition key
`centipede`. Invalid/unsupported entries are logged and ignored; the first valid entry wins when a species is listed
twice. Failed/protected placements retain ordinary death drops. The bounded vertical resting search does not overwrite
an existing carcass or force-load terrain. Thrown, mined and hopper-delivered items remain ordinary inventory items.
Whole-item transfers preserve blood/acid progress; invalid bleeding positions pause it, and mid-cut blocks still yield scraps.

## Cooking addons

Two different add-ons supply Alex's Mobs foods for Farmer's Delight, and both are supported:
**Alex's Mobs Delight** (`alexsmobsdelight`, by BF_Meow and rawchickenNEG) and **Alex's Delight** (`alexsdelight`, by
NCP Bails). Both need Farmer's Delight. With either installed, a carcass yields the addon's own ingredient for each
product it verifiably supplies, so the addon's cutting, cooking and meal recipes take it directly; everything else a
carcass gives (hides, fur, scutes, fat, hooves, bones, heads, Butchery's organs) is unchanged.

| Installed | Grizzly bear | Crocodile | Bison |
|---|---|---|---|
| Neither | Alex's Butchery bear meat | Alex's Butchery reptile meat | Butchery steak assortment (8-14) |
| Alex's Mobs Delight | its raw bear meat | its raw crocodile meat | its raw bison meat (4-7) |
| Alex's Delight | Alex's Butchery bear meat | Alex's Butchery reptile meat | its raw bison (4-7) |
| Both (default) | Alex's Mobs Delight | Alex's Mobs Delight | Alex's Mobs Delight |
| Both, Alex's Delight first | Alex's Mobs Delight | Alex's Mobs Delight | Alex's Delight |

Alex's Delight has no bear or crocodile meat, so those stay with Alex's Mobs Delight or this mod's own. The other
creatures covered: dropbear and sea bear (bear meat), caiman (crocodile meat), anaconda, emu, seagull and hummingbird
(each of the three birds gives one whole bird), froststalker, tusklin, seal, orca and cachalot whale (whale meat),
bunfungus (Alex's Mobs Delight legs or Alex's Delight bunfungus), terrapin and alligator snapping turtle (turtle meat
and their shells), mantis shrimp (the tail matching its colour), mimic octopus tentacles, Devil's Hole pupfish and
mudskipper. Shared inputs are never converted for every creature: rattlesnakes and komodo dragons keep reptile meat,
other birds game bird, cows Butchery's steaks.

- **Which addon wins** a product both supply: the world's `serverconfig/alexsbutchery-server.toml`, `[compat]`:
  `providerPriority` (default `["alexsmobsdelight", "alexsdelight"]`), `disabledProviders`, and
  `preferExternalProducts = false` to keep every carcass on this mod's own items. They take effect on restart. A
  datapack can choose per creature (`docs/compat-profiles.md`, examples in `docs/compat-examples/`).
- **Nothing paid twice**: a carcass kill removes from the death drops what the carcass yields again, for that creature
  only: its meat from either addon, raw or cooked by a burning death, and the parts its own death table shares with its
  carcass (the feathers of the birds that drop them, ink, the bones of the bone creatures, maggots, the bunfungus's red
  mushrooms, the tusklin's porkchops). A kangaroo keeps any feathers it drops. Claws, tails, ears, hats, shrimp tools, eggs,
  effect-holding seagulls and any stack carrying item data stay. With `replaceMobDrops = false` the creature keeps all
  its drops as well as dropping the carcass.
- **Older items**: bear, froststalker, tusklin, seal and whale meat and both turtle shells craft one-for-one into Alex's
  Mobs Delight's, bunfungus meat into Alex's Delight's (stacks carrying item data are left as they are). Reptile meat,
  game bird, tropical fish, Butchery steaks and mantis shrimp tails cannot be converted: a loose stack no longer tells
  which creature it came from. Those cook as before.
- **Both addons**: both cut kangaroo meat, moose ribs and catfish and both cook centipede legs. The preferred addon
  owns those recipes; the other's cuts come from the owner's cut on the cutting board and its cooked centipede leg from
  the cooking pot, so both addons' meals stay reachable (`resolveKnownRecipeConflicts`).
- JEI's Butchering tab shows what the server's loaded tables actually yield, each link of a variant chain with its own
  counts and what carcasses no variant matches get; Jade names the addon supplying a carcass's meat only while its stage
  table still yields it; the creative tabs hide this mod's meat only where nothing yields it. Operators can run
  `/alexsbutchery compat status`, `validate` and `explain <creature>`.

## Jade

For this mod's carcasses Jade shows the next step and its tool, the steps done, bleeding and acid progress and which
cooking addon supplies the meat still to come; for its skin rack, the skin and its curing. For Butchery's own carcasses
and corpses (167 blocks in Butchery 5.2 and 5.3: every carcass and corpse they register, except player corpses) it shows
the next step and tool as Butchery's own state says, "Bleeding..." while Butchery bleeds one (without a percentage: 5.2
keeps no elapsed time, and 5.3's timer length differs per carcass), when it must be hung first, and when a frog must go
on a metal tray. For Butchery's skin rack it shows the
skin, the next step and the cured leather, with a curing percentage on Butchery 5.3 (which counts the time) and
"Curing..." on 5.2. Each is a separate Jade option. Player corpses are left to Butchery 5.3's own Jade line (5.2 has
none). The guidance was recorded from Butchery 5.2 and 5.3 (their patch releases, such as 5.3.1, use the same) and is
checked against them; with any other Butchery version Jade shows nothing for Butchery's blocks. Butchery 5.3 cannot bleed piglin brute corpses or brown horse carcasses (their
bleeding handlers are no longer called), so Jade offers no next step for those there.

Tested with (Forge 1.20.1, 47.4.23, the lowest the mod accepts): Butchery 5.2, 5.3 and 5.3.1, Alex's Mobs 1.22.9, Citadel 2.6.3 (also the lowest), Farmer's Delight
1.20.1-1.3.4, Alex's Mobs Delight 1.1.0, Alex's Delight 1.5, Immersive Tanning 1.1.1, Butchery: Alex's Mobs Addon
1.0.1-BETA, JEI 15.20, Jade 11.13, Patchouli 85, Carry On 2.1.2.7, and in the Ultima pack (Butchery 5.3.1, JEI 15.74). Alex's Delight 1.6
targets NeoForge 1.21.1 and is not supported. Other food mods are not covered unless a datapack profile adds them.

## Building

Butchery is All Rights Reserved and is not on any Maven, so the build reads it from `libs/`
(`libs/butchery-5.2-forge-1.20.1.jar`, gitignored). Download it from CurseForge into `libs/` before building.
The mod runs on Butchery 5.2, 5.3 and 5.3.1. To build and test against 5.3, put `butchery-5.3-forge-1.20.1.jar` in `libs/` and add
`-Pbutchery_artifact=butchery-5.3-forge` to any Gradle command. Alex's Mobs and Citadel are fetched from the Modrinth
Maven. For 5.3.1, use `butchery-5.3.1-forge-1.20.1.jar` with `-Pbutchery_artifact=butchery-5.3.1-forge`.
Versions live in `gradle.properties`.

- `.mcmod-tools/gradlew-quiet.sh <dir> compileJava compileGameTestJava compileSmokeTestJava` - compile
- `... runData` - regenerate `src/generated/resources` from the mob and item tables
- The posed anatomy that carcasses, heads and trophies are selected by (`src/main/resources/data/alexsbutchery/carcass_bounds.json`)
  is not data generation output: `... runClient -PsmokeTest -PexportBounds` writes `run/smoketest/carcass_bounds.json`,
  copy it there. `build` fails (`checkCarcassBounds`) when it is missing, malformed, misses a form, or was exported from
  other poses, a different geometry revision (currently 5), Alex's Mobs or Citadel.
- `... runGameTestServer -PnativeProbe` (once per Butchery version, with `-Pbutchery_artifact=butchery-5.3-forge` for 5.3),
  then `python3 tools/native_butchery_table.py run-gametest/native-butchery-walk-5.*.json run-gametest/native-skin-rack-5.*.json`
  - re-record Butchery's own carcass and rack behaviour for Jade (`src/main/resources/data/alexsbutchery/native_butchery.json`)
- `... build smokeTestJar` - the mod jar and the test agent jar in `build/libs/`
- `python3 tools/generate_textures.py --write` - export the item icons from `art/butchery_art.py`

## Testing

- `... runGameTestServer` - headless GameTests; the pass/fail line is in `run-gametest/logs/latest.log`. They also replay
  every step the Jade table records for Butchery's own carcasses against the installed Butchery.
- `... runGameTestServer -PwithFarmersDelight` - the same with Farmer's Delight loaded, which adds the knife tests.
- `-PwithAlexsMobsDelight`, `-PwithAlexsDelight` (each brings Farmer's Delight; give both for the dual-addon profile),
  `-PwithImmersiveTanning`, `-PwithOfficialAddon` (reads `libs/butchery_alexs_mobs-1.0.1-BETA-forge-1.20.1.jar`),
  `-PwithoutJei`, `-PwithoutJade`, `-PwithoutPatchouli` select the optional mods of any dev run. The addons come from
  CurseMaven (ids in `gradle.properties`). A GameTest and the smoke test fail when the loaded mods differ from the
  flags, and the task fails unless every registered GameTest ran and passed. Run the core, Farmer's Delight, Alex's Mobs
  Delight, Alex's Delight and both-addons profiles against both Butchery versions.
- `... runClient -PsmokeTest` - stages every carcass and screenshots them to `run/screenshots/`
- `... runClient -PsmokeTest -PperfBench=<label>` - frame-time and targeting benchmark in fixed scenes, written to
  `run/smoketest/perf-<label>.json`; `-PperfSeconds`, `-PperfWarmup` and `-PperfSamples` set the sampling
- `... runClient -PsmokeTest -PsaveCompat=create` (with an earlier build), then `-PsaveCompat=verify` - an old-save
  fixture and its check, `run/smoketest/save-compat*.json`
- `... runClient -PsmokeTest -PremoteServer=host:port` - against a dedicated server: cutting, breaking and refused
  actions, the client's yield view against the server's, a trophy hung on a wall and dropped with its look, Adventure
  mode refusals, a bleed that waits while its chunk is unloaded, Jade's server answer for Butchery's carcasses, and last,
  no longer an operator, refusals inside spawn protection; written to `run/smoketest/remote-result.json`. The dev
  player must be an operator there, beside a second operator and with `spawn-protection` above 0 (a server without
  operators has no spawn protection); the run deops it, so stop the server from its console afterwards
- `... runClient -PsmokeTest -PcompatWorlds` - two worlds with different compatibility settings in one client: JEI and
  the creative tabs follow each world and are cleared on leaving, `run/smoketest/compat-worlds-result.json`
- `python3 tools/packtest/ultima_pack_test.py` - runs the built jars in an isolated copy of the Ultima CurseForge
  instance and writes `build/packtest/<label>/summary.json`

## Resource packs

Resource packs may change the pose profiles (`assets/alexsbutchery/pose_profiles/`), which change how carcasses are
drawn. What can be selected and cut stays the anatomy bundled with the mod, which the server also uses, so a changed pose
can be drawn outside its outline. The client logs which profiles a pack changed.

## Adding a mob

Add a row to `def/MobDefs.java` (archetype helper, drops per stage, replaced drops), a pose profile under
`assets/alexsbutchery/pose_profiles/<mob>.json` if the name heuristics do not fit, then `runData`. A lying carcass rests
on its lowest solid part; in the profile's `lying` block, `ignore` names parts that should not hold the body up (ears,
flippers, dangling legs) and `rotations` sets joint angles in degrees. Then re-export the bounds (`-PexportBounds`).

Presentation tuning may be added under a pose profile's `presentation` object without changing world anatomy:

```json
{"presentation":{"third_person":{"scale":1.0,"max_span":4.0,"offset":[0,0,0],"rotation":[0,0,0]}}}
```

Keys are `gui`, `ground`, `fixed`, `head`, `none`, `first_person`, `third_person`, `carry_first_person` and
`carry_third_person`. Offsets use blocks in the rotated display frame; rotations use degrees. Scale multiplies that
context's default; `max_span` caps its longest dimension (`0` uses the context default). Ground and `none` retain world
scale and contact height; `none` is a neutral local lying presentation, not an implicit carrying context. Hands/carry
start at 0.65 anatomical scale, capped at 2.4/4 blocks for ordinary first/third person and 2.8/5 for carrying. GUI/fixed/head
always stay contained (offsets are ignored and scale cannot enlarge their frame). Invalid tuning falls back to defaults
with one warning per resource reload. `F3+T`, disconnect and world changes clear the bounded presentation caches.

`runClient -PsmokeTest -PitemReview` renders every registered form in all nine item contexts and writes matched
`run/screenshots/item_before_*.png` / `item_after_*.png` contact sheets. The before images reconstruct the captured
pre-fix source transforms. `-PwithCarryOn -PcarryReview` exercises the optional adapter; see the
[0.2.2 head-mount validation report](docs/0.2.2-head-mount-validation.md) for the current package and head-contact
evidence, and the [0.2.1 validation report](docs/0.2.1-carcass-validation.md) for its historical artifact versions
and completed runtime checks.
