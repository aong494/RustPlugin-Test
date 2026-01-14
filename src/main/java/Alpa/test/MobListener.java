package Alpa.test;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class MobListener implements Listener {
    private final main plugin;
    private final Random random = new Random(); // ⭐ 이 줄을 추가하세요!

    public MobListener(main plugin) {
        this.plugin = plugin;
    }

    /**
     * 몹이 데미지를 입었을 때 실행되는 이벤트
     * 넉백 저항을 1.0(100%)으로 설정했더라도, 만약의 밀려남을 방지하기 위함입니다.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            if (villager.getScoreboardTags().contains("custom_mob_base")) {

                // 한 틱 뒤에 체력 업데이트
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!villager.isDead()) {
                        // ⭐ 수정됨: 주민에 저장된 커스텀 네임(기본 이름)을 가져옵니다.
                        String baseName = villager.getCustomName() != null ? villager.getCustomName() : "§7커스텀 몹";

                        villager.getNearbyEntities(1.0, 3.0, 1.0).forEach(e -> {
                            if (e instanceof ArmorStand && e.getScoreboardTags().contains("name_tag_" + villager.getUniqueId())) {
                                // ⭐ 수정됨: 3개의 인수를 모두 전달 (주민, 이름표, 기본이름)
                                CustomMobSummoner.updateMobName(villager, (ArmorStand) e, baseName);
                            }
                        });
                    }
                });
            }
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            if (villager.getScoreboardTags().contains("custom_mob_base")) {
                // 1. 기본 드랍물 및 경험치 제거
                event.getDrops().clear();
                event.setDroppedExp(0);

                Location deathLoc = villager.getLocation();
                int modelData = -1;

                // 태그에서 모델 데이터 추출
                for (String tag : villager.getScoreboardTags()) {
                    if (tag.startsWith("model_data_")) {
                        try {
                            modelData = Integer.parseInt(tag.replace("model_data_", ""));
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("모델 데이터 파싱 실패: " + tag);
                        }
                        break;
                    }
                }

                // ⭐ 리스폰 로직 (파일 저장 기능 추가)
                if (modelData != -1) {
                    long respawnSec = plugin.getConfig().getLong("mobs." + modelData + ".respawn-second", 0);
                    if (respawnSec > 0) {
                        // A. 서버 재부팅 대비 파일 기록
                        long respawnAt = System.currentTimeMillis() + (respawnSec * 1000L);
                        plugin.spawnManager.addPendingMob(deathLoc, modelData, respawnAt);

                        int finalModelData = modelData;
                        // B. 현재 서버 세션용 스케줄러
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            CustomMobSummoner.spawnStaticMob(plugin, deathLoc, finalModelData);

                            // C. 리스폰 완료 후 파일에서 제거
                            plugin.spawnManager.removePendingMob(deathLoc);
                        }, respawnSec * 20L);
                    }
                }

                // 2. 주변 모델 및 이름표 제거
                villager.getNearbyEntities(1.0, 3.0, 1.0).forEach(e -> {
                    if (e instanceof ArmorStand) e.remove();
                });

                // 3. 커스텀 드랍 아이템 계산
                handleCustomDrops(villager, event);
            }
        }
    }

    private void handleCustomDrops(Villager villager, EntityDeathEvent event) {
        for (String tag : villager.getScoreboardTags()) {
            if (tag.startsWith("model_data_")) {
                try {
                    String modelId = tag.replace("model_data_", "");
                    List<String> dropList = plugin.getConfig().getStringList("mobs." + modelId + ".drops");

                    if (dropList == null || dropList.isEmpty()) return;

                    for (String dropEntry : dropList) {
                        String[] split = dropEntry.split(":");
                        if (split.length < 3) continue;

                        String itemName = split[0].toUpperCase();
                        int amount = Integer.parseInt(split[1]);
                        int chance = Integer.parseInt(split[2]);

                        if (random.nextInt(100) < chance) {
                            Material material = Material.getMaterial(itemName);

                            // 만약 Material로 못 찾는다면 (모드 아이템인 경우)
                            if (material == null) {
                                // 최신 버전 버킷/스피갓에서는 모드 아이템의 경우
                                // Material.matchMaterial(itemName, true)를 시도해볼 수 있습니다.
                                material = Material.matchMaterial(itemName);
                            }

                            if (material != null) {
                                event.getDrops().add(new ItemStack(material, amount));
                            } else {
                                plugin.getLogger().warning("[System] 아이템을 찾을 수 없습니다: " + itemName);
                            }
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("드랍 로직 에러: " + e.getMessage());
                }
                break;
            }
        }
    }

    /**
     * 몹이 텔레포트되는 것을 방지 (움직이지 않는 몹 고정)
     */
    @EventHandler
    public void onMobTeleport(EntityTeleportEvent event) {
        if (event.getEntity().getCustomName() != null && event.getEntity().getCustomName().contains("[직업]")) {
            // 엔더맨 같은 몹을 베이스로 썼을 경우 텔레포트 차단
            event.setCancelled(true);
        }
    }
    @EventHandler
    public void onHorseSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.AbstractHorse) {
            org.bukkit.entity.AbstractHorse horse = (org.bukkit.entity.AbstractHorse) event.getEntity();
            horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        }
    }
    @EventHandler
    public void onHorseDeath(EntityDeathEvent event) {
        // 1. 죽은 엔티티가 말 종류(AbstractHorse)인지 확인
        if (event.getEntity() instanceof AbstractHorse) {
            // 2. 드랍 아이템 목록(List<ItemStack>)에서 안장을 찾아 제거
            Iterator<ItemStack> iterator = event.getDrops().iterator();

            while (iterator.hasNext()) {
                ItemStack drop = iterator.next();

                // 아이템이 안장(Saddle)인 경우 드랍 목록에서 삭제
                if (drop != null && drop.getType() == Material.SADDLE) {
                    iterator.remove();
                }
            }
        }
    }
}