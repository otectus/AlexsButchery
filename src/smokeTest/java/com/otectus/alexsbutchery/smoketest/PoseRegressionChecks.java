package com.otectus.alexsbutchery.smoketest;

import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.github.alexthe666.citadel.client.model.TabulaModelRenderUtils.ModelBox;
import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.client.pose.PoseProfiles;
import com.otectus.alexsbutchery.client.render.CarcassModels;
import com.otectus.alexsbutchery.client.render.CarcassScene;
import com.otectus.alexsbutchery.client.render.ModelBounds;
import com.otectus.alexsbutchery.client.render.StageTextures;
import com.otectus.alexsbutchery.def.MobDefs;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real donor models and textures: visibility, cache order, snapshot ownership and interrupted borrowed meshes. */
final class PoseRegressionChecks {
    public static void run(Map<String, Object> checks) {
        var def = MobDefs.byId("moose");
        var handle = CarcassModels.get(def.entity());
        var profile = PoseProfiles.get(def);
        CompoundTag bare = new CompoundTag(), antlered = new CompoundTag();
        bare.putBoolean("Antlered", false);
        antlered.putBoolean("Antlered", true);
        var bareSubject = new CarcassScene.Subject(def, handle, profile, bare, StageTextures.Look.FRESH, Set.of());
        var antlerSubject = new CarcassScene.Subject(def, handle, profile, antlered, StageTextures.Look.FRESH, Set.of());
        var bareShape = handle.shape(bare);
        var antlerShape = handle.shape(antlered);
        checks.put("pose_moose_empty_snapshot_uses_antlered_default", handle.shape(null).texture().equals(antlerShape.texture())
                && CarcassBounds.variant("moose", new CompoundTag()).equals(CarcassBounds.variant("moose", antlered))
                && !CarcassBounds.variant("moose", bare).equals(CarcassBounds.variant("moose", antlered)));
        checks.put("pose_moose_anatomy_keys_differ", !key(bareSubject, bareShape).equals(key(antlerSubject, antlerShape)));

        // Texture alone may change visible anatomy (e.g. an alternate renderer/resource pack).
        var changedTexture = new CarcassModels.Shape(bareShape.model(), bareShape.parts(), antlerShape.texture(),
                bareShape.scale(), bareShape.width(), bareShape.height(), bareShape.snapshot());
        checks.put("pose_texture_is_part_of_key", !key(bareSubject, bareShape).equals(key(bareSubject, changedTexture)));

        // A new donor NBT field must not need adding to a whitelist. Nor may mutating the caller's NBT corrupt a map key.
        var source = new CompoundTag();
        var nested = new CompoundTag(); nested.putInt("unlisted_anatomy", 1); source.put("appearance", nested);
        var snapshot = handle.shape(source).snapshot();
        var saved = source.copy();
        Map<CarcassModels.Snapshot, Boolean> keys = new HashMap<>(); keys.put(snapshot, true);
        nested.putInt("unlisted_anatomy", 2);
        var changedSnapshot = handle.shape(source).snapshot();
        checks.put("pose_complete_snapshot_distinguishes_anatomy", !snapshot.equals(changedSnapshot));
        var oldShape = new CarcassModels.Shape(bareShape.model(), bareShape.parts(), bareShape.texture(),
                bareShape.scale(), bareShape.width(), bareShape.height(), snapshot);
        var newShape = new CarcassModels.Shape(bareShape.model(), bareShape.parts(), bareShape.texture(),
                bareShape.scale(), bareShape.width(), bareShape.height(), changedSnapshot);
        checks.put("pose_unlisted_snapshot_field_is_part_of_key", !key(bareSubject, oldShape).equals(key(bareSubject, newShape)));
        checks.put("pose_snapshot_is_immutable", Boolean.TRUE.equals(keys.get(handle.shape(saved).snapshot())));
        checks.put("pose_unchanged_snapshot_reuses_shape", handle.shape(antlered) == handle.shape(antlered.copy()));

        clearPoses();
        Mesh bareMesh = render(bareSubject, false), antlerMesh = render(antlerSubject, false);
        checks.put("pose_antlerless_moose_visible_floor", Math.abs(bareMesh.bounds().minY) < .001);
        // The visible torso spans 14 model pixels across; invisible antlers used to expand it to 42 (2.625 blocks).
        checks.put("pose_antlerless_moose_has_no_invisible_support", bareMesh.bounds().getYsize() < 1.0);
        checks.put("pose_antlered_moose_keeps_antlers", antlerMesh.bounds().getYsize() > 2.4
                && Math.abs(antlerMesh.bounds().minY) < .001);

        List<String> failures = new ArrayList<>();
        for (boolean hanging : new boolean[]{false, true}) for (var look : StageTextures.Look.values()) {
            var first = new CarcassScene.Subject(def, handle, profile, bare, look, Set.of());
            var second = new CarcassScene.Subject(def, handle, profile, antlered, look, Set.of());
            clearPoses(); Mesh coldFirst = render(first, hanging);
            clearPoses(); Mesh coldSecond = render(second, hanging);
            clearPoses();
            if (!same(coldFirst, render(first, hanging)) || !same(coldSecond, render(second, hanging)))
                failures.add(look + "/" + hanging + "/bare-first");
            clearPoses();
            if (!same(coldSecond, render(second, hanging)) || !same(coldFirst, render(first, hanging)))
                failures.add(look + "/" + hanging + "/antlers-first");
        }
        checks.put("pose_variant_render_order_failures", failures.toString());
        checks.put("pose_variant_render_order_independent", failures.isEmpty());

        var shape = handle.shape(bare);
        Map<AdvancedModelBox, ObjectList<ModelBox>> lists = new IdentityHashMap<>();
        shape.parts().values().forEach(part -> {
            if (part.raw() instanceof AdvancedModelBox box) lists.put(box, box.cubeList);
        });
        boolean[] filtered = {false};
        var expected = new IllegalStateException("intentional visibility interruption");
        boolean caught = false;
        try {
            call("CubeVisibility", "render", new Class<?>[]{CarcassModels.Shape.class, Runnable.class}, shape, (Runnable) () -> {
                filtered[0] = lists.entrySet().stream().anyMatch(e -> e.getKey().cubeList.size() < e.getValue().size());
                throw expected;
            });
        } catch (IllegalStateException e) { if (e != expected) throw e; caught = true; }
        checks.put("pose_visibility_filter_exercised", filtered[0]);
        checks.put("pose_visibility_interruption_restores_donor", caught
                && lists.entrySet().stream().allMatch(e -> e.getKey().cubeList == e.getValue()));
        clearPoses();
    }

    private static void clearPoses() {
        call("LyingPose", "clear", new Class<?>[0]);
        call("HangingPose", "clear", new Class<?>[0]);
    }

    private static Object key(CarcassScene.Subject subject, CarcassModels.Shape shape) {
        return call("PoseKey", "of", new Class<?>[]{CarcassScene.Subject.class, CarcassModels.Shape.class}, subject, shape);
    }

    // The smoke agent is a separate module: keep its package separate and production cache internals non-public.
    private static Object call(String type, String method, Class<?>[] parameters, Object... arguments) {
        try {
            var target = Class.forName("com.otectus.alexsbutchery.client.render." + type).getDeclaredMethod(method, parameters);
            target.setAccessible(true);
            return target.invoke(null, arguments);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static Mesh render(CarcassScene.Subject subject, boolean hanging) {
        Mesh mesh = new Mesh();
        if (hanging) CarcassScene.renderHanging(subject, Direction.NORTH, new PoseStack(), type -> mesh, 15728880);
        else CarcassScene.renderLying(subject, Direction.NORTH, new PoseStack(), type -> mesh, 15728880);
        return mesh;
    }

    private static boolean same(Mesh a, Mesh b) {
        if (a.vertices.size() != b.vertices.size()) return false;
        for (int i = 0; i < a.vertices.size(); i++) if (a.vertices.get(i).distanceToSqr(b.vertices.get(i)) > 1E-10) return false;
        return true;
    }

    private static final class Mesh extends ModelBounds {
        private final List<Vec3> vertices = new ArrayList<>();
        @Override public com.mojang.blaze3d.vertex.VertexConsumer vertex(double x, double y, double z) {
            vertices.add(new Vec3(x, y, z));
            return super.vertex(x, y, z);
        }
    }

    private PoseRegressionChecks() {}
}
