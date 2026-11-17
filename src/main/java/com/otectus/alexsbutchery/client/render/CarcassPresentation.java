package com.otectus.alexsbutchery.client.render;

import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.otectus.alexsbutchery.client.pose.PresentationProfile;
import com.otectus.alexsbutchery.config.ClientConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns display transforms only. Scene anatomy remains in blocks; generated item JSON has identity transforms.
 * The vanilla ItemRenderer translates -0.5 on all axes before BEWLR; renderItem cancels it before anchoring.
 * Carry callers supply their player's grip frame directly, without a selection-height or generic item fit.
 */
public final class CarcassPresentation {
    private record Key(PoseKey pose, CarcassVisualState.Form form, String context, boolean left) {}
    public record Metrics(AABB visible, Vec3 grip, double contact) {
        public double span() { return Math.max(visible.getXsize(), Math.max(visible.getYsize(), visible.getZsize())); }
    }
    private static final Map<Key, Metrics> CACHE = new LinkedHashMap<>(128, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Metrics> entry) { return size() > 2048; }
    };

    static void clear() { CACHE.clear(); }
    public static int cachedMetrics() { return CACHE.size(); }

    /** Conservative local envelope including a resource pack's ground scale and offset, before item bob/spin. */
    public static AABB groundEnvelope(CarcassVisualState visual) {
        var subject = visual.subject();
        if (subject == null) return new AABB(-.5, 0, -.5, .5, 1, .5);
        var tuning = tuning(subject, "ground");
        var metric = metrics(visual, subject, "ground", false);
        var bounds = metric.visible();
        double scale = tuning.scale();
        double cap = tuning.maxSpan() > 0 ? tuning.maxSpan() : ClientConfig.GROUND_ITEM_MAX_SPAN.get();
        if (cap > 0) scale = Math.min(scale, cap / Math.max(.001, metric.span()));
        Vec3 centre = bounds.getCenter(), offset = tuning.offset();
        return new AABB((bounds.minX - centre.x) * scale + offset.x, (bounds.minY - metric.contact()) * scale + offset.y,
                (bounds.minZ - centre.z) * scale + offset.z, (bounds.maxX - centre.x) * scale + offset.x,
                (bounds.maxY - metric.contact()) * scale + offset.y, (bounds.maxZ - centre.z) * scale + offset.z);
    }

    public static void renderItem(CarcassVisualState visual, ItemDisplayContext context, PoseStack pose,
                                  MultiBufferSource buffers, int light, int overlay) {
        context = vanillaContext(context);
        String display = switch (context) {
            case GUI -> "gui";
            case GROUND -> "ground";
            case FIXED -> "fixed";
            case HEAD -> "head";
            case NONE -> "none";
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> "first_person";
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> "third_person";
            default -> "none";
        };
        boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        pose.pushPose();
        try {
            pose.translate(.5, .5, .5);
            // ItemEntityRenderer adds 0.25 * JSON ground scale before the normal 0..0.2 bob.
            // Identity JSON scale makes that 0.25: cancel its icon-centre lift, retaining the vanilla bob/spin.
            if (context == ItemDisplayContext.GROUND) pose.translate(0, -.25, 0);
            render(visual, display, left, pose, buffers, light, overlay);
        } finally { pose.popPose(); }
    }

    /** Forge lets other mods extend this enum; their declared fallback owns the anatomical presentation. */
    private static ItemDisplayContext vanillaContext(ItemDisplayContext context) {
        // A malformed or cyclic extension is a neutral local view, never an implicit carry or inventory fit.
        for (int remaining = 32; context != null && context.isModded() && remaining > 0; remaining--)
            context = context.fallback();
        return context == null || context.isModded() ? ItemDisplayContext.NONE : context;
    }

    public static void renderCarry(CarcassVisualState visual, boolean firstPerson, PoseStack pose,
                                   MultiBufferSource buffers, int light, int overlay) {
        render(visual, firstPerson ? "carry_first_person" : "carry_third_person", false, pose, buffers, light, overlay);
    }

    /** Raw neutral lying geometry, also used by measurements and the independent smoke-test vertex capture. */
    public static void renderNeutral(CarcassVisualState visual, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var subject = visual.subject();
        if (subject != null) draw(visual, subject, pose, buffers, light, overlay);
    }

    private static void draw(CarcassVisualState visual, CarcassScene.Subject s, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        switch (visual.form()) {
            case BODY -> CarcassScene.renderLying(s, Direction.SOUTH, pose, buffers, light);
            case HEAD, MOUNT -> CarcassScene.renderHead(s.handle(), s.def(), s.profile(), s.mobData(),
                    visual.form() == CarcassVisualState.Form.MOUNT, Direction.SOUTH, pose, buffers, light, overlay);
            case RUG -> {
                net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                        visual.state().setValue(com.otectus.alexsbutchery.block.RugBlock.FACING, Direction.SOUTH), pose, buffers, light, overlay);
                CarcassScene.renderRugHead(s.handle(), s.profile(), s.mobData(), Direction.SOUTH, pose, buffers, light);
            }
        }
    }

    public static Metrics metrics(CarcassVisualState visual, String context, boolean left) {
        var subject = visual.subject();
        if (subject == null) return new Metrics(new AABB(0, 0, 0, 1, 1, 1), new Vec3(.5, .5, .5), 0);
        return metrics(visual, subject, context, left);
    }

    private static Metrics metrics(CarcassVisualState visual, CarcassScene.Subject s, String context, boolean left) {
        var key = new Key(PoseKey.of(s, s.handle().shape(s.mobData())), visual.form(), context, left);
        Metrics cached = CACHE.get(key);
        if (cached != null) return cached;
        Capture capture = new Capture();
        PoseStack pose = new PoseStack();
        rotate(context, left, tuning(s, context), pose);
        draw(visual, s, pose, capture, 15728880, 0);
        AABB visible = capture.mesh.bounds();
        if (!finite(visible) || visible.getSize() <= 0) throw new IllegalStateException("Empty/nonfinite presentation: " + s.def().id());
        // Body cubes, not antlers or the selection envelope, determine the grip. Multipart bodies average their torsos.
        Vec3 grip = capture.grips == 0 ? visible.getCenter() : capture.grip.scale(1D / capture.grips);
        double contact = 0;
        if (context.equals("ground") || context.equals("none")) {
            contact = LyingPose.measure(buffers -> {
                PoseStack supportPose = new PoseStack();
                rotate(context, left, tuning(s, context), supportPose);
                if (visual.form() == CarcassVisualState.Form.BODY)
                    CarcassScene.renderLyingSupport(s, Direction.SOUTH, supportPose, buffers, 15728880);
                else draw(visual, s, supportPose, buffers, 15728880, 0);
            }).floor();
        }
        Metrics result = new Metrics(visible, grip, contact);
        CACHE.put(key, result);
        return result;
    }

    private static void render(CarcassVisualState visual, String context, boolean left, PoseStack pose,
                               MultiBufferSource buffers, int light, int overlay) {
        var subject = visual.subject();
        if (subject == null) return;
        var tuning = tuning(subject, context);
        Metrics metrics = metrics(visual, subject, context, left);
        AABB bounds = metrics.visible();
        boolean bounded = context.equals("gui") || context.equals("fixed") || context.equals("head");
        double scale;
        Vec3 anchor;
        if (bounded) {
            double frame = context.equals("head") ? .9 : .88;
            // Fit emitted vertices after rotation, not the enclosing living-mob hitbox.
            double footprint = context.equals("gui") ? Math.max(bounds.getXsize(), bounds.getYsize()) : metrics.span();
            scale = frame / Math.max(.001, footprint)
                    * Math.min(1, tuning.scale());
            if (tuning.maxSpan() > 0) scale = Math.min(scale, tuning.maxSpan() / Math.max(.001, metrics.span()));
            anchor = bounds.getCenter();
        } else {
            scale = switch (context) {
                case "first_person", "third_person", "carry_first_person", "carry_third_person" -> .65;
                default -> 1;
            };
            scale *= tuning.scale();
            double cap = tuning.maxSpan() > 0 ? tuning.maxSpan() : switch (context) {
                case "first_person" -> 2.4;
                case "third_person" -> 4;
                case "carry_first_person" -> 2.8;
                case "carry_third_person" -> 5;
                case "ground" -> ClientConfig.GROUND_ITEM_MAX_SPAN.get();
                default -> 0;
            };
            if (cap > 0) scale = Math.min(scale, cap / Math.max(.001, metrics.span()));
            // Measure support after presentation rotation too. Decorative fins/ignored ears may dip below it.
            anchor = context.equals("ground") || context.equals("none")
                    ? new Vec3(bounds.getCenter().x, metrics.contact(), bounds.getCenter().z) : metrics.grip();
        }
        pose.pushPose();
        try {
            Vec3 offset = tuning.offset();
            // Bounded displays always remain contained, even with a resource-pack offset.
            if (!bounded) pose.translate(left ? -offset.x : offset.x, offset.y, offset.z);
            pose.scale((float) scale, (float) scale, (float) scale);
            pose.translate(-anchor.x, -anchor.y, -anchor.z);
            rotate(context, left, tuning, pose);
            draw(visual, subject, pose, buffers, light, overlay);
        } finally { pose.popPose(); }
    }

    private static PresentationProfile tuning(CarcassScene.Subject subject, String context) {
        return subject.profile().presentation.getOrDefault(context, PresentationProfile.DEFAULT);
    }

    private static void rotate(String context, boolean left, PresentationProfile tuning, PoseStack pose) {
        switch (context) {
            case "gui" -> { pose.mulPose(Axis.XP.rotationDegrees(30)); pose.mulPose(Axis.YP.rotationDegrees(225)); }
            case "first_person" -> pose.mulPose(Axis.YP.rotationDegrees(left ? -35 : 35));
            case "third_person" -> { pose.mulPose(Axis.XP.rotationDegrees(15)); pose.mulPose(Axis.YP.rotationDegrees(left ? -90 : 90)); }
            case "carry_first_person", "carry_third_person" -> pose.mulPose(Axis.YP.rotationDegrees(90));
            case "fixed", "head", "none", "ground" -> { }
            default -> throw new IllegalArgumentException("Unknown presentation " + context);
        }
        Vec3 rotation = tuning.rotation();
        pose.mulPose(Axis.XP.rotationDegrees((float) rotation.x));
        pose.mulPose(Axis.YP.rotationDegrees((float) (left ? -rotation.y : rotation.y)));
        pose.mulPose(Axis.ZP.rotationDegrees((float) (left ? -rotation.z : rotation.z)));
    }

    private static final class Capture implements MultiBufferSource {
        final ModelBounds mesh = new ModelBounds();
        Vec3 grip = Vec3.ZERO;
        int grips;
        @Override public VertexConsumer getBuffer(RenderType type) { return mesh; }
    }

    /** Invoked only during a cache miss, with the exact posed model matrix and current part visibility. */
    static void captureGrip(CarcassModels.Shape shape, PoseStack pose, MultiBufferSource buffers) {
        if (!(buffers instanceof Capture capture)) return;
        AdvancedModelBox chosen = null;
        double largest = 0;
        for (String name : new String[]{"body", "chest", "torso", "mantle", "shell", "abdomen", "body1"}) {
            var part = shape.parts().get(name);
            if (part != null && part.raw() instanceof AdvancedModelBox box && visible(box) && !box.cubeList.isEmpty()) { chosen = box; break; }
        }
        if (chosen == null) for (var part : shape.parts().values()) {
            if (!(part.raw() instanceof AdvancedModelBox box) || !visible(box)) continue;
            for (var cube : box.cubeList) {
                double volume = Math.abs((cube.posX2 - cube.posX1) * (cube.posY2 - cube.posY1) * (cube.posZ2 - cube.posZ1));
                if (volume > largest) { largest = volume; chosen = box; }
            }
        }
        if (chosen == null) return;
        Matrix4f transform = new Matrix4f(pose.last().pose()).mul(matrix(chosen).last().pose());
        ModelBounds body = new ModelBounds();
        for (var cube : chosen.cubeList) for (int i = 0; i < 8; i++) {
            Vector3f v = transform.transformPosition(new Vector3f((i % 2 == 0 ? cube.posX1 : cube.posX2) / 16F,
                    ((i & 2) == 0 ? cube.posY1 : cube.posY2) / 16F, ((i & 4) == 0 ? cube.posZ1 : cube.posZ2) / 16F));
            body.vertex(v.x, v.y, v.z);
        }
        capture.grip = capture.grip.add(body.bounds().getCenter());
        capture.grips++;
    }

    private static boolean visible(AdvancedModelBox box) {
        for (var part = box; part != null; part = part.getParent()) if (!part.showModel) return false;
        return true;
    }

    private static PoseStack matrix(AdvancedModelBox box) {
        PoseStack pose = box.getParent() == null ? new PoseStack() : matrix(box.getParent());
        var parent = box.getParent();
        if (parent != null && !parent.scaleChildren) pose.scale(1 / parent.scaleX, 1 / parent.scaleY, 1 / parent.scaleZ);
        box.translateAndRotate(pose);
        return pose;
    }

    private static boolean finite(AABB b) {
        return Double.isFinite(b.minX) && Double.isFinite(b.minY) && Double.isFinite(b.minZ)
                && Double.isFinite(b.maxX) && Double.isFinite(b.maxY) && Double.isFinite(b.maxZ);
    }

    private CarcassPresentation() {}
}
