package net.camacraft.nicecatch.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.camacraft.camafy.client.input.Keys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The mod's keybinds. ROD_CONTROL is the "hands on the reel" hold — everything the mod
 * used to read off a held right-click (charging a cast, gripping through a bite, reeling,
 * fighting) reads this instead, and it defaults to the right mouse button so nothing
 * changes out of the box. CUT_LINE is the give-up key: snip the line, lose the bobber,
 * walk away from a fish that is simply too much — without fumbling for another hotbar slot.
 *
 * The control key is polled from the device (Camafy's Keys) rather than through
 * KeyMapping.isDown(): its default shares the physical button with vanilla's use key, and
 * stacked mappings on one key do not reliably both receive state updates. Raw polling
 * always tells the truth.
 */
public final class RodControls
{
    public static final KeyMapping ROD_CONTROL = new KeyMapping(
            "key.nicecatch.rod_control", KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, "key.categories.nicecatch");

    // Z, not V: V is Grapple Unbound's hook config and half of Full Stop's F3+V, and No Conflict
    // unbinds clashing defaults — a V cut-line would ship dead in the modpack.
    public static final KeyMapping CUT_LINE = new KeyMapping(
            "key.nicecatch.cut_line", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.nicecatch");

    private RodControls() {}

    /** True while the rod-control key is physically held and no screen is open. */
    public static boolean controlDown(Minecraft mc)
    {
        if (mc.screen != null || mc.player == null) return false;
        if (!Keys.isBound(ROD_CONTROL)) {
            return mc.options.keyUse.isDown(); // unbound: fall back to vanilla use
        }
        // A scancode can't be polled from GLFW; Keys trusts the mapping's own state for those.
        return Keys.isPhysicallyDown(ROD_CONTROL);
    }

    /**
     * Whether rod control shares the vanilla use button (the default). When it does, cast
     * charging keeps riding the intercepted right-click (so the aim-at-nothing rule still
     * lets doors and chests work); rebound elsewhere, charging is started by the key itself.
     */
    public static boolean controlIsUseButton(Minecraft mc)
    {
        InputConstants.Key key = Keys.physicalKey(ROD_CONTROL);
        return key.equals(InputConstants.UNKNOWN) || key.equals(Keys.physicalKey(mc.options.keyUse));
    }
}
