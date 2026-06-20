package dev.simulated_team.aero_reformation.content.items.mushroom_shell;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import rbasamoyai.createbigcannons.index.CBCMunitionPropertiesHandlers;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonCommonShellProperties;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonFuzePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonProjectilePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.EntityDamagePropertiesComponent;

public class MushroomShellProjectile extends FuzedBigCannonProjectile {

    private static final ResourceLocation MUSHROOM_NBT =
            ResourceLocation.fromNamespaceAndPath("aero_reformation", "mushroom1_1");

    public MushroomShellProjectile(EntityType<? extends MushroomShellProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected void detonate(Position position) {
        if (!(this.level() instanceof ServerLevel sl) || sl.isClientSide()) return;

        Direction facing = this.getDirection();
        StructureTemplate template = sl.getStructureManager().get(MUSHROOM_NBT).orElse(null);
        if (template == null) return;

        BlockPos origin = new BlockPos((int) Math.floor(position.x()), (int) Math.floor(position.y()), (int) Math.floor(position.z()));
        net.minecraft.core.Vec3i size = template.getSize(Rotation.NONE);
        int cx = size.getX() / 2;
        int cy = size.getY() / 2;

        // NBT mushroom faces +Z (SOUTH), cap at far +Z end
        // Map projectile facing to Y-axis rotation
        Rotation rotation = switch (facing) {
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST  -> Rotation.CLOCKWISE_90;
            case WEST  -> Rotation.COUNTERCLOCKWISE_90;
            default    -> Rotation.NONE; // SOUTH, UP, DOWN
        };

        // Center the structure: template center (cx,cy,0) rotated, negated to offset
        origin = origin.offset(switch (rotation) {
            case CLOCKWISE_90        -> new BlockPos(0, -cy, -cx);
            case CLOCKWISE_180       -> new BlockPos(cx, -cy, 0);
            case COUNTERCLOCKWISE_90 -> new BlockPos(0, -cy, cx);
            default                  -> new BlockPos(-cx, -cy, 0); // NONE
        });
        // Push toward the cannon (Z-axis needs opposite). WEST/NORTH need 1 less.
        Direction pushDir = facing.getAxis() == Direction.Axis.Z ? facing.getOpposite() : facing;
        int pushDist = (facing == Direction.EAST || facing == Direction.NORTH) ? 3 : 4;
        origin = origin.relative(pushDir, pushDist);

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(true);

        // Clear 3x3 tunnel along the stem before placing the mushroom
        for (int tz = 0; tz < size.getZ(); tz++) {
            BlockPos stemLocal = rotateTemplatePos(cx, cy, tz, rotation);
            BlockPos stemWorld = origin.offset(stemLocal);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    sl.setBlock(stemWorld.offset(rotateTemplatePos(dx, dy, 0, rotation)),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }

        template.placeInWorld(sl, origin, origin, settings, sl.getRandom(), 3);

        // Schedule TNT explosion 3 seconds later at mushroom center
        BlockPos explodePos = origin.offset(cx, cy, 0);
        DelayedExplosionManager.schedule(sl, explodePos, 60);

        // Particles centered on the mushroom cap area
        net.minecraft.core.Vec3i rotatedSize = template.getSize(rotation);
        sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                origin.getX() + rotatedSize.getX() / 2.0,
                origin.getY() + rotatedSize.getY() / 2.0,
                origin.getZ() + rotatedSize.getZ() / 2.0,
                60, rotatedSize.getX() / 2.0, rotatedSize.getY() / 2.0, rotatedSize.getZ() / 2.0, 0.02);
    }

    /** Rotate a template-local position by the given Rotation. */
    private static BlockPos rotateTemplatePos(int tx, int ty, int tz, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90        -> new BlockPos(-tz, ty, tx);
            case CLOCKWISE_180       -> new BlockPos(-tx, ty, -tz);
            case COUNTERCLOCKWISE_90 -> new BlockPos(tz, ty, -tx);
            default                  -> new BlockPos(tx, ty, tz);
        };
    }

    @Override
    public BlockState getRenderedBlockState() {
        return dev.simulated_team.aero_reformation.registrate.AeroBlocks.MUSHROOM_SHELL.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH);
    }

    @Nonnull
    @Override
    protected BigCannonFuzePropertiesComponent getFuzeProperties() {
        return this.getAllProperties().fuze();
    }

    @Nonnull
    @Override
    protected BigCannonProjectilePropertiesComponent getBigCannonProjectileProperties() {
        return this.getAllProperties().bigCannonProperties();
    }

    @Nonnull
    @Override
    public EntityDamagePropertiesComponent getDamageProperties() {
        return this.getAllProperties().damage();
    }

    @Nonnull
    @Override
    protected BallisticPropertiesComponent getBallisticProperties() {
        return this.getAllProperties().ballistics();
    }

    protected BigCannonCommonShellProperties getAllProperties() {
        return CBCMunitionPropertiesHandlers.COMMON_SHELL_BIG_CANNON_PROJECTILE.getPropertiesOf(this);
    }
}
