package com.otectus.alexsbutchery.compat;

import com.otectus.alexsbutchery.AlexsButchery;
import com.otectus.alexsbutchery.client.compat.ClientCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** The server-to-client channel that carries the {@link CompatView}. */
public final class CompatNetwork {
    /** 2: outputs name their product group and mark what only carcasses no variant matched get (0.2.1). */
    private static final String VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(AlexsButchery.id("compat"), () -> VERSION,
            VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.messageBuilder(Sync.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Sync::write).decoder(Sync::read).consumerMainThread(Sync::handle).add();
    }

    public static void send(ServerPlayer player, CompatView view) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Sync(view));
    }

    /** The server's current view, replacing whatever the client held. */
    public record Sync(CompatView view) {
        void write(FriendlyByteBuf buf) {
            view.write(buf);
        }

        static Sync read(FriendlyByteBuf buf) {
            return new Sync(CompatView.read(buf));
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            CompatView.setReceived(view);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCompat.onReceived(view));
        }
    }

    private CompatNetwork() {}
}
