package net.camacraft.nicecatch.server;

import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.camacraft.nicecatch.item.FishingNetItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * A water bucket is a hand net without the mesh ({@code bucketScoopsLikeNet}). Vanilla's bucket took any fish it
 * touched for certain, which skipped every rule the fishing net has and let the rare fish be bucketed outright. Now
 * a bucket on a free-swimming fish rolls the net's odds ({@link FishingNetItem#scoopChance}: the net's base chance,
 * the fish's weight, whether it saw you coming, a bobber holding its attention, a scattered school); a hit is
 * vanilla's pickup (the bucket, its sound, Tactical Fishing), a miss scatters the spot like a missed sweep. A hooked
 * fish is left to vanilla (a line already holds it).
 *
 * <p>Both sides cancel vanilla's interaction and the server does the pickup itself: vanilla's client half swaps the
 * bucket and discards its copy of the fish before the server has answered, so a fish the server kept would have
 * vanished from the client's view.
 */
@Mod.EventBusSubscriber(modid = NiceCatch.MODID)
public final class BucketScoops
{
    private BucketScoops() {}

    @SubscribeEvent
    public static void onBucketAFish(PlayerInteractEvent.EntityInteract event)
    {
        if (!NiceCatchConfig.SERVER_SPEC.isLoaded() || !NiceCatchConfig.SERVER.bucketScoopsLikeNet.get()) return;
        ItemStack held = event.getItemStack();
        if (!held.is(Items.WATER_BUCKET)) return;
        if (!(event.getTarget() instanceof PathfinderMob fish) || !(fish instanceof Bucketable) || !fish.isAlive()) return;
        if (!FishBehavior.isFishLike(fish) || FishBehavior.isHooked(fish)) return;
        // ours from here: the client only swings
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) return;
        InteractionHand hand = event.getHand();
        double chance = FishingNetItem.scoopChance(player, fish, NiceCatchConfig.SERVER.netCatchChance.get());
        if (level.random.nextFloat() < chance) {
            pickUp(player, hand, fish);
            return;
        }
        // the fish slips past the rim and the spot erupts
        Vec3 at = fish.position();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.5F, 1.2F + level.random.nextFloat() * 0.3F);
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.2D, at.z, 8, 0.3D, 0.1D, 0.3D, 0.0D);
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        FishBehavior.scatter(fish, at, cfg.scatterDurationTicks.get());
        FishBehavior.scatterAround(level, at, cfg.scatterRadius.get(), 0.8F, fish, FishBehavior.LIGHT_SCARE_COOLDOWN);
    }

    /** Vanilla's pickup (bucket, sound, filled-bucket trigger, the fish gone) for any bucketable fish. */
    @SuppressWarnings("unchecked")
    private static <T extends LivingEntity & Bucketable> Optional<InteractionResult> pickUp(Player player, InteractionHand hand, PathfinderMob fish)
    {
        return Bucketable.bucketMobPickup(player, hand, (T) fish);
    }
}
