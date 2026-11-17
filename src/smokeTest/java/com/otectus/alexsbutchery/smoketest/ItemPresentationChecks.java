package com.otectus.alexsbutchery.smoketest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.client.render.*;
import com.otectus.alexsbutchery.client.pose.PoseProfile;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Captures actual ItemRenderer output, independently of the presentation's measurement cache. */
final class ItemPresentationChecks {
    static void run(Map<String, Object> checks) {
        List<String> failures = new ArrayList<>();
        int forms = 0, renders = 0;
        for (var entries : ModBlocks.all()) {
            List<Block> blocks = new ArrayList<>();
            blocks.add(entries.carcass().get());
            if (entries.drained() != null) blocks.add(entries.drained().get());
            if (entries.skeleton() != null) blocks.add(entries.skeleton().get());
            if (entries.head() != null) blocks.add(entries.head().get());
            if (entries.mount() != null) blocks.add(entries.mount().get());
            if (entries.rug() != null) blocks.add(entries.rug().get());
            for (var block : blocks) {
                forms++;
                var stack = new ItemStack(block);
                boolean anatomicalItem = !(block instanceof com.otectus.alexsbutchery.block.RugBlock);
                var visual = CarcassVisualState.from(stack);
                ModelBounds raw = new ModelBounds();
                CarcassPresentation.renderNeutral(visual, new PoseStack(), type -> raw, 15728880, 0);
                for (ItemDisplayContext context : ItemDisplayContext.values()) {
                    renders++;
                    AABB bounds = render(stack, context);
                    String label = block + "/" + context;
                    if (!finite(bounds) || bounds.getSize() < 1E-5) failures.add(label + " empty/nonfinite");
                    if (anatomicalItem && (context == ItemDisplayContext.GUI || context == ItemDisplayContext.FIXED || context == ItemDisplayContext.HEAD)) {
                        double limit = context == ItemDisplayContext.HEAD ? .451 : .441;
                        if (Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX)) > limit
                                || Math.max(Math.abs(bounds.minY), Math.abs(bounds.maxY)) > limit) failures.add(label + " icon overflow " + bounds);
                    }
                    if (anatomicalItem && context == ItemDisplayContext.GUI && Math.max(bounds.getXsize(), bounds.getYsize()) < .878)
                        failures.add(label + " does not fill the projected icon frame");
                    if (anatomicalItem && context == ItemDisplayContext.GROUND && !dimensionsEqual(bounds, raw.bounds())) failures.add(label + " ground dimensions changed");
                }
            }
        }
        checks.put("item_species", MobDefs.all().size());
        checks.put("item_forms", forms);
        checks.put("item_context_renders", renders);
        checks.put("item_display_contexts", java.util.Arrays.stream(ItemDisplayContext.values()).map(Enum::name).toList());
        boolean customContextsMatch = true;
        var customContextVisual = CarcassVisualState.from(stack("moose", new CompoundTag()));
        for (ItemDisplayContext context : ItemDisplayContext.values()) if (context.isModded()) {
            ItemDisplayContext fallback = context;
            for (int limit = 32; fallback != null && fallback.isModded() && limit > 0; limit--) fallback = fallback.fallback();
            if (fallback == null || fallback.isModded()) fallback = ItemDisplayContext.NONE;
            ModelBounds actual = new ModelBounds(), expected = new ModelBounds();
            CarcassPresentation.renderItem(customContextVisual, context, new PoseStack(), type -> actual, 15728880, 0);
            CarcassPresentation.renderItem(customContextVisual, fallback, new PoseStack(), type -> expected, 15728880, 0);
            customContextsMatch &= actual.bounds().equals(expected.bounds());
        }
        checks.put("item_modded_contexts_follow_forge_fallback", customContextsMatch);
        checks.put("item_context_failures", failures.toString());
        checks.put("item_all_contexts_finite_contained_ground_parity", failures.isEmpty());
        checks.put("item_elephant_larger_than_jerboa", render(stack("elephant", new CompoundTag()), ItemDisplayContext.GROUND).getSize()
                > render(stack("jerboa", new CompoundTag()), ItemDisplayContext.GROUND).getSize() * 3);
        CompoundTag small = new CompoundTag(), large = new CompoundTag();
        small.putInt("CatfishSize", 0); large.putInt("CatfishSize", 2);
        checks.put("item_catfish_size_preserved", render(stack("catfish", large), ItemDisplayContext.GROUND).getSize()
                > render(stack("catfish", small), ItemDisplayContext.GROUND).getSize() * 1.4);

        CompoundTag antlers = new CompoundTag(), bare = new CompoundTag();
        antlers.putBoolean("Antlered", true); bare.putBoolean("Antlered", false);
        var first = stack("moose", antlers); var second = stack("moose", bare);
        var a = render(first, ItemDisplayContext.GUI); var b = render(second, ItemDisplayContext.GUI);
        checks.put("item_alternating_variants_stable", a.equals(render(first, ItemDisplayContext.GUI)) && b.equals(render(second, ItemDisplayContext.GUI)));
        var before = first.copy();
        var visual = CarcassVisualState.from(first);
        for (boolean fp : new boolean[]{true, false}) CarcassPresentation.renderCarry(visual, fp, new PoseStack(), type -> new ModelBounds(), 15728880, 0);
        checks.put("item_render_preserves_payload", ItemStack.matches(before, first));
        // Independently find the largest emitted solid box (moose torso), after the actual carry transform.
        // Raw lying-frame torso Y legitimately differs when antlers change support; the carried torso must not.
        var aGrip = carriedTorso(visual);
        var bGrip = carriedTorso(CarcassVisualState.from(second));
        checks.put("item_antlers_do_not_lower_carry_grip", aGrip.distanceTo(bGrip) < .01 && aGrip.length() < .01);

        var drained = ModBlocks.of(MobDefs.byId("moose")).drained().get();
        var cut = CarcassVisualState.from(drained.defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 9), new CompoundTag());
        checks.put("item_carry_cut_state_decoded", cut.done().contains(com.otectus.alexsbutchery.butcher.Stages.Action.HEAD)
                && cut.look() == StageTextures.Look.SKINNED);
        PoseStack pose = new PoseStack(); var frame = pose.last();
        ModelBounds broken = new ModelBounds() {
            @Override public com.mojang.blaze3d.vertex.VertexConsumer vertex(double x, double y, double z) { throw new IllegalStateException("injected item render interruption"); }
        };
        boolean threw = false;
        try { CarcassPresentation.renderItem(visual, ItemDisplayContext.GUI, pose, type -> broken, 15728880, 0); }
        catch (RuntimeException expected) { threw = true; }
        checks.put("item_exception_restores_pose_and_model", threw && pose.last() == frame && a.equals(render(first, ItemDisplayContext.GUI)));
        java.util.Set<Long> indexBefore = new java.util.HashSet<>(), indexAfter = new java.util.HashSet<>();
        var level = Minecraft.getInstance().level;
        com.otectus.alexsbutchery.block.CarcassIndex.visit(level, -2, 2, -2, 2, pos -> { indexBefore.add(pos); return true; });
        var detached = new CarcassBlockEntity(net.minecraft.core.BlockPos.ZERO, visual.state());
        detached.setMobData(antlers);
        ModelBounds detachedMesh = new ModelBounds();
        new CarcassRenderer(null).render(detached, 0, new PoseStack(), type -> detachedMesh, 15728880, 0);
        com.otectus.alexsbutchery.block.CarcassIndex.visit(level, -2, 2, -2, 2, pos -> { indexAfter.add(pos); return true; });
        checks.put("item_detached_render_has_no_world_or_index_side_effect", detached.getLevel() == null
                && indexBefore.equals(indexAfter) && detachedMesh.bounds().getSize() > 0);
        var bad = com.google.gson.JsonParser.parseString("{\"presentation\":{\"ground\":{\"scale\":-1}},\"scale\":2}").getAsJsonObject();
        PoseProfile profile = PoseProfile.fromJson(bad);
        checks.put("item_invalid_presentation_retains_anatomy_defaults", profile.presentation.isEmpty() && profile.scale == 2 && profile.presentationError != null);
        int cache = CarcassPresentation.cachedMetrics();
        for (int i = 0; i < 20; i++) { render(first, ItemDisplayContext.GUI); render(second, ItemDisplayContext.GUI); }
        checks.put("item_steady_state_metrics_reused", CarcassPresentation.cachedMetrics() == cache);
        CarcassModels.clear();
        checks.put("item_reload_clears_metrics", CarcassPresentation.cachedMetrics() == 0);
        checks.put("item_reload_rebuilds_same_geometry", a.equals(render(first, ItemDisplayContext.GUI)));
        var mooseProfile = com.otectus.alexsbutchery.client.pose.PoseProfiles.get(MobDefs.byId("moose"));
        var originalTuning = mooseProfile.presentation;
        try {
            mooseProfile.presentation = Map.of("ground", new com.otectus.alexsbutchery.client.pose.PresentationProfile(1, 0,
                    net.minecraft.world.phys.Vec3.ZERO, new net.minecraft.world.phys.Vec3(27, 0, 18)));
            CarcassModels.clear();
            float floor = LyingPose.measure(buffers -> Minecraft.getInstance().getItemRenderer().renderStatic(second,
                    ItemDisplayContext.GROUND, 15728880, 0, new PoseStack(), buffers, level, 0)).floor();
            checks.put("item_resource_pitch_roll_keeps_solid_contact", Math.abs(floor + .25) < .002);
        } finally { mooseProfile.presentation = originalTuning; CarcassModels.clear(); }
        ItemCullingChecks.run(checks);
        PoseRegressionChecks.run(checks);
    }

    static ItemStack stack(String species, CompoundTag appearance) {
        var stack = new ItemStack(ModBlocks.of(MobDefs.byId(species)).carcass().get());
        stack.getOrCreateTagElement("BlockEntityTag").put(CarcassBlockEntity.MOB_DATA, appearance.copy());
        return stack;
    }

    private static AABB render(ItemStack stack, ItemDisplayContext context) {
        ModelBounds mesh = new ModelBounds();
        Minecraft mc = Minecraft.getInstance();
        mc.getItemRenderer().renderStatic(stack, context, 15728880, 0, new PoseStack(), type -> mesh, mc.level, 0);
        return mesh.bounds();
    }

    private static boolean dimensionsEqual(AABB a, AABB b) {
        return Math.abs(a.getXsize() - b.getXsize()) < .002 && Math.abs(a.getYsize() - b.getYsize()) < .002
                && Math.abs(a.getZsize() - b.getZsize()) < .002;
    }
    private static net.minecraft.world.phys.Vec3 carriedTorso(CarcassVisualState visual) {
        ModelBounds mesh = new ModelBounds();
        CarcassPresentation.renderCarry(visual, false, new PoseStack(), type -> mesh, 15728880, 0);
        return mesh.cubes().stream().max(java.util.Comparator.comparingDouble(b -> b.getXsize() * b.getYsize() * b.getZsize()))
                .orElseThrow().getCenter();
    }
    private static boolean finite(AABB b) {
        return Double.isFinite(b.minX + b.minY + b.minZ + b.maxX + b.maxY + b.maxZ);
    }
    private ItemPresentationChecks() {}
}
