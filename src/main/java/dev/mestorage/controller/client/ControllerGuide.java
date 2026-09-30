package dev.mestorage.controller.client;

import dev.mestorage.controller.MEStorageController;
import guideme.GuidesCommon;
import guideme.PageAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** The controller article extends AE2's existing GuideME guide through resource-pack discovery. */
public final class ControllerGuide {
    public static final ResourceLocation GUIDE_ID = new ResourceLocation("ae2", "guide");
    public static final ResourceLocation PAGE_ID = new ResourceLocation(MEStorageController.ID, "controller.md");

    private ControllerGuide() {}

    /**
     * Uses the same navigation as GuideME's native item-tooltip hold-G action.
     * GuideME remembers the current screen and restores it on close. Do not close the
     * player's container here: the server menu and carried stack stay synchronized
     * while its screen is temporarily replaced by the guide.
     */
    public static void open() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        GuidesCommon.openGuide(minecraft.player, GUIDE_ID, new PageAnchor(PAGE_ID, null));
    }
}
