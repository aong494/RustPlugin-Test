package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container; // 심볼 해결을 위해 반드시 필요
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
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
    public MaintenanceResult getMaintenanceStatus(Location cupLoc) {
        ConfigurationSection dataConfig = plugin.dataStorage.getConfig();
        FileConfiguration mConfig = plugin.maintenanceConfig;

        Map<Material, Integer> totalCosts = new HashMap<>();

        // 1. 반경 내 블록 스캔 (기존 로직 활용)
        int radius = mConfig.getInt("settings.radius", 15);
        // 예시 데이터: 돌 100개, 나무 200개라고 가정
        Map<Material, Integer> counts = new HashMap<>();
        // counts = scanBlocksInRadius(cupLoc, radius);

        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            String typeKey = getSettingKey(entry.getKey());
            String path = "costs." + typeKey;
            if (!mConfig.contains(path)) continue;

            Material costItem = Material.valueOf(mConfig.getString(path + ".item"));
            int perAmount = mConfig.getInt(path + ".amount", 10);
            int costQty = mConfig.getInt(path + ".cost", 1);
            int totalRequired = (int) Math.ceil((double) entry.getValue() / perAmount) * costQty;

            totalCosts.put(costItem, totalCosts.getOrDefault(costItem, 0) + totalRequired);
        }

        // 2. 인벤토리 체크
        Inventory inv = getInventoryAt(cupLoc);
        boolean isDecaying = false;
        List<String> costTexts = new ArrayList<>();

        for (Map.Entry<Material, Integer> cost : totalCosts.entrySet()) {
            int current = countItem(inv, cost.getKey());
            if (current < cost.getValue()) isDecaying = true;
            costTexts.add(getItemKoreanName(cost.getKey()) + " x" + cost.getValue());
        }

        return new MaintenanceResult(isDecaying, String.join(", ", costTexts));
    }

    // 결과값 전달용 내부 클래스
    public static class MaintenanceResult {
        public final boolean isDecaying;
        public final String costString;
        public MaintenanceResult(boolean d, String c) { this.isDecaying = d; this.costString = c; }
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
    // BlockDecayManager 클래스 내부에 추가/수정

    public void syncMaintenanceToPlayer(Player player, Location cupLoc) {
        ConfigurationSection dataConfig = plugin.dataStorage.getConfig();
        FileConfiguration mConfig = plugin.maintenanceConfig;

        // 1. 해당 도구함 영향권 내 실제 블록 수 합산
        Map<Material, Integer> counts = new HashMap<>();
        int radius = mConfig.getInt("settings.radius", 15);

        // dataConfig에서 planks와 doors를 스캔하여 이 도구함(cupLoc) 범위 내 블록만 카운트
        countBlocksForCupboard(dataConfig.getConfigurationSection("planks"), cupLoc, radius, counts);
        countBlocksForCupboard(dataConfig.getConfigurationSection("doors"), cupLoc, radius, counts);

        // 2. 소모량 계산
        Map<Material, Integer> totalCosts = new HashMap<>();
        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            String typeName = getSettingKey(entry.getKey());
            String path = "costs." + typeName;
            if (!mConfig.contains(path)) continue;

            Material costItem = Material.valueOf(mConfig.getString(path + ".item"));
            int perAmount = mConfig.getInt(path + ".amount", 10);
            int costQty = mConfig.getInt(path + ".cost", 1);
            int totalRequired = (int) Math.ceil((double) entry.getValue() / perAmount) * costQty;

            totalCosts.put(costItem, totalCosts.getOrDefault(costItem, 0) + totalRequired);
        }

        // 3. 인벤토리 체크 및 텍스트 생성
        Inventory inv = getInventoryAt(cupLoc);
        boolean isDecaying = false;
        List<String> costStrings = new ArrayList<>();

        for (Map.Entry<Material, Integer> cost : totalCosts.entrySet()) {
            int currentInInv = (inv == null) ? 0 : countItemInInventory(inv, cost.getKey());
            if (currentInInv < cost.getValue()) isDecaying = true;
            costStrings.add(getItemKoreanName(cost.getKey()) + " x" + cost.getValue());
        }

        String finalCostStr = costStrings.isEmpty() ? "자원 필요 없음" : String.join(", ", costStrings);

        // 4. Forge 클라이언트로 패킷 전송 (브릿지 메서드 호출)
        // 이 부분은 본인의 패킷 전송 방식(ex: ModMessages.sendToPlayer)에 맞춰 작성
        plugin.sendMaintenancePacket(player, isDecaying, finalCostStr);
    }

    // 특정 도구함 범위 내 블록 개수를 세는 헬퍼 메서드
    private void countBlocksForCupboard(ConfigurationSection section, Location cupLoc, int radius, Map<Material, Integer> counts) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            if (isBlockInRadius(key, cupLoc, radius)) {
                Block b = getBlockFromKey(key);
                if (b != null) {
                    Material mat = b.getType();
                    counts.put(mat, counts.getOrDefault(mat, 0) + 1);
                }
            }
        }
    }

    // 인벤토리 내 특정 아이템 개수를 세는 메서드
    private int countItemInInventory(Inventory inv, Material mat) {
        int count = 0;
        for (ItemStack is : inv.getContents()) {
            if (is != null && is.getType() == mat) count += is.getAmount();
        }
        return count;
    }
    private Inventory getInventoryAt(Location loc) {
        // 1. 저장된 위치 바로 위 체크 (기본값)
        Block above = loc.clone().add(0, 1, 0).getBlock();
        if (above.getType() == Material.TRAPPED_CHEST) {
            if (above.getState() instanceof org.bukkit.block.Chest chest) return chest.getInventory();
        }

        // 2. 저장된 위치 그 자체 체크 (혹시 상자 위치가 저장되었을 경우)
        Block current = loc.getBlock();
        if (current.getType() == Material.TRAPPED_CHEST) {
            if (current.getState() instanceof org.bukkit.block.Chest chest) return chest.getInventory();
        }

        // 3. 저장된 위치 2칸 위 체크 (혹시 바닥 좌표가 저장되었을 경우)
        Block above2 = loc.clone().add(0, 2, 0).getBlock();
        if (above2.getType() == Material.TRAPPED_CHEST) {
            if (above2.getState() instanceof org.bukkit.block.Chest chest) return chest.getInventory();
        }

        // [디버그] 그래도 못 찾았다면 주변 블록 상태 출력
        System.out.println("[디버그] 상자 감지 실패! 위치: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        System.out.println("  - 현재(Y): " + current.getType());
        System.out.println("  - 위(Y+1): " + above.getType());
        System.out.println("  - 위위(Y+2): " + above2.getType());

        return null;
    }
    private void updateStatusAtLocation(Location loc, Set<String> missing) {
        Block chestBlock = loc.clone().add(0, 1, 0).getBlock();
        org.bukkit.block.BlockState state = chestBlock.getState();

        if (state instanceof Container container) {
            String status = (missing == null || missing.isEmpty()) ? "§a[보호 중]" : "§c[재료 부족]";
            container.setCustomName(status + " 도구함");
            state.update(true, false); // 물리적 업데이트는 필요 없으므로 false
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
                // [추가] 실제 해당 좌표에 도구함 블록이 존재하는지 확인
                Block actualBlock = cupLoc.getBlock();
                if (!actualBlock.getType().name().contains("TOOL_CUPBOARD")) {
                    // 블록이 없는데 데이터만 남아있는 경우 (유령 데이터 제거)
                    String cupKey = plugin.blockToKey(actualBlock);
                    plugin.dataStorage.getConfig().set("protectors." + cupKey, null);
                    plugin.dataStorage.saveConfig();
                    System.out.println("[디버그] 존재하지 않는 도구함 데이터 삭제됨: " + cupKey);

                    unmaintained.add(key); // 보호받지 못하는 블록으로 처리
                    continue;
                }

                // 정상이면 카운트 진행
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
    public int countItem(Inventory inv, Material mat) {
        if (inv == null) return 0;
        int total = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == mat) {
                total += item.getAmount();
            }
        }
        return total;
    }
}