package com.otectus.alexsbutchery.compat;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a client is told about the server's butchering yields, sent on login and after every data reload: per creature
 * and stage, the items a stage gives with count ranges and chances as the server's loaded tables and rules describe
 * them, which outputs come from an addon, variant alternatives and what carcasses no variant matches get, stages a pack replaced with
 * logic that cannot be summarised, and which of our items no creature yields any more. Display only: drops are always
 * decided by the server. The {@link #digest} lets a test compare what the client holds with what the server built.
 */
public record CompatView(Map<String, List<StageView>> mobs, Set<ResourceLocation> hiddenItems, List<ProviderView> providers, String digest) {

    /** One stage of one creature. {@code custom}: a pack's table could not be summarised; the outputs are partial. */
    public record StageView(String stage, boolean custom, List<OutputView> outputs) {}

    /**
     * One output slot: alternatives (more than one for variants), the count per roll and its chance, which provider
     * supplies it ({@code native} for this mod's own drops), the snapshot tag that chooses among variant alternatives,
     * whether it applies only to carcasses no earlier variant output matched ({@code otherwise}: the next provider of the
     * chain, or the native drops), and the product group it belongs to (null for a side product). Each link of a
     * provider chain is an output of its own, with its own counts and provider.
     */
    public record OutputView(List<ResourceLocation> items, int min, int max, float chance, String provider,
                             @Nullable String variantTag, boolean otherwise, @Nullable String group) {
        public OutputView asOtherwise() {
            return new OutputView(items, min, max, chance, provider, variantTag, true, group);
        }

        public OutputView withChance(float scaled) {
            return new OutputView(items, min, max, scaled, provider, variantTag, otherwise, group);
        }

        public OutputView inGroup(@Nullable String of) {
            return new OutputView(items, min, max, chance, provider, variantTag, otherwise, of);
        }
    }

    public record ProviderView(String provider, String name, String version, boolean installed, boolean active) {}

    /** The client's copy, set from the network; data only, cleared on disconnect. */
    private static volatile CompatView received;
    /** Views received since the game started, so a check can tell that a reload re-sent the view. */
    private static volatile int receivedCount;

    @Nullable
    public static CompatView received() {
        return received;
    }

    public static int receivedCount() {
        return receivedCount;
    }

    public static void setReceived(@Nullable CompatView view) {
        received = view;
        if (view != null) receivedCount++;
    }

    public static CompatView create(Map<String, List<StageView>> mobs, Set<ResourceLocation> hidden, List<ProviderView> providers) {
        CompatView unsigned = new CompatView(mobs, hidden, providers, "");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        unsigned.writeBody(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            return new CompatView(mobs, hidden, providers, HexFormat.of().formatHex(hash, 0, 8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void write(FriendlyByteBuf buf) {
        writeBody(buf);
        buf.writeUtf(digest);
    }

    private void writeBody(FriendlyByteBuf buf) {
        buf.writeVarInt(mobs.size());
        mobs.forEach((mob, stages) -> {
            buf.writeUtf(mob);
            buf.writeVarInt(stages.size());
            for (StageView stage : stages) {
                buf.writeUtf(stage.stage());
                buf.writeBoolean(stage.custom());
                buf.writeVarInt(stage.outputs().size());
                for (OutputView o : stage.outputs()) {
                    buf.writeCollection(o.items(), FriendlyByteBuf::writeResourceLocation);
                    buf.writeVarInt(o.min());
                    buf.writeVarInt(o.max());
                    buf.writeFloat(o.chance());
                    buf.writeUtf(o.provider());
                    buf.writeNullable(o.variantTag(), FriendlyByteBuf::writeUtf);
                    buf.writeBoolean(o.otherwise());
                    buf.writeNullable(o.group(), FriendlyByteBuf::writeUtf);
                }
            }
        });
        buf.writeCollection(hiddenItems, FriendlyByteBuf::writeResourceLocation);
        buf.writeVarInt(providers.size());
        for (ProviderView p : providers) {
            buf.writeUtf(p.provider());
            buf.writeUtf(p.name());
            buf.writeUtf(p.version());
            buf.writeBoolean(p.installed());
            buf.writeBoolean(p.active());
        }
    }

    public static CompatView read(FriendlyByteBuf buf) {
        int mobCount = buf.readVarInt();
        Map<String, List<StageView>> mobs = new LinkedHashMap<>();
        for (int m = 0; m < mobCount; m++) {
            String mob = buf.readUtf();
            int stageCount = buf.readVarInt();
            List<StageView> stages = new ArrayList<>(stageCount);
            for (int s = 0; s < stageCount; s++) {
                String stage = buf.readUtf();
                boolean custom = buf.readBoolean();
                int outputCount = buf.readVarInt();
                List<OutputView> outputs = new ArrayList<>(outputCount);
                for (int o = 0; o < outputCount; o++) {
                    List<ResourceLocation> items = buf.readList(FriendlyByteBuf::readResourceLocation);
                    int min = buf.readVarInt();
                    int max = buf.readVarInt();
                    float chance = buf.readFloat();
                    String provider = buf.readUtf();
                    String variant = buf.readNullable(FriendlyByteBuf::readUtf);
                    boolean otherwise = buf.readBoolean();
                    String group = buf.readNullable(FriendlyByteBuf::readUtf);
                    outputs.add(new OutputView(items, min, max, chance, provider, variant, otherwise, group));
                }
                stages.add(new StageView(stage, custom, outputs));
            }
            mobs.put(mob, stages);
        }
        Set<ResourceLocation> hidden = new LinkedHashSet<>(buf.readList(FriendlyByteBuf::readResourceLocation));
        int providerCount = buf.readVarInt();
        List<ProviderView> providers = new ArrayList<>(providerCount);
        for (int i = 0; i < providerCount; i++) {
            providers.add(new ProviderView(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readBoolean()));
        }
        return new CompatView(mobs, hidden, providers, buf.readUtf());
    }

    @Nullable
    public StageView stage(String mob, String stage) {
        List<StageView> stages = mobs.get(mob);
        if (stages == null) return null;
        for (StageView s : stages) if (s.stage().equals(stage)) return s;
        return null;
    }

    @Nullable
    public ProviderView provider(String id) {
        for (ProviderView p : providers) if (p.provider().equals(id)) return p;
        return null;
    }
}
