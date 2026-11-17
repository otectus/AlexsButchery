# Changelog

## 0.2.2 — 2026-11-17

Head and trophy contact now follows the rendered solid anatomy and the actual wall plaque.

- Trophy skulls now reach the wood backing in every facing: the rear contact uses the wood face at 15/16 rather than
  the former assumed surface at 12/16. Head anchors use the first solid skull/neck cube, skipping root-owned moose antlers and
  descendant ears or tusks; an empty Cosmaw mouth root falls back to its solid descendants.
- Cube measurement now uses the real transformed corners of inflated or shrunken quads. This corrects the anatomy used
  for crow, hummingbird and skreecher contact and bounds. Loose heads likewise ignore flat detail planes as supports.
- Small trophies render a half-width, half-height regular wall plaque instead of Butchery's tilted native tabletop stand. Plaque collision and
  selection now cover the real size and lower trim, while head/rug offsets remain local to their facing.
- Reload clears cached head poses. The exported anatomy is revision 5 for this solid-head-contact and plaque geometry.
- Packaging excludes generated `.cache` files from the release jar.
- Verification: the pre-version-bump production logic passed 129 required GameTests, the geometry reload review
  (5,288 anatomy bounds, 2,780 ground contacts, 576 heads, 288 mount contacts and 288 loose-head contacts), and the
  item-context review (2,979 renders across 82 species and 331 forms). The 0.2.2 package then passed `build`,
  `compileGameTestJava` and `compileSmokeTestJava`; see `docs/0.2.2-head-mount-validation.md` for scope and evidence.

## 0.2.1 — 2026-11-15

Carcass presentation, carrying and configurable death placement, plus the 0.2.0 review fixes for head mounts,
Jade, processing, permissions, drops and dual-addon recipes.

- Dropped carcasses retain species and saved-size proportions. Every item display context has its own measured
  presentation; inventory icons include antlers, multipart bodies and trophy plaques without overflowing slots.
- Optional Carry On integration preserves variants and actual cut/skeleton stages, anchors bodies at the arms, and
  avoids terrain-dependent detached block rendering. Resource packs can tune item/carry presentation separately.
- Per-world death representation can be overridden per species, independently of blood, weight, trophy size and
  hookability. Ground bleeding is a separate option, with legacy behavior retained by default.
- Portable blood/acid progress is bounded and preserved; failed/protected hanging and death placement commit no
  consumption or success reward. Existing giants remain recoverable as items.

- Heads, trophies and rugs keep the creature's look. A harvested head carries it (a white tiger, a tusked elephant, a
  moose with or without antlers), Butchery's taxidermy table mounts it as it was, and the rug crafted with it keeps it;
  a white tiger's rug lies with a white pelt, in the world and in inventories. Breaking any of them drops it with the
  look. Heads harvested before this update stay the default look; nothing is guessed for them.
- Head mounts hang on walls: placed on the side of a sturdy block, facing out, refused with nothing to hang on, and
  dropped as one trophy, look and all, when the wall goes. A trophy saved before keeps its place until its wall changes.
- Heads and trophies are selected by the head as drawn, so an elephant's trunk and tusks or a whale's jaw reaching past
  the block can be targeted, with the same reach, walls, mining time and drops as carcasses. Loose-head collision is unchanged.
- Jade now covers Butchery's own carcasses and corpses (167 blocks in Butchery 5.2 and 5.3, player corpses left to
  Butchery 5.3's own line): the next step and its tool with the steps done, "Bleeding..." while Butchery bleeds one, when
  it must be hung or a frog put on a metal tray; and Butchery's skin rack: the skin, the next step, curing (a percentage
  on 5.3, which counts the time) and the leather. Both are separate Jade options. The guidance was recorded from
  Butchery's real handlers and is replayed against them by the GameTests.
- Acid works alone: while it dissolves a carcass, no cleaver, knife or second bottle does anything to it (nothing paid,
  no tool worn), and the skeleton only replaces the carcass the acid was poured on. Floor carcasses in acid show
  "Dissolving in acid..." instead of the bleeding hint.
- A carcass picked up while it bleeds or dissolves carries on where it was put down instead of starting over, so its
  blood and acid are paid once; a hookable carcass on the ground waits until it is hung again.
- Instant Bleeding drains the same blood as waiting (250, 1000 or 2000 mB) with Butchery 5.2 and 5.3 alike; with 5.3
  regular and large carcasses used to drain 50 mB, and with 5.2 large ones 1000.
- Hanging a carcass and putting a skin on Butchery's skin rack follow the placement rules: Adventure mode without
  `CanPlaceOn`, spectators, spawn protection and protection mods refuse it, and a refused attempt leaves the item, the
  rope and the rack as they were.
- The cut that breaks a tool pays like any other: Looting and tool conditions read the tool as it was.
- A skin rack destroyed any way (a player, an explosion, a command that destroys it) gives back its skin or leather
  once, as the rack's contents (a loot modifier such as Immersive Tanning's Binding Leather leaves it as it was), and
  Butchery's rack as any block would (an explosion destroys some); taking the leather off is not a second payout.
- More death drops are no longer paid twice: the feathers of the emu, roadrunner, shoebill, seagull, crow, blue jay,
  toucan, potoo and bald eagle, the giant squid's and mimic octopus's ink, the fly's maggots, the bones of the bone
  serpent, skelewag and soul vulture, the bunfungus's red mushrooms and the tusklin's porkchops (raw or cooked), each for
  that creature only.
- A tuskless elephant gives no tusks and a moose that has shed its antlers no antlers; carcasses saved without that
  record yield them as before.
- Dual-addon recipes: a known recipe is removed only while it is exactly what its addon ships (tool, outputs, chances,
  item data, cooking time and experience compared), and a conflict in which a pack changed any of its recipes is left
  as loaded and reported; an alternate never replaces or duplicates a pack's recipe at its id; and a conflict whose
  alternate cannot be made safely keeps both recipes and is reported unresolved.
- JEI shows each link of a variant chain with its own counts and provider and what carcasses no variant matches get,
  a native group's chance as that of any of its pools, and refreshes as soon as its runtime starts and only when the
  server's description changed. The creative tabs no longer hide an item a pack's stage table still yields, and Jade
  names a cooking addon only while the stage table still yields its item.
- The posed selection anatomy moved to `src/main/resources` so data generation can no longer delete it, and the build
  fails when it is missing, incomplete or stale.
- Requires Forge 47.4.23 and Citadel 2.6.3 or later, the versions it was tested with.
- Tests: GameTests for every finding above, a replay of every recorded Butchery step, Jade's real providers, real kills,
  the real taxidermy table and placement through the server's use path. Client checks add every head and trophy to the
  geometry review and, against a dedicated server, a trophy hung and dropped with its look, Adventure and
  spawn-protection refusals, a bleed that waits while its chunk is unloaded and Jade's server answer for Butchery's
  carcasses.

## 0.2.0 — 2026-10-25

Compatibility release for the two Alex's Mobs cooking addons, Alex's Mobs Delight (1.1.0) and Alex's Delight (1.5).

- Carcasses yield the installed addon's ingredient per creature and product instead of swapping items afterwards. Crocodiles and caimans give Alex's Mobs Delight crocodile meat, bison give Alex's Mobs Delight bison meat or Alex's Delight raw bison (4-7 whole pieces instead of 8-14 Butchery steaks), and bears keep giving Alex's Mobs Delight bear meat. Also covered: anaconda, emu, seagull and hummingbird (each of the three birds gives one whole bird), froststalker, tusklin, seal, orca and cachalot whale, bunfungus (2-3 Alex's Mobs Delight legs, or Alex's Delight bunfungus), terrapin and alligator snapping turtle meat and shells, the mantis shrimp tail matching the shrimp's saved colour, mimic octopus tentacles, Devil's Hole pupfish and mudskipper. Shared items are never converted for every creature: rattlesnakes and komodo dragons keep reptile meat, other birds game bird, cows Butchery's steaks.
- With both addons installed, Alex's Mobs Delight's items are used by default; `[compat]` in the server config sets `providerPriority`, `disabledProviders` and `preferExternalProducts` (false: this mod's own items everywhere). Changes take effect on restart. Without either addon, carcasses yield exactly what 0.1.3 did.
- A carcass kill no longer pays the meat twice: the creature's ordinary meat from either addon (table overrides and loot modifiers alike), raw or cooked by a burning death, is removed from its death drops, including what Alex's Mobs Delight's Dimensional Slicer adds. Crocodile claws and tails, bunfungus ears and hats, mantis shrimp tools, eggs and effect-holding seagulls stay, as does any stack carrying item data. Nothing is removed when no carcass is made. Burning bison, kangaroos, moose, catfish and lobsters no longer keep their cooked meat beside the carcass.
- Removal is now per creature: a creature's own replaced parts are removed only from its own carcass kills. Packs that add items to `#alexsbutchery:replaced_by_carcass` still remove them from every carcass kill, and packs that took entries out of it still keep those drops.
- Older bear, froststalker, tusklin, seal and whale meat and both turtle shells craft one-for-one into Alex's Mobs Delight's, and bunfungus meat into Alex's Delight's, so existing stacks reach the addons' recipes; a stack carrying item data does not convert, so nothing on it is lost. Nothing converts reptile meat, game bird, tropical fish, Butchery steaks or shrimp tails, whose creature a loose stack no longer tells.
- With both addons installed, the cutting board and furnaces have one recipe each for kangaroo meat, moose ribs, catfish and centipede legs, owned by the preferred addon; the other addon's cuts come from the owner's cut on the cutting board and its cooked centipede leg from the cooking pot, so both addons' meals stay reachable. Alex's Delight's kangaroo shank and moose rib also count in Alex's Mobs Delight's kangaroo and moose recipes, and vice versa for cooked kangaroo slices and bunfungus legs.
- Datapacks control all of it: `data/<namespace>/alexsbutchery/compat_profiles/` holds the built-in profiles, which packs can replace, disable or extend with other food mods, plus per-creature provider selections. The stage tables keep their ids; a pack's own stage table yields exactly what it says. A malformed profile file rejects that reload and keeps the previous rules. Schema and working examples: `docs/compat-profiles.md`, `docs/compat-examples/`.
- JEI's Butchering tab shows what the server actually yields (addon items, count ranges, chances, variant colours with their fallback, stages a server defines itself) and follows world and server changes. Creative tabs hide this mod's meats only where no creature yields them any more. Jade names the addon supplying a carcass's meat. Operators get `/alexsbutchery compat status`, `validate` and `explain <creature>`.
- The Patchouli guide gains a "Cooking Addons" entry.
- Tests: GameTests for every profile (core, Farmer's Delight, each addon, both addons; Butchery 5.2 and 5.3; Immersive Tanning, the official addon, and without JEI, Jade and Patchouli) cover the three-animal case with real kills, species isolation, shrimp variants, Looting, yield budgets, native table equivalence, pack stage tables, death drops (burning, Dimensional Slicer, specialty parts, declined carcasses, the opt-out), recipe conflicts and conversions, cooking chains, saved carcasses and single payment of every stage. The GameTest task now fails unless every registered test ran and passed. Client checks compare JEI and the creative tabs with the server's rules, in two worlds with different settings and against a dedicated server.

## 0.1.3 — 2026-10-11

- Ground-contact measurement now ignores fully transparent model cubes. Antlerless moose no longer rest on invisible antlers or float above the ground.
- Cached lying and hanging poses distinguish texture visibility and the complete saved appearance. Antlered and antlerless moose keep their own layouts regardless of render order.
- Regenerated carcass selection bounds distinguish antlered and antlerless moose, keeping targeting aligned with the corrected visible anatomy.

## 0.1.2 — 2026-10-10

- Farmer's Delight knives follow Butchery's own setting. With Butchery 5.3 they drop Alex's Mobs carcasses only while "Farmers Delight Knives Drop Carcasses" (`config/Butchery.toml`, `[Farmers Delight Compatibility]`, off by default) is on, with Butchery's precedence for Butcher's Touch, "Only Cleavers Drop Carcasses" off, and Butchery cleavers that are also tagged as knives. Butchery 5.2 has no such option and keeps treating the knives as cleavers. A refused kill changes nothing: the creature keeps its own drops and no advancement is granted. Both Butchery versions remain supported.
- Floor carcasses (whales, the Laviathan, the Void Worm, elephants...) are placed where a falling block would come to rest. A creature killed standing on a slab, path, soul sand, carpet or deep snow no longer deletes that block or loses its carcass there; killed in water or lava, it sinks to the bottom; over the void, it keeps its ordinary drops.
- Selecting a carcass no longer costs frame rate. Its outline is built once from the anatomy boxes instead of re-walking a voxel union every frame (up to 150 ms per frame and 25 s to build for the largest forms), ray tests no longer allocate, and the picker only visits carcasses near the ray instead of every block entity around it. Reach, walls, mining time, tools, protection mods and drops behave as before.
- Lying carcasses rest on their lowest solid anatomy, measured once per form, stage and size instead of estimated from the living creature's collision box. Carcasses that floated up to half a block (bison, rhinoceros, kangaroo, tiger, the cave centipede) now lie on the ground. The Farseer lies on its side with its arms along its body instead of through the floor. Each segment of the Void Worm, cave centipede, anaconda and bone serpent rests on the ground.
- A carcass on a slab, path, soul sand or carpet lies on that surface, for rendering and selection alike. Existing worlds pick this up without replacing anything.
- Carcass rendering is culled by the anatomy's own bounds instead of a fixed box up to 25 blocks wide.
- Selection bounds record what they were exported from; the server warns when the installed Alex's Mobs or Citadel differs.
- Tests: the knife rules against Butchery's own carcass drops with both Butchery versions, with and without Farmer's Delight; floor placement on partial blocks, fluids and the void; outline and ray parity with vanilla; export completeness and staleness; protection mods cancelling distant actions. New client checks benchmark frame time, reopen a world saved by 0.1.1, run the interactions against a dedicated server and photograph ground contact. GameTests start from a fresh world each run.

## 0.1.1 — 2026-10-04

- Redraw all four rug pelts with distinct species silhouettes, tapered tiger stripes, irregular snow-leopard rosettes, and coherent bear/bison fur shading. Keep the editable artwork and PNG exports synchronized.
- Seat rug heads using their transformed solid geometry, including child rotations and renderer scale. The bison's beard no longer lifts its skull above the pelt; neck overlap rotates with all four placements.
- Freeze the Laviathan dummy's previous/current head and body pose so frame interpolation cannot shake its neck. Restore borrowed living-model transforms after carcass and head rendering, including interrupted renders.
- Replace shared carcass selection boxes with generated anatomy bounds for each stage, orientation, and supported size variant. Find overhanging bodies outside their anchor block while preserving vanilla reach, obstruction checks, mining time, tools, protection events, collision, and drops.
- Add server packet/mining regression tests and client checks for mesh coverage, rug placement, frame interpolation, real targeting/butchery, and saved-world reloads.

## 0.1.0 — 2026-09-28

Initial release for Minecraft 1.20.1 and Forge.

- Add carcasses for every Alex's Mobs creature, with hanging, bleeding, skinning or plucking, butchering, and skeleton stages integrated with Butchery.
- Render carcasses and trophy heads using Alex's Mobs models and saved appearance; add head mounts and four large pelt rugs.
- Add mob-specific meat, hides, trophies, and other drops, plus Farmer's Delight cutting recipes.
- Add Jade hints, Butchery guide pages and advancements, and generated resources for the new content.
- Add GameTests and a client smoke-test agent for carcass stages and rendering.
