package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.block.AbstractCarcassBlock;
import com.otectus.alexsbutchery.compat.NativeButchery;
import com.otectus.alexsbutchery.compat.jade.AlexsButcheryJadePlugin;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs this mod's real Jade providers the way Jade does, without a client: the server provider fills the server data
 * for a block accessor, then the client provider writes its lines into a tooltip, both through stand-ins for Jade's
 * accessor, tooltip and config. Checks the lines for Butchery's own cow, chicken, spider, bee, sniffer (lying and hung)
 * and skin rack at each kind of point, this mod's own carcass at the matching point, a player corpse, missing server
 * data and a state the table does not know. Only loaded with Jade installed ({@link JadeTooltipTests}).
 */
final class JadeChecks {

    static void run(GameTestHelper h) {
        var carcass = AlexsButcheryJadePlugin.NativeProvider.INSTANCE;
        BlockPos at = new BlockPos(2, 2, 2);
        h.setBlock(at.above(), TestSupport.butcheryBlock("hook").defaultBlockState());

        // A cow: hanging, bleeding, on the ground, drained and part cut.
        expect(h, at, "cow_carcass", 1, Map.of(), carcass, List.of("next:tool.cleaver,action.bleed"));
        expect(h, at, "cow_carcass", 1, Map.of("isBleeding", 1.0), carcass, List.of("bleeding_wait"));
        expect(h, at, "cow_carcass", 0, Map.of(), carcass, List.of("hang"));
        expect(h, at, "drained_cow_carcass", 6, Map.of(), carcass, List.of("next:tool.skinning_knife,action.skin", "progress:1,5"));
        // A chicken being plucked, a spider and a bee (no block entity: judged by its state alone).
        expect(h, at, "drained_chicken_carcass", 5, Map.of("pluckedFeathers", 2.0), carcass, List.of("next:tool.skinning_knife,action.pluck", "progress:3,8"));
        expect(h, at, "spider_carcass", 0, Map.of(), carcass, List.of("next:tool.cleaver,action.head", "progress:0,4"));
        expect(h, at, "bee_carcass", 1, Map.of(), carcass, List.of("next:tool.cleaver,action.cut", "progress:1,4"));
        // The sniffer hangs as the same block, at butcherStage 1 under a hook: only then does a cleaver bleed it, and a
        // cleaver on the ground (Butchery 5.2 marks it bleeding there) changes nothing. Then the warden's boss tool.
        expect(h, at, "sniffer_carcass_block", -1, Map.of(), carcass, List.of("hang"));
        expect(h, at, "sniffer_carcass_block", -1, Map.of("isBleeding", 1.0), carcass, List.of("hang"));
        expect(h, at, "sniffer_carcass_block", -1, Map.of("butcherStage", 1.0), carcass, List.of("next:tool.cleaver,action.bleed"));
        expect(h, at, "sniffer_carcass_block", -1, Map.of("butcherStage", 1.0, "isBleeding", 1.0), carcass, List.of("bleeding_wait"));
        expect(h, at, "drained_sniffer_carcass_block", -1, Map.of("butcherStage", 5.0, "isDrained", 1.0), carcass,
                List.of("next:tool.cleaver,action.head", "progress:0,5"));
        expect(h, at, "warden_carcass", -1, Map.of("butcherStage", 2.0), carcass, List.of("next:tool.boss_tool,action.cut", "progress:2,5"));
        // Player corpses are left to Butchery's own Jade line.
        expect(h, at, "playercorpse", 0, Map.of(), carcass, List.of());
        // A drained cow Butchery never marked drained (it cannot be cut so): no guess.
        place(h, at, "drained_cow_carcass", 6, Map.of());
        h.getBlockEntity(at).getPersistentData().remove("isDrained");
        assertLines(h, "drained cow without isDrained", tooltip(h, at, carcass, carcass, true), List.of());
        // No answer from the server for a block that has a block entity: nothing either.
        place(h, at, "drained_cow_carcass", 6, Map.of());
        assertLines(h, "drained cow without server data", tooltip(h, at, carcass, carcass, false), List.of());

        // This mod's kangaroo at the same point as the skinned-next cow: the same lines.
        h.setBlock(at, ModBlocks.of(MobDefs.byId("kangaroo")).drained().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 2));
        var ours = AlexsButcheryJadePlugin.CarcassProvider.INSTANCE;
        assertLines(h, "this mod's kangaroo, head off", tooltip(h, at, ours, ours, true), List.of("next:tool.skinning_knife,action.skin", "progress:1,5"));
        assertLines(h, "no native lines for this mod's carcass", tooltip(h, at, carcass, carcass, true), List.of());

        // A cooking addon is named for the meat only while the loaded stage table still yields it.
        String bear = "{\"schema_version\":1,\"entity\":\"alexsmobs:grizzly_bear\",\"provider\":\"packfood\",\"required_mods\":[\"minecraft\"],"
                + "\"groups\":{\"meat\":{\"item\":\"minecraft:mutton\",\"count_mode\":\"native_group\"}}}";
        var grizzly = MobDefs.byId("grizzly_bear");
        h.setBlock(at, ModBlocks.of(grizzly).drained().get().defaultBlockState().setValue(AbstractCarcassBlock.BLOCKSTATE_STAGED, 1));
        var rules = PresentationTests.rules(h, Map.of(new net.minecraft.resources.ResourceLocation("pack:grizzly_jade"), bear), List.of("packfood"));
        net.minecraft.server.MinecraftServer server = h.getLevel().getServer();
        com.otectus.alexsbutchery.compat.CompatState.withState(server, rules, () -> assertLines(h, "grizzly with its meat from a provider",
                tooltip(h, at, ours, ours, true), List.of("next:tool.cleaver,action.head", "progress:0,5", "yields:group.meat,packfood")));
        net.minecraft.world.level.storage.loot.LootTable sticks = net.minecraft.world.level.storage.loot.LootTable.lootTable()
                .withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool().add(net.minecraft.world.level.storage.loot.entries.LootItem
                        .lootTableItem(net.minecraft.world.item.Items.STICK))).build();
        CompatTestSupport.withLootTables(h, Map.of(grizzly.stageTable(com.otectus.alexsbutchery.def.MobDef.CUT_1), sticks), () -> {
            var replaced = PresentationTests.rules(h, Map.of(new net.minecraft.resources.ResourceLocation("pack:grizzly_jade"), bear), List.of("packfood"));
            com.otectus.alexsbutchery.compat.CompatState.withState(server, replaced, () -> assertLines(h, "grizzly whose first cut yields sticks",
                    tooltip(h, at, ours, ours, true), List.of("next:tool.cleaver,action.head", "progress:0,5")));
        });

        // Butchery's skin rack: the skin and its next step, salted, wet (a percentage only where Butchery counts), cured.
        var rack = AlexsButcheryJadePlugin.NativeRackProvider.INSTANCE;
        h.setBlock(at.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        NativeButchery.Rack table = NativeButchery.rack();
        h.assertTrue(table != null, "the rack table has this Butchery's section");
        int skinState = table.skins().keySet().iterator().next();
        expect(h, at, "skin_rack", skinState, Map.of(), rack, List.of("rack.skin:" + table.skins().get(skinState), "rack.salt"));
        expect(h, at, "skin_rack", table.salted(), Map.of(), rack, List.of("rack.wet"));
        expect(h, at, "skin_rack", table.cured(), Map.of(), rack, List.of("rack.done"));
        expect(h, at, "skin_rack", 0, Map.of(), rack, List.of());
        if (table.timer() != null) {
            expect(h, at, "skin_rack", table.wet(), Map.of("skinWetted", 1.0, table.timer(), 300.0), rack, List.of("rack.curing:*"));
        } else {
            expect(h, at, "skin_rack", table.wet(), Map.of(), rack, List.of("rack.curing_wait"));
        }
        h.succeed();
    }

    private static void place(GameTestHelper h, BlockPos at, String id, int state, Map<String, Double> flags) {
        Block block = TestSupport.butcheryBlock(id);
        IntegerProperty property = NativeButcheryProbe.stateProperty(block);
        // A fresh block entity: re-setting the same block would keep the last case's persistent flags.
        h.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        h.setBlock(at, property == null || state < 0 ? block.defaultBlockState() : block.defaultBlockState().setValue(property, state));
        BlockEntity be = h.getBlockEntity(at);
        if (be != null) flags.forEach(be.getPersistentData()::putDouble);
    }

    private static void expect(GameTestHelper h, BlockPos at, String id, int state, Map<String, Double> flags,
                               Object provider, List<String> expected) {
        place(h, at, id, state, flags);
        @SuppressWarnings("unchecked")
        var server = (IServerDataProvider<BlockAccessor>) provider;
        assertLines(h, id + "@" + state + flags, tooltip(h, at, (IBlockComponentProvider) provider, server, true), expected);
    }

    private static void assertLines(GameTestHelper h, String what, List<Component> lines, List<String> expected) {
        List<String> got = new ArrayList<>();
        for (Component line : lines) {
            String key = NativeButcheryTests.keys(List.of(line)).get(0).replace("jade.alexsbutchery.", "");
            List<String> args = new ArrayList<>();
            for (String arg : NativeButcheryTests.args(line)) args.add(arg.replace("jade.alexsbutchery.", ""));
            if (key.equals("rack.skin") && line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getArgs().length == 1 && t.getArgs()[0] instanceof Component name
                    && name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents item) {
                args = List.of(item.getKey().replaceFirst("^item\\.([a-z0-9_]+)\\.", "$1:"));
            }
            got.add(args.isEmpty() ? key : key + ":" + String.join(",", args));
        }
        boolean same = got.size() == expected.size();
        for (int i = 0; same && i < got.size(); i++) {
            String want = expected.get(i);
            same = want.endsWith(":*") ? got.get(i).startsWith(want.substring(0, want.length() - 1)) : got.get(i).equals(want);
        }
        h.assertTrue(same, what + ": expected " + expected + ", Jade shows " + got);
    }

    /** The lines the client provider writes, with the server data the server provider sent (or none). */
    static List<Component> tooltip(GameTestHelper h, BlockPos rel, IBlockComponentProvider client, IServerDataProvider<BlockAccessor> server, boolean answered) {
        BlockPos abs = h.absolutePos(rel);
        ServerPlayer player = TestSupport.advancementPlayer(h);
        BlockState state = h.getLevel().getBlockState(abs);
        BlockEntity be = h.getLevel().getBlockEntity(abs);
        CompoundTag data = new CompoundTag();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        BlockAccessor accessor = (BlockAccessor) Proxy.newProxyInstance(JadeChecks.class.getClassLoader(), new Class<?>[]{BlockAccessor.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBlock" -> state.getBlock();
                    case "getBlockState" -> state;
                    case "getBlockEntity", "getTarget" -> be;
                    case "getPosition" -> abs;
                    case "getSide" -> Direction.UP;
                    case "getLevel" -> h.getLevel();
                    case "getPlayer" -> player;
                    case "getServerData" -> data;
                    case "getHitResult" -> hit;
                    case "isServerConnected" -> answered;
                    case "getPickedResult", "getFakeBlock" -> ItemStack.EMPTY;
                    case "getAccessorType" -> BlockAccessor.class;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "test accessor " + abs;
                    default -> zero(method.getReturnType());
                });
        if (answered && be != null) server.appendServerData(data, accessor);
        List<Component> lines = new ArrayList<>();
        ITooltip tooltip = (ITooltip) Proxy.newProxyInstance(JadeChecks.class.getClassLoader(), new Class<?>[]{ITooltip.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("add") && args != null) for (Object arg : args) if (arg instanceof Component c) lines.add(c);
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "test tooltip";
                        default -> zero(method.getReturnType());
                    };
                });
        IPluginConfig config = (IPluginConfig) Proxy.newProxyInstance(JadeChecks.class.getClassLoader(), new Class<?>[]{IPluginConfig.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? Boolean.TRUE : zero(method.getReturnType()));
        client.appendTooltip(tooltip, accessor, config);
        return lines;
    }

    private static Object zero(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == java.util.Set.class) return java.util.Set.of();
        if (type == java.util.List.class) return List.of();
        return null;
    }

    private JadeChecks() {}
}
