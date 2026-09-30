package net.camacraft.nicecatch.client;

import net.camacraft.camafy.client.anim.Blink;
import net.camacraft.camafy.client.charge.ChargeBar;
import net.camacraft.camafy.client.gui.hud.HudBar;
import net.camacraft.camafy.client.gui.hud.HudHint;
import net.camacraft.camafy.client.gui.hud.XpBarSlot;
import net.camacraft.camafy.util.color.Argb;
import net.camacraft.nicecatch.NiceCatch;
import net.camacraft.nicecatch.NiceCatchConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the fishing bar in the XP bar's slot (the way the horse jump bar replaces it):
 * a cyan oscillating bar while charging a cast, a flashing red bar while a fish is biting,
 * and a green-to-red (by line tension) progress bar during the fight. The slot is claimed
 * through Camafy, which draws one mod's bar per frame.
 */
public class FishingBarOverlay
{
    /** Above Better Toss's throw bar: a cast or a fight in progress wins the slot. */
    private static final int SLOT_PRIORITY = 200;
    /** Flashing hints hide for the second half of every 12 ticks. */
    private static final Blink HINT_FLASH = new Blink(12, 6);

    /** Claims the XP bar's slot; called once at client setup. */
    public static void register()
    {
        XpBarSlot.claim(ResourceLocation.fromNamespaceAndPath(NiceCatch.MODID, "fishing_bar"), SLOT_PRIORITY,
                new XpBarSlot.Claim()
                {
                    @Override
                    public boolean active()
                    {
                        return ClientFishing.phase() != ClientFishing.Phase.IDLE || ClientFishing.celebrateTicks() > 0;
                    }

                    @Override
                    public void render(GuiGraphics graphics, int x, int y, float partialTick)
                    {
                        FishingBarOverlay.render(graphics, x, y, partialTick);
                    }
                });
    }

    /** {@code (x, y)} is the XP bar's top-left. */
    public static void render(GuiGraphics graphics, int x, int y, float partialTick)
    {
        switch (ClientFishing.phase()) {
            case CHARGING -> {
                // Camafy's shared charge bar: the same sweep and look as Better Toss's throws.
                ChargeBar.draw(graphics, x, y, ClientFishing.chargeValue(partialTick), 0xFF3CB4E7);
                drawHint(graphics, "nicecatch.hint.charge", 0xFF9BDCF5, false);
            }
            case BITE -> {
                HudBar.background(graphics, x, y, HudBar.Style.XP_BEVEL);
                boolean flashOn = (ClientFishing.biteTicks() / 4) % 2 == 0;
                if (flashOn) {
                    HudBar.fill(graphics, x, y, 1.0F, ClientFishing.isEntityBite() ? 0xFFE03434 : 0xFFE8C33A,
                            HudBar.Style.XP_BEVEL);
                }
                // A directional bite tells the player which way to yank; the hint flashes on its
                // own cadence (not the shared flash gate) so the direction is never off-screen long.
                String key = !ClientFishing.isEntityBite() ? "nicecatch.hint.loot_bite"
                        : switch (ClientFishing.biteDirection()) {
                            case 1 -> "nicecatch.hint.bite_left";
                            case 2 -> "nicecatch.hint.bite_right";
                            default -> "nicecatch.hint.bite";
                        };
                drawHint(graphics, key, 0xFFFF6A5A, ClientFishing.biteDirection() == 0);
            }
            case REEL -> {
                boolean item = ClientFishing.isReelItem();
                int color = item ? 0xFFC79A4E : 0xFF5FBFE0; // amber while reeling an item, cyan for empty line
                HudBar.draw(graphics, x, y, ClientFishing.shownProgress(), color, HudBar.Style.XP_BEVEL);
                drawHint(graphics, item ? "nicecatch.hint.reel_item" : "nicecatch.hint.reel_empty", color, false);
            }
            case FIGHT -> {
                float t = (float) (ClientFishing.fightTicks() + partialTick);
                if (ClientFishing.isFishRunning()) {
                    x += HudBar.jitterFast(t, 1.9F, 1.5F);
                }
                HudBar.background(graphics, x, y, HudBar.Style.XP_BEVEL);
                int color = tensionColor(ClientFishing.tension());
                // High tension warning: flash the fill toward red.
                if (ClientFishing.tension() > 0.8F && (ClientFishing.fightTicks() / 3) % 2 == 0) {
                    color = 0xFFFF2020;
                }
                HudBar.fill(graphics, x, y, ClientFishing.shownProgress(), color, HudBar.Style.XP_BEVEL);

                // Lift-reactive coaching: the moment the lift registers, hints that asked
                // for it turn green — instant confirmation the input is landing. (The rod
                // visibly rising and a soft whoosh carry the same signal.)
                boolean lifting = ClientFishing.liftAnim() > 0.35F;

                if (ClientFishing.chainNoticeTicks() > 0) {
                    // A chain bite is the fight's biggest moment — it owns the hint slot
                    // for a few seconds. (It used to go to the vanilla actionbar, which
                    // draws at this exact height and overlapped these hints into mush.)
                    drawHint(graphics, "nicecatch.chain", 0xFFFFB03A, true);
                } else if (ClientFishing.tension() > 0.8F) {
                    drawHint(graphics, "nicecatch.hint.tension", 0xFFFF4040, true);
                } else if (ClientFishing.dartDirection() != 0) {
                    // A dart demands its counter-pull NOW; it outranks every calm hint.
                    drawHint(graphics, ClientFishing.dartDirection() == 1
                            ? "nicecatch.hint.dart_left" : "nicecatch.hint.dart_right", 0xFFF08A3A, true);
                } else if (ClientFishing.fatigue() > 0.85F) {
                    drawHint(graphics, "nicecatch.hint.tired", 0xFF7EE0C8, false);
                } else {
                    // Coach the response to whatever the fish is doing right now.
                    switch (ClientFishing.fightPhase()) {
                        case HOLD -> drawHint(graphics, "nicecatch.hint.phase.hold", 0xFFB6E8A0, false);
                        case SWEEP -> drawHint(graphics, "nicecatch.hint.phase.sweep", 0xFFB6E8A0, false);
                        case PULL -> drawHint(graphics, "nicecatch.hint.run",
                                lifting ? 0xFF7EE044 : 0xFFE8C33A, false);
                        case SOUND -> drawHint(graphics, "nicecatch.hint.phase.sound",
                                lifting ? 0xFF7EE044 : 0xFFE8C33A, false);
                        case CHARGE -> drawHint(graphics, "nicecatch.hint.phase.charge", 0xFFF08A3A, true);
                    }
                }
            }
            default -> {
                if (ClientFishing.celebrateTicks() > 0) {
                    HudBar.draw(graphics, x, y, 1.0F, 0xFF7EE044, HudBar.Style.XP_BEVEL);
                    // No hint here: the server's actionbar line (name, weight, traits) owns
                    // this screen spot during the celebration.
                }
            }
        }
    }

    /**
     * One-line coaching text so nobody has to guess the controls. Drawn where the vanilla
     * actionbar text lives (well above the hotbar) so it never overlaps hearts or hunger.
     */
    private static void drawHint(GuiGraphics graphics, String key, int color, boolean flash)
    {
        if (!NiceCatchConfig.CLIENT.showHints.get()) return;
        Component text = Component.translatable(key);
        if (flash) {
            HudHint.draw(graphics, text, color, HINT_FLASH, ClientFishing.biteTicks() + ClientFishing.fightTicks());
        } else {
            HudHint.draw(graphics, text, color);
        }
    }

    /** Lime when relaxed, gold at half tension, red near the snapping point. */
    private static int tensionColor(float tension)
    {
        if (tension < 0.5F) {
            return Argb.lerpRgb(tension * 2.0F, 0xFF7EE044, 0xFFE8C33A);
        }
        return Argb.lerpRgb((tension - 0.5F) * 2.0F, 0xFFE8C33A, 0xFFE03434);
    }
}
