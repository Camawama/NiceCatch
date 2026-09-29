package net.camacraft.nicecatch.network;

import net.camacraft.camafy.network.CamafyChannel;
import net.camacraft.nicecatch.NiceCatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class NiceCatchNet
{
    /** Bump whenever any message's wire format changes, so mismatched jars fail fast at login. */
    private static final String PROTOCOL_VERSION = "2";

    public static final CamafyChannel CHANNEL = CamafyChannel.create(
            ResourceLocation.fromNamespaceAndPath(NiceCatch.MODID, "main"), PROTOCOL_VERSION);

    public static void register()
    {
        // Each message is pinned to the side that receives it: one arriving the wrong way is dropped,
        // so a modified client can never run a client-bound handler on an integrated server.
        // Ids follow registration order, so keep it.
        CHANNEL.serverbound(CastMessage.class, CastMessage::encode, CastMessage::decode, CastMessage::handle)
                .serverbound(HookSetMessage.class, HookSetMessage::encode, HookSetMessage::decode, HookSetMessage::handle)
                .serverbound(ReelMessage.class, ReelMessage::encode, ReelMessage::decode, ReelMessage::handle)
                .clientbound(BiteMessage.class, BiteMessage::encode, BiteMessage::decode, BiteMessage::handle)
                .clientbound(FightTickMessage.class, FightTickMessage::encode, FightTickMessage::decode, FightTickMessage::handle)
                .clientbound(FightEndMessage.class, FightEndMessage::encode, FightEndMessage::decode, FightEndMessage::handle)
                .clientbound(ArrowFightMessage.class, ArrowFightMessage::encode, ArrowFightMessage::decode, ArrowFightMessage::handle)
                .clientbound(ArrowLineMessage.class, ArrowLineMessage::encode, ArrowLineMessage::decode, ArrowLineMessage::handle)
                .serverbound(CutLineMessage.class, CutLineMessage::encode, CutLineMessage::decode, CutLineMessage::handle);
    }

    public static void sendToServer(Object message)
    {
        CHANNEL.toServer(message);
    }

    public static void sendTo(ServerPlayer player, Object message)
    {
        CHANNEL.toPlayer(player, message);
    }

    /** To every player tracking this entity (and the entity itself, if it's a player). */
    public static void sendToTrackingAndSelf(net.minecraft.world.entity.Entity entity, Object message)
    {
        CHANNEL.toTrackingAndSelf(entity, message);
    }
}
