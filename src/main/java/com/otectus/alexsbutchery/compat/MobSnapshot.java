package com.otectus.alexsbutchery.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/**
 * Captures what a dead mob looked like (Alex's Mobs variant, tusks, antlers, size...) as the NBT its own class
 * writes, minus everything vanilla that describes the living entity rather than its appearance. The carcass, its
 * head and its trophy keep this so the client can restore it onto a dummy entity and ask the mob's renderer for the
 * right texture.
 */
public final class MobSnapshot {
    private static final Set<String> STRIP = Set.of(
            "Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround", "Invulnerable", "PortalCooldown", "UUID",
            "Passengers", "CustomName", "CustomNameVisible", "Silent", "NoGravity", "Glowing", "TicksFrozen", "HasVisualFire",
            "Tags", "Health", "HurtTime", "HurtByTimestamp", "DeathTime", "AbsorptionAmount", "Attributes", "ActiveEffects",
            "FallFlying", "SleepingX", "SleepingY", "SleepingZ", "Brain", "ArmorItems", "HandItems", "ArmorDropChances",
            "HandDropChances", "Leash", "LeftHanded", "DeathLootTable", "DeathLootTableSeed", "PersistenceRequired",
            "CanPickUpLoot", "NoAI", "InLove", "Age", "ForcedAge", "LoveCause", "Owner", "Sitting", "Tame", "Team",
            "ForgeCaps", "ForgeData", "NeoForgeData", "Bukkit", "Paper", "Spigot.ticksLived", "WorldUUIDLeast", "WorldUUIDMost");

    public static CompoundTag capture(LivingEntity entity) {
        CompoundTag tag = new CompoundTag();
        try {
            entity.saveWithoutId(tag);
        } catch (RuntimeException e) {
            return new CompoundTag();
        }
        for (String key : STRIP) tag.remove(key);
        return tag;
    }

    private MobSnapshot() {}
}
