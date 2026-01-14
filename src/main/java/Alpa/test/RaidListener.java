package Alpa.test;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class RaidListener implements Listener {
    private final main plugin;

    public RaidListener(main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRaidBoxClick(PlayerInteractEvent event) {
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Location loc = event.getClickedBlock().getLocation();
        RaidManager.RaidInstance instance = plugin.getRaidManager().getInstance(loc);

        if (instance == null) return;

        event.setCancelled(true); // 기본 상자 열기 등 방지
        Player player = event.getPlayer();

        switch (instance.getState()) {
            case IDLE:
                instance.start();
                break;
            case RUNNING:
                player.sendMessage("§c이미 레이드가 진행 중입니다!");
                break;
            case REWARD_READY:
                // ⭐ 메서드 이름을 openRewardGui로 호출해야 합니다.
                instance.openRewardGui(player);
                break;
            case COOLDOWN:
                player.sendMessage("§b현재 쿨타임 중입니다. (남은 시간: " + instance.getCooldownRemain() + "초)");
                break;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // 인벤토리 타이틀 확인 (색상 코드 포함 주의)
        String title = event.getView().getTitle();
        if (title.startsWith("§8레이드 보상: ")) {
            Player player = (Player) event.getPlayer();
            String raidName = title.replace("§8레이드 보상: ", "");

            // ⭐ 모든 인스턴스를 돌며 이름이 같은 것을 찾아 보상 수령 완료 체크
            for (RaidManager.RaidInstance instance : plugin.getRaidManager().getAllInstances()) {
                if (instance.getTemplateName().equalsIgnoreCase(raidName)) {
                    instance.checkRewardComplete(player);
                    break;
                }
            }
        }
    }
    @EventHandler
    public void onRaidMobDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        String templateName = null;
        for (String tag : victim.getScoreboardTags()) {
            if (tag.startsWith("raid_mob_")) {
                templateName = tag.replace("raid_mob_", "");
                break;
            }
        }
        if (templateName == null) return;

        event.getDrops().clear();
        Random random = new Random();

        // ⭐ 커스텀 드랍(ItemStack 방식) 처리
        ConfigurationSection config = plugin.getRaidManager().getConfig();
        String path = "raidboxes." + templateName + ".mob.custom-drops";

        if (config.contains(path)) {
            List<?> customDrops = config.getList(path);
            for (Object obj : customDrops) {
                if (obj instanceof Map<?, ?> map) {
                    ItemStack item = (ItemStack) map.get("item");
                    int chance = (int) map.get("chance");

                    if (random.nextInt(100) < chance) {
                        event.getDrops().add(item.clone());
                    }
                }
            }
        }
    }
    // 1. 서로를 공격 대상으로 지정하는 것 방지 (AI 차단)
    @EventHandler
    public void onRaidMobTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        if (event.getTarget() == null) return;

        // 공격자와 대상 모두 레이드 몹인지 확인
        if (event.getEntity().getScoreboardTags().contains("raid_mob") &&
                event.getTarget().getScoreboardTags().contains("raid_mob")) {

            // 타겟 지정을 취소하여 서로 공격하지 않게 함
            event.setCancelled(true);
        }
    }

    // 2. 화살이나 광역 데미지로 서로에게 피해를 입히는 것 방지
    @EventHandler
    public void onRaidMobDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        // 발사체(화살 등)인 경우 발사한 주체를 찾음
        if (damager instanceof org.bukkit.entity.Projectile projectile) {
            if (projectile.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
        }

        // 공격자와 피해자 모두 레이드 몹인 경우 데미지 무효화
        if (damager.getScoreboardTags().contains("raid_mob") &&
                victim.getScoreboardTags().contains("raid_mob")) {

            event.setCancelled(true);
        }
    }
}