package com.otectus.alexsbutchery.registry;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.block.CarcassBlock;
import com.otectus.alexsbutchery.block.DrainedCarcassBlock;
import com.otectus.alexsbutchery.block.HeadBlock;
import com.otectus.alexsbutchery.block.HeadMountBlock;
import com.otectus.alexsbutchery.block.RugBlock;
import com.otectus.alexsbutchery.block.SkeletonBlock;
import com.otectus.alexsbutchery.block.SkinRackBlock;
import com.otectus.alexsbutchery.block.StagedCarcassBlock;
import com.otectus.alexsbutchery.def.MobDef;
import com.otectus.alexsbutchery.def.MobDefs;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * One set of blocks per mob in the table: fresh carcass, drained carcass (bleeding mobs), head and head mount, the
 * skeleton acid leaves (mobs with bones) and a pelt rug (the few mobs whose pelt makes one).
 */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AlexsButchery.MOD_ID);

    public record Entries(MobDef def, RegistryObject<CarcassBlock> carcass, @Nullable RegistryObject<DrainedCarcassBlock> drained,
                          @Nullable RegistryObject<HeadBlock> head, @Nullable RegistryObject<HeadMountBlock> mount,
                          @Nullable RegistryObject<SkeletonBlock> skeleton, @Nullable RegistryObject<RugBlock> rug) {

        /** Every block of this mob that exists, for tags, loot and the shared block entity type. */
        public Stream<RegistryObject<? extends Block>> all() {
            return Stream.<RegistryObject<? extends Block>>of(carcass, drained, head, mount, skeleton, rug).filter(Objects::nonNull);
        }
    }

    private static final Map<MobDef, Entries> BY_DEF = new LinkedHashMap<>();

    /** Butchery's skin rack while one of our skins cures on it; never an item, it swaps back to Butchery's rack. */
    public static final RegistryObject<SkinRackBlock> SKIN_RACK = BLOCKS.register("skin_rack", SkinRackBlock::new);

    static {
        for (MobDef def : MobDefs.all()) {
            RegistryObject<CarcassBlock> carcass = BLOCKS.register(def.carcassId().getPath(),
                    () -> def.bleeds() ? new CarcassBlock(def) : new StagedCarcassBlock(def));
            RegistryObject<DrainedCarcassBlock> drained = def.bleeds()
                    ? BLOCKS.register(def.drainedId().getPath(), () -> new DrainedCarcassBlock(def)) : null;
            RegistryObject<HeadBlock> head = def.hasHead() ? BLOCKS.register(def.headId().getPath(), () -> new HeadBlock(def)) : null;
            RegistryObject<HeadMountBlock> mount = def.hasHead() ? BLOCKS.register(def.mountId().getPath(), () -> new HeadMountBlock(def)) : null;
            RegistryObject<SkeletonBlock> skeleton = def.hasSkeleton()
                    ? BLOCKS.register(def.skeletonId().getPath(), () -> new SkeletonBlock(def)) : null;
            RegistryObject<RugBlock> rug = def.hasRug() ? BLOCKS.register(def.rugId().getPath(), () -> new RugBlock(def)) : null;
            BY_DEF.put(def, new Entries(def, carcass, drained, head, mount, skeleton, rug));
        }
    }

    public static Entries of(MobDef def) {
        return BY_DEF.get(def);
    }

    public static Collection<Entries> all() {
        return Collections.unmodifiableCollection(BY_DEF.values());
    }

    /** Every mob block, resolved; only valid after block registration. */
    public static Block[] allBlocks() {
        return BY_DEF.values().stream().flatMap(Entries::all).map(RegistryObject::get).toArray(Block[]::new);
    }

    private ModBlocks() {}
}
