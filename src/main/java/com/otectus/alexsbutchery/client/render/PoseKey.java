package com.otectus.alexsbutchery.client.render;

import com.otectus.alexsbutchery.butcher.Stages;
import com.otectus.alexsbutchery.client.pose.PoseProfile;
import net.minecraft.resources.ResourceLocation;

/**
 * What a posed carcass's geometry depends on: its model, profile, texture (including transparent anatomy), size,
 * complete appearance snapshot, look and stages. The handle owns the immutable snapshot key, so a cache lookup
 * does not copy NBT. Model and profile use identity; caches are cleared on resource reload.
 */
record PoseKey(Object model, PoseProfile profile, ResourceLocation texture, float rendererScale,
               CarcassModels.Snapshot snapshot, StageTextures.Look look, int done) {
    static PoseKey of(CarcassScene.Subject s, CarcassModels.Shape shape) {
        int done = 0;
        for (Stages.Action action : s.done()) done |= 1 << action.ordinal();
        return new PoseKey(shape.model(), s.profile(), shape.texture(), shape.scale(), shape.snapshot(), s.look(), done);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PoseKey k && model == k.model && profile == k.profile && rendererScale == k.rendererScale
                && texture.equals(k.texture) && snapshot.equals(k.snapshot) && look == k.look && done == k.done;
    }

    @Override
    public int hashCode() {
        int h = System.identityHashCode(model) * 31 + System.identityHashCode(profile);
        h = h * 31 + Float.hashCode(rendererScale);
        h = h * 31 + texture.hashCode();
        h = h * 31 + snapshot.hashCode();
        h = h * 31 + look.hashCode();
        return h * 31 + done;
    }
}
