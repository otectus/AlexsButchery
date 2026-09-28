package com.otectus.alexsbutchery.block;

import com.otectus.alexsbutchery.def.MobDef;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * What a bottle of sulfuric acid leaves of a whole carcass, like Butchery's {@code <mob>_skeleton} blocks: the mob's
 * own model in bone colours, lying ({@code blockstate 0}) or hanging from the hook it was dissolved on ({@code 1}).
 * Decorative; it keeps the mob snapshot so variants and sizes survive, and breaks back into its item.
 */
public class SkeletonBlock extends AbstractCarcassBlock {

    public SkeletonBlock(MobDef def) {
        super(def, SoundType.BONE_BLOCK);
    }

    @Override
    public IntegerProperty stateProperty() {
        return BLOCKSTATE_FRESH;
    }

    @Override
    public boolean hanging(BlockState state) {
        return state.getValue(BLOCKSTATE_FRESH) == 1;
    }
}
