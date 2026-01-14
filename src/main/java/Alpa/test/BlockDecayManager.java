package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class BlockDecayManager extends BukkitRunnable {

    private final main plugin;
    private final Random random = new Random();

    public BlockDecayManager(main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // 이번 주기에서 확인된 덫상자들의 부족한 재료 상태를 저장할 맵
        // Chest -> 부족한 재료들의 이름 Set
        Map<Chest, Set<String>> chestMissingItems = new HashMap<>();
        // 이번 주기에서 확인된 모든 덫상자 목록 (이름 초기화를 위함)
        Set<Chest> scanedChests = new HashSet<>();

        ConfigurationSection planks = plugin.getConfig().getConfigurationSection("planks");
        if (planks != null) {
            for (String key : planks.getKeys(false)) {
                processDecayWithTracking(key, "plank", scanedChests, chestMissingItems);
            }
        }

        ConfigurationSection doors = plugin.getConfig().getConfigurationSection("doors");
        if (doors != null) {
            for (String key : doors.getKeys(false)) {
                processDecayWithTracking(key, "door", scanedChests, chestMissingItems);
            }
        }

        // 모든 검사가 끝난 후 상자 이름 일괄 업데이트
        for (Chest chest : scanedChests) {
            if (chestMissingItems.containsKey(chest) && !chestMissingItems.get(chest).isEmpty()) {
                String missingList = String.join(", ", chestMissingItems.get(chest));
                updateChestName(chest, "§c[재료 부족] " + missingList + " 필요!");
            } else {
                updateChestName(chest, "§a[유지 중] 보관함");
            }
        }

        plugin.saveConfig();
    }

    private void processDecayWithTracking(String key, String type, Set<Chest> scanedChests, Map<Chest, Set<String>> chestMissingItems) {
        Block b = getBlockFromKey(key);
        if (b == null || b.getType() == Material.AIR) return;

        Chest chest = findMaintenanceChest(b);
        boolean isMaintained = false;

        if (chest != null) {
            scanedChests.add(chest);
            Material costItem = (b.getType() == Material.IRON_BLOCK) ? Material.IRON_INGOT : Material.OAK_PLANKS;
            String itemName = (b.getType() == Material.IRON_BLOCK) ? "철괴" : "나무판자";

            if (chest.getInventory().contains(costItem, 1)) {
                String ratioKey = (b.getType() == Material.IRON_BLOCK) ? "iron-ratio" : "plank-ratio";
                int ratio = plugin.getConfig().getInt("maintenance-settings." + ratioKey, 10);

                if (random.nextInt(ratio) == 0) {
                    chest.getInventory().removeItem(new ItemStack(costItem, 1));
                }
                isMaintained = true;
            } else {
                // 부족한 아이템 목록에 추가
                chestMissingItems.computeIfAbsent(chest, k -> new HashSet<>()).add(itemName);
            }
        }

        if (!isMaintained) {
            String fullPath = type + "s." + key + ".health";
            int hp = plugin.getConfig().getInt(fullPath);
            int newHp = hp - 1;
            if (newHp <= 0) {
                plugin.destroyBlockByKey(key, type);
            } else {
                plugin.getConfig().set(fullPath, newHp);
            }
        }
    }

    private void updateChestName(Chest chest, String name) {
        if (name.equals(chest.getCustomName())) return;
        chest.setCustomName(name);
        chest.update();
    }

    private Chest findMaintenanceChest(Block b) {
        int radius = plugin.getConfig().getInt("maintenance-settings.radius", 5);
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block rel = b.getRelative(x, y, z);
                    if (rel.getType() == Material.TRAPPED_CHEST) {
                        return (Chest) rel.getState();
                    }
                }
            }
        }
        return null;
    }

    private Block getBlockFromKey(String key) {
        String[] parts = key.split("_");
        if (parts.length < 4) return null;
        World w = Bukkit.getWorld(parts[0]);
        if (w == null) return null;
        return w.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }
}