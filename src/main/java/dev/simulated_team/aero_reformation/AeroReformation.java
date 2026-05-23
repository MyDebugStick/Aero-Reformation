package dev.simulated_team.aero_reformation;

import com.mojang.logging.LogUtils;
import dev.simulated_team.aero_reformation.config.AeroReformationConfig;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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
        AeroBlocks.CREATIVE_TAB.register(modEventBus);

        // Register renderers (client only via deferred)
        modEventBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(AeroBlocks.REDSTONE_SPRING_BE.get(),
                    dev.simulated_team.aero_reformation.content.blocks.redstone_spring.RedstoneSpringRenderer::new);
        });

        LOGGER.info("Aero Reformation loaded! Features: nav_inverted, swivel_stiffness, swivel_swap, gimbal_inverted, levitite_mining, redstone_spring");
    }
}
