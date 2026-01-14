package Alpa.test;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.Player;


import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RaidManager {
    private final main plugin;
    private File configFile;
    private FileConfiguration raidConfig;
    private final Map<Location, RaidInstance> activeInstances = new HashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    public RaidManager(main plugin) {
        this.plugin = plugin;
        setupConfig();
        setupDataFile();
        // ⭐ 서버가 완전히 켜진 후 안전하게 로드하기 위해 딜레이를 100틱(5초)으로 상향
        new BukkitRunnable() {
            @Override
            public void run() {
                loadPlacedBoxes();
            }
        }.runTaskLater(plugin, 100L);
    }

    public void cleanup() {
        for (RaidInstance instance : activeInstances.values()) {
            instance.removeHologram();
            if (instance.currentTask != null) instance.currentTask.cancel();
        }
    }

    private void setupConfig() {
        configFile = new File(plugin.getDataFolder(), "raidbox.yml");
        if (!configFile.exists()) plugin.saveResource("raidbox.yml", false);
        raidConfig = YamlConfiguration.loadConfiguration(configFile);
    }

    private void setupDataFile() {
        dataFile = new File(plugin.getDataFolder(), "placed_boxes.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void savePlacedBoxes() {
        try { dataConfig.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public void loadPlacedBoxes() {
        // 1. 월드가 아직 준비 안 되었을 수 있으므로 1초 뒤에 실행 (비동기 방지)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (dataConfig == null || !dataConfig.contains("placed")) return;

            ConfigurationSection section = dataConfig.getConfigurationSection("placed");
            for (String key : section.getKeys(false)) {
                String worldName = section.getString(key + ".world");
                double x = section.getDouble(key + ".x");
                double y = section.getDouble(key + ".y");
                double z = section.getDouble(key + ".z");
                String template = section.getString(key + ".template");

                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location loc = new Location(world, x, y, z);

                    // ⭐ 핵심: 해당 구역 청크를 강제로 로드해야 홀로그램(엔티티)이 생성됨
                    if (!loc.getChunk().isLoaded()) {
                        loc.getChunk().load();
                    }

                    RaidInstance instance = new RaidInstance(loc, template);
                    activeInstances.put(loc, instance);

                    // 생성 직후 홀로그램 강제 초기화
                    instance.updateHologram("§e우클릭 시 시작", ChatColor.YELLOW);
                }
            }
            plugin.getLogger().info("[Raid] 리부팅 후 레이드 박스 복구 완료.");
        }, 20L); // 20틱 = 1초 지연
    }

    public void createInstance(Location loc, String templateName) {
        if (!raidConfig.contains("raidboxes." + templateName)) return;

        String type = raidConfig.getString("raidboxes." + templateName + ".type", "CHEST");
        Material mat = Material.matchMaterial(type);
        loc.getBlock().setType(mat != null ? mat : Material.CHEST);

        RaidInstance instance = new RaidInstance(loc, templateName);
        activeInstances.put(loc, instance);

        String id = loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
        dataConfig.set("placed." + id + ".world", loc.getWorld().getName());
        dataConfig.set("placed." + id + ".x", (double)loc.getBlockX());
        dataConfig.set("placed." + id + ".y", (double)loc.getBlockY());
        dataConfig.set("placed." + id + ".z", (double)loc.getBlockZ());
        dataConfig.set("placed." + id + ".template", templateName);
        savePlacedBoxes();
    }

    public boolean removeInstance(String templateName) {
        Location targetLoc = null;
        String targetId = null;

        for (Map.Entry<Location, RaidInstance> entry : activeInstances.entrySet()) {
            if (entry.getValue().getTemplateName().equalsIgnoreCase(templateName)) {
                targetLoc = entry.getKey();
                targetId = targetLoc.getWorld().getName() + "_" + targetLoc.getBlockX() + "_" + targetLoc.getBlockY() + "_" + targetLoc.getBlockZ();
                break;
            }
        }

        if (targetLoc != null) {
            activeInstances.get(targetLoc).removeHologram();
            if (activeInstances.get(targetLoc).currentTask != null) activeInstances.get(targetLoc).currentTask.cancel();
            targetLoc.getBlock().setType(Material.AIR);
            activeInstances.remove(targetLoc);
            dataConfig.set("placed." + targetId, null);
            savePlacedBoxes();
            return true;
        }
        return false;
    }

    public RaidInstance getInstance(Location loc) { return activeInstances.get(loc); }
    public Collection<RaidInstance> getAllInstances() { return activeInstances.values(); }
    public FileConfiguration getConfig() { return raidConfig; }

    public class RaidInstance {
        private final Location location;
        private final String templateName;
        private RaidState state = RaidState.IDLE;
        private int timeLeft;
        private long cooldownEnd = 0;
        private ArmorStand hologram;
        private BukkitRunnable currentTask;
        private Inventory rewardInv;
        public int getTimeLeft() {return this.timeLeft;}

        public RaidInstance(Location loc, String templateName) {
            this.location = loc;
            this.templateName = templateName;
            // ⭐ 인스턴스 생성 시 2초 뒤 홀로그램 소환
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                updateHologram("§e우클릭 시 시작", ChatColor.YELLOW);
            }, 40L);
        }

        private void removeExistingHolograms(Location loc) {
            if (loc.getWorld() == null) return;
            loc.getWorld().getNearbyEntities(loc.clone().add(0.5, 1.2, 0.5), 1.0, 2.0, 1.0).forEach(entity -> {
                if (entity instanceof ArmorStand as) {
                    if (as.getScoreboardTags().contains("raid_hologram") || (as.getCustomName() != null && as.getCustomName().contains("시작"))) {
                        as.remove();
                    }
                }
            });
        }

        public void start() {
            if (state != RaidState.IDLE) return;
            if (System.currentTimeMillis() < cooldownEnd) return;

            ConfigurationSection section = raidConfig.getConfigurationSection("raidboxes." + templateName);
            if (section == null) return;

            timeLeft = section.getInt("timer");
            int configSpawnTime = section.getInt("spawn-time"); // 설정값 미리 가져오기
            state = RaidState.RUNNING;

            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', section.getString("message", "")));

            currentTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (timeLeft <= 0) {
                        state = RaidState.REWARD_READY;
                        updateHologram("§a[보상 획득 가능]", ChatColor.GREEN);
                        this.cancel();
                        return;
                    }

                    // ⭐ 수정: 몹 소환 조건 체크 및 로그 추가
                    if (timeLeft == configSpawnTime) {
                        plugin.getLogger().info("[Raid] " + templateName + " 몹 소환 시도 중... (남은시간: " + timeLeft + ")");
                        ConfigurationSection mobSec = section.getConfigurationSection("mob");
                        if (mobSec != null) {
                            int amount = mobSec.getInt("amount", 1);
                            for (int i = 0; i < amount; i++) spawnMob(mobSec);
                        } else {
                            plugin.getLogger().warning("[Raid] '" + templateName + "'의 mob 설정 섹션을 찾을 수 없습니다!");
                        }
                    }

                    updateHologram("§c남은 시간: " + timeLeft + "초", ChatColor.RED);
                    timeLeft--;
                }
            };
            currentTask.runTaskTimer(plugin, 0, 20L);
        }

        public void openRewardGui(Player player) {
            if (rewardInv == null) {
                rewardInv = Bukkit.createInventory(null, 27, "§8레이드 보상: " + templateName);
                List<String> rewards = raidConfig.getStringList("raidboxes." + templateName + ".rewards");
                for (String s : rewards) {
                    String[] split = s.split(":");
                    Material m = Material.matchMaterial(split[0]);
                    if (m != null) rewardInv.addItem(new ItemStack(m, Integer.parseInt(split[1])));
                }
            }
            player.openInventory(rewardInv);
        }

        public void checkRewardComplete(Player player) {
            if (rewardInv == null) return;
            boolean isEmpty = Arrays.stream(rewardInv.getContents()).allMatch(i -> i == null || i.getType() == Material.AIR);
            if (isEmpty) {
                startCooldown();
                player.sendMessage("§a[Raid] 모든 보상을 수령하여 쿨타임이 시작됩니다.");
            }
        }
        private void startCooldown() {
            state = RaidState.COOLDOWN;
            int cd = raidConfig.getInt("raidboxes." + templateName + ".cooldown");
            cooldownEnd = System.currentTimeMillis() + (cd * 1000L);
            new BukkitRunnable() {
                @Override
                public void run() {
                    long remain = (cooldownEnd - System.currentTimeMillis()) / 1000L;
                    if (remain <= 0) {
                        state = RaidState.IDLE;
                        rewardInv = null;
                        updateHologram("§e우클릭 시 시작", ChatColor.YELLOW);
                        this.cancel();
                    } else {
                        updateHologram("§b쿨타임: " + remain + "초", ChatColor.AQUA);
                    }
                }
            }.runTaskTimer(plugin, 0, 20L);
        }

        private void updateHologram(String text, ChatColor color) {
            if (location.getWorld() == null) return;

            // 1. 청크가 로드되지 않았다면 소환이 불가능하므로 강제 로드
            if (!location.getChunk().isLoaded()) {
                location.getChunk().load();
            }

            // 2. 기존 홀로그램이 없거나, 유효하지 않거나(죽음), 서버 리부팅으로 초기화된 경우
            if (hologram == null || !hologram.isValid() || hologram.isDead()) {
                // 주변에 혹시 남아있을지 모르는 중복 홀로그램 제거
                removeExistingHolograms(location);

                // 새로운 아머스탠드 소환
                hologram = (ArmorStand) location.getWorld().spawnEntity(
                        location.clone().add(0.5, 1.2, 0.5),
                        EntityType.ARMOR_STAND
                );
                hologram.setGravity(false);
                hologram.setVisible(false);
                hologram.setMarker(true);
                hologram.setCustomNameVisible(true);
                hologram.setPersistent(false);
                hologram.addScoreboardTag("raid_hologram");
            }
            hologram.setCustomName(color + text);
        }

        public void removeHologram() { if (hologram != null) hologram.remove(); }

        private void spawnMob(ConfigurationSection mobSec) {
            if (mobSec == null) return;

            World world = Bukkit.getWorld(mobSec.getString("world", "world"));
            if (world == null) world = location.getWorld();
            if (world == null) return;

            // 1. 랜덤 객체 생성
            Random random = new Random();

            // 2. 설정된 기준 좌표 가져오기
            double baseX = mobSec.getDouble("x", location.getX());
            double baseY = mobSec.getDouble("y", location.getY());
            double baseZ = mobSec.getDouble("z", location.getZ());

            // 3. 분산 범위 설정 (예: 반경 3블록 이내 무작위)
            // random.nextDouble() * 6 - 3 은 -3.0에서 +3.0 사이의 값을 반환합니다.
            double offsetX = (random.nextDouble() * 6) - 3;
            double offsetZ = (random.nextDouble() * 6) - 3;

            // 4. 최종 소환 위치 계산 (Y값은 끼임 방지를 위해 0.5~1 정도 높여주는 것이 좋습니다)
            Location spawnLoc = new Location(world, baseX + offsetX, baseY + 0.5, baseZ + offsetZ);

            try {
                String typeStr = mobSec.getString("type", "HUSK").toUpperCase();
                LivingEntity mob = (LivingEntity) world.spawnEntity(spawnLoc, EntityType.valueOf(typeStr));

                mob.setCustomName(ChatColor.translateAlternateColorCodes('&', mobSec.getString("name", "")));
                mob.setCustomNameVisible(true);
                mob.addScoreboardTag("raid_mob");
                mob.addScoreboardTag("raid_mob_" + templateName);

                // 3. 체력 및 속도 설정
                double hp = mobSec.getDouble("hp", 20.0);
                mob.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(hp);
                mob.setHealth(hp);
                mob.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(mobSec.getDouble("speed", 0.2));

                // 4. ⭐ 작동 확인된 장비 설정 로직 그대로 적용
                if (mob.getEquipment() != null) {
                    // 무기 설정
                    String weaponName = mobSec.getString("weapon", "AIR");
                    Material weaponMat = Material.matchMaterial(weaponName); // 사용자님의 기존 방식
                    if (weaponMat != null && weaponMat != Material.AIR) {
                        mob.getEquipment().setItemInMainHand(new ItemStack(weaponMat));
                        mob.getEquipment().setItemInMainHandDropChance(0f);
                    }

                    // 방어구 설정
                    ConfigurationSection armor = mobSec.getConfigurationSection("armor");
                    if (armor != null) {
                        setEquipment(mob, "helmet", armor.getString("helmet"));
                        setEquipment(mob, "chest", armor.getString("chestplate"));
                        setEquipment(mob, "legs", armor.getString("leggings"));
                        setEquipment(mob, "boots", armor.getString("boots"));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Raid] 몹 생성 중 오류: " + e.getMessage());
            }
        }

        // 장비 설정을 위한 보조 메서드 (코드 중복 방지)
        private void setEquipment(LivingEntity mob, String slot, String matName) {
            if (matName == null || matName.equalsIgnoreCase("AIR")) return;
            Material mat = Material.matchMaterial(matName);
            if (mat == null) return;

            ItemStack item = new ItemStack(mat);
            switch (slot) {
                case "helmet" -> mob.getEquipment().setHelmet(item);
                case "chest" -> mob.getEquipment().setChestplate(item);
                case "legs" -> mob.getEquipment().setLeggings(item);
                case "boots" -> mob.getEquipment().setBoots(item);
            }
            // 드랍 방지 (내구도 닳아서 떨어지는 것 방지)
            setDropChance(mob, slot);
        }

        private void setDropChance(LivingEntity mob, String slot) {
            if (slot.equals("helmet")) mob.getEquipment().setHelmetDropChance(0f);
            else if (slot.equals("chest")) mob.getEquipment().setChestplateDropChance(0f);
            else if (slot.equals("legs")) mob.getEquipment().setLeggingsDropChance(0f);
            else if (slot.equals("boots")) mob.getEquipment().setBootsDropChance(0f);
        }

        public String getTemplateName() { return templateName; }
        public RaidState getState() { return state; }
        public long getCooldownRemain() { return Math.max(0, (cooldownEnd - System.currentTimeMillis()) / 1000L); }
    }
    public enum RaidState { IDLE, RUNNING, REWARD_READY, COOLDOWN }
    // RaidManager.java 파일 맨 아래쪽 } 바로 위에 붙여넣으세요.

    public void addHandItemToDrops(Player player, String templateName, int chance) {
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem == null || handItem.getType() == Material.AIR) {
            player.sendMessage("§c[Raid] 손에 아이템을 들고 있어야 합니다.");
            return;
        }

        String path = "raidboxes." + templateName + ".mob.custom-drops";
        // 기존 리스트 가져오기
        List<Map<String, Object>> customDrops = (List<Map<String, Object>>) raidConfig.getList(path, new ArrayList<>());

        Map<String, Object> itemData = new HashMap<>();
        itemData.put("item", handItem.clone());
        itemData.put("chance", chance);

        customDrops.add(itemData);
        raidConfig.set(path, customDrops);
        saveRaidConfig();

        player.sendMessage("§a[Raid] " + templateName + " 몹 드랍템으로 등록 완료! (§f" + chance + "%§a)");
    }

    public void setHandItemToEquipment(Player player, String templateName, String slot) {
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem == null || handItem.getType() == Material.AIR) {
            player.sendMessage("§c[Raid] 손에 아이템을 들고 있어야 합니다.");
            return;
        }

        String path = slot.equalsIgnoreCase("weapon") ?
                "raidboxes." + templateName + ".mob.custom-weapon" :
                "raidboxes." + templateName + ".mob.custom-armor." + slot.toLowerCase();

        raidConfig.set(path, handItem.clone());
        saveRaidConfig();

        player.sendMessage("§a[Raid] " + templateName + " 몹의 " + slot + " 부위 장비를 등록했습니다.");
    }

    private void saveRaidConfig() {
        try {
            raidConfig.save(configFile);
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("[Raid] 설정을 저장하는 중 오류 발생: " + e.getMessage());
        }
    }
}