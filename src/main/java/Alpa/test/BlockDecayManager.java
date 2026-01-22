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
        // 데이터가 저장된 dataStorage의 설정을 가져옵니다.
        ConfigurationSection config = plugin.dataStorage.getConfig();

        Map<Chest, Set<String>> chestMissingItems = new HashMap<>();
        Set<Chest> scanedChests = new HashSet<>();

        // 1. 판자(planks) 섹션 처리
        ConfigurationSection planksSection = config.getConfigurationSection("planks");
        if (planksSection != null) {
            for (String key : planksSection.getKeys(false)) {
                processDecayWithTracking(key, "planks", scanedChests, chestMissingItems);
            }
        }

        // 2. 문(doors) 섹션 처리
        ConfigurationSection doorsSection = config.getConfigurationSection("doors");
        if (doorsSection != null) {
            for (String key : doorsSection.getKeys(false)) {
                processDecayWithTracking(key, "doors", scanedChests, chestMissingItems);
            }
        }

        // 상자 이름 업데이트
        for (Chest chest : scanedChests) {
            if (chestMissingItems.containsKey(chest) && !chestMissingItems.get(chest).isEmpty()) {
                String missingList = String.join(", ", chestMissingItems.get(chest));
                updateChestName(chest, "§c[재료 부족] " + missingList + " 필요!");
            } else {
                updateChestName(chest, "§a[유지 중] 보관함");
            }
        }

        // 변경된 체력을 데이터 저장소에 저장
        plugin.dataStorage.saveConfig();
    }

    private void processDecayWithTracking(String key, String sectionName, Set<Chest> scanedChests, Map<Chest, Set<String>> chestMissingItems) {
        Block b = getBlockFromKey(key);
        // 블록이 공기라면(이미 파괴됨) 데이터에서도 삭제하고 종료
        if (b == null || b.getType() == Material.AIR) {
            plugin.dataStorage.getConfig().set(sectionName + "." + key, null);
            return;
        }

        Chest chest = findMaintenanceChest(b);
        boolean isMaintained = false;

        if (chest != null) {
            scanedChests.add(chest);
            // 블록 타입에 따른 유지 비용 결정
            Material costItem = (b.getType() == Material.IRON_BLOCK) ? Material.IRON_INGOT : Material.OAK_PLANKS;
            String itemName = (b.getType() == Material.IRON_BLOCK) ? "철괴" : "나무판자";

            if (chest.getInventory().contains(costItem, 1)) {
                // plugin.getConfig()에서 설정값을 읽어옴 (확률)
                String ratioKey = (b.getType() == Material.IRON_BLOCK) ? "iron-ratio" : "plank-ratio";
                int ratio = plugin.getConfig().getInt("maintenance-settings." + ratioKey, 10);

                if (random.nextInt(Math.max(1, ratio)) == 0) {
                    removeItemFromChest(chest, costItem, 1);
                }
                isMaintained = true;
            } else {
                chestMissingItems.computeIfAbsent(chest, k -> new HashSet<>()).add(itemName);
            }
        }

        // 유지보수 중이 아니라면 체력 감소
        if (!isMaintained) {
            String path = sectionName + "." + key + ".health";
            int hp = plugin.dataStorage.getConfig().getInt(path, 100);
            int newHp = hp - 1;

            if (newHp <= 0) {
                // 체력이 0이 되면 블록 파괴 (destroyBlockByKey 내부에서 saveConfig 호출됨)
                String typeSuffix = sectionName.substring(0, sectionName.length() - 1); // planks -> plank
                plugin.destroyBlockByKey(key, typeSuffix);
            } else {
                plugin.dataStorage.getConfig().set(path, newHp);
            }
        }
    }

    // 인벤토리에서 아이템 제거 보조 함수
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
        chest.update(true); // 리포스 강제 업데이트
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
        try {
            return w.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (Exception e) {
            return null;
        }
    }
}