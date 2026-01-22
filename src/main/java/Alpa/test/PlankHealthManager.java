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

    // [유틸리티 메소드] 판자나 나무 계단인지 확인하는 로직
    private boolean isTargetBlock(Material material) {
        if (material == null) return false;
        String name = material.name().toUpperCase();

        // 1. 나무 판자/계단
        boolean isWoodPlank = name.contains("_PLANKS");
        boolean isWoodStair = name.contains("_STAIRS") && !name.contains("COBBLE") && !name.contains("STONE") && !name.contains("BRICK");

        // 2. 석재(조약돌) 판자/계단 (추가됨)
        boolean isStoneBrick = name.equals("STONE_BRICKS");
        boolean isStoneBrickStair = name.equals("STONE_BRICK_STAIRS");

        return isWoodPlank || isWoodStair || isStoneBrick || isStoneBrickStair;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlankPlace(BlockPlaceEvent e) {
        Block b = e.getBlock();
        Material type = b.getType();
        if (!isTargetBlock(type)) return;

        String key = plugin.blockToKey(b);
        String path = "planks." + key;
        String typeName = type.name();

        // 재질에 따른 초기 체력 결정
        int initialHp = 50;
        if (typeName.contains("IRON")) initialHp = 200;
        else if (typeName.contains("STONE_BRICK")) initialHp = 100;

        // data.yml에 저장
        plugin.dataStorage.getConfig().set(path + ".health", initialHp);
        plugin.dataStorage.getConfig().set(path + ".owner", e.getPlayer().getUniqueId().toString());
        plugin.dataStorage.saveConfig();
    }

    // 상호작용 로직
    @EventHandler
    public void onPlankInteract(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block b = e.getClickedBlock();
        if (b == null || !isTargetBlock(b.getType())) return;

        String key = plugin.blockToKey(b);
        if (!plugin.dataStorage.getConfig().contains("planks." + key)) return;

        Player p = e.getPlayer();
        if (p.getInventory().getItemInMainHand().getType() == Material.STICK) {
            e.setCancelled(true);

            // [권한 체크]
            if (!plugin.canAccess(p, key, "planks")) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§c[Plank] 이 블록의 관리 권한이 없습니다."));
                return;
            }

            // 이후 철거/확인 로직 수행 (생략)
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
        if (!isTargetBlock(b.getType())) return;

        // dataStorage에서 키 확인
        String blockKey = plugin.blockToKey(b);
        if (!plugin.dataStorage.getConfig().contains("planks." + blockKey)) return;

        Player p = e.getPlayer();
        e.setCancelled(true); // 블록 파괴 취소 (체력 로직으로 전환)

        // 데미지 설정 (설정값은 config.yml 유지)
        String toolName = p.getInventory().getItemInMainHand().getType().name();
        int dmg = plugin.getConfig().getInt("damage-settings." + toolName,
                plugin.getConfig().getInt("damage-settings.DEFAULT", 1));

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