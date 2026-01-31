package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BlueprintManager {
    private final main plugin;
    // 플레이어마다 현재 보고 있는 홀로그램 위치를 저장 (이전 위치 삭제용)
    private final Map<UUID, Location> lastHologramLoc = new HashMap<>();

    public BlueprintManager(main plugin) {
        this.plugin = plugin;
        startHologramTask();
    }

    private void startHologramTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // 1. 청사진 아이템을 들고 있는지 확인 (이 메서드는 직접 구현하거나 아래 예시 참고)
                    if (isHoldingBlueprint(player)) {
                        updateHologram(player);
                    } else {
                        // 청사진을 안 들고 있으면 기존 홀로그램 삭제
                        clearHologram(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // 1틱(0.05초)마다 실행
    }

    public void updateHologram(Player player) {
        Block targetBlock = player.getTargetBlockExact(5);

        // 바라보는 블록이 없거나 공중이면 삭제 후 리턴
        if (targetBlock == null || targetBlock.getType() == Material.AIR) {
            clearHologram(player);
            return;
        }

        Location buildLoc = targetBlock.getLocation().add(0, 1, 0);
        Location lastLoc = lastHologramLoc.get(player.getUniqueId());

        // 위치가 변했을 때만 업데이트
        if (lastLoc == null || !lastLoc.equals(buildLoc)) {
            clearHologram(player); // 이전 위치 지우기

            // 새 위치에 홀로그램 표시
            player.sendBlockChange(buildLoc, Bukkit.createBlockData(Material.CYAN_STAINED_GLASS));
            lastHologramLoc.put(player.getUniqueId(), buildLoc);
        }
    }

    public void clearHologram(Player player) {
        Location lastLoc = lastHologramLoc.remove(player.getUniqueId());
        if (lastLoc != null) {
            // 실제 서버의 블록 데이터로 다시 되돌림 (플레이어 화면에서 가짜 블록 사라짐)
            player.sendBlockChange(lastLoc, lastLoc.getBlock().getBlockData());
        }
    }

    private boolean isHoldingBlueprint(Player player) {
        // 예시: 손에 든 아이템 이름이 "청사진"인 경우
        return player.getInventory().getItemInMainHand().getType() == Material.PAPER;
    }
}