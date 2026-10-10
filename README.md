# Alex's Butchery

Butchery add-on that covers every Alex's Mobs creature (Forge 1.20.1): cleaver kills drop a carcass, hang it on
Butchery's hook, bleed it into a blood grate, take the head, skin or pluck it, cut it up for meat and Butchery's
organs, mount the head at the taxidermy table and cure the skin on the skin rack. Carcasses are drawn with Alex's
Mobs' models and saved appearance, including terrapin colors and patterns. Skinned stages use separate tissue
materials; skeletons use skull, rib, spine and limb geometry fitted to the animal's model.

Acid dissolves a whole carcass into its skeleton, and tiger, snow leopard, grizzly and bison pelts make rugs. Jade shows
each carcass's next step and tool. Butchery's Patchouli guide gains an "Alex's Mobs" category, advancements join
Butchery's tree, and Farmer's Delight's cutting board takes the new meats.

Which weapons turn a kill into a carcass follows Butchery's own options. With "Only Cleavers Drop Carcasses" on, a
cleaver or Butcher's Touch is needed. Farmer's Delight's knives (and anything else in `farmersdelight:tools/knives`)
count only when Butchery 5.3's "Farmers Delight Knives Drop Carcasses" is on; that option is in `config/Butchery.toml`
under `[Farmers Delight Compatibility]`, off by default. Butchery 5.2 has no such option and treats those knives as
cleavers, so with 5.2 they always butcher. A refused kill leaves the creature's ordinary drops alone.

## Building

Butchery is All Rights Reserved and is not on any Maven, so the build reads it from `libs/`
(`libs/butchery-5.2-forge-1.20.1.jar`, gitignored). Download it from CurseForge into `libs/` before building.
The mod runs on Butchery 5.2 and 5.3. To build and test against 5.3, put `butchery-5.3-forge-1.20.1.jar` in `libs/` and add
`-Pbutchery_artifact=butchery-5.3-forge` to any Gradle command. Alex's Mobs and Citadel are fetched from the Modrinth
Maven. Versions live in `gradle.properties`.

- `.mcmod-tools/gradlew-quiet.sh <dir> compileJava compileGameTestJava compileSmokeTestJava` - compile
- `... runData` - regenerate `src/generated/resources` from the mob and item tables
- `... build smokeTestJar` - the mod jar and the test agent jar in `build/libs/`
- `python3 tools/generate_textures.py --write` - export the item icons from `art/butchery_art.py`

## Testing

- `... runGameTestServer` - headless GameTests; the pass/fail line is in `run-gametest/logs/latest.log`
- `... runGameTestServer -PwithFarmersDelight` - the same with Farmer's Delight loaded, which adds the knife tests. A
  run fails when Farmer's Delight's presence does not match the flag. Run both, against both Butchery versions.
- `... runClient -PsmokeTest` - stages every carcass and screenshots them to `run/screenshots/`
- `... runClient -PsmokeTest -PperfBench=<label>` - frame-time and targeting benchmark in fixed scenes, written to
  `run/smoketest/perf-<label>.json`; `-PperfSeconds`, `-PperfWarmup` and `-PperfSamples` set the sampling
- `... runClient -PsmokeTest -PsaveCompat=create` (with an earlier build), then `-PsaveCompat=verify` - an old-save
  fixture and its check, `run/smoketest/save-compat*.json`
- `... runClient -PsmokeTest -PremoteServer=host:port` - cutting, breaking and refused actions against a dedicated
  server where the dev player is an operator, written to `run/smoketest/remote-result.json`
- `python3 tools/packtest/ultima_pack_test.py` - runs the built jars in an isolated copy of the Ultima CurseForge
  instance and writes `build/packtest/<label>/summary.json`

## Adding a mob

Add a row to `def/MobDefs.java` (archetype helper, drops per stage, replaced drops), a pose profile under
`assets/alexsbutchery/pose_profiles/<mob>.json` if the name heuristics do not fit, then `runData`. A lying carcass rests
on its lowest solid part; in the profile's `lying` block, `ignore` names parts that should not hold the body up (ears,
flippers, dangling legs) and `rotations` sets joint angles in degrees. Then re-export the bounds (`-PexportBounds`).
