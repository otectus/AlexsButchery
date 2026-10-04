package com.otectus.alexsbutchery.smoketest;

import com.github.alexthe666.alexsmobs.entity.EntityLaviathan;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.client.pose.PoseProfiles;
import com.otectus.alexsbutchery.client.render.CarcassModels;
import com.otectus.alexsbutchery.client.render.CarcassScene;
import com.otectus.alexsbutchery.client.render.ModelBounds;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Timer;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

final class GeometryChecks {
    static void run(Map<String, Object> checks) {
        List<String> failures = new ArrayList<>();
        int cases = 0;
        for (var def : MobDefs.all()) for (var block : BoundsExport.blocks(def)) {
            List<CompoundTag> variants = new ArrayList<>(BoundsExport.variants(def));
            String sizeTag = CarcassBounds.scaleTag(def.id());
            if (!sizeTag.isEmpty()) for (var data : BoundsExport.variants(def)) for (float size : new float[]{.7F, 1.35F}) {
                var scaled = data.copy(); scaled.putFloat(sizeTag, size); variants.add(scaled);
            }
            for (int stage : block.stateProperty().getPossibleValues()) for (var data : variants) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    var support = (cases % 2 == 0 ? ButcheryHooks.hook() : ButcheryHooks.rope()).defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, Direction.from2DDataValue((cases / 3) % 4));
                    var state = block.defaultBlockState().setValue(block.stateProperty(), stage).setValue(AbstractCarcassBlock.FACING, facing);
                    var boxes = CarcassBounds.boxes(state, data, support);
                    String label = CarcassBounds.key(state, data) + "/" + facing + "/" + data;
                    ModelBounds mesh = new ModelBounds() {
                        private boolean failed;
                        @Override public VertexConsumer vertex(double x, double y, double z) {
                            if (!failed && boxes.stream().noneMatch(b -> b.inflate(.0001).contains(x, y, z))) {
                                failed = true; failures.add(label + ":vertex-outside:" + new Vec3(x, y, z));
                            }
                            return super.vertex(x, y, z);
                        }
                    };
                    BoundsExport.render(state, data, facing, support, mesh);
                    cases++;
                }
            }
        }
        checks.put("geometry_bounds_render_cases", cases);
        checks.put("geometry_bounds_failures", failures.toString());
        checks.put("geometry_bounds_cover_rendered_anatomy", failures.isEmpty());
        rugs(checks);
        laviathan(checks);
    }

    private static void rugs(Map<String, Object> checks) {
        List<String> failures = new ArrayList<>();
        Map<String, Double> chinHeights = new java.util.LinkedHashMap<>();
        for (var def : MobDefs.all()) if (def.hasRug()) {
            var handle = CarcassModels.get(def.entity());
            double height = -1;
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                ModelBounds chin = new ModelBounds();
                ModelBounds mesh = new ModelBounds() {
                    int vertices;
                    @Override public VertexConsumer vertex(double x, double y, double z) {
                        // The first solid cube is the skull/jaw, not the bison's hanging beard plane.
                        if (vertices++ < 24) chin.vertex(x, y, z);
                        return super.vertex(x, y, z);
                    }
                };
                CarcassScene.renderRugHead(handle, PoseProfiles.get(def), new CompoundTag(), facing, new PoseStack(), type -> mesh, 15728880);
                double y = chin.bounds().minY;
                chinHeights.put(def.id() + "/" + facing, y);
                if (Math.abs(y - .3 / 16) > .07) failures.add(def.id() + ":floating-chin:" + y);
                if (height >= 0 && Math.abs(height - mesh.bounds().getYsize()) > .0001) failures.add(def.id() + ":orientation-height");
                height = mesh.bounds().getYsize();
                if (height < .15) failures.add(def.id() + ":flattened-head");
            }
        }
        checks.put("rug_chin_heights", chinHeights);
        checks.put("rug_orientation_failures", failures.toString());
        checks.put("rug_heads_seated_all_orientations", failures.isEmpty());
    }

    private static void laviathan(Map<String, Object> checks) {
        var def = MobDefs.byId("laviathan");
        var state = ModBlocks.of(def).carcass().get().defaultBlockState();
        var handle = CarcassModels.get(def.entity());
        var shape = handle.shape(null);
        EntityLaviathan dummy = (EntityLaviathan) handle.dummy();
        try {
            var field = java.util.Arrays.stream(Minecraft.class.getDeclaredFields()).filter(f -> f.getType() == Timer.class).findFirst().orElseThrow();
            field.setAccessible(true);
            Timer timer = (Timer) field.get(Minecraft.getInstance());
            float previous = timer.partialTick;
            var raw = new HashSet<Long>();
            var fixed = new HashSet<Long>();
            try {
                for (float frame : new float[]{0F, .25F, .75F, 1F}) {
                    timer.partialTick = frame;
                    // Reproduce the unticked dummy's mismatched interpolation endpoints.
                    dummy.yBodyRot = 90; dummy.yBodyRotO = dummy.yHeadRot = dummy.yHeadRotO = 0;
                    shape.model().setupAnim(dummy, 0, 0, 0, 0, 0);
                    Fingerprint before = new Fingerprint();
                    shape.model().renderToBuffer(new PoseStack(), before, 15728880, 0, 1, 1, 1, 1);
                    raw.add(before.hash);
                    var livingPose = pose(shape);
                    Fingerprint after = new Fingerprint();
                    BoundsExport.render(state, new CompoundTag(), Direction.NORTH, ButcheryHooks.hook().defaultBlockState(), after);
                    fixed.add(after.hash);
                    if (!livingPose.equals(pose(shape))) checks.put("laviathan_living_pose_restored", false);
                }
            } finally { timer.partialTick = previous; }
            checks.put("laviathan_original_jitter_reproduced", raw.size() > 1);
            checks.put("laviathan_frozen_across_frame_fractions", fixed.size() == 1);
            checks.putIfAbsent("laviathan_living_pose_restored", true);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static List<Float> pose(CarcassModels.Shape shape) {
        List<Float> values = new ArrayList<>();
        shape.parts().values().forEach(p -> { for (float f : p.pivot()) values.add(f); for (float f : p.rotation()) values.add(f); });
        return values;
    }

    private static class Fingerprint extends ModelBounds {
        long hash = 1;
        @Override public VertexConsumer vertex(double x, double y, double z) {
            hash = hash * 31 + Math.round(x * 100000); hash = hash * 31 + Math.round(y * 100000); hash = hash * 31 + Math.round(z * 100000);
            return super.vertex(x, y, z);
        }
    }
    private GeometryChecks() {}
}
