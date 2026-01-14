package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class SpawnManager {
    private final main plugin;
    private File file;
    private FileConfiguration config;

    // ⭐ 부활 대기 데이터용 추가
    private File pendingFile;
    private FileConfiguration pendingConfig;

    public SpawnManager(main plugin) {
        this.plugin = plugin;
        setupFiles();
        restorePendingMobs();
    }

    public void setupFiles() {
        // 기존 spawns.yml
        if (file == null) file = new File(plugin.getDataFolder(), "spawns.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        config = YamlConfiguration.loadConfiguration(file);

        // ⭐ pending_mobs.yml (부활 대기 기록용)
        pendingFile = new File(plugin.getDataFolder(), "pending_mobs.yml");
        if (!pendingFile.exists()) {
            try { pendingFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        pendingConfig = YamlConfiguration.loadConfiguration(pendingFile);
    }

    // 몹 사망 시 기록 (MobListener에서 호출할 예정)
    public void addPendingMob(Location loc, int modelData, long respawnAt) {
        String key = locationToString(loc);
        pendingConfig.set("pending." + key + ".modelData", modelData);
        pendingConfig.set("pending." + key + ".time", respawnAt);
        savePending();
    }

    public void removePendingMob(Location loc) {
        pendingConfig.set("pending." + locationToString(loc), null);
        savePending();
    }

    public void save() { try { config.save(file); } catch (IOException e) { e.printStackTrace(); } }
    public void savePending() { try { pendingConfig.save(pendingFile); } catch (IOException e) { e.printStackTrace(); } }
    public FileConfiguration getConfig() { return config; }

    private String locationToString(Location loc) {
        return loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }

    public void loadAllSpawns() {
        if (config == null || !config.contains("spawns")) return;

        for (String key : config.getConfigurationSection("spawns").getKeys(false)) {
            String path = "spawns." + key;
            Location loc = getLocationFromConfig(config, path);
            if (loc == null || loc.getWorld() == null) continue;

            // 해당 좌표의 청크가 로드되어 있는지 확인
            if (loc.getChunk().isLoaded()) {
                // 1틱(0.05초) 뒤에 실행하여 서버가 기존 엔티티를 로드할 시간을 줍니다.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // [핵심 수정] 소환 위치 반경 1블록 내의 기존 몹들을 강제로 제거합니다.
                    // 이렇게 하면 겹쳐있던 몹들이 싹 사라지고 1마리만 남게 됩니다.
                    loc.getWorld().getNearbyEntities(loc, 1.0, 3.0, 1.0).forEach(e -> {
                        if (e.getScoreboardTags().contains("custom_mob_base")) {
                            e.remove(); // 기존 몹 제거
                        }
                        // 모델이나 이름표 아머스탠드도 같이 정리 (태그 확인)
                        if (e instanceof org.bukkit.entity.ArmorStand &&
                                (e.getScoreboardTags().contains("custom_mob_model") || e.getCustomName() != null)) {
                            e.remove();
                        }
                    });

                    // 깨끗해진 자리에 새로 한 마리만 소환
                    int modelData = config.getInt(path + ".modelData");
                    CustomMobSummoner.spawnStaticMob(plugin, loc, modelData);
                });
            }
        }
    }

    private void restorePendingMobs() {
        if (pendingConfig == null || !pendingConfig.contains("pending")) return;
        ConfigurationSection section = pendingConfig.getConfigurationSection("pending");
        if (section == null) return;

        long now = System.currentTimeMillis();
        for (String key : section.getKeys(false)) {
            try {
                // key: World_X_Y_Z
                String[] split = key.split("_");
                if (split.length < 4) continue;

                org.bukkit.World world = Bukkit.getWorld(split[0]);
                if (world == null) continue;

                double x = Double.parseDouble(split[1]);
                double y = Double.parseDouble(split[2]);
                double z = Double.parseDouble(split[3]);

                final Location loc = new Location(world, x + 0.5, y, z + 0.5);

                // ⭐ 수정: 경로 확인 (pending.key.modelData)
                int modelData = section.getInt(key + ".modelData");
                long respawnAt = section.getLong(key + ".time");

                if (now >= respawnAt) {
                    if (!isMobAlreadyThere(loc)) {
                        // ⭐ 중요: 단순 소환이 아니라 커스텀 몹의 모든 속성을 입히는 spawnStaticMob 호출
                        CustomMobSummoner.spawnStaticMob(plugin, loc, modelData);
                    }
                    removePendingMob(loc);
                } else {
                    long delayTicks = (respawnAt - now) / 50L;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!isMobAlreadyThere(loc)) {
                            CustomMobSummoner.spawnStaticMob(plugin, loc, modelData);
                        }
                        removePendingMob(loc);
                    }, Math.max(1L, delayTicks));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("부활 대기 몹 복구 중 오류 발생: " + key);
            }
        }
    }

    // ⭐ 중복 검사 공용 메서드
    private boolean isMobAlreadyThere(Location loc) {
        return loc.getWorld().getNearbyEntities(loc, 2.0, 2.0, 2.0).stream()
                .anyMatch(e -> e.getScoreboardTags().contains("custom_mob_base"));
    }

    private Location getLocationFromConfig(FileConfiguration conf, String path) {
        return new Location(
                Bukkit.getWorld(conf.getString(path + ".world")),
                conf.getDouble(path + ".x"),
                conf.getDouble(path + ".y"),
                conf.getDouble(path + ".z"),
                (float) conf.getDouble(path + ".yaw"), 0
        );
    }

    public void addSpawnLocation(Location loc, int modelData) {
        String id = UUID.randomUUID().toString();
        String path = "spawns." + id;
        config.set(path + ".world", loc.getWorld().getName());
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", loc.getYaw());
        config.set(path + ".modelData", modelData);
        save();
        CustomMobSummoner.spawnStaticMob(plugin, loc, modelData);
    }

    public boolean isPending(Location loc) {
        if (pendingConfig == null || !pendingConfig.contains("pending")) return false;

        // 블록 좌표(정수)로 변환하여 비교해야 정확합니다.
        String key = loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
        return pendingConfig.contains("pending." + key);
    }
}