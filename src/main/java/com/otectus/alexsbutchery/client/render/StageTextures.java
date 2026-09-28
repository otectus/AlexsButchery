package com.otectus.alexsbutchery.client.render;

import com.github.alexthe666.alexsmobs.entity.EntityTerrapin;
import com.github.alexthe666.alexsmobs.entity.util.TerrapinTypes;
import com.mojang.blaze3d.platform.NativeImage;
import com.otectus.alexsbutchery.AlexsButchery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Appearance layers are composited before draining. Tissue and bone have their own UV material, never fur UVs. */
public final class StageTextures {
    public enum Look { FRESH, DRAINED, SKINNED, BONE }
    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    public static final int MATERIAL_SIZE = 128;
    private static final int[] BONE_RAMP = {0xc4bba1, 0xd4cbb2, 0xe3dac0, 0xe3dac0, 0xdad1b8};

    public static ResourceLocation get(ResourceLocation source, Look look) {
        if (look == Look.FRESH) return source;
        if (look == Look.SKINNED || look == Look.BONE) return material(look);
        String key = source + "#drained";
        return CACHE.computeIfAbsent(key, ignored -> {
            NativeImage image = null;
            try {
                image = read(source);
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int c = image.getPixelRGBA(x, y);
                    int r = FastColor.ABGR32.red(c), g = FastColor.ABGR32.green(c), b = FastColor.ABGR32.blue(c);
                    float grey = (0.299F * r + 0.587F * g + 0.114F * b) * 0.82F;
                    image.setPixelRGBA(x, y, FastColor.ABGR32.color(FastColor.ABGR32.alpha(c),
                            Math.round(grey * 0.75F + b * 0.25F), Math.round((grey * 0.75F + g * 0.25F) * 0.95F),
                            Math.round((grey * 0.75F + r * 0.25F) * 0.95F)));
                }
                return register("drained/" + source.getNamespace() + "/" + source.getPath().replace('.', '_'), image);
            } catch (IOException | RuntimeException e) {
                if (image != null) image.close();
                AlexsButchery.LOGGER.warn("Could not drain texture {}", source, e);
                return source;
            }
        });
    }

    /** Alex's Mobs draws OVERLAY terrapins using three tinted passes over the same UV layout. */
    public static ResourceLocation appearance(LivingEntity entity, ResourceLocation source) {
        if (!(entity instanceof EntityTerrapin turtle) || turtle.getTurtleType() != TerrapinTypes.OVERLAY || turtle.isKoopa()) return source;
        int shell = Math.floorMod(turtle.getShellType(), 6), skin = Math.floorMod(turtle.getSkinType(), 4);
        String key = "terrapin/" + shell + "_" + skin + "_" + Integer.toHexString(turtle.getTurtleColor()) + "_"
                + Integer.toHexString(turtle.getShellColor()) + "_" + Integer.toHexString(turtle.getSkinColor());
        return CACHE.computeIfAbsent(key, ignored -> {
            NativeImage image = null;
            try {
                image = read(source);
                tint(image, turtle.getTurtleColor());
                overlay(image, new ResourceLocation("alexsmobs", "textures/entity/terrapin/overlay/terrapin_shell_pattern_" + shell + ".png"), turtle.getShellColor());
                overlay(image, new ResourceLocation("alexsmobs", "textures/entity/terrapin/overlay/terrapin_skin_pattern_" + skin + ".png"), turtle.getSkinColor());
                return register(key, image);
            } catch (IOException | RuntimeException e) {
                if (image != null) image.close();
                AlexsButchery.LOGGER.warn("Could not composite terrapin appearance", e);
                return source;
            }
        });
    }

    static NativeImage read(ResourceLocation source) throws IOException {
        var mc = Minecraft.getInstance();
        var resource = mc.getResourceManager().getResource(source);
        if (resource.isPresent()) {
            try (InputStream in = resource.get().open()) { return NativeImage.read(in); }
        }
        if (mc.getTextureManager().getTexture(source) instanceof DynamicTexture texture && texture.getPixels() != null) {
            NativeImage pixels = texture.getPixels();
            NativeImage copy = new NativeImage(pixels.getWidth(), pixels.getHeight(), false);
            copy.copyFrom(pixels);
            return copy;
        }
        throw new IOException("No pixels for " + source);
    }

    private static void tint(NativeImage image, int rgb) {
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            image.setPixelRGBA(x, y, tinted(image.getPixelRGBA(x, y), rgb));
        }
    }

    private static int tinted(int c, int rgb) {
        return FastColor.ABGR32.color(FastColor.ABGR32.alpha(c), FastColor.ABGR32.blue(c) * (rgb & 255) / 255,
                FastColor.ABGR32.green(c) * (rgb >> 8 & 255) / 255, FastColor.ABGR32.red(c) * (rgb >> 16 & 255) / 255);
    }

    private static void overlay(NativeImage target, ResourceLocation source, int rgb) throws IOException {
        try (NativeImage layer = read(source)) {
            for (int y = 0; y < target.getHeight(); y++) for (int x = 0; x < target.getWidth(); x++) {
                int over = tinted(layer.getPixelRGBA(x * layer.getWidth() / target.getWidth(), y * layer.getHeight() / target.getHeight()), rgb);
                int under = target.getPixelRGBA(x, y);
                float a = FastColor.ABGR32.alpha(over) / 255F, b = FastColor.ABGR32.alpha(under) / 255F * (1F - a), total = a + b;
                if (total == 0F) continue;
                target.setPixelRGBA(x, y, FastColor.ABGR32.color(Math.round(total * 255F),
                        Math.round((FastColor.ABGR32.blue(over) * a + FastColor.ABGR32.blue(under) * b) / total),
                        Math.round((FastColor.ABGR32.green(over) * a + FastColor.ABGR32.green(under) * b) / total),
                        Math.round((FastColor.ABGR32.red(over) * a + FastColor.ABGR32.red(under) * b) / total)));
            }
        }
    }

    /** Small repeating muscle bundles and connective tissue, independent of any animal's coat markings. */
    public static ResourceLocation material(Look look) {
        if (look != Look.SKINNED && look != Look.BONE) throw new IllegalArgumentException("Not a tissue material: " + look);
        return CACHE.computeIfAbsent("material/" + look, ignored -> {
            NativeImage image = new NativeImage(MATERIAL_SIZE, MATERIAL_SIZE, false);
            for (int y = 0; y < MATERIAL_SIZE; y++) for (int x = 0; x < MATERIAL_SIZE; x++) {
                int rgb;
                if (look == Look.BONE) {
                    int shade = Math.floorMod(x / 3 + y / 5, 5);
                    rgb = BONE_RAMP[shade];
                } else {
                    int band = Math.floorMod(x + y / 4, 12);
                    // Cream seams border broad dark-red muscle fibres; no per-pixel random speckling.
                    rgb = band == 0 ? 0xc8ac8c : band == 1 ? 0x9b6051 : band < 5 ? 0x8b3035
                            : band < 9 ? 0xa74345 : 0x652329;
                }
                image.setPixelRGBA(x, y, FastColor.ABGR32.color(255, rgb & 255, rgb >> 8 & 255, rgb >> 16 & 255));
            }
            return register("material/" + look.name().toLowerCase(java.util.Locale.ROOT), image);
        });
    }

    private static ResourceLocation register(String path, NativeImage image) {
        ResourceLocation id = AlexsButchery.id("generated/" + path);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        return id;
    }

    public static void clear() {
        var textures = Minecraft.getInstance().getTextureManager();
        CACHE.values().stream().distinct().filter(id -> id.getNamespace().equals(AlexsButchery.MOD_ID) && id.getPath().startsWith("generated/")).forEach(textures::release);
        CACHE.clear();
    }

    private StageTextures() {}
}
