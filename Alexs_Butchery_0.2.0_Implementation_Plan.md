# Alex’s Butchery 0.2.0 — Compatibility Implementation Plan

> Historical planning/handoff document. Current implemented behavior and release status are documented in the
> [README](README.md), [changelog](CHANGELOG.md), and [0.2.2 validation report](docs/0.2.2-head-mount-validation.md).

**Target:** Minecraft 1.20.1 · Forge 47 · Java 17\
**Repository:** https://github.com/otectus/AlexsButchery\
**Prepared:** October 25, 2026\
**Purpose:** A coding-agent handoff. Implement the update described below; this document itself is a source-based plan, not a claim that the changes or runtime tests have been completed.

## 1. Release objective

Make butchering produce predictable, useful ingredients when Alex’s Delight, Alex’s Mobs Delight, and related food-processing mods are installed. Prioritize the reported discrepancy: bears yield an addon’s meat, while crocodiles and bison yield different ingredients.

The finished experience should be straightforward: a player processes an animal, receives the correct ingredients for the installed cooking addon, and can discover their uses in JEI. Pack authors can choose which addon supplies overlapping products. Without either addon, the current native drops remain available.

This is a compatibility release. Preserve the existing carcass workflow, models, targeting improvements, trophies, rugs, bleeding, organs, and Butchery configuration behavior. Do not turn it into a rendering overhaul, a loader port, or a general-purpose item-unification mod.

### 1.1 Distinguish the two addons

The first report explicitly names **Alex’s Mobs Delight**. The second asks for **Alex’s Delight**. These are separate projects with different item IDs and acquisition mechanisms. Support both deliberately.

| Project | Mod ID | Baseline to test | Role in 0.2.0 |
|---|---|---|---|
| Alex’s Butchery | `alexsbutchery` | Repository reports **0.1.2**, commit `335ddf295bd5ee196d44df164eee01bc48b17eb2` | Update to 0.2.0 |
| Alex’s Mobs Delight, by bf_meow/rawchickenNEG | `alexsmobsdelight` | **1.1.0**, Forge 1.20.1; source commit `298abcf1e9ededdd8ff180867404cbe31db86f84` | First priority; the addon named in the complaint |
| Alex’s Delight, by Baisylia/NCP_Bails | `alexsdelight` | **1.5**, Forge 1.20.1; source commit `4c41f005f406204c472751fbe18955d73d5d854c` | Explicit support for its meats and cooking chains |
| Butchery | `butchery` | **5.2 and 5.3**, both currently supported | Preserve both versions and their different knife rules |
| Alex’s Mobs / Citadel | `alexsmobs` / `citadel` | Build pins 1.22.9 / 2.6.3 | Preserve current baseline |
| Farmer’s Delight | `farmersdelight` | Build pins 1.20.1-1.3.4 | Cutting, cooking, knife tags and downstream recipes |

Recheck the branch and actual test jars before implementation. Record exact versions and hashes in the test report. Alex’s Delight’s newer 1.6 release is for NeoForge 1.21.1; do not substitute it into this Forge 1.20.1 matrix. Source commits establish the planning baseline, but the shipped jars must also be verified.

## 2. What the source establishes

### 2.1 Primary cause: incomplete, item-only substitution

The species difference is explained by deterministic source behavior. It does not require an RNG bug.

| Animal | Current carcass meat | Existing substitution | Why the complaint fits |
|---|---|---|---|
| Grizzly bear | `alexsbutchery:raw_bear_meat` | Explicit mapping to `alexsmobsdelight:raw_bear_meat` | The addon’s item is emitted when present |
| Crocodile | `alexsbutchery:raw_reptile_meat` | No crocodile mapping | The generic reptile item remains |
| Bison | Butchery chuck, ribeye, sirloin, T-bone and rump steaks | No bison mapping | Butchery steak items remain |

`compat/Substitutions.java` lists 16 raw/cooked/material substitutions for `alexsmobsdelight`, and none for `alexsdelight`. It maps `Item → Item`; it does not receive the mob, processing stage, or saved variant. `CarcassLoot.roll()` applies that map after rolling the stage table. See [S2–S4].

**Do not fix this by globally mapping reptile meat to crocodile meat or Butchery steaks to bison meat.** Those inputs are shared. Anaconda, komodo dragon, rattlesnake, and unrelated Butchery processing must not acquire the wrong species identity.

### 2.2 Additional gaps relevant to this release

1. **Death drops are a separate path.** `KillHandler` removes items in the global `alexsbutchery:replaced_by_carcass` tag only after a qualifying carcass is created. `ModItemTags` fills this from native `MobDef.replacedDrops()`. The new addon meats are not covered by those definitions. Duplicate meat at death plus meat from carving is therefore a source-supported risk to reproduce, not something to assume has already been demonstrated in the reporter’s pack. [S5]
2. **Cooking is not normalized.** `ModRecipes` generates native raw-to-native-cooked recipes and native-to-Farmer’s-Delight cutting recipes. The current cooked substitutions are not a universal furnace/skillet rewrite. Merely adding more entries to `Substitutions` leaves legacy ingredients and cooking paths inconsistent. [S6]
3. **JEI is a static approximation.** Its butchering recipes are built from `MobDefs`, then substituted locally. They do not describe arbitrary server datapack changes, and a future server-specific preference would need synchronization. [S7]
4. **The addons change loot differently.** Alex’s Mobs Delight adds crocodile/bear loot through global loot modifiers, but directly replaces some entity tables, including bison. Alex’s Delight also replaces bison and bunfungus entity tables. Replaying an entity’s death table during each cut would rerun unrelated rewards and conditions. [S8–S11]
5. **There is a real shared-input recipe collision.** Both addons define knife cutting of `alexsmobs:raw_catfish` into two of their own catfish slices. Giving the carcass the correct whole fish does not, by itself, resolve which downstream recipe is selected. [S12]
6. **Some drops carry special meaning or data.** Alex’s Mobs Delight’s seagull rewards can contain stored effects; mantis shrimp tails depend on `Variant`; special knives add or harvest loot outside simple table substitutions. A namespace-wide filter would destroy legitimate rewards. [S13]

The exact reporter mod list, config, and runtime behavior were not available. First reproduce the three-animal case with the pinned dependencies, then compare an available real pack. Keep confirmed source findings, reproduced failures, and remaining hypotheses separate in the implementation report.

## 3. Required player-facing behavior

### 3.1 Default product choice

Use this default preference, independently for each supported product: **Alex’s Mobs Delight → Alex’s Delight → native output**. The first addon has broader species coverage and matches the primary report. This is a product decision for 0.2.0, not a claim that one addon’s items are universally superior.

| Installed addons | Bear meat | Crocodile meat | Bison meat |
|---|---|---|---|
| Neither | Native bear meat | Native reptile meat | Existing Butchery steak assortment |
| Alex’s Mobs Delight only | `alexsmobsdelight:raw_bear_meat` | `alexsmobsdelight:raw_crocodile_meat` | `alexsmobsdelight:raw_bison_meat` |
| Alex’s Delight only | Native bear meat | Native reptile meat | `alexsdelight:raw_bison` |
| Both, default preference | Alex’s Mobs Delight | Alex’s Mobs Delight | Alex’s Mobs Delight |
| Both, Alex’s Delight preferred | Alex’s Mobs Delight fallback | Alex’s Mobs Delight fallback | Alex’s Delight |

Alex’s Delight 1.5 does not register bear or crocodile meat. Do not invent IDs or label its lack of those products a compatibility failure. An explicit **native** mode disables optional product replacement; a preferred addon that lacks a product normally falls through to the next provider.

### 3.2 Invariants

- Provider identity is stable for a given server policy, mob, product and variant. Ordinary quantity/chance rolls remain random.
- Each processing action advances exactly once and emits its intended reward once. Preserve existing Looting behavior; do not apply an extra multiplier during normalization.
- Carcass processing emits raw ingredients by default. A burning death must not cause raw and cooked death loot to survive alongside a replacement carcass reward. Do not introduce automatic cooked carcasses in this release.
- Distinct specialty drops remain obtainable: crocodile claws/tails, bunfungus ears/hats, shrimp tools, and effect-bearing seagull items are not interchangeable with generic meat.
- Optional mods remain optional. No missing-item failures, client-only references on a server, or startup errors when an addon is absent.
- Existing items and carcasses remain valid after upgrading. No registry ID removals, blanket inventory conversion, or carcass-state renumbering.

## 4. Implement one contextual compatibility system

### 4.1 Resolution contract

Replace the context-free substitution map with a small shared service, for example `CompatResolver`. Keep optional-mod access behind this boundary.

Inputs should include the source entity ID, processing stage, logical product/group, native fallback, saved variant where relevant, and the server’s validated compatibility policy. Results should describe the selected provider, concrete outputs, raw/cooked relationships where genuinely equivalent, applicable death-drop replacements, and diagnostic reason.

Use the same validated definitions for gameplay, recipe bridges, JEI descriptions and diagnostics. Do not maintain separate hardcoded maps that gradually diverge. Keep **species-specific yield rules** separate from **context-free item equivalences**; only the latter may be used to convert existing inventory ingredients.

Resolve registry IDs after registries are ready. A missing, invalid or air target invalidates that candidate and selects the next valid provider or native fallback. Validate all required items in a multi-output group before using it. Never produce a partial replacement while deleting all native outputs.

Do not pick a tag’s first item, iterate an unordered map to choose a provider, inspect translated display names, or infer equivalence from words such as `meat`, `steak`, or `delight`.

### 4.2 Preserve datapack ownership of stage tables

The existing public tables `alexsbutchery:carcass/<mob>/<stage>` are an extension point. Preserve their IDs and the ability to replace them with ordinary loot tables.

Recommended implementation: add a narrowly scoped custom loot entry, such as `alexsbutchery:compat_yield`, to the **generated default tables**. It identifies a mob/stage/product group, asks the resolver for the selected yield, and falls back to the original native group. Native fallback pools can live in generated internal tables. This gives pack authors a clear escape hatch: a replacement table without the entry yields exactly what they wrote.

Requirements for this entry:

- Keep native side products outside a replaced meat group unless the profile explicitly owns them.
- Preserve the native fallback’s counts, chances and functions. With no addon, the distribution must match 0.1.2.
- If delegating to an internal fallback table, use the same loot context without invoking the global modifier pipeline a second time. Apply global modifiers through the outer stage roll as usual; do not bypass them globally.
- Resolve optional IDs only for active profiles. Inactive resources must not turn into ordinary invalid loot entries when their addon is absent.
- Keep the current block loot context valid. Do not masquerade the player in `THIS_ENTITY` as the dead animal, or instantiate a living mob to obtain drops.
- Bound output counts and split stacks by the destination’s stack limit. Do not attach internal routing markers to player-visible stacks.
- A custom table that intentionally omits compatibility remains untouched by a later blanket postprocessor.

Remove the unconditional `Substitutions.apply()` pass from `CarcassLoot` when the new path is in use. Do not transform outputs twice. Keep Butchery organ-table rolls outside meat-provider replacement unless a separately documented rule explicitly covers an organ.

If a simpler implementation meets all these behaviors, it is acceptable; do not sacrifice datapack overrides, server authority, or species isolation merely to keep a global map.

### 4.3 Server lifecycle and snapshots

Build immutable resolved state per running server after config and datapack data are available. Rebuild it atomically on successful data reload; discard it on server stop and clear client copies on disconnect. Do not retain one JVM-wide lazy map across different single-player worlds or servers.

Sync the selected rule/version data needed for client displays on login and reload. A client must not decide authoritative drops from its own preferences. Use the existing `MobSnapshot` appearance data for verified variants. Its capture strips `Fire` and other living-state fields; do not assume it stores death conditions. If a later specialty-drop transfer needs kill-tool eligibility or fire state, add an explicitly versioned harvest record rather than reconstructing it from appearance NBT.

NBT-sensitive inputs need explicit handling. Preserve meaningful tags when converting a truly equivalent item; do not copy arbitrary item capabilities to unrelated items. Unknown/custom effect data should make an automatic conversion ineligible rather than silently disappear. Ordinary existing stacks need no conversion at all unless the player uses a documented recipe.

## 5. Built-in compatibility coverage

### 5.1 Primary regression targets

**Bear:** keep current grizzly meat interoperability, including cooking and slicing. The existing shared bear family also serves dropbear and sea bear; preserve that established behavior explicitly in their profiles. Do not infer a new polar-bear carcass feature from Alex’s Mobs Delight’s vanilla polar-bear drops.

**Crocodile:** replace only the crocodile’s generic reptile meat group with `alexsmobsdelight:raw_crocodile_meat`. Preserve its native hide, scutes, bones, fat, head and organ behavior. Caiman can use the same meat because the inspected addon’s caiman table explicitly does so; give caiman its own rule. Anaconda uses its own verified ingredient. Komodo dragon and rattlesnake retain native fallback unless an actual corresponding provider is established.

**Bison:** treat all five native steak types as **one species-specific meat group**, retaining fur/hide, fat, hoof, bones, head and organs. Select `alexsmobsdelight:raw_bison_meat` or `alexsdelight:raw_bison` according to policy. Neither mapping applies to vanilla cows or other Butchery steak sources.

The native bison definition yields **8–14 steak items** across its three cuts before Looting: chuck plus the additional named cuts. Both target bison items can be cut into two smaller pieces. Use **4–7 whole bison meat items** as the initial compatibility yield budget: 1–2, 2–3 and 1–2 across the three cuts. This is a deliberate proposed balance change for optional profiles, not a quantity discovered in the existing code. Keep the native assortment unchanged in native mode. Verify food values and downstream yields against the actual Butchery jars before finalizing the budget; record any adjustment and its rationale.

### 5.2 Additional verified mappings

IDs below are verified in the referenced upstream sources. Counts and conditions still require the release-jar audit. “Preserve” means retain the existing item and use the addon’s downstream recipes, rather than bypassing its cutting step.

| Mob/product | Alex’s Mobs Delight path | Alex’s Delight path / fallback | Implementation rule |
|---|---|---|---|
| Bear family | `raw_bear_meat` / `cooked_bear_meat` | Native | Keep established family substitution, now explicit per species |
| Crocodile / caiman | `raw_crocodile_meat` / `cooked_crocodile_meat` | Native reptile meat | Species rules; no global reptile conversion |
| Bison | `raw_bison_meat` / `cooked_bison_meat` | `raw_bison` / `cooked_bison` | Grouped whole-meat yield |
| Emu | `raw_emu` / `cooked_emu` | Native | Preserve existing support; audit whole-bird versus leg yields |
| Froststalker | `raw_froststalker_meat` / `cooked_froststalker_meat` | Native | Preserve existing meat support; legs remain distinct |
| Tusklin | `raw_tusklin_meat` / `cooked_tusklin_meat` | Native | Preserve existing support |
| Seal | `seal_meat` / `cooked_seal_meat` | Native | Meat replacement only; keep leather variants distinct |
| Cachalot whale / existing orca meat family | `whale_meat` / `cooked_whale_meat` | Native | Explicitly retain existing shared-family behavior |
| Bunfungus | `raw_bunfungus_leg` / `cooked_bunfungus_leg` | `raw_bunfungus` / `cooked_bunfungus` | Provider profiles have different units; do not call a leg and whole meat interchangeable |
| Anaconda | `raw_anaconda` / `cooked_anaconda` | Native reptile meat | Species rule |
| Seagull | `raw_seagull` / `cooked_seagull` | Native game bird | Species rule; preserve all eternal/enchanted death rewards |
| Hummingbird | `raw_hummingbird` / `cooked_hummingbird` | Native game bird | Species rule; do not change other birds |
| Terrapin / snapping turtle meat | `raw_turtle_meat` / `cooked_turtle_meat` | Existing Butchery turtle meat | Explicit rules for these two sources |
| Terrapin shell | `terrapin_shell` | Native shell | Existing equivalence; preserve recipe usability |
| Snapping turtle shell | `alligator_snapping_turtle_shell` | Native shell | Existing equivalence; distinct from terrapin shell |
| Mantis shrimp tail | `mantis_shrimp_tail_green/red/lime/white` | Native shrimp tail | Use saved `Variant`: 0 green, 1 red, 2 lime, 3 white |
| Mimic octopus | `mimic_octopus_tentacle` / `cooked_octopus_tentacle` | Existing Butchery tentacles | Species rule; never convert all tentacles globally |
| Devil’s Hole pupfish | `raw_devils_hole_pupfish` / `cooked_devils_hole_pupfish` | Existing tropical fish fallback | Species rule; do not change other tropical fish sources |
| Mudskipper | `raw_mudskipper` / `cooked_mudskipper` | Existing tropical fish fallback | Separate species rule |
| Kangaroo | Preserve `alexsmobs:kangaroo_meat` | Preserve same input | Addons provide different downstream cuts; resolve recipe coexistence |
| Moose ribs | Preserve `alexsmobs:moose_ribs` | Preserve same input | Keep rib pieces/loose ribs as downstream products |
| Catfish | Preserve `alexsmobs:raw_catfish` | Preserve same input | Resolve overlapping cutting recipes |
| Lobster | Preserve `alexsmobs:lobster_tail` | Preserve same input | Keep addon dishes reachable without replacing the tail |
| Centipede legs, maggots, lost tentacle | Preserve the existing Alex’s Mobs items | Preserve where applicable | Verify addon cooking routes; no invented replacement needed |

Unqualified IDs in the Alex’s Mobs Delight column use `alexsmobsdelight:`; unqualified Alex’s Delight IDs use `alexsdelight:`. Native entries remain the current registered items.

Unknown or missing variant data must have a documented fallback. For an old shrimp carcass with no `Variant`, use the native tail rather than fabricate a color. Do not consume RNG to choose a color. JEI can show the verified variants with an explanatory label.

### 5.3 Specialty parts: preserve first, transfer only deliberately

Audit every provider-added drop for supported mobs and classify it as:

1. A normal ingredient explicitly supplied by a carcass profile.
2. A distinct specialty part kept in the original death drops.
3. A rare, tool-specific, or data-bearing reward that must remain with the upstream mod.

For 0.2.0, the default should preserve untransferred specialty drops at death. This already keeps those recipes usable without reproducing their acquisition conditions. Crocodile claws and tails are a concrete example: they are separate inputs, with knife conditions, and must not disappear simply because ordinary crocodile meat moves into the carcass.

The upstream whole-crocodile recipe uses claws, meat, a tail, and an Alex’s Mobs crocodile chestplate. Do not add a second free whole-crocodile output from the same carcass. The claw and tail cutting recipes also produce more meat and scutes; include these chains in the yield audit.

Do not add both a whole animal and every ingredient obtainable by cutting that whole animal without an explicit budget. Audit emu, bunfungus, anaconda, and shrimp with particular care. Broader anatomy transfers can be included only when they preserve eligibility, have an explicit stage and yield, and replace the corresponding death rewards exactly once. They are not required to fix the initial complaint.

## 6. Prevent duplicate or missing death rewards

Extend the existing successful-carcass path; do not create a global loot scrubber.

1. Retain all existing eligibility checks: supported adult, configured/tamed exclusions, official-addon deferral, killer/weapon requirements, boss tool, and viable floor placement.
2. Confirm actual carcass creation before removing any ordinary reward. Failed placement, unsupported kills, cancelled processing, or deferral must leave upstream drops intact.
3. Build a **per-species** suppression set from the active profile’s represented products. Include verified raw/cooked death equivalents, including the nonpreferred addon’s equivalent meat when both are installed. Preserve raw/cooked distinctions for output; this set only identifies redundant death loot.
4. Do not suppress an item merely because it belongs to an installed provider or a broad food tag. Never remove specialty parts the carcass does not supply.
5. Respect `replaceMobDrops=false`: retaining death loot plus carcass rewards is the existing opt-out behavior. Explain this when diagnostics identify duplicates.
6. Preserve the existing `replaced_by_carcass` tag as a documented pack extension. Move automatic built-in filtering to species-aware rules; distinguish explicit pack-supplied removals from inferred equivalents. Keep documented native replacement behavior and verify migration of pack overrides.

Audit event ordering. Alex’s Mobs Delight has a `LivingDropsEvent` listener that can add dimensional-slicer rewards. Perform final duplicate filtering after the known relevant additions using a documented mutable event phase; do not mutate at an observation-only phase. Test the installed event ordering rather than assuming declaration order is sufficient. A later listener must not reintroduce a suppressed ordinary meat reward.

The crocodile knife also harvests loot during attacks. Do not scan nearby ItemEntities or delete items already obtained by its separate mechanic. The compatibility guarantee concerns the successful death-to-carcass transaction, not confiscation of upstream on-hit rewards. Keep eternal/enchanted seagull items, their NBT, shrimp tools, and unrepresented rare drops intact.

Keep safety local: when a profile cannot be validated, prefer native yield and preserve questionable external rewards with a diagnostic over silently deleting them. Do not change global mob-loot gamerule semantics as a side effect; test the installed Forge/Butchery behavior and preserve it.

## 7. Complete the cooking and cutting paths

### 7.1 Native inventory remains useful

Do not unregister or automatically replace old Alex’s Butchery ingredients. Existing stacks, storage, automation filters and recipes must survive.

Keep native cooking recipes as a reliable fallback. Add explicit conditional bridge recipes only for unambiguous Alex’s Butchery-owned item equivalents, or make carefully scoped recipes accept equivalent tags where their unit sizes agree. A player with old bear meat must have a documented route into the addon’s bear recipes.

Do not offer universal conversions from `raw_reptile_meat` to crocodile meat or from Butchery steaks to bison: loose stacks have lost the species provenance required to justify those conversions. Likewise, generic game bird and tropical fish stacks cannot identify their original species. Explain this limitation in the compatibility guide.

For context-free equivalence, preserve raw→raw and cooked→cooked. Do not create reverse conversions that exploit unequal quantities, nutrition, containers, cooking XP, or special effects. Whole bunfungus meat and bunfungus legs are not a safe automatic 1:1 bridge just because both are food.

### 7.2 Reuse provider recipes

When a carcass emits an upstream item, use the upstream mod’s smelting, smoking, campfire, skillet, cutting and cooking recipes where they already exist. Do not recreate an entire addon’s recipe set.

Test complete representative chains:

- Bear → addon bear meat → bear slices → a bear dish.
- Bison → Alex’s Mobs Delight meat → cubes → bison tartare or another verified recipe.
- Bison → Alex’s Delight raw bison → bison mince → cooked patty → bison burger.
- Crocodile → addon meat → a crocodile dish; separately verify preserved claws/tail and the whole-crocodile recipe.
- Bunfungus → selected provider ingredients → that provider’s meals, while retaining the other provider’s reachable recipes when both are installed.
- Kangaroo, moose and catfish → whole native ingredient → selected provider’s cuts → meals.

Avoid a broad recipe that lets every meat substitute for beef, fish or poultry in unrelated addon recipes. Existing generic Farmer’s Delight fallback recipes can remain for native items. Tags should encode genuine species/product equivalence, with optional external entries and `replace: false` where appropriate.

### 7.3 Both addons installed

Treat coexistence as a supported configuration, not just two separate single-addon tests.

Inventory duplicate recipes by recipe type, input, tool and other matching conditions. The two catfish cutting recipes are already confirmed. Audit kangaroo and moose cutting next; do not assume their whole inputs imply interchangeable output portions.

Implement a narrow, documented policy for verified conflicts:

- The selected provider owns the ordinary cutting-board action for a shared whole input.
- Suppress only the losing **known recipe ID with the audited matching shape**. Preserve user-replaced recipes whose semantics no longer match that manifest. Do not depend on alphabetical recipe order.
- Keep both addons’ distinct foods reachable. Prefer accepting verified equivalent cuts via tags. Where a recipe hardcodes an item, add a separate, explicit bridge or alternate recipe under this mod’s namespace; preserve quantities and additional ingredients. Validate each bridge for loops and yield inflation.
- Do not globally remove the nonpreferred addon’s recipes or hide its unique ingredients.
- Apply conflict resolution on the server before the final recipe list is synchronized. Reapply consistently after `/reload`, retaining a clean source snapshot rather than repeatedly editing an already-filtered list. Refresh JEI to match the actual loaded recipe manager.

Choose the narrowest supported Forge/recipe-manager integration for this lifecycle after inspecting the pinned APIs. A small, targeted compatibility hook is preferable to many copied upstream recipe files. Make conflict handling independently configurable, and report known conflicts when it is disabled.

Do not patch the addons’ entire entity loot tables to enforce carcass preference. For ordinary kills without a carcass, preserve upstream behavior and diagnose table precedence separately.

## 8. Configuration and datapack extension

Keep compatibility gameplay settings in the existing SERVER config. Proposed settings:

| Setting | Default | Meaning |
|---|---|---|
| `compat.preferExternalProducts` | `true` | Use verified optional products in carcass yields |
| `compat.providerPriority` | `["alexsmobsdelight", "alexsdelight"]` | Ordered preference; native fallback is implicit |
| `compat.disabledProviders` | `[]` | Exclude a provider’s mappings |
| `compat.resolveKnownRecipeConflicts` | `true` | Apply the audited shared-input recipe policy |
| `compat.debugLogging` | `false` | Detailed bounded diagnostics; no per-tick logging |

Reuse `replaceMobDrops`, `disabledMobs`, `deferToOfficialAddon`, and Butchery’s own options. Do not duplicate knife-permission, organ, or Looting toggles.

Make gameplay preference changes restart-required for 0.2.0 unless the full config→resolver→recipes→client refresh sequence is implemented and tested. Datapack `/reload` must still work atomically. Never apply half a configuration change while the other half waits for restart.

Provide a versioned server-data directory such as `data/<namespace>/alexsbutchery/compat_profiles/`. Support explicit entity/stage/product mappings, required mods, concrete output IDs, count/chance definitions, death equivalents, variant predicates, and disabling/replacing a built-in profile. The exact parser can follow project conventions; document its final schema with validated examples.

Illustrative profile shape to turn into an executable example:

```json
{
  "schema_version": 1,
  "entity": "alexsmobs:crocodile",
  "provider": "alexsmobsdelight",
  "required_mods": ["alexsmobsdelight"],
  "groups": {
    "meat": {
      "stages": ["cut_1", "cut_2", "cut_3"],
      "item": "alexsmobsdelight:raw_crocodile_meat",
      "count_mode": "native_group",
      "death_equivalents": [
        "alexsmobsdelight:raw_crocodile_meat",
        "alexsmobsdelight:cooked_crocodile_meat"
      ]
    }
  }
}
```

This is a **proposed new schema**, not syntax supported by 0.1.2. Define `native_group` precisely: retain this generated group’s native count/chance behavior while replacing its product. Bison uses explicit per-stage whole-meat budgets instead. Native fallback remains owned by the generated loot entry, so a missing provider cannot erase the stage’s meat.

Deterministic precedence: normal resource-pack priority resolves the same profile resource ID; a documented per-entity/product override takes precedence over the server’s global provider order; native is always the last fallback. Reject ambiguous duplicate rules at the same effective priority instead of silently choosing one. An explicit disable must survive lower-priority defaults.

Check required mods before optional item resolution. Missing absent addons are normal. Missing required items in a supposedly active built-in profile are actionable errors. Include the resource, entity, stage and failed item ID in diagnostics. Reject malformed custom reload data while retaining the last valid state; do not publish a partially rebuilt rule set.

Supply examples for selecting Alex’s Delight bison, keeping native crocodile output, adding a third-party species meat, disabling one profile, and overriding a stage table without compatibility. These data hooks provide practical KubeJS/CraftTweaker/modpack interoperability without requiring hard dependencies or claiming compatibility with untested food mods.

## 9. Discovery, diagnostics and existing integrations

### JEI and creative visibility

Use resolved server output descriptors for the existing Butchering category. Show actual item identities, count ranges and probabilities; distinguish variant alternatives and native fallback. Refresh on join/reload and clear on disconnect. Do not display the old local `MobDefs` list as authoritative after the server changes a profile.

Arbitrary custom loot tables cannot always be summarized exactly. If the table has been replaced with unsupported dynamic logic, label the display as custom/server-defined instead of promising an incorrect fixed result. Never evaluate a reward table for JEI in a way that consumes world RNG, spawns items, or grants loot.

Hide an Alex’s Butchery item from creative browsing only when **every relevant source** is replaced and no distinct use needs it visible. Crocodile compatibility does not justify hiding generic reptile meat still used by other reptiles. Existing stacks should remain discoverable through their uses; hiding is presentation, not registry removal.

### Diagnostics

Add operator-only, read-only commands such as:

```text
/alexsbutchery compat status
/alexsbutchery compat explain alexsmobs:bison
/alexsbutchery compat validate
```

`explain` should show installed provider versions, selected provider, stage products, native fallback, missing/rejected candidates, relevant config, known recipe conflicts, and which death items are suppressed or preserved. Include loaded source/profile IDs. It must not roll loot or modify the world. Log one compact startup summary and rate-limit warnings.

### Documentation and related mods

Update the Patchouli guide through `ModGuideBook`, translations through `ModLang`, README, changelog, and MODMAP’s maintained sections. Explain the two addon names, expected three-animal results, provider choice, native fallback, and the actual knife settings.

Preserve Farmer’s Delight’s knife eligibility behavior through `ButcheryHooks`: Butchery 5.3’s dedicated option is off by default; 5.2 has no such option and already treats those knives as cleavers. Do not tag every addon knife as a Butchery skinning knife or bypass that setting.

Retain the existing Immersive Tanning hide-mounting recipes and skin/rug chains. Do not substitute a raw hide with already processed addon leather without a separate audited workflow. Preserve `OfficialAddonGuard`; never emit a second carcass for a mob delegated to `butchery_alexs_mobs`, and keep the existing rule against shipping tags in that addon’s namespace.

## 10. Save compatibility and performance

- Keep all existing registered item/block IDs, blockstate numbers, snapshot keys and stage progression.
- Old untouched carcasses resolve using current policy when processed; already-completed stages stay completed. Reloading never resets a stage or reissues earlier rewards.
- Do not backfill new specialty rewards into old partially processed carcasses. The core update does not need to store a historical provider choice on every carcass.
- Existing loose native meat stays native until processed through an explicit valid recipe. Removing an optional addon restores native future yields; do not promise to rescue that removed mod’s own inventory items from Minecraft’s missing-registry behavior.
- Resolve rules once per startup/reload; cache lookups by relevant species/stage/product/variant. No registry-wide scans, JSON parsing, reflection loops, or entity construction on each cut or tick.
- Preserve fast carcass selection and rendering. Run representative smoke checks, but do not regenerate geometry assets unless geometry inputs actually changed.

## 11. Implementation map

| Existing area | Required work |
|---|---|
| `compat/Substitutions.java` | Retire item-only gameplay routing; replace with contextual resolver and validated provider definitions |
| `data/CarcassStageLoot.java` | Emit compatibility-aware default groups with exact native fallbacks |
| `def/MobDefs.java` / `MobDef.java` | Identify semantic meat/material groups without losing existing side products; keep entity/stage IDs stable |
| `butcher/CarcassLoot.java` | Route once; keep native block context and organ separation; no death-table replay |
| `butcher/CutMachine.java` | Supply stage/mob/snapshot context and preserve action/Looting semantics |
| `butcher/KillHandler.java` | Per-species represented-drop filtering only after successful carcass creation; audit event ordering |
| `compat/MobSnapshot.java` | Reuse verified variant data; add harvest metadata only if an implemented transfer needs it |
| `config/ServerConfig.java` | Provider policy and conflict settings with documented lifecycle |
| `data/ModRecipes.java`, `ModItemTags.java` | Optional bridges/tags, native recipe preservation, precise death-replacement integration |
| `client/compat/jei/*` | Server-resolved outputs and refreshed recipe visibility |
| `registry/ModCreativeTabs.java` | Context-aware visibility, especially shared generic meats |
| `compat/jade/*`, `ModGuideBook`, `ModLang` | Correct concise hints/help; avoid duplicating the full admin diagnostic in ordinary tooltips |
| New common compatibility/data/network classes | Profile loader, custom yield entry, resolved-state synchronization, conflict policy and diagnostic commands |
| `build.gradle`, `gradle.properties` | Optional test profiles and version 0.2.0; dependencies stay optional at runtime |
| `src/gameTest`, `src/smokeTest`, `tools/packtest` | Extend existing test infrastructure rather than build a parallel harness |

Read `CLAUDE.md`, README and MODMAP before editing. Use the project’s quiet Gradle helper when present. Version numbers live in `gradle.properties`. Regenerate `src/generated/resources` from the providers; do not patch generated JSON as the permanent fix. Do not bundle third-party jars.

## 12. Verification and release gates

### 12.1 Dependency matrix

Run these profiles against both Butchery 5.2 and 5.3. Keep addon dependencies valid: do not create an artificial “Alex’s Delight without Farmer’s Delight” runtime.

| Profile | Additional mods | Required coverage |
|---|---|---|
| Core | No Farmer’s Delight or either food addon | Native drops, startup, data generation, no optional linkage |
| FD | Farmer’s Delight only | Native recipes and knife regression tests |
| AMD | Farmer’s Delight + Alex’s Mobs Delight 1.1.0 | Primary complaint, all built-in AMD mappings and specialties |
| AD | Farmer’s Delight + Alex’s Delight 1.5 | Bison/bunfungus support and downstream recipes |
| Both | Farmer’s Delight + both addons | Default and reversed preference, recipe conflicts, no duplicated ordinary meat |

Include a dedicated-server/client run, two single-player worlds with different policies in one process, and a copy of a representative real pack. Add focused profiles with Immersive Tanning and the official Butchery addon. JEI/Jade/Patchouli should also be tested absent so optional UI integrations cannot become launch requirements.

### 12.2 Behavioral tests

| Test | Required assertion |
|---|---|
| Three-animal regression | Complete bear, crocodile and bison processing produces exactly the expected provider identities for every matrix profile |
| Species isolation | Rattlesnake/komodo do not become crocodile; ordinary cow steaks do not become bison; other birds/fish retain correct products |
| All declared mappings | Every active target ID exists; every supported species/product has the documented fallback |
| Death versus carving | Measure ground drops before processing and after each stage; ordinary represented meat is not paid twice |
| Specialty preservation | Crocodile claws/tail, bunfungus ear/hat, shrimp tools and effect-bearing seagull rewards survive unless explicitly transferred |
| Special knife ordering | Dimensional slicer cannot reintroduce suppressed ordinary death meat; crocodile knife’s independent on-hit rewards are preserved |
| Failed/declined carcass | Void/placement failure, baby, disabled/tamed exclusions, official-addon deferral and refused weapon leave upstream loot unchanged |
| Knife rules | 5.3 knives with setting on/off; 5.2 legacy behavior; Butcher’s Touch; cleaver-only off; cleaver also tagged as knife |
| Replacement opt-out | `replaceMobDrops=false` deliberately preserves original rewards; diagnostics explain the result |
| Raw/cooked death variants | Fire kills do not bypass duplicate suppression; processing still emits the chosen raw products |
| Looting | Existing number of stage rolls is preserved; resolver does not multiply again; finite stack limits are respected |
| Native distributions | With providers disabled/absent, generated fallback output distributions match the old tables, including bison side products |
| Yield budget | Bison compatibility budgets and whole/sliced conversions match documented totals; no bridge loop creates ingredients or XP |
| Variants | All four shrimp variants, missing/invalid old snapshot, save/reload, pickup/replacement where supported |
| Reload and precedence | Profile replacement, explicit disable, missing target, malformed reload, and a custom ordinary stage table behave as documented |
| Real recipe use | Furnaces, smoker, campfire, cutting board, cooking pot, and applicable skillet paths accept actual awarded ingredients |
| Dual-addon conflict | Shared-input recipes have a deliberate selected result, and both addons’ distinct meal chains remain obtainable |
| Client truthfulness | JEI agrees with server output and recipes before/after reload and when switching servers/worlds |
| Item data | Custom tags/effect-bearing stacks are preserved or deliberately excluded from conversion; no silent NBT loss |
| Saved-world upgrade | 0.1.2 carcasses at several stages and native inventory reopen, continue processing, and never repeat completed rewards |
| Processing integrity | Wrong tool, protected/cancelled interaction, repeated packets and concurrent players cannot advance or pay a stage twice |

Prefer deterministic tests for provider selection, counts/ranges and rule validation. Use fixed seeds or controlled fixtures for probability branches; do not make a test pass depend on a rare drop happening during one random kill. Use real entity deaths in integration tests so global loot modifiers and event listeners are exercised; direct resolver tests alone cannot prove compatibility.

### 12.3 Build and artifact validation

Run the existing compilation, data generation, build, GameTest and relevant smoke workflows. Extend Gradle with explicit optional-addon test flags and assert that the loaded mod set matches the selected profile; a missing jar must not silently turn an integration test into a core-only pass.

The repository warns that `runGameTestServer` can exit zero even when tests fail. Read the actual pass/fail summary in `run-gametest/logs/latest.log`, and make the test wrapper fail on failed or missing expected tests. Verify the fresh-world behavior already used by the harness.

Confirm generated resources are reproducible across the core and optional-mod development setups. Datagen must not bake the developer’s installed addon selection into the release jar. Inspect the final jar for optional-class linkage, correct 0.2.0 metadata, expected resources, and absence of bundled dependencies/test agents.

## 13. Work order and completion criteria

1. **Baseline and reproduction.** Pin dependencies; capture original three-animal results; audit all declared item IDs, death paths, relevant recipes and specialty rewards. Produce a compact coverage/yield manifest.
2. **Core compatibility.** Implement species/stage-aware resolution, native fallback, bear/crocodile/bison profiles and precise death suppression. Add the primary tests before broadening coverage.
3. **Coverage and recipes.** Complete the verified mapping table, variant handling, legacy-item bridges, complete cooking chains and dual-addon recipe policy. Preserve untransferred special rewards.
4. **Pack control and presentation.** Finish server config/data lifecycle, synchronization, JEI, diagnostics, examples and guide text.
5. **Release validation.** Run the full matrix, upgrade checks and representative pack; fix failures; set 0.2.0; regenerate data and update documentation.

Deliver:

- The implementation and focused regression tests.
- Updated player documentation and a compatibility matrix listing exact tested versions.
- A developer-facing profile/schema guide with working datapack examples.
- A test report with the original failure, before/after output tables, executed commands, dependency versions/hashes and remaining limitations.
- The built 0.2.0 jar and a changelog describing actual shipped behavior.

Do not mark the release complete while crocodile or bison still falls back despite a valid selected provider, death loot is duplicated or lost, one addon’s foods become unreachable when both are installed, the client describes different products from the server, or optional-mod absence breaks startup. Do not claim “full compatibility with all Delight mods”; name the integrations actually audited and tested.

## 14. Source references

The following sources were inspected for this plan. Repository links are pinned where code behavior matters. These are source findings; runtime verification remains an implementation deliverable.

- **[S1] Baseline:** [Alex’s Butchery gradle.properties](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/gradle.properties), [README](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/README.md), [CLAUDE.md](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/CLAUDE.md), [changelog](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/CHANGELOG.md).
- **[S2] Existing substitution rules:** [Substitutions.java](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/compat/Substitutions.java#L22).
- **[S3] Species outputs and bison steak pools:** [MobDefs.java](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/def/MobDefs.java#L41).
- **[S4] Stage rolling and substitutions:** [CarcassLoot.java](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/butcher/CarcassLoot.java#L28), [CutMachine.java](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/butcher/CutMachine.java), [CarcassStageLoot.java](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/data/CarcassStageLoot.java).
- **[S5] Death replacement and generated tag:** [KillHandler.java](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/butcher/KillHandler.java#L46), [ModItemTags.java](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/data/ModItemTags.java).
- **[S6] Current recipes:** [ModRecipes.java](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/data/ModRecipes.java).
- **[S7] Current recipe display:** [AlexsButcheryJeiPlugin.java](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/client/compat/jei/AlexsButcheryJeiPlugin.java#L40).
- **[S8] Alex’s Mobs Delight:** [official project page](https://www.curseforge.com/minecraft/mc-mods/alexs-mobs-delight), [version properties](https://github.com/rawchickenNEG/AlexsMobsDelight/blob/298abcf1e9ededdd8ff180867404cbe31db86f84/gradle.properties), [item registry](https://github.com/rawchickenNEG/AlexsMobsDelight/blob/298abcf1e9ededdd8ff180867404cbe31db86f84/src/main/java/io/github/rcneg/alexsmobsdelight/init/ItemRegistry.java).
- **[S9] Alex’s Mobs Delight loot:** [crocodile modifier](https://github.com/rawchickenNEG/AlexsMobsDelight/blob/298abcf1e9ededdd8ff180867404cbe31db86f84/src/main/resources/data/alexsmobsdelight/loot_modifiers/add_crocodile_meats.json), [crocodile products/conditions](https://github.com/rawchickenNEG/AlexsMobsDelight/blob/298abcf1e9ededdd8ff180867404cbe31db86f84/src/main/resources/data/alexsmobsdelight/loot_tables/crocodile_meats.json), [bison entity table](https://github.com/rawchickenNEG/AlexsMobsDelight/blob/298abcf1e9ededdd8ff180867404cbe31db86f84/src/main/resources/data/alexsmobs/loot_tables/entities/bison.json).
- **[S10] Alex’s Delight:** [official project page](https://www.curseforge.com/minecraft/mc-mods/alexs-delight), [1.20 source version](https://github.com/Baisylia/Alexs-Delight-1.20/blob/4c41f005f406204c472751fbe18955d73d5d854c/gradle.properties), [item registry](https://github.com/Baisylia/Alexs-Delight-1.20/blob/4c41f005f406204c472751fbe18955d73d5d854c/src/main/java/com/ncpbails/alexsdelight/item/ModItems.java). The project page’s source link points to its older 1.19.2 repository; this plan uses the author’s 1.20 repository instead.
- **[S11] Alex’s Delight entity overrides:** [bison](https://github.com/Baisylia/Alexs-Delight-1.20/blob/4c41f005f406204c472751fbe18955d73d5d854c/src/main/resources/data/alexsmobs/loot_tables/entities/bison.json), [bunfungus](https://github.com/Baisylia/Alexs-Delight-1.20/blob/4c41f005f406204c472751fbe18955d73d5d854c/src/main/resources/data/alexsmobs/loot_tables/entities/bunfungus.json).
- **[S12] Confirmed catfish recipe overlap:** [Alex’s Mobs Delight cutting recipe](https://github.com/rawchickenNEG/AlexsMobsDelight/blob/298abcf1e9ededdd8ff180867404cbe31db86f84/src/main/resources/data/alexsmobsdelight/recipes/cutting/raw_catfish_slice.json), [Alex’s Delight cutting recipe](https://github.com/Baisylia/Alexs-Delight-1.20/blob/4c41f005f406204c472751fbe18955d73d5d854c/src/main/resources/data/alexsdelight/recipes/cutting/raw_catfish_slice.json).
- **[S13] Special rewards and variants:** [AttackEvents.java](https://github.com/rawchickenNEG/AlexsMobsDelight/blob/298abcf1e9ededdd8ff180867404cbe31db86f84/src/main/java/io/github/rcneg/alexsmobsdelight/events/AttackEvents.java), [mantis shrimp loot](https://github.com/rawchickenNEG/AlexsMobsDelight/blob/298abcf1e9ededdd8ff180867404cbe31db86f84/src/main/resources/data/alexsmobs/loot_tables/entities/mantis_shrimp.json), [MobSnapshot.java](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/compat/MobSnapshot.java).
- **[S14] Yield and processing references:** [AMD food values](https://github.com/rawchickenNEG/AlexsMobsDelight/blob/298abcf1e9ededdd8ff180867404cbe31db86f84/src/main/java/io/github/rcneg/alexsmobsdelight/init/AMDFoodValues.java), [AD food values](https://github.com/Baisylia/Alexs-Delight-1.20/blob/4c41f005f406204c472751fbe18955d73d5d854c/src/main/java/com/ncpbails/alexsdelight/item/ModFoods.java), [AMD whole crocodile recipe](https://github.com/rawchickenNEG/AlexsMobsDelight/blob/298abcf1e9ededdd8ff180867404cbe31db86f84/src/main/resources/data/alexsmobsdelight/recipes/raw_whole_crocodile.json).
- **[S15] Forge 1.20.1 implementation guidance:** [global loot modifiers](https://docs.minecraftforge.net/en/1.20.1/resources/server/glm/), [conditionally loaded data](https://docs.minecraftforge.net/en/1.20.1/resources/server/conditional/), [tags](https://docs.minecraftforge.net/en/1.20.1/resources/server/tags/). Use these mechanisms according to their documented scope; recipe conditions are not automatically valid predicates in arbitrary custom data or loot entries.
