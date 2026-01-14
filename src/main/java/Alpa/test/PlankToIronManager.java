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

public class PlankToIronManager implements Listener {

    private final main plugin;
    private final int IRON_BLOCK_HP = 200;

    public PlankToIronManager(main plugin) {
        this.plugin = plugin;
        // Config 초기 설정
        if (!plugin.getConfig().contains("upgrade-settings.iron-cost")) {
            plugin.getConfig().set("upgrade-settings.iron-cost", 5); // 철괴 소모 기본값
            plugin.saveConfig();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUpgrade(PlayerInteractEvent e) {
        // 쉬프트 + 좌클릭 + 막대기 + 손 확인
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (e.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player player = e.getPlayer();
        if (!player.isSneaking()) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType() != Material.STICK) return;

        Block b = e.getClickedBlock();
        if (b == null || !b.getType().name().contains("_PLANKS")) return;

        String pKey = "planks." + plugin.blockToKey(b);

        // 데이터베이스에 등록된 판자인지 확인
        if (plugin.getConfig().contains(pKey)) {
            e.setCancelled(true); // 블록 타격 방지

            int ironCost = plugin.getConfig().getInt("upgrade-settings.iron-cost", 5);

            if (hasIronIngots(player, ironCost)) {
                removeIronIngots(player, ironCost);

                // 블록 변경 및 데이터 업데이트
                b.setType(Material.IRON_BLOCK);
                plugin.getConfig().set(pKey + ".health", IRON_BLOCK_HP);
                plugin.getConfig().set(pKey + ".type", "IRON_BLOCK"); // 타입 저장 (선택 사항)
                plugin.saveConfig();

                // 시각/청각 효과
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§f[Upgrade] §e철 블록으로 업그레이드 완료! (§f체력: " + IRON_BLOCK_HP + "§e)"));
                player.playSound(b.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f);
                b.getWorld().spawnParticle(Particle.CRIT, b.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3);
            } else {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§c[Upgrade] 철괴가 " + ironCost + "개 필요합니다!"));
            }
        }
    }

    private boolean hasIronIngots(Player player, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.IRON_INGOT) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    private void removeIronIngots(Player player, int amount) {
        int toRemove = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == Material.IRON_INGOT) {
                if (item.getAmount() > toRemove) {
                    item.setAmount(item.getAmount() - toRemove);
                    break;
                } else {
                    toRemove -= item.getAmount();
                    contents[i] = null;
                }
            }
            if (toRemove <= 0) break;
        }
        player.getInventory().setContents(contents);
    }
}