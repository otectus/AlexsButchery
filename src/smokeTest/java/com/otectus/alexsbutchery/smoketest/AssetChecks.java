package com.otectus.alexsbutchery.smoketest;

import com.github.alexthe666.alexsmobs.entity.EntityTerrapin;
import com.github.alexthe666.alexsmobs.entity.util.TerrapinTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.client.pose.PoseProfiles;
import com.otectus.alexsbutchery.client.render.CarcassModels;
import com.otectus.alexsbutchery.client.render.CarcassScene;
import com.otectus.alexsbutchery.client.render.StageTextures;
import com.otectus.alexsbutchery.compat.MobSnapshot;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks actual emitted vertices, material pixels and restoration of the shared living model, on the client. */
final class AssetChecks {
    static void run(Map<String, Object> checks) {
        List<String> unchanged = new ArrayList<>(), empty = new ArrayList<>(), unrestored = new ArrayList<>();
        int skeletons = 0;
        for (MobDef def : MobDefs.all()) {
            var handle = CarcassModels.get(def.entity());
            if (handle == null) continue;
            long fresh = render(def, StageTextures.Look.FRESH, Set.of(), empty);
            if (def.hasSkeleton()) {
                skeletons++;
                if (fresh == render(def, StageTextures.Look.BONE, Set.of(), empty)) unchanged.add(def.id() + ":skeleton");
            }
            Set<Stages.Action> done = EnumSet.noneOf(Stages.Action.class);
            if (def.hasHead()) done.add(Stages.Action.HEAD);
            if (def.bleeds() && def.skin() != com.otectus.alexsbutchery.def.SkinStep.NONE) done.add(Stages.Action.SKIN);
            StageTextures.Look look = done.contains(Stages.Action.SKIN) ? StageTextures.Look.SKINNED : StageTextures.Look.FRESH;
            long before = render(def, look, done, empty);
            done.add(Stages.Action.CUT_1);
            long cut1 = render(def, look, done, empty);
            done.add(Stages.Action.CUT_2);
            long cut2 = render(def, look, done, empty);
            if (before == cut1) unchanged.add(def.id() + ":cut1");
            if (cut1 == cut2) unchanged.add(def.id() + ":cut2");
            if (fresh != render(def, StageTextures.Look.FRESH, Set.of(), empty)) unrestored.add(def.id());
        }
        checks.put("asset_skeletons_checked", skeletons);
        checks.put("asset_unchanged_stages", unchanged.toString());
        checks.put("asset_all_stages_change_geometry", unchanged.isEmpty());
        checks.put("asset_empty_meshes", empty.toString());
        checks.put("asset_all_stage_meshes_visible", empty.isEmpty());
        checks.put("asset_unrestored_models", unrestored.toString());
        checks.put("asset_living_models_restored", unrestored.isEmpty());
        var tiger = CarcassModels.get(MobDefs.byId("tiger").entity());
        CompoundTag white = new CompoundTag(); white.putBoolean("White", true);
        ResourceLocation a = StageTextures.get(tiger.shape(null).texture(), StageTextures.Look.SKINNED);
        ResourceLocation b = StageTextures.get(tiger.shape(white).texture(), StageTextures.Look.SKINNED);
        checks.put("asset_skin_material_independent_of_coat", a.equals(b));
        terrapin(checks);
        alphaVariants(checks);
        exceptionalRender(checks);
        HangingChecks.run(checks);
        textureLifetime(checks);
    }

    private static long render(MobDef def, StageTextures.Look look, Set<Stages.Action> done, List<String> empty) {
        var handle = CarcassModels.get(def.entity());
        var subject = new CarcassScene.Subject(def, handle, PoseProfiles.get(def), new CompoundTag(), look, done);
        Vertices vertices = new Vertices();
        CarcassScene.renderLying(subject, Direction.SOUTH, new PoseStack(), type -> vertices, 15728880);
        if (vertices.count == 0) empty.add(def.id() + ":" + look + ":" + done);
        return vertices.hash;
    }

    static CompoundTag turtle(int variant) {
        var handle = CarcassModels.get(MobDefs.byId("terrapin").entity());
        EntityTerrapin turtle = (EntityTerrapin) handle.dummy();
        return turtle(turtle, variant);
    }

    static CompoundTag turtle(EntityTerrapin turtle, int variant) {
        turtle.setTurtleType(TerrapinTypes.OVERLAY);
        turtle.setTurtleColor(variant == 0 ? 0x749f45 : 0x568fc0);
        turtle.setShellColor(variant == 0 ? 0x7a4526 : 0xe1c564);
        turtle.setSkinColor(variant == 0 ? 0xe2cc6f : 0xc45765);
        turtle.setShellType(variant == 0 ? 0 : 4);
        turtle.setSkinType(variant == 0 ? 1 : 3);
        return MobSnapshot.capture(turtle);
    }

    private static void terrapin(Map<String, Object> checks) {
        var handle = CarcassModels.get(MobDefs.byId("terrapin").entity());
        CompoundTag first = turtle(0), second = turtle(1);
        ResourceLocation a = handle.shape(first).texture(), b = handle.shape(second).texture();
        checks.put("asset_terrapin_variant_pixels_differ", pixels(a) != pixels(b));
        ResourceLocation da = StageTextures.get(a, StageTextures.Look.DRAINED), db = StageTextures.get(b, StageTextures.Look.DRAINED);
        checks.put("asset_drained_terrapin_variant_pixels_differ", pixels(da) != pixels(db));
        checks.put("asset_terrapin_snapshot_restored", handle.shape(first).texture().equals(a));
    }

    private static void alphaVariants(Map<String, Object> checks) {
        MobDef elephant = MobDefs.byId("elephant");
        var handle = CarcassModels.get(elephant.entity());
        long[] hashes = new long[2];
        for (int i = 0; i < 2; i++) {
            CompoundTag data = new CompoundTag(); data.putBoolean("Tusked", i == 1);
            var shape = handle.shape(data);
            handle.pose(shape);
            Vertices vertices = new Vertices();
            // Render without entity scale: this must detect the actual alternate tusk geometry.
            com.otectus.alexsbutchery.client.render.StageGeometry.render(shape, PoseProfiles.get(elephant), true,
                    StageTextures.Look.BONE, Set.of(), true, new PoseStack(), type -> vertices, 15728880);
            hashes[i] = vertices.hash;
        }
        checks.put("asset_skeleton_tusk_variants_differ", hashes[0] != hashes[1]);
    }

    private static void exceptionalRender(Map<String, Object> checks) {
        MobDef def = MobDefs.byId("gorilla");
        long before = render(def, StageTextures.Look.FRESH, Set.of(), new ArrayList<>());
        var subject = new CarcassScene.Subject(def, CarcassModels.get(def.entity()), PoseProfiles.get(def), new CompoundTag(),
                StageTextures.Look.SKINNED, EnumSet.of(Stages.Action.HEAD, Stages.Action.SKIN, Stages.Action.CUT_1, Stages.Action.CUT_2));
        Vertices broken = new Vertices() {
            @Override public VertexConsumer vertex(double x, double y, double z) { throw new IllegalStateException("asset-test-interruption"); }
        };
        boolean threw = false;
        try { CarcassScene.renderLying(subject, Direction.SOUTH, new PoseStack(), type -> broken, 15728880); }
        catch (IllegalStateException e) { threw = "asset-test-interruption".equals(e.getMessage()); }
        checks.put("asset_interrupted_render_restores_model", threw && before == render(def, StageTextures.Look.FRESH, Set.of(), new ArrayList<>()));
    }

    private static void textureLifetime(Map<String, Object> checks) {
        var def = MobDefs.byId("terrapin");
        CompoundTag data = turtle(0);
        ResourceLocation id = CarcassModels.get(def.entity()).shape(data).texture();
        DynamicTexture texture = (DynamicTexture) Minecraft.getInstance().getTextureManager().getTexture(id);
        ResourceLocation drainedId = StageTextures.get(id, StageTextures.Look.DRAINED);
        DynamicTexture drained = (DynamicTexture) Minecraft.getInstance().getTextureManager().getTexture(drainedId);
        long before = pixels(id);
        CarcassModels.clear();
        checks.put("asset_world_cache_releases_textures", texture.getPixels() == null && drained.getPixels() == null);
        ResourceLocation rebuilt = CarcassModels.get(def.entity()).shape(data).texture();
        checks.put("asset_world_cache_rebuilds_appearance", before == pixels(rebuilt)
                && Minecraft.getInstance().getTextureManager().getTexture(rebuilt) != texture);
    }

    private static long pixels(ResourceLocation id) {
        if (!(Minecraft.getInstance().getTextureManager().getTexture(id) instanceof DynamicTexture texture)) return 0;
        var image = texture.getPixels();
        long hash = 1;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) hash = hash * 31 + image.getPixelRGBA(x, y);
        return hash;
    }

    private static class Vertices implements VertexConsumer {
        long hash = 1;
        int count;
        public VertexConsumer vertex(double x, double y, double z) {
            hash = hash * 31 + Math.round(x * 10000); hash = hash * 31 + Math.round(y * 10000); hash = hash * 31 + Math.round(z * 10000);
            count++; return this;
        }
        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void endVertex() {}
        public void defaultColor(int r, int g, int b, int a) {}
        public void unsetDefaultColor() {}
    }
    private AssetChecks() {}
}
