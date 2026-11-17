package com.otectus.alexsbutchery.client.render;

import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.alexsbutchery.client.pose.PoseProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/** Visible head geometry and solid attachment anatomy, measured in the same frame used to draw it. */
final class HeadPose {
    record Layout(Vec3 centre, AABB skull, double floor) {}
    private static final Map<PoseKey, Layout> CACHE = new LinkedHashMap<>(64, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<PoseKey, Layout> entry) { return size() > 512; }
    };

    static void clear() { CACHE.clear(); }

    static Layout get(CarcassModels.Handle handle, PoseProfile profile, CompoundTag data) {
        var shape = handle.shape(data);
        var key = new PoseKey(shape.model(), profile, shape.texture(), shape.scale(), shape.snapshot(), StageTextures.Look.FRESH, 0);
        Layout cached = CACHE.get(key);
        if (cached != null) return cached;
        var head = CarcassScene.headPart(shape, profile);
        if (head == null) return new Layout(Vec3.ZERO, new AABB(-.25, -.25, -.25, .25, .25, .25), -.25);
        Runnable restore = ModelParts.savePose(shape);
        try {
            handle.pose(shape);
            ModelBounds visible = new ModelBounds();
            ModelBounds solid = new ModelBounds();
            ModelBounds skull = new ModelBounds();
            CubeVisibility.render(shape, () -> {
                head.render(new PoseStack(), visible, 15728880, 0);
                if (head.raw() instanceof AdvancedModelBox box) {
                    ModelParts.solidBounds(box, new PoseStack(), solid);
                    ModelParts.skullBounds(box, new PoseStack(), skull);
                }
            });
            Vec3 centre = visible.bounds().getCenter();
            // Empty group roots use their solid descendants; planes never hold a skull off its support.
            AABB anatomy = solid.cubes().isEmpty() ? visible.bounds() : solid.bounds();
            AABB attachment = skull.cubes().isEmpty() ? anatomy : skull.bounds();
            float scale = profile.headScale * shape.scale();
            AABB posed = transform(attachment, centre, scale);
            Layout result = new Layout(centre, posed, transform(anatomy, centre, scale).minY);
            CACHE.put(key, result);
            return result;
        } finally { restore.run(); }
    }

    private static AABB transform(AABB box, Vec3 centre, float scale) {
        return new AABB((centre.x - box.maxX) * scale, (centre.y - box.maxY) * scale, (box.minZ - centre.z) * scale,
                (centre.x - box.minX) * scale, (centre.y - box.minY) * scale, (box.maxZ - centre.z) * scale);
    }

    private HeadPose() {}
}
