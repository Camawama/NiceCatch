package net.camacraft.nicecatch.network;

import net.camacraft.camafy.network.Packets;
import net.camacraft.nicecatch.server.ServerFishingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: the player released the charge bar and wants to cast at the given power (0..1),
 * after holding the charge for holdTicks (a cast held too long wanders off the aim).
 */
public class CastMessage
{
    private final float power;
    private final InteractionHand hand;
    private final int holdTicks;

    public CastMessage(float power, InteractionHand hand, int holdTicks)
    {
        this.power = power;
        this.hand = hand;
        this.holdTicks = holdTicks;
    }

    public static void encode(CastMessage msg, FriendlyByteBuf buf)
    {
        buf.writeFloat(msg.power);
        buf.writeBoolean(msg.hand == InteractionHand.OFF_HAND);
        buf.writeVarInt(Math.max(0, msg.holdTicks));
    }

    public static CastMessage decode(FriendlyByteBuf buf)
    {
        float power = buf.readFloat();
        InteractionHand hand = buf.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        int holdTicks = buf.readVarInt();
        return new CastMessage(power, hand, holdTicks);
    }

    public static void handle(CastMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        Packets.handleServer(ctx, sender -> ServerFishingManager.onCast(sender, msg.power, msg.hand, msg.holdTicks));
    }
}
