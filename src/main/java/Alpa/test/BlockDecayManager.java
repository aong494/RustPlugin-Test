package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container; // 심볼 해결을 위해 반드시 필요
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import java.lang.reflect.Field;

import java.lang.reflect.Method;
import java.util.*;

public class BlockDecayManager extends BukkitRunnable {

    private final main plugin;

    public BlockDecayManager(main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        ConfigurationSection dataConfig = plugin.dataStorage.getConfig();
        FileConfiguration mConfig = plugin.maintenanceConfig;

        Map<Location, Map<Material, Integer>> locationBlockCounts = new HashMap<>();
        Set<String> unmaintainedPlanks = new HashSet<>();
        Set<String> unmaintainedDoors = new HashSet<>();

        // 1. 블록 스캔 (AIR 및 모드 블록 대응)
        scanSection("planks", dataConfig, locationBlockCounts, unmaintainedPlanks);
        scanSection("doors", dataConfig, locationBlockCounts, unmaintainedDoors);

        Map<Location, Set<String>> locationMissingItems = new HashMap<>();

        // 2. 도구함별 자원 차감
        for (Map.Entry<Location, Map<Material, Integer>> entry : locationBlockCounts.entrySet()) {
            Location cupLoc = entry.getKey();
            Map<Material, Integer> counts = entry.getValue();

            Inventory inv = getInventoryAt(cupLoc);
            System.out.println("[디버그] 위치: " + cupLoc.getBlockX() + ", " + cupLoc.getBlockY() + ", " + cupLoc.getBlockZ());
            System.out.println("[디버그] 도구함 인벤토리 감지 결과: " + (inv == null ? "실패" : "성공 (" + inv.getSize() + "칸)"));

            if (inv != null) {
                boolean allMaintained = true;
                for (Map.Entry<Material, Integer> countEntry : counts.entrySet()) {
                    Material blockType = countEntry.getKey();
                    int count = countEntry.getValue();

                    String typeName = getSettingKey(blockType);
                    String path = "costs." + typeName;

                    if (!mConfig.contains(path)) continue;

                    Material costItem = Material.valueOf(mConfig.getString(path + ".item"));
                    int perAmount = mConfig.getInt(path + ".amount", 10);
                    int costQty = mConfig.getInt(path + ".cost", 1);
                    int totalRequired = (int) Math.ceil((double) count / perAmount) * costQty;

                    if (inv.contains(costItem, totalRequired)) {
                        removeItemFromInventory(inv, costItem, totalRequired);
                    } else {
                        allMaintained = false;
                        locationMissingItems.computeIfAbsent(cupLoc, k -> new HashSet<>()).add(getItemKoreanName(costItem));
                        markAsUnmaintainedAtLocation(cupLoc, unmaintainedPlanks, unmaintainedDoors, dataConfig);
                    }
                }
                updateStatusAtLocation(cupLoc, locationMissingItems.get(cupLoc));
            } else {
                markAsUnmaintainedAtLocation(cupLoc, unmaintainedPlanks, unmaintainedDoors, dataConfig);
            }
        }

        // 3. 체력 감소 적용
        applyDecay(unmaintainedPlanks, "planks");
        applyDecay(unmaintainedDoors, "doors");

        plugin.dataStorage.saveConfig();
    }
    private Inventory getInventoryAt(Location loc) {
        Block block = loc.getBlock();

        if (block.getType() == Material.TRAPPED_CHEST) {
            if (block.getState() instanceof org.bukkit.block.Chest chest) {
                return chest.getInventory(); // 100% 성공 보장
            }
        }
        return null;
    }
    private void updateStatusAtLocation(Location loc, Set<String> missing) {
        org.bukkit.block.BlockState state = loc.getBlock().getState();
        // Container 인터페이스가 이름을 바꿀 수 있는 상위 객체입니다.
        if (!(state instanceof Container)) {
            state = loc.getBlock().getRelative(0, 1, 0).getState();
        }

        if (state instanceof Container container) {
            String status = (missing == null || missing.isEmpty()) ? "§a[보호 중]" : "§c[재료 부족]";
            container.setCustomName(status + " 도구함");
            state.update(true);
        }
    }

    private Location findMaintenanceLocation(Block b) {
        int radius = plugin.maintenanceConfig.getInt("settings.radius", 15);
        Location blockLoc = b.getLocation();
        ConfigurationSection protectors = plugin.dataStorage.getConfig().getConfigurationSection("protectors");
        if (protectors == null) return null;

        for (String key : protectors.getKeys(false)) {
            String worldName = protectors.getString(key + ".world");
            if (worldName == null || !worldName.equals(blockLoc.getWorld().getName())) continue;

            Location cupLoc = new Location(blockLoc.getWorld(),
                    protectors.getInt(key + ".x"),
                    protectors.getInt(key + ".y"),
                    protectors.getInt(key + ".z"));

            if (cupLoc.distance(blockLoc) <= radius) return cupLoc;
        }
        return null;
    }

    // --- 나머지 지원 메서드 ---

    private void scanSection(String section, ConfigurationSection config, Map<Location, Map<Material, Integer>> counts, Set<String> unmaintained) {
        ConfigurationSection s = config.getConfigurationSection(section);
        if (s == null) return;

        for (String key : s.getKeys(false)) {
            Block b = getBlockFromKey(key);
            if (b == null) continue;

            Location cupLoc = findMaintenanceLocation(b);
            if (cupLoc != null) {
                counts.computeIfAbsent(cupLoc, k -> new HashMap<>());
                Material logicType = b.getType();
                if (logicType == Material.AIR) {
                    logicType = section.equals("planks") ? Material.OAK_PLANKS : Material.IRON_DOOR;
                }
                counts.get(cupLoc).put(logicType, counts.get(cupLoc).getOrDefault(logicType, 0) + 1);
            } else {
                unmaintained.add(key);
            }
        }
    }

    private void removeItemFromInventory(Inventory inv, Material material, int amount) {
        ItemStack[] contents = inv.getContents();
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
        inv.setContents(contents);
    }

    private void markAsUnmaintainedAtLocation(Location loc, Set<String> planks, Set<String> doors, ConfigurationSection dataConfig) {
        int radius = plugin.maintenanceConfig.getInt("settings.radius", 15);
        addBlocksInRadius(dataConfig.getConfigurationSection("planks"), loc, radius, planks);
        addBlocksInRadius(dataConfig.getConfigurationSection("doors"), loc, radius, doors);
    }

    private void addBlocksInRadius(ConfigurationSection section, Location loc, int radius, Set<String> targetSet) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            if (isBlockInRadius(key, loc, radius)) targetSet.add(key);
        }
    }

    private boolean isBlockInRadius(String key, Location center, int radius) {
        String[] p = key.split("_");
        if (!p[0].equals(center.getWorld().getName())) return false;
        try {
            int x = Integer.parseInt(p[1]), y = Integer.parseInt(p[2]), z = Integer.parseInt(p[3]);
            return Math.abs(x - center.getBlockX()) <= radius &&
                    Math.abs(z - center.getBlockZ()) <= radius;
        } catch (Exception e) { return false; }
    }

    private void applyDecay(Set<String> keys, String section) {
        int decayAmt = plugin.maintenanceConfig.getInt("settings.decay-amount", 1);
        for (String key : keys) {
            String path = section + "." + key + ".health";
            int hp = plugin.dataStorage.getConfig().getInt(path, 50);
            int newHp = Math.max(0, hp - decayAmt);
            plugin.dataStorage.getConfig().set(path, newHp);

            if (newHp <= 0) {
                Block b = getBlockFromKey(key);
                if (b != null && section.equals("doors")) plugin.removeLockEntity(b);
                plugin.destroyBlockByKey(key, section.substring(0, section.length() - 1));
            }
        }
    }

    private String getSettingKey(Material mat) {
        String name = mat.name();
        if (name.equals("AIR") || name.contains("_PLANKS") || name.contains("TOOL_CUPBOARD")) return "WOODEN_PLANK";
        if (name.contains("STONE_BRICK")) return "STONE_BRICKS";
        return name;
    }

    private Block getBlockFromKey(String key) {
        try {
            String[] parts = key.split("_");
            World world = Bukkit.getWorld(parts[0]);
            return (world != null) ? world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])) : null;
        } catch (Exception e) { return null; }
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