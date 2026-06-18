package dev.simulated_team.aero_reformation.registrate;

import dev.simulated_team.aero_reformation.content.items.mushroom_shell.MushroomShellBlock;
import dev.simulated_team.aero_reformation.content.items.mushroom_shell.MushroomShellItem;
import dev.simulated_team.aero_reformation.content.items.mushroom_shell.MushroomShellProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;

import java.util.function.Supplier;

import static dev.simulated_team.aero_reformation.registrate.AeroBlocks.*;

/**
 * All CBC-dependent registrations, isolated in a separate class
 * to avoid classloading CBC types when the mod is absent.
 */
@SuppressWarnings("unchecked")
public class AeroCBCBlocks {

    public static final Supplier<EntityType<MushroomShellProjectile>> MUSHROOM_SHELL_ENTITY;
    public static final Supplier<MushroomShellBlock> MUSHROOM_SHELL;
    public static final Supplier<BlockItem> MUSHROOM_SHELL_ITEM;
    public static final Supplier<BlockEntityType<FuzedBlockEntity>> MUSHROOM_SHELL_BE;

    static {
        MUSHROOM_SHELL_ENTITY = ENTITY_TYPES.register("mushroom_shell", () ->
                EntityType.Builder.<MushroomShellProjectile>of(
                                MushroomShellProjectile::new, MobCategory.MISC)
                        .sized(0.5F, 0.5F)
                        .clientTrackingRange(16)
                        .updateInterval(3)
                        .fireImmune()
                        .build("aero_reformation:mushroom_shell"));

        MUSHROOM_SHELL = BLOCKS.register("mushroom_shell", () -> new MushroomShellBlock(
                BlockBehaviour.Properties.of()
                        .strength(1.5f, 3.0f)
                        .noOcclusion()
                        .sound(SoundType.STONE)
        ));

        MUSHROOM_SHELL_ITEM = ITEMS.register("mushroom_shell", () -> new MushroomShellItem(
                MUSHROOM_SHELL.get(), new Item.Properties().stacksTo(1)
        ));

        MUSHROOM_SHELL_BE = BLOCK_ENTITY_TYPES.register("mushroom_shell",
                () -> {
                    var holder = new BlockEntityType<?>[1];
                    holder[0] = BlockEntityType.Builder.of(
                            (pos, state) -> new FuzedBlockEntity(holder[0], pos, state),
                            MUSHROOM_SHELL.get()
                    ).build(null);
                    return (BlockEntityType<FuzedBlockEntity>) (Object) holder[0];
                });
    }

    /** Called from AeroBlocks static block (triggers static init). */
    public static void init() {}

    public static void registerHandlers(net.neoforged.bus.api.IEventBus modEventBus) {
        modEventBus.addListener((net.neoforged.neoforge.registries.RegisterEvent event) -> {
            if (event.getRegistryKey() == net.minecraft.core.registries.Registries.ENTITY_TYPE) {
                rbasamoyai.createbigcannons.munitions.config.MunitionPropertiesHandler.registerProjectileHandler(
                        MUSHROOM_SHELL_ENTITY.get(),
                        rbasamoyai.createbigcannons.index.CBCMunitionPropertiesHandlers.COMMON_SHELL_BIG_CANNON_PROJECTILE);
            }
        });
    }

    /** Register renderers. Call on client bus only. */
    public static void registerRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(MUSHROOM_SHELL_ENTITY.get(),
                rbasamoyai.createbigcannons.munitions.big_cannon.BigCannonProjectileRenderer::new);
        e.registerBlockEntityRenderer(MUSHROOM_SHELL_BE.get(),
                dev.simulated_team.aero_reformation.content.items.mushroom_shell.MushroomShellRenderer::new);
    }
}
