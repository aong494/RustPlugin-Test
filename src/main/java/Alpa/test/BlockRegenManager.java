package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class BlockRegenManager {
    private final main plugin;
    private File file;
    private FileConfiguration config;

    // 리젠 대기 블록 저장용
    private File pendingFile;
    private FileConfiguration pendingConfig;

    public BlockRegenManager(main plugin) {
        this.plugin = plugin;
        setup();
    }

    public void setup() {
        // 기존 blocks.yml 설정
        if (file == null) file = new File(plugin.getDataFolder(), "blocks.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        config = YamlConfiguration.loadConfiguration(file);

        if (!config.contains("regen-blocks")) {
            config.set("regen-blocks.DIRT", 30);
            config.set("regen-blocks.GRASS_BLOCK", 30);
            save();
        }

        // 서버 리부팅 대비 pending_regens.yml 설정
        pendingFile = new File(plugin.getDataFolder(), "pending_regens.yml");
        if (!pendingFile.exists()) {
            try { pendingFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        pendingConfig = YamlConfiguration.loadConfiguration(pendingFile);
    }

    public void save() {
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    // 블록 파괴 시 기록
    public void addPendingRegen(Location loc, Material type, long respawnAt) {
        String key = locationToString(loc); // 형식: world_x_y_z
        pendingConfig.set("pending." + key + ".type", type.name());
        pendingConfig.set("pending." + key + ".time", respawnAt);
        savePending(); // 여기서 실제로 파일에 씁니다.
        // Bukkit.getLogger().info("데이터 기록됨: " + key); // 디버그용 (확인 후 삭제)
    }

    public void savePending() {
        try {
            pendingConfig.save(pendingFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public int getRegenTime(Material type) {
        return config.getInt("regen-blocks." + type.name(), -1);
    }

    public synchronized void removePendingRegen(Location loc) {
        String key = locationToString(loc);
        pendingConfig.set("pending." + key, null);
        savePending(); // 즉시 물리적 파일로 쓰기
    }

    public ConfigurationSection getPendingSection() {
        return pendingConfig.getConfigurationSection("pending");
    }

    private String locationToString(Location loc) {
        return loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }
}