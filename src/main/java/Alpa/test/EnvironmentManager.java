package Alpa.test;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

public class EnvironmentManager implements Listener {

    private final main plugin;

    public EnvironmentManager(main plugin) {
        this.plugin = plugin;
    }

    // 1. 물이 얼지 않게 설정
    @EventHandler
    public void onWaterFreeze(BlockFormEvent e) {
        if (e.getNewState().getType() == Material.ICE) {
            e.setCancelled(true);
        }
    }

    // 2. 피스톤이 밀 때 (Config 설정 확인)
    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        // config에서 piston-enabled 값을 가져옴 (기본값 true)
        boolean pistonEnabled = plugin.getConfig().getBoolean("settings.piston-enabled", true);

        // 만약 설정이 false라면 작동 중지
        if (!pistonEnabled) {
            e.setCancelled(true);
        }
    }

    // 3. 피스톤이 당길 때 (Config 설정 확인)
    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        boolean pistonEnabled = plugin.getConfig().getBoolean("settings.piston-enabled", true);

        if (!pistonEnabled) {
            e.setCancelled(true);
        }
    }
}