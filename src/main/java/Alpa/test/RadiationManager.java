package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public class RadiationManager {
    private final main plugin;
    private final Map<UUID, Double> playerRad = new HashMap<>();
    private final Map<UUID, Integer> soundTickCounter = new HashMap<>();
    private final Random random = new Random();

    // 방호 태그를 식별할 키
    private final NamespacedKey protectionKey;

    public RadiationManager(main plugin) {
        this.plugin = plugin;
        // 메인 클래스에서 생성한 것과 동일한 키 생성
        this.protectionKey = new NamespacedKey(plugin, "radiation_protection");
        startTask();
    }

    public double getRad(Player player) {
        return playerRad.getOrDefault(player.getUniqueId(), 0.0);
    }

    // 플레이어가 입은 갑옷의 총 방호 레벨 합산
    private int getTotalProtection(Player player) {
        int total = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;

            Integer level = armor.getItemMeta().getPersistentDataContainer().get(protectionKey, PersistentDataType.INTEGER);
            if (level != null) {
                total += level;
            }
        }
        return total;
    }

    private void startTask() {
        new BukkitRunnable() {
            int tickCycle = 0;

            @Override
            public void run() {
                tickCycle++;
                boolean isSecondTick = (tickCycle % 20 == 0);

                double recovery = plugin.getConfig().getDouble("radiation-settings.recovery-amount", 0.2);
                double threshold = plugin.getConfig().getDouble("radiation-settings.damage-threshold", 70.0);
                double damage = plugin.getConfig().getDouble("radiation-settings.damage-amount", 1.0);
                double maxRad = plugin.getConfig().getDouble("radiation-settings.max-value", 100.0);

                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    String biomeName = player.getLocation().getBlock().getBiome().name();
                    double current = getRad(player);

                    List<String> lv1 = plugin.getConfig().getStringList("radiation-settings.biomes.level-1");
                    List<String> lv2 = plugin.getConfig().getStringList("radiation-settings.biomes.level-2");
                    List<String> lv3 = plugin.getConfig().getStringList("radiation-settings.biomes.level-3");

                    double perSecondChange = -recovery;
                    int soundDelay = 0;
                    float pitchBase = 1.0f;

                    if (lv1.contains(biomeName)) {
                        perSecondChange = plugin.getConfig().getDouble("radiation-settings.intensity.level-1", 0.5);
                        soundDelay = 20; pitchBase = 0.8f;
                    } else if (lv2.contains(biomeName)) {
                        perSecondChange = plugin.getConfig().getDouble("radiation-settings.intensity.level-2", 1.5);
                        soundDelay = 5; pitchBase = 1.2f;
                    } else if (lv3.contains(biomeName)) {
                        perSecondChange = plugin.getConfig().getDouble("radiation-settings.intensity.level-3", 3.0);
                        soundDelay = 1; pitchBase = 1.8f;
                    }

                    // [추가] 방호복 로직 적용
                    if (perSecondChange > 0) {
                        int protection = getTotalProtection(player);
                        // 방호 레벨 1당 방사능 유입 10% 감소 (레벨 10이면 100% 차단)
                        double reduction = Math.max(0, 1.0 - (protection * 0.1));
                        perSecondChange *= reduction;
                    }

                    if (isSecondTick) {
                        current = Math.min(maxRad, Math.max(0, current + perSecondChange));
                        current = Math.round(current * 10.0) / 10.0;
                        playerRad.put(uuid, current);

                        if (current >= threshold) {
                            player.damage(damage);
                        }
                    }

                    if (perSecondChange > 0) {
                        int count = soundTickCounter.getOrDefault(uuid, 0);
                        if (soundDelay > 0 && count >= soundDelay) {
                            Sound clickSound = (soundDelay <= 1) ? Sound.BLOCK_NOTE_BLOCK_SNARE : Sound.BLOCK_NOTE_BLOCK_HAT;
                            player.playSound(player.getLocation(), clickSound, 0.4f, pitchBase + (random.nextFloat() * 0.4f));
                            soundTickCounter.put(uuid, 0);
                        } else {
                            soundTickCounter.put(uuid, count + 1);
                        }
                    }
                }
                if (tickCycle >= 20) tickCycle = 0;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    public void setRad(Player player, double value) {
        playerRad.put(player.getUniqueId(), Math.round(value * 10.0) / 10.0);
    }

}