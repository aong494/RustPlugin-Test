package Alpa.test;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Pose;
import net.citizensnpcs.api.event.NPCDeathEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

public class DeadManager implements Listener {

    private final main plugin;
    private final Map<UUID, Location> downedLocations = new HashMap<>();
    private final Map<UUID, BukkitRunnable> activeTasks = new HashMap<>();
    private final Set<UUID> processing = new HashSet<>();
    private final Map<UUID, UUID> hitboxEntityMap = new HashMap<>();
    private final Map<UUID, UUID> revivingPlayers = new HashMap<>();
    private final Map<UUID, Integer> reviveProgress = new HashMap<>();
    private final Map<UUID, NPC> playerNPCs = new HashMap<>();
    private final Map<UUID, ItemStack[]> inventoryCache = new HashMap<>();
    private final Set<UUID> deathReserved = new HashSet<>();
    private FileConfiguration config;

    public DeadManager(main plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "dead.yml");
        if (!configFile.exists()) {
            plugin.saveResource("dead.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Location loc = player.getLocation().clone().add(0, 0.1, 0);

        inventoryCache.put(uuid, player.getInventory().getContents().clone());

        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, player.getName());
        npc.data().setPersistent("persistent", true);

        // [중요] 나중에 꺼내 쓰기 쉽게 좌표를 문자열로 저장
        npc.data().setPersistent("fixed-location",
                loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch());

        npc.spawn(loc);
        npc.setProtected(false);
        npc.data().setPersistent("owner-uuid", uuid.toString());
        npc.data().setPersistent("player-animation", "SIT");
        npc.data().setPersistent("lookclose", false);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (npc.isSpawned() && npc.getEntity() instanceof Player npcPlayer) {

                // 1. 무적 상태 완전 해제
                npcPlayer.setInvulnerable(false); // 마인크래프트 기본 무적 해제
                npc.setProtected(false);         // Citizens 보호 해제

                // 2. API를 통해 체력 직접 강제 주입 (가장 확실함)
                double targetHealth = 5.0; // 하트 2.5개 분량
                npcPlayer.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(targetHealth);
                npcPlayer.setHealth(targetHealth);

                // 3. 명령어는 보조 수단으로 한 번 더 실행
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc select " + npc.getId());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc health --max 5");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc health --set 5");

                // 4. 애니메이션 및 AI 설정
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc panimate SIT");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc gravity false");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc pathrange 0");
            }
        }, 20L);

        playerNPCs.put(uuid, npc);
    }
    public void restoreNPCAnimations() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            CitizensAPI.getNPCRegistry().forEach(npc -> {
                // "player-animation"이 "SIT"인 경우를 찾도록 수정
                Object anim = npc.data().get("player-animation");
                if (anim != null && anim.toString().equals("SIT")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc select " + npc.getId());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc panimate SIT");
                }
            });
        }, 60L);
    }
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String uuidString = player.getUniqueId().toString();

        // 1. NPC 제거 (기존 로직 동일)
        List<NPC> toRemove = new ArrayList<>();
        CitizensAPI.getNPCRegistry().forEach(npc -> {
            Object data = npc.data().get("owner-uuid");
            if (data != null && data.toString().equals(uuidString)) {
                toRemove.add(npc);
            }
        });
        for (NPC npc : toRemove) { npc.destroy(); }
        // 2. 오프라인 사망 처리 로직
        if (config.getBoolean("pending-deaths." + uuidString)) {
            // 인벤토리 비우기
            player.getInventory().clear();

            // 1틱 뒤에 처리 (위치 선점을 위해)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // "이미 죽은 상태"로 간주하고 침대나 랜덤 스폰으로 보냄
                Location spawnLoc = null;

                // [BedManager 연동] 침대 좌표 확인
                String activeBedLoc = plugin.getConfig().getString("active_bed." + uuidString);
                if (activeBedLoc != null) {
                    // 침대 클래스에서 사용하는 좌표 변환 메서드 활용 (BedManager에 public으로 선언되어 있어야 함)
                    spawnLoc = stringToLocation(activeBedLoc);
                }

                // 침대가 없으면 랜덤 스폰
                if (spawnLoc == null || spawnLoc.getBlock().getType() == Material.AIR) {
                    spawnLoc = plugin.respawnManager.getSafeRandomLocation();
                    player.sendMessage(ChatColor.RED + "오프라인 도중 NPC가 살해당했습니다. 랜덤 지역에서 부활합니다.");
                } else {
                    player.sendMessage(ChatColor.GREEN + "오프라인 도중 NPC가 살해당했습니다. 침대에서 부활합니다.");
                }

                // 위치 이동 및 상태 초기화
                if (spawnLoc != null) {
                    player.teleport(spawnLoc.add(0.5, 1, 0.5));
                }
                player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
                player.setFoodLevel(20);

                // 데이터 정리
                config.set("pending-deaths." + uuidString, null);
                saveDeadConfig();
            }, 1L);
        }
    }
    private Location stringToLocation(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            String[] p = s.split(",");
            if (p.length < 4) return null;
            return new Location(Bukkit.getWorld(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
        } catch (Exception e) {
            return null;
        }
    }
    @EventHandler
    public void onNPCDeath(NPCDeathEvent event) {
        NPC npc = event.getNPC();

        // 리부팅 후에도 주인을 찾을 수 있도록 NPC 데이터에서 직접 UUID를 가져옵니다.
        String ownerUUIDString = (String) npc.data().get("owner-uuid");
        if (ownerUUIDString == null) return;

        UUID ownerUUID = UUID.fromString(ownerUUIDString);
        Location deathLoc = npc.getStoredLocation();

        // 1. 캐시된 인벤토리 아이템 드랍
        ItemStack[] items = inventoryCache.get(ownerUUID);
        if (items != null) {
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    deathLoc.getWorld().dropItemNaturally(deathLoc, item);
                }
            }
            inventoryCache.remove(ownerUUID);
        }

        // 2. [수정] 사망 예약 정보를 파일(dead.yml)에 저장
        // 메모리에만 저장하면 리부팅 시 데이터가 날아갑니다.
        config.set("pending-deaths." + ownerUUIDString, true);
        saveDeadConfig(); // 파일 저장 메서드 호출

        playerNPCs.remove(ownerUUID);
        Bukkit.getLogger().info(npc.getName() + "의 NPC가 처치되어 사망 정보가 파일에 기록되었습니다.");
    }

    // 파일 저장을 위한 헬퍼 메서드 (클래스 안에 추가)
    private void saveDeadConfig() {
        try {
            config.save(new File(plugin.getDataFolder(), "dead.yml"));
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }
    @EventHandler
    public void onNPCDamage(EntityDamageByEntityEvent event) {
        if (CitizensAPI.getNPCRegistry().isNPC(event.getEntity())) {
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(event.getEntity());

            // 1. 저장된 좌표 문자열 가져오기
            String locStr = (String) npc.data().get("fixed-location");
            if (locStr != null) {
                // 2. 넉백(물리적 밀림) 완전 제거
                event.getEntity().setVelocity(new org.bukkit.util.Vector(0, 0, 0));

                // 3. 즉시 원래 좌표로 텔레포트
                String[] parts = locStr.split(",");
                Location fixedLoc = new Location(
                        Bukkit.getWorld(parts[0]),
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]),
                        Float.parseFloat(parts[4]),
                        Float.parseFloat(parts[5])
                );

                // [핵심] 맞은 직후 0틱(즉시) 혹은 1틱 뒤에 원래 자리로 박아버림
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (npc.isSpawned()) {
                        npc.teleport(fixedLoc, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);

                        // 눕기(앉기) 풀림 방지
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc select " + npc.getId());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc panimate SIT");
                    }
                });
            }
        }
        event.setDamage(1.0);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        if (downedLocations.containsKey(uuid)) {
            event.setCancelled(true);
            executeDeath(player);
            return;
        }

        if (player.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            if (processing.contains(uuid)) return;
            processing.add(uuid);
            enterDownedState(player);
        }
    }

    private void enterDownedState(Player player) {
        UUID uuid = player.getUniqueId();
        Location loc = player.getLocation().clone();
        downedLocations.put(uuid, loc);

        int bleedOutTime = config.getInt("dbno-settings.bleed-out-time", 5);
        double selfReviveChance = config.getDouble("dbno-settings.self-revive-chance", 0.1);

        player.setHealth(1.0);
        player.addScoreboardTag("isDowned");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "effect give " + player.getName() + " examplemod:downed infinite 0 false");
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 200, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 200, false, false));
        // 히트박스 소환
        player.getWorld().spawn(loc, org.bukkit.entity.Horse.class, horse -> {
            horse.setInvisible(true);     // 모델 숨기기
            horse.setAI(false);           // 움직임 차단
            horse.setGravity(false);      // 고정
            horse.setSilent(true);        // 소리 제거
            horse.setTamed(true);         // 길들여진 상태로 설정 (상호작용 방해 최소화)
            horse.setInvulnerable(false); // 데미지 허용
            horse.teleport(player.getLocation().add(1, -0.5, 0));
            horse.addScoreboardTag("downed_hitbox");

            // 말이 성장한 상태여야 히트박스가 큽니다.
            horse.setAdult();

            hitboxEntityMap.put(player.getUniqueId(), horse.getUniqueId());
        });
        // 2. 타이머 로직 (사망 대기 시간 동안 작동)
        BukkitRunnable task = new BukkitRunnable() {
            int elapsedSeconds = 0;

            @Override
            public void run() {
                if (!downedLocations.containsKey(uuid) || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                // 부상 상태 유지 (체력 고정)
                player.setHealth(1.0);

                // 최종 사망 시간 도달 시
                if (elapsedSeconds >= bleedOutTime) {
                    // [핵심] 여기서 딱 한 번 확률을 계산합니다.
                    if (Math.random() < selfReviveChance) {
                        revivePlayer(player);
                        player.sendMessage(ChatColor.GOLD + "죽음의 문턱에서 스스로 일어났습니다!");
                    } else {
                        executeDeath(player);
                        player.sendMessage(ChatColor.RED + "과다출혈로 사망했습니다.");
                    }
                    this.cancel();
                    return;
                }

                elapsedSeconds++;
            }
        };
        task.runTaskTimer(plugin, 0L, 20L); // 1초마다 실행
        activeTasks.put(uuid, task);

        player.sendMessage(ChatColor.RED + "부상을 입었습니다! " + bleedOutTime + "초 후에 운명이 결정됩니다.");
    }
    @EventHandler
    public void onHitboxDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Horse horse &&
                horse.getScoreboardTags().contains("downed_hitbox")) {

            event.setCancelled(true); // 말 파괴 방지

            UUID targetUUID = null;
            for (Map.Entry<UUID, UUID> entry : hitboxEntityMap.entrySet()) {
                if (entry.getValue().equals(horse.getUniqueId())) {
                    targetUUID = entry.getKey();
                    break;
                }
            }

            if (targetUUID != null) {
                Player target = Bukkit.getPlayer(targetUUID);
                if (target != null) {
                    executeDeath(target); // 플레이어 즉시 사망
                    horse.remove();       // 히트박스 제거
                }
            }
        }
    }
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (downedLocations.containsKey(event.getPlayer().getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
                Location fixed = from.clone();
                fixed.setYaw(to.getYaw());
                fixed.setPitch(to.getPitch());
                event.setTo(fixed);
            }
        }
    }
    // 1. 히트박스 우클릭 감지 (기존 onRevive는 삭제됨)
    @EventHandler
    public void onHitboxInteract(PlayerInteractEntityEvent event) {
        org.bukkit.entity.Entity clicked = event.getRightClicked();
        Player rescuer = event.getPlayer();

        // 히트박스(말)를 클릭한 경우
        if (clicked instanceof Horse && clicked.getScoreboardTags().contains("downed_hitbox")) {
            event.setCancelled(true);
            UUID targetUUID = getOwnerByHitbox(clicked.getUniqueId());
            if (targetUUID != null) {
                Player target = Bukkit.getPlayer(targetUUID);
                if (target != null && !revivingPlayers.containsKey(rescuer.getUniqueId())) {
                    startReviving(rescuer, target);
                }
            }
        }
        // 다운된 플레이어 본체를 클릭한 경우 (즉시 부활 방지)
        else if (clicked instanceof Player target && downedLocations.containsKey(target.getUniqueId())) {
            event.setCancelled(true);
            if (!revivingPlayers.containsKey(rescuer.getUniqueId())) {
                startReviving(rescuer, target);
            }
        }
    }
    private void revivePlayer(Player player) {
        cleanup(player);
        player.setHealth(6.0); // 부활 시 체력 3칸
    }

    private void executeDeath(Player player) {
        cleanup(player);
        plugin.bedManager.sendBedLocationsToClient(player);
        player.setHealth(0);
    }
    private void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        UUID hitboxId = hitboxEntityMap.remove(uuid);
        downedLocations.remove(uuid);
        processing.remove(uuid);
        player.removeScoreboardTag("isDowned");
        player.setInvulnerable(false);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "effect clear " + player.getName() + " examplemod:downed");
        player.removePotionEffect(PotionEffectType.SLOW);
        player.removePotionEffect(PotionEffectType.JUMP);
        if (activeTasks.containsKey(uuid)) {
            activeTasks.get(uuid).cancel();
            activeTasks.remove(uuid);
        }
        if (hitboxId != null) {
            org.bukkit.entity.Entity hitbox = Bukkit.getEntity(hitboxId);
            if (hitbox != null) {
                hitbox.remove();
            }
        }
    }
    private UUID getOwnerByHitbox(UUID hitboxId) {
        for (Map.Entry<UUID, UUID> entry : hitboxEntityMap.entrySet()) {
            if (entry.getValue().equals(hitboxId)) return entry.getKey();
        }
        return null;
    }
    private void startReviving(Player rescuer, Player target) {
        UUID rescuerUUID = rescuer.getUniqueId();
        revivingPlayers.put(rescuerUUID, target.getUniqueId());
        reviveProgress.put(rescuerUUID, 0);

        int requiredTicks = config.getInt("dbno-settings.revive-hold-time", 3) * 20;

        new BukkitRunnable() {
            @Override
            public void run() {
                // [중요] 구조 시작 직후에는 sneaking이 아닐 수 있으므로 5틱 정도 유예를 주거나,
                // 단순히 '쉬프트 꾹 누르기'로 가이드를 줍니다.
                if (!rescuer.isSneaking() ||
                        rescuer.getLocation().distance(target.getLocation()) > 3.0 ||
                        !downedLocations.containsKey(target.getUniqueId())) {

                    cleanupRevive(rescuer);
                    rescuer.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            new net.md_5.bungee.api.chat.TextComponent(ChatColor.RED + "구조 중단됨 (Shift를 꾹 누르세요)"));
                    this.cancel();
                    return;
                }

                int progress = reviveProgress.get(rescuerUUID) + 1;
                reviveProgress.put(rescuerUUID, progress);

                float percentage = (float) progress / requiredTicks;
                rescuer.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        new net.md_5.bungee.api.chat.TextComponent(ChatColor.YELLOW + "구조 중: " + makeProgressBar(percentage)));

                if (progress >= requiredTicks) {
                    revivePlayer(target);
                    rescuer.sendMessage(ChatColor.GREEN + target.getName() + "님을 구출했습니다!");
                    cleanupRevive(rescuer);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 2L, 1L);
    }
    private void cleanupRevive(Player rescuer) {
        revivingPlayers.remove(rescuer.getUniqueId());
        reviveProgress.remove(rescuer.getUniqueId());
    }

    private String makeProgressBar(float percentage) {
        int totalBars = 20;
        int filledBars = (int) (percentage * totalBars);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) bar.append(ChatColor.GREEN + "■");
            else bar.append(ChatColor.GRAY + "□");
        }
        bar.append(ChatColor.YELLOW + "]");
        return bar.toString();
    }
}