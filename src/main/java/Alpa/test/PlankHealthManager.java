package Alpa.test;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class PlankHealthManager implements Listener {

    private final main plugin;
    private final int PLANK_MAX_HP = 50; // 나무판자 기본 체력

    public PlankHealthManager(main plugin) {
        this.plugin = plugin;
    }

    // 2. 우클릭 체력 확인 및 쉬프트 우클릭 즉시 파괴 (권한 체크 포함)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlankPlace(BlockPlaceEvent e) {
        // ignoreCancelled = true: main에서 e.setCancelled(true)를 했다면 이 코드는 실행되지 않습니다.
        // EventPriority.MONITOR: 모든 보호 로직이 끝난 후 가장 마지막에 실행되도록 합니다.

        Block b = e.getBlock();
        if (!b.getType().name().contains("_PLANKS")) return;

        String key = "planks." + plugin.blockToKey(b);

        plugin.getConfig().set(key + ".health", PLANK_MAX_HP);
        plugin.getConfig().set(key + ".owner", e.getPlayer().getUniqueId().toString());
        plugin.saveConfig();

        e.getPlayer().sendMessage("§e[Plank] 강화된 나무판자가 설치되었습니다. (체력: " + PLANK_MAX_HP + ")");
    }

    @EventHandler
    public void onPlankInteract(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block b = e.getClickedBlock();
        if (b == null || !b.getType().name().contains("_PLANKS")) return;

        String key = "planks." + plugin.blockToKey(b);
        if (!plugin.getConfig().contains(key)) return;

        Player p = e.getPlayer();

        if (p.getInventory().getItemInMainHand().getType() == Material.STICK) {
            e.setCancelled(true);

            if (!hasPermission(p, key)) {
                // 권한 없음 알림도 액션바에 표시 (선택 사항)
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§c[Plank] 관리 권한이 없습니다."));
                return;
            }

            if (p.isSneaking()) {
                int currentHp = plugin.getConfig().getInt(key + ".health");
                plugin.applyPlankDmg(b, currentHp, p);
                // 철거 완료 메시지는 액션바에서 처리됨 (applyPlankDmg 내부 로직 연동)
            } else {
                int hp = plugin.getConfig().getInt(key + ".health");
                int maxHp = 50; // 판자 최대 체력 설정값 (필요 시 수정)

                // [수정] 채팅 대신 액션바 + 시각적 체력바 표시
                String progressBar = getProgressBar(hp, maxHp);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§6[Plank] 남은 체력: " + progressBar + " §f(" + hp + " / " + maxHp + ")"));
            }
        }
    }

    private String getProgressBar(int current, int max) {
        int bars = 10;
        int completedBars = (int) (((double) current / max) * bars);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            if (i < completedBars) sb.append("§a■");
            else sb.append("§7■");
        }
        return sb.toString();
    }

    // 3. 파괴 시 체력 차감 로직 (모두에게 적용)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlankBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!b.getType().name().contains("_PLANKS")) return;

        String key = "planks." + plugin.blockToKey(b);
        if (!plugin.getConfig().contains(key)) return;

        Player p = e.getPlayer();

        e.setCancelled(true); // 블록 파괴 취소

        // 데미지 계산
        String toolName = p.getInventory().getItemInMainHand().getType().name();
        int dmg = plugin.getConfig().getInt("damage-settings." + toolName,
                plugin.getConfig().getInt("damage-settings.DEFAULT", 1));

        // 수정된 applyPlankDmg 호출 (플레이어 p 인자 추가)
        plugin.applyPlankDmg(b, dmg, p);
    }
    // --- 권한 확인 로직 ---
    private boolean hasPermission(Player player, String configKey) {
        if (player.isOp()) return true; // 관리자는 무조건 허용

        String ownerUUID = plugin.getConfig().getString(configKey + ".owner");
        if (ownerUUID == null) return true; // 주인 정보가 없으면 공용으로 간주

        // 1. 본인인지 확인
        if (player.getUniqueId().toString().equals(ownerUUID)) return true;

        // 2. 주인의 그룹과 플레이어의 그룹이 같은지 확인
        String ownerGroup = plugin.groupManager.getGroupViaUUID(ownerUUID); // 주인의 그룹 조회
        String playerGroup = plugin.groupManager.getPlayerGroup(player);   // 나의 그룹 조회

        return ownerGroup != null && ownerGroup.equals(playerGroup);
    }
}