package com.otectus.alexsbutchery.smoketest;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.block.DrainedCarcassBlock;
import com.otectus.alexsbutchery.block.SkeletonBlock;
import com.otectus.alexsbutchery.block.StagedCarcassBlock;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.client.pose.PoseProfiles;
import com.otectus.alexsbutchery.client.render.CarcassModels;
import com.otectus.alexsbutchery.client.render.CarcassScene;
import com.otectus.alexsbutchery.client.render.ModelBounds;
import com.otectus.alexsbutchery.client.render.StageTextures;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reproducible common-side bounds, generated with the exact dependency models and pose/stage renderer. */
final class BoundsExport {
    static List<CompoundTag> variants(MobDef def) {
        List<CompoundTag> data = new ArrayList<>();
        data.add(new CompoundTag());
        switch (def.id()) {
            case "catfish" -> { for (int i = 1; i < 3; i++) { var nbt = new CompoundTag(); nbt.putInt("CatfishSize", i); data.add(nbt); } }
            case "elephant" -> { var nbt = new CompoundTag(); nbt.putBoolean("Tusked", true); data.add(nbt); }
            case "blobfish" -> { var nbt = new CompoundTag(); nbt.putBoolean("Depressurized", true); data.add(nbt); }
            case "bison" -> { var nbt = new CompoundTag(); nbt.putInt("Age", -24000); data.add(nbt); }
            case "gorilla" -> { var nbt = new CompoundTag(); nbt.putBoolean("Silverback", true); data.add(nbt); }
            case "gelada_monkey" -> { var nbt = new CompoundTag(); nbt.putBoolean("Leader", true); data.add(nbt); }
            case "leafcutter_ant" -> { var nbt = new CompoundTag(); nbt.putBoolean("Queen", true); data.add(nbt); }
            case "flutter" -> { var nbt = new CompoundTag(); nbt.putBoolean("Potted", true); data.add(nbt); }
        }
        return data;
    }

    static List<AbstractCarcassBlock> blocks(MobDef def) {
        var entry = ModBlocks.of(def);
        List<AbstractCarcassBlock> result = new ArrayList<>();
        result.add((AbstractCarcassBlock) entry.carcass().get());
        if (entry.drained() != null) result.add((AbstractCarcassBlock) entry.drained().get());
        if (entry.skeleton() != null) result.add((AbstractCarcassBlock) entry.skeleton().get());
        return result;
    }

    static CarcassScene.Subject subject(BlockState state, CompoundTag data) {
        var block = (AbstractCarcassBlock) state.getBlock();
        MobDef def = block.def();
        Set<Stages.Action> done = block instanceof DrainedCarcassBlock ? Stages.done(def, false, block.stage(state))
                : block instanceof StagedCarcassBlock ? Stages.done(def, true, block.stage(state)) : Set.of();
        StageTextures.Look look = block instanceof SkeletonBlock ? StageTextures.Look.BONE
                : block instanceof DrainedCarcassBlock ? (done.contains(Stages.Action.SKIN) ? StageTextures.Look.SKINNED : StageTextures.Look.DRAINED)
                : StageTextures.Look.FRESH;
        return new CarcassScene.Subject(def, CarcassModels.get(def.entity()), PoseProfiles.get(def), data, look, done);
    }

    static void render(BlockState state, CompoundTag data, Direction facing, BlockState support, ModelBounds mesh) {
        var subject = subject(state, data);
        if (((AbstractCarcassBlock) state.getBlock()).hanging(state))
            CarcassScene.renderHanging(subject, facing, support, new PoseStack(), type -> mesh, 15728880);
        else CarcassScene.renderLying(subject, facing, new PoseStack(), type -> mesh, 15728880);
    }

    static void write(Path path, Map<String, Object> checks) throws java.io.IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(CarcassBounds.INPUTS, CarcassBounds.currentInputs());
        var support = ButcheryHooks.hook().defaultBlockState();
        var anchor = CarcassBounds.support(support).subtract(.5, 1, .5);
        for (MobDef def : MobDefs.all()) for (var block : blocks(def)) for (int stage : block.stateProperty().getPossibleValues()) {
            var state = block.defaultBlockState().setValue(block.stateProperty(), stage);
            for (var data : variants(def)) {
                ModelBounds mesh = new ModelBounds();
                render(state, data, Direction.NORTH, support, mesh);
                List<AABB> boxes = new ArrayList<>();
                for (var cube : mesh.cubes()) {
                    var b = block.hanging(state) ? cube.move(anchor.scale(-1)) : cube;
                    // One thirty-second block of picking tolerance also makes fins/whiskers targetable.
                    b = new AABB(Math.floor(b.minX * 32) / 32 - .03125, Math.floor(b.minY * 32) / 32 - .03125,
                            Math.floor(b.minZ * 32) / 32 - .03125, Math.ceil(b.maxX * 32) / 32 + .03125,
                            Math.ceil(b.maxY * 32) / 32 + .03125, Math.ceil(b.maxZ * 32) / 32 + .03125);
                    AABB candidate = b;
                    if (boxes.stream().anyMatch(old -> contains(old, candidate))) continue;
                    boxes.removeIf(old -> contains(candidate, old));
                    boxes.add(b);
                }
                if (boxes.isEmpty()) throw new IllegalStateException("Empty bounds: " + CarcassBounds.key(state, data));
                for (var b : boxes) if (Math.max(Math.max(Math.abs(b.minX), Math.abs(b.maxX)),
                        Math.max(Math.abs(b.minZ), Math.abs(b.maxZ))) >= CarcassBounds.SEARCH_RADIUS - 1)
                    throw new IllegalStateException("Bounds exceed search radius: " + def.id());
                result.put(CarcassBounds.key(state, data), boxes.stream().map(b -> new double[]{b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ}).toList());
            }
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, new GsonBuilder().create().toJson(result) + "\n");
        checks.put("bounds_export_entries", result.size() - 1);
    }

    private static boolean contains(AABB outer, AABB inner) {
        return outer.minX <= inner.minX && outer.minY <= inner.minY && outer.minZ <= inner.minZ
                && outer.maxX >= inner.maxX && outer.maxY >= inner.maxY && outer.maxZ >= inner.maxZ;
    }
    private BoundsExport() {}
}
