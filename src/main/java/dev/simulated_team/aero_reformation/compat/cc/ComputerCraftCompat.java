package dev.simulated_team.aero_reformation.compat.cc;

import dev.simulated_team.aero_reformation.AeroReformation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/**
 * Safe entry point for the optional ComputerCraft integration.
 *
 * <p>This class never references ComputerCraft classes directly. The real
 * registration lives in {@link ComputerCraftRegistration}, which is only
 * loaded (via reflection) when ComputerCraft is present at runtime — so the
 * rest of the mod runs fine without it.
 */
public final class ComputerCraftCompat {
    /** Whether ComputerCraft is loaded on the classpath. */
    public static final boolean CC_LOADED;

    static {
        boolean loaded = false;
        try {
            loaded = ModList.get().isLoaded("computercraft");
        } catch (Exception ignored) {}
        CC_LOADED = loaded;
    }

    private ComputerCraftCompat() {}

    /**
     * Register the ComputerCraft peripheral providers if the mod is present.
     * Safe to call unconditionally from the mod constructor.
     */
    public static void registerIfPresent(IEventBus modEventBus) {
        if (!CC_LOADED) return;
        try {
            Class<?> registration = Class.forName(
                    "dev.simulated_team.aero_reformation.compat.cc.ComputerCraftRegistration");
            registration.getMethod("register", IEventBus.class).invoke(null, modEventBus);
            AeroReformation.LOGGER.info("[ComputerCraft] Registered Aero Reformation peripherals");
        } catch (ReflectiveOperationException e) {
            AeroReformation.LOGGER.warn("[ComputerCraft] Failed to register peripherals", e);
        }
    }
}
