package com.otectus.alexsbutchery.client.render;

import com.otectus.alexsbutchery.block.CarcassBounds;
import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.client.pose.PoseProfile;

/**
 * What a posed carcass's geometry depends on, built without copying its snapshot: the model its renderer picked, the
 * profile, the renderer's size, the snapshot variant and size the exported bounds also key on, the look and the
 * stages done. Model and profile are compared by identity; caches holding these are cleared on resource reload.
 */
record PoseKey(Object model, PoseProfile profile, float rendererScale, String variant, double size, StageTextures.Look look, int done) {
    static PoseKey of(CarcassScene.Subject s, CarcassModels.Shape shape) {
        int done = 0;
        for (Stages.Action action : s.done()) done |= 1 << action.ordinal();
        String mob = s.def().id();
        return new PoseKey(shape.model(), s.profile(), shape.scale(), CarcassBounds.variant(mob, s.mobData()),
                CarcassBounds.scale(mob, s.mobData()), s.look(), done);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PoseKey k && model == k.model && profile == k.profile && rendererScale == k.rendererScale
                && variant.equals(k.variant) && size == k.size && look == k.look && done == k.done;
    }

    @Override
    public int hashCode() {
        int h = System.identityHashCode(model) * 31 + System.identityHashCode(profile);
        h = h * 31 + Float.hashCode(rendererScale);
        h = h * 31 + variant.hashCode();
        h = h * 31 + Double.hashCode(size);
        h = h * 31 + look.hashCode();
        return h * 31 + done;
    }
}
