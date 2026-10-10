# Changelog

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
