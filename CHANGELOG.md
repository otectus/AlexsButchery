# Changelog

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
