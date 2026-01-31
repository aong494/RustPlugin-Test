package Alpa.test;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class DoorManager implements Listener {

    private final main plugin;

    public DoorManager(main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDoorInteract(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Block b = e.getClickedBlock();
        if (b == null) return;

        String typeName = b.getType().name().toUpperCase();
        boolean isModDoor = typeName.contains("BIG_DOOR") || typeName.contains("ARMORED_DOOR");
        if (!typeName.contains("_DOOR") && !isModDoor) return;
        if (typeName.contains("TRAPDOOR")) return;

        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        Block bottom = getBottom(b);
        String blockKey = plugin.blockToKey(bottom); // "world_x_y_z"
        String locPath = "doors." + blockKey;

        // --- [분기 1] 블레이즈 막대: 잠금 설정 및 정보 확인 ---
        if (item.getType() == Material.BLAZE_ROD && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);

            // dataStorage 확인
            if (plugin.dataStorage.getConfig().contains(locPath)) { // [수정] dataStorage 확인
                int maxHp = typeName.contains("IRON") ? 200 : 100;
                int currentHp = plugin.dataStorage.getConfig().getInt(locPath + ".health", maxHp);
                String progressBar = plugin.getProgressBar(currentHp, maxHp);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§e[Lock] 잠긴 문 정보: " + progressBar + " §f(" + currentHp + " / " + maxHp + ")"));
            } else {
                int initialHp = typeName.contains("IRON") ? 200 : 100;
                // [수정] plugin.getConfig() -> plugin.dataStorage.getConfig()
                plugin.dataStorage.getConfig().set(locPath + ".health", initialHp);
                plugin.dataStorage.getConfig().set(locPath + ".owner", player.getUniqueId().toString());
                plugin.dataStorage.saveConfig(); // [수정] dataStorage 저장
                player.sendMessage("§a[Lock] 문이 잠겼습니다. (체력: " + initialHp + ")");
                player.playSound(b.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
            }
            return;
        }

        // --- [분기 2] 나무 막대기: 철거 및 체력 확인 ---
        if (item.getType() == Material.STICK && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);

            // 데이터가 없는 일반 문이면 무시
            if (!plugin.dataStorage.getConfig().contains(locPath)) return;

            // [권한 체크] canAccess 사용 (data.yml의 doors 카테고리 확인)
            if (!plugin.canAccess(player, blockKey, "doors")) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§c[Door] 이 문의 관리 권한이 없습니다."));
                return;
            }

            if (player.isSneaking()) {
                // 철거 (dataStorage에서 삭제)
                plugin.dataStorage.getConfig().set(locPath, null);
                plugin.dataStorage.saveConfig();
                bottom.setType(Material.AIR);
                bottom.getRelative(0, 1, 0).setType(Material.AIR);
                player.sendMessage("§e[Door] 문을 철거했습니다.");
            } else {
                // 체력 확인
                int maxHp = typeName.contains("IRON") ? 200 : 100;
                int currentHp = plugin.dataStorage.getConfig().getInt(locPath + ".health", maxHp);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§6[Door] 체력: " + plugin.getProgressBar(currentHp, maxHp) + " §f(" + currentHp + " / " + maxHp + ")"));
            }
            return;
        }

        // --- [분기 3] 일반 개폐 로직 ---
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // 잠긴 문인지 확인 (dataStorage 확인)
            if (plugin.dataStorage.getConfig().contains(locPath)) {
                if (plugin.canAccess(player, blockKey, "doors")) {
                    e.setCancelled(true);
                    toggleDoor(bottom);
                } else {
                    e.setCancelled(true);
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText("§c[Lock] 권한이 없어 열 수 없습니다."));
                    player.playSound(b.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 1.2f);
                }
            } else {
                // 잠기지 않은 문: 철문만 강제 개폐 (일반 문은 마인크래프트 기본 로직)
                if (typeName.contains("IRON")) {
                    e.setCancelled(true);
                    toggleDoor(bottom);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDoorBreak(org.bukkit.event.block.BlockBreakEvent e) {
        Block b = e.getBlock();
        String typeName = b.getType().name();

        if (!typeName.contains("_DOOR") || typeName.contains("TRAPDOOR")) return;

        Block bottom = getBottom(b);
        String blockKey = plugin.blockToKey(bottom);
        String locPath = "doors." + blockKey;

        // dataStorage에 데이터가 있는 문만 데미지 로직 적용
        if (!plugin.dataStorage.getConfig().contains(locPath)) return;

        Player p = e.getPlayer();
        Material hand = p.getInventory().getItemInMainHand().getType();

        // 도구 사용 시 파괴 취소
        if (hand == Material.STICK || hand == Material.BLAZE_ROD) {
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        // 데미지 계산 (설정값은 config.yml 유지)
        String toolName = hand.name();
        int dmg = plugin.getConfig().getInt("damage-settings." + toolName,
                plugin.getConfig().getInt("damage-settings.DEFAULT", 1));

        plugin.applyDmg(bottom, dmg, p);
    }

    // --- 나머지 유틸리티 메서드 (기존과 동일) ---
    private void toggleDoor(Block b) {
        if (!(b.getBlockData() instanceof Door data)) return;
        boolean nextState = !data.isOpen();
        updateDoorState(b, nextState);
        Block top = b.getRelative(0, 1, 0);
        if (top.getType() == b.getType()) updateDoorState(top, nextState);

        Sound sound = nextState ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE;
        if (!b.getType().name().contains("IRON")) {
            sound = nextState ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE;
        }
        b.getWorld().playSound(b.getLocation(), sound, 1f, 1f);
    }

    private void updateDoorState(Block b, boolean open) {
        if (b.getBlockData() instanceof Door d) {
            d.setOpen(open);
            b.setBlockData(d);
        }
    }

    private Block getBottom(Block b) {
        if (b.getBlockData() instanceof Bisected bisected && bisected.getHalf() == Bisected.Half.TOP) {
            return b.getRelative(0, -1, 0);
        }
        return b;
    }
}