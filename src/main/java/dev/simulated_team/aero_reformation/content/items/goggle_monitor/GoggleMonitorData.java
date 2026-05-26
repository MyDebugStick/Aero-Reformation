package dev.simulated_team.aero_reformation.content.items.goggle_monitor;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GoggleMonitorData {

    /** Per-player sensor bindings. Cleared when goggles removed. */
    static final Map<UUID, List<SensorMonitorEntry>> PLAYER_BINDINGS = new ConcurrentHashMap<>();

    private static final String PERSIST_KEY = "aero_reformation_goggle_bindings";

    public static String sensorDefaultName(String type) {
        return switch (type) {
            case "altitude_sensor" -> "高度传感器";
            case "velocity_sensor" -> "速度传感器";
            case "gimbal_sensor" -> "姿态分析仪";
            case "nav_table" -> "导航台";
            case "redstone_link" -> "无线终端";
            default -> type;
        };
    }

    public static List<SensorMonitorEntry> getEntries(Player player) {
        return PLAYER_BINDINGS.getOrDefault(player.getUUID(), Collections.emptyList());
    }

    public static void clearEntries(Player player) {
        PLAYER_BINDINGS.remove(player.getUUID());
        saveToPlayer(player);
    }

    public static boolean addEntry(Player player, SensorMonitorEntry entry) {
        List<SensorMonitorEntry> entries = PLAYER_BINDINGS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        for (SensorMonitorEntry e : entries) {
            if (e.pos().equals(entry.pos()) && e.sensorType().equals(entry.sensorType())) {
                return false;
            }
        }
        entries.add(entry);
        saveToPlayer(player);
        return true;
    }

    /** Load bindings from player persistent data on login */
    public static void loadFromPlayer(Player player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(PERSIST_KEY, Tag.TAG_LIST)) return;
        ListTag list = persistent.getList(PERSIST_KEY, Tag.TAG_COMPOUND);
        List<SensorMonitorEntry> entries = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            SensorMonitorEntry entry = SensorMonitorEntry.fromNBT(tag);
            if (entry != null) entries.add(entry);
        }
        if (!entries.isEmpty()) {
            PLAYER_BINDINGS.put(player.getUUID(), entries);
        }
    }

    /** Save bindings to player persistent data */
    public static void saveToPlayer(Player player) {
        List<SensorMonitorEntry> entries = PLAYER_BINDINGS.getOrDefault(player.getUUID(), Collections.emptyList());
        CompoundTag persistent = player.getPersistentData();
        if (entries.isEmpty()) {
            persistent.remove(PERSIST_KEY);
        } else {
            ListTag list = new ListTag();
            for (SensorMonitorEntry e : entries) {
                list.add(e.toNBT());
            }
            persistent.put(PERSIST_KEY, list);
        }
    }

    public static ItemStack findGoggles(Player player) {
        ItemStack head = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        if (isGoggles(head)) return head;
        return ItemStack.EMPTY;
    }

    public static boolean isGoggles(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        String id = key.toString();
        return id.equals("create:goggles") || id.equals("create:engineers_goggles");
    }
}
