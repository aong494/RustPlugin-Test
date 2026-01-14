package Alpa.test;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class CustomMobSummoner {

    public static void spawnStaticMob(main plugin, Location loc, int modelData) {
        // 1. 주민 (히트박스/체력 담당)
        Villager base = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        base.setRemoveWhenFarAway(false);
        base.setPersistent(false);
        base.setAI(false);
        base.setInvisible(true);
        base.setSilent(true);
        base.setCollidable(false);

        // [Config 적용] 모델 데이터별 최대 체력 설정
        double maxHealth = plugin.getConfig().getDouble("mobs." + modelData + ".hp", 100.0);
        if (base.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            base.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
        }
        base.setHealth(maxHealth);

        // [Config 적용] 모델 데이터별 기본 이름 설정
        String baseName = plugin.getConfig().getString("mobs." + modelData + ".name", "§7커스텀 몹");

        // 주민 자체의 이름표는 끄고, 별도의 이름표 엔티티를 관리하도록 함
        base.setCustomNameVisible(false);
        base.setCustomName(baseName); // 나중에 가져오기 위해 저장

        // 2. 모델용 아머스탠드 (바닥 고정)
        Location modelLoc = loc.clone().add(0, -1, 0);
        ArmorStand modelHolder = (ArmorStand) loc.getWorld().spawnEntity(modelLoc, EntityType.ARMOR_STAND);
        modelHolder.setPersistent(true);
        modelHolder.setRemoveWhenFarAway(false);
        modelHolder.setPersistent(false);
        modelHolder.setInvisible(true);
        modelHolder.setMarker(true);
        modelHolder.setGravity(false);
        modelHolder.getEquipment().setHelmet(createModelItem(modelData));
        modelHolder.addScoreboardTag("custom_mob_model");
        modelHolder.addScoreboardTag("model_id_" + modelData);

        // 3. 이름표용 아머스탠드 (이름 전용)
        // 주민의 키(약 1.9)보다 위인 2.0 정도로 설정하면 머리 위에 뜹니다.
        Location nameTagLoc = loc.clone().add(0, 1.3, 0);
        ArmorStand nameTag = (ArmorStand) loc.getWorld().spawnEntity(nameTagLoc, EntityType.ARMOR_STAND);
        nameTag.setPersistent(true);
        nameTag.setRemoveWhenFarAway(false);
        nameTag.setPersistent(false);
        nameTag.setInvisible(true);
        nameTag.setMarker(true);
        nameTag.setGravity(false);
        nameTag.setCustomNameVisible(true);

        // 초기 이름 설정 (현재 체력 / 최대 체력)
        updateMobName(base, nameTag, baseName);

        // 나중에 리스너에서 nameTag를 찾기 위해 두 엔티티를 연결 (ScoreboardTag 활용)
        base.addScoreboardTag("custom_mob_base");
        nameTag.addScoreboardTag("name_tag_" + base.getUniqueId());
        base.addScoreboardTag("model_data_" + modelData);
    }

    // 아이템 생성 보조 메서드
    private static ItemStack createModelItem(int modelData) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(modelData);
            item.setItemMeta(meta);
        }
        return item;
    }

    // 이름표 업데이트 메서드
    public static void updateMobName(Villager base, ArmorStand nameTag, String baseName) {
        double health = Math.round(base.getHealth() * 10.0) / 10.0;
        double maxHealth = base.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
        nameTag.setCustomName(" §e(" + health + " / " + maxHealth + ")");
    }
}