package com.otectus.alexsbutchery.smoketest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.block.HeadBlock;
import com.otectus.alexsbutchery.block.RugBlock;
import com.otectus.alexsbutchery.block.entity.CarcassBlockEntity;
import com.otectus.alexsbutchery.compat.ButcheryHooks;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Authors an old-save fixture ({@code -PsaveCompat=create}): a flat world holding carcasses in every kind of saved
 * state, plus {@code save-compat.json} naming each block's state and block entity data. Uses only API present since
 * 0.1.1, so the released build can write the world that {@link SaveCompatVerify} later opens with the update.
 */
@Mod.EventBusSubscriber(modid = SmokeTestAgent.MOD_ID, value = Dist.CLIENT)
public final class SaveCompatCreate {
    static final String MODE = System.getProperty("alexsbutchery.smoketest.saveCompat", "");
    static final boolean ENABLED = MODE.equals("create") || MODE.equals("verify");
    private static final String OUTPUT = System.getProperty(SmokeTestAgent.OUTPUT_PROPERTY);
    private static int titleTicks;
    private static int worldTicks;
    private static boolean done;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!MODE.equals("create") || OUTPUT == null || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            if (mc.screen instanceof TitleScreen && ++titleTicks == 40) {
                String name = "SaveCompat" + System.currentTimeMillis();
                LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
                        WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20261011L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
            return;
        }
        mc.options.pauseOnLostFocus = false;
        if (++worldTicks == 60) mc.getSingleplayerServer().execute(() -> stage(mc));
        if (worldTicks == 200) {
            done = true;
            AlexsButchery.LOGGER.info("SAVECOMPAT world written; quitting");
            mc.stop();
        }
    }

    private static void stage(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        BlockPos base = server.getPlayerList().getPlayers().get(0).blockPosition().offset(0, 0, 8);
        JsonArray entries = new JsonArray();
        int[] slot = {0};
        // One carcass per 9 blocks along x: lying, hanging, staged, snapshots, bleeding, acid, skeleton, slab, heads, rug.
        java.util.function.BiFunction<BlockState, CompoundTag, BlockPos> place = (state, data) -> {
            BlockPos pos = base.offset(9 * slot[0]++, 0, 0);
            level.setBlockAndUpdate(pos, state);
            if (data != null && level.getBlockEntity(pos) instanceof CarcassBlockEntity be) be.setMobData(data);
            return pos;
        };
        CompoundTag marker = new CompoundTag();
        marker.putBoolean("SaveCompatMarker", true);
        place.apply(carcass("laviathan", 0, Direction.EAST), marker);
        CompoundTag tusked = marker.copy(); tusked.putBoolean("Tusked", true);
        place.apply(drained("elephant", 7, Direction.SOUTH), tusked);
        place.apply(drained("void_worm", 7, Direction.WEST), marker);
        place.apply(carcass("centipede", 6, Direction.NORTH), marker);
        place.apply(carcass("farseer", 0, Direction.NORTH), marker);
        CompoundTag catfish = marker.copy(); catfish.putInt("CatfishSize", 2);
        place.apply(carcass("catfish", 0, Direction.EAST), catfish);
        place.apply(skeleton("bison", 0, Direction.WEST), marker);
        // On a slab: the update lowers it onto the slab's top without moving the block.
        level.setBlockAndUpdate(base.offset(9 * slot[0], -1, 0), Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
        place.apply(carcass("rhinoceros", 0, Direction.NORTH), marker);
        // Hanging, bleeding into nothing, and hanging with acid working through it.
        BlockPos bleeding = hangUnder(level, base.offset(9 * slot[0], 3, 0), carcass("kangaroo", 1, Direction.SOUTH));
        slot[0]++;
        ((CarcassBlockEntity) level.getBlockEntity(bleeding)).setMobData(marker);
        ((CarcassBlockEntity) level.getBlockEntity(bleeding)).startBleeding();
        BlockPos acid = hangUnder(level, base.offset(9 * slot[0], 3, 0), drained("gazelle", 1, Direction.NORTH));
        slot[0]++;
        ((CarcassBlockEntity) level.getBlockEntity(acid)).setMobData(marker);
        ((CarcassBlockEntity) level.getBlockEntity(acid)).startDissolving(1_000_000);
        BlockPos head = base.offset(9 * slot[0]++, 0, 0);
        level.setBlockAndUpdate(head, ModBlocks.of(MobDefs.byId("tiger")).head().get().defaultBlockState().setValue(HeadBlock.FACING, Direction.EAST));
        ((CarcassBlockEntity) level.getBlockEntity(head)).setMobData(marker);
        BlockPos rug = base.offset(9 * slot[0]++, 0, 0);
        level.setBlockAndUpdate(rug, ModBlocks.of(MobDefs.byId("bison")).rug().get().defaultBlockState().setValue(RugBlock.FACING, Direction.WEST));
        ((CarcassBlockEntity) level.getBlockEntity(rug)).setMobData(marker);

        for (int i = 0; i < slot[0]; i++) for (int dy : new int[]{0, 2}) {
            BlockPos pos = base.offset(9 * i, dy, 0);
            if (!(level.getBlockEntity(pos) instanceof CarcassBlockEntity be)) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("x", pos.getX());
            entry.addProperty("y", pos.getY());
            entry.addProperty("z", pos.getZ());
            entry.addProperty("state", NbtUtils.writeBlockState(level.getBlockState(pos)).toString());
            entry.addProperty("block_entity", be.saveWithoutMetadata().toString());
            entries.add(entry);
        }
        JsonObject manifest = new JsonObject();
        manifest.addProperty("world", server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString());
        manifest.addProperty("mod_version", net.minecraftforge.fml.ModList.get().getModContainerById(AlexsButchery.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?"));
        manifest.addProperty("x", base.getX());
        manifest.addProperty("y", base.getY());
        manifest.addProperty("z", base.getZ());
        manifest.add("entries", entries);
        try {
            Files.createDirectories(Path.of(OUTPUT));
            Files.writeString(Path.of(OUTPUT, "save-compat.json"), new GsonBuilder().setPrettyPrinting().create().toJson(manifest), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        AlexsButchery.LOGGER.info("SAVECOMPAT staged {} block entities in {}", entries.size(), manifest.get("world"));
    }

    private static BlockPos hangUnder(ServerLevel level, BlockPos hook, BlockState carcass) {
        level.setBlockAndUpdate(hook, ButcheryHooks.hook().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        level.setBlockAndUpdate(hook.below(), carcass);
        return hook.below();
    }

    private static BlockState carcass(String id, int stage, Direction facing) {
        var block = (AbstractCarcassBlock) ModBlocks.of(MobDefs.byId(id)).carcass().get();
        return block.defaultBlockState().setValue(block.stateProperty(), stage).setValue(AbstractCarcassBlock.FACING, facing);
    }

    private static BlockState drained(String id, int stage, Direction facing) {
        var block = (AbstractCarcassBlock) ModBlocks.of(MobDefs.byId(id)).drained().get();
        return block.defaultBlockState().setValue(block.stateProperty(), stage).setValue(AbstractCarcassBlock.FACING, facing);
    }

    private static BlockState skeleton(String id, int stage, Direction facing) {
        var block = (AbstractCarcassBlock) ModBlocks.of(MobDefs.byId(id)).skeleton().get();
        return block.defaultBlockState().setValue(block.stateProperty(), stage).setValue(AbstractCarcassBlock.FACING, facing);
    }

    private SaveCompatCreate() {}
}
