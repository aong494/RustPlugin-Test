package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class BlockDecayManager extends BukkitRunnable {

    private final main plugin;

    public BlockDecayManager(main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        ConfigurationSection dataConfig = plugin.dataStorage.getConfig();
        FileConfiguration mConfig = plugin.maintenanceConfig; // maintenance.yml

        // 상자별로 어떤 블록이 몇 개 있는지 저장
        Map<Chest, Map<Material, Integer>> chestBlockCounts = new HashMap<>();
        // 유지보수를 받지 못하는 블록 키 저장
        Set<String> unmaintainedPlanks = new HashSet<>();
        Set<String> unmaintainedDoors = new HashSet<>();

        // 1. 모든 블록 스캔 및 관리 상자 매칭
        scanSection("planks", dataConfig, chestBlockCounts, unmaintainedPlanks);
        scanSection("doors", dataConfig, chestBlockCounts, unmaintainedDoors);

        Map<Chest, Set<String>> chestMissingItems = new HashMap<>();
        Set<Chest> processedChests = new HashSet<>(chestBlockCounts.keySet());

        // 2. 상자별 비용 계산 및 차감
        for (Chest chest : processedChests) {
            Map<Material, Integer> counts = chestBlockCounts.get(chest);

            for (Material blockType : counts.keySet()) {
                int count = counts.get(blockType);
                String typeName = getSettingKey(blockType); // 설정 키 추출 로직 분리

                String path = "costs." + typeName;
                if (!mConfig.contains(path)) {
                    // 설정이 없으면 체력 깎음
                    markAsUnmaintainedInChest(chest, blockType, unmaintainedPlanks, unmaintainedDoors, dataConfig);
                    continue;
                }

                Material costItem = Material.valueOf(mConfig.getString(path + ".item"));
                int perAmount = mConfig.getInt(path + ".amount", 10);
                int costQty = mConfig.getInt(path + ".cost", 1);
                int totalRequired = (int) Math.ceil((double) count / perAmount) * costQty;

                if (chest.getInventory().contains(costItem, totalRequired)) {
                    removeItemFromChest(chest, costItem, totalRequired);
                } else {
                    // [핵심] 재료가 부족하면 해당 상자 범위 내의 블록들 체력을 깎도록 등록
                    chestMissingItems.computeIfAbsent(chest, k -> new HashSet<>()).add(getItemKoreanName(costItem));
                    markAsUnmaintainedInChest(chest, blockType, unmaintainedPlanks, unmaintainedDoors, dataConfig);
                }
            }
        }

        // 3. 상자 이름 업데이트 (상태 표시)
        for (Chest chest : processedChests) {
            updateStatusName(chest, chestMissingItems.get(chest));
        }

        // 4. 유지보수 실패한 블록들 체력 감소
        applyDecay(unmaintainedPlanks, "planks");
        applyDecay(unmaintainedDoors, "doors");

        plugin.dataStorage.saveConfig();
    }

    private String getSettingKey(Material mat) {
        String name = mat.name();
        if (name.equals("GOLD_BLOCK")) return "GOLD_BLOCK";
        if (name.equals("IRON_BLOCK")) return "IRON_BLOCK";
        if (name.contains("STONE_BRICK")) return "STONE_BRICKS";
        if (name.contains("_PLANKS") || name.contains("_STAIRS")) return "WOODEN_PLANK";
        return name;
    }

    private void scanSection(String section, ConfigurationSection config, Map<Chest, Map<Material, Integer>> counts, Set<String> unmaintained) {
        ConfigurationSection s = config.getConfigurationSection(section);
        if (s == null) return;

        for (String key : s.getKeys(false)) {
            Block b = getBlockFromKey(key);
            if (b == null || b.getType() == Material.AIR) {
                config.set(section + "." + key, null);
                continue;
            }

            Chest chest = findMaintenanceChest(b);
            if (chest != null) {
                counts.computeIfAbsent(chest, k -> new HashMap<>());
                Map<Material, Integer> typeMap = counts.get(chest);
                typeMap.put(b.getType(), typeMap.getOrDefault(b.getType(), 0) + 1);
            } else {
                unmaintained.add(key);
            }
        }
    }

    private void markAsUnmaintainedInChest(Chest chest, Material type, Set<String> planks, Set<String> doors, ConfigurationSection dataConfig) {
        int radius = plugin.maintenanceConfig.getInt("settings.radius", 10);
        org.bukkit.Location loc = chest.getLocation();

        // 상자 주변의 데이터를 뒤져서 해당 타입의 블록 키를 찾아냄
        // 1. 판자 섹션 뒤지기
        ConfigurationSection pSec = dataConfig.getConfigurationSection("planks");
        if (pSec != null) {
            for (String key : pSec.getKeys(false)) {
                if (isBlockInRadius(key, loc, radius)) {
                    Block b = getBlockFromKey(key);
                    if (b != null && b.getType() == type) {
                        planks.add(key); // 체력 감소 대상에 추가
                    }
                }
            }
        }
        // 2. 문 섹션 뒤지기
        ConfigurationSection dSec = dataConfig.getConfigurationSection("doors");
        if (dSec != null) {
            for (String key : dSec.getKeys(false)) {
                if (isBlockInRadius(key, loc, radius)) {
                    Block b = getBlockFromKey(key);
                    if (b != null && b.getType() == type) {
                        doors.add(key); // 체력 감소 대상에 추가
                    }
                }
            }
        }
    }

    private boolean isBlockInRadius(String key, org.bukkit.Location center, int radius) {
        String[] p = key.split("_");
        if (!p[0].equals(center.getWorld().getName())) return false;
        int x = Integer.parseInt(p[1]);
        int y = Integer.parseInt(p[2]);
        int z = Integer.parseInt(p[3]);
        return Math.abs(x - center.getBlockX()) <= radius &&
                Math.abs(y - center.getBlockY()) <= radius &&
                Math.abs(z - center.getBlockZ()) <= radius;
    }

    private void applyDecay(Set<String> keys, String section) {
        int decayAmt = plugin.maintenanceConfig.getInt("settings.decay-amount", 1);
        for (String key : keys) {
            String path = section + "." + key + ".health";
            int hp = plugin.dataStorage.getConfig().getInt(path, 50);
            int newHp = hp - decayAmt;

            if (newHp <= 0) {
                plugin.destroyBlockByKey(key, section.substring(0, section.length() - 1));
            } else {
                plugin.dataStorage.getConfig().set(path, newHp);
            }
        }
    }

    private void updateStatusName(Chest chest, Set<String> missing) {
        if (missing == null || missing.isEmpty()) {
            updateChestName(chest, "§a[유지 중] 보관함");
        } else {
            updateChestName(chest, "§c[재료 부족] " + String.join(", ", missing) + " 필요!");
        }
    }

    // --- 유틸리티 메서드 ---

    private void removeItemFromChest(Chest chest, Material material, int amount) {
        ItemStack[] contents = chest.getInventory().getContents();
        int remaining = amount;
        for (ItemStack is : contents) {
            if (is != null && is.getType() == material) {
                if (is.getAmount() > remaining) {
                    is.setAmount(is.getAmount() - remaining);
                    remaining = 0;
                } else {
                    remaining -= is.getAmount();
                    is.setAmount(0);
                }
            }
            if (remaining <= 0) break;
        }
        chest.getInventory().setContents(contents);
    }

    private void updateChestName(Chest chest, String name) {
        if (name.equals(chest.getCustomName())) return;
        chest.setCustomName(name);
        chest.update(true);
    }

    private Chest findMaintenanceChest(Block b) {
        int radius = plugin.maintenanceConfig.getInt("settings.radius", 10);
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
    private String getItemKoreanName(Material mat) {
        switch (mat) {
            case IRON_INGOT: return "철괴";
            case COBBLESTONE: return "조약돌";
            case OAK_PLANKS: return "나무판자";
            default: return mat.name();
        }
    }
}