package com.otectus.alexsbutchery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.compat.MobSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Client cache of everything needed to draw a mob's model outside of the entity: a dummy entity of that type, its
 * renderer, and per snapshot the {@link Shape} that renderer would draw. The model instances are the live ones
 * Alex's Mobs renders with, so callers must restore any part visibility they change.
 */
public final class CarcassModels {
    private static final CompoundTag NO_DATA = new CompoundTag();
    /** {@code LivingEntityRenderer#scale}: where Alex's Mobs sizes a mob and swaps models (catfish sizes, segments). */
    private static final Method SCALE = ObfuscationReflectionHelper.findMethod(LivingEntityRenderer.class, "m_7546_",
            LivingEntity.class, PoseStack.class, float.class);

    /**
     * What one snapshot of a mob looks like: the model its renderer picks for it, that model's named parts, the
     * texture, the renderer's own size factor and the mob's dimensions. Valid until the next {@link Handle#shape}.
     */
    public record Shape(EntityModel<LivingEntity> model, Map<String, ModelParts.Part> parts, ResourceLocation texture,
                        float scale, float width, float height) {}

    public static final class Handle {
        private final EntityType<?> type;
        private final LivingEntity dummy;
        private final LivingEntityRenderer<LivingEntity, EntityModel<LivingEntity>> renderer;
        /** The dummy's own save data, restored before each snapshot so no look leaks from one carcass to the next. */
        private final CompoundTag defaults;
        private final Map<EntityModel<?>, Map<String, ModelParts.Part>> partsByModel = new IdentityHashMap<>();
        @Nullable private CompoundTag applied;
        @Nullable private Shape shape;

        Handle(EntityType<?> type, LivingEntity dummy, LivingEntityRenderer<LivingEntity, EntityModel<LivingEntity>> renderer) {
            this.type = type;
            this.dummy = dummy;
            this.renderer = renderer;
            this.defaults = MobSnapshot.capture(dummy);
        }

        public EntityType<?> type() {
            return type;
        }

        public LivingEntity dummy() {
            return dummy;
        }

        /** The shape for this snapshot; the last one is reused while carcasses of the same look are drawn in a row. */
        public Shape shape(@Nullable CompoundTag mobData) {
            CompoundTag data = mobData == null ? NO_DATA : mobData;
            if (shape != null && data.equals(applied)) return shape;
            read(defaults.copy().merge(data));
            dummy.refreshDimensions();
            float scale = rendererScale();
            EntityModel<LivingEntity> model = renderer.getModel();
            Map<String, ModelParts.Part> parts = partsByModel.computeIfAbsent(model, ModelParts::collect);
            ResourceLocation texture = StageTextures.appearance(dummy, renderer.getTextureLocation(dummy));
            shape = new Shape(model, parts, texture, scale, dummy.getBbWidth(), dummy.getBbHeight());
            applied = data.copy();
            return shape;
        }

        private void read(CompoundTag tag) {
            try {
                dummy.readAdditionalSaveData(tag);
            } catch (RuntimeException e) {
                // A partial snapshot is fine; whatever was read stays.
            }
        }

        /**
         * Runs the renderer's {@code scale} hook on a scratch pose so it can pick its model for the dummy, and keeps
         * the uniform size it applies (0.85 for the emu, 1.1 for a tusked elephant). Translations it makes for
         * riding or posing are ignored.
         */
        private float rendererScale() {
            PoseStack scratch = new PoseStack();
            try {
                SCALE.invoke(renderer, dummy, scratch, 1F);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return 1F;
            }
            float s = scratch.last().pose().getScale(new Vector3f()).x;
            return Float.isFinite(s) && s > 0.05F ? s : 1F;
        }

        /** Puts the model in its resting pose for the dummy (no animation), the way the entity renderer would. */
        public void pose(Shape shape) {
            EntityModel<LivingEntity> model = shape.model();
            if (model instanceof com.github.alexthe666.citadel.client.model.AdvancedEntityModel<?> advanced) advanced.resetToDefaultPose();
            model.young = false;
            model.riding = false;
            model.attackTime = 0F;
            if (dummy instanceof com.github.alexthe666.alexsmobs.entity.EntityLaviathan laviathan) {
                // ModelLaviathan ignores ageInTicks for interpolation and reads Minecraft's frame time.
                // An unticked LivingEntity starts with a random body yaw and previous yaw of zero.
                // Neutralise both endpoints on our private dummy, never on the living entity/model class.
                laviathan.yBodyRot = laviathan.yBodyRotO = laviathan.yHeadRot = laviathan.yHeadRotO = 0F;
                laviathan.setHeadHeight(0F);
                laviathan.prevHeadHeight = 0F;
                laviathan.swimProgress = laviathan.prevSwimProgress = 0F;
                laviathan.biteProgress = laviathan.prevBiteProgress = 0F;
            }
            if (dummy instanceof com.github.alexthe666.alexsmobs.entity.EntityTriops triops) {
                // Its constructor seeds both tail yaws from a random spawn yaw. Geometry exported on
                // another client must describe the same resting pose, independent of that random seed.
                triops.yBodyRot = triops.yBodyRotO = 0F;
                triops.tail1Yaw = triops.prevTail1Yaw = triops.tail2Yaw = triops.prevTail2Yaw = 0F;
            }
            model.prepareMobModel(dummy, 0F, 0F, 0F);
            model.setupAnim(dummy, 0F, 0F, 0F, 0F, 0F);
        }
    }

    private static final Map<ResourceLocation, Handle> CACHE = new HashMap<>();
    private static final Set<ResourceLocation> FAILED = new HashSet<>();
    private static Level cachedLevel;

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> clear());
    }

    public static void clear() {
        CACHE.clear();
        FAILED.clear();
        StageGeometry.clear();
        HangingPose.clear();
        LyingPose.clear();
        StageTextures.clear();
        cachedLevel = null;
    }

    @Nullable
    public static Handle get(ResourceLocation entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (cachedLevel != mc.level) {
            clear();
            cachedLevel = mc.level;
        }
        Handle handle = CACHE.get(entityId);
        if (handle != null || FAILED.contains(entityId)) return handle;
        handle = create(mc, entityId);
        if (handle == null) FAILED.add(entityId);
        else CACHE.put(entityId, handle);
        return handle;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Handle create(Minecraft mc, ResourceLocation entityId) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(entityId);
        if (type == null) {
            AlexsButchery.LOGGER.warn("Carcass renderer: unknown entity type {}", entityId);
            return null;
        }
        Entity created;
        try {
            created = type.create(mc.level);
        } catch (RuntimeException e) {
            AlexsButchery.LOGGER.warn("Carcass renderer: could not create a dummy {}", entityId, e);
            return null;
        }
        if (!(created instanceof LivingEntity dummy)) {
            AlexsButchery.LOGGER.warn("Carcass renderer: {} is not a living entity", entityId);
            return null;
        }
        EntityRenderer<? super LivingEntity> renderer = mc.getEntityRenderDispatcher().getRenderer(dummy);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) {
            AlexsButchery.LOGGER.warn("Carcass renderer: {} has no living entity renderer ({})", entityId, renderer);
            return null;
        }
        return new Handle(type, dummy, (LivingEntityRenderer<LivingEntity, EntityModel<LivingEntity>>) living);
    }

    private CarcassModels() {}
}
