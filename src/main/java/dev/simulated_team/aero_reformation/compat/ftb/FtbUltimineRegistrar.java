package dev.simulated_team.aero_reformation.compat.ftb;

import dev.ftb.mods.ftbultimine.api.blockselection.RegisterBlockSelectionHandlerEvent;
import dev.ftb.mods.ftbultimine.api.rightclick.RegisterRightClickHandlerEvent;

/**
 * Loaded via reflection only when FTB Ultimine is confirmed present.
 * Avoids NoClassDefFoundError in the main mod class.
 */
public class FtbUltimineRegistrar {
    public static void register() {
        RegisterBlockSelectionHandlerEvent.REGISTER.register(
                d -> d.registerHandler(new CopycatSelectionHandler()));
        RegisterRightClickHandlerEvent.REGISTER.register(
                d -> d.registerHandler(new CopycatRightClickHandler()));
    }
}
