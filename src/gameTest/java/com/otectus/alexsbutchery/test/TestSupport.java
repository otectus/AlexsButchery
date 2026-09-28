package com.otectus.alexsbutchery.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

final class TestSupport {
    static final BlockPos ORIGIN = new BlockPos(2, 1, 2);
    /** The empty.nbt template is a cube of this many blocks. */
    static final int STRUCTURE_SIZE = 5;

    static FakePlayer player(GameTestHelper h) {
        FakePlayer player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "ButcherTest")) {
            @Override
            public boolean hasDisconnected() {
                return false;
            }
        };
        BlockPos pos = h.absolutePos(ORIGIN);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5);
        return player;
    }

    static ItemStack butcheryItem(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("butchery", path));
        if (item == null) throw new IllegalStateException("Butchery item missing: " + path);
        return new ItemStack(item);
    }

    static Block butcheryBlock(String path) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("butchery", path));
        if (block == null) throw new IllegalStateException("Butchery block missing: " + path);
        return block;
    }

    static EntityType<?> alexsMob(String path) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("alexsmobs", path));
        if (type == null) throw new IllegalStateException("Alex's Mobs entity missing: " + path);
        return type;
    }

    /**
     * A real server player for tests that check advancements: Forge's PlayerAdvancements never awards anything to a
     * FakePlayer. Standing south of the origin in survival.
     */
    static ServerPlayer advancementPlayer(GameTestHelper h) {
        // The helper's own mock player cannot join under Forge's networking; this one never joins. It borrows a
        // FakePlayer's connection, which drops every packet, so reward and toast packets go nowhere.
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "ButcherRecords"));
        player.connection = player(h).connection;
        player.setGameMode(GameType.SURVIVAL);
        BlockPos pos = h.absolutePos(ORIGIN);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5);
        return player;
    }

    /** Kills the mob with the player's main-hand item, the way a melee kill would. */
    static void kill(GameTestHelper h, LivingEntity victim, Player player) {
        victim.hurt(h.getLevel().damageSources().playerAttack(player), 10_000F);
    }

    /** Right-clicks the block at {@code rel} with the player's main hand. */
    static InteractionResult use(GameTestHelper h, BlockPos rel, Player player) {
        BlockPos pos = h.absolutePos(rel);
        Vec3 hit = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.99);
        return h.getLevel().getBlockState(pos).use(h.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, Direction.SOUTH, pos, false));
    }

    static List<ItemEntity> items(GameTestHelper h) {
        // Only this test's structure: neighbouring tests in the batch drop their own items a few blocks away.
        BlockPos min = h.absolutePos(BlockPos.ZERO);
        BlockPos max = h.absolutePos(new BlockPos(STRUCTURE_SIZE - 1, STRUCTURE_SIZE - 1, STRUCTURE_SIZE - 1));
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(min).minmax(new net.minecraft.world.phys.AABB(max)));
    }

    static long count(GameTestHelper h, Item item) {
        return items(h).stream().filter(e -> e.getItem().is(item)).mapToLong(e -> e.getItem().getCount()).sum();
    }

    static long count(GameTestHelper h, ResourceLocation item) {
        Item resolved = ForgeRegistries.ITEMS.getValue(item);
        return resolved == null ? 0 : count(h, resolved);
    }

    static void clearItems(GameTestHelper h) {
        items(h).forEach(ItemEntity::discard);
    }

    private TestSupport() {}
}
