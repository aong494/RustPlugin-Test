package Alpa.test;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.Location;

public class RespawnListener implements Listener {
    private final main plugin;

    public RespawnListener(main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!event.isBedSpawn()) {
            // ⭐ 안전 검사가 포함된 메서드 호출
            Location safeLoc = plugin.respawnManager.getSafeRandomLocation();
            if (safeLoc != null) {
                event.setRespawnLocation(safeLoc);
            }
        }
    }
}