package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BlockDecayManager extends BukkitRunnable {
    private final main plugin;
    private boolean isProcessing = false;
    private final Map<String, List<Location>> cupboardCache = new ConcurrentHashMap<>();

    public BlockDecayManager(main plugin) {
        this.plugin = plugin;
    }

    // --- [복구 및 강화] 모드 클라이언트에 정보를 보내는 핵심 메서드 ---
    public void syncMaintenanceToPlayer(Player player, Location cupLoc) {
        ConfigurationSection dataConfig = plugin.dataStorage.getConfig();
        FileConfiguration mConfig = plugin.maintenanceConfig;

        // 1. 해당 도구함 영향권 내 블록 수 합산 (실시간 계산)
        Map<Material, Integer> counts = new HashMap<>();
        int radius = mConfig.getInt("settings.radius", 15);

        // planks와 doors 섹션을 돌며 이 도구함 범위 내 블록 카운트
        countBlocksForCupboard(dataConfig.getConfigurationSection("planks"), cupLoc, radius, counts);
        countBlocksForCupboard(dataConfig.getConfigurationSection("doors"), cupLoc, radius, counts);

        // 2. 설정값(maintenance.yml)에 따른 필요 자원량 계산
        Map<Material, Integer> totalCosts = new HashMap<>();
        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            String typeName = getSettingKey(entry.getKey());
            String path = "costs." + typeName;
            if (!mConfig.contains(path)) continue;

            Material costItem = Material.valueOf(mConfig.getString(path + ".item"));
            int perAmount = mConfig.getInt(path + ".amount", 10);
            int costQty = mConfig.getInt(path + ".cost", 1);

            // 올림 계산: (블록수 / 단위당) * 소모량
            int totalRequired = (int) Math.ceil((double) entry.getValue() / perAmount) * costQty;
            totalCosts.put(costItem, totalCosts.getOrDefault(costItem, 0) + totalRequired);
        }

        // 3. 현재 인벤토리와 대조하여 부족 여부 확인 및 텍스트 생성
        Inventory inv = getInventoryAt(cupLoc);
        boolean isDecaying = false;
        List<String> costStrings = new ArrayList<>();

        for (Map.Entry<Material, Integer> cost : totalCosts.entrySet()) {
            int currentInInv = countItemInInventory(inv, cost.getKey());
            if (currentInInv < cost.getValue()) isDecaying = true;

            // 모드가 읽을 수 있게 "아이템이름 x개" 형식으로 리스트업
            costStrings.add(getItemKoreanName(cost.getKey()) + " x" + cost.getValue());
        }

        String finalCostStr = costStrings.isEmpty() ? "유지보수 필요 없음" : String.join(", ", costStrings);

        // 4. [중요] 모드로 패킷 전송 (main 클래스에 정의된 메서드 호출)
        plugin.sendMaintenancePacket(player, isDecaying, finalCostStr);
    }

    @Override
    public void run() {
        if (isProcessing) return;

        // [최적화] 비동기 처리
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            isProcessing = true;
            ConfigurationSection config = plugin.dataStorage.getConfig();
            if (config == null) { isProcessing = false; return; }

            // 비동기 전용 캐시 업데이트
            updateCupboardCacheAsync(config);

            List<String> plankKeys = config.contains("planks") ? new ArrayList<>(config.getConfigurationSection("planks").getKeys(false)) : new ArrayList<>();
            List<String> doorKeys = config.contains("doors") ? new ArrayList<>(config.getConfigurationSection("doors").getKeys(false)) : new ArrayList<>();

            if (plankKeys.isEmpty() && doorKeys.isEmpty()) { isProcessing = false; return; }

            // 배치(Batch) 처리로 메인 스레드 부하 분산
            processInBatches(plankKeys, "planks");
            processInBatches(doorKeys, "doors");
            isProcessing = false;
        });
    }

    private void processInBatches(List<String> keys, String type) {
        int batchSize = 25; // 한 틱(0.05초)에 처리할 블록 개수
        for (int i = 0; i < keys.size(); i += batchSize) {
            final List<String> batch = keys.subList(i, Math.min(i + batchSize, keys.size()));

            // 실제 블록 및 인벤토리 조작은 메인 스레드에서 안전하게 수행
            Bukkit.getScheduler().runTask(plugin, () -> {
                int radius = plugin.maintenanceConfig.getInt("settings.radius", 15);
                for (String key : batch) {
                    handleSingleDecay(key, type, radius);
                }
            });

            // 비동기 스레드에서 메인 스레드에 과부하를 주지 않도록 미세한 대기
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
    }

    private void handleSingleDecay(String key, String type, int radius) {
        Block b = getBlockFromKey(key);
        if (b == null) return;

        Location cupLoc = findCupboardFromCache(b.getLocation(), radius);
        boolean isProtected = false;

        if (cupLoc != null) {
            Inventory inv = getInventoryAt(cupLoc);
            if (inv != null && consumeResource(inv, b.getType())) {
                isProtected = true;
            }
        }

        if (!isProtected) {
            applyHealthDamage(key, type);
        }
    }

    private void applyHealthDamage(String key, String type) {
        ConfigurationSection config = plugin.dataStorage.getConfig();
        String path = type + "." + key + ".health";
        int currentHp = config.getInt(path, 50);
        int decayAmt = plugin.maintenanceConfig.getInt("settings.decay-amount", 1);

        int newHp = currentHp - decayAmt;
        config.set(path, newHp);

        if (newHp <= 0) {
            plugin.destroyBlockByKey(key, type.substring(0, type.length() - 1));
        }
    }

    // --- 유틸리티 및 캐시 로직 ---

    private void updateCupboardCacheAsync(ConfigurationSection config) {
        ConfigurationSection protectors = config.getConfigurationSection("protectors");
        if (protectors == null) return;

        Map<String, List<Location>> newCache = new HashMap<>();
        for (String key : protectors.getKeys(false)) {
            Location loc = plugin.keyToLocation(key);
            if (loc != null && loc.getWorld() != null) {
                newCache.computeIfAbsent(loc.getWorld().getName(), k -> new ArrayList<>()).add(loc);
            }
        }
        cupboardCache.clear();
        cupboardCache.putAll(newCache);
    }

    private Location findCupboardFromCache(Location loc, int radius) {
        List<Location> cups = cupboardCache.get(loc.getWorld().getName());
        if (cups == null) return null;
        double rSq = radius * radius;
        for (Location cup : cups) {
            if (cup.distanceSquared(loc) <= rSq) return cup;
        }
        return null;
    }

    private boolean consumeResource(Inventory inv, Material blockType) {
        Material cost = blockType.name().contains("IRON") ? Material.IRON_INGOT : Material.OAK_PLANKS;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == cost) {
                int amt = item.getAmount();
                if (amt > 1) item.setAmount(amt - 1);
                else inv.removeItem(item);
                return true;
            }
        }
        return false;
    }
    private void countBlocksForCupboard(ConfigurationSection section, Location cupLoc, int radius, Map<Material, Integer> counts) {
        if (section == null) return;
        int rSq = radius * radius;
        for (String key : section.getKeys(false)) {
            String[] p = key.split("_");
            if (!p[0].equals(cupLoc.getWorld().getName())) continue;
            double dx = Double.parseDouble(p[1]) - cupLoc.getX();
            double dz = Double.parseDouble(p[3]) - cupLoc.getZ();
            if ((dx*dx + dz*dz) <= rSq) {
                Block b = getBlockFromKey(key);
                if (b != null) counts.put(b.getType(), counts.getOrDefault(b.getType(), 0) + 1);
            }
        }
    }

    private int countItemInInventory(Inventory inv, Material mat) {
        if (inv == null) return 0;
        int count = 0;
        for (ItemStack is : inv.getContents()) {
            if (is != null && is.getType() == mat) count += is.getAmount();
        }
        return count;
    }

    private void removeItem(Inventory inv, Material mat, int amount) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack is = inv.getItem(i);
            if (is != null && is.getType() == mat) {
                int newAmt = is.getAmount() - amount;
                if (newAmt > 0) is.setAmount(newAmt);
                else inv.setItem(i, null);
                return;
            }
        }
    }

    private void updateCupboardCache() {
        cupboardCache.clear();
        ConfigurationSection protectors = plugin.dataStorage.getConfig().getConfigurationSection("protectors");
        if (protectors == null) return;
        for (String key : protectors.getKeys(false)) {
            cupboardCache.computeIfAbsent(protectors.getString(key+".world"), k -> new ArrayList<>())
                    .add(plugin.keyToLocation(key));
        }
    }
    private Inventory getInventoryAt(Location loc) {
        Block above = loc.clone().add(0, 1, 0).getBlock();
        if (above.getState() instanceof org.bukkit.block.Container c) return c.getInventory();
        return null;
    }

    private Block getBlockFromKey(String key) {
        try {
            String[] p = key.split("_");
            return Bukkit.getWorld(p[0]).getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (Exception e) { return null; }
    }

    private String getSettingKey(Material mat) {
        if (mat.name().contains("IRON")) return "IRON_BLOCK";
        return "WOODEN_PLANK";
    }

    private String getItemKoreanName(Material mat) {
        if (mat == Material.IRON_INGOT) return "철괴";
        if (mat == Material.OAK_PLANKS) return "나무판자";
        if (mat == Material.COBBLESTONE) return "조약돌";
        return mat.name();
    }
}