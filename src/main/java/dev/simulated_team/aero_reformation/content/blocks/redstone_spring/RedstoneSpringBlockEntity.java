package dev.simulated_team.aero_reformation.content.blocks.redstone_spring;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencerInstructions;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.simulated_team.simulated.data.SimLang;
import dev.simulated_team.simulated.mixin_interface.extra_kinetics.KineticBlockEntityExtension;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraBlockPos;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class RedstoneSpringBlockEntity extends KineticBlockEntity implements ExtraKinetics {

    private final Output springOutput;
    public ScrollValueBehaviour angleInput;
    protected double sequencedAngleLimit;
    private boolean bidirectional = false;

    public boolean isBidirectional() { return bidirectional; }
    public void toggleBidirectional() { this.bidirectional = !this.bidirectional; setChanged(); }

    public RedstoneSpringBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.springOutput = new Output(type, new ExtraBlockPos(pos), state, this);
        this.sequencedAngleLimit = -1;
    }

    // 1.21.1 BlockEntitySupplier uses the (BlockPos, BlockState) constructor
    public RedstoneSpringBlockEntity(BlockPos pos, BlockState state) {
        this(dev.simulated_team.aero_reformation.registrate.AeroBlocks.REDSTONE_SPRING_BE.get(), pos, state);
    }

    // ==================== Main BE methods ====================

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return super.addToGoggleTooltip(tooltip, isPlayerSneaking);
    }

    public boolean isSpringStatic() {
        return this.springOutput.angle == this.springOutput.oldAngle;
    }

    public float interpolatedSpring(float pt) {
        return (float) (this.springOutput.oldAngle + (this.springOutput.angle - this.springOutput.oldAngle) * pt);
    }

    public float getAngle() {
        return (float) this.springOutput.angle;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.add(this.angleInput = new AngleScrollBehaviour(this).between(1, 360));
        this.angleInput.onlyActiveWhen(() -> true);
        this.angleInput.setValue(90);
    }

    @Override
    public void tick() {
        super.tick();
        this.springOutput.tick();
    }

    @Override
    public void onSpeedChanged(float previousSpeed) {
        super.onSpeedChanged(previousSpeed);
        this.sequencedAngleLimit = -1;
        if (this.sequenceContext != null && this.sequenceContext.instruction() == SequencerInstructions.TURN_ANGLE)
            this.sequencedAngleLimit = this.sequenceContext.getEffectiveValue(this.getTheoreticalSpeed());
        this.springOutput.updateParentSpeed(previousSpeed, this.getSpeed());
    }

    @Override
    public float calculateAddedStressCapacity() {
        return (float) dev.simulated_team.aero_reformation.config.AeroReformationConfig.redstoneSpringStressCapacity;
    }

    @Override
    public String getExtraKineticsSaveName() {
        return "RedstoneSpringOutput";
    }

    @Override
    public KineticBlockEntity getExtraKinetics() {
        return this.springOutput;
    }

    @Override
    public boolean shouldConnectExtraKinetics() {
        return false;
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putBoolean("Bidirectional", this.bidirectional);
        if (this.sequencedAngleLimit >= 0)
            compound.putDouble("SequencedAngleLimit", this.sequencedAngleLimit);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        this.bidirectional = compound.getBoolean("Bidirectional");
        this.sequencedAngleLimit = compound.contains("SequencedAngleLimit") ? compound.getDouble("SequencedAngleLimit") : -1;
    }

    // ==================== Read redstone signal ====================

    private int getMaxNeighborSignal() {
        if (this.level == null) return 0;
        int max = 0;
        Direction facing = getBlockState().getValue(RedstoneSpringBlock.FACING);
        for (Direction dir : Direction.values()) {
            if (dir.getAxis() == facing.getAxis()) continue; // skip shaft input/output faces
            max = Math.max(max, this.level.getSignal(this.worldPosition.relative(dir), dir));
        }
        return max;
    }

    int getEffectiveSignal() {
        if (this.level == null) return 0;
        if (!bidirectional) return getMaxNeighborSignal();
        // Bidirectional: any of the 4 lateral faces can power; >1 face powered → center
        int count = 0;
        int maxSig = 0;
        Direction facing = getBlockState().getValue(RedstoneSpringBlock.FACING);
        for (Direction dir : Direction.values()) {
            if (dir.getAxis() == facing.getAxis()) continue;
            int s = this.level.getSignal(this.worldPosition.relative(dir), dir);
            if (s > 0) { count++; if (s > maxSig) maxSig = s; }
        }
        return count == 1 ? maxSig : 0;
    }

    /**
     * Returns rotation direction: 1 = clockwise, -1 = counter-clockwise, 0 = center.
     * For UP/DOWN facing, clockwise is EAST; for horizontal facing uses getClockWise().
     */
    int getRotationSign() {
        if (!bidirectional) return (int) Math.signum(getSpeed());
        if (getEffectiveSignal() == 0) return 0;
        Direction facing = getBlockState().getValue(RedstoneSpringBlock.FACING);
        Direction cw = facing.getAxis() == Direction.Axis.Y
                ? (facing == Direction.UP ? Direction.EAST : Direction.WEST)
                : facing.getClockWise();
        // Find another non-axis direction on a different axis than cw
        Direction cw2 = null;
        for (Direction dir : Direction.values()) {
            if (dir.getAxis() == facing.getAxis() || dir.getAxis() == cw.getAxis()) continue;
            cw2 = dir; break;
        }
        int cwSignal = this.level.getSignal(this.worldPosition.relative(cw), cw);
        int cw2Signal = cw2 != null ? this.level.getSignal(this.worldPosition.relative(cw2), cw2) : 0;
        return (cwSignal > 0 || cw2Signal > 0) ? 1 : -1;
    }

    // ==================== Output (based on vanilla torque spring) ====================

    public static class Output extends GeneratingKineticBlockEntity implements ExtraKineticsBlockEntity {

        public static final IRotate CONFIG = new IRotate() {
            @Override
            public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
                return face == state.getValue(RedstoneSpringBlock.FACING);
            }

            @Override
            public Direction.Axis getRotationAxis(BlockState state) {
                return state.getValue(RedstoneSpringBlock.FACING).getAxis();
            }
        };

        private final RedstoneSpringBlockEntity parent;

        protected double oldAngle = 0.0;
        protected double angle = 0.0;

        private int rotationDurationTicks = 0;
        private int rotationProgressTicks = 0;
        private double sequencedAngleLimit = -1;
        private float lastSpringSpeed = 0;
        private float generatedSpeed;
        private double targetAngle = 0;
        private State currentState = State.STOPPED;
        private float queuedSpeed;
        private int customValidationCountdown;

        public Output(BlockEntityType<?> type, ExtraBlockPos pos, BlockState state,
                      RedstoneSpringBlockEntity parentBlockEntity) {
            super(type, pos, state);
            this.parent = parentBlockEntity;
        }

        @Override
        public KineticBlockEntity getParentBlockEntity() {
            return this.parent;
        }

        @Override
        public void initialize() {
            super.initialize();
            this.reActivateSource = true;
            this.updateSpeed = true;
        }

        @Override
        public void tick() {
            // Vanilla validation logic
            ((KineticBlockEntityExtension) this).simulated$setValidationCountdown(Integer.MAX_VALUE);
            if (this.customValidationCountdown-- <= 0) {
                this.customValidationCountdown = AllConfigs.server().kinetics.kineticValidationFrequency.get();
                this.customValidateKinetics();
            }

            this.generatedSpeed = this.queuedSpeed;
            super.tick();

            this.oldAngle = this.angle;

            // Advance angle
            if (this.rotationDurationTicks >= 0 && this.rotationProgressTicks <= this.rotationDurationTicks) {
                this.rotationProgressTicks++;
                float angularSpeed = KineticBlockEntity.convertToAngular(this.speed);

                if (this.sequencedAngleLimit >= 0)
                    angularSpeed = (float) Mth.clamp(angularSpeed, -this.sequencedAngleLimit, this.sequencedAngleLimit);
                if (this.sequencedAngleLimit >= 0)
                    this.sequencedAngleLimit = Math.max(0, this.sequencedAngleLimit - Math.abs(angularSpeed));

                this.angle += angularSpeed;

                if (this.rotationProgressTicks == this.rotationDurationTicks) {
                    this.sequenceContext = null;
                    this.rotationProgressTicks = -1;
                    this.rotationDurationTicks = -1;
                    this.queuedSpeed = 0;
                    this.reActivateSource = true;
                    this.updateSpeed = true;
                    this.currentState = State.STOPPED;
                }
            }

            final boolean powered = this.getBlockState().getValue(RedstoneSpringBlock.POWERED);
            final boolean parentStopped = this.parent.getSpeed() == 0;

            // ---- State machine (vanilla logic; only angle calc uses redstone scaling) ----

            if (this.currentState == State.TURNING && parentStopped) {
                if (this.targetAngle != 0 || powered)
                    this.stopTurning();
            } else if (this.currentState == State.STOPPED && parentStopped && !powered) {
                if (this.targetAngle != 0.0) {
                    this.beginTurnTo(0.0);
                }
            } else if (this.currentState == State.TURNING) {

                final int sig = this.parent.getEffectiveSignal();
                final int rotSign = this.parent.getRotationSign();
                final double calcAngle = sig == 0 ? 0
                        : this.parent.angleInput.getValue() * sig / 15.0 * rotSign;
                if (this.targetAngle != calcAngle || this.lastSpringSpeed != this.generatedSpeed) {
                    this.stopTurning();
                }
            } else if (!parentStopped && this.currentState == State.STOPPED) {
                final int rotSign = this.parent.getRotationSign();
                final double targetAngle = this.parent.angleInput.getValue() * rotSign;
                this.beginTurnTo(targetAngle);
            }
        }

        // ---- Network validation (vanilla logic) ----

        private void customValidateKinetics() {
            if (this.hasSource()) {
                if (!this.hasNetwork()) {
                    this.removeSource();
                    return;
                }
                if (!this.level.isLoaded(this.source)) return;

                BlockEntity blockEntity = this.level.getBlockEntity(this.source);
                if (blockEntity instanceof ExtraKinetics ek
                        && ((KineticBlockEntityExtension) this).simulated$getConnectedToExtraKinetics()) {
                    blockEntity = ek.getExtraKinetics();
                }
                KineticBlockEntity sourceBE = blockEntity instanceof KineticBlockEntity ? (KineticBlockEntity) blockEntity : null;
                if (sourceBE == null || sourceBE.getTheoreticalSpeed() == 0) {
                    this.removeSource();
                    this.detachKinetics();
                }
            }
        }

        void updateParentSpeed(float previousSpeed, float newParentSpeed) {
            if (newParentSpeed != 0) {
                this.lastSpringSpeed = newParentSpeed;
            } else if (previousSpeed != 0) {
                this.lastSpringSpeed = previousSpeed;
            }
        }

        // ---- Rotation control (vanilla logic) ----

        private void stopTurning() {
            this.sequenceContext = null;
            this.rotationProgressTicks = -1;
            this.rotationDurationTicks = -1;
            this.sequencedAngleLimit = -1;
            this.targetAngle = Double.MAX_VALUE;
            this.reActivateSource = true;
            this.updateSpeed = true;
            this.queuedSpeed = 0;
            this.currentState = State.STOPPED;
        }

        private void beginTurnTo(double target) {
            final int signal = this.parent.getEffectiveSignal();
            final int rotSign = this.parent.getRotationSign();
            if (signal == 0 || rotSign == 0) {
                target = 0;
            } else {
                target = Math.round(Math.abs(target) * signal / 15.0) * rotSign;
            }

            double relativeAngle = target - this.angle;

            if (relativeAngle == 0) return;
            if (this.currentState == State.TURNING && this.targetAngle == target) return;

            // Shortest-path direction (reverses when signal angle < current angle)
            this.lastSpringSpeed = (float) (Math.abs(this.lastSpringSpeed) * Math.signum(relativeAngle));

            if (this.parent.sequencedAngleLimit >= 0)
                relativeAngle = (float) Mth.clamp(relativeAngle, -this.parent.sequencedAngleLimit, this.parent.sequencedAngleLimit);

            this.detachKinetics();
            this.targetAngle = target;
            this.sequenceContext = new SequencedGearshiftBlockEntity.SequenceContext(
                    SequencerInstructions.TURN_ANGLE, relativeAngle / this.lastSpringSpeed);

            final double degreesPerTick = KineticBlockEntity.convertToAngular(Math.abs(this.lastSpringSpeed));
            this.rotationDurationTicks = (int) Math.ceil(Math.abs(relativeAngle) / degreesPerTick) + 2;
            this.rotationProgressTicks = 0;

            this.sequencedAngleLimit = this.sequenceContext.getEffectiveValue(this.lastSpringSpeed);
            this.currentState = State.TURNING;
            this.queuedSpeed = this.lastSpringSpeed;
            this.generatedSpeed = this.queuedSpeed;
            this.reActivateSource = true;
            this.updateSpeed = true;
        }

        // ---- Stress (vanilla logic) ----

        @Override
        public float getGeneratedSpeed() {
            return this.generatedSpeed;
        }

        @Override
        public float calculateStressApplied() {
            return 0;
        }

        @Override
        public float calculateAddedStressCapacity() {
            float base = super.calculateAddedStressCapacity();
            if (base > 0) return base;
            return (float) dev.simulated_team.aero_reformation.config.AeroReformationConfig.redstoneSpringStressCapacity;
        }

        // ---- NBT (vanilla logic) ----

        @Override
        protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
            super.write(compound, registries, clientPacket);
            compound.putDouble("OldAngle", this.oldAngle);
            compound.putDouble("Angle", this.angle);
            compound.putDouble("TargetAngle", this.targetAngle);
            compound.putFloat("LastSpringSpeed", this.lastSpringSpeed);
            compound.putInt("CurrentState", this.currentState.ordinal());
            compound.putInt("RotationProgressTicks", this.rotationProgressTicks);
            compound.putInt("RotationDurationTicks", this.rotationDurationTicks);
            compound.putFloat("GeneratedSpeed", this.generatedSpeed);
            compound.putFloat("QueuedSpeed", this.queuedSpeed);
            if (this.sequencedAngleLimit >= 0)
                compound.putDouble("SequencedAngleLimit", this.sequencedAngleLimit);
        }

        @Override
        protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
            super.read(compound, registries, clientPacket);
            this.oldAngle = compound.getDouble("OldAngle");
            this.angle = compound.getDouble("Angle");
            this.targetAngle = compound.getDouble("TargetAngle");
            this.lastSpringSpeed = compound.getFloat("LastSpringSpeed");
            this.sequencedAngleLimit = compound.contains("SequencedAngleLimit") ? compound.getDouble("SequencedAngleLimit") : -1;
            this.rotationProgressTicks = compound.getInt("RotationProgressTicks");
            this.rotationDurationTicks = compound.getInt("RotationDurationTicks");
            this.generatedSpeed = compound.getFloat("GeneratedSpeed");
            this.queuedSpeed = compound.getFloat("QueuedSpeed");
            if (compound.contains("CurrentState"))
                this.currentState = State.values()[compound.getInt("CurrentState")];
        }

        private enum State { STOPPED, TURNING }
    }

    // ==================== Slider (based on vanilla) ====================

    public static class AngleScrollBehaviour extends ScrollValueBehaviour {
        public AngleScrollBehaviour(SmartBlockEntity be) {
            super(SimLang.translate("torsion_spring.angle_limit").component(), be, new AngleValueBox());
            this.withFormatter(v -> Math.max(1, v) + CreateLang.translateDirect("generic.unit.degrees").getString());
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
            return new ValueSettingsBoard(this.label, 360, 45,
                    ImmutableList.of(Component.literal("\u27f3").withStyle(ChatFormatting.BOLD)),
                    new ValueSettingsFormatter(this::formatValue));
        }

        public MutableComponent formatValue(ValueSettings settings) {
            return SimLang.number(Math.max(1, settings.value()))
                    .add(CreateLang.translateDirect("generic.unit.degrees")).component();
        }
    }

    public static class AngleValueBox extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8, 15.5);
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            return super.getLocalOffset(level, pos, state)
                    .add(Vec3.atLowerCornerOf(state.getValue(RedstoneSpringBlock.FACING).getNormal()).scale(-5 / 16f));
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            if (!this.getSide().getAxis().isHorizontal()) {
                dev.engine_room.flywheel.lib.transform.TransformStack.of(ms)
                        .rotateY((AngleHelper.horizontalAngle(state.getValue(RedstoneSpringBlock.FACING)) + 180) * (float) Math.PI / 180);
            }
            super.rotate(level, pos, state, ms);
        }

        @Override
        public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
            Vec3 offset = this.getLocalOffset(level, pos, state);
            if (offset == null) return false;
            return localHit.distanceTo(offset) < this.scale / 1.5f;
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction.getAxis() != state.getValue(RedstoneSpringBlock.FACING).getAxis();
        }
    }
}
