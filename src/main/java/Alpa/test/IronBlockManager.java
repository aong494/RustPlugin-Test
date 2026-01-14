package Alpa.test;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class IronBlockManager implements Listener {

    private final main plugin;
    private final int IRON_MAX_HP = 200;

    public IronBlockManager(main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onIronInteract(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.IRON_BLOCK) return;

        Player player = e.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType() != Material.STICK) return;

        String key = "planks." + plugin.blockToKey(b); // 데이터 키는 기존 planks와 공유 (또는 iron으로 변경 가능)
        if (!plugin.getConfig().contains(key)) return;

        e.setCancelled(true); // 막대기 상호작용 시 기본 동작 방지

        // --- [1. 좌클릭: 수리 로직] ---
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            int currentHp = plugin.getConfig().getInt(key + ".health", IRON_MAX_HP);

            if (currentHp >= IRON_MAX_HP) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§a[Iron] 이미 체력이 가득 차 있습니다."));
                return;
            }

            // 철괴 1개 소모 체크
            if (hasItem(player, Material.IRON_INGOT, 1)) {
                removeItem(player, Material.IRON_INGOT, 1);

                int repairAmt = 20; // 철 블록 수리 회복량 (원하는 대로 수정)
                int newHp = Math.min(IRON_MAX_HP, currentHp + repairAmt);
                plugin.getConfig().set(key + ".health", newHp);
                plugin.saveConfig();

                String progressBar = getProgressBar(newHp, IRON_MAX_HP);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§e[Iron] 수리 완료! (-1철괴) " + progressBar + " §f(" + newHp + " / " + IRON_MAX_HP + ")"));
                player.playSound(b.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.0f, 1.5f);
                b.getWorld().spawnParticle(Particle.CRIT, b.getLocation().add(0.5, 0.5, 0.5), 10);
            } else {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§c[Iron] 수리하려면 철괴가 1개 필요합니다!"));
            }
        }

        // --- [2. 우클릭: 확인 및 철거] ---
        else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (player.isSneaking()) {
                // 쉬프트 우클릭: 철거
                plugin.getConfig().set(key, null);
                plugin.saveConfig();
                b.setType(Material.AIR);
                player.sendMessage("§e[Iron] 철 블록을 철거했습니다.");
                player.playSound(b.getLocation(), Sound.BLOCK_METAL_BREAK, 1.0f, 1.0f);
            } else {
                // 일반 우클릭: 체력 확인
                int hp = plugin.getConfig().getInt(key + ".health", IRON_MAX_HP);
                String progressBar = getProgressBar(hp, IRON_MAX_HP);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§e[Iron] 체력: " + progressBar + " §f(" + hp + " / " + IRON_MAX_HP + ")"));
            }
        }
    }

    // 아이템 확인 및 제거 유틸리티
    private boolean hasItem(Player p, Material m, int amt) {
        int count = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == m) count += item.getAmount();
        }
        return count >= amt;
    }

    private void removeItem(Player p, Material m, int amt) {
        p.getInventory().removeItem(new ItemStack(m, amt));
    }

    private String getProgressBar(int current, int max) {
        int bars = 10;
        int completedBars = (int) (((double) current / max) * bars);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            sb.append(i < completedBars ? "§e■" : "§7■");
        }
        return sb.toString();
    }
}