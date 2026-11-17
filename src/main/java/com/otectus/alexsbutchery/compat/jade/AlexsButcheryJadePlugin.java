package com.otectus.alexsbutchery.compat.jade;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.CarcassBlock;
import com.otectus.alexsbutchery.block.DrainedCarcassBlock;
import com.otectus.alexsbutchery.block.SkeletonBlock;
import com.otectus.alexsbutchery.block.SkinRackBlock;
import com.otectus.alexsbutchery.block.StagedCarcassBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.block.entity.SkinRackBlockEntity;
import com.otectus.alexsbutchery.butcher.Acid;
import com.otectus.alexsbutchery.butcher.Bleeding;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.compat.CompatState;
import com.otectus.alexsbutchery.compat.CompatView;
import com.otectus.alexsbutchery.compat.NativeButchery;
import com.otectus.alexsbutchery.compat.ProviderNames;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.SkinStep;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;

/**
 * Jade lines for carcasses (what the next step is and which tool does it, bleeding and acid progress, and which cooking
 * addon supplies the meat still to come) and for the skin rack while it holds one of our skins. Jade finds the plugin by its annotation, so nothing loads it without
 * Jade. The server providers send the live counters; the client providers only format them and the block state.
 */
@WailaPlugin
public class AlexsButcheryJadePlugin implements IWailaPlugin {
    static final ResourceLocation CARCASS = AlexsButchery.id("carcass");
    static final ResourceLocation SKIN_RACK = AlexsButchery.id("skin_rack");
    /** Butchery's own carcasses and corpses, configurable apart from this mod's ({@link NativeButchery}). */
    static final ResourceLocation BUTCHERY_CARCASS = AlexsButchery.id("butchery_carcass");
    /** Butchery's own skin rack ({@link NativeButchery#rackLines}). */
    static final ResourceLocation BUTCHERY_SKIN_RACK = AlexsButchery.id("butchery_skin_rack");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(CarcassProvider.INSTANCE, CarcassBlockEntity.class);
        registration.registerBlockDataProvider(RackProvider.INSTANCE, SkinRackBlockEntity.class);
        // Butchery's block entities have no common class: every block entity, filtered by the native table at once.
        registration.registerBlockDataProvider(NativeProvider.INSTANCE, BlockEntity.class);
        registration.registerBlockDataProvider(NativeRackProvider.INSTANCE, BlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CarcassProvider.INSTANCE, AbstractCarcassBlock.class);
        registration.registerBlockComponent(RackProvider.INSTANCE, SkinRackBlock.class);
        registration.registerBlockComponent(NativeProvider.INSTANCE, Block.class);
        registration.registerBlockComponent(NativeRackProvider.INSTANCE, Block.class);
    }

    static MutableComponent line(String key, Object... args) {
        return Component.translatable("jade.alexsbutchery." + key, args);
    }

    static int percent(int done, int total) {
        return total <= 0 ? 100 : Math.min(100, Math.round(done * 100F / total));
    }

    public enum CarcassProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof CarcassBlockEntity carcass)) return;
            if (accessor.getBlockState().getBlock() instanceof CarcassBlock) {
                data.putBoolean("CanBleedHere", Bleeding.canBleedAt(accessor.getLevel(), accessor.getPosition(),
                        accessor.getBlockState(), carcass.def()));
            }
            if (carcass.isDissolving()) data.putInt("Acid", percent(Acid.DISSOLVE_TICKS - carcass.acidTicks(), Acid.DISSOLVE_TICKS));
            else if (carcass.isBleeding()) data.putInt("Bleed", percent(carcass.bleedTicks(), carcass.def().blood().totalTicks()));
            CompoundTag providers = providers(accessor, carcass);
            if (!providers.isEmpty()) data.put("Providers", providers);
        }

        /**
         * Which cooking addon supplies each product group this carcass still has to give (group to provider name), as
         * the server's rules and the carcass's saved variant decide, and only where the loaded stage table still yields
         * that provider's item for that group (a pack's own table that yields something else makes no claim); groups
         * yielding this mod's own items are left out.
         */
        private static CompoundTag providers(BlockAccessor accessor, CarcassBlockEntity carcass) {
            CompoundTag out = new CompoundTag();
            BlockState state = accessor.getBlockState();
            if (!(state.getBlock() instanceof AbstractCarcassBlock block) || block instanceof SkeletonBlock) return out;
            CompatState rules = CompatState.get(accessor.getLevel().getServer());
            if (rules == null) return out;
            MobDef def = block.def();
            boolean fresh = !(block instanceof DrainedCarcassBlock);
            java.util.Set<String> done = new java.util.HashSet<>();
            for (Stages.Action action : Stages.done(def, fresh, block.stage(state))) done.add(action.table(def));
            for (String group : def.groups()) {
                for (String stage : def.stagesOf(group)) {
                    if (done.contains(stage)) continue;
                    CompatView.StageView loaded = rules.view().stage(def.id(), stage);
                    for (CompatState.Choice choice : rules.resolution(def.entity(), group, stage).chain()) {
                        if (!(choice instanceof CompatState.Provided provided)) break;
                        if (provided.select(carcass.mobData()) == null) continue;
                        boolean yielded = loaded != null && loaded.outputs().stream()
                                .anyMatch(o -> group.equals(o.group()) && provided.provider().equals(o.provider()));
                        if (yielded) out.putString(group, ProviderNames.name(provided.provider()));
                        break;
                    }
                    break;
                }
            }
            return out;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();
            if (!(state.getBlock() instanceof AbstractCarcassBlock block) || block instanceof SkeletonBlock) return;
            MobDef def = block.def();
            CompoundTag data = accessor.getServerData();
            if (data.contains("Acid", Tag.TAG_INT)) {
                tooltip.add(line("dissolving", data.getInt("Acid")).withStyle(ChatFormatting.GREEN));
                return;
            }
            boolean canBleedHere = !(block instanceof CarcassBlock)
                    || (data.contains("CanBleedHere", Tag.TAG_BYTE) ? data.getBoolean("CanBleedHere")
                    : Bleeding.canBleedAt(accessor.getLevel(), accessor.getPosition(), state, def));
            if (data.contains("Bleed", Tag.TAG_INT) && canBleedHere) {
                tooltip.add(line("bleeding", data.getInt("Bleed")).withStyle(ChatFormatting.DARK_RED));
                return;
            }
            int stage = block.stage(state);
            if (block instanceof StagedCarcassBlock || block instanceof DrainedCarcassBlock) {
                boolean fresh = block instanceof StagedCarcassBlock;
                Stages.Step step = Stages.next(def, fresh, stage);
                if (step != null) {
                    tooltip.add(line("next", tool(step.action()), action(def, step.action())));
                    List<Stages.Action> actions = Stages.actions(def);
                    int done = Stages.done(def, fresh, stage).size();
                    tooltip.add(line("progress", done, actions.size()).withStyle(ChatFormatting.GRAY));
                }
            } else if (block instanceof CarcassBlock) {
                if (canBleedHere)
                    tooltip.add(line("next", tool(null), line("action.bleed")));
                else tooltip.add(line("hang").withStyle(ChatFormatting.GRAY));
            }
            if (data.contains("Providers", Tag.TAG_COMPOUND)) {
                CompoundTag providers = data.getCompound("Providers");
                for (String group : providers.getAllKeys())
                    tooltip.add(line("yields", line("group." + group), providers.getString(group)).withStyle(ChatFormatting.GOLD));
            }
            ItemStack held = accessor.getPlayer().getMainHandItem();
            if (ButcheryHooks.isAcidBottle(held) && Acid.canDissolve(block, state)) {
                tooltip.add(line("acid").withStyle(ChatFormatting.GREEN));
            }
        }

        /** The tool an action needs; null means the cleaver's bleeding cut. */
        private static Component tool(Stages.Action action) {
            boolean knife = action != null && action.needsKnife();
            return line(knife ? "tool.skinning_knife" : "tool.cleaver").withStyle(ChatFormatting.GOLD);
        }

        private static Component action(MobDef def, Stages.Action action) {
            return switch (action) {
                case HEAD -> line("action.head");
                case SKIN -> line(def.skin() == SkinStep.PLUCK ? "action.pluck" : "action.skin");
                case CUT_3 -> line("action.last_cut");
                default -> line("action.cut");
            };
        }

        @Override
        public ResourceLocation getUid() {
            return CARCASS;
        }
    }

    public enum RackProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof SkinRackBlockEntity rack)) return;
            data.putInt("Stage", rack.stage());
            if (!rack.skin().isEmpty()) data.putString("Skin", rack.skin().getDescriptionId());
            if (rack.stage() == SkinRackBlock.STAGE_CURING) {
                data.putInt("Cure", percent(SkinRackBlock.CURE_TICKS - rack.cureTicks(), SkinRackBlock.CURE_TICKS));
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("Stage", Tag.TAG_INT)) return;
            if (data.contains("Skin", Tag.TAG_STRING)) {
                tooltip.add(line("rack.skin", Component.translatable(data.getString("Skin"))).withStyle(ChatFormatting.GOLD));
            }
            tooltip.add(switch (data.getInt("Stage")) {
                case SkinRackBlock.STAGE_HUNG -> line("rack.salt").withStyle(ChatFormatting.GRAY);
                case SkinRackBlock.STAGE_SALTED -> line("rack.sponge").withStyle(ChatFormatting.AQUA);
                case SkinRackBlock.STAGE_CURING -> line("rack.curing", data.getInt("Cure")).withStyle(ChatFormatting.AQUA);
                default -> line("rack.done").withStyle(ChatFormatting.GREEN);
            });
        }

        @Override
        public ResourceLocation getUid() {
            return SKIN_RACK;
        }
    }

    /**
     * Butchery's own carcasses and corpses. The server sends the persistent flags Butchery keeps for the block (it never
     * syncs them to clients); blocks without a block entity are judged by their state alone. Anything the table does not
     * describe, or a missing server answer, shows nothing.
     */
    public enum NativeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            CompoundTag flags = NativeButchery.serverData(accessor.getBlockEntity());
            if (flags != null) data.put(NativeButchery.DATA, flags);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();
            if (NativeButchery.of(state.getBlock()) == null) return;
            CompoundTag data = accessor.getServerData();
            CompoundTag flags = data.contains(NativeButchery.DATA, Tag.TAG_COMPOUND) ? data.getCompound(NativeButchery.DATA)
                    : state.getBlock() instanceof EntityBlock ? null : new CompoundTag();
            for (Component line : NativeButchery.lines(state, flags)) tooltip.add(line);
        }

        @Override
        public ResourceLocation getUid() {
            return BUTCHERY_CARCASS;
        }
    }

    /** Butchery's own skin rack: the skin on it and what it needs next, from its state and the server's curing data. */
    public enum NativeRackProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            CompoundTag rack = NativeButchery.rackServerData(accessor.getBlockEntity());
            if (rack != null) data.put(NativeButchery.RACK_DATA, rack);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();
            if (!NativeButchery.isRack(state)) return;
            CompoundTag data = accessor.getServerData();
            CompoundTag rack = data.contains(NativeButchery.RACK_DATA, Tag.TAG_COMPOUND) ? data.getCompound(NativeButchery.RACK_DATA) : null;
            for (Component line : NativeButchery.rackLines(state, rack)) tooltip.add(line);
        }

        @Override
        public ResourceLocation getUid() {
            return BUTCHERY_SKIN_RACK;
        }
    }
}
