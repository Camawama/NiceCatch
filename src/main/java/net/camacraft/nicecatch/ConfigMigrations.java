package net.camacraft.nicecatch;

import net.camacraft.camafy.config.ConfigMigrator;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Kills the oldest foot-gun in the project: Forge server configs live per-world, and an
 * existing world keeps whatever value a key had when it was first written — so every time
 * a shipped default was retuned, old worlds silently played on stale numbers until someone
 * hand-edited a toml. Now each config file carries a version stamp; when a file from an
 * older generation loads, any value STILL SITTING ON ITS OLD DEFAULT is moved to the new
 * one. A value the user customized never matches the old default, so it is never touched.
 * (Camafy's ConfigMigrator does the loading, the check and the stamp; this is just the table.)
 */
public final class ConfigMigrations
{
    private ConfigMigrations() {}

    public static void register(IEventBus modBus)
    {
        NiceCatchConfig.Server cfg = NiceCatchConfig.SERVER;
        ConfigMigrator.register(modBus, NiceCatchConfig.SERVER_SPEC, cfg.configVersion,
                NiceCatchConfig.Server.CURRENT_CONFIG_VERSION, version -> {
                    if (version < 2) {
                        // Every default retuned since these keys first shipped.
                        ConfigMigrator.move(cfg.biteChancePerSecond, 0.10D, 0.22D);
                        ConfigMigrator.move(cfg.scatterOnHookChance, 0.35D, 0.2D);
                        ConfigMigrator.move(cfg.dartWindowTicks, 25, 35);
                        ConfigMigrator.move(cfg.netCooldownTicks, 40, 60);
                        ConfigMigrator.move(cfg.schoolSpawnWeightMultiplier, 1.0D, 2.5D);
                        ConfigMigrator.move(cfg.soundPhaseMinWeightLbs, 25.0D, 5.0D);
                        ConfigMigrator.move(cfg.tensionPerRevolutionRun, 0.30D, 0.24D);
                        ConfigMigrator.move(cfg.tensionRecoveryPerTick, 0.012D, 0.016D);
                    }
                });
    }
}
