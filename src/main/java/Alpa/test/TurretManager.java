package Alpa.test;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.*;
import java.util.*;

public class TurretManager implements Listener {
    private final main plugin;
    private final File blockFile;
    private FileConfiguration blockConfig;
    private final Map<Location, Inventory> activeInvs = new HashMap<>();
    private final Map<Location, Long> lastTargetedTicks = new HashMap<>();
    private final Map<Location, ItemDisplay> turretModels = new HashMap<>();

    public TurretManager(main plugin) {
        this.plugin = plugin;
        this.blockFile = new File(plugin.getDataFolder(), "turret_blocks.yml");
        this.blockConfig = YamlConfiguration.loadConfiguration(blockFile);

        // 서버 시작 시 기존 터렛들의 인벤토리를 파일에서 메모리로 로드
        loadAllTurrets();
        startTurretTask();
    }
    private final Map<Location, Long> lastFireTicks = new HashMap<>();

    // --- [데이터 저장/로드 핵심 로직] ---

    private void saveBlocks() {
        try { blockConfig.save(blockFile); } catch (IOException e) { e.printStackTrace(); }
    }

    private String locToKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    // 아이템 배열을 Base64 문자열로 변환 (저장용)
    private String itemStackArrayToBase64(ItemStack[] items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) dataOutput.writeObject(item);
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) { return ""; }
    }

    // Base64 문자열을 아이템 배열로 변환 (로드용)
    private ItemStack[] itemStackArrayFromBase64(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack[] items = new ItemStack[dataInput.readInt()];
            for (int i = 0; i < items.length; i++) items[i] = (ItemStack) dataInput.readObject();
            dataInput.close();
            return items;
        } catch (Exception e) { return new ItemStack[9]; }
    }

    private void loadAllTurrets() {
        for (String key : blockConfig.getKeys(false)) {
            String[] parts = key.split(",");
            Location loc = new Location(Bukkit.getWorld(parts[0]),
                    Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
            // 엔티티 로드 (중복 소환 방지 핵심)
            String uuidStr = blockConfig.getString(key + ".model_uuid");
            if (uuidStr != null) {
                UUID uuid = UUID.fromString(uuidStr);
                Entity entity = Bukkit.getEntity(uuid);
                if (entity instanceof ItemDisplay display) {
                    turretModels.put(loc, display);
                }
            }
            // 인벤토리 로드
            String encodedItems = blockConfig.getString(key + ".items");
            Inventory inv = Bukkit.createInventory(null, 9, "§6터렛 설정");
            if (encodedItems != null) inv.setContents(itemStackArrayFromBase64(encodedItems));
            activeInvs.put(loc, inv);
        }
    }
    // --- [이벤트 핸들러] ---

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getView().getTitle().equals("§6터렛 설정")) {
            // 현재 닫힌 인벤토리가 어느 터렛의 것인지 찾아서 저장
            for (Map.Entry<Location, Inventory> entry : activeInvs.entrySet()) {
                if (entry.getValue().equals(e.getInventory())) {
                    String key = locToKey(entry.getKey());
                    blockConfig.set(key + ".items", itemStackArrayToBase64(e.getInventory().getContents()));
                    saveBlocks();
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlock().getType() == Material.GOLD_BLOCK) {
            Location loc = e.getBlock().getLocation();
            String key = locToKey(loc);

            // 1. 소유자 저장
            blockConfig.set(key + ".owner", e.getPlayer().getUniqueId().toString());

            // 2. 상부 모델 소환 (CustomModelData 2)
            ItemStack turretTop = new ItemStack(Material.PAPER);
            ItemMeta meta = turretTop.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(2);
                turretTop.setItemMeta(meta);
            }

            Location modelLoc = loc.clone().add(0.5, 0.5, 0.5); // 블록 중앙
            ItemDisplay display = loc.getWorld().spawn(modelLoc, ItemDisplay.class, ent -> {
                ent.setItemStack(turretTop);
                ent.setBrightness(new Display.Brightness(15, 15));
            });

            // 3. 파일에 엔티티 UUID 저장 (재부팅 대비)
            blockConfig.set(key + ".model_uuid", display.getUniqueId().toString());
            saveBlocks();

            activeInvs.put(loc, Bukkit.createInventory(null, 9, "§6터렛 설정"));
            turretModels.put(loc, display);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() == Material.GOLD_BLOCK) {
            Location loc = e.getBlock().getLocation();

            // 1. 맵에서 엔티티를 찾아 즉시 제거
            ItemDisplay model = turretModels.get(loc);
            if (model != null) {
                model.remove();
                turretModels.remove(loc);
            } else {
                // 만약 맵에 없다면 (재부팅 후 로드 실패 등), 근처의 ItemDisplay를 뒤져서 제거 (안전장치)
                loc.getWorld().getNearbyEntities(loc.add(0.5, 0.5, 0.5), 1.0, 1.0, 1.0).forEach(entity -> {
                    if (entity instanceof ItemDisplay) {
                        // CustomModelData가 2번인 것만 골라서 삭제
                        ItemStack item = ((ItemDisplay) entity).getItemStack();
                        if (item != null && item.hasItemMeta() && item.getItemMeta().getCustomModelData() == 2) {
                            entity.remove();
                        }
                    }
                });
            }

            // 2. 아이템 드롭 및 데이터 삭제 호출
            dropTurretItems(loc);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getType() == Material.GOLD_BLOCK) {
            if (e.getPlayer().getInventory().getItemInMainHand().getType() == Material.STICK) {
                return;
            }
            e.setCancelled(true);
            Inventory inv = activeInvs.get(e.getClickedBlock().getLocation());
            if (inv != null) e.getPlayer().openInventory(inv);
        }
    }

    // --- [터렛 태스크 및 사격 로직] ---

    public void startTurretTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTick = Bukkit.getWorlds().get(0).getFullTime();

                for (Map.Entry<Location, Inventory> entry : activeInvs.entrySet()) {
                    Location loc = entry.getKey();
                    Inventory inv = entry.getValue();

                    // 1. 전력 확인
                    if (!plugin.electricManager.isPowered(loc)) continue;

                    // 2. 무기 정보 및 쿨타임 체크
                    ItemStack weaponStack = inv.getItem(0);
                    String weaponId = (weaponStack != null) ? weaponStack.getType().getKey().toString() : "default";
                    String configPath = plugin.turretConfig.contains("weapons." + weaponId) ? "weapons." + weaponId : "weapons.default";
                    int fireRate = plugin.turretConfig.getInt(configPath + ".fire_rate", 20);

                    long lastFire = lastFireTicks.getOrDefault(loc, 0L);
                    if (currentTick - lastFire < fireRate) continue;

                    // 3. 타겟 탐색
                    double range = plugin.turretConfig.getDouble("settings.range", 15.0);
                    Player detectedTarget = findTarget(loc, range);
                    ItemDisplay model = turretModels.get(loc); // 상부 모델 엔티티

                    // --- [타겟이 있을 때만 회전 및 공격 로직 작동] ---
                    if (detectedTarget != null) {

                        // A. 상부 모델 회전 (좌우 Yaw만)
                        if (model != null && model.isValid()) {
                            Vector direction = detectedTarget.getLocation().toVector().subtract(model.getLocation().toVector());
                            direction.setY(0); // 좌우로만 고정

                            if (direction.lengthSquared() > 0) {
                                Location lookAt = model.getLocation().clone().setDirection(direction.normalize());
                                model.teleport(lookAt);
                            }
                        }

                        // B. 락온 사운드 및 타이머 로직
                        if (!lastTargetedTicks.containsKey(loc)) {
                            lastTargetedTicks.put(loc, currentTick);

                            // 락온 사운드 (삐-빅)
                            loc.getWorld().playSound(loc, "block.note_block.bit", SoundCategory.BLOCKS, 1.0f, 1.5f);
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (activeInvs.containsKey(loc)) // 터렛이 아직 존재할 때만
                                        loc.getWorld().playSound(loc, "block.note_block.bit", SoundCategory.BLOCKS, 1.0f, 1.0f);
                                }
                            }.runTaskLater(plugin, 3L);

                            loc.getWorld().spawnParticle(Particle.VILLAGER_ANGRY, loc.clone().add(0.5, 1.5, 0.5), 1);
                        }

                        // C. 사격 로직 (락온 후 0.5초 대기)
                        if (currentTick - lastTargetedTicks.get(loc) >= 10) {
                            Player finalTarget = findTarget(loc, range);
                            if (finalTarget != null && finalTarget.equals(detectedTarget)) {
                                if (shoot(loc, finalTarget, inv, configPath)) {
                                    lastFireTicks.put(loc, currentTick);
                                }
                            }
                            lastTargetedTicks.remove(loc); // 사격 후 또는 타겟 소실 후 초기화
                        }
                    } else {
                        // 타겟이 없으면 락온 상태 초기화
                        lastTargetedTicks.remove(loc);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Player findTarget(Location loc, double range) {
        Player closest = null;
        double minDistance = range;

        for (Player player : loc.getWorld().getPlayers()) {
            // 1. 기본 유효성 검사
            if (player == null || !player.isOnline() || player.isDead()) continue;
            if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) continue;

            double dist = player.getLocation().distance(loc);
            if (dist <= minDistance) {
                if (!canSee(loc, player)) continue;

                // 2. 소유자 정보 가져오기
                String ownerUUIDStr = blockConfig.getString(locToKey(loc) + ".owner");
                if (ownerUUIDStr == null) continue;

                UUID ownerUUID = UUID.fromString(ownerUUIDStr);

                // [중요] 만약 타겟 플레이어가 설치한 주인 본인이라면 패스!
                if (player.getUniqueId().equals(ownerUUID)) continue;

                // 3. 그룹 체크
                OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUUID);
                String ownerGroup = plugin.groupManager.getPlayerGroup(owner);
                String targetGroup = plugin.groupManager.getPlayerGroup(player);

                // 주인이 그룹에 속해 있고, 타겟도 같은 그룹이라면 패스
                if (ownerGroup != null && ownerGroup.equals(targetGroup)) continue;

                minDistance = dist;
                closest = player;
            }
        }
        return closest;
    }
    private boolean canSee(Location start, Player target) {
        Location turretEye = start.clone().add(0.5, 1.2, 0.5); // 터렛의 "눈" 위치
        Location targetEye = target.getEyeLocation(); // 플레이어의 머리 위치

        Vector direction = targetEye.toVector().subtract(turretEye.toVector());
        double distance = turretEye.distance(targetEye);

        // 터렛 눈 위치에서 타겟 방향으로 레이트레이스 실행
        // fluidHandling.NONE: 액체는 무시, blockInteraction.VISUAL: 시각적 블록(벽) 체크
        org.bukkit.util.RayTraceResult result = turretEye.getWorld().rayTraceBlocks(
                turretEye,
                direction.normalize(),
                distance,
                org.bukkit.FluidCollisionMode.NEVER,
                true
        );

        // 가로막는 블록이 없으면(result가 null이면) 시야가 확보된 것임
        return result == null || result.getHitBlock() == null;
    }

    private boolean shoot(Location loc, Player target, Inventory inv, String configPath) {
        // 1. 해당 무기가 요구하는 탄약 확인
        String ammoKey = plugin.turretConfig.getString(configPath + ".ammo_item", "minecraft:iron_nugget");

        // 탄약 소비 로직 (1~8번 슬롯 뒤지기)
        boolean hasAmmo = false;
        for (int i = 1; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType().getKey().toString().equalsIgnoreCase(ammoKey)) {
                item.setAmount(item.getAmount() - 1);
                hasAmmo = true;
                break;
            }
        }
        if (!hasAmmo) return false;

        // 2. 대미지 적용
        double damage = plugin.turretConfig.getDouble(configPath + ".damage", 2.0);

        // 3. 시각 효과 및 실제 타격
        Location turretEye = loc.clone().add(0.5, 1.2, 0.5); // 터렛 "눈" 위치
        Vector direction = target.getEyeLocation().toVector().subtract(turretEye.toVector()).normalize();
        double distance = turretEye.distance(target.getEyeLocation());

        // [사운드] JEG 총기 사운드 재생
        String soundName = plugin.turretConfig.getString(configPath + ".sound", "entity.generic.explode");
        turretEye.getWorld().playSound(turretEye, soundName, SoundCategory.BLOCKS, 1.0f, 1.0f);

        // [궤적] 즉시 그려지는 총알 궤적 (Tracer)
        for (double i = 0; i < distance; i += 0.3) {
            Location point = turretEye.clone().add(direction.clone().multiply(i));

            // 총알 느낌을 위해 작고 빠른 연기 파티클 사용
            point.getWorld().spawnParticle(Particle.SMOKE_NORMAL, point, 1, 0, 0, 0, 0.0);

            // 총구 화염 효과 (처음 1블록 구간)
            if (i < 1.0) {
                point.getWorld().spawnParticle(Particle.FLAME, point, 1, 0.02, 0.02, 0.02, 0.01);
            }
        }

        // 4. 실제 대미지 입히기
        target.damage(damage);

        // [효과] 피격 지점 피 파티클 (붉은 레드스톤)
        target.getWorld().spawnParticle(Particle.REDSTONE, target.getEyeLocation(), 5, 0.1, 0.1, 0.1, new Particle.DustOptions(Color.RED, 1.0f));

        return true;
    }
    public void removeTurret(Location loc) {
        ItemDisplay model = turretModels.get(loc);
        if (model != null) {
            model.remove(); // 여기서 엔티티만 제거하므로 아이템은 떨어지지 않음
            turretModels.remove(loc);
        } else {
            // 안전장치: 주변 1블록 내의 터렛 모델 강제 제거
            loc.getWorld().getNearbyEntities(loc.clone().add(0.5, 0.5, 0.5), 0.8, 0.8, 0.8).forEach(ent -> {
                if (ent instanceof ItemDisplay display) {
                    ItemStack item = display.getItemStack();
                    if (item != null && item.hasItemMeta() && item.getItemMeta().getCustomModelData() == 2) {
                        ent.remove();
                    }
                }
            });
        }
    }
    public void dropTurretItems(Location loc) {
        Inventory inv = activeInvs.get(loc);
        if (inv != null) {
            for (ItemStack item : inv.getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    // 터렛 안에 들어있던 무기, 탄약 등만 드롭
                    loc.getWorld().dropItemNaturally(loc, item);
                }
            }
            inv.clear();
        }

        // 데이터 정리
        String key = locToKey(loc);
        blockConfig.set(key, null);
        saveBlocks();

        activeInvs.remove(loc);
        lastTargetedTicks.remove(loc);
        lastFireTicks.remove(loc);
    }
}