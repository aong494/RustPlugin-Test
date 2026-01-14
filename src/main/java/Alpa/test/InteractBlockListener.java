package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class InteractBlockListener implements Listener {
    private final main plugin;

    public InteractBlockListener(main plugin) {
        this.plugin = plugin;

        // ⭐ 파티클 작업 시작 (1초마다 주변 상호작용 블록 탐색)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                Location pLoc = player.getLocation();

                // 플레이어 주변 7x7x7 범위 탐색
                for (int x = -7; x <= 7; x++) {
                    for (int y = -3; y <= 3; y++) {
                        for (int z = -7; z <= 7; z++) {
                            org.bukkit.block.Block b = pLoc.clone().add(x, y, z).getBlock();
                            String typeName = b.getType().name();

                            // 설정 파일에 등록된 블록 이름이라면 파티클 생성
                            if (plugin.interactBlockManager.getConfig().contains("blocks." + typeName)) {
                                b.getWorld().spawnParticle(
                                        org.bukkit.Particle.VILLAGER_HAPPY, // 초록색 반짝임
                                        b.getLocation().add(0.5, 0.8, 0.5), // 블록 위쪽 중심
                                        2, 0.2, 0.2, 0.2, 0.05
                                );
                            }
                        }
                    }
                }
            }
        }, 0L, 20L); // 20틱 = 1초
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        String blockType = block.getType().name();
        if (plugin.interactBlockManager.getConfig().contains("blocks." + blockType)) {
            event.setCancelled(true); // 블록 기본 상호작용 막기

            // 1. 아이템 드랍
            for (ItemStack item : plugin.interactBlockManager.getRandomDrops(blockType)) {
                block.getWorld().dropItemNaturally(block.getLocation(), item);
            }

            // 2. 블록 타입 및 리젠 시간 가져오기
            Material originalType = block.getType();
            int delay = plugin.interactBlockManager.getConfig().getInt("blocks." + blockType + ".regen_seconds");

            // 3. 블록 일시 제거
            block.setType(Material.AIR);

            // 4. ⭐ 중요: 직접 스케줄러를 돌리지 말고, 아래 정의한 scheduleRegen을 호출해야 파일에 저장됩니다!
            scheduleRegen(block, originalType, delay);
        }
    }
    // InteractBlockListener.java 내부의 블록 제거/리젠 부분

    private void scheduleRegen(Block block, Material originalType, int seconds) {
        Location loc = block.getLocation();

        // 1. 서버 재부팅 대비 데이터 저장 (pending_regens.yml에 기록)
        long respawnAt = System.currentTimeMillis() + (seconds * 1000L);
        plugin.blockRegenManager.addPendingRegen(loc, originalType, respawnAt);

        // 2. 현재 서버에서 돌아갈 리젠 로직
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // 복구 로직 실행
            if (loc.getBlock().getType() == Material.AIR || loc.getBlock().getType() == Material.CAVE_AIR) {
                loc.getBlock().setType(originalType);
            }

            // 3. 리젠 완료 후 파일에서 데이터 삭제
            plugin.blockRegenManager.removePendingRegen(loc);
        }, seconds * 20L);
    }
}