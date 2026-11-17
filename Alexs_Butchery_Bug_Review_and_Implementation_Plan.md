# Alex’s Butchery: bug review and implementation instructions

> Historical planning/handoff document. Current implemented behavior and release status are documented in the
> [README](README.md), [changelog](CHANGELOG.md), and [0.2.2 validation report](docs/0.2.2-head-mount-validation.md).

Reviewed 2026-11-09. This document is the implementation brief; the review did not fix production code.

## 1. Baseline, scope, and interpretation

Review the **current working tree**, based on commit `335ddf295bd5ee196d44df164eee01bc48b17eb2`, rather than assuming that commit contains everything reviewed here. At review start there were 196 modified tracked files and substantial untracked compatibility code, tests, documentation, and generated resources. Preserve that work. In particular, do not replace the current cooking-addon implementation with the older committed implementation.

Authoritative target configuration is in `gradle.properties`: Minecraft 1.20.1, Forge 47.4.23, Java 17, official mappings, mod version 0.2.0. Inspected dependencies include local Butchery 5.2 and 5.3 jars, Alex’s Mobs 1.22.9, and the configured Jade 11.13 integration. Keep version declarations in that properties file. Read `CLAUDE.md`, `MODMAP.md`, and the Minecraft development skill before implementation.

The review covered registration and definitions; kill, placement, hanging, bleeding, acid, cutting, and rack lifecycles; heads, mounts, and rugs; selection and rendering boundaries; loot/recipe/tag generation; cooking-addon resolution and presentation; Jade/JEI; build guidance; and existing validation. This is a source and targeted runtime review, not a guarantee that every possible modpack, visual variant, or multiplayer timing has been exhausted. Findings below distinguish reproduced defects, source-confirmed defects, and issues requiring further visual or compatibility validation.

### The two reported problems

- **“When will the head mounts be fixed.”** There are several concrete problems: harvested heads lose appearance NBT, taxidermy independently discards it, mounts lack wall-support behavior, and their selectable shape covers the plaque rather than the protruding head. Fix the entire head-to-trophy lifecycle. No separate universal head-transform/facing error was established; do not claim that NBT fixes alone resolve every visual complaint.
- **Jade works for this addon’s mobs but not regular Butchery mobs.** This is an integration coverage gap in the inspected versions. This mod registers providers only for its own classes. Butchery 5.2 has no corresponding integration; 5.3’s Jade integration only adds a player-corpse status line. Installing or toggling Jade does not add native animal processing guidance.

### Priority and evidence

P1 means a high-priority functional gap, data loss, duplication, permission bypass, or load failure. P2 means a narrower correctness/usability issue. P3 means documentation or an unresolved design/compatibility question. There is no established universal P0 failure.

| ID | Priority | Finding | Evidence |
|---|---|---|---|
| AB-01 | P1 | Appearance data lost through head harvest, taxidermy, rug crafting, and rug break | Source/dependency confirmed |
| AB-02 | P2 | Wall mounts can float and survive backing removal | Source confirmed |
| AB-03 | P2 | Visible trophy/head anatomy is outside the selectable shape | Source confirmed; visual acceptance still needed |
| AB-04 | P1 | Native Butchery animal Jade integration is absent | Source/dependency confirmed |
| AB-05 | P1 | Acid and ordinary processing can operate on the same carcass | Reproduced |
| AB-06 | P1 | Picking up a bleeding carcass resets already-emitted blood accounting | Reproduced loss of accounting |
| AB-07 | P1 | Direct hanging placement bypasses Adventure restrictions; related event paths need the same safeguards | Adventure hanging reproduced |
| AB-08 | P2 | Instant bleeding gives a different volume from timed bleeding | Reproduced; dependency-version dependent |
| AB-09 | P2 | Breaking a tool on a cut loses its Looting effect for that cut | Reproduced |
| AB-10 | P2 | Rack contents and rack disappear through non-player destruction paths | Reproduced with `destroyBlock(..., true, ...)` |
| AB-11 | P2 | Native death-drop replacement misses several products paid again by carcass stages | Source/data confirmed |
| AB-12 | P2 | Recipe conflict matching deletes pack-modified recipes | Reproduced |
| AB-13 | P1 | Generated alternate recipe IDs can collide with pack recipes and fail recipe publication | Duplicate plan reproduced; load failure follows from `RecipeManager` |
| AB-14 | P2 | Rejecting an alternate still deletes its original recipe | Reproduced |
| AB-15 | P2 | Client yield descriptions/hiding can disagree with actual tables and provider fallbacks | Source confirmed |
| AB-16 | P2 | JEI can miss a compatibility update during runtime initialization | Source-confirmed timing gap; not reproduced in a live client |
| AB-17 | P1 | Datagen removes the required exported anatomy file | Reproduced in an isolated copy |
| AB-18 | P3 | Public coverage claims and some maintenance guidance do not match source | Source confirmed |

## 2. Implementation constraints

1. Keep registry IDs, existing block-state numbering, and saved `MobData` keys compatible. In particular, do not renumber Alex’s carcass states or reuse those state numbers to interpret native Butchery blocks.
2. Compile against Butchery 5.2 by default and validate against 5.3. Put dependency-specific behavior behind `compat/ButcheryHooks` or similarly narrow adapters. Inspect both actual jars before choosing an injection or mapping.
3. Jade, JEI, Patchouli, Farmer’s Delight, and the cooking addons remain optional. Keep client-only classes out of server initialization and optional API references inside their existing compatibility boundaries.
4. Fix generator Java and regenerate its outputs. Do not make a generated JSON edit the only fix. First resolve AB-17 so regeneration cannot delete current geometry. Never use `git checkout`/`git restore` as a blind workaround in this dirty tree.
5. Do not bundle dependency jars or commit decompiled dependency source. Local bytecode/source inspection is evidence, not distributable implementation material.
6. Prefer small shared helpers where behavior is genuinely shared: snapshot copying, process eligibility, placement authorization, and native Butchery descriptors. Do not replace the entire mod architecture to fix these issues.
7. The verification section is an implementation acceptance plan. Historical reports and the diagnostic probes from this review are not evidence that the proposed fixes already pass.

Java references abbreviated as `block/...`, `data/...`, `compat/...`, etc. below are relative to `src/main/java/com/otectus/alexsbutchery/`. Line numbers refer to the reviewed working tree and will move during implementation.

## 3. Heads, mounts, and rugs

### AB-01 — preserve appearance through every transformation

**Evidence:** `data/CarcassStageLoot.java:60` builds head loot like an ordinary item, applying only count/chance. `butcher/CutMachine.java:54` supplies the carcass BE to the loot context, so the data is available but unused. For example, `src/generated/resources/data/alexsbutchery/loot_tables/carcass/tiger/head.json` has no `copy_nbt`. `data/ModRecipes.java:57` creates static shaped rug outputs; its taxidermy recipes also declare static outputs. `data/ModBlockLoot.java:45` uses `dropSelf` for rugs, unlike heads/mounts/skeletons.

Butchery 5.2 and 5.3’s `TaxidermytableOnTickUpdateProcedure` obtain `recipe.getResultItem(null).copy()` and write output slot 3. Input slots are 0–2, with the head in slot 1; a separate `TaxidermyTakenfromslotProcedure` consumes inputs when the result is taken. Simply implementing `Recipe.assemble` in a new recipe does not fix a station that calls `getResultItem` instead.

**Implementation:**

1. In `CarcassStageLoot.pool`, when the actual emitted item is the current definition’s head item, copy only `CarcassBlockEntity.MOB_DATA` from the BE loot context into `BlockEntityTag.MobData`. Follow `ModBlockLoot.copyMobData`. Keep food, horns, feathers, and other side products free of carcass NBT.
2. Introduce a narrowly scoped snapshot-copy helper that recognizes matching `ModItems.Kind.HEAD` and `Kind.MOUNT`/rug ownership. Copy the compound, never share a mutable reference. Copy only appearance data, not bleeding/acid clocks, arbitrary root NBT, capabilities, or unrelated inventory contents.
3. Bridge the native taxidermy station at the result-write boundary. The inspected 5.2/5.3 seam is the single `IItemHandlerModifiable.setStackInSlot(3, result)` invocation in `TaxidermytableOnTickUpdateProcedure.execute`. A server-side mixin can decorate the newly computed result immediately before that call, reading the current input head from the same container. Require a matching addon head and mount definition, preserve output count/other recipe data, then delegate once. Verify the exact descriptor and number of targets in both jars. Do not modify vanilla/native Butchery trophies or custom unrelated recipes.
4. Do not decorate slot 3 on a later tick: the output may have been taken, replaced, or extracted by automation. Verify normal taking, shift-click, and supported automation behavior at the actual station. If native automation has a separate upstream consumption problem, identify it separately rather than silently expanding this bridge into a station rewrite.
5. Replace static rug crafting with an input-aware recipe serializer, preferably retaining the existing shaped ingredients and recipe IDs. Its `assemble` copies the matching head’s appearance into the result. Keep normal recipe-book/JEI behavior, remaining items, and network serialization correct.
6. Change rug block loot to preserve `MobData`. Preserve placement, save/load, ordinary breaking, and support-removal drops. Existing head/mount BE save/sync and block-drop copying already work; reuse them.
7. Treat missing old data as a valid default appearance. Information already discarded by older harvest/crafting cannot be recovered; do not invent a former variant or rewrite old stacks indiscriminately.

Also resolve the variant-pelt mismatch exposed by preserving a white tiger’s head: `src/generated/resources/assets/alexsbutchery/models/block/tiger_rug.json` always selects the same `tiger_pelt` texture, while the BER can render a white head from `MobData`. Make the rendered pelt follow the supported coat variant as well, using the existing authored-art/generator workflow and an appropriate model-data or BER path. Keep the default appearance for legacy untagged rugs. A flat inventory icon is an existing intentional representation and need not be replaced with a three-dimensional head model.

**Acceptance:** use tiger `White=true`, elephant `Tusked=true`, and moose `Antlered=false/true`. Those creatures actually have head items; catfish is headless and is not a valid head fixture. Kill/cut a real variant-bearing creature, inspect the harvested stack, place/break/re-place the head, craft a mount at the real taxidermy table, place/save/reload/break it, and compare the exact appearance compound at each step. Repeat the rug path with a white tiger head. Ensure two differently tagged heads/results do not cross-contaminate or stack together incorrectly. Check a missing-data legacy head and untagged default head too.

### AB-02 — implement wall support and placement

**Evidence:** `block/HeadMountBlock.java:22` changes only shape. `HeadBlock.java:58` selects facing from the player. Neither implements mount support checks or neighbor-removal behavior. A wall plaque is placeable without its backing and remains after the wall is removed.

Implement `HeadMountBlock.getStateForPlacement`, `canSurvive`, and `updateShape` using a consistent outward `FACING` and a sturdy backing face at `pos.relative(FACING.getOpposite())`. Prefer the clicked horizontal wall face; explicitly reject an unsupported placement. Do not apply this wall restriction to ordinary freestanding heads. On backing removal, use the normal block-drop path so exactly one tagged trophy is recoverable. Preserve rotations/mirroring and avoid silent destruction of old unsupported mounts during migration.

**Acceptance:** all four wall facings, top/bottom clicks, adjacent corners, non-sturdy supports, support replacement/removal, rotation/mirror, reload, and a tagged legacy mount. Verify both plaque alignment and retained appearance.

### AB-03 — make selectable geometry match visible heads/trophies

**Evidence:** `HeadBlock.java:26` uses a fixed 8×8×8 box; `HeadMountBlock.java:13` uses a four-pixel-deep plaque shape. `client/render/CarcassScene.java:235` renders arbitrary mob heads outside those shapes. `CarcassBlockEntity.java:163` explicitly enlarges the render AABB for large heads, but that does not change selection. `CarcassTargeting` recognizes only `AbstractCarcassBlock`.

Choose server-owned head/mount interaction geometry derived from the same transformed head subtree and plaque that are rendered. Reuse the existing geometry/export/picker infrastructure where appropriate, but add distinct form keys and tests; the current carcass form/stage assumptions cannot simply be applied to `HeadBlock`. A larger `getShape` alone does not guarantee selection when the ray traverses only an overhang and never the anchor voxel. Extend indexed selection and server reach validation to the supported decorative forms, preserving obstruction, reach, protection events, mining time, and normal drops. Keep decorative collision behavior intentional and separate from selection if necessary.

**Acceptance:** aim at an elephant trunk/tusk and whale/Void Worm protruding head from several directions, including when the anchor is outside the ray’s traversed voxels. The outline and selected block must agree, an intervening wall must block selection, and a distant anchor must not grant excess reach. Verify all mount facings, item/GUI rendering, frustum culling, reload, and rendering a living mob immediately afterward. Review close-up images of every registered head/mount; ordinary default-carcase galleries do not cover this.

## 4. Jade parity and consistent guidance

### AB-04 — add native Butchery providers

**Evidence:** `compat/jade/AlexsButcheryJadePlugin.java:50` and `:56` register only `AbstractCarcassBlock`, `CarcassBlockEntity`, and this addon’s `SkinRackBlock`/BE. Native blocks inherit none of them. The 5.3 dependency’s `net.mcreator.butchery.integration.JadeIntegration` only registers `PlayercorpseBlock` and displays its persistent `isDrained` flag.

**Architecture and required behavior:**

1. Keep existing addon providers and their UIDs. Add a separately configurable native provider UID, such as `alexsbutchery:butchery_carcass`, plus native rack guidance if exposed separately. Reuse localized text/formatting where applicable, not the addon’s state machine.
2. Create an explicit, version-audited native descriptor table keyed by registered block ID. It should map each supported block/state to form, orientation/attachment requirements, completed operations, next operation/tool, and supported persistent status flags. Share family descriptors only after checking their native procedures. Resolve IDs at runtime to avoid direct linkage to 5.3-only classes.
3. Do not rely solely on native tags. The inspected native carcass/corpse tag union has 128 IDs, while the jar blockstate inventory contains 167 carcass/corpse assets and 40 asset IDs outside that union. These are asset/tag counts, not proof of 167 live registry entries: intersect with the live registry and test coverage. Examples missed by tags include chicken, bat, bee, spider, ocelot, mule, phantom, and warden families. Account for variant cats and humanoid corpses separately.
4. Register native server-data collection on the necessary base class(es), filtering by the descriptor table before reading/serializing anything. A broad `BlockEntity.class` registration is acceptable only with an immediate native-ID filter. Do not run expensive registry scans on each tooltip request.
5. Read native BE `getPersistentData()` on the server. Common native bleeding flags are `isBleeding` and `isDrained`. Send namespaced, explicit fields and format from the received status. Do not assume the client receives native persistent flags in ordinary update tags.
6. Map actual operations for the current supported native families. A fresh cow and a drained chicken do not share the same stage range: inspected examples are cow 0–9 and chicken 0–8. Spider/bee, undead/skinless, floor-only, and special variants need their own native transitions. Never call `Stages.next` with a native block’s raw integer.
7. Native bleeding is scheduled through Butchery procedures; no reliable common persisted elapsed-time counter was established. Show truthful “Bleeding…” or “Curing…” status where only a flag exists. Do not invent a percentage by starting a client-side timer when the player looks at the block. Continue showing real progress for this addon, which owns persistent clocks.
8. Guard every optional/missing property, absent BE/server response, unsupported ID, and unknown future state. A conservative fallback should omit an uncertain action, not recommend a wrong tool or crash.
9. Avoid duplicate player-corpse text with Butchery 5.3. Default to leaving its existing player-corpse provider responsible for that line; document the precise coverage. Do not claim that vanilla animals, all hostile corpses, skin racks, and the official Alex’s Mobs addon are covered unless each is actually handled.
10. Keep all Jade API references in `compat/jade`. Load with no Jade installed, on a dedicated server, and in an integrated client. Native support should not require the cooking addons.

The native family mapping supplement later in this document is part of the implementation evidence. Validate mappings through actual native actions, not just blockstate asset names.

**Acceptance:** with both supported Butchery jars, cover a fresh and every reachable processed state from each declared family; include tag exceptions. Exercise the actual server provider and tooltip output, not only a pure formatting helper. Test no-Jade startup, missing server data, player-corpse non-duplication, English translation keys, and `/reload`. Capture actual tooltips for a cow, chicken, spider/bee, a floor carcass, and an addon carcass at equivalent processing points. Keep a live-registry coverage assertion so a newly registered native family cannot silently lack a descriptor.

## 5. Processing state, blood, and tools

### AB-05 — make acid processing exclusive

**Evidence:** `block/AbstractCarcassBlock.java:84` tries acid and then calls `useTool`; it never blocks ordinary tools while dissolving. `butcher/CutMachine.java:34` likewise lacks a busy guard. `CarcassBlock.useTool` can begin bleeding during acid. `Acid.pour` rejects an already bleeding carcass, but the reverse order is unguarded.

**Reproduced:** pour acid on a whole drained kangaroo, use a cleaver to harvest its head, then advance the acid clock. The head drops and the remaining carcass still becomes a full skeleton. Fresh carcasses can also enter contradictory acid/bleeding state.

Define one authoritative process-eligibility check, enforced at the interaction boundary and in processing entry points. While dissolving, consume/reject conflicting tool interactions without advancing stages, paying loot, damaging tools, filling blood, or awarding a second processing advancement. Apply the same rule on client prediction and server execution. At completion, verify the block/BE and process are still valid; do not transform an unrelated replacement.

Do not silently renumber or repurpose old saved flags. Define a deterministic recovery policy for legacy NBT with both bleeding and acid set: preserve the already-prioritized acid process, clear contradictory bleeding state, and ensure no previously issued blood/stage output is repaid.

`client/gui/FloorHintOverlay.java:53` currently offers bleeding guidance during acid. Suppress it or show a localized dissolving/wait hint. Existing addon Jade already checks acid first (`AlexsButcheryJadePlugin.java:116`) and should remain consistent with the new shared eligibility rule.

**Acceptance:** acid→cleaver, acid→skinning knife, acid→acid, bleeding→acid, repeated packets/two players, terminal stages, save/reload, and removal/replacement mid-process. Every refused interaction must leave the tool, item count, clock, stage, outputs, and advancements unchanged.

### AB-06 — retain processing accounting on pickup

**Evidence:** `ModBlockLoot.wholeOrScraps` (`data/ModBlockLoot.java:50`) preserves only appearance for fresh states 0/1. `CarcassBlockEntity` saves `Bleeding`, `BleedTicks`, `Fills`, and `AcidTicks`, but item loot discards those fields. `HangHandler.java:76` reloads item BE data, so there is an existing restoration path once the data is present.

**Reproduced:** after one 50 mB fill, breaking the fresh carcass returns a carcass item without bleeding or fill accounting. Rehanging initializes a fresh process. Repeating pickup before completion permits blood to be extracted again from the same carcass. Acid pickup also loses a paid-for countdown.

Recommended fix: preserve a validated process snapshot on whole-carcass drops and resume it when placed/hung. Keep the same field names where possible, or migrate old fields explicitly. Validate clock/fill ranges and applicable forms; reject contradictory or nonsensical state. Preserve the number of emitted fills, not merely a visual percentage. A hookable carcass placed on the floor must pause a resumed bleeding process until validly hung. Floor carcasses can resume in place. Define the interaction with changing Instant Bleed settings: only remaining blood may be emitted.

Do not preserve processing fields on head, mount, rug, skeleton, or mid-cut scraps. An alternative design that makes active carcasses non-recoverable would change gameplay; do not choose that silently when resumable data is available.

**Acceptance:** bleed partway, collect/remove fluid from the grate, break/re-place/re-hang repeatedly, and finish. Total emitted blood across all placements must equal the one-carcass budget. Repeat across world reload and chunk unload. Acid pickup must resume or follow an explicitly documented loss policy, never reset for a free new processing cycle. Existing untagged items still start normally.

### AB-07 — enforce placement permissions and transaction success

**Evidence:** `butcher/HangHandler.java:63` cancels the Forge right-click event and directly places a block, before ordinary item-placement checks. A real `ServerPlayerGameMode.useItemOn` call in Adventure mode, with no `CanPlaceOn` permission, still placed and consumed a kangaroo carcass. `RackTakeoverHandler.java:44` has the analogous direct mutation pattern. Both ignore the boolean result of `setBlock` before consuming inputs/awarding success.

Add explicit server-authoritative checks for player mode/build restrictions and placement at the **actual destination**, not just the clicked hook/rack. Respect preexisting event cancellation and applicable protection hooks. Use a normal placement transaction or a scoped compatibility adapter that snapshots/reverts block and BE state if a placement event is denied. Do not assume changing event priority alone recreates vanilla placement protections. Honor the legitimate behavior of fake players/automation without granting blanket permission.

Only consume the item, advance rope state, play successful effects, or grant advancement after placement succeeds and the expected BE exists. Rack takeover must retain the original rack if any prerequisite/placement step fails. Keep client/server prediction and cancellation results aligned.

**Acceptance:** Survival and Creative success; Adventure failure without explicit permission and correct handling of permitted Adventure placement; Spectator refusal; canceled right-click and canceled placement events; protected destination beneath an otherwise usable hook; failed `setBlock`; full/invalid rope; rack takeover failure. Each denied attempt preserves world, stack, rope, and advancements. Existing distant-carcass protection tests do not cover these placement paths.

### AB-08 — make instant and timed blood budgets identical

**Evidence:** `def/BloodClass.java:9` defines SMALL=250 mB, REGULAR=1000 mB, LARGE=2000 mB. `Bleeding.java:41` calls `ButcheryHooks.instantFill(..., def.small())` and immediately converts; the wrapper at `compat/ButcheryHooks.java:185` selects only native small/regular procedures.

**Observed:** the LARGE elephant probe emitted 2000 mB through timed ticking, but 1000 mB instantly with Butchery 5.2 and only 50 mB instantly in the tested Butchery 5.3 + cooking-addons profile. The second result invalidates an assumption that the dependency’s similarly named procedure retains 5.2 semantics.

Make the addon’s `BloodClass` and saved remaining-fill count authoritative for its own blood budget. Implement a version-independent fill-amount path through the grate capability, or a verified sequence of the compatible 50 mB fill operation, preserving the native search distance, grate eligibility, capacity, and puddle semantics. Do not blindly call the 5.2 instant procedure twice for LARGE: that does not fix 5.3. Separate process completion from how much a full/missing grate can accept, matching the intended spill/loss behavior.

**Acceptance:** every blood class × timed/instant × both Butchery versions, empty/part-full/full/no grate, and resumed partial carcasses. When sufficient capacity exists, totals must be 250/1000/2000 respectively. Check actual blood fluid type, drip/puddle behavior, and exactly one final drained conversion.

### AB-09 — calculate loot using the pre-damage tool

**Evidence:** `butcher/CutMachine.java:47` calls `hurtAndBreak` before reading Looting at line 52 and before supplying `TOOL` to loot evaluation. The final durability point empties the stack.

**Reproduced:** a healthy Looting III cleaver yielded four head rolls; an otherwise identical cleaver breaking on that action yielded one. The same ordering can invalidate datapack loot predicates that inspect the tool.

Capture the tool used for the action before damage. Compute enchantments and evaluate all stage/organ loot against that snapshot; damage the actual held stack once for the successful action. Retain existing creative, Unbreaking, advancement, and broadcast behavior. This is an ordering fix, not a proposal to rebalance the existing multiple-roll Looting policy.

**Acceptance:** one durability remaining with Looting on/off and tool-sensitive loot conditions, healthy comparison, break notification, and one successful state transition. Also test skinning knives and invalid tools.

## 6. Rack lifecycle and content consistency

### AB-10 — make rack drops independent of player-only callbacks

**Evidence:** `SkinRackBlock.java:162` drops rack/contents only in `playerWillDestroy`; `ModBlockLoot.java:47` deliberately supplies `noDrop`. A destruction path calling `Level.destroyBlock(pos, true, player)` without the player-break callback produced neither rack nor contents in the diagnostic probe. Explosions and other destruction paths require coverage too; they were not separately reproduced here.

Put the survival drops in a common, loot-aware path that has access to the rack BE, and remove duplicate manual payouts. Return the original Butchery rack plus the appropriate content: raw skin at hung/salted/unfinished stages, leather when done, according to the intended interruption policy. Respect normal creative and explosion/drop rules. Merely adding drops in `onRemove` is unsafe: collecting leather and intentionally swapping back to an empty native rack would then pay twice.

**Acceptance:** normal player breaking, `destroyBlock(..., true, ...)`, explosion rules, creative breaking, successful leather collection, shift-removing raw skin, save/reload, and deliberate rack replacement. Pay contents and the rack at most once; a successful restoration is not a destruction payout.

### AB-11 — finish species-specific death-drop replacement

**Evidence:** `KillHandler.java:93` trusts per-species suppression built by `CompatResolver.java:126`; that set is based on `MobDef.replacedDrops`, not automatically on everything yielded by the carcass. Several native definitions omit products also present in the dependency’s normal death loot. The following exact item overlaps were found by comparing the local Alex’s Mobs jar with current generated stage tables:

| Creature(s) | Death/stage product absent from that species’ replacement declaration |
|---|---|
| Emu | `alexsmobs:emu_feather`, `minecraft:feather` |
| Roadrunner | `alexsmobs:roadrunner_feather`, `minecraft:feather` |
| Shoebill, seagull, crow, blue jay, toucan, potoo, bald eagle | `minecraft:feather` |
| Giant squid, mimic octopus | `minecraft:ink_sac` |
| Fly | `alexsmobs:maggot` |
| Bone serpent, skelewag, soul vulture | `minecraft:bone` |
| Bunfungus | `minecraft:red_mushroom` |

Additionally, native tusklin death loot contains 3–6 porkchops (cooked on burning deaths), while its definition yields tusklin meat and never declares porkchop replacement (`MobDefs.java:70`). The old test report explicitly notes this as retained legacy behavior, but the current README’s blanket “No meat twice” promise does not match it.

Audit each overlap as a represented body product versus an intentionally independent reward. Add explicit species replacements for represented products; do not globally suppress all feathers/bones/ink or remove named/NBT rewards. For deliberate exceptions, document and test the reason instead of claiming blanket replacement. Correct tusklin’s raw/cooked equivalence if the intended contract is one meat source per carcass kill. Keep baby, non-qualifying kill, disabled species, official-addon deferral, and `replaceMobDrops=false` behavior intact.

Regenerate the replacement tag and validate the runtime suppression map. Add deterministic drop-event fixtures for the actual products (not only random death-table rolls), including burning deaths and data-bearing stacks. Inspect variant death tables and optional-addon modifiers as well; the table above is a proven starting set, not permission to suppress every item sharing an ID with a stage drop.

## 7. Recipe policy and client presentation

### AB-12 — match the full audited recipe before deleting it

**Evidence:** `compat/RecipePolicy.java:262` checks recipe type, one ingredient’s enumerated items, and the first/static result and count. It does not compare cutting tool, chance, secondary outputs, NBT constraints, or cooking parameters. The existing changed-recipe test changes the output count, which this predicate does notice.

**Reproduced:** replace `alexsdelight:cutting/kangaroo_shank` with a recipe requiring a diamond axe while keeping its original input and two-shank result. `RecipePolicy.plan` still deletes the modified recipe. This violates the documented promise that a pack’s replacement is left as written.

Use a complete semantic fingerprint for each supported recipe type, including its ingredient semantics, tool, all outputs/counts/chances/NBT, and relevant cooking/container parameters. Do not treat `Ingredient.getItems()` as proof that a strict/partial-NBT ingredient is equivalent to a plain item ingredient. Obtain type-specific data without hard-linking optional mod classes into common initialization: a narrow optional adapter or validated serialization is appropriate. If an unfamiliar serializer/ingredient cannot be compared safely, retain the recipe and report the unresolved conflict.

**Acceptance:** independently change tool, ingredient NBT, result NBT, secondary output, chance, cooking time, and XP while preserving primary input/output. Each modified recipe remains. Exact audited originals still resolve. Test both provider orders and disabled-provider/native modes.

### AB-13 — handle alternate recipe ID collisions before publication

**Evidence:** `RecipePolicy.alternate` constructs deterministic `alexsbutchery:compat_conflict/...` IDs; `plan` adds them at line 175 and appends them to the original recipe collection at line 211 without checking whether a pack already defined that ID.

**Reproduced:** a pack recipe at `alexsbutchery:compat_conflict/kangaroo_meat_cutting/alexsdelight_kangaroo_shank` remains in the planned list alongside a new alternate with the identical ID. `RecipeManager.replaceRecipes` builds a unique `byName` immutable map, so publishing that list can throw. The diagnostic tested the duplicate plan, not a deliberately crashing server reload.

Plan in a single ID-keyed collection. Detect a preexisting ID before adding an alternate. Preserve the pack’s recipe; validate whether it already provides the needed route. If it does not, report the collision and leave the conflict unresolved rather than deleting the losing route or overwriting the pack’s content. Assert ID uniqueness before calling `replaceRecipes`. Publish only a fully valid plan so a failure cannot leave the recipe map and name index inconsistent.

**Acceptance:** collision with same and different recipe types, useful and unrelated custom outputs, both provider orders, first server start, and real `/reload`. A pack-owned ID must never cause duplicate publication or be silently overwritten.

### AB-14 — commit removal only after validating the replacement route

**Evidence:** `RecipePolicy.java:172` records losing recipes for removal before building their alternates. Construction failure returns null but does not undo removal. The loop check at line 200 removes an unsafe alternate, yet the original recipes remain removed and the outcome remains `RESOLVED`.

**Reproduced:** add a reverse conversion from Alex’s Delight kangaroo shank to Alex’s Mobs Delight kangaroo slice. The proposed forward alternate is rejected as cyclic, but the original shank cutting recipe is still removed. The promised replacement route is absent.

Make each conflict adjustment transactional: construct the alternate, check ID collision, parse/type validity, input reachability, and cycle policy, then commit both removal and addition. If any check fails, keep the original recipes and report a truthful unresolved outcome. Do not mark a conflict resolved solely because one side disappeared. Keep application idempotent and based on the clean recipe set; test the actual reload lifecycle as well as `plan`.

**Acceptance:** valid alternate, loop rejection, construction failure, ID collision, absent owner source, and pack override. Each rejected plan retains the original losing route, or proves an existing valid alternate route before removing it. Verify final recipe-manager contents and client recipe sync.

### AB-15 — derive display claims from actual reachable outputs

There are several related discrepancies:

- `CompatResolver.java:316` calculates hidden native items entirely from static `MobDefs` and provider resolutions. It does not inspect a pack’s actual replacement stage table. A pack can explicitly yield `raw_bear_meat` while AMD is installed, yet that reachable item remains hidden from the creative tab.
- `AlexsButcheryJadePlugin.java:85` names a provider by resolution chain, independently of whether the loaded stage table still contains a `compat_yield` entry. A replacement table that yields only a stick can still be described as yielding an addon’s meat.
- `LootSummary.java:98` returns after the first provider, listing native fallback items for a variant provider. Actual `CompatYieldEntry.java:78` continues through **later providers** when the saved variant is absent/unmatched. A pack-defined second provider is therefore missing/misidentified in JEI’s alternatives.
- `LootSummary.java:120` uses the maximum native-pool chance when combining optional native outputs into a `native_group` output. For two independent 50% pools, the chance of any output is 75%, not 50%. It also does not apply the runtime 576-item emission cap to a summarized replaced group. Unsupported structures should not be presented as exact.

Build presentation from the loaded table structure and the same resolution semantics used by the actual yield entry. Represent conditional branches with their own outputs/counts/provider identities, or explicitly mark a summary incomplete when exact representation is impossible. Do not attach one provider’s count range or name to a different fallback. Treat uncertain/custom tables conservatively when hiding items; avoid hiding a native item when a loaded table demonstrably yields it. Jade should describe only the actual remaining stages/groups, and should omit or qualify uncertain provider claims.

For `native_group`, compute the distribution accurately for supported independent simple pools or mark it custom; do not return a plausible-looking but wrong percentage. Preserve runtime zero yields, count limits, native fallback, and loaded datapack changes. Keep unsupported custom loot labeled as server-defined, without running random loot just to build a tooltip.

**Acceptance:** a native-meat table override under AMD; a non-meat replacement table; two chained variant providers with overlapping/disjoint variants and unknown legacy data; native fallback; a 2×50% group; a group over the emission cap; a custom nested/conditional table; real `/reload`; JEI/creative/Jade agreement with deterministic server drops. Existing tests of a simple stick override and static hidden-item lists do not cover these combinations.

### AB-16 — refresh JEI when its runtime becomes available

**Evidence:** `client/compat/jei/AlexsButcheryJeiPlugin.java:50` builds recipes during registration; `refresh` at line 76 returns if the runtime is null; `onRuntimeAvailable` at line 63 only assigns the runtime. A compatibility packet arriving after registration but before runtime availability can therefore be stored without ever updating the displayed recipes until a later reload. This is a source-proven event-order hole, not an observed failure in the reviewed client reports.

Reconcile the latest `CompatView` in `onRuntimeAvailable`, using the same update path as packet/reload handling. Make it idempotent so already-registered recipes are not duplicated, and clear pending/display state on disconnect/runtime loss. Inspect repeated refresh behavior: currently each refresh hides old recipe objects and adds new ones. Reuse/diff stable recipes or supported JEI APIs to avoid unbounded retained hidden objects across identical refreshes.

**Acceptance:** view-before-registration, view-between-registration-and-runtime, view-after-runtime, runtime replacement, same digest repeated, changed digest, disconnect/reconnect, and two worlds in one client. Test the visible JEI recipes, not only this plugin’s `shown` field. No-JEI clients and servers must still load.

## 8. Generation, geometry ownership, and documentation

### AB-17 — keep exported anatomy out of datagen’s deletion path

**Evidence:** `CLAUDE.md:19` and `MODMAP.md:201` document that `runData` deletes `src/generated/resources/data/alexsbutchery/carcass_bounds.json`. `data/DataGenerators.java:24` never registers a provider for it. `block/CarcassBounds.java:62` reads that classpath resource; when absent it logs an error and makes every carcass select as one block.

**Reproduced:** in the isolated review copy, `runData` completed successfully and the anatomy file was gone afterward. Its before-run SHA-256 was `8f7ffb233f4b3c5476b07420b38885e718f2bec4519a3ce0795f321dc16e02d5`. A successful datagen task is currently compatible with packaging broken selection geometry.

Move the client-exported authoritative resource into `src/main/resources/data/alexsbutchery/carcass_bounds.json`, or another explicitly separate source directory outside datagen’s managed output. Prefer the simple main-resources move unless the project needs a separate export source set. Remove the duplicate generated-path copy from the intended change; ensure the jar contains exactly one resource at the expected path. Update the exporter copy destination, build/docs references, and validation scripts together. Preserve the **current** reviewed export, not the version from `HEAD`.

Add a focused build/resource validation that fails for absent, empty, malformed, incomplete, or stale geometry. The existing `boundsWereExportedFromTheseInputs` GameTest is useful but a normal `build` must not silently package a missing resource. Keep revision/profile/dependency-input checks and re-export after geometry changes. Compare the file before and after two consecutive `runData` executions and inspect the packaged jar.

### AB-18 — correct supported-content and maintenance claims

`README.md:3` and `META-INF/mods.toml` advertise every Alex’s Mobs creature, whereas `def/MobDefs.java:16` explicitly excludes several creatures and the current table has 82 entries. `Flutter` is also absent from the table but not named in that exclusion comment. Reconcile the runtime inventory, table, exclusion rationale, and public wording. A wording correction and explicit supported set are preferable to adding new mobs merely to satisfy an inaccurate universal claim. If Flutter is an accidental omission, make its addition a separately specified content change with real anatomy, drops, data generation, and validation.

Clarify that Jade currently supports addon carcasses until AB-04 is delivered; afterward describe the exact native coverage and any intentionally unsupported player/third-party corpse cases. Correct “No meat twice” only after AB-11’s intended exceptions are resolved. Remove the unsafe datagen restore instructions after AB-17. Keep the hand-maintained `MODMAP.md` roadmap consistent with actual existing performance/save/pack tests, but do not rewrite its generated region manually or add unrelated release/install tasks.

### Additional bounded checks before declaring the review work complete

These are concrete unresolved risks/design questions, not established default-gameplay failures:

- **Geometry-changing resource packs:** `client/pose/PoseProfiles.java:43` accepts resource-manager overrides, while `CarcassBounds` reads the bundled export and hashes bundled profiles via `Class.getResourceAsStream`. A client-only pose/scale override can therefore diverge from server selection. Reproduce with one altered pose. Choose a documented contract: server-approved matching geometry/profile data, or a guarded limitation on geometry-changing overrides. Do not silently trust client-provided hitboxes. This becomes especially relevant when extending trophy selection.
- **Anatomical loot versus variants:** elephant tusks and moose antlers are currently unconditional/chance-based table outputs rather than conditional on the snapshot (`MobDefs.java:48`, `:59`). Establish whether tuskless/shed-antler creatures should yield those parts. Do not conflate preserving the head’s appearance with changing intended loot balance. If conditioning is intended, explicitly false data should suppress the absent anatomy and missing old data needs a deliberate default.
- **Rug footprint:** the flat model spans two blocks in each horizontal axis while `RugBlock.canSurvive` tests only its anchor’s support. This may be intentional decoration. Verify the desired overhang behavior before imposing a multi-block support requirement.
- **Dependency support promises:** `forge_version_range=[47,)` and `citadel_version_range=[2.6.0,)` are broader than the versions exercised here; code uses version-sensitive Forge methods, model APIs, and fixed exported geometry. Test the declared minimums or narrow the ranges to evidence-backed compatible versions. A compatibility risk is not proof that every older version crashes.
- **Native station automation:** validate taxidermy result extraction against the dependency’s slot-consumption procedure. Fix addon NBT handling without obscuring any separately discovered upstream duplication bug.

## 9. Native Jade mapping supplement

This is a starting map from inspected 5.2/5.3 dependency bytecode. It deliberately distinguishes exact representative paths from proposed family sharing. The two local artifacts have matching carcass/corpse block registration sets, but their procedures can still change behavior, as Instant Bleed demonstrates.

| Representative | Proven data/transition | Source to inspect for the full adapter |
|---|---|---|
| Cow | Fresh `blockstate` 0–1; hanging state 1 starts bleeding. Drained ground path: `0 → 6 → 7 → 8 → 9 → removed`; knife for `6 → 7`, cleaver for the other operations | `CowcarcassbleedingProcedure`, `CowcutupProcedure`, fresh/drained block classes |
| Chicken | Fresh 0–1. Drained ground path: `0 → 5 → 6 → 7 → 8 → removed`; knife for `5 → 6` | `ChickencarcassbleedingProcedure`, `ChickencutupProcedure` |
| Spider | Direct cleaver ground path: `0 → 4 → 5 → 6 → removed`; block property range 0–7 | `SpidercutupProcedure`; verify cave-spider separately |
| Cats | Eleven drained variants use a different family: ground `0 → 3` with cleaver, `3 → 4` with skinning knife; remaining branches must be mapped before release | `CatcarcasscutupProcedure`; cat property range 0–6 |
| Zombie | Fresh state 1 begins bleeding. Drained ground path `0 → 5 → 6 → 7 → removed`, cleaver | `ZombiecarcassbleedingProcedure`, `ZombiecarcasscutupProcedure` |
| Warden | No ordinary `blockstate` sequence. Persistent double `butcherStage`: `0 → 1 → 2 → 3 → 4 → removed`; requires `butchery:boss_tool` at every step | `WardencutupProcedure`; first stage uses `wither_head_drop`, then `warden_cut_1_drop`…`warden_cut_4_drop` |
| Bee | `blockstate` 0–3; no sequence inferred from that range | `BeecarcassBlock` and its actual interaction handler; do not search only for a guessed `BeecarcasscutupProcedure` name |
| Hanging Enderman | Special hanging fresh/drained forms; not the ordinary integer-state model | `hanging_enderman_carcass`, `hanging_drained_enderman_carcass`, associated block/procedure classes |
| Native skin rack | `blockstate` 0–31; 0 is empty, other states encode skin/model identity as well as processing. This is not the addon rack’s four-stage enum | `SkinrackBlock`, `SkinrackBlockEntity`, `PlaceskinonrackProcedure`, curing/salting/sponge handlers |

The listed cow/chicken/cat/zombie paths are **ground paths**, not complete hanging maps. Finish the remaining branches by reading the native handlers and running real interactions. Do not derive a hanging offset by analogy. Native regular and small scheduled bleeding durations observed in representative procedures are 900 and 225 ticks, respectively; that still does not give the client a trustworthy elapsed counter.

Completion gate: every known, supported native animal carcass family in 5.2/5.3 must have accurate reachable-state guidance. The conservative unknown-state fallback is for genuinely unknown/unsupported cases; it is not a substitute for finishing the shipped-family mappings.

For cats, account for `all_black`, `black`, `british_shorthair`, `calico`, `jellie`, `persian`, `ragdoll`, `red`, `siamese`, `tabby`, and `white`. For full native coverage, enumerate actual `ButcheryModBlocks` registry values in a GameTest after registries load, record the ID, properties, BE type, and relevant procedure, and assign each relevant block to an adapter or an explicit exclusion. Include mammal variants, small animals, fish/aquatic/direct-cut families, hostile corpses, floor-only/boss forms, and multi-block forms. Similar names or property ranges are insufficient evidence of identical mechanics.

For each adapter, create table-driven assertions for every reachable state, held tool, busy status, and support mode. Ensure each suggested action succeeds through the actual native handler and that the resulting state matches the descriptor. Enumerating registered states is useful for coverage, but unreachable/invalid states must use conservative fallback text.

Native rack curing flag/counter names were not fully established in this review. Determine them from its actual procedures before displaying an exact next prerequisite; send the server’s verified state/skin identity. Do not present the raw numeric identity selector as a percentage. Rack parity is not complete while ordinary supported skin types display misleading “salt/wet/cure” instructions.

## 10. Ordered implementation and acceptance plan

### Work order

1. Preserve the dirty baseline and resolve geometry-export ownership (AB-17), enabling safe subsequent datagen.
2. Fix process exclusivity, pickup accounting, permission/placement transactions, blood budgets, tool ordering, and rack drops (AB-05…AB-10). Add focused regression tests for each observed failure.
3. Fix the complete head/mount/rug appearance pipeline (AB-01), wall support (AB-02), and decoration targeting (AB-03). Re-export geometry if its schema/poses/forms change.
4. Implement native Jade descriptors/providers and shared eligibility guidance (AB-04), testing real native interactions rather than assumed numbering.
5. Correct native suppression and transactional recipe policy (AB-11…AB-14), then correct display semantics and JEI lifecycle (AB-15…AB-16).
6. Resolve the bounded compatibility questions relevant to the changes, update accurate user-facing guidance (AB-18), and complete the appropriate runtime matrix below. Do not defer the two reported issues behind unrelated visual polish or release work.

Keep one implementation owner per changed file; several tasks share `ModBlockLoot`, `ModRecipes`, `ButcheryHooks`, and the Jade plugin. Parallel work, if used, must not overwrite those shared edits or run builds concurrently in one checkout.

### Required focused regressions

| Area | Required proof |
|---|---|
| Appearance | Real harvested variant → head item/BE → actual taxidermy output/BE → save/reload/drop; rug variant survives crafting and breaking |
| Decoration | Four supported wall facings, support loss with one tagged drop, protruding-head targeting, occlusion/reach/protection, model restoration |
| Jade | Every supported native family/state, addon behavior preserved, native flags from server, no false progress, actual tooltip output, no duplicate player-corpse status |
| Processes | Acid rejects cuts/bleed; mutually exclusive persistent state; partial pickup cannot repay blood or reset paid processing; chunk/world reload |
| Permissions | Actual game-mode use path, Adventure/Spectator restrictions, event cancellation, destination protection, failed placement, rollback of rope/rack/input |
| Blood/tools | Blood classes × both dependency versions × instant/timed; partial budgets; last-durability Looting and tool predicates |
| Rack | Player/non-player destruction and collection paths, every process stage, no double or missing drops |
| Suppression | Deterministic per-species normal/cooked/data-bearing drops, disabled/native/official-addon policy preserved |
| Recipes | Modified semantic fields retained; unique IDs; alternate failure rolls back removal; both provider orders; reload and client recipe sync |
| Presentation | Loaded-table overrides, conditional provider chains, conservative custom summaries/hiding, JEI event-order permutations |
| Generated data | Two clean datagen runs preserve exported bounds; generated outputs match source; jar has exactly one valid anatomy export |

### Commands and runtime profiles for the implementing agent

Use `/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh <checkout> ...`. Run Gradle serially per checkout and preserve complete log paths. The project’s `runGameTestServer` deletes its `run-gametest/world`; use an isolated checkout/copy when that directory contains work worth keeping.

```bash
# Compile all three actual source sets.
/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh <checkout> compileJava compileGameTestJava compileSmokeTestJava

# Regenerate only after export ownership is fixed; check the diff and bounds hash.
/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh <checkout> runData
python3 /home/otectus/Projects/.mcmod-tools/check_mod.py <checkout>
python3 tools/generate_textures.py --check

# Core and representative integration profiles; repeat with the Butchery 5.3 flag.
/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh <checkout> runGameTestServer
/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh <checkout> runGameTestServer -Pbutchery_artifact=butchery-5.3-forge
/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh <checkout> runGameTestServer -PwithAlexsMobsDelight -PwithAlexsDelight
/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh <checkout> runGameTestServer -PwithoutJei -PwithoutJade -PwithoutPatchouli

# Use/extend the existing client harness for the new acceptance checks.
/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh <checkout> runClient -PsmokeTest -PgeometryReview
/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh <checkout> runClient -PsmokeTest -PgeometryReload
/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh <checkout> runClient -PsmokeTest -PcompatWorlds -PwithAlexsMobsDelight -PwithAlexsDelight

# Package only after checks; inspect jar resources and dependency boundaries.
/home/otectus/Projects/.mcmod-tools/gradlew-quiet.sh <checkout> build smokeTestJar
```

The angle-bracket checkout is a placeholder, not a literal shell argument. Run Python project tools from that checkout. Also run the project-documented Farmer’s Delight, each single cooking-addon, and dual-addon GameTest profiles against both Butchery versions after recipe/suppression changes; include `-PwithImmersiveTanning` and `-PwithOfficialAddon` for rack/death-handler changes. Reuse valid earlier results when source/inputs are unchanged.

When geometry changes, run the existing `-PsmokeTest -PexportBounds` workflow, copy its output to the **new authoritative destination**, then run the geometry and reload checks. Extend the harness with explicit head/mount variants and actual Jade tooltip assertions; the existing flags alone do not add those checks automatically.

Complete a dedicated-server/client check of trophy interaction, permission denial, process resumption, and native Jade server data. Use a disposable server/world and the existing `-PremoteServer=host:port` infrastructure; operator status is a harness requirement for setup, so include a non-operator/protected player case for permission acceptance. Verify an old-save fixture containing default/tagged decorations and partial processing; do not overwrite the existing historical fixture without preserving it.

The final implementation report must include exact commands, versions/profile flags, registered/passed test counts, current screenshots, log/artifact paths, unresolved defects, and a clear distinction between compilation, headless checks, client visual review, and packaged-jar validation. Shipping/installing/publishing the mod is a separate action, not required by this brief.

## 11. Checks actually performed during this review

Production source and existing project artifacts were preserved. The only intended new repository file is this document. Additional diagnostic Java files were created **only** in `/tmp/alexsbutchery-review-heh_p08k`, an isolated copy of the current source/resources/docs and build configuration with a symlink to the existing dependency directory. That copy has its own build output and disposable GameTest world.

| Check | Actual result | Evidence |
|---|---|---|
| `gradlew-quiet.sh /home/otectus/Projects/AlexsButchery compileJava compileGameTestJava compileSmokeTestJava` | PASS, all requested source sets compiled/up to date; 12 s | `/tmp/gradle-AlexsButchery-compileJava-20261109-133613.log` |
| `python3 /home/otectus/Projects/.mcmod-tools/check_mod.py /home/otectus/Projects/AlexsButchery` | 0 errors, 1 warning, 414 notes | `/tmp/alexsbutchery-review-check-mod.log` |
| `python3 tools/generate_textures.py --check` | PASS: 83 icons and 4 rug pelts up to date | Command output; textures were not regenerated |
| Isolated `runGameTestServer` with default Butchery 5.2 | PASS: all 84 registered tests; 78 preexisting tests + 6 diagnostic probes | `/tmp/gradle-alexsbutchery-review-heh_p08k-runGameTestServer-20261109-133925.log` |
| Isolated `runGameTestServer -Pbutchery_artifact=butchery-5.3-forge -PwithAlexsMobsDelight -PwithAlexsDelight` | Task FAILED: 95 of 96 tests passed. The only failure was a review diagnostic that expected 5.2’s 1000 mB instant result but observed 50 mB on 5.3; all 87 preexisting tests passed, and all three recipe-policy probes reproduced their defects | `/tmp/gradle-alexsbutchery-review-heh_p08k-runGameTestServer-20261109-134208.log` |
| Isolated `runData -Pbutchery_artifact=butchery-5.3-forge -PwithAlexsMobsDelight -PwithAlexsDelight` | Gradle PASS, 44 s; required `carcass_bounds.json` was deleted | `/tmp/gradle-alexsbutchery-review-heh_p08k-runData-20261109-134510.log` |
| Local dependency inspection | Butchery 5.2/5.3 Jade, native states/procedures, taxidermy output path, instant-fill difference; Alex’s Mobs native drop tables; Forge recipe publication | Local jars in `libs/` and matching cached Forge classes; no dependency source committed |

**Interpret diagnostic passes correctly:** `ReviewProbes.java` and `ReviewRecipeProbes.java` assert undesirable current behavior to verify the review, so a passing diagnostic means the defect was reproduced. They are not production regression tests or acceptance tests for fixes. Implementing-agent regressions must invert those expectations to assert the desired behavior. The review probes cover acid→head→skeleton, dropped bleed accounting, lost Looting on tool break, timed/instant blood, Adventure hanging, rack destruction, changed cutting tool deletion, duplicate alternate IDs, and alternate rollback failure. Their source remains in the temporary copy for inspection, but the concrete cases are also specified above so implementation does not depend on retaining `/tmp`.

The static checker’s no-item warning is the intentionally non-item takeover rack. Its model notes arise from loop-based registration and are not 414 missing-item bugs. Do not “fix” them by adding a rack item or duplicating registrations.

GameTest logs include expected malformed-profile diagnostics exercised by tests, upstream optional-recipe errors, and a test-harness config-watcher removal exception during shutdown. The latter follows `TestRun.stopConfigReloads`; it is not evidence of a production server crash. Do not suppress all log errors to obtain a clean-looking report.

Existing `build/review/0.2.0/test-report.md`, `run/smoketest/*.json`, pack reports, and screenshots were consulted as historical coverage only. A historical default kangaroo-head/mount screenshot does not validate current tagged variants. **No new client visual review, native Jade tooltip runtime test, real taxidermy crafting test, dedicated-server session, save-upgrade run, or packaged release test was performed during this review.** Those remain implementation acceptance work.

For block-entity persistence and synchronization, follow the version-matched Forge contract: save/load the owned fields, mark mutations changed, and explicitly synchronize the client data that is needed. [Forge 1.20.1 block-entity documentation](https://docs.minecraftforge.net/en/1.20.1/blockentities/) supports those lifecycle requirements; the defect findings above are grounded in this repository and its actual dependency jars.
