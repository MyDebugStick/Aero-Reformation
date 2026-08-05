package dev.simulated_team.aero_reformation.compat.cc;

import com.mojang.logging.LogUtils;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;

/**
 * Actual ComputerCraft registration. This class (and everything it references
 * from {@code dan200.computercraft.*}) is only ever loaded when ComputerCraft
 * is present at runtime — {@link ComputerCraftCompat} loads it reflectively.
 *
 * <p>Every entry point is wrapped in a try/catch: if the installed ComputerCraft
 * version is incompatible, peripherals are simply skipped and the rest of the
 * mod (and the game) keeps working normally.
 */
public final class ComputerCraftRegistration {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ComputerCraftRegistration() {}

    /** Called reflectively by {@link ComputerCraftCompat#registerIfPresent(IEventBus)}. */
    public static void register(IEventBus modEventBus) {
        try {
            modEventBus.addListener(ComputerCraftRegistration::registerCapabilities);
        } catch (Throwable t) {
            LOGGER.warn("[ComputerCraft] Failed to attach capability listener, peripherals disabled", t);
        }
    }

    /** Expose the RCS thruster and power block as CC peripherals via a BlockCapability. */
    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        try {
            // RCS thruster -> "aero_rcs" peripheral
            event.registerBlockEntity(PeripheralCapability.get(), AeroBlocks.RCS_THRUSTER_BE.get(),
                    (be, side) -> new RcsPeripheral(be));
            // Power block -> "aero_power" peripheral
            event.registerBlockEntity(PeripheralCapability.get(), AeroBlocks.POWER_BE.get(),
                    (be, side) -> new PowerPeripheral(be));
            // Guidance warhead -> "aero_warhead" peripheral
            event.registerBlockEntity(PeripheralCapability.get(), AeroBlocks.GUIDANCE_WARHEAD_BE.get(),
                    (be, side) -> new GuidanceWarheadPeripheral(be));
        } catch (Throwable t) {
            LOGGER.warn("[ComputerCraft] Failed to register peripherals, CC integration disabled", t);
        }
    }
}
