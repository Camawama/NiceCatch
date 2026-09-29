package net.camacraft.nicecatch.network;

import net.camacraft.camafy.network.Packets;
import net.camacraft.nicecatch.client.ClientFishing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the fight is over. */
public class FightEndMessage
{
    public static final byte CAUGHT = 0;
    public static final byte ESCAPED = 1;
    public static final byte SNAPPED = 2;

    private final byte result;

    public FightEndMessage(byte result)
    {
        this.result = result;
    }

    public static void encode(FightEndMessage msg, FriendlyByteBuf buf)
    {
        buf.writeByte(msg.result);
    }

    public static FightEndMessage decode(FriendlyByteBuf buf)
    {
        return new FightEndMessage(buf.readByte());
    }

    public static void handle(FightEndMessage msg, Supplier<NetworkEvent.Context> ctx)
    {
        Packets.handleClient(ctx, () -> () -> ClientFishing.handleFightEnd(msg.result));
    }
}
