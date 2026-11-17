package com.otectus.alexsbutchery.test;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.def.MobDefs;
import com.otectus.alexsbutchery.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AlexsButchery.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PortableTransportTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void itemMergingAndHopperTransfersConserveDistinctProgress(GameTestHelper h) {
        ItemStack first = stack(1, 2), second = stack(2, 3);
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        var a = item(h, pos, first.copy());
        var b = item(h, pos, second.copy());
        var equal = item(h, pos, first.copyWithCount(1));
        h.runAfterDelay(45, () -> {
            h.assertTrue(a.isAlive() && b.isAlive(), "different process payloads never merge");
            h.assertTrue(a.getItem().getCount() + b.getItem().getCount() + (equal.isAlive() ? equal.getItem().getCount() : 0) == 6,
                    "item merging conserves every carcass");
            SimpleContainer container = new SimpleContainer(3);
            h.assertTrue(HopperBlockEntity.addItem(null, container, first.copy(), Direction.DOWN).isEmpty(), "hopper inserts first progress");
            h.assertTrue(HopperBlockEntity.addItem(null, container, second.copy(), Direction.DOWN).isEmpty(), "hopper inserts distinct progress");
            h.assertTrue(container.getItem(0).getCount() == 2 && container.getItem(1).getCount() == 3,
                    "hopper keeps different progress in separate slots");
            h.assertTrue(ItemStack.isSameItemSameTags(container.getItem(0), first)
                    && ItemStack.isSameItemSameTags(container.getItem(1), second), "container transfer retains exact payloads");
            var saved = container.createTag();
            var restored = new SimpleContainer(3);
            restored.fromTag(saved);
            int count = 0;
            for (int i = 0; i < restored.getContainerSize(); i++) count += restored.getItem(i).getCount();
            h.assertTrue(count == 5 && ItemStack.isSameItemSameTags(restored.getItem(0), first), "container save/load retains payload and counts");
            h.succeed();
        });
    }

    private static ItemStack stack(int fills, int count) {
        ItemStack stack = new ItemStack(ModItems.of(MobDefs.byId("moose")).carcass().get(), count);
        var tag = stack.getOrCreateTagElement("BlockEntityTag");
        tag.putBoolean("Bleeding", true); tag.putInt("Fills", fills); tag.putInt("BleedTicks", fills * 45);
        var appearance = new net.minecraft.nbt.CompoundTag();
        appearance.putBoolean("Antlered", fills == 1);
        tag.put("MobData", appearance);
        return stack;
    }
    private static ItemEntity item(GameTestHelper h, BlockPos pos, ItemStack stack) {
        var entity = new ItemEntity(h.getLevel(), pos.getX() + .5, pos.getY(), pos.getZ() + .5, stack);
        entity.setNoGravity(true); entity.setDeltaMovement(Vec3.ZERO); entity.setNeverPickUp();
        h.getLevel().addFreshEntity(entity);
        return entity;
    }
}
