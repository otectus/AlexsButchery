package com.otectus.alexsbutchery.block;

import com.otectus.alexsbutchery.def.MobDef;

/** A block that belongs to one mob of the table (carcass, drained carcass, head, head mount). */
public interface MobBlock {
    MobDef def();
}
