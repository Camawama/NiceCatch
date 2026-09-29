package net.camacraft.nicecatch.network;

import net.camacraft.camafy.network.Packets;
import net.camacraft.nicecatch.client.FishingLineRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> clients near the fish: an arrow-line fight between this player and this fish
 * began (active) or ended. Lets every spectator render the line, not just the angler —
 * a rod fight needs none of this because its bobber entity is already synced to everyone.
 */
public class ArrowLineMessage
{
    private final int playerId;
    private final int fishId;
    private final boolean active;

    public ArrowLineMessage(int playerId, int fishId, boolean active)
    {
        this.playerId = playerId;
        this.fishId = fishId;
        this.active = active;
    }

    public static void encode(ArrowLineMessage msg, FriendlyByteBuf buf)
    {
        buf.writeVarInt(msg.playerId);
        buf.writeVarInt(msg.fishId);
        buf.writeBoolean(msg.active);
    }

    public static ArrowLineMessage decode(FriendlyByteBuf buf)
    {
        return new ArrowLineMessage(buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(ArrowLineMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        Packets.handleClient(ctx, () -> () -> FishingLineRenderer.handleLine(msg.playerId, msg.fishId, msg.active));
    }
}
