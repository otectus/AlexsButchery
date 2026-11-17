package com.otectus.alexsbutchery.mixin.compat;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Prevents the optional Carry On target from being resolved when the mod is absent or on a dedicated server. */
public final class CarryOnMixinPlugin implements IMixinConfigPlugin {
    private boolean available;

    @Override
    public void onLoad(String mixinPackage) {
        var loading = FMLLoader.getLoadingModList();
        available = FMLEnvironment.dist == Dist.CLIENT && loading != null && loading.getModFileById("carryon") != null;
        if (available) try {
            // Carry On jar-jars MixinExtras 0.2.0-beta.6. Bootstrap it here too so config ordering cannot decide
            // whether our @WrapOperation hooks are registered; reflection keeps the no-Carry-On path linkage-free.
            Class.forName("com.llamalad7.mixinextras.MixinExtrasBootstrap").getMethod("init").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Carry On is loaded without its required MixinExtras bootstrap", e);
        }
    }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return available; }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
