package net.camacraft.nicecatch.network;

import net.camacraft.camafy.network.Packets;
import net.camacraft.nicecatch.client.ClientFishing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: a fishing-line arrow struck a fish, so the reel fight begins. Unlike a rod
 * fight (which the client starts itself on the hook-set), an arrow fight is triggered entirely
 * server-side, so the server tells the client to enter the fight and which entity the line is
 * attached to (for the follow camera and the rendered line).
 */
public class ArrowFightMessage
{
    private final int fishId;

    public ArrowFightMessage(int fishId)
    {
        this.fishId = fishId;
    }

    public static void encode(ArrowFightMessage msg, FriendlyByteBuf buf)
    {
        buf.writeVarInt(msg.fishId);
    }

    public static ArrowFightMessage decode(FriendlyByteBuf buf)
    {
        return new ArrowFightMessage(buf.readVarInt());
    }

    public static void handle(ArrowFightMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        Packets.handleClient(ctx, () -> () -> ClientFishing.startArrowFight(msg.fishId));
    }
}
