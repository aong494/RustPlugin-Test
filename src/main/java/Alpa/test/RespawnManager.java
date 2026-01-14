package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

public class RespawnManager {
    private final main plugin;
    private File file;
    private FileConfiguration config;
    private final Random random = new Random();

    public RespawnManager(main plugin) {
        this.plugin = plugin;
        setup();
    }

    public void setup() {
        if (file == null) file = new File(plugin.getDataFolder(), "locations.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void addLocation(Location loc) {
        List<String> locs = config.getStringList("random-respawns");
        String locStr = loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch();
        locs.add(locStr);
        config.set("random-respawns", locs);
        save();
    }

    // ⭐ 안전한 좌표를 찾을 때까지 랜덤 재시도
    public Location getSafeRandomLocation() {
        List<String> locs = config.getStringList("random-respawns");
        if (locs.isEmpty()) return null;

        int maxAttempts = 10; // 최대 10번 재시도
        for (int i = 0; i < maxAttempts; i++) {
            String selected = locs.get(random.nextInt(locs.size()));
            Location loc = parseLocation(selected);

            if (loc != null && isSafeLocation(loc)) {
                return loc; // 안전한 곳을 찾으면 즉시 반환
            }
        }

        // 모든 시도가 실패하면 리스트의 첫 번째 좌표라도 반환 (혹은 기본 스폰)
        return parseLocation(locs.get(0));
    }

    // ⭐ 좌표가 안전한지 검사하는 메서드
    private boolean isSafeLocation(Location loc) {
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        Block ground = loc.clone().add(0, -1, 0).getBlock();

        // 1. 몸이 들어갈 두 칸이 비어있어야 함 (공기, 꽃, 잔디 등 통과 가능한 블록)
        if (!feet.isPassable() || !head.isPassable()) return false;

        // 2. 발밑이 공기나 용암이면 안 됨 (추락 방지)
        if (ground.isPassable() || ground.getType() == Material.LAVA) return false;

        return true;
    }

    private Location parseLocation(String locStr) {
        try {
            String[] split = locStr.split(",");
            return new Location(
                    Bukkit.getWorld(split[0]),
                    Double.parseDouble(split[1]),
                    Double.parseDouble(split[2]),
                    Double.parseDouble(split[3]),
                    Float.parseFloat(split[4]),
                    Float.parseFloat(split[5])
            );
        } catch (Exception e) {
            return null;
        }
    }

    public void save() {
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }
}