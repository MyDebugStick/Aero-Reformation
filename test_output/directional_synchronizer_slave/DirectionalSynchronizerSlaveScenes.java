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
 * 源场景: ponderer:directional_synchronizer_slave
 * 源标题: 方向同步器
 * 生成时间: 2026-07-13 01:25:08
 */
public class DirectionalSynchronizerSlaveScenes {

    public static void directional_synchronizer_slave_p1(SceneBuilder builder, SceneBuildingUtil util) {
        // 红石镜像模式
        var scene = new CreateSceneBuilder(builder);
        scene.title("directional_synchronizer_slave_scene_1", "红石镜像模式");
        scene.configureBasePlate(0, 0, 5);

        scene.addKeyframe();

        scene.addInstruction(ps -> {
            var yRot = ps.getTransform().yRotation;
            yRot.setValue(320.0f);
            yRot.chase(320.0f, 0.3f, net.createmod.catnip.animation.LerpedFloat.Chaser.EXP);
        });
        scene.world().showSection(util.select().everywhere(), Direction.UP);


        // Move camera center to (5.0, 0.5, 2.5) via reflection
        scene.addInstruction(ps -> {
            try {
                var sceneClass = Class.forName("net.createmod.ponder.foundation.PonderScene");
                var offsetXField = sceneClass.getDeclaredField("basePlateOffsetX");
                var offsetZField = sceneClass.getDeclaredField("basePlateOffsetZ");
                offsetXField.setAccessible(true);
                offsetZField.setAccessible(true);
                offsetXField.setInt(ps, 2);
                offsetZField.setInt(ps, 0);
            } catch (Exception e) {}
        });
        scene.setSceneOffsetY(0.5f);
        scene.idle(20);


        scene.idleSeconds(1);


        {
            var textBuilder = scene.overlay().showText(20)
                .text("方向同步器会根据子母方块的朝向输出信号")
                .pointAt(new Vec3(2.5, 2.0, 2.5));
            textBuilder.placeNearTarget();
        }


        scene.idleSeconds(2);


        {
            var textBuilder = scene.overlay().showText(20)
                .text("用子同步器右击母方块进行绑定")
                .pointAt(new Vec3(2.5, 2.0, 2.5));
            textBuilder.placeNearTarget();
        }


        {
            var inputBuilder = scene.overlay().showControls(new Vec3(2.5, 2.0, 2.5), Pointing.DOWN, 40);
            inputBuilder.rightClick();
            inputBuilder.withItem(new ItemStack(
                BuiltInRegistries.ITEM.getOptional(
                    ResourceLocation.parse("aero_reformation:directional_synchronizer_slave")).orElse(Items.STICK)));
        }


        scene.effects().indicateSuccess(util.grid().at(2, 1, 2));


        scene.idleSeconds(2);


        scene.rotateCameraY(10.0f);
        scene.idle(20);


        {
            var textBuilder = scene.overlay().showText(20)
                .text("默认模式下子方块会输出母方块浅蓝色面对应方向的信号")
                .pointAt(new Vec3(9.5, 1.0, 2.5));
            textBuilder.placeNearTarget();
        }

        scene.addKeyframe();

        // Erase original block from base section / 擦除原位方块防止重叠
        scene.addInstruction(ps -> {
            ps.getBaseWorldSection().erase(util.select().position(util.grid().at(9, 1, 2)));
            ps.getBaseWorldSection().queueRedraw();
        });
        {
            var state = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.parse("aero_reformation:directional_synchronizer_slave")).orElse(Blocks.STONE).defaultBlockState();
            scene.world().setBlock(util.grid().at(9, 1, 2), state, false);
            // Apply block properties with typed references / 应用方块属性
            scene.world().modifyBlock(util.grid().at(9, 1, 2), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, java.lang.Boolean.TRUE) : s, false);
        }
        // Entrance animation / 入场动画: 独立 section 从 down 方向飞入
        scene.world().showIndependentSection(util.select().position(util.grid().at(9, 1, 2)), Direction.DOWN);


        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("east");
            if (rawP != null) {
                var rawV = rawP.getValue("none");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("south");
            if (rawP != null) {
                var rawV = rawP.getValue("side");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("north");
            if (rawP != null) {
                var rawV = rawP.getValue("side");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("west");
            if (rawP != null) {
                var rawV = rawP.getValue("none");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("power");
            if (rawP != null) {
                var rawV = rawP.getValue("15");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(9, 1, 3)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{}"));
            } catch (Exception ignored) {}
        }, false);


        scene.effects().indicateRedstone(util.grid().at(9, 1, 3));


        scene.world().modifyBlock(util.grid().at(9, 1, 4), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("east");
            if (rawP != null) {
                var rawV = rawP.getValue("none");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 4), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("south");
            if (rawP != null) {
                var rawV = rawP.getValue("side");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 4), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("north");
            if (rawP != null) {
                var rawV = rawP.getValue("side");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 4), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("west");
            if (rawP != null) {
                var rawV = rawP.getValue("none");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 4), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("power");
            if (rawP != null) {
                var rawV = rawP.getValue("14");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(9, 1, 4)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{}"));
            } catch (Exception ignored) {}
        }, false);


        scene.effects().indicateRedstone(util.grid().at(9, 1, 4));


        scene.idleSeconds(2);


        {
            var inputBuilder = scene.overlay().showControls(new Vec3(2.5, 1.5, 2.0), Pointing.DOWN, 20);
            inputBuilder.rightClick();
            inputBuilder.withItem(new ItemStack(
                BuiltInRegistries.ITEM.getOptional(
                    ResourceLocation.parse("create:wrench")).orElse(Items.STICK)));
        }


        scene.world().modifyBlock(util.grid().at(2, 1, 2), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST) : s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST) : s, false);
        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(2, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{MirrorMode:0b,RcsMode:0b,WorldFacing:3}"));
            } catch (Exception ignored) {}
        }, false);


        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("east");
            if (rawP != null) {
                var rawV = rawP.getValue("none");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("south");
            if (rawP != null) {
                var rawV = rawP.getValue("side");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("north");
            if (rawP != null) {
                var rawV = rawP.getValue("side");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("west");
            if (rawP != null) {
                var rawV = rawP.getValue("none");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("power");
            if (rawP != null) {
                var rawV = rawP.getValue("0");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(9, 1, 3)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{}"));
            } catch (Exception ignored) {}
        }, false);


        scene.world().modifyBlock(util.grid().at(9, 1, 4), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("east");
            if (rawP != null) {
                var rawV = rawP.getValue("none");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 4), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("south");
            if (rawP != null) {
                var rawV = rawP.getValue("side");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 4), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("north");
            if (rawP != null) {
                var rawV = rawP.getValue("side");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 4), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("west");
            if (rawP != null) {
                var rawV = rawP.getValue("none");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 4), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("power");
            if (rawP != null) {
                var rawV = rawP.getValue("0");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(9, 1, 4)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{}"));
            } catch (Exception ignored) {}
        }, false);


        scene.world().modifyBlock(util.grid().at(10, 1, 2), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("east");
            if (rawP != null) {
                var rawV = rawP.getValue("side");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(10, 1, 2), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("south");
            if (rawP != null) {
                var rawV = rawP.getValue("none");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(10, 1, 2), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("north");
            if (rawP != null) {
                var rawV = rawP.getValue("none");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(10, 1, 2), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("west");
            if (rawP != null) {
                var rawV = rawP.getValue("side");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(10, 1, 2), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("power");
            if (rawP != null) {
                var rawV = rawP.getValue("15");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(10, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{}"));
            } catch (Exception ignored) {}
        }, false);


        scene.effects().indicateRedstone(util.grid().at(10, 1, 2));


        scene.world().modifyBlock(util.grid().at(11, 1, 2), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("east");
            if (rawP != null) {
                var rawV = rawP.getValue("side");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(11, 1, 2), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("south");
            if (rawP != null) {
                var rawV = rawP.getValue("none");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(11, 1, 2), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("north");
            if (rawP != null) {
                var rawV = rawP.getValue("none");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(11, 1, 2), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("west");
            if (rawP != null) {
                var rawV = rawP.getValue("side");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlock(util.grid().at(11, 1, 2), s -> {
            net.minecraft.world.level.block.state.properties.Property rawP = s.getBlock().getStateDefinition().getProperty("power");
            if (rawP != null) {
                var rawV = rawP.getValue("14");
                if (rawV.isPresent())
                    return s.setValue(rawP, (java.lang.Comparable) rawV.get());
            }
            return s;
        }, false);
        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(11, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{}"));
            } catch (Exception ignored) {}
        }, false);


        scene.effects().indicateRedstone(util.grid().at(11, 1, 2));


        scene.idleSeconds(1);


        {
            var textBuilder = scene.overlay().showText(60)
                .text("在sable实体上时，输出也会随物理体的旋转变化")
                .pointAt(util.vector().of(2.5, 1.5, 2.5));
            textBuilder.placeNearTarget();
        }
    }

    public static void directional_synchronizer_slave_p2(SceneBuilder builder, SceneBuildingUtil util) {
        // 默认模式
        var scene = new CreateSceneBuilder(builder);
        scene.title("directional_synchronizer_slave_s_70422", "默认模式");
        scene.configureBasePlate(0, 0, 5);


        scene.addInstruction(ps -> {
            var yRot = ps.getTransform().yRotation;
            yRot.setValue(320.0f);
            yRot.chase(320.0f, 0.3f, net.createmod.catnip.animation.LerpedFloat.Chaser.EXP);
        });
        scene.world().showSection(util.select().everywhere(), Direction.UP);


        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, java.lang.Boolean.FALSE) : s, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR) : s, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST) : s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST) : s, false);
        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(9, 1, 3)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{HasAttachedComputer:0b,RedstoneStrength:15}"));
            } catch (Exception ignored) {}
        }, false);


        // Move camera center to (5.0, 0.5, 2.5) via reflection
        scene.addInstruction(ps -> {
            try {
                var sceneClass = Class.forName("net.createmod.ponder.foundation.PonderScene");
                var offsetXField = sceneClass.getDeclaredField("basePlateOffsetX");
                var offsetZField = sceneClass.getDeclaredField("basePlateOffsetZ");
                offsetXField.setAccessible(true);
                offsetZField.setAccessible(true);
                offsetXField.setInt(ps, 2);
                offsetZField.setInt(ps, 0);
            } catch (Exception e) {}
        });
        scene.setSceneOffsetY(0.5f);
        scene.idle(20);


        scene.idleSeconds(1);


        {
            var textBuilder = scene.overlay().showText(20)
                .text("用红石火把右击母方块进入红石镜像模式")
                .pointAt(new Vec3(2.5, 1.5, 3.0));
            textBuilder.placeNearTarget();
        }


        {
            var inputBuilder = scene.overlay().showControls(new Vec3(2.5, 2.0, 2.5), Pointing.DOWN, 20);
            inputBuilder.rightClick();
            inputBuilder.withItem(new ItemStack(
                BuiltInRegistries.ITEM.getOptional(
                    ResourceLocation.parse("minecraft:redstone_torch")).orElse(Items.STICK)));
        }


        scene.idleSeconds(1);


        scene.effects().indicateRedstone(util.grid().at(2, 1, 2));


        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, java.lang.Boolean.FALSE) : s, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR) : s, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST) : s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST) : s, false);
        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(9, 1, 3)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{HasAttachedComputer:0b,RedstoneStrength:0}"));
            } catch (Exception ignored) {}
        }, false);


        scene.idleSeconds(2);


        {
            var textBuilder = scene.overlay().showText(20)
                .text("母同步器会将每个面的红石信号同步给子同步器")
                .pointAt(util.vector().of(2.5, 1.5, 2.5));
            textBuilder.placeNearTarget();
        }


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
            scene.world().modifyBlock(util.grid().at(2, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR) : s, false);
            scene.world().modifyBlock(util.grid().at(2, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.NORTH) : s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.NORTH) : s, false);
        }
        // Entrance animation / 入场动画: 独立 section 从 down 方向飞入
        scene.world().showIndependentSection(util.select().position(util.grid().at(2, 1, 3)), Direction.DOWN);


        scene.idle(10);


        {
            var inputBuilder = scene.overlay().showControls(new Vec3(2.5, 2.0, 3.5), Pointing.DOWN, 20);
            inputBuilder.rightClick();
        }


        {
            var state = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.parse("create:analog_lever")).orElse(Blocks.STONE).defaultBlockState();
            scene.world().setBlock(util.grid().at(2, 1, 3), state, false);
            // Apply block properties with typed references / 应用方块属性
            scene.world().modifyBlock(util.grid().at(2, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR) : s, false);
            scene.world().modifyBlock(util.grid().at(2, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.NORTH) : s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.NORTH) : s, false);
        }


        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, java.lang.Boolean.FALSE) : s, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR) : s, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST) : s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST) : s, false);
        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(9, 1, 3)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{HasAttachedComputer:0b,RedstoneStrength:1}"));
            } catch (Exception ignored) {}
        }, false);


        scene.idle(10);


        {
            var state = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.parse("create:analog_lever")).orElse(Blocks.STONE).defaultBlockState();
            scene.world().setBlock(util.grid().at(2, 1, 3), state, false);
            // Apply block properties with typed references / 应用方块属性
            scene.world().modifyBlock(util.grid().at(2, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR) : s, false);
            scene.world().modifyBlock(util.grid().at(2, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.NORTH) : s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.NORTH) : s, false);
        }


        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, java.lang.Boolean.FALSE) : s, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR) : s, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST) : s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST) : s, false);
        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(9, 1, 3)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{HasAttachedComputer:0b,RedstoneStrength:2}"));
            } catch (Exception ignored) {}
        }, false);


        scene.idle(10);


        {
            var state = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.parse("create:analog_lever")).orElse(Blocks.STONE).defaultBlockState();
            scene.world().setBlock(util.grid().at(2, 1, 3), state, false);
            // Apply block properties with typed references / 应用方块属性
            scene.world().modifyBlock(util.grid().at(2, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR) : s, false);
            scene.world().modifyBlock(util.grid().at(2, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.NORTH) : s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.NORTH) : s, false);
        }


        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, java.lang.Boolean.FALSE) : s, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR) : s, false);
        scene.world().modifyBlock(util.grid().at(9, 1, 3), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST) : s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST) : s, false);
        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(9, 1, 3)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{HasAttachedComputer:0b,RedstoneStrength:3}"));
            } catch (Exception ignored) {}
        }, false);


        scene.idleSeconds(1);


        // Erase original block from base section / 擦除原位方块防止重叠
        scene.addInstruction(ps -> {
            ps.getBaseWorldSection().erase(util.select().position(util.grid().at(3, 1, 2)));
            ps.getBaseWorldSection().queueRedraw();
        });
        {
            var state = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.parse("minecraft:lever")).orElse(Blocks.STONE).defaultBlockState();
            scene.world().setBlock(util.grid().at(3, 1, 2), state, false);
            // Apply block properties with typed references / 应用方块属性
            scene.world().modifyBlock(util.grid().at(3, 1, 2), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR) : s, false);
            scene.world().modifyBlock(util.grid().at(3, 1, 2), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, java.lang.Boolean.FALSE) : s, false);
            scene.world().modifyBlock(util.grid().at(3, 1, 2), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.NORTH) : s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.NORTH) : s, false);
        }
        // Entrance animation / 入场动画: 独立 section 从 down 方向飞入
        scene.world().showIndependentSection(util.select().position(util.grid().at(3, 1, 2)), Direction.DOWN);


        scene.idleSeconds(1);


        {
            var state = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.parse("minecraft:lever")).orElse(Blocks.STONE).defaultBlockState();
            scene.world().setBlock(util.grid().at(3, 1, 2), state, false);
            // Apply block properties with typed references / 应用方块属性
            scene.world().modifyBlock(util.grid().at(3, 1, 2), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR) : s, false);
            scene.world().modifyBlock(util.grid().at(3, 1, 2), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, java.lang.Boolean.TRUE) : s, false);
            scene.world().modifyBlock(util.grid().at(3, 1, 2), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.NORTH) : s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.NORTH) : s, false);
        }


        scene.effects().indicateRedstone(util.grid().at(3, 1, 2));


        scene.world().modifyBlock(util.grid().at(10, 1, 2), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, java.lang.Boolean.FALSE) : s, false);
        scene.world().modifyBlock(util.grid().at(10, 1, 2), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR) : s, false);
        scene.world().modifyBlock(util.grid().at(10, 1, 2), s -> s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST) : s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) ? s.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST) : s, false);
        scene.world().modifyBlockEntityNBT(util.select().position(util.grid().at(10, 1, 2)), BlockEntity.class, nbt -> {
            try {
                nbt.merge(TagParser.parseTag("{HasAttachedComputer:0b,RedstoneStrength:15}"));
            } catch (Exception ignored) {}
        }, false);


        scene.idleSeconds(1);


        {
            var textBuilder = scene.overlay().showText(60)
                .text("在sable实体上时，输出也会随旋转变化")
                .pointAt(util.vector().of(2.5, 1.5, 2.5));
            textBuilder.placeNearTarget();
        }
    }
}
