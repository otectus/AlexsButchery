package com.otectus.alexsbutchery.smoketest;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.client.compat.carryon.mixin.CarryOnDataAccessor;
import com.otectus.alexsbutchery.client.compat.carryon.CarryOnCarcassRenderer;
import com.otectus.alexsbutchery.client.render.CarcassPresentation;
import com.otectus.alexsbutchery.client.render.CarcassVisualState;
import com.otectus.alexsbutchery.client.render.ModelBounds;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4f;
import tschipp.carryon.client.render.CarryRenderHelper;
import tschipp.carryon.common.carry.CarryOnData;
import tschipp.carryon.common.carry.CarryOnDataManager;
import tschipp.carryon.common.scripting.CarryOnScript;
import tschipp.carryon.common.scripting.Matchables.NBTCondition;
import tschipp.carryon.common.scripting.Matchables.NumberBoundCondition;
import tschipp.carryon.common.scripting.Matchables.OptionalVec3;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Runtime assertions and staging helpers for {@code -PcarryReview -PwithCarryOn}. */
final class CarryReviewChecks {
    static final boolean ENABLED = Boolean.getBoolean("alexsbutchery.smoketest.carryReview");
    private static CompoundTag savedLocalCarry;
    private static ItemStack savedMainHand = ItemStack.EMPTY;
    private static ItemStack savedOffHand = ItemStack.EMPTY;
    private static boolean savedHands;

    static void run(Map<String, Object> checks) {
        boolean loaded = ModList.get().isLoaded("carryon");
        checks.put("carry_mod_loaded", loaded);
        if (!loaded) return;
        String version = ModList.get().getModContainerById("carryon").map(c -> c.getModInfo().getVersion().toString()).orElse("missing");
        checks.put("carry_version", version);
        checks.put("carry_pinned_version", "2.1.2.7".equals(version));

        CarryOnData first = payload(false, 9, 270, "first-player");
        CarryOnData second = payload(true, 1, 45, "second-player");
        CompoundTag firstTile = tile(first), secondTile = tile(second);
        var firstVisual = CarcassVisualState.from(first.getBlock(), firstTile);
        var secondVisual = CarcassVisualState.from(second.getBlock(), secondTile);
        checks.put("carry_reads_tile_not_block", firstVisual != null && !firstVisual.appearance().getBoolean("Antlered")
                && secondVisual != null && secondVisual.appearance().getBoolean("Antlered"));
        checks.put("carry_two_player_payloads_isolated", "first-player".equals(firstTile.getCompound("ForgeData").getString("Owner"))
                && "second-player".equals(secondTile.getCompound("ForgeData").getString("Owner")));
        checks.put("carry_stage_preserved", firstVisual != null && !firstVisual.done().isEmpty()
                && first.getBlock().getValue(AbstractCarcassBlock.BLOCKSTATE_STAGED) == 9);
        checks.put("carry_timers_preserved", secondTile.getBoolean("Bleeding") && secondTile.getInt("BleedTicks") == 45);
        checks.put("carry_first_person_finite", finite(firstVisual, true));
        checks.put("carry_third_person_finite", finite(firstVisual, false));
        CompoundTag before = ((CarryOnDataAccessor) (Object) first).alexsbutchery$getRawCarryData().copy();
        finite(firstVisual, true);
        finite(firstVisual, false);
        checks.put("carry_render_preserves_storage_bytes", before.equals(((CarryOnDataAccessor) (Object) first).alexsbutchery$getRawCarryData()));

        // Exercise the adapter callbacks with two distinct Player instances, not a global Minecraft.player lookup.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            CompoundTag original = CarryOnDataManager.getCarryData(mc.player).getNbt().copy();
            RemotePlayer remote = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "CarryReviewRemote"));
            try {
                CarryOnDataManager.setCarryData(mc.player, first);
                CarryOnDataManager.setCarryData(remote, second);
                ModelBounds localMesh = new ModelBounds(), remoteMesh = new ModelBounds();
                boolean localDrawn = CarryOnCarcassRenderer.renderFirstPerson(mc.player, type -> localMesh, new PoseStack(), 15728880);
                boolean remoteDrawn = CarryOnCarcassRenderer.renderThirdPerson(remote, type -> remoteMesh, new PoseStack(), 15728880);
                checks.put("carry_actual_two_player_callbacks", localDrawn && remoteDrawn && localMesh.bounds().getSize() > 0
                        && remoteMesh.bounds().getSize() > 0);
                checks.put("carry_callbacks_use_each_player_tile", !tile(CarryOnDataManager.getCarryData(mc.player))
                        .equals(tile(CarryOnDataManager.getCarryData(remote))));

                CarryOnScript transformScript = script(false);
                first.setActiveScript(transformScript);
                CarryOnDataManager.setCarryData(mc.player, first);
                ModelBounds scriptedFirst = new ModelBounds();
                boolean scriptedFirstDrawn = CarryOnCarcassRenderer.renderFirstPerson(
                        mc.player, type -> scriptedFirst, new PoseStack(), 15728880);
                ModelBounds expectedFirst = new ModelBounds();
                PoseStack expectedFirstPose = new PoseStack();
                expectedFirstPose.translate(0, -0.52, -1.15);
                CarryRenderHelper.performScriptTransformation(expectedFirstPose, transformScript);
                CarcassPresentation.renderCarry(firstVisual, true, expectedFirstPose, type -> expectedFirst, 15728880, 0);
                ModelBounds wrongFirst = new ModelBounds();
                PoseStack wrongFirstPose = new PoseStack();
                CarryRenderHelper.performScriptTransformation(wrongFirstPose, transformScript);
                wrongFirstPose.translate(0, -0.52, -1.15);
                CarcassPresentation.renderCarry(firstVisual, true, wrongFirstPose, type -> wrongFirst, 15728880, 0);
                checks.put("carry_script_first_person_order", scriptedFirstDrawn
                        && same(scriptedFirst.bounds(), expectedFirst.bounds(), 1.0E-4)
                        && !same(scriptedFirst.bounds(), wrongFirst.bounds(), 1.0E-3));

                second.setActiveScript(transformScript);
                CarryOnDataManager.setCarryData(remote, second);
                PoseStack scriptedUpstream = new PoseStack();
                boolean thirdTransformApplied = CarryOnCarcassRenderer.applyThirdPersonTransform(
                        remote, 0, scriptedUpstream, second.getBlock().getBlock());
                CarryRenderHelper.performScriptTransformation(scriptedUpstream, transformScript);
                PoseStack scriptedCopy = topFrame(scriptedUpstream);
                ModelBounds scriptedThird = new ModelBounds();
                boolean scriptedThirdDrawn = CarryOnCarcassRenderer.renderThirdPerson(
                        remote, type -> scriptedThird, scriptedCopy, 15728880);
                ModelBounds expectedThird = new ModelBounds();
                CarcassPresentation.renderCarry(secondVisual, false, topFrame(scriptedUpstream),
                        type -> expectedThird, 15728880, 0);
                PoseStack plainUpstream = new PoseStack();
                boolean plainTransformApplied = CarryOnCarcassRenderer.applyThirdPersonTransform(
                        remote, 0, plainUpstream, second.getBlock().getBlock());
                ModelBounds plainThird = new ModelBounds();
                CarcassPresentation.renderCarry(secondVisual, false, topFrame(plainUpstream),
                        type -> plainThird, 15728880, 0);
                PoseStack wrongUpstream = new PoseStack();
                CarryRenderHelper.performScriptTransformation(wrongUpstream, transformScript);
                boolean wrongTransformApplied = CarryOnCarcassRenderer.applyThirdPersonTransform(
                        remote, 0, wrongUpstream, second.getBlock().getBlock());
                ModelBounds wrongThird = new ModelBounds();
                CarcassPresentation.renderCarry(secondVisual, false, topFrame(wrongUpstream),
                        type -> wrongThird, 15728880, 0);
                checks.put("carry_script_third_person_upstream_order", thirdTransformApplied && plainTransformApplied
                        && wrongTransformApplied
                        && scriptedThirdDrawn && same(scriptedThird.bounds(), expectedThird.bounds(), 1.0E-4)
                        && !same(scriptedThird.bounds(), plainThird.bounds(), 1.0E-3)
                        && !same(scriptedThird.bounds(), wrongThird.bounds(), 1.0E-3));

                CarryOnData replacement = payload(true, 1, 45, "replacement-script");
                replacement.setActiveScript(script(true));
                CarryOnDataManager.setCarryData(remote, replacement);
                checks.put("carry_script_render_replacement_opts_out",
                        !CarryOnCarcassRenderer.renderFirstPerson(remote, type -> new ModelBounds(), new PoseStack(), 15728880)
                                && !CarryOnCarcassRenderer.applyThirdPersonTransform(
                                        remote, 0, new PoseStack(), replacement.getBlock().getBlock())
                                && !CarryOnCarcassRenderer.renderThirdPerson(
                                        remote, type -> new ModelBounds(), new PoseStack(), 15728880));
                int playerForms = 0;
                boolean playerFormsPass = true;
                for (String skin : new String[]{"default", "slim"})
                    for (var arm : net.minecraft.world.entity.HumanoidArm.values())
                        for (var posture : new net.minecraft.world.entity.Pose[]{net.minecraft.world.entity.Pose.STANDING,
                                net.minecraft.world.entity.Pose.CROUCHING, net.minecraft.world.entity.Pose.SWIMMING,
                                net.minecraft.world.entity.Pose.FALL_FLYING}) {
                            RemotePlayer actor = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "CarryForm")) {
                                @Override public String getModelName() { return skin; }
                            };
                            actor.setMainArm(arm);
                            actor.setPose(posture);
                            actor.setShiftKeyDown(posture == net.minecraft.world.entity.Pose.CROUCHING);
                            actor.setSwimming(posture == net.minecraft.world.entity.Pose.SWIMMING);
                            if (posture == net.minecraft.world.entity.Pose.FALL_FLYING) actor.startFallFlying();
                            actor.yBodyRotO = 345; actor.yBodyRot = 5;
                            CarryOnDataManager.setCarryData(actor, payload(true, 0, 45, "player-form"));
                            ModelBounds cameraMesh = new ModelBounds(), bodyMesh = new ModelBounds();
                            PoseStack bodyPose = new PoseStack();
                            playerFormsPass &= CarryOnCarcassRenderer.renderFirstPerson(actor, type -> cameraMesh, new PoseStack(), 15728880)
                                    && CarryOnCarcassRenderer.applyThirdPersonTransform(actor, .5F, bodyPose, second.getBlock().getBlock())
                                    && CarryOnCarcassRenderer.renderThirdPerson(actor, type -> bodyMesh, topFrame(bodyPose), 15728880)
                                    && valid(cameraMesh.bounds()) && valid(bodyMesh.bounds());
                            playerForms++;
                        }
                checks.put("carry_player_form_cases", playerForms);
                checks.put("carry_slim_regular_both_arms_and_postures", playerFormsPass);
            } finally {
                CarryOnDataManager.setCarryData(mc.player, new CarryOnData(original));
            }
        } else checks.put("carry_actual_two_player_callbacks", false);
    }

    /** Put an actual state+tile payload on the local player so the mixin path can be photographed for several frames. */
    static void stageLocal(boolean antlered, int stage, int bleedTicks) {
        var player = Minecraft.getInstance().player;
        if (player == null) throw new IllegalStateException("No local player for carry review");
        if (savedLocalCarry == null) savedLocalCarry = CarryOnDataManager.getCarryData(player).getNbt().copy();
        if (!savedHands) {
            savedMainHand = player.getMainHandItem().copy();
            savedOffHand = player.getOffhandItem().copy();
            savedHands = true;
        }
        // Keep guide books and other staged smoke-test items out of both Carry On arm renders and screenshots.
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        CarryOnDataManager.setCarryData(player, payload(antlered, stage, bleedTicks, "local-review"));
    }

    static void restoreLocal() {
        var player = Minecraft.getInstance().player;
        if (player != null && savedLocalCarry != null) CarryOnDataManager.setCarryData(player, new CarryOnData(savedLocalCarry.copy()));
        if (player != null && savedHands) {
            player.setItemSlot(EquipmentSlot.MAINHAND, savedMainHand.copy());
            player.setItemSlot(EquipmentSlot.OFFHAND, savedOffHand.copy());
        }
        savedLocalCarry = null;
        savedMainHand = ItemStack.EMPTY;
        savedOffHand = ItemStack.EMPTY;
        savedHands = false;
    }

    private static CarryOnData payload(boolean antlered, int stage, int bleedTicks, String owner) {
        BlockState state = stage > 1
                ? ModBlocks.of(MobDefs.byId("moose")).drained().get().defaultBlockState()
                        .setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, stage)
                : ModBlocks.of(MobDefs.byId("moose")).carcass().get().defaultBlockState()
                        .setValue(AbstractCarcassBlock.BLOCKSTATE_FRESH, stage);
        BlockEntity entity = new CarcassBlockEntity(BlockPos.ZERO, state);
        CompoundTag saved = new CompoundTag();
        CompoundTag appearance = new CompoundTag();
        appearance.putBoolean("Antlered", antlered);
        saved.put(CarcassBlockEntity.MOB_DATA, appearance);
        if (stage <= 1) {
            saved.putBoolean("Bleeding", true);
            saved.putInt("BleedTicks", bleedTicks);
        }
        saved.put("ForgeData", new CompoundTag());
        saved.getCompound("ForgeData").putString("Owner", owner);
        entity.load(saved);
        CarryOnData data = new CarryOnData(new CompoundTag());
        data.setBlock(state, entity);
        return data;
    }

    private static CompoundTag tile(CarryOnData data) {
        CompoundTag raw = ((CarryOnDataAccessor) (Object) data).alexsbutchery$getRawCarryData();
        return raw.contains("tile", Tag.TAG_COMPOUND) ? raw.getCompound("tile").copy() : new CompoundTag();
    }

    private static boolean finite(CarcassVisualState visual, boolean firstPerson) {
        if (visual == null) return false;
        ModelBounds mesh = new ModelBounds();
        CarcassPresentation.renderCarry(visual, firstPerson, new PoseStack(), type -> mesh, 15728880, 0);
        var bounds = mesh.bounds();
        return Double.isFinite(bounds.minX + bounds.minY + bounds.minZ + bounds.maxX + bounds.maxY + bounds.maxZ)
                && bounds.getSize() > 0;
    }

    /** A real 2.1.2.7 script object: transform-only scripts compose; a name override deliberately replaces rendering. */
    private static CarryOnScript script(boolean replacement) {
        var blockKey = BuiltInRegistries.BLOCK.getResourceKey(Blocks.STONE).orElseThrow();
        var block = new CarryOnScript.ScriptObject.ScriptObjectBlock(Optional.of(blockKey),
                NumberBoundCondition.NONE, NumberBoundCondition.NONE, NBTCondition.NONE);
        var object = new CarryOnScript.ScriptObject(block,
                CarryOnScript.ScriptObject.ScriptObjectEntity.EMPTY);
        var render = new CarryOnScript.ScriptRender(replacement ? Optional.of(blockKey) : Optional.empty(),
                Optional.empty(), Optional.empty(), new OptionalVec3("x=0.19,y=-0.11,z=0.27"),
                new OptionalVec3("x=17,y=29,z=-13"), new OptionalVec3("x=0.73,y=1.11,z=0.89"),
                OptionalVec3.NONE, OptionalVec3.NONE, true, true);
        return new CarryOnScript(17, object, CarryOnScript.ScriptConditions.EMPTY, render,
                CarryOnScript.ScriptEffects.EMPTY);
    }

    /** Carry On copies only the composed top pose before invoking renderBakedModel. Match that pinned source path. */
    private static PoseStack topFrame(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.mulPoseMatrix(new Matrix4f(source.last().pose()));
        return copy;
    }

    private static boolean same(AABB a, AABB b, double epsilon) {
        return Math.abs(a.minX - b.minX) <= epsilon && Math.abs(a.minY - b.minY) <= epsilon
                && Math.abs(a.minZ - b.minZ) <= epsilon && Math.abs(a.maxX - b.maxX) <= epsilon
                && Math.abs(a.maxY - b.maxY) <= epsilon && Math.abs(a.maxZ - b.maxZ) <= epsilon;
    }

    private static boolean valid(AABB bounds) {
        return bounds.getSize() > 0 && Double.isFinite(bounds.minX + bounds.minY + bounds.minZ
                + bounds.maxX + bounds.maxY + bounds.maxZ);
    }

    private CarryReviewChecks() {}
}
