package com.otectus.alexsbutchery.client.render;

import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.github.alexthe666.citadel.client.model.TabulaModelRenderUtils.ModelBox;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.NativeImage;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.client.pose.PoseProfile;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Separate tissue and skeletal meshes fitted to the donor model's local anatomical boxes. The donor's hierarchy,
 * pivots and rotations still pose the animal; its coat UVs and soft-tissue volumes never become "bones".
 * Mesh lists are borrowed only inside a render-thread try/finally, and never left on the living entity's model.
 */
public final class StageGeometry {
    private record Key(EntityModel<?> model, ResourceLocation texture, PoseProfile profile, boolean headModel) {}
    private record Part(ObjectList<ModelBox> flesh, ObjectList<ModelBox> firstCut,
                        ObjectList<ModelBox> secondCut, ObjectList<ModelBox> bones, boolean hard) {}
    private static final Map<Key, Map<AdvancedModelBox, Part>> CACHE = new HashMap<>();

    public static void clear() { CACHE.clear(); }

    private static Map<AdvancedModelBox, Part> mesh(CarcassModels.Shape shape, PoseProfile profile, boolean headModel) {
        return CACHE.computeIfAbsent(new Key(shape.model(), shape.texture(), profile, headModel), key -> {
            Map<AdvancedModelBox, Part> parts = new IdentityHashMap<>();
            try (NativeImage pixels = StageTextures.read(shape.texture())) {
                shape.parts().forEach((name, part) -> {
                    if (!(part.raw() instanceof AdvancedModelBox box) || parts.containsKey(box)) return;
                    String n = name.toLowerCase(Locale.ROOT);
                    ObjectList<ModelBox> flesh = new ObjectArrayList<>(), first = new ObjectArrayList<>(), second = new ObjectArrayList<>(), bones = new ObjectArrayList<>();
                    boolean soft = soft(n);
                    boolean hard = n.contains("horn") || n.contains("antler") || n.contains("tusk") || n.contains("beak")
                            || n.contains("claw") || n.contains("hoof") || n.contains("tooth") || n.contains("teeth");
                    boolean head = n.equals("head") || n.equals("skull") || headModel && name.equals(profile.headRoot);
                    if (!soft) for (ModelBox cube : box.cubeList) {
                        if (!CubeVisibility.visible(cube, pixels)) continue;
                        Bounds b = Bounds.of(cube);
                        // Feather, membrane and fur planes are surface detail, not a slab of muscle or a bone.
                        if (b.minSize() <= 0.01F) {
                            if (n.contains("fin") || n.contains("wing")) longBone(b.thicken(), bones);
                            continue;
                        }
                        float inset = Math.min(0.35F, b.minSize() * 0.08F);
                        Bounds muscle = b.inset(inset);
                        flesh.add(cube(muscle));
                        first.add(cube(muscle.cut(0.75F)));
                        second.add(cube(muscle.cut(0.45F)));
                        if (head) skull(b, bones);
                        else if (n.equals("body") && headModel && !shape.parts().containsKey("head") && !shape.parts().containsKey(profile.headRoot)) {
                            // Small fish have one body cube including the head, unlike their larger variants.
                            skull(new Bounds(b.x, b.y, b.z, b.w, b.h, b.d * 0.28F), bones);
                            ribs(new Bounds(b.x, b.y, b.z + b.d * 0.28F, b.w, b.h, b.d * 0.72F), bones);
                        } else if (n.contains("body") || n.contains("chest") || n.contains("torso") || n.contains("abdomen") || n.equals("part") || n.equals("shell")) ribs(b, bones);
                        else if (n.contains("tail") || n.contains("neck")) spine(b, bones);
                        else if (n.contains("jaw") || n.contains("snout") || n.contains("beak") || n.contains("muzzle")) jaw(b, bones);
                        else if (n.contains("horn") || n.contains("antler") || n.contains("tusk") || n.contains("tooth") || n.contains("teeth")) bones.add(cube(b.inset(b.minSize() * 0.08F)));
                        else longBone(b, bones);
                    }
                    parts.put(box, new Part(flesh, first, second, bones, hard));
                });
            } catch (IOException e) {
                throw new IllegalStateException("No source texture for tissue geometry: " + shape.texture(), e);
            }
            return parts;
        });
    }

    private static boolean soft(String n) {
        return n.contains("ear") && !n.contains("rear") || n.contains("hair") || n.contains("fur") || n.contains("mane")
                || n.contains("feather") || n.contains("whisker") || n.contains("barbel") || n.contains("tongue") || n.contains("trunk")
                || n.contains("eye") || n.contains("eyelid") || n.contains("pouch") || n.contains("saddle")
                || n.contains("carpet") || n.contains("cabin") || n.contains("hat") || n.contains("microphone")
                || n.contains("sack") || n.contains("flag") || n.contains("antenna");
    }

    public static void render(CarcassModels.Shape shape, PoseProfile profile, boolean headModel, StageTextures.Look look,
                              Set<Stages.Action> done, boolean hasSkeleton, PoseStack pose, MultiBufferSource buffers, int light) {
        var mesh = mesh(shape, profile, headModel);
        boolean truncates = profile.segments != null && profile.segments.truncateCuts;
        boolean second = !truncates && done.contains(Stages.Action.CUT_2);
        boolean first = !truncates && done.contains(Stages.Action.CUT_1);
        boolean bone = look == StageTextures.Look.BONE;
        if (mesh.isEmpty()) throw new IllegalStateException("No tissue geometry for " + shape.model().getClass().getName());
        Map<AdvancedModelBox, ObjectList<ModelBox>> saved = new IdentityHashMap<>();
        mesh.forEach((box, part) -> saved.put(box, box.cubeList));
        try {
            // Later cuts expose the remaining rib cage rather than pretending that another tail was removed.
            if (bone || second && hasSkeleton) {
                mesh.forEach((box, part) -> box.cubeList = part.bones());
                draw(shape, StageTextures.Look.BONE, pose, buffers, light);
            }
            if (!bone) {
                mesh.forEach((box, part) -> box.cubeList = part.hard() ? ObjectList.of() : second ? part.secondCut() : first ? part.firstCut() : part.flesh());
                draw(shape, StageTextures.Look.SKINNED, pose, buffers, light);
                // Keratin and teeth keep their own appearance when surrounding skin/flesh is removed.
                mesh.forEach((box, part) -> box.cubeList = part.hard() ? saved.get(box) : ObjectList.of());
                shape.model().renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(shape.texture())),
                        light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
            }
        } finally {
            saved.forEach((box, cubes) -> box.cubeList = cubes);
        }
    }

    private static void draw(CarcassModels.Shape shape, StageTextures.Look look, PoseStack pose, MultiBufferSource buffers, int light) {
        shape.model().renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(StageTextures.material(look))),
                light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
    }

    private record Bounds(float x, float y, float z, float w, float h, float d) {
        static Bounds of(ModelBox b) { return new Bounds(b.posX1, b.posY1, b.posZ1, b.posX2 - b.posX1, b.posY2 - b.posY1, b.posZ2 - b.posZ1); }
        float minSize() { return Math.min(w, Math.min(h, d)); }
        Bounds thicken() { return new Bounds(x, y, z, Math.max(0.4F, w), Math.max(0.4F, h), Math.max(0.4F, d)); }
        Bounds inset(float v) { return new Bounds(x + v, y + v, z + v, w - 2 * v, h - 2 * v, d - 2 * v); }
        Bounds cut(float fraction) {
            // Keep the rear/lower portion. The exposed face is closed and receives the tissue material.
            return h > d ? new Bounds(x, y + h * (1 - fraction), z, w, h * fraction, d)
                    : new Bounds(x, y, z + d * (1 - fraction), w, h, d * fraction);
        }
    }

    private static ModelBox cube(Bounds b) {
        // Deliberately new UVs: coat transparency, spots and eyes must not survive skinning.
        return new ModelBox(0, 0, b.x, b.y, b.z, b.w, b.h, b.d, 0, 0, 0, false, StageTextures.MATERIAL_SIZE, StageTextures.MATERIAL_SIZE);
    }

    private static void box(ObjectList<ModelBox> out, float x, float y, float z, float w, float h, float d) {
        if (w > 0 && h > 0 && d > 0) out.add(cube(new Bounds(x, y, z, w, h, d)));
    }

    /** Skull roof, occiput, cheek pillars and jaw leave real eye openings and a hollow underside. */
    private static void skull(Bounds original, ObjectList<ModelBox> out) {
        Bounds b = original.inset(original.minSize() * 0.10F);
        float t = Math.max(0.15F, b.minSize() * 0.18F);
        box(out, b.x, b.y, b.z, b.w, t, b.d);
        box(out, b.x, b.y, b.z + b.d - t, b.w, b.h, t);
        box(out, b.x, b.y + b.h * 0.5F, b.z, t, b.h * 0.5F, b.d);
        box(out, b.x + b.w - t, b.y + b.h * 0.5F, b.z, t, b.h * 0.5F, b.d);
        box(out, b.x, b.y + b.h - t, b.z, b.w, t, b.d);
        box(out, b.x + b.w * 0.4F, b.y + t, b.z, b.w * 0.2F, b.h * 0.45F, t);
    }

    private static void jaw(Bounds b, ObjectList<ModelBox> out) {
        float t = Math.max(0.12F, b.minSize() * 0.22F);
        box(out, b.x, b.y + b.h - t, b.z, b.w, t, b.d);
        box(out, b.x, b.y + b.h * 0.4F, b.z, t, b.h * 0.6F, b.d);
        box(out, b.x + b.w - t, b.y + b.h * 0.4F, b.z, t, b.h * 0.6F, b.d);
    }

    /** Ribs wrap around an empty chest, with a dorsal spine and a narrower sternum. */
    private static void ribs(Bounds original, ObjectList<ModelBox> out) {
        Bounds b = original.inset(original.minSize() * 0.13F);
        boolean upright = b.h > b.d;
        float length = upright ? b.h : b.d;
        float t = Math.max(0.12F, Math.min(b.w, upright ? b.d : b.h) * 0.10F);
        int count = Math.max(3, Math.min(9, Math.round(length / (t * 3F))));
        if (upright) {
            box(out, b.x + b.w / 2 - t / 2, b.y, b.z + b.d - t, t, b.h, t);
            box(out, b.x + b.w / 2 - t / 2, b.y + b.h * 0.12F, b.z, t, b.h * 0.72F, t);
        } else {
            box(out, b.x + b.w / 2 - t / 2, b.y, b.z, t, t, b.d);
            box(out, b.x + b.w / 2 - t / 2, b.y + b.h - t, b.z + b.d * 0.12F, t, t, b.d * 0.72F);
        }
        for (int i = 0; i < count; i++) {
            float at = length * (0.08F + 0.82F * i / (count - 1));
            if (upright) {
                box(out, b.x, b.y + at, b.z, t, t, b.d);
                box(out, b.x + b.w - t, b.y + at, b.z, t, t, b.d);
                box(out, b.x, b.y + at, b.z, b.w, t, t);
                box(out, b.x, b.y + at, b.z + b.d - t, b.w, t, t);
            } else {
                box(out, b.x, b.y, b.z + at, t, b.h, t);
                box(out, b.x + b.w - t, b.y, b.z + at, t, b.h, t);
                box(out, b.x, b.y, b.z + at, b.w, t, t);
                box(out, b.x, b.y + b.h - t, b.z + at, b.w, t, t);
            }
        }
    }

    private static void spine(Bounds b, ObjectList<ModelBox> out) {
        longBone(b, out);
        boolean vertical = b.h > b.d;
        float length = vertical ? b.h : b.d, t = Math.max(0.1F, b.minSize() * 0.2F);
        int count = Math.max(2, Math.min(10, Math.round(length / Math.max(1F, t * 3))));
        for (int i = 0; i < count; i++) {
            float at = length * (i + 0.5F) / count;
            if (vertical) box(out, b.x + b.w * 0.2F, b.y + at, b.z + b.d * 0.35F, b.w * 0.6F, t, b.d * 0.3F);
            else box(out, b.x + b.w * 0.2F, b.y + b.h * 0.35F, b.z + at, b.w * 0.6F, b.h * 0.3F, t);
        }
    }

    private static void longBone(Bounds b, ObjectList<ModelBox> out) {
        float t = Math.max(0.1F, b.minSize() * 0.25F);
        float cx = b.x + b.w / 2, cy = b.y + b.h / 2, cz = b.z + b.d / 2;
        if (b.h >= b.w && b.h >= b.d) box(out, cx - t / 2, b.y, cz - t / 2, t, b.h, t);
        else if (b.d >= b.w) box(out, cx - t / 2, cy - t / 2, b.z, t, t, b.d);
        else box(out, b.x, cy - t / 2, cz - t / 2, b.w, t, t);
    }

    private StageGeometry() {}
}
