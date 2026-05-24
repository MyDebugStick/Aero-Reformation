package dev.simulated_team.aero_reformation.registrate;

import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.content.blocks.redstone_spring.RedstoneSpringBlock;
import dev.simulated_team.aero_reformation.content.blocks.redstone_spring.RedstoneSpringBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class AeroBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, AeroReformation.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, AeroReformation.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AeroReformation.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AeroReformation.MODID);

    // ==================== Redstone Spring ====================

    public static final Supplier<RedstoneSpringBlock> REDSTONE_SPRING =
            BLOCKS.register("redstone_spring", () -> new RedstoneSpringBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.5f)
                            .noOcclusion()
                            .requiresCorrectToolForDrops()
            ));

    public static final Supplier<BlockEntityType<RedstoneSpringBlockEntity>> REDSTONE_SPRING_BE =
            BLOCK_ENTITY_TYPES.register("redstone_spring",
                    () -> BlockEntityType.Builder.of(RedstoneSpringBlockEntity::new, REDSTONE_SPRING.get())
                            .build(null));

    public static final Supplier<BlockItem> REDSTONE_SPRING_ITEM =
            ITEMS.register("redstone_spring", () -> new BlockItem(
                    REDSTONE_SPRING.get(), new Item.Properties()
            ));

    // ==================== Ender Compass ====================

    public static final Supplier<Item> ENDER_COMPASS =
            ITEMS.register("ender_compass", () -> new dev.simulated_team.aero_reformation.content.items.ender_compass.EnderCompassItem(
                    new Item.Properties().stacksTo(1)
                            .component(dev.simulated_team.simulated.index.SimDataComponents.TARGET,
                                    dev.simulated_team.aero_reformation.content.items.ender_compass.EnderCompassNavigationTarget.INSTANCE)
            ));

    // ==================== Directional Synchronizer ====================

    public static final Supplier<Block> DIRECTIONAL_SYNCHRONIZER_MASTER =
            BLOCKS.register("directional_synchronizer_master", () ->
                    new dev.simulated_team.aero_reformation.content.blocks.directional_synchronizer.DirectionalSynchronizerMasterBlock(
                    BlockBehaviour.Properties.of().strength(1.5f).noOcclusion()
                            .isRedstoneConductor((s, l, p) -> false)
                            .isViewBlocking((s, l, p) -> false)
            ));

    public static final Supplier<BlockItem> DIRECTIONAL_SYNCHRONIZER_MASTER_ITEM =
            ITEMS.register("directional_synchronizer_master", () -> new BlockItem(
                    DIRECTIONAL_SYNCHRONIZER_MASTER.get(), new Item.Properties()
            ));

    public static final Supplier<Block> DIRECTIONAL_SYNCHRONIZER_SLAVE =
            BLOCKS.register("directional_synchronizer_slave", () ->
                    new dev.simulated_team.aero_reformation.content.blocks.directional_synchronizer.DirectionalSynchronizerSlaveBlock(
                    BlockBehaviour.Properties.of().strength(1.5f).noOcclusion()
                            .isRedstoneConductor((s, l, p) -> false)
                            .isViewBlocking((s, l, p) -> false)
            ));

    public static final Supplier<BlockItem> DIRECTIONAL_SYNCHRONIZER_SLAVE_ITEM =
            ITEMS.register("directional_synchronizer_slave", () ->
                    new dev.simulated_team.aero_reformation.content.items.directional_synchronizer.DirectionalSynchronizerSlaveBlockItem(
                    new Item.Properties()
            ));

    public static final Supplier<BlockEntityType<dev.simulated_team.aero_reformation.content.blocks.directional_synchronizer.DirectionalSynchronizerSlaveBlockEntity>> DIRECTIONAL_SYNCHRONIZER_SLAVE_BE =
            BLOCK_ENTITY_TYPES.register("directional_synchronizer_slave",
                    () -> BlockEntityType.Builder.of(
                            dev.simulated_team.aero_reformation.content.blocks.directional_synchronizer.DirectionalSynchronizerSlaveBlockEntity::new,
                            DIRECTIONAL_SYNCHRONIZER_SLAVE.get()
                    ).build(null));

    public static final Supplier<BlockEntityType<dev.simulated_team.aero_reformation.content.blocks.directional_synchronizer.DirectionalSynchronizerMasterBlockEntity>> DIRECTIONAL_SYNCHRONIZER_MASTER_BE =
            BLOCK_ENTITY_TYPES.register("directional_synchronizer_master",
                    () -> BlockEntityType.Builder.of(
                            dev.simulated_team.aero_reformation.content.blocks.directional_synchronizer.DirectionalSynchronizerMasterBlockEntity::new,
                            DIRECTIONAL_SYNCHRONIZER_MASTER.get()
                    ).build(null));

    // ==================== Creative Tab ====================

    public static final Supplier<CreativeModeTab> AERO_REFORMATION_TAB = CREATIVE_TAB.register(
            "aero_reformation_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aero_reformation"))
                    .icon(() -> new ItemStack(REDSTONE_SPRING_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(REDSTONE_SPRING_ITEM.get());
                        output.accept(ENDER_COMPASS.get());
                        output.accept(DIRECTIONAL_SYNCHRONIZER_MASTER_ITEM.get());
                        output.accept(DIRECTIONAL_SYNCHRONIZER_SLAVE_ITEM.get());
                    })
                    .build()
    );
}
