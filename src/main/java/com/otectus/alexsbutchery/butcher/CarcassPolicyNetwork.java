package com.otectus.alexsbutchery.butcher;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.def.DeathSpawnMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Sends the server's frozen world-load policy after Forge's raw SERVER-config handshake. */
@Mod.EventBusSubscriber(modid = AlexsButchery.MOD_ID)
public final class CarcassPolicyNetwork {
    private static final String VERSION = "1";
    private static final int MAX_SPECIES = 256;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(AlexsButchery.id("carcass_policy"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.messageBuilder(Sync.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Sync::write).decoder(Sync::read).consumerMainThread(Sync::handle).add();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Sync(CarcassPolicy.current().snapshot()));
        }
    }

    public record Sync(CarcassPolicy.Snapshot snapshot) {
        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(snapshot.deathModes().size());
            snapshot.deathModes().forEach((id, mode) -> {
                buf.writeResourceLocation(id);
                buf.writeEnum(mode);
            });
            buf.writeVarInt(snapshot.groundBleeding().size());
            snapshot.groundBleeding().forEach(buf::writeResourceLocation);
        }

        static Sync read(FriendlyByteBuf buf) {
            int modes = boundedCount(buf);
            Map<ResourceLocation, DeathSpawnMode> deathModes = new LinkedHashMap<>();
            for (int i = 0; i < modes; i++) deathModes.put(buf.readResourceLocation(), buf.readEnum(DeathSpawnMode.class));
            int grounds = boundedCount(buf);
            Set<ResourceLocation> groundBleeding = new LinkedHashSet<>();
            for (int i = 0; i < grounds; i++) groundBleeding.add(buf.readResourceLocation());
            return new Sync(new CarcassPolicy.Snapshot(deathModes, groundBleeding));
        }

        void handle(Supplier<NetworkEvent.Context> supplied) {
            NetworkEvent.Context context = supplied.get();
            context.enqueueWork(() -> CarcassPolicy.acceptSnapshot(snapshot));
            context.setPacketHandled(true);
        }

        private static int boundedCount(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            if (count < 0 || count > MAX_SPECIES) throw new IllegalArgumentException("Invalid carcass policy species count " + count);
            return count;
        }
    }

    private CarcassPolicyNetwork() {}
}
