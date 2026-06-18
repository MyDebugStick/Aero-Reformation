package dev.simulated_team.aero_reformation.content.items.mushroom_shell;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import rbasamoyai.createbigcannons.index.CBCMunitionPropertiesHandlers;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;
import rbasamoyai.createbigcannons.munitions.big_cannon.SimpleShellBlock;

public class MushroomShellBlock extends SimpleShellBlock<MushroomShellProjectile> {

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return simpleCodec(MushroomShellBlock::new);
    }

    public MushroomShellBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Class<FuzedBlockEntity> getBlockEntityClass() {
        return FuzedBlockEntity.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BlockEntityType<? extends FuzedBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends FuzedBlockEntity>) dev.simulated_team.aero_reformation.registrate.AeroBlocks.MUSHROOM_SHELL_BE.get();
    }

    @Override
    public boolean isBaseFuze() {
        return CBCMunitionPropertiesHandlers.COMMON_SHELL_BIG_CANNON_PROJECTILE
                .getPropertiesOf(this.getAssociatedEntityType()).fuze().baseFuze();
    }

    @Override
    @SuppressWarnings("unchecked")
    public EntityType<? extends MushroomShellProjectile> getAssociatedEntityType() {
        return (EntityType<? extends MushroomShellProjectile>) dev.simulated_team.aero_reformation.registrate.AeroBlocks.MUSHROOM_SHELL_ENTITY.get();
    }
}
