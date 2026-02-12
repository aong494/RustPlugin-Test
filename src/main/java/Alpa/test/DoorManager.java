package Alpa.test;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.block.BlockFace;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DoorManager implements Listener {
    private final Map<UUID, ItemDisplay> previewLocks = new HashMap<>();
    private final main plugin;

    public DoorManager(main plugin) {
        this.plugin = plugin;

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    ItemStack hand = p.getInventory().getItemInMainHand();

                    // 1. 자물쇠 아이템(CMD 1) 확인
                    boolean holdingLock = hand.getType() == Material.BLAZE_ROD &&
                            hand.hasItemMeta() &&
                            hand.getItemMeta().getCustomModelData() == 1;

                    if (!holdingLock) {
                        // 아이템을 안 들고 있으면 홀로그램 제거
                        plugin.hologramManager.removePreview(p);

                        // [중요] 설치된 자물쇠 숨기기 로직 호출 (기존 코드에서 쓰던 방식)
                        updateInstalledLocksVisibility(p);
                        continue;
                    }

                    // 2. [수정] RayTrace를 사용하여 문 표면 좌표를 정확히 가져옴
                    org.bukkit.util.RayTraceResult ray = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 5);
                    Block target = (ray != null) ? ray.getHitBlock() : null;

                    if (target != null && isDoor(target)) {
                        // 3. [연결] HologramManager의 updatePreview 호출
                        // ray.getHitPosition()이 플레이어가 보고 있는 문의 "정확한 표면 좌표"입니다.
                        plugin.hologramManager.updatePreview(p, target, ray.getHitPosition().toLocation(p.getWorld()));
                    } else {
                        // 문이 아닌 곳을 보면 홀로그램 제거
                        plugin.hologramManager.removePreview(p);
                    }

                    // 4. 설치된 자물쇠 숨기기 로직 실행
                    updateInstalledLocksVisibility(p);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDoorInteract(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Block b = e.getClickedBlock();
        if (b == null) return;

        String typeName = b.getType().name().toUpperCase();
        boolean isArmored = typeName.contains("ARMORED_DOOR");
        boolean isIron = b.getType() == Material.IRON_DOOR;
        boolean isNormalDoor = typeName.contains("_DOOR") && !typeName.contains("TRAPDOOR");
        boolean isBigDoor = typeName.contains("BIG_DOOR") || typeName.contains("DOOR_DUMMY");

        if (!isNormalDoor && !isBigDoor) return;

        Player player = e.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        Block master = plugin.getMasterBlock(b);

        // [중요] master가 null일 경우 방어 로직 (아머드 도어 인식 실패 방지)
        if (master == null) master = b;

        String blockKey = plugin.blockToKey(master);
        String locPath = "doors." + blockKey;

        // [1] 좌클릭 타격 처리 (무기/맨손 상관없이 체력 감소)
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            // 우리 플러그인에 등록된 문인지 확인
            if (plugin.dataStorage.getConfig().contains(locPath)) {
                e.setCancelled(true); // 문 열림/부서짐 방지
                plugin.applyDmg(b, 1, player); // 데미지 1 입힘
                return;
            }
        }

        // [1] 자물쇠 설치 로직
        if (item.getType() == Material.BLAZE_ROD && item.hasItemMeta()) {
            if (item.getItemMeta().hasCustomModelData() && item.getItemMeta().getCustomModelData() == 1) {
                e.setCancelled(true);

                if (!plugin.dataStorage.getConfig().contains(locPath + ".owner")) {
                    Location clickLoc = e.getInteractionPoint();
                    if (clickLoc == null) clickLoc = b.getLocation().add(0.5, 0.5, 0.5);

                    plugin.dataStorage.getConfig().set(locPath + ".owner", player.getUniqueId().toString());
                    plugin.dataStorage.saveConfig();

                    // 여기서 player를 추가로 전달 (3개의 인수)
                    spawnLockAtPoint(master, clickLoc, player);
                    player.sendMessage("§a[Lock] 자물쇠가 설치되었습니다.");
                }
                return;
            }
        }

        // [2] 개폐 권한 로직
        if (plugin.dataStorage.getConfig().contains(locPath)) {
            if (!plugin.canAccess(player, blockKey, "doors")) {
                e.setCancelled(true);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§c[Lock] 권한이 없습니다."));
                player.playSound(b.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 1.2f);
            } else {
                // 철문이거나 아머드 도어일 때 수동 개폐 지원 (철문은 원래 우클릭으로 안열리므로)
                if (isIron || isArmored) {
                    e.setCancelled(true);
                    toggleDoor(master);
                }
            }
        }
    }

    private void spawnLockAtPoint(Block master, Location clickLoc, Player player) {
        org.bukkit.block.data.BlockData data = master.getBlockData();
        BlockFace facing = BlockFace.NORTH;

        // 1. 방향 판정
        if (data instanceof org.bukkit.block.data.Directional dir) {
            facing = dir.getFacing();
        } else {
            String dataString = data.getAsString().toLowerCase();
            if (dataString.contains("facing=north")) facing = BlockFace.NORTH;
            else if (dataString.contains("facing=south")) facing = BlockFace.SOUTH;
            else if (dataString.contains("facing=east")) facing = BlockFace.EAST;
            else if (dataString.contains("facing=west")) facing = BlockFace.WEST;
            else facing = getPlayerFacing(player).getOppositeFace();
        }

        String type = master.getType().name().toUpperCase();
        boolean isBigDoor = type.contains("BIG_DOOR") || type.contains("DOOR_DUMMY");
        boolean isArmored = type.contains("ARMORED_DOOR");
        FileConfiguration config = plugin.getLockConfig();

        double centerX = master.getX() + 0.5;
        double centerZ = master.getZ() + 0.5;
        Location fixedLoc = clickLoc.clone();

        double depth;
        double thickness;
        float extraYaw;

        // 2. [lock.yml 연동] 빅 도어 vs 일반 문 분기 처리
        if (isBigDoor) {
            // --- 빅 도어 설정 (BIG_DOOR 섹션 참조) ---
            String base = "BIG_DOOR";
            // 두께 조절: 앞뒤 자물쇠 사이의 간격 (기본값 0.25)
            thickness = config.getDouble(base + ".thickness", 0.25);

            // 방향별 깊이 및 회전값 가져오기
            String dirKey = base + "." + facing.name();
            depth = config.getDouble(dirKey + ".depth", 0.45);
            extraYaw = (float) config.getDouble(dirKey + ".yaw", 0.0);

            // 방향에 따른 좌표 보정 (S, E 방향일 때 모델 위치 반전 대응)
            if (facing == BlockFace.SOUTH || facing == BlockFace.EAST) {
                depth = -depth;
            }

        } else {
            // --- 일반 문 설정 (기존 로직) ---
            String baseKey = isArmored ? "ARMORED_DOOR." + facing.name() : "IRON_DOOR";
            String depthKey = "depth-" + facing.name().toLowerCase();

            depth = config.contains(baseKey + "." + depthKey)
                    ? config.getDouble(baseKey + "." + depthKey)
                    : config.getDouble(baseKey + ".depth", 0.05);

            extraYaw = (float) config.getDouble(baseKey + ".yaw", 0.0);

            thickness = (facing == BlockFace.EAST || facing == BlockFace.WEST)
                    ? config.getDouble("IRON_DOOR.thickness-eastwest", 0.1)
                    : config.getDouble("IRON_DOOR.thickness", 0.1);
        }

        // 블록 축 고정
        if (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) {
            fixedLoc.setZ(centerZ);
        } else {
            fixedLoc.setX(centerX);
        }

        boolean isOpening = data.getAsString().contains("open=true");

        // 3. 자물쇠 소환
        Location frontLoc = fixedLoc.clone();
        applyOffset(frontLoc, facing, -thickness / 2);
        createFinalLock(master, frontLoc, facing, false, isOpening, extraYaw, (float) depth);

        Location backLoc = fixedLoc.clone();
        applyOffset(backLoc, facing, thickness / 2);
        createFinalLock(master, backLoc, facing, true, isOpening, extraYaw, (float) depth);
    }
    private void applyOffset(Location loc, BlockFace facing, double offset) {
        switch (facing) {
            case NORTH -> loc.add(0, 0, offset);
            case SOUTH -> loc.add(0, 0, -offset);
            case EAST -> loc.add(-offset, 0, 0);
            case WEST -> loc.add(offset, 0, 0);
        }
    }

    // 이 메서드가 없어서 에러가 났던 것입니다. 추가해 주세요.
    private BlockFace getPlayerFacing(Player player) {
        float yaw = player.getLocation().getYaw();
        if (yaw < 0) yaw += 360;
        yaw %= 360;
        if (yaw <= 45 || yaw >= 315) return BlockFace.SOUTH;
        if (yaw >= 45 && yaw <= 135) return BlockFace.WEST;
        if (yaw >= 135 && yaw <= 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    private void createFinalLock(Block master, Location loc, BlockFace facing, boolean isBack,
                                 boolean invisible, float extraYaw, float depthOffset) {
        master.getWorld().spawn(loc, ItemDisplay.class, ent -> {
            // 아이템 설정
            ItemStack lockItem = new ItemStack(Material.BLAZE_ROD);
            ItemMeta meta = lockItem.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(1);
                lockItem.setItemMeta(meta);
            }
            ent.setItemStack(lockItem);

            // 1. 기본 방향 설정 (마인크래프트 바닐라 문 기준)
            float yaw = switch (facing) {
                case NORTH -> 180f;
                case SOUTH -> 0f;
                case EAST -> 270f;
                case WEST -> 90f;
                default -> 0f;
            };

            // 2. 최종 각도 계산
            float finalYaw = yaw + extraYaw;
            if (isBack) finalYaw += 180f; // 뒷면은 180도 반전

            Transformation trans = ent.getTransformation();

            // 3. 회전 적용 (Y축 기준)
            float radians = (float) Math.toRadians(finalYaw);
            trans.getLeftRotation().set(new org.joml.AxisAngle4f(radians, 0, 1, 0));

            if (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) {
                // 북남 방향일 때 앞뒤 이동 (세 번째 인자 Z)
                trans.getTranslation().set(0, 0, depthOffset);
            } else {
                // 동서 방향일 때 앞뒤 이동 (첫 번째 인자 X)
                trans.getTranslation().set(depthOffset, 0, 0);
            }

            // 5. 크기 고정 및 적용
            trans.getScale().set(1.0f, 1.0f, 1.0f);
            ent.setTransformation(trans);

            // 기타 설정
            ent.setViewRange(invisible ? 0.0f : 1.0f);
            ent.addScoreboardTag("door_lock");
            ent.addScoreboardTag("lock_owner_" + master.getX() + "_" + master.getY() + "_" + master.getZ());

            // [핵심] UUID 저장 로직
            String blockKey = plugin.blockToKey(master);
            FileConfiguration config = plugin.getLockConfig();
            java.util.List<String> uuids = config.getStringList("locks." + blockKey + ".uuids");
            uuids.add(ent.getUniqueId().toString());
            config.set("locks." + blockKey + ".uuids", uuids);
            plugin.saveLockConfig();
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDoorBreak(org.bukkit.event.block.BlockBreakEvent e) {
        Block b = e.getBlock();
        String typeName = b.getType().name().toUpperCase();

        // 1. 대상 확인
        boolean isBigDoor = typeName.contains("BIG_DOOR") || typeName.contains("DOOR_DUMMY");
        boolean isNormalDoor = typeName.contains("_DOOR") && !typeName.contains("TRAPDOOR");

        if (!isNormalDoor && !isBigDoor) return;

        // 2. 마스터 블록 찾기
        Block master = plugin.getMasterBlock(b);
        if (master == null) master = b;

        String blockKey = plugin.blockToKey(master);

        // 3. [UUID 기반 자물쇠 제거]
        // 기존의 nearbyEntities 검색 대신 lock.yml에 저장된 UUID 리스트를 사용합니다.
        org.bukkit.configuration.file.FileConfiguration lockConfig = plugin.getLockConfig();
        String uuidPath = "locks." + blockKey + ".uuids";

        if (lockConfig.contains(uuidPath)) {
            java.util.List<String> uuids = lockConfig.getStringList(uuidPath);
            for (String uuidStr : uuids) {
                try {
                    java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                    org.bukkit.entity.Entity ent = org.bukkit.Bukkit.getEntity(uuid);
                    if (ent != null) {
                        ent.remove(); // 자물쇠 엔티티 즉시 삭제
                    }
                } catch (Exception ignored) {}
            }
            lockConfig.set("locks." + blockKey, null);
            plugin.saveLockConfig();
        }

        // 4. 권한 및 데미지 로직
        if (plugin.dataStorage.getConfig().contains("doors." + blockKey)) {
            Player p = e.getPlayer();
            Material hand = p.getInventory().getItemInMainHand().getType();

            // 도구가 설정용 막대기라면 취소
            if (hand == Material.STICK || hand == Material.BLAZE_ROD) {
                e.setCancelled(true);
                return;
            }

            e.setCancelled(true);
            String toolName = hand.name();
            int dmg = plugin.getConfig().getInt("damage-settings." + toolName,
                    plugin.getConfig().getInt("damage-settings.DEFAULT", 1));

            plugin.applyDmg(master, dmg, p);
        }
    }
    // --- 나머지 유틸리티 메서드 (기존과 동일) ---
    private void toggleDoor(Block b) {
        Block master = getBottom(b);
        org.bukkit.block.data.BlockData data = master.getBlockData();
        boolean nextState;

        // 1. 표준 Door 인터페이스 지원 여부 확인
        if (data instanceof org.bukkit.block.data.type.Door door) {
            nextState = !door.isOpen();
            door.setOpen(nextState);
            master.setBlockData(door);
        }
        // 2. [아머드 도어 대응] 인터페이스가 없다면 문자열 데이터를 직접 파싱/수정
        else {
            String dataString = data.getAsString();
            // 현재 닫혀있으면(open=false) true로, 열려있으면(open=true) false로 교체
            if (dataString.contains("open=true")) {
                nextState = false;
                master.setBlockData(org.bukkit.Bukkit.createBlockData(dataString.replace("open=true", "open=false")));
            } else {
                nextState = true;
                master.setBlockData(org.bukkit.Bukkit.createBlockData(dataString.replace("open=false", "open=true")));
            }
        }

        // 상단 블록도 동일하게 업데이트 (2칸 높이 문 대응)
        Block top = master.getRelative(BlockFace.UP);
        if (top.getType() == master.getType()) {
            org.bukkit.block.data.BlockData topData = top.getBlockData();
            String topStr = topData.getAsString();
            if (nextState) {
                top.setBlockData(org.bukkit.Bukkit.createBlockData(topStr.replace("open=false", "open=true")));
            } else {
                top.setBlockData(org.bukkit.Bukkit.createBlockData(topStr.replace("open=true", "open=false")));
            }
        }

        // 자물쇠 가시성 제어 및 소리 재생
        handleLockVisibility(master, nextState);
        playDoorSound(master, nextState);
    }
    private void playDoorSound(Block b, boolean open) {
        String typeName = b.getType().name().toUpperCase();
        boolean isArmored = typeName.contains("ARMORED_DOOR");
        boolean isIron = b.getType() == Material.IRON_DOOR;

        Sound sound;

        // 철문이거나 아머드 도어일 때 철문 소리 재생
        if (isIron || isArmored) {
            sound = open ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE;
        } else {
            // 그 외 일반 문은 나무문 소리 재생
            sound = open ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE;
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
    @EventHandler
    public void onStickDrop(org.bukkit.event.player.PlayerDropItemEvent e) {
        // 나무 막대기나 블레이즈 막대는 어떤 상황에서도 버려질 수 있도록 강제 허용
        Material type = e.getItemDrop().getItemStack().getType();
        if (type == Material.STICK || type == Material.BLAZE_ROD) {
            e.setCancelled(false); // 취소를 해제함
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDoorRedstone(org.bukkit.event.block.BlockRedstoneEvent e) {
        Block b = e.getBlock();
        String typeName = b.getType().name().toUpperCase();

        if (typeName.contains("_DOOR") || typeName.contains("BIG_DOOR") || typeName.contains("DUMMY")) {
            Block master = plugin.getMasterBlock(b);

            // 레드스톤 신호가 0보다 크면 문이 열리는 것으로 간주
            boolean isOpening = e.getNewCurrent() > 0;

            // 문 애니메이션 시간을 고려하여 아주 짧은 딜레이 후 가시성 업데이트
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                handleLockVisibility(master, isOpening);
            }, 1L);
        }
    }
    private void handleLockVisibility(Block master, boolean isDoorOpen) {
        // 마스터 블록 좌표 기반 태그 생성
        String lockTag = "lock_owner_" + master.getX() + "_" + master.getY() + "_" + master.getZ();

        master.getWorld().getEntitiesByClass(ItemDisplay.class).stream()
                .filter(ent -> ent.getScoreboardTags().contains("door_lock"))
                .filter(ent -> ent.getScoreboardTags().contains(lockTag))
                .forEach(ent -> {
                    // 문이 열려있으면(isDoorOpen=true) 안보이게(0.0f), 닫혀있으면 보이게(1.0f)
                    float range = isDoorOpen ? 0.0f : 1.0f;
                    ent.setViewRange(range);
                });
    }
    private boolean isDoor(Block b) {
        String type = b.getType().name().toUpperCase();
        return type.contains("_DOOR") || type.contains("BIG_DOOR") || type.contains("DUMMY");
    }
    private void updateInstalledLocksVisibility(Player p) {
        p.getNearbyEntities(15, 15, 15).stream()
                .filter(ent -> ent instanceof ItemDisplay && ent.getScoreboardTags().contains("door_lock"))
                .forEach(ent -> {
                    ItemDisplay lock = (ItemDisplay) ent;

                    String ownerTag = ent.getScoreboardTags().stream()
                            .filter(tag -> tag.startsWith("lock_owner_"))
                            .findFirst().orElse(null);

                    if (ownerTag != null) {
                        try {
                            String[] parts = ownerTag.replace("lock_owner_", "").split("_");
                            int x = Integer.parseInt(parts[0]);
                            int y = Integer.parseInt(parts[1]);
                            int z = Integer.parseInt(parts[2]);

                            Block masterBlock = ent.getWorld().getBlockAt(x, y, z);
                            String dataStr = masterBlock.getBlockData().getAsString();
                            boolean isDoorOpen = dataStr.contains("open=true");

                            if (isDoorOpen) {
                                // 문이 열리면 즉시 숨김
                                if (lock.getViewRange() != 0.0f) {
                                    lock.setViewRange(0.0f);
                                }
                            } else {
                                // 문이 닫혔을 때 (현재 숨겨진 상태인 경우에만 로직 실행)
                                if (lock.getViewRange() == 0.0f) {
                                    String typeName = masterBlock.getType().name().toUpperCase();

                                    if (typeName.contains("BIG_DOOR")) {
                                        // [Big Door 전용] 2초 딜레이 후 등장
                                        lock.setViewRange(0.01f); // 중복 스케줄러 생성 방지용 임시값
                                        new org.bukkit.scheduler.BukkitRunnable() {
                                            @Override
                                            public void run() {
                                                // 2초 뒤에 문이 여전히 닫혀있다면 보이게 함
                                                if (masterBlock.getBlockData().getAsString().contains("open=false")) {
                                                    lock.setViewRange(1.0f);
                                                }
                                            }
                                        }.runTaskLater(plugin, 40L); // 40L = 2초
                                    } else {
                                        // [일반 문] 즉시 등장
                                        lock.setViewRange(1.0f);
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                });
    }
}