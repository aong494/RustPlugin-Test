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

public class PlankRepairManager implements Listener {

    private final main plugin;

    public PlankRepairManager(main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onRepair(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (e.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Block b = e.getClickedBlock();
        if (b == null) return;

        Player player = e.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // 1. 막대기인지 확인
        if (mainHand.getType() == Material.STICK) {
            String key = plugin.blockToKey(b); // world_x_y_z
            String pPath = "planks." + key;

            // data.yml에 정보가 있는지 확인
            if (plugin.dataStorage.getConfig().contains(pPath)) {
                e.setCancelled(true);

                // [추가] 권한 확인: 본인 또는 그룹원만 수리 가능
                if (!plugin.canAccess(player, key, "planks")) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§c[Repair] 이 구조물을 수리할 권한이 없습니다."));
                    return;
                }

                String typeName = b.getType().name();
                int maxHp = 50;
                Material repairMat = null;
                String settingKey = "plank-settings";
                String displayName = "Plank";

                if (typeName.contains("IRON")) {
                    maxHp = 200;
                    repairMat = Material.IRON_INGOT;
                    displayName = "Iron";
                    settingKey = "upgrade-settings";
                } else if (typeName.contains("STONE_BRICK")) {
                    maxHp = 100;
                    repairMat = Material.COBBLESTONE;
                    displayName = "Stone";
                    settingKey = "stone-settings";
                }

                // [수정] dataStorage에서 현재 체력을 가져옴
                int currentHp = plugin.dataStorage.getConfig().getInt(pPath + ".health", maxHp);
                if (currentHp >= maxHp) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§a[" + displayName + "] 이미 체력이 가득 차 있습니다."));
                    return;
                }

                // 수리 비용 및 회복량 설정 (설정값은 여전히 config.yml에서 가져옴)
                int repairCost = plugin.getConfig().getInt(settingKey + ".repair-cost", 1);
                int repairAmt = plugin.getConfig().getInt(settingKey + ".repair-amount", 10);

                // 재료 확인 (기존 plugin에 구현된 메서드 활용)
                boolean hasMat = (repairMat != null) ? plugin.hasItem(player, repairMat, repairCost) : plugin.hasEnoughPlanks(player, repairCost);

                if (hasMat) {
                    if (repairMat != null) plugin.removeItem(player, repairMat, repairCost);
                    else plugin.removePlanks(player, repairCost);

                    int newHp = Math.min(maxHp, currentHp + repairAmt);

                    // [수정] dataStorage에 새로운 체력을 저장하고 data.yml 파일 저장
                    plugin.dataStorage.getConfig().set(pPath + ".health", newHp);
                    plugin.dataStorage.saveConfig();

                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§b[" + displayName + "] 수리 완료! " + plugin.getProgressBar(newHp, maxHp) + " §f(" + newHp + " / " + maxHp + ")"));

                    Sound s = typeName.contains("IRON") ? Sound.BLOCK_ANVIL_USE : (typeName.contains("STONE") ? Sound.BLOCK_STONE_PLACE : Sound.BLOCK_WOOD_PLACE);
                    player.playSound(b.getLocation(), s, 1.0f, 1.2f);
                    b.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, b.getLocation().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2);
                } else {
                    String matName = (repairMat == Material.COBBLESTONE) ? "조약돌" : (repairMat == Material.IRON_INGOT) ? "철괴" : "나무판자";
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§c[" + displayName + "] 수리하려면 " + matName + "가 " + repairCost + "개 필요합니다!"));
                }
            }
        }
    }
}