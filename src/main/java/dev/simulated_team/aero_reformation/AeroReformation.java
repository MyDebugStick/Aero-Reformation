package dev.simulated_team.aero_reformation;

import com.mojang.logging.LogUtils;
import dev.simulated_team.aero_reformation.config.AeroReformationConfig;
import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderCompassNavigationTarget;
import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderCompassRecipe;
import dev.simulated_team.aero_reformation.content.blocks.power.PowerConfigPayload;
import dev.simulated_team.aero_reformation.content.blocks.power.ToggleCameraLockPayload;
import dev.simulated_team.aero_reformation.content.blocks.power.ToggleRollLockPayload;
import dev.simulated_team.aero_reformation.content.blocks.power.SyncSignalPayload;
import dev.simulated_team.aero_reformation.content.blocks.power.PowerKeyBindings;
import dev.simulated_team.aero_reformation.content.blocks.power.ToggleRedstonePayload;
import dev.simulated_team.aero_reformation.network.EnderCompassSyncPacket;
import dev.simulated_team.aero_reformation.network.GoggleBindPacket;
import dev.simulated_team.aero_reformation.network.GoggleMonitorSyncPacket;
import dev.simulated_team.aero_reformation.network.LoadstoneSyncPacket;
import dev.simulated_team.aero_reformation.network.SensorAgencyConfigPacket;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import dev.simulated_team.simulated.index.SimRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

@Mod(AeroReformation.MODID)
public class AeroReformation {
    public static final String MODID = "aero_reformation";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AeroReformation(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, AeroReformationConfig.CONFIG_SPEC);
        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading e) -> {
            if (e.getConfig().getSpec() == AeroReformationConfig.CONFIG_SPEC) AeroReformationConfig.refresh();
        });
        modEventBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading e) -> {
            if (e.getConfig().getSpec() == AeroReformationConfig.CONFIG_SPEC) AeroReformationConfig.refresh();
        });

        // Register blocks, items, block entities & creative tab
        AeroBlocks.BLOCKS.register(modEventBus);
        AeroBlocks.ITEMS.register(modEventBus);
        AeroBlocks.BLOCK_ENTITY_TYPES.register(modEventBus);
        AeroBlocks.MENU_TYPES.register(modEventBus);
        AeroBlocks.CREATIVE_TAB.register(modEventBus);
        AeroBlocks.ENTITY_TYPES.register(modEventBus);
        AeroDataComponents.REGISTER.register(modEventBus);

        // Key bindings
        modEventBus.addListener(PowerKeyBindings::register);

        // Register recipe serializer
        var recipeSerializers = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
        recipeSerializers.register("ender_compass_channel", () -> EnderCompassRecipe.Serializer.INSTANCE);
        recipeSerializers.register(modEventBus);

        // Register network packets
        modEventBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) -> {
            var registrar = event.registrar(MODID);
            registrar.playBidirectional(SensorAgencyConfigPacket.TYPE, SensorAgencyConfigPacket.STREAM_CODEC,
                    SensorAgencyConfigPacket::handleBidirectional);
            registrar.playToServer(EnderCompassSyncPacket.TYPE, EnderCompassSyncPacket.STREAM_CODEC,
                    EnderCompassSyncPacket::handle);
            registrar.playToServer(GoggleBindPacket.TYPE, GoggleBindPacket.STREAM_CODEC,
                    GoggleBindPacket::handle);
            registrar.playToClient(GoggleMonitorSyncPacket.TYPE, GoggleMonitorSyncPacket.STREAM_CODEC,
                    GoggleMonitorSyncPacket::handle);
            registrar.playToClient(LoadstoneSyncPacket.TYPE, LoadstoneSyncPacket.STREAM_CODEC,
                    LoadstoneSyncPacket::handle);
            registrar.playToServer(ToggleRedstonePayload.TYPE, ToggleRedstonePayload.STREAM_CODEC,
                    ToggleRedstonePayload::handle);
            registrar.playToServer(ToggleCameraLockPayload.TYPE, ToggleCameraLockPayload.STREAM_CODEC,
                    ToggleCameraLockPayload::handle);
            registrar.playToServer(ToggleRollLockPayload.TYPE, ToggleRollLockPayload.STREAM_CODEC,
                    ToggleRollLockPayload::handle);

            registrar.playToServer(PowerConfigPayload.TYPE, PowerConfigPayload.STREAM_CODEC,
                    PowerConfigPayload::handle);
            registrar.playToServer(SyncSignalPayload.TYPE, SyncSignalPayload.STREAM_CODEC,
                    SyncSignalPayload::handle);
        });

        // Register NavigationTarget into Simulated's existing registry
        modEventBus.addListener((RegisterEvent event) -> {
            if (event.getRegistryKey().equals(SimRegistries.Keys.NAVIGATION_TARGET)) {
                event.register(SimRegistries.Keys.NAVIGATION_TARGET,
                        ResourceLocation.fromNamespaceAndPath(MODID, "ender_compass"),
                        () -> EnderCompassNavigationTarget.INSTANCE);
            }
        });

        // Register renderers (client only via deferred)
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(AeroBlocks.REDSTONE_SPRING_BE.get(),
                    dev.simulated_team.aero_reformation.content.blocks.redstone_spring.RedstoneSpringRenderer::new);
            e.registerBlockEntityRenderer(AeroBlocks.ELECTRIC_LOADSTONE_BE.get(),
                    dev.simulated_team.aero_reformation.content.blocks.electric_loadstone.ElectricLoadstoneRenderer::new);
            e.registerEntityRenderer(AeroBlocks.SEAT_ENTITY_TYPE.get(),
                    dev.simulated_team.aero_reformation.content.blocks.power.SeatRenderer::new);
        });

        // Register screen
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent e) -> {
            e.register(AeroBlocks.SENSOR_AGENCY_MENU.get(),
                    dev.simulated_team.aero_reformation.content.blocks.sensor_agency.SensorAgencyScreen::new);
        });

        LOGGER.info("Aero Reformation loaded! Features: nav_inverted, swivel_stiffness, swivel_swap, gimbal_inverted, levitite_mining, redstone_spring");
    }
}
