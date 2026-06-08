package dev.simulated_team.aero_reformation.registrate;

import dev.simulated_team.aero_reformation.AeroReformation;
import dev.simulated_team.aero_reformation.content.blocks.redstone_spring.RedstoneSpringBlock;
import dev.simulated_team.aero_reformation.content.blocks.redstone_spring.RedstoneSpringBlockEntity;
import dev.simulated_team.aero_reformation.content.blocks.sensor_agency.SensorAgencyBlock;
import dev.simulated_team.aero_reformation.content.blocks.sensor_agency.SensorAgencyBlockEntity;
import dev.simulated_team.aero_reformation.content.blocks.sensor_agency.SensorAgencyBlockItem;
import dev.simulated_team.aero_reformation.content.blocks.sensor_agency.SensorAgencyMenu;
import dev.simulated_team.aero_reformation.content.blocks.electric_loadstone.ElectricLoadstoneBlock;
import dev.simulated_team.aero_reformation.content.blocks.electric_loadstone.ElectricLoadstoneBlockEntity;
import dev.simulated_team.aero_reformation.content.blocks.rcs_thruster.RcsThrusterBlock;
import dev.simulated_team.aero_reformation.content.blocks.rcs_thruster.RcsThrusterBlockEntity;
import dev.simulated_team.aero_reformation.content.blocks.rcs_thruster.RcsThrusterBlockItem;
import dev.simulated_team.aero_reformation.content.blocks.power.PowerBlock;
import dev.simulated_team.aero_reformation.content.blocks.power.PowerBlockEntity;
import dev.simulated_team.aero_reformation.content.blocks.power.PilotSeatBlock;
import dev.simulated_team.aero_reformation.content.blocks.power.CreateSeatBlock;
import dev.simulated_team.aero_reformation.content.blocks.power.EndRodSeatBlock;
import dev.simulated_team.aero_reformation.content.blocks.power.SeatEntity;
import dev.simulated_team.aero_reformation.content.blocks.physics_anchor.AnchorMarkerEntity;
import dev.simulated_team.aero_reformation.content.blocks.physics_anchor.PhysicsAnchorBlock;
import dev.simulated_team.aero_reformation.content.blocks.physics_anchor.PhysicsAnchorBlockEntity;
import dev.simulated_team.aero_reformation.content.blocks.gravity_crystal.GravityCrystalBlock;
import dev.simulated_team.aero_reformation.content.blocks.gravity_crystal.GravityCrystalBlockEntity;
import dev.simulated_team.aero_reformation.content.blocks.gravity_crystal.GravityCrystalBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.List;

public class AeroBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, AeroReformation.MODID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, AeroReformation.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AeroReformation.MODID);

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, AeroReformation.MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AeroReformation.MODID);

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AeroReformation.MODID);

    // ==================== Seat Entity ====================

    public static final Supplier<EntityType<SeatEntity>> SEAT_ENTITY_TYPE =
            ENTITY_TYPES.register("seat", () ->
                    EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                            .sized(0.0F, 0.0F)
                            .clientTrackingRange(10)
                            .build("aero_reformation:seat"));

    public static final Supplier<EntityType<AnchorMarkerEntity>> ANCHOR_MARKER =
            ENTITY_TYPES.register("anchor_marker", () ->
                    EntityType.Builder.<AnchorMarkerEntity>of(AnchorMarkerEntity::new, MobCategory.MISC)
                            .sized(0.0F, 0.0F)
                            .clientTrackingRange(512)
                            .updateInterval(1)
                            .build("aero_reformation:anchor_marker"));

    // ==================== Redstone Spring ====================

    public static final Supplier<RedstoneSpringBlock> REDSTONE_SPRING =
            BLOCKS.register("redstone_spring", () -> new RedstoneSpringBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.5f)
                            .noOcclusion()
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

    // ==================== Electric Loadstone ====================

    public static final Supplier<ElectricLoadstoneBlock> ELECTRIC_LOADSTONE =
            BLOCKS.register("electric_loadstone", () -> new ElectricLoadstoneBlock(
                    BlockBehaviour.Properties.of()
                            .strength(2.0f)
                            .noOcclusion()
            ));

    public static final Supplier<BlockEntityType<ElectricLoadstoneBlockEntity>> ELECTRIC_LOADSTONE_BE =
            BLOCK_ENTITY_TYPES.register("electric_loadstone",
                    () -> BlockEntityType.Builder.of(ElectricLoadstoneBlockEntity::new, ELECTRIC_LOADSTONE.get())
                            .build(null));

    public static final Supplier<BlockItem> ELECTRIC_LOADSTONE_ITEM =
            ITEMS.register("electric_loadstone", () -> new BlockItem(
                    ELECTRIC_LOADSTONE.get(), new Item.Properties()
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

    // ==================== RCS Thruster ====================

    public static final Supplier<RcsThrusterBlock> RCS_THRUSTER =
            BLOCKS.register("rcs_thruster", () -> new RcsThrusterBlock(
                    BlockBehaviour.Properties.of()
                            .strength(2.0f)
                            .noOcclusion()
            ));

    public static final Supplier<BlockEntityType<RcsThrusterBlockEntity>> RCS_THRUSTER_BE =
            BLOCK_ENTITY_TYPES.register("rcs_thruster",
                    () -> BlockEntityType.Builder.of(RcsThrusterBlockEntity::new, RCS_THRUSTER.get())
                            .build(null));

    public static final Supplier<BlockItem> RCS_THRUSTER_ITEM =
            ITEMS.register("rcs_thruster", () -> new RcsThrusterBlockItem(
                    RCS_THRUSTER.get(), new Item.Properties()
            ));

    public static final Supplier<Item> INCOMPLETE_RCS_THRUSTER =
            ITEMS.register("incomplete_rcs_thruster", () -> new Item(
                    new Item.Properties().stacksTo(1)
            ));

    // ==================== Sensor Agency ====================

    public static final Supplier<SensorAgencyBlock> SENSOR_AGENCY =
            BLOCKS.register("sensor_agency", () -> new SensorAgencyBlock(
                    BlockBehaviour.Properties.of()
                            .strength(1.5f)
                            .noOcclusion()
                            .isRedstoneConductor((s, l, p) -> false)
                            .isViewBlocking((s, l, p) -> false)
            ));

    public static final Supplier<BlockEntityType<SensorAgencyBlockEntity>> SENSOR_AGENCY_BE =
            BLOCK_ENTITY_TYPES.register("sensor_agency",
                    () -> BlockEntityType.Builder.of(SensorAgencyBlockEntity::new, SENSOR_AGENCY.get())
                            .build(null));

    public static final Supplier<BlockItem> SENSOR_AGENCY_ITEM =
            ITEMS.register("sensor_agency", () -> new SensorAgencyBlockItem(
                    SENSOR_AGENCY.get(), new Item.Properties()
            ));

    public static final Supplier<MenuType<SensorAgencyMenu>> SENSOR_AGENCY_MENU =
            MENU_TYPES.register("sensor_agency", () ->
                    new MenuType<>((id, inv) -> new SensorAgencyMenu(id, inv, null,
                            new dev.simulated_team.aero_reformation.content.blocks.sensor_agency.SensorAgencyConfig()),
                            FeatureFlags.DEFAULT_FLAGS));

    // ==================== Power Seat Blocks (tag-based BE sharing) ====================
    /** Add seat blocks here — all share PowerBlockEntity and tag aero_reformation:power_seats */
    public static final List<Supplier<? extends Block>> POWER_SEAT_BLOCKS = new ArrayList<>();

    public static final Supplier<PowerBlock> POWER =
            BLOCKS.register("power", () -> {
                var b = new PowerBlock(BlockBehaviour.Properties.of()
                        .strength(2.0f)
                        .noOcclusion()
                        .isViewBlocking((s, l, p) -> false));
                POWER_SEAT_BLOCKS.add(() -> b);
                return b;
            });

    public static final Supplier<BlockItem> POWER_ITEM =
            ITEMS.register("power", () -> new BlockItem(
                    POWER.get(), new Item.Properties()
            ));

    // ==================== Pilot Seat (飞行员座椅) ====================

    public static final Supplier<PilotSeatBlock> PILOT_SEAT =
            BLOCKS.register("pilot_seat", () -> {
                var b = new PilotSeatBlock(BlockBehaviour.Properties.of()
                        .strength(2.0f)
                        .noOcclusion()
                        .isViewBlocking((s, l, p) -> false));
                POWER_SEAT_BLOCKS.add(() -> b);
                return b;
            });

    public static final Supplier<BlockItem> PILOT_SEAT_ITEM =
            ITEMS.register("pilot_seat", () -> new BlockItem(
                    PILOT_SEAT.get(), new Item.Properties()
            ));

    // ==================== Create Seat (机械动力白坐垫) ====================

    public static final Supplier<CreateSeatBlock> CREATE_SEAT =
            BLOCKS.register("create_seat", () -> {
                var b = new CreateSeatBlock(BlockBehaviour.Properties.of()
                        .strength(2.0f)
                        .noOcclusion()
                        .isViewBlocking((s, l, p) -> false));
                POWER_SEAT_BLOCKS.add(() -> b);
                return b;
            });

    public static final Supplier<BlockItem> CREATE_SEAT_ITEM =
            ITEMS.register("create_seat", () -> new BlockItem(
                    CREATE_SEAT.get(), new Item.Properties()
            ));

    // ==================== End Rod Seat (末地烛座椅) ====================

    public static final Supplier<EndRodSeatBlock> END_ROD_SEAT =
            BLOCKS.register("end_rod_seat", () -> {
                var b = new EndRodSeatBlock(BlockBehaviour.Properties.of()
                        .strength(2.0f)
                        .noOcclusion()
                        .isViewBlocking((s, l, p) -> false));
                POWER_SEAT_BLOCKS.add(() -> b);
                return b;
            });

    public static final Supplier<BlockItem> END_ROD_SEAT_ITEM =
            ITEMS.register("end_rod_seat", () -> new BlockItem(
                    END_ROD_SEAT.get(), new Item.Properties()
            ));

    public static final Supplier<BlockEntityType<PowerBlockEntity>> POWER_BE =
            BLOCK_ENTITY_TYPES.register("power",
                    () -> {
                        var blocks = POWER_SEAT_BLOCKS.stream().map(Supplier::get).toArray(Block[]::new);
                        return BlockEntityType.Builder.of(PowerBlockEntity::new, blocks).build(null);
                    });

    // ==================== Physics Anchor (物理锚点) ====================

    public static final Supplier<PhysicsAnchorBlock> PHYSICS_ANCHOR =
            BLOCKS.register("physics_anchor", () -> new PhysicsAnchorBlock(
                    BlockBehaviour.Properties.of()
                            .strength(3.0f)
                            .noOcclusion()
            ));

    public static final Supplier<BlockEntityType<PhysicsAnchorBlockEntity>> PHYSICS_ANCHOR_BE =
            BLOCK_ENTITY_TYPES.register("physics_anchor",
                    () -> BlockEntityType.Builder.of(PhysicsAnchorBlockEntity::new, PHYSICS_ANCHOR.get())
                            .build(null));

    public static final Supplier<BlockItem> PHYSICS_ANCHOR_ITEM =
            ITEMS.register("physics_anchor", () -> new BlockItem(
                    PHYSICS_ANCHOR.get(), new Item.Properties()
            ));

    // ==================== Gravity Crystal (重力水晶) ====================

    public static final Supplier<GravityCrystalBlock> GRAVITY_CRYSTAL =
            BLOCKS.register("gravity_crystal", () -> new GravityCrystalBlock(
                    BlockBehaviour.Properties.of()
                            .strength(3.0f)
                            .noOcclusion()
                            .lightLevel(s -> 8)
            ));

    public static final Supplier<BlockEntityType<GravityCrystalBlockEntity>> GRAVITY_CRYSTAL_BE =
            BLOCK_ENTITY_TYPES.register("gravity_crystal",
                    () -> BlockEntityType.Builder.of(GravityCrystalBlockEntity::new, GRAVITY_CRYSTAL.get())
                            .build(null));

    public static final Supplier<BlockItem> GRAVITY_CRYSTAL_ITEM =
            ITEMS.register("gravity_crystal", () -> new GravityCrystalBlockItem(
                    GRAVITY_CRYSTAL.get(), new Item.Properties()
            ));

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
                        output.accept(SENSOR_AGENCY_ITEM.get());
                        output.accept(ELECTRIC_LOADSTONE_ITEM.get());
                        output.accept(RCS_THRUSTER_ITEM.get());
                        output.accept(POWER_ITEM.get());
                        output.accept(PILOT_SEAT_ITEM.get());
                        output.accept(CREATE_SEAT_ITEM.get());
                        output.accept(END_ROD_SEAT_ITEM.get());
                        output.accept(PHYSICS_ANCHOR_ITEM.get());
                        output.accept(GRAVITY_CRYSTAL_ITEM.get());
                    })
                    .build()
    );
}
