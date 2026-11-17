# Alex’s Butchery: Carcass Scaling, Carrying, and Death Placement

> Historical planning/handoff document. Current implemented behavior and release status are documented in the
> [README](README.md), [changelog](CHANGELOG.md), and [0.2.2 validation report](docs/0.2.2-head-mount-validation.md).

## Implementation handoff

**Prepared:** November 15, 2026\
**Project:** [otectus/AlexsButchery](https://github.com/otectus/AlexsButchery)\
**Target:** Minecraft 1.20.1, Forge, Java 17\
**Purpose:** Fix undersized dropped carcasses, incorrect carried carcasses, and the lack of configurable block placement for additional species. Resolve the related rendering and transport defects identified below without losing existing content or save data.

This is a source review and implementation specification. No mod source was changed, and no Minecraft client, dedicated server, Gradle build, or gameplay test was run during this review. Findings labeled **confirmed** are supported by the inspected code; cross-mod consequences and screenshots still require reproduction with the actual release jars.

## 1. Start with the right source baseline

The supplied repository and the distributed mod are not currently aligned:

| Source | Inspected state | Consequence |
| --- | --- | --- |
| Alex’s Butchery GitHub | Only returned branch: `main`; commit `335ddf295bd5ee196d44df164eee01bc48b17eb2`; `gradle.properties` declares **0.1.2** | All Alex’s Butchery file references in this document are pinned to this snapshot. |
| Alex’s Butchery CurseForge | [0.2.0, file 9333422](https://www.curseforge.com/minecraft/mc-mods/alexs-butchery-compat/files/9333422), uploaded October 26, 2026 | Locate the actual release source before implementation. Preserve its cooking-addon integrations and any intervening fixes. Do not release a patched 0.1.2 tree over 0.2.0. |
| Butchery dependency declared by the repository | Minimum 5.2; development defaults to 5.2 and supports testing 5.3 | Inspect the exact supported jars; the open-ended dependency range is not proof that every later version behaves identically. |
| Butchery public source | [MC-jmods/Butchery](https://github.com/MC-jmods/Butchery), commit `1cb5598af651d991e7bee21c30fd85660e4057eb`; its `1.20.1-FORGE` metadata declares **4.9** | Useful historical evidence for donkey model transforms, not a verified representation of Butchery 5.2/5.3/5.3.1. |
| Carry On reference | Commit `0d9e6cf32e9fef32e4b5af1eb317da432dd60c7b`; metadata declares **2.1.2 / Minecraft 1.20.1** | This is the relevant inspected implementation. The current `1.20` branch targets 1.20.6 and must not be used blindly for a 1.20.1 adapter. |

Record the implementation checkout SHA and actual dependency jar versions/checksums. Compare the methods identified below against that checkout. A finding already fixed in the release source becomes a regression requirement; do not reintroduce an older implementation.

The screenshots do not identify exact versions or the carrying mod. Raised arms and an empty selected hotbar slot are consistent with **Carry On block carrying**. Treat that as a strong working hypothesis, not an established fact. Reproduce ordinary hotbar holding and Carry On lifting separately. If a different carrier is installed, trace its equivalent calls before choosing hooks.

## 2. Required result

Deliver all of the following:

1. Dropped carcass items have believable species-relative dimensions. A moose must not become a tiny token beside Butchery’s donkey. An elephant item, when obtained through breaking, creative inventory, or commands, must have a deliberate ground presentation too.
2. Inventory icons remain legible and contained. Fixing world size must not cause giant models to overflow slots, JEI, or creative tabs.
3. First-person holding, third-person holding, and block carrying each use appropriate anchors and transforms. A carried body belongs between the player’s arms, not at their feet.
4. Carried appearance preserves the actual species variant, size, fresh/drained/skinned state, removed anatomy, and skeleton form. Rendering must not alter the authoritative carried data.
5. Pack authors can make additional species appear as placed carcass blocks at death, including moose, without accidentally changing blood yield, weight, trophy size, or whether they can hang.
6. Pickup, drop, hang, carry, placement, reload, and multiplayer synchronization preserve the data needed by the operation. A move must not duplicate carcasses, restart already-collected blood, or consume an item after failed placement.
7. Existing ground contact, overhanging selection, multipart geometry, stage loot, and optional integrations continue to work.

Apply the solution across the roster. The inspected table contains **82 species and 82 pose profiles**. Do not fix only elephant and moose with isolated hard-coded multipliers.

## 3. Understand the four different representations

| Representation | Current path | What needs fixing |
| --- | --- | --- |
| Dropped inventory item | `ItemEntity` → item model → `CarcassItemRenderer`, context `GROUND` | Icon fitting and the generated ground scale compound; species size is normalized away. |
| Placed carcass block | `CarcassBlockEntity` → `CarcassRenderer` → `CarcassScene` | Already draws anatomy at world scale; preserve existing grounding and selection. Make detached rendering safe. |
| Item held in a hand | Item renderer, first/third-person hand contexts | Uses the same icon-fitting code with generic block-item transforms; needs hand-specific presentation. |
| Carcass block lifted by Carry On | Carry On state/storage → carried-object transforms → item renderer with `NONE` in the inspected version | Mixes full block bounds with a reduced item rendering; loses render-time NBT and stage. Needs a targeted adapter. |

Changing item JSON cannot change an `ItemEntity` into a block. Changing death placement does not fix discarded items or Carry On. Increasing `PoseProfile.scale` globally is particularly unsuitable: it affects placed geometry, can invalidate selection exports, and is substantially canceled by the current inverse item fit.

## 4. Verified findings and their priority

### F1 — All carcass item contexts are normalized to icon size

**Confirmed; high priority.**

[`CarcassItemRenderer.renderByItem`](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/client/render/CarcassItemRenderer.java#L43) receives `ItemDisplayContext context` but never uses it. For bodies, it computes:

```text
E = max(0.3, CarcassScene.extent(subject))
internal scale = (0.85 / E) × 0.5
```

[`ModBlockStates.entity`](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/data/ModBlockStates.java#L56) also writes a ground scale of `0.25`. Both inspected generated models, elephant and moose, contain that transform. The combined ground multiplier is `0.10625 / E`.

If the model’s diameter approximately follows `2E`, its displayed diameter approaches **0.2125 blocks regardless of species**. That figure explains the normalization; it is not an exact measured screenshot dimension because `extent()` is only an estimate of the drawn mesh.

The problem applies to fresh carcasses, drained carcasses, and skeleton items. Heads and mounted heads use a separate whole-animal-based fit in the same renderer and need their own audit.

### F2 — Carry On positions a large block but draws a small item

**Confirmed code interaction in the inspected sources; likely explanation of the carrying screenshot.**

Carry On’s [`getRenderHeight`](https://github.com/Tschipp/CarryOn/blob/0d9e6cf32e9fef32e4b5af1eb317da432dd60c7b/Common/src/main/java/tschipp/carryon/client/render/CarryRenderHelper.java#L338) obtains the block selection shape. [`applyBlockTransformations`](https://github.com/Tschipp/CarryOn/blob/0d9e6cf32e9fef32e4b5af1eb317da432dd60c7b/Common/src/main/java/tschipp/carryon/client/render/CarryRenderHelper.java#L140) lowers the render by `(height - 1) / 1.2`. The general transform scales by `0.6` and raises it by `1.6` in that scaled frame.

The bundled default moose fresh-ground selection bounds have a height of **2.6875 blocks**. Those transforms place the anchor at approximately:

```text
0.6 × (1.6 − (2.6875 − 1) / 1.2) = 0.11625 blocks above player origin
```

Carry On then uses [`renderBakedModel`](https://github.com/Tschipp/CarryOn/blob/0d9e6cf32e9fef32e4b5af1eb317da432dd60c7b/Common/src/main/java/tschipp/carryon/client/render/CarryRenderHelper.java#L233), which calls the item renderer with `ItemDisplayContext.NONE`. Alex’s Butchery still performs its icon fit. A miniature near the feet is therefore a plausible direct outcome.

This is not proof of the exact installed combination. It is a concrete, testable mechanism. Do not compensate by making real block selection boxes artificially small.

### F3 — The carried preview loses variant and processing state

**Confirmed data-flow gap in the inspected combination; high priority.**

In Carry On:

- `getRenderState()` starts from the carried block’s **default** state.
- Both first- and third-person block rendering construct a fresh `ItemStack` without the captured block-entity data.
- `getRenderBlock()` falls back to an item model for `ENTITYBLOCK_ANIMATED`/empty/custom models, which includes these carcasses.
- `CarryOnData` actually retains the authoritative state under `block` and block-entity NBT under **`tile`**. Importantly, `getContentNbt()` returns the `block` compound for blocks, not the `tile` compound.

In Alex’s Butchery, the item renderer reads only `BlockEntityTag.MobData`, creates subjects with `Set.of()` completed actions, and treats a drained item as wholly drained. With Carry On’s empty stack, appearance defaults and stage omissions are expected. This does **not** establish that Carry On destroys saved data on placement; its rendering and storage paths are different.

### F4 — Death representation is coupled to processing capabilities

**Confirmed; medium/high priority.**

[`MobDef.floor()`](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/def/MobDef.java#L79) means `!hookable`. The builder’s `.floor()` also changes blood class, weight, and mount size. [`KillHandler`](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/butcher/KillHandler.java#L41) uses that same flag to choose direct block placement versus an item drop.

The existing block-at-death species are:

| Species IDs, under `alexsmobs` | Current behavior |
| --- | --- |
| `elephant`, `cachalot_whale`, `orca`, `laviathan`, `sea_bear`, `giant_squid`, `warped_mosco`, `void_worm` | Directly place a carcass block at a valid resting position. |
| Other registered species, including `moose`, `bison`, `rhinoceros` | Create a carcass item after a qualifying kill. |

The current elephant path does **not** spawn a special persistent corpse entity or falling-block entity. It directly places the block where a falling block would rest. Preserve the fixes in that path.

There is no inspected client/server setting for world item scale or per-species death representation. The existing `floor_carcass` block tag does not control `KillHandler`.

### F5 — Item fitting uses proxy dimensions, not complete displayed geometry

**Confirmed design limitation; medium priority.**

`CarcassScene.extent()` estimates size from living collision dimensions and segment placement. Head item fitting uses the living animal’s width/height. Neither is a reliable envelope for antlers, tusks, long tails, wings, mounted plaques, or a particular cut stage. Item centering handles a vertical middle but does not establish a full measured X/Y/Z presentation frame.

Use actual posed geometry for fitting and anchoring. Reuse the existing measurement machinery; do not introduce per-frame vertex scans.

### F6 — The block renderer assumes a live world and location

**Confirmed; relevant to any carrying adapter.**

[`CarcassRenderer`](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/client/render/CarcassRenderer.java#L54) dereferences `entity.getLevel()` and samples the block above or terrain below the entity’s position. A detached block entity can have no level, and a carried preview should not inherit a slab or hook from the old position or player’s current feet.

Do not “fix” Carry On by simply invoking this world-dependent renderer on an arbitrary temporary block entity. `CarcassBlockEntity.setLevel()` also registers positions in `CarcassIndex`; assigning a world to preview entities can pollute the targeting index.

### F7 — Moving an unfinished fresh carcass as an item can reset blood progress

**Confirmed serialization omission; resource-duplication consequence requires a gameplay regression test.**

[`ModBlockLoot.wholeOrScraps`](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/data/ModBlockLoot.java#L50) returns whole fresh/drained items for states `0` and `1` and copies **only `MobData`**. The block entity stores `Bleeding`, `BleedTicks`, `Fills`, and `AcidTicks`, but these are omitted from that item transfer.

Fresh bleeding blocks remain in state `0` or `1` until conversion. Breaking one after it has filled a grate can therefore produce a fresh item without the recorded fills; rehang/restart can repeat blood production. Acid progress is similarly forgotten. Fix the round trip without turning partially butchered blocks—which currently yield scraps—into recoverable whole animals.

### F8 — Hanging consumes an item without checking placement success

**Confirmed; medium priority.**

[`HangHandler`](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/butcher/HangHandler.java#L60) checks for air below the support but ignores the return value of `level.setBlock()`, then advances the rope, shrinks the stack, and awards progress. It does not explicitly validate the below-support position against placement permissions before its direct world mutation.

This creates a failure path at invalid positions or refused placement and a compatibility gap with protection systems. Preserve normal interaction cancellation and test protection at the **destination**, not just the clicked hook.

### F9 — “Too heavy to carry” is not the actual current survival rule

**Confirmed documentation/behavior mismatch; medium priority.**

Floor carcasses have ordinary breakable block properties, registered items, and the same whole-item loot path. Consequently, an elephant can be a placed death carcass and later become a tiny dropped item when broken. Those facts are compatible with the user’s observation.

Preserve existing item recovery in this fix. Update wording to distinguish “placed at death” and “cannot hang.” Do not silently add an uncarryable restriction or remove old giant carcass stacks. A stricter transport rule would be a separate, explicit gameplay option.

### F10 — Exception safety and coverage need strengthening

**Confirmed code gaps; medium priority.**

`CarcassItemRenderer` pushes a pose without a surrounding `finally`. Parts of head rendering have the same pattern. Shared-model mutation in `CarcassScene.drawModel()` begins before its restoration `try` block. Existing restoration work is valuable, but setup exceptions can still bypass it.

The inspected test suite has substantial block placement, geometry, targeting, and bleeding coverage. It has no dedicated Carry On integration setup and no comprehensive item-context gallery. Add those missing tests rather than replacing the existing suite.

## 5. What counts as a quick fix

### Temporary, cosmetic workaround

A resource pack can override a generated item model’s `display.ground.scale`. Changing `0.25` to `1` makes that particular ground item four times larger relative to the current build. Preserve the `minecraft:builtin/entity` parent and other model fields. This is a **workaround**, not the requested complete solution: inverse fitting still removes species-relative size, placement behavior is unchanged, and Carry On uses `NONE`, not `GROUND`.

Do not publish a universal multiplier as an elephant/moose size correction. The correct value varies with the model and current renderer. Do not tell users an existing config fixes this; none was found in the inspected source.

### Minimum proper code fix

Branch rendering by display context, reserve inverse fitting for bounded views, make ground presentation use world-scale anatomy, and regenerate item models so a second hidden scale does not undo the correction. Ship that only after checking all core display contexts. The full release must also include the Carry On/state work below; a ground-only patch does not resolve the supplied carrying report.

## 6. Rendering architecture to implement

### 6.1 Separate carcass state from where it is being displayed

Introduce a shared immutable render description, or extend the existing `CarcassScene.Subject` around the same concept. Suggested names are proposals, not existing APIs:

```text
CarcassVisualState
  species / item kind
  actual processing stage and completed actions
  appearance snapshot
  fresh / drained / skinned / bone material

CarcassPresentation
  world lying / world hanging / ground item / inventory
  first-person hand / third-person hand / carried block / fixed display
  orientation, explicit support frame, scale and anchor
```

Build the visual state from three supported inputs: a live block state plus block-entity data; an item stack; or a carried state plus captured NBT. Reuse `Stages.done()` and the block renderer’s material selection. Do not duplicate stage rules in the item renderer or compatibility adapter.

The carried presentation may lay a previously hanging body horizontally while it is being transported. That changes its pose, not its processing stage or saved blockstate. Placement rules determine its final orientation.

No render-description constructor should mutate NBT, call `setLevel()`, add a world entity, advance a timer, or register a targeting position.

### 6.2 Measure once and distinguish three kinds of bounds

Use separate concepts for:

- **Visible envelope:** every displayed solid or decorative part, including antlers and mount plaques. Use it to keep GUI/fixed views within their frame.
- **Support geometry:** the solid anatomy that supports a lying carcass. Continue respecting `lying.ignore`, joint rotations, and multipart lifts from `LyingPose`.
- **Carry anchor/body span:** the torso or other appropriate grip reference. Do not move the whole body to the player’s feet because a tall antler dominates the selection envelope.

Reuse `ModelBounds`, `ModelParts`, `LyingPose`, and `PoseKey` where appropriate. Extend the metric cache to contain finite minimum/maximum coordinates, center, contact plane, and optional grip anchor. Include species/form, relevant appearance/size, stage, pose/profile revision, and renderer/model selection in the key. Do not use an entire mutable NBT object as an unbounded cache key.

Cap caches; clear them on resource reload, model reload, logout, and world changes alongside `CarcassModels.clear()`. Alternate two same-species variants in one frame to prove no cache leakage. Avoid creating dummies or measuring mesh vertices for every rendered item every frame.

### 6.3 Assign each transform to one owner

Choose and document one transform pipeline. A practical choice is:

1. `CarcassScene` owns anatomical scale, pose, removed parts, and grounding.
2. A presentation helper owns fit, grip/center/contact anchor, and context rotation.
3. Generated JSON contains only deliberate remaining transforms; it must not silently shrink geometry again.
4. Minecraft or the carrying mod owns its outer world/camera/player transform.

For `GROUND`, remove the generated quarter-scale when the Java presentation owns world dimensions. For other contexts, either retain documented JSON transforms or migrate them into the helper; never apply both copies. Verify with the actual Forge 1.20.1 item renderer’s translation order before writing numeric offsets.

For icon fitting, measure the visible geometry **after the selected presentation rotation**. Fit the resulting projected width/height into the available icon area with a small margin, then translate its center. Arbitrary living collision dimensions are not sufficient.

### 6.4 Required behavior per context

| Context | Required size/anchor behavior |
| --- | --- |
| `GUI` | Fit the complete visible form, including heads/plaques and multipart bodies, within the icon. Keep the existing recognizability and lighting quality. |
| `GROUND` | Default to the same anatomical scale as the corresponding placed lying form. Preserve differences between species and saved size variants. Use a floor-contact anchor rather than an icon midpoint. |
| First-person right/left hand | Preserve identity and relative size while keeping the grip near the hand and the view usable. Use a separate first-person presentation with documented clipping/size caps for extreme forms. Mirror intentionally. |
| Third-person right/left hand | Use a grip/body anchor with a substantial body in the hand; do not fit everything to a sub-block cube. Use the donkey as a visual comparison, not a hard-coded universal dimension. |
| `FIXED` | Fit to a deliberate display frame; do not unexpectedly apply full elephant or whale world scale inside an item frame. |
| `HEAD` | Provide a safe bounded attachment if the item is rendered on an entity’s head. Do not assume this context cannot occur. |
| `NONE` | Define a documented neutral/local presentation. It is not automatically a GUI icon and is not proof of Carry On. External callers need an explicit presentation contract. |
| Carry On block presentation | Draw from the actual carried state, using a consistent body/grip anchor and separate first/third-person framing. This is not ordinary hand-item rendering. |

Small species should remain naturally small in the world; do not enlarge every insect to donkey size. Large physical forms should remain large. For extreme giant items, prefer matching their placed form and provide an explicit optional visual cap rather than silently normalizing all species. A cap is presentation only and must not alter loot, weight, scale NBT, or block bounds.

Keep normal item pickup, despawn, merge, gravity, and inventory behavior unless a demonstrated defect requires a targeted change. A new corpse entity framework is unnecessary for this request. Check large-item culling, shadows, item-stack copies, bobbing/spinning, and contact during the existing animation. If enlarged meshes are culled while visible, address that narrowly using the actual item-render path; do not enlarge server collision boxes just to solve culling.

### 6.5 Presentation profiles and safe failure

Extend client resource data with optional item/carry presentation fields for difficult forms. Document units: Minecraft blocks for render-space offsets and degrees for rotations. Validate finite, positive scales and bounded offsets; reject malformed values with a once-per-resource warning and retain safe defaults.

Keep appearance data separate from gameplay policy. Resource packs may tune grip/pose/visual scale; they must not decide death mode or bleeding permission. Reuse the existing reload listener and cache clearing. Do not alter a species’ world `scale` merely to tune its item icon.

Wrap every pose push in `try/finally`. Start model-restoration protection before `handle.pose()` or any part mutation. Ensure that a failed render cannot leave the next carcass or a living Alex’s Mob with missing parts, wrong transforms, or a corrupt matrix stack. Keep errors diagnosable without per-frame log spam.

## 7. Carry On compatibility: implement the complete path

### 7.1 Reproduce and pin the integration

Add an optional development runtime flag for the exact supported Carry On Forge 1.20.1 artifact. Record its actual version, not just `2.1.2` from a source property. Compare the jar’s relevant methods against the pinned source. The repository currently has no Carry On development dependency or dedicated tests.

Use the player’s actual carried data. The reviewed data structure is:

```text
CarryOnData
  block: serialized BlockState (including properties)
  tile: block-entity save data (including MobData and timers)
```

Do not retrieve `MobData` from `getContentNbt()` and assume it is block-entity NBT. In this version that returns `block`. Use the verified accessor/storage path or an isolated accessor bridge for `tile`; make a defensive copy when required. Preserve unknown fields in authoritative storage.

### 7.2 Provide an explicit carried-render adapter

Create a client-only adapter for Alex’s Butchery mob blocks, with the actual **player being drawn** as an argument. It should:

1. Read that player’s carried block state and `tile` snapshot.
2. Decode a `CarcassVisualState` without constructing or ticking a live world block entity.
3. Determine the carry presentation and measured body/grip metrics.
4. Render through the shared scene path in the carrier’s coordinate frame.
5. Replace the old rendering for that object exactly once, with balanced poses and restoration on failure.

Handle local first person, rear third person, front third person, and remote players. Never read only `Minecraft.player` in a callback rendering another player. Test two people carrying different variants of the same species simultaneously.

Use a supported callback if the installed version provides one. Otherwise use narrowly scoped optional client mixins into the verified per-player block-render path. The inspected targets to study are `CarriedObjectRender.drawFirstPersonBlock`, its third-person block branch, `CarryRenderHelper.getRenderBlock`, and the height/width calculations. Specify exact signatures against the supported artifact before implementation.

Avoid globally overriding `ItemDisplayContext.NONE`, replacing every carried block renderer, or changing vanilla/Butchery blocks. `renderBakedModel()` itself has no player argument in the inspected code, so hooking it alone cannot reliably recover whose state to render. If a scoped render context is necessary, bind it around one player/object operation and clear it in `finally`, including exceptions and nested renders. Never retain “last carried player” global state.

### 7.3 Fix position and size together

Replace or bypass Carry On’s generic selection-height adjustment for these blocks. The adapter must use metrics from the **same presentation it draws**. Reusing a 2.6875-block moose height to position a normalized item is incorrect; simply enlarging that tiny item leaves the anchor problem unresolved.

Use a torso/grip anchor to support the body between the hands. Keep the rest of the body and antlers extending naturally. Author separate first-person framing if full body size obscures the camera. For large but carryable forms, a documented carry scale is acceptable; it must be consistent with the metrics used for positioning and must not rescale world blocks or drops.

Account for Carry On’s existing transforms exactly once. Retain smooth interpolated body yaw, player translation, crouch handling, and supported swimming/flying behavior. Do not add a second arm animation system when Carry On already owns the pose. Check slim and regular player models and left/right primary arm.

Respect explicit pack-authored Carry On render overrides. Define precedence: apply the built-in Alex’s Butchery adapter when no intentional replacement model is active, with a documented opt-out; do not silently replace a user-selected substitute. Cosmetic script transforms can compose with the adapter only when their order is explicitly tested.

### 7.4 Preserve state during transport

Fresh/drained/skinned appearance, removed head/limbs/segments, skeleton form, variant data, bleed counters, and acid counters must survive pickup and replacement. A carry pose may change orientation for display but cannot reset stage or regenerate anatomy.

Pause block-processing timers while detached. Resume after valid placement without retroactive catch-up rewards. Verify this against Carry On’s storage semantics; do not tick a client preview. Client render correctness alone is not proof that the server placement preserves progress.

Do not infer a new prohibition on carrying giants from `floor()`. Preserve current supported pickup behavior. If Carry On independently disallows a particular block, leave that decision intact and test a clearly allowed specimen such as moose.

### 7.5 Keep the integration optional and server-safe

Keep all Carry On rendering references in client-only compatibility classes. Gate optional mixins before target-class resolution, using the project’s Mixin setup and a suitable plugin or equivalent mechanism. Do not rely on a runtime `if` inside an otherwise eagerly loaded class.

Compile against the pinned optional API only as needed; do not bundle Carry On. Run both a client and a dedicated server **without** Carry On. Never ship unresolved optional mixin targets or references to client render classes on the server. Compatibility fallback should be limited and logged once; a missing optional mod must not prevent startup.

## 8. Configurable carcass blocks at death

### 8.1 Separate the policies

Add a death-spawn policy independent of existing species biology and handling:

| Policy | Responsibility | Existing defaults to retain |
| --- | --- | --- |
| `DeathSpawnMode.ITEM` / `PLACED_BLOCK` | What a successful qualifying kill creates | Eight existing floor species use blocks; other species use items. |
| `hookable` | Whether the species may be attached to hook/rope | Keep the current species capability. |
| `canBleedOnGround` | Whether a fresh bleeding carcass can begin/resume draining on the floor | True for the existing floor species, false for other bleeding species. Irrelevant to bloodless creatures. |
| `blood`, `weight`, `mount` | Blood quantity/timing, compatibility weight tags, trophy size | Unchanged when only death mode changes. |

Refactor the `.floor()` builder convenience so its legacy defaults are explicit. Do not use `!hookable` as the universal meaning of death representation, carrying permission, or ground-processing permission. Keep existing IDs, stage numbering, and registered block/item types.

Changing a moose to `PLACED_BLOCK` should produce a full-sized moose block without changing its blood class to `LARGE` or removing its hook support. If the pack also wants elephant-like processing on the ground, enable that **separately**.

### 8.2 Configuration contract

Implement server-authoritative, per-world options with synchronized client behavior. The following is a **proposed new schema**, not a config that works in current releases:

```toml
[carcasses]
# SPECIES_DEFAULT preserves the existing death-mode table.
# PLACED_BLOCK makes all eligible supported species prefer a placed block.
deathSpawnMode = "SPECIES_DEFAULT"

# Explicit per-species mode overrides the global choice.
# Entries use actual Alex's Mobs entity registry IDs.
deathSpawnOverrides = [
  "alexsmobs:moose=PLACED_BLOCK",
  "alexsmobs:bison=PLACED_BLOCK",
  "alexsmobs:rhinoceros=PLACED_BLOCK"
]

# Optional additional species allowed to bleed on the ground.
# Leave empty to retain the existing hang-first progression.
groundBleedingMobs = []
```

Ship empty overrides by default. Provide a documented optional large-game preset with the three overrides above. A second example can put those same entity IDs in `groundBleedingMobs` for complete ground-processing convenience. The user can then choose elephant-like placement alone or placement plus ground bleeding.

Resolve precedence as: disabled/deferred/otherwise ineligible kill → no conversion; explicit species override → global placed-block choice → species default. Support `ITEM` as a per-species exception to the global placed-block mode. It changes representation only; an elephant item still cannot be hung unless hookability is separately changed in a future feature.

Validate unknown registry IDs, unsupported species, invalid modes, duplicate/conflicting entries, and malformed identifiers. Use immutable resolved lookups, not list parsing for every death. Map actual entity IDs to `MobDef`; note that the cave centipede uses `alexsmobs:centipede_head`, while its definition/profile key is `centipede`. Document the distinction.

Treat this gameplay configuration as world-load configuration for the first implementation, synchronized to clients; document a restart requirement if needed. Do not pretend client `F3+T` reloads server rules. If runtime config reload is supported, update caches, hints, and clients together. Runtime policies must not mutate registered stack sizes or blockstate property sets.

### 8.3 Reuse and harden the existing resting-place service

Extract the existing `KillHandler.restingPlace()`/placement logic into a shared service so both legacy giants and configurable species use it. Preserve its tested handling of slabs, paths, soul sand, carpet, snow, liquids, and the void. Do not return to `FallingBlockEntity.fall()`: the repository’s changelog explains that an earlier path could remove the block occupied by the victim.

For every block-mode kill:

1. Evaluate the existing adult, tamed-mob, disabled-mob, official-addon, weapon, and boss-tool rules first.
2. Capture the original appearance snapshot before conversion.
3. Search only allowed loaded positions within build limits. Do not force-load chunks or overwrite another carcass/container/nonreplaceable block.
4. Validate the destination and applicable placement/protection rules. Keep actor handling explicit for player and nonplayer killers; do not quietly give a fabricated actor elevated permissions.
5. Place exactly one fresh ground-state block with the victim’s intended facing.
6. Initialize its block-entity payload and confirm the operation succeeded before suppressing replacement drops or granting conversion-specific progress.
7. On failure, retain the ordinary death drops and do not award a successful conversion. This matches the existing failed-giant-placement policy. Never produce both a block and a carcass item as fallback.

Return a structured success/failure result so callers cannot accidentally interpret a partially initialized block as success. Guard rollback against overwriting a block changed by another handler. Preserve event cancellation and ordinary unrelated death drops. Keep the existing drop-replacement policy unless release-source review identifies a separate verified defect.

For repeated deaths in one column, either choose a bounded valid nearby position or deliberately retain ordinary drops; document which. The initial release can preserve the current vertical search. It must never overwrite an existing corpse or scan an unbounded area.

`PLACED_BLOCK` is a one-time **death** decision. Do not silently convert every thrown item, hopper output, death inventory drop, or mined carcass back into a block. That would create pickup loops, break automation, and erase distinctions between an inventory item and an in-world object.

### 8.4 Keep the processing loop understandable

Update `CarcassBlock.useTool`, the relevant block/server logic, `FloorHintOverlay`, Jade, JEI where applicable, and Patchouli text to use the effective ground-bleeding rule. A block-mode moose with ground bleeding disabled should clearly say it must be moved to a hook. With ground bleeding enabled it should offer the cleaver step.

Static generated tags cannot automatically encode every world’s config. Do not solve runtime hints by adding all species to a static floor tag. Audit Butchery’s own hint/tag behavior against the supported dependency version and prevent contradictory guidance for overridden species. Generate owned tags accurately, while keeping runtime decisions in the shared policy resolver.

Retain skeleton/acid behavior, head and skin stages, organ settings, loot probabilities, and all cooking-addon substitutions from the actual release source. Death representation must not change yields or introduce duplicate ingredient rewards.

## 9. Transport integrity and placement fixes

### 9.1 Define an owned portable payload

Centralize serialization of the data that may accompany a whole carcass item. Preserve the established `BlockEntityTag.MobData` path for existing stacks. Add a small payload version only if required; missing new fields must mean a valid legacy unprocessed item.

For fresh whole carcasses, preserve the existing meaningful processing fields: `Bleeding`, `BleedTicks`, `Fills`, and `AcidTicks`. Keep authoritative stage identity separate from mere pose. Preserve legitimate current-release data/capabilities during supported transfers. Do not indiscriminately copy world coordinates, a live entity UUID, inventories from living mobs, or arbitrary executable behavior into a preview or placement object.

Use the same codec/helper for break loot, manual item placement, hook/rope placement, and any new migration path. Update datagen in `ModBlockLoot`, not only generated loot JSON. A damaged or old payload must load with bounded valid counters and defaults instead of crashing or granting extra blood.

### 9.2 Make progress resume instead of restart

Required lifecycle:

| Transition | Progress rule |
| --- | --- |
| Live bleeding block → whole item | Save fills already paid and the bleed clock; no additional reward on break. |
| Whole item sitting in inventory or as a dropped item | Processing paused. |
| Item → floor block that requires hanging | Keep progress paused until it is again in a valid bleeding position. |
| Item → permitted ground-processing block or hook | Resume remaining work; do not replay prior fills. |
| Block → Carry On storage → block | Preserve state and payload; pause while detached. |
| Dissolving whole block moved through a supported route | Preserve remaining acid time; resume exactly once after valid placement. |
| Mid-butchering block broken normally | Keep the existing scraps behavior. Do not reconstruct a whole carcass. |

The server ticker must validate that a bleeding carcass’s current placement is allowed to process. Merely restoring `Bleeding=true` and ticking an otherwise hook-required ground block would introduce a new progression bypass. `startBleeding()` must not reset a resumed process or replay its starting reward.

Review synchronization of the legacy `ForgeData` hint flags against the owned `Bleeding`/drained state. Restore or derive presentation flags consistently after loading; gameplay must not rely on a stale hint flag. Preserve pause/resume across ordinary chunk unload and full world reload too.

Keep different-progress stacks distinct under item merging. Equal legitimate payloads may stack according to existing stack limits; different appearance, stage, or counters must not merge and discard state. Test hopper/container round trips and multiple items dropped from one stack.

### 9.3 Make hanging transactional

Refactor `HangHandler` to commit item consumption, rope advancement, sound, and advancement only after successful placement and payload transfer. Check build height, destination suitability, actual support state, and placement permission at the lower position.

If placement fails or a protection handler rejects it:

- The original item count and NBT remain unchanged.
- The support/rope remains unchanged.
- No new carcass remains in the world.
- No successful hang advancement is awarded.
- The client receives a failure result and, when useful, a localized explanation.

Avoid a custom networking path for ordinary interactions when Forge’s existing interaction flow is sufficient. Keep the server authoritative. Verify creative mode consumes no item but still creates only one block per accepted interaction. Keep the existing main-hand hook policy unless deliberately extending it; test that an offhand click cannot duplicate or consume anything.

### 9.4 Make detached rendering safe without changing world rendering

Refactor `CarcassRenderer` to obtain an explicit world-support presentation only for a real installed block entity. A detached/missing-level path should use a neutral support frame, or call the shared scene directly from its adapter. Never sample unrelated terrain at `BlockPos.ZERO` or the player’s feet to ground a carried corpse.

Continue using `CarcassBounds.groundLevel()` for actual placed blocks. Keep outline geometry, renderer pose, reach checks, obstruction checks, and server validation aligned. Do not change collision behavior to solve visual alignment.

If shared anatomical posing, scale, or ground support changes, bump `CarcassBounds.GEOMETRY_REVISION`, re-export bounds, and run the existing staleness/geometry checks. If a change is strictly item presentation, it should not require changing server selection geometry; prove that separation.

## 10. Files and responsibilities

All paths below are relative to the implementation checkout. Main Java paths are under `src/main/java/com/otectus/alexsbutchery/` unless stated otherwise. New filenames are suggestions.

| Area | Existing files to inspect/change | Expected result |
| --- | --- | --- |
| Context rendering | `client/render/CarcassItemRenderer.java`, `CarcassScene.java`, `ModelBounds.java`, `ModelParts.java` | Explicit display contexts, measured fitting, physical ground size, correct head/mount handling. |
| Shared render state | `CarcassRenderer.java`, `CarcassScene.java`, `butcher/Stages.java`; new `CarcassVisualState`/presentation helper | One decoder for stage, material, removed anatomy, and snapshot data. |
| Caches and reload | `CarcassModels.java`, `PoseKey.java`, `LyingPose.java`, `client/pose/PoseProfile.java`, `PoseProfiles.java` | Bounded metrics caches, validated presentation profiles, correct reload. |
| Item models | `data/ModBlockStates.java`; generated item JSON | No accidental double scale; reproducible datagen. |
| Carry integration | New client-only `compat/carryon` adapter and narrowly scoped optional mixins; client mixin config; `build.gradle` | Actual carried state/appearance, correct first/third-person frame, optional dependency. |
| Death mode | `def/MobDef.java`, `MobDefs.java`, `config/ServerConfig.java`, `butcher/KillHandler.java`; new policy/placement helper | Independent placement policy and per-species configuration. |
| Portable state | `block/entity/CarcassBlockEntity.java`, `item/MobBlockItem.java`, `data/ModBlockLoot.java`, `butcher/HangHandler.java` | Shared whole-carcass payload and safe round trips. |
| Process lifecycle | `block/CarcassBlock.java`, `butcher/Bleeding.java`, `Acid.java`, applicable transitions | Pause/resume, no replayed rewards, valid processing positions. |
| Help and config presentation | `client/gui/FloorHintOverlay.java`, `compat/jade/AlexsButcheryJadePlugin.java`, `data/ModGuideBook.java`, `ModLang.java`, relevant JEI classes | Hints describe effective settings and distinguish placement from carrying/hanging. |
| Generated data | `src/generated/resources`, especially item models, loot, tags, language, guide pages | Generated from source providers, no isolated manual edits. |
| Regression tests | Existing `src/gameTest` and `src/smokeTest`; new item/carry checks | Quantitative rendering and server lifecycle coverage. |
| Release information | `README.md`, `CHANGELOG.md`, `gradle.properties`, dependency metadata if needed | Actual release lineage, supported versions, new options, migration behavior. |

Audit every `def.floor()` call during the refactor. Assign it to the correct concept instead of mechanically renaming it: death placement, hook support, ground bleeding, hints, item stack size, trophy render bounds, and advancement conditions are not interchangeable. Preserve registered stack limits in this release unless there is a separately justified migration.

## 11. Regression plan

### 11.1 Establish a repeatable visual scene

Recreate the user’s comparison with a marked block grid and the same camera, lighting, resolution, and FOV for before/after screenshots. Use a vanilla-donor Butchery donkey, moose, and elephant. Record the precise creation method for each object: qualifying kill, mined whole block, creative item, `Q` drop, ordinary held item, or Carry On pickup.

Start without shaders or cosmetic render mods. Then repeat with the reported pack’s shaders/render stack if available. Shaders are not needed to explain the verified scale/height mismatch, but the final result must work in the intended pack.

Capture inventory/JEI, dropped ground items, placed forms, normal first/third-person hands, Carry On first person, rear/front third person, and another player’s view. Use identical snapshots for the item/block comparisons.

### 11.2 Coverage across forms and species

Automatically enumerate `MobDefs.all()` and supported item kinds. Smoke-render every species in every relevant context, checking finite transforms, nonempty output, and exceptions. Do not assume every mob has a head, drained form, skeleton, or rug.

Give these cases individual visual review:

| Case | Why it matters |
| --- | --- |
| Moose with/without antlers | Reported failure; large protrusions affect fitting and carry height. |
| Elephant including relevant tusk variants | Reported tiny item; giant block-at-death and later item recovery. |
| Bison, rhinoceros, grizzly bear | Large torso/limbs and different profile extents; candidate block-mode preset. |
| Rabbit-sized/smaller Alex’s Mobs, such as jerboa and hummingbird | Small species must not be forced to a large universal size. |
| Terrapin appearance variants | Detect missing snapshot and cross-player texture leakage. |
| All supported catfish sizes | Detect size normalization and model-selection regressions. |
| Cachalot whale and Laviathan | Long/extreme geometry, culling, first-person framing, interpolation. |
| Anaconda, cave centipede, bone serpent, Void Worm | Multipart pose, centering, segment removal, per-segment grounding. |
| Farseer and Warped Mosco | Unusual anatomy, joint pose, oversized silhouettes. |
| Fresh, drained, headless, skinned, cut, and skeletal forms | Material and anatomy must follow the actual stage. |
| Heads, mounted heads, and the existing rugs | Prevent collateral regressions from shared render/registration code. |

A stage unavailable as a normal whole inventory item still needs a carried-block test if the carrier permits lifting that stage. Do not add new survival items merely to simplify visual testing.

### 11.3 Meaningful automated rendering checks

Add tests that assert behavior rather than duplicating the implementation formula:

1. **Ground parity:** after removing the outer item-entity animation transform, the item’s anatomical dimensions match the same posed block form within a documented small tolerance. Compare independent emitted-vertex bounds, not two calls to the same scale helper.
2. **Relative size:** two different-size catfish snapshots remain different in ground presentation; elephant/moose are not normalized to the same envelope as small game.
3. **GUI containment:** rendered geometry remains inside the intended icon frame, including antlers and plaques, under the actual presentation rotation.
4. **Carry anchor:** transformed torso/grip position remains near the intended hands/chest across default and variant forms. It must not follow the full selection-height offset down toward the feet.
5. **State preservation:** headless/skinned/partially cut carried meshes omit the expected anatomy and use correct material; the original payload is byte-equivalent for fields the renderer does not own.
6. **No render leakage:** alternate living mobs, items, world carcasses, and multiple same-species variants; inject a controlled render exception and verify pose/model restoration.
7. **Reload:** `F3+T`, world change, and disconnect/reconnect rebuild presentation data without stale geometry or textures.
8. **Detached safety:** rendering a neutral detached subject does not require a world lookup and does not add an entry to `CarcassIndex`.

Use the project’s existing capture/geometry infrastructure. Add before/after image outputs for human review; numeric bounds alone cannot prove good hand alignment.

### 11.4 Server and data regression matrix

| Test | Required result |
| --- | --- |
| Default adult moose kill | One item with snapshot; no placed block. |
| Default adult elephant kill | One block at the valid resting position; no carcass item. |
| Moose override `PLACED_BLOCK` | One block; unchanged blood, weight, trophy size, and hookability. |
| Global placed-block mode with one `ITEM` override | Override wins for that species; defaults remain valid for all other species. |
| Babies, disabled mobs, protected tamed cases, refused weapons, official-addon overlap | Existing refusal/deference behavior and ordinary drops preserved. |
| Giant boss-tool rule | Changing death mode does not bypass the boss-tool requirement. |
| Slab/path/soul sand/carpet/snow/fluid/void/blocked column | Existing placement expectations preserved; failed conversion keeps ordinary loot. |
| Existing carcass at destination | No overwrite, duplicate block, or lost drops. |
| Failed/canceled hook destination placement | No item consumption, rope increment, success award, or orphan block. |
| Client/server interaction repeats | One accepted operation; no client-side authoritative mutation or duplicate consumption. |
| Begin bleeding, collect at least one fill, break, rehang | Already-earned fills do not repeat; remaining process completes once. |
| Partially bleeding item placed on hook-required ground | Timer remains paused; hanging resumes it. |
| Move dissolving whole carcass | Remaining acid process survives supported movement and completes once. |
| Break a mid-cut carcass | Existing scraps result remains; no whole-body restoration. |
| Carry On pickup/place of variants and stages | Snapshot, stage, timers, and relevant metadata survive. |
| Disconnect/reconnect and chunk/world reload while carrying or processing | No reset, duplicate, invisible permanent state, or orphan target index. |
| Two players carry different forms of one species | Each client sees each player’s own payload correctly. |
| Container/hopper/item merging | Payload differences remain distinct; counts are conserved. |
| Old save and pre-fix inventory stacks | Existing IDs load; legacy payloads get valid defaults; no forced re-placement or deletion. |
| No Carry On, Jade, JEI, or Patchouli installed | Core client/server still starts and works. Test optional absences relevant to changed code. |

Preserve the current knife matrix against Butchery 5.2 and 5.3, with and without Farmer’s Delight, since `KillHandler` is being refactored. Include the current Forge 1.20.1 Butchery **5.3.1** release in compatibility validation where supported. Do not assume weighted inventory effects or hints are identical between versions: [Butchery’s 5.3 release notes](https://www.curseforge.com/minecraft/mc-mods/butchery/files/9092071) say those features were temporarily disabled for performance. Keep the addon’s weight tags, but do not add an independent slowness system to “fix” a deliberately disabled upstream feature.

### 11.5 Performance and targeting

Benchmark item piles and several simultaneously carried/placed complex carcasses using the existing frame-time tooling. Check steady-state allocations and one-time cache construction separately. No world scan, mesh union construction, new dummy entity, or NBT deserialization loop should be added to every frame for an unchanged object.

Retain the optimized `CarcassShape` and `CarcassIndex` paths. Re-run overhang targeting, wall obstruction, reach, breaking time, protection cancellation, and anatomy-bound parity whenever shared geometry or placement changes. Do not repair Carry On by regressing the previous selection-performance fix.

## 12. Build and verification commands

Use the actual source checkout and its Java 17 environment. The repository requires a local licensed Butchery jar under `libs/`; do not redistribute that dependency with the addon. The `.mcmod-tools` wrapper mentioned in project notes is environment-specific; standard Gradle wrapper commands are suitable when it is absent.

```bash
./gradlew compileJava compileGameTestJava compileSmokeTestJava
./gradlew runData
./gradlew runGameTestServer
./gradlew runGameTestServer -PwithFarmersDelight
./gradlew runGameTestServer -Pbutchery_artifact=butchery-5.3-forge
./gradlew runGameTestServer -Pbutchery_artifact=butchery-5.3-forge -PwithFarmersDelight
./gradlew runClient -PsmokeTest
./gradlew runClient -PsmokeTest -PgeometryReview
./gradlew runClient -PsmokeTest -PgroundReview=carcass-fix
./gradlew runClient -PsmokeTest -PperfBench=carcass-fix
./gradlew build smokeTestJar
```

The existing project notes warn that `runGameTestServer` can exit successfully even when tests fail. Read the pass/fail summary in `run-gametest/logs/latest.log` and the smoke-test JSON results. Add reliable result checking if needed; a green Gradle exit alone is not acceptance evidence.

Add documented switches for the new item-context and carry galleries, for example `-PitemReview` and `-PwithCarryOn -PcarryReview`. These are **new switches to implement**, not commands supported by the reviewed tree. Include a dedicated-server carry test with two observing clients or an equivalent repeatable harness. Use a no-Carry-On startup run to verify optional linkage.

When shared geometry changes:

```bash
./gradlew runClient -PsmokeTest -PexportBounds
```

Review and copy the produced `run/smoketest/carcass_bounds.json` to the generated resource location, then rerun staleness, shape, geometry, targeting, and reload tests. Preserve provenance inputs. Run the existing old-save `create`/`verify` fixture workflow using the real preceding release, in disposable test worlds.

After successful focused checks, run the project’s existing Ultima pack test if that environment is available. Record any unavailable runtime checks explicitly; do not claim visual parity from compilation or static inspection alone.

## 13. Implementation sequence

### Phase 1 — Baseline and reproductions

Resolve the 0.1.2/0.2.0 source discrepancy. Identify the exact carrying mod/version. Capture the reported item and carry failures in a controlled scene. Add failing assertions for scale, carry anchor, state preservation, failed hanging, and blood-progress round trips.

### Phase 2 — Shared visual state and item presentation

Extract stage/material decoding, introduce cached presentation metrics, implement every item context, and regenerate models. Preserve world geometry. Complete the item-context gallery before treating ground sizing as resolved.

### Phase 3 — Carry compatibility

Integrate actual carried state and `tile` NBT. Correct the metric/anchor mismatch. Exercise local and remote rendering and optional dependency startup. Test real pickup/place, not just a fabricated visual snapshot.

### Phase 4 — Configurable death blocks

Separate death mode from hook/bleeding biology. Add validated server configuration, reuse resting-place logic, and update help. Demonstrate both moose placement-only and moose placement-plus-ground-bleeding presets.

### Phase 5 — Transport and transaction integrity

Complete portable progress payloads, valid-position timer pause/resume, transactional hanging, and destination permission checks. Test old stacks and Carry On persistence alongside normal block/item movement.

### Phase 6 — Regression, pack review, and release

Run the relevant dependency matrix, the full species smoke sweep, selected visual galleries, geometry/selection checks, and before/after performance review. Reconcile generated assets and release documentation. Use the next version in the actual release lineage, not a version inferred from the stale GitHub `gradle.properties`.

Each phase should leave the project buildable. Keep commits organized around reviewable behavior changes. Do not remove completed 0.2.0 compatibility work or rewrite unrelated loot systems while addressing this report.

## 14. Acceptance checklist for the coding agent

- [ ] The implementation is based on the source matching the latest intended release; the checkout SHA and jar versions are recorded.
- [ ] Elephant, moose, and donkey comparisons show credible world dimensions without breaking inventory icons.
- [ ] All relevant display contexts have explicit behavior; no unused `context` parameter or implicit icon-fitting fallback remains.
- [ ] All 82 species and their registered forms are covered by the generated render sweep.
- [ ] Carry On renders an allowed moose at the arms/chest with correct variant and processing state, locally and remotely.
- [ ] Carry rendering neither depends on terrain under the player nor changes world selection bounds.
- [ ] Client render adapters never tick or register detached block entities and never mutate carried payloads.
- [ ] No mandatory Carry On linkage exists; no optional-mod absence crashes the client or dedicated server.
- [ ] Per-species block-at-death settings work, preserve default behavior, and do not silently alter biology or hookability.
- [ ] All documented config examples match implemented names, ID rules, precedence, and reload behavior.
- [ ] Failed creation/hanging preserves items, drops, rope state, and permissions.
- [ ] Moving a partially bleeding whole carcass cannot repeat blood rewards; invalid processing positions pause progress.
- [ ] Carrying and reloading preserve processing state, appearance, and timers; normal mid-cut break loot is unchanged.
- [ ] Existing giants remain recoverable as before; “floor” is no longer inaccurately documented as a blanket carry prohibition.
- [ ] Resource reload and injected-render-error checks show no model, texture, pose, or cache leakage.
- [ ] Existing selection performance, ground contact, multipart geometry, and release-specific integrations pass regression checks.
- [ ] The release report includes changed files, test results, before/after images, dependency versions, and any genuinely unverified cases.

## 15. Source index and review limits

These are stable references for the inspected code. Use the corresponding implementation paths after reconciling the release baseline.

| Reference | Evidence |
| --- | --- |
| [Alex’s Butchery snapshot](https://github.com/otectus/AlexsButchery/tree/335ddf295bd5ee196d44df164eee01bc48b17eb2) | Reviewed main-tree baseline. |
| [Version/dependencies](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/gradle.properties) | Forge/Minecraft/Java-era dependency targets and 0.1.2 version. |
| [Build and test setup](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/build.gradle) | Source sets, optional dependencies, smoke/geometry/bounds/performance switches. |
| [Item renderer](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/client/render/CarcassItemRenderer.java) | Context ignored; body/head fitting; snapshot decoding. |
| [Item model generation](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/data/ModBlockStates.java) | Generated quarter-size ground transform and generic hand transforms. |
| [Shared scene](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/client/render/CarcassScene.java) | Lying/hanging anatomy, multipart layout, proxy extent, shared-model restoration. |
| [Ground pose measurement](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/client/render/LyingPose.java) | Existing support measurement and bounded pose cache. |
| [World renderer](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/client/render/CarcassRenderer.java) | Accurate stage decoding and world/support dependencies. |
| [Generated bounds](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/generated/resources/data/alexsbutchery/carcass_bounds.json) | Static moose height used in the Carry On anchor calculation. These are selection envelopes, not exact visual silhouette dimensions. |
| [Species definitions](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/def/MobDefs.java) | 82-species roster and current eight block-at-death species. |
| [Death handling](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/butcher/KillHandler.java) | Direct placement, ordinary item path, kill eligibility and failure behavior. |
| [Block-entity state](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/block/entity/CarcassBlockEntity.java) | Appearance, timers, sync, index registration. |
| [Break loot](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/data/ModBlockLoot.java) | Whole-item vs scraps behavior and MobData-only copy. |
| [Bleeding lifecycle](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/butcher/Bleeding.java) | Fill count, conversion, progress behavior. |
| [Hook/rope placement](https://github.com/otectus/AlexsButchery/blob/335ddf295bd5ee196d44df164eee01bc48b17eb2/src/main/java/com/otectus/alexsbutchery/butcher/HangHandler.java) | Direct setBlock followed by item/rope mutation. |
| [Carry On 1.20.1 metadata](https://github.com/Tschipp/CarryOn/blob/0d9e6cf32e9fef32e4b5af1eb317da432dd60c7b/gradle.properties) | Pins the inspected carrier to the correct Minecraft generation. |
| [Carry On rendering](https://github.com/Tschipp/CarryOn/blob/0d9e6cf32e9fef32e4b5af1eb317da432dd60c7b/Common/src/main/java/tschipp/carryon/client/render/CarriedObjectRender.java) | Fresh item stacks and local/remote render branches. |
| [Carry On transforms and model fallback](https://github.com/Tschipp/CarryOn/blob/0d9e6cf32e9fef32e4b5af1eb317da432dd60c7b/Common/src/main/java/tschipp/carryon/client/render/CarryRenderHelper.java) | Shape height, generic offset, item fallback, `NONE` context. |
| [Carry On storage](https://github.com/Tschipp/CarryOn/blob/0d9e6cf32e9fef32e4b5af1eb317da432dd60c7b/Common/src/main/java/tschipp/carryon/common/carry/CarryOnData.java) | Separate `block` and `tile` compounds and full captured state. |
| [Historical Butchery donkey model](https://github.com/MC-jmods/Butchery/blob/1cb5598af651d991e7bee21c30fd85660e4057eb/1.20.1-FORGE/src/main/resources/assets/butchery/models/custom/donkey_carcass.json) | Separate GUI and world/hand transforms; ground transform does not impose the addon’s icon-fit chain. Historical source only. |
| [Historical Butchery version metadata](https://github.com/MC-jmods/Butchery/blob/1cb5598af651d991e7bee21c30fd85660e4057eb/1.20.1-FORGE/src/main/resources/META-INF/mods.toml) | Why this public source cannot stand in for current dependency jars. |
| [Alex’s Butchery 0.2.0 release](https://www.curseforge.com/minecraft/mc-mods/alexs-butchery-compat/files/9333422) | Distributed release is newer than the supplied GitHub tree. |
| [Butchery 5.3 release](https://www.curseforge.com/minecraft/mc-mods/butchery/files/9092071) / [5.3.1 Forge release](https://www.curseforge.com/minecraft/mc-mods/butchery/files/9112343) | Dependency-version test planning and relevant upstream changes. |

Review performed: inspected the supplied screenshots, GitHub branch/release state, the carcass render/definition/placement/serialization paths, existing test infrastructure, generated example models and bounds, relevant historical Butchery assets, and a pinned Carry On 1.20.1 render/storage implementation. Static checks confirmed the roster/profile counts, the unused item context, example ground transforms, and the numerical moose carry-offset mechanism. The distributed 0.2.0 binary and the reporter’s exact modpack were not executed or decompiled; their runtime behavior remains an explicit verification task for implementation.
