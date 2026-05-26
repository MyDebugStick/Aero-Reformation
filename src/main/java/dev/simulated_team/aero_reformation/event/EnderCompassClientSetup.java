package dev.simulated_team.aero_reformation.event;

import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderCompassData;
import dev.simulated_team.aero_reformation.content.items.ender_compass.EnderCompassScreen;
import dev.simulated_team.aero_reformation.registrate.AeroBlocks;
import dev.simulated_team.aero_reformation.registrate.AeroDataComponents;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.GlobalPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

import static dev.simulated_team.aero_reformation.AeroReformation.MODID;

@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class EnderCompassClientSetup {

    public static final KeyMapping ENDER_COMPASS_KEY = new KeyMapping(
            "key.aero_reformation.ender_compass",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.aero_reformation");

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(ENDER_COMPASS_KEY);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        while (ENDER_COMPASS_KEY.consumeClick()) {
            ItemStack held = mc.player.getMainHandItem();
            EnderCompassData data = held.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
            if (data.hasChannel()) {
                mc.setScreen(new EnderCompassScreen(held));
            }
        }
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.renderer.item.ItemProperties.register(
                    AeroBlocks.ENDER_COMPASS.get(),
                    net.minecraft.resources.ResourceLocation.parse("angle"),
                    (ItemStack stack, @Nullable ClientLevel level,
                     @Nullable LivingEntity entity, int seed) -> {
                        Entity viewer = entity != null ? entity : stack.getEntityRepresentation();
                        if (viewer == null) return 0.0f;

                        if (level == null && viewer.level() instanceof ClientLevel cl1)
                            level = cl1;
                        if (level == null) return 0.0f;

                        EnderCompassData data = stack.getOrDefault(AeroDataComponents.ENDER_COMPASS, EnderCompassData.EMPTY);
                        Optional<GlobalPos> target = data.target();

                        double dx, dz;
                        if (target.isPresent() && target.get().dimension() == level.dimension()) {
                            GlobalPos pos = target.get();
                            dx = pos.pos().getX() - viewer.getX();
                            dz = pos.pos().getZ() - viewer.getZ();
                        } else {
                            // No target set — point to world origin (0, 0)
                            dx = 0.0 - viewer.getX();
                            dz = 0.0 - viewer.getZ();
                        }

                        double angle = (viewer.getYRot() * Mth.DEG_TO_RAD) - Math.atan2(dz, dx);
                        float raw = Mth.positiveModulo((float) (angle / (Math.PI * 2.0)), 1.0f);
                        return Mth.positiveModulo(0.75f - raw, 1.0f);
                    });
        });
    }
}
