package dev.simulated_team.aero_reformation.event;

import dev.simulated_team.aero_reformation.config.AeroReformationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = "aero_reformation")
public class LevititeMiningHandler {

    private static final ResourceLocation LEVITITE = ResourceLocation.parse("aeronautics:levitite");
    private static final ResourceLocation PEARLESCENT_LEVITITE = ResourceLocation.parse("aeronautics:pearlescent_levitite");

    /** Diamond pickaxe speed / obsidian hardness = 8.0 / 50.0. */
    private static final float OBSIDIAN_SPEED_RATIO = Tiers.DIAMOND.getSpeed() / 50.0f; // 0.16

    private static boolean isLevitite(BlockState state) {
        ResourceLocation id = state.getBlockHolder().getKey().location();
        return id.equals(LEVITITE) || id.equals(PEARLESCENT_LEVITITE);
    }

    private static boolean isGoldPickaxe(ItemStack stack) {
        return stack.getItem() instanceof PickaxeItem p && p.getTier() == Tiers.GOLD;
    }

    private static boolean hasSilkTouch(ItemStack stack) {
        ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        return ench.keySet().stream().anyMatch(h -> h.is(Enchantments.SILK_TOUCH));
    }

    // ==================== BreakSpeed ====================

    @SubscribeEvent
    public static void onBreakSpeed(final PlayerEvent.BreakSpeed event) {
        if (!AeroReformationConfig.levititeGoldPickaxeOnly) return;

        Player player = event.getEntity();
        if (player.isCreative() || player.isSpectator()) return;
        if (!isLevitite(event.getState())) return;

        ItemStack held = player.getMainHandItem();

        if (isGoldPickaxe(held)) {
            if (hasSilkTouch(held)) {
                // Scale speed so total mining time equals diamond pickaxe on obsidian
                BlockPos pos = event.getPosition().orElse(null);
                if (pos != null) {
                    float hardness = event.getState().getDestroySpeed(player.level(), pos);
                    if (hardness > 0) {
                        event.setNewSpeed(hardness * OBSIDIAN_SPEED_RATIO);
                    }
                }
            } else {
                event.setNewSpeed(0f);
            }
        }
        // Other tools: normal speed, drops handled in BreakEvent
    }

    // ==================== BreakEvent ====================

    @SubscribeEvent
    public static void onBlockBreak(final BlockEvent.BreakEvent event) {
        if (!AeroReformationConfig.levititeGoldPickaxeOnly) return;

        Player player = event.getPlayer();
        if (player == null || player.isCreative() || player.isSpectator()) return;
        if (!isLevitite(event.getState())) return;

        ItemStack held = player.getMainHandItem();

        if (isGoldPickaxe(held) && hasSilkTouch(held)) {
            // Valid tool: cancel default handling, break with drops + double durability
            event.setCanceled(true);
            if (!player.level().isClientSide()) {
                LevelAccessor levelAccessor = event.getLevel();
                Level level = (Level) levelAccessor;
                BlockState state = event.getState();
                BlockPos pos = event.getPos();
                // Remove block
                level.removeBlock(pos, false);
                // Manually spawn the block as drop (silk touch behavior)
                ItemStack drop = new ItemStack(state.getBlock().asItem());
                Block.popResource(level, pos, drop);
                // Apply exactly 2 durability damage, bypassing Unbreaking
                int dmg = held.getOrDefault(DataComponents.DAMAGE, 0);
                held.set(DataComponents.DAMAGE, dmg + 2);
                if (held.getOrDefault(DataComponents.DAMAGE, 0) >= held.getMaxDamage()) {
                    held.shrink(1);
                }
            }
        } else {
            // Wrong tool → break without drops
            event.setCanceled(true);
            event.getLevel().destroyBlock(event.getPos(), false);
        }
    }
}
