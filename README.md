# Alex's Butchery

Butchery add-on that covers every Alex's Mobs creature (Forge 1.20.1): cleaver kills drop a carcass, hang it on
Butchery's hook, bleed it into a blood grate, take the head, skin or pluck it, cut it up for meat and Butchery's
organs, mount the head at the taxidermy table and cure the skin on the skin rack. Carcasses are drawn with Alex's
Mobs' models and saved appearance, including terrapin colors and patterns. Skinned stages use separate tissue
materials; skeletons use skull, rib, spine and limb geometry fitted to the animal's model.

Acid dissolves a whole carcass into its skeleton, and tiger, snow leopard, grizzly and bison pelts make rugs. Jade shows
each carcass's next step and tool. Butchery's Patchouli guide gains an "Alex's Mobs" category, advancements join
Butchery's tree, and Farmer's Delight's cutting board takes the new meats.

## Building

Butchery is All Rights Reserved and is not on any Maven, so the build reads it from `libs/`
(`libs/butchery-5.2-forge-1.20.1.jar`, gitignored). Download it from CurseForge into `libs/` before building.
Alex's Mobs and Citadel are fetched from the Modrinth Maven. Versions live in `gradle.properties`.

- `.mcmod-tools/gradlew-quiet.sh <dir> compileJava compileGameTestJava compileSmokeTestJava` - compile
- `... runData` - regenerate `src/generated/resources` from the mob and item tables
- `... build smokeTestJar` - the mod jar and the test agent jar in `build/libs/`
- `python3 tools/generate_textures.py --write` - export the item icons from `art/butchery_art.py`

## Testing

- `... runGameTestServer` - headless GameTests; the pass/fail line is in `run-gametest/logs/latest.log`
- `... runClient -PsmokeTest` - stages every carcass and screenshots them to `run/screenshots/`
- `python3 tools/packtest/ultima_pack_test.py` - runs the built jars in an isolated copy of the Ultima CurseForge
  instance and writes `build/packtest/<label>/summary.json`

## Adding a mob

Add a row to `def/MobDefs.java` (archetype helper, drops per stage, replaced drops), a pose profile under
`assets/alexsbutchery/pose_profiles/<mob>.json` if the name heuristics do not fit, then `runData`.
