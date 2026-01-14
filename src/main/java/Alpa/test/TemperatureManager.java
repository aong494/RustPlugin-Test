package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class TemperatureManager {
    private final main plugin;
    private final Map<UUID, Double> playerTemp = new HashMap<>();
    private final NamespacedKey insulationKey;

    public TemperatureManager(main plugin) {
        this.plugin = plugin;
        this.insulationKey = new NamespacedKey(plugin, "insulation");
        startTask();
    }

    // 플레이어의 총 보온 레벨 계산 (태그 방식)
    private int getInsulationLevel(Player player) {
        int totalLevel = 0;

        // 1. 갑옷 태그 점수 합산 (기존 로직)
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType() == Material.AIR || !armor.hasItemMeta()) continue;
            Integer level = armor.getItemMeta().getPersistentDataContainer().get(insulationKey, org.bukkit.persistence.PersistentDataType.INTEGER);
            if (level != null) totalLevel += level;
        }

        // 2. 횃불 체크 (오른손 또는 왼손)
        if (player.getInventory().getItemInMainHand().getType() == Material.TORCH ||
                player.getInventory().getItemInOffHand().getType() == Material.TORCH) {
            totalLevel += 5;
        }

        // 3. 주변 열원 체크 (모닥불 + 화로 등)
        if (isNearHeatSource(player, 5)) {
            totalLevel += 5;
        }

        return totalLevel;
    }

    // 주변에 열원(모닥불, 화로 등)이 있는지 확인
    private boolean isNearHeatSource(Player player, int radius) {
        Location loc = player.getLocation();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = loc.clone().add(x, y, z).getBlock();
                    Material type = block.getType();

                    // 체크할 블록 목록: 모닥불, 영혼 모닥불, 화로, 용광로, 훈연기
                    if (type == Material.CAMPFIRE ||
                            type == Material.SOUL_CAMPFIRE ||
                            type == Material.FURNACE ||
                            type == Material.BLAST_FURNACE ||
                            type == Material.SMOKER) {

                        org.bukkit.block.data.BlockData data = block.getBlockData();

                        // 불을 켤 수 있는 블록이고 현재 불이 켜져(isLit) 있는지 확인
                        if (data instanceof Lightable lightable) {
                            if (lightable.isLit()) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void startTask() {
        new BukkitRunnable() {
            int tickCycle = 0;

            @Override
            public void run() {
                tickCycle++;
                if (tickCycle < 20) return;
                tickCycle = 0;

                double defaultTemp = plugin.getConfig().getDouble("temperature-settings.default-temp", 37.5);
                double change = plugin.getConfig().getDouble("temperature-settings.change-amount", 0.2);
                int coldThreshold = plugin.getConfig().getInt("temperature-settings.insulation.cold-protection-threshold", 5);
                int heatThreshold = plugin.getConfig().getInt("temperature-settings.insulation.heat-trigger-threshold", 3);

                List<String> hotBiomes = plugin.getConfig().getStringList("temperature-settings.hot-biomes");
                List<String> coldBiomes = plugin.getConfig().getStringList("temperature-settings.cold-biomes");

                for (Player player : Bukkit.getOnlinePlayers()) {
                    double current = getTemp(player);
                    String biomeName = player.getLocation().getBlock().getBiome().name();
                    int insulLevel = getInsulationLevel(player);

                    // [추가] 물속 여부 확인
                    boolean isInWater = player.isInWater();

                    if (hotBiomes.contains(biomeName)) {
                        // [추가] 더운 곳이라도 물속에 있다면 체온 회복
                        if (isInWater) {
                            applyRecovery(player, current, defaultTemp, change);
                            continue; // 물속 로직 실행 시 아래 hotBiomes 로직 건너뜀
                        }

                        if (insulLevel >= heatThreshold) {
                            current += change;
                        }
                    }
                    else if (coldBiomes.contains(biomeName)) {
                        if (insulLevel < coldThreshold) {
                            current -= change;
                        } else {
                            // 보온 충분 시 회복
                            double diff = defaultTemp - current;
                            if (diff > 0.1) current += (change / 2.0);
                            else if (diff < -0.1) current -= (change / 2.0);
                            else current = defaultTemp;
                        }
                    }
                    else {
                        // 일반 지역 복귀 로직
                        double diff = current - defaultTemp;
                        if (Math.abs(diff) > 0.1) {
                            if (diff > 0) current -= (change / 2.0);
                            else current += (change / 2.0);
                        } else {
                            current = defaultTemp;
                        }
                    }

                    current = Math.min(50.0, Math.max(20.0, current));
                    current = Math.round(current * 10.0) / 10.0;
                    playerTemp.put(player.getUniqueId(), current);
                }
            }

            // 반복되는 회복 로직을 별도 메서드로 분리 (startTask 내부에서 호출용)
            private void applyRecovery(Player player, double current, double defaultTemp, double change) {
                double diff = defaultTemp - current;
                if (Math.abs(diff) > 0.1) {
                    if (diff > 0) current += (change / 1.0); // 물속에서는 좀 더 빠르게 회복
                    else current -= (change / 1.0);
                } else {
                    current = defaultTemp;
                }
                current = Math.round(current * 10.0) / 10.0;
                playerTemp.put(player.getUniqueId(), current);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // [추가] 외부(main 클래스 등)에서 체온을 직접 수정할 때 사용
    public void setTemp(Player player, double value) {
        playerTemp.put(player.getUniqueId(), Math.round(value * 10.0) / 10.0);
    }
    public double getTemp(Player player) {
        return playerTemp.getOrDefault(player.getUniqueId(), 37.5);
    }
}