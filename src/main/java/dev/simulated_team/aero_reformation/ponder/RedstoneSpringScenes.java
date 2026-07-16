package dev.simulated_team.aero_reformation.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * 由 Ponderer 场景自动生成的 Ponder 故事板
 * 源场景: ponderer:redstone_spring
 * 源标题: 红石扭簧
 * 生成时间: 2026-07-12 22:24:56
 */
public class RedstoneSpringScenes {

    public static void redstone_spring_p1(SceneBuilder builder, SceneBuildingUtil util) {
        // 红石扭簧
        var scene = new CreateSceneBuilder(builder);
        scene.title("redstone_spring_scene_1", "红石扭簧");
        scene.configureBasePlate(0, 0, 5);

        scene.addKeyframe();

        scene.addInstruction(ps -> ps.getTransform().yRotation.setValue(410.0f)); // 瞬间旋转相机，无动画
        scene.world().showSection(util.select().everywhere(), Direction.UP);


        scene.idleSeconds(1);


        {
            var textBuilder = scene.overlay().showText(20)
                .text("红石扭簧会根据收到的信号大小改变旋转角度")
                .pointAt(new Vec3(2.5, 2.0, 2.5));
            textBuilder.placeNearTarget();
        }


        scene.idleSeconds(3);


        {
            var inputBuilder = scene.overlay().showControls(new Vec3(2.0, 1.5, 3.5), Pointing.UP, 20);
            inputBuilder.rightClick();
        }


        scene.idleSeconds(1);


        scene.overlay().showOutline(PonderPalette.WHITE, ResourceLocation.parse("highlight_0"), util.select().fromTo(util.grid().at(2, 1, 3), util.grid().at(2, 1, 2)), 40);


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 3)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{State:1}"));
            } catch (Exception ignored) {}
        }, false);


        scene.effects().indicateRedstone(util.grid().at(2, 1, 3));


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{Bidirectional:0b,ConnectedToExtraKinetics:0b,Network:{Capacity:262144.0f,Id:-12919261429696L,Size:2,Stress:0.0f},RedstoneSpringOutput:{Angle:-6.0d,CurrentState:0,GeneratedSpeed:0.0f,LastSpringSpeed:16.0f,OldAngle:0.0d,QueuedSpeed:0.0f,RotationDurationTicks:-1,RotationProgressTicks:-1,SequencedAngleLimit:0.0d,Speed:0.0f,TargetAngle:-6.0d},ScrollValue:90,Source:[I;-47,64,48],Speed:-16.0f}"));
            } catch (Exception ignored) {}
        }, false);


        scene.idleSeconds(1);


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 3)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{State:2}"));
            } catch (Exception ignored) {}
        }, false);


        scene.effects().indicateRedstone(util.grid().at(2, 1, 3));


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{Bidirectional:0b,ConnectedToExtraKinetics:0b,Network:{Capacity:262144.0f,Id:-12919261429696L,Size:2,Stress:0.0f},RedstoneSpringOutput:{Angle:-12.0d,CurrentState:0,GeneratedSpeed:0.0f,LastSpringSpeed:16.0f,OldAngle:-6.0d,QueuedSpeed:0.0f,RotationDurationTicks:-1,RotationProgressTicks:-1,SequencedAngleLimit:0.0d,Speed:0.0f,TargetAngle:-12.0d},ScrollValue:90,Source:[I;-47,64,48],Speed:-16.0f}"));
            } catch (Exception ignored) {}
        }, false);


        scene.idleSeconds(1);


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 3)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{State:4}"));
            } catch (Exception ignored) {}
        }, false);


        scene.effects().indicateRedstone(util.grid().at(2, 1, 3));


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{Bidirectional:0b,ConnectedToExtraKinetics:0b,Network:{Capacity:262144.0f,Id:-12919261429696L,Size:2,Stress:0.0f},RedstoneSpringOutput:{Angle:-24.0d,CurrentState:0,GeneratedSpeed:0.0f,LastSpringSpeed:16.0f,OldAngle:-12.0d,QueuedSpeed:0.0f,RotationDurationTicks:-1,RotationProgressTicks:-1,SequencedAngleLimit:0.0d,Speed:0.0f,TargetAngle:-24.0d},ScrollValue:90,Source:[I;-47,64,48],Speed:-16.0f}"));
            } catch (Exception ignored) {}
        }, false);


        scene.idleSeconds(1);


        scene.world().destroyBlock(util.grid().at(2, 1, 3));


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{Bidirectional:0b,ConnectedToExtraKinetics:0b,Network:{Capacity:262144.0f,Id:-12919261429696L,Size:2,Stress:0.0f},RedstoneSpringOutput:{Angle:0.0d,CurrentState:0,GeneratedSpeed:0.0f,LastSpringSpeed:16.0f,OldAngle:-24.0d,QueuedSpeed:0.0f,RotationDurationTicks:-1,RotationProgressTicks:-1,SequencedAngleLimit:0.0d,Speed:0.0f,TargetAngle:0.0d},ScrollValue:90,Source:[I;-47,64,48],Speed:-16.0f}"));
            } catch (Exception ignored) {}
        }, false);

        scene.addKeyframe();

        scene.idleSeconds(1);


        {
            var textBuilder = scene.overlay().showText(20)
                .text("通过交互，可以修改其最大旋转角度，每个档位的角度也会随其变化")
                .pointAt(new Vec3(2.5, 2.0, 2.5));
            textBuilder.placeNearTarget();
        }


        scene.idleSeconds(3);


        {
            var inputBuilder = scene.overlay().showControls(new Vec3(2.5, 2.7, 2.7), Pointing.DOWN, 40);
            inputBuilder.rightClick();
        }


        scene.idleSeconds(2);


        // Erase original block from base section / 擦除原位方块防止重叠
        scene.addInstruction(ps -> {
            ps.getBaseWorldSection().erase(util.select().position(util.grid().at(2, 1, 3)));
            ps.getBaseWorldSection().queueRedraw();
        });
        {
            var state = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.parse("create:analog_lever")).orElse(Blocks.STONE).defaultBlockState();
            scene.world().setBlock(util.grid().at(2, 1, 3), state, false);
            // Apply block properties with typed references / 应用方块属性
            scene.world().modifyBlock(util.grid().at(2, 1, 3), s -> s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR), false);
            scene.world().modifyBlock(util.grid().at(2, 1, 3), s -> s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST), false);
        }
        // Entrance animation / 入场动画: 独立 section 从 down 方向飞入
        scene.world().showIndependentSection(util.select().position(util.grid().at(2, 1, 3)), Direction.DOWN);


        scene.idleSeconds(1);


        {
            var textBuilder = scene.overlay().showText(20)
                .text("如将最大角度设定为360度时")
                .pointAt(new Vec3(2.5, 2.0, 2.5));
            textBuilder.placeNearTarget();
        }


        scene.idleSeconds(2);


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 3)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{State:1}"));
            } catch (Exception ignored) {}
        }, false);


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{Bidirectional:0b,ConnectedToExtraKinetics:0b,Network:{Capacity:262144.0f,Id:-12919261429696L,Size:2,Stress:0.0f},RedstoneSpringOutput:{Angle:-24.0d,CurrentState:0,GeneratedSpeed:0.0f,LastSpringSpeed:-16.0f,OldAngle:0.0d,QueuedSpeed:0.0f,RotationDurationTicks:-1,RotationProgressTicks:-1,SequencedAngleLimit:0.0d,Speed:0.0f,TargetAngle:-24.0d},ScrollValue:360,Source:[I;-47,64,48],Speed:-16.0f}"));
            } catch (Exception ignored) {}
        }, false);


        scene.idleSeconds(1);


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 3)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{State:2}"));
            } catch (Exception ignored) {}
        }, false);


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{Bidirectional:0b,ConnectedToExtraKinetics:0b,Network:{Capacity:262144.0f,Id:-12919261429696L,Size:2,Stress:0.0f},RedstoneSpringOutput:{Angle:-48.0d,CurrentState:0,GeneratedSpeed:0.0f,LastSpringSpeed:-16.0f,OldAngle:-24.0d,QueuedSpeed:0.0f,RotationDurationTicks:-1,RotationProgressTicks:-1,SequencedAngleLimit:0.0d,Speed:0.0f,TargetAngle:48.0d},ScrollValue:360,Source:[I;-47,64,48],Speed:-16.0f}"));
            } catch (Exception ignored) {}
        }, false);
    }

    public static void redstone_spring_p2(SceneBuilder builder, SceneBuildingUtil util) {
        // 双向模式
        var scene = new CreateSceneBuilder(builder);
        scene.title("redstone_spring_s_5b57c", "双向模式");
        scene.configureBasePlate(0, 0, 5);


        scene.addInstruction(ps -> ps.getTransform().yRotation.setValue(410.0f)); // 瞬间旋转相机，无动画
        scene.world().showSection(util.select().everywhere(), Direction.UP);


        scene.idleSeconds(1);


        {
            var textBuilder = scene.overlay().showText(10)
                .text("用红石火把右击红石扭簧切换双向模式")
                .pointAt(new Vec3(2.5, 2.0, 2.5));
            textBuilder.placeNearTarget();
        }


        {
            var inputBuilder = scene.overlay().showControls(new Vec3(2.5, 2.2, 2.5), Pointing.DOWN, 20);
            inputBuilder.rightClick();
            inputBuilder.withItem(new ItemStack(
                BuiltInRegistries.ITEM.getOptional(
                    ResourceLocation.parse("minecraft:redstone_torch")).orElse(Items.STICK)));
        }


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{Bidirectional:1b,ConnectedToExtraKinetics:0b,Network:{Capacity:262144.0f,Id:-12919261429696L,Size:2,Stress:0.0f},RedstoneSpringOutput:{Angle:0.0d,CurrentState:0,GeneratedSpeed:0.0f,LastSpringSpeed:16.0f,OldAngle:0.0d,QueuedSpeed:0.0f,RotationDurationTicks:-1,RotationProgressTicks:-1,SequencedAngleLimit:0.0d,Speed:0.0f,TargetAngle:0.0d},ScrollValue:90,Source:[I;-47,64,48],Speed:-16.0f}"));
            } catch (Exception ignored) {}
        }, false);


        scene.idleSeconds(1);


        {
            var textBuilder = scene.overlay().showText(10)
                .text("双向模式下，红石扭簧的旋转方向随信号接收面变化")
                .pointAt(new Vec3(2.5, 2.0, 2.5));
            textBuilder.placeNearTarget();
        }


        scene.effects().indicateSuccess(util.grid().at(2, 1, 2));


        scene.idleSeconds(1);


        scene.overlay().showOutline(PonderPalette.WHITE, ResourceLocation.parse("highlight_1"), util.select().fromTo(util.grid().at(2, 1, 3), util.grid().at(2, 1, 2)), 40);


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 3)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{State:2}"));
            } catch (Exception ignored) {}
        }, false);


        scene.effects().indicateRedstone(util.grid().at(2, 1, 3));


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{Bidirectional:1b,ConnectedToExtraKinetics:0b,Network:{Capacity:262144.0f,Id:-12919261429696L,Size:2,Stress:0.0f},RedstoneSpringOutput:{Angle:-12.0d,CurrentState:0,GeneratedSpeed:0.0f,LastSpringSpeed:-16.0f,OldAngle:0.0d,QueuedSpeed:0.0f,RotationDurationTicks:-1,RotationProgressTicks:-1,SequencedAngleLimit:0.0d,Speed:0.0f,TargetAngle:-12.0d},ScrollValue:90,Source:[I;-47,64,48],Speed:-16.0f}"));
            } catch (Exception ignored) {}
        }, false);


        scene.idleSeconds(2);


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 3)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{State:0}"));
            } catch (Exception ignored) {}
        }, false);


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{Bidirectional:1b,ConnectedToExtraKinetics:0b,Network:{Capacity:262144.0f,Id:-12919261429696L,Size:2,Stress:0.0f},RedstoneSpringOutput:{Angle:0.0d,CurrentState:0,GeneratedSpeed:0.0f,LastSpringSpeed:16.0f,OldAngle:-12.0d,QueuedSpeed:0.0f,RotationDurationTicks:-1,RotationProgressTicks:-1,SequencedAngleLimit:0.0d,Speed:0.0f,TargetAngle:0.0d},ScrollValue:90,Source:[I;-47,64,48],Speed:-16.0f}"));
            } catch (Exception ignored) {}
        }, false);


        scene.idleSeconds(2);


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 1)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{State:2}"));
            } catch (Exception ignored) {}
        }, false);


        scene.overlay().showOutline(PonderPalette.WHITE, ResourceLocation.parse("highlight_1"), util.select().fromTo(util.grid().at(2, 1, 2), util.grid().at(2, 1, 1)), 40);


        scene.effects().indicateRedstone(util.grid().at(2, 1, 1));


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{Bidirectional:1b,ConnectedToExtraKinetics:0b,Network:{Capacity:262144.0f,Id:-12919261429696L,Size:2,Stress:0.0f},RedstoneSpringOutput:{Angle:12.0d,CurrentState:0,GeneratedSpeed:0.0f,LastSpringSpeed:16.0f,OldAngle:0.0d,QueuedSpeed:0.0f,RotationDurationTicks:-1,RotationProgressTicks:-1,SequencedAngleLimit:0.0d,Speed:0.0f,TargetAngle:12.0d},ScrollValue:90,Source:[I;-47,64,48],Speed:-16.0f}"));
            } catch (Exception ignored) {}
        }, false);


        scene.idleSeconds(2);


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 1)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{State:0}"));
            } catch (Exception ignored) {}
        }, false);


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{Bidirectional:1b,ConnectedToExtraKinetics:0b,Network:{Capacity:262144.0f,Id:-12919261429696L,Size:2,Stress:0.0f},RedstoneSpringOutput:{Angle:0.0d,CurrentState:0,GeneratedSpeed:0.0f,LastSpringSpeed:16.0f,OldAngle:12.0d,QueuedSpeed:0.0f,RotationDurationTicks:-1,RotationProgressTicks:-1,SequencedAngleLimit:0.0d,Speed:0.0f,TargetAngle:0.0d},ScrollValue:90,Source:[I;-47,64,48],Speed:-16.0f}"));
            } catch (Exception ignored) {}
        }, false);

        scene.addKeyframe();

        scene.idleSeconds(1);


        {
            var textBuilder = scene.overlay().showText(20)
                .text("当多个面被充能时，扭簧会归中")
                .pointAt(new Vec3(2.5, 2.0, 2.5));
            textBuilder.placeNearTarget();
        }


        scene.idleSeconds(2);


        scene.overlay().showOutline(PonderPalette.BLUE, ResourceLocation.parse("highlight_1"), util.select().fromTo(util.grid().at(2, 1, 1), util.grid().at(2, 1, 3)), 40);


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 3)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{State:2}"));
            } catch (Exception ignored) {}
        }, false);


        scene.effects().indicateRedstone(util.grid().at(2, 1, 3));


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{Bidirectional:1b,ConnectedToExtraKinetics:0b,Network:{Capacity:262144.0f,Id:-12919261429696L,Size:2,Stress:0.0f},RedstoneSpringOutput:{Angle:-12.0d,CurrentState:0,GeneratedSpeed:0.0f,LastSpringSpeed:-16.0f,OldAngle:0.0d,QueuedSpeed:0.0f,RotationDurationTicks:-1,RotationProgressTicks:-1,SequencedAngleLimit:0.0d,Speed:0.0f,TargetAngle:-12.0d},ScrollValue:90,Source:[I;-47,64,48],Speed:-16.0f}"));
            } catch (Exception ignored) {}
        }, false);


        scene.idleSeconds(2);


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 1)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{State:2}"));
            } catch (Exception ignored) {}
        }, false);


        scene.effects().indicateRedstone(util.grid().at(2, 1, 1));


        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{Bidirectional:1b,ConnectedToExtraKinetics:0b,Network:{Capacity:262144.0f,Id:-12919261429696L,Size:2,Stress:0.0f},RedstoneSpringOutput:{Angle:0.0d,CurrentState:0,GeneratedSpeed:0.0f,LastSpringSpeed:-16.0f,OldAngle:-12.0d,QueuedSpeed:0.0f,RotationDurationTicks:-1,RotationProgressTicks:-1,SequencedAngleLimit:0.0d,Speed:0.0f,TargetAngle:0.0d},ScrollValue:90,Source:[I;-47,64,48],Speed:-16.0f}"));
            } catch (Exception ignored) {}
        }, false);
    }
}
