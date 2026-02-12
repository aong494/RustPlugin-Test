package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CardKeyManager {
    private final main plugin;
    private File file;
    private FileConfiguration config;

    // 리더기 위치 -> 보안 레벨 (1, 2, 3)
    private final Map<Location, Integer> readerLevels = new HashMap<>();
    // 리더기 위치 -> 연결된 문 위치
    private final Map<Location, Location> linkedDoors = new HashMap<>();

    // 플레이어별 연결 작업 상태 저장 (UUID -> 리더기 위치)
    public Map<UUID, Location> pendingLinks = new HashMap<>();

    public int autoCloseSeconds = 3; // 기본 3초

    public CardKeyManager(main plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        file = new File(plugin.getDataFolder(), "cardkeyreader.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        config = YamlConfiguration.loadConfiguration(file);

        // 기본 설정 로드
        if (!config.contains("settings.auto_close_seconds")) {
            config.set("settings.auto_close_seconds", 3);
            saveConfig();
        }
        autoCloseSeconds = config.getInt("settings.auto_close_seconds", 3);

        // 데이터 로드
        readerLevels.clear();
        linkedDoors.clear();

        if (config.contains("readers")) {
            for (String key : config.getConfigurationSection("readers").getKeys(false)) {
                Location readerLoc = stringToLoc(key);
                int level = config.getInt("readers." + key + ".level");
                String doorStr = config.getString("readers." + key + ".door");

                if (readerLoc != null) {
                    readerLevels.put(readerLoc, level);
                    if (doorStr != null) {
                        linkedDoors.put(readerLoc, stringToLoc(doorStr));
                    }
                }
            }
        }
    }

    public void saveConfig() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 리더기 설정 저장
    public void setReaderLevel(Location loc, int level) {
        readerLevels.put(loc, level);
        String key = locToString(loc);
        config.set("readers." + key + ".level", level);
        saveConfig();
    }

    // 문 연결 저장
    public void linkDoor(Location readerLoc, Location doorLoc) {
        linkedDoors.put(readerLoc, doorLoc);
        String key = locToString(readerLoc);
        config.set("readers." + key + ".door", locToString(doorLoc));
        saveConfig();
    }

    public Integer getReaderLevel(Location loc) {
        return readerLevels.get(loc);
    }

    public Location getLinkedDoor(Location readerLoc) {
        return linkedDoors.get(readerLoc);
    }

    // 좌표 변환 유틸리티
    private String locToString(Location loc) {
        if (loc == null) return null;
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location stringToLoc(String str) {
        if (str == null) return null;
        String[] parts = str.split(",");
        if (parts.length != 4) return null;
        return new Location(Bukkit.getWorld(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]));
    }
}