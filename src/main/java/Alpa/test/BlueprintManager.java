package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.Event;
import org.bukkit.util.RayTraceResult;

import java.util.*;

public class BlueprintManager implements Listener {
    private final main plugin;
    private final Map<UUID, List<BlockDisplay>> activeEntities = new HashMap<>();
    private final Map<UUID, Location> lastBaseLoc = new HashMap<>();

    public BlueprintManager(main plugin) {
        this.plugin = plugin;
        startHologramTask();
    }

    private void startHologramTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ItemStack handItem = player.getInventory().getItemInMainHand();
                    boolean isLock = handItem != null && handItem.getType() == Material.BLAZE_ROD &&
                            handItem.hasItemMeta() && handItem.getItemMeta().hasCustomModelData() &&
                            handItem.getItemMeta().getCustomModelData() == 1;
                    if (handItem != null && handItem.getType().isBlock() && handItem.getType() != Material.AIR) {
                        updateHologram(player, handItem.getType());
                    } else {
                        clearHologram(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void updateHologram(Player player, Material material) {
        // 1. 플레이어 위치와 시선 방향 가져오기
        Location eyeLoc = player.getEyeLocation();
        org.bukkit.util.Vector direction = eyeLoc.getDirection(); // player.getDirection() 대신 사용

        // 2. 레이트레이스 실행 (최대 5칸)
        RayTraceResult rayTrace = player.getWorld().rayTraceBlocks(eyeLoc, direction, 5);

        // 결과가 없거나 블록에 맞지 않았으면 초기화
        if (rayTrace == null || rayTrace.getHitBlock() == null) {
            clearHologram(player);
            return;
        }

        // 3. 맞은 블록과 면(Face) 정보 가져오기
        Block targetBlock = rayTrace.getHitBlock();
        BlockFace face = rayTrace.getHitBlockFace();

        // 4. 설치될 기준 위치 계산 (바라보는 면의 앞칸)
        Location baseLoc = targetBlock.getRelative(face).getLocation();

        // 중복 계산 방지
        if (baseLoc.equals(lastBaseLoc.get(player.getUniqueId()))) return;
        clearHologram(player);
        lastBaseLoc.put(player.getUniqueId(), baseLoc);

        // --- 이후 로직 (width, height 계산 및 소환) ---
        String name = material.name().toUpperCase();
        int width = name.contains("BIG_DOOR") ? 4 : 1;
        int height = (name.contains("DOOR") || name.contains("CUPBOARD")) ? 2 : 1;
        if (name.contains("BIG_DOOR")) height = 4;

        // 천장 설치 시 보정
        if (face == BlockFace.DOWN && height > 1) {
            baseLoc.add(0, -(height - 1), 0);
        }

        List<Location> locs = getStructureLocations(baseLoc, player.getFacing(), width, height);

        boolean canBuild = canBuildHere(player, locs, material);
        Material ghostMat = canBuild ? Material.CYAN_STAINED_GLASS : Material.RED_STAINED_GLASS;

        // 1. 이번에 생성할 엔티티들을 담을 임시 리스트 생성
        List<BlockDisplay> entities = new ArrayList<>();

        for (Location loc : locs) {
            BlockDisplay display = loc.getWorld().spawn(loc, org.bukkit.entity.BlockDisplay.class, (ent) -> {
                ent.setBlock(Bukkit.createBlockData(ghostMat));
                ent.setPersistent(false);
                ent.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(player)) p.hideEntity(plugin, ent);
                }
            });

            // 2. 생성된 엔티티를 리스트에 추가
            entities.add(display);
        }

        // 3. [핵심] activeEntities 맵에 플레이어 UUID와 함께 저장
        activeEntities.put(player.getUniqueId(), entities);
    }
    // 설치 가능 여부 판단 메서드
    public boolean canBuildHere(Player player, List<Location> locations, Material material) {
        if (player.isOp()) return true;
        for (Location loc : locations) {
            // 1. 블록 겹침 체크: 공기가 아니거나 교체 가능한 블록(풀 등)이 아니면 겹친 것으로 판단
            Block block = loc.getBlock();
            if (!block.getType().isAir() && !block.isReplaceable()) {
                return false; // 다른 블록과 겹치므로 설치 불가(빨간색)
            }

            // 2. 보호 구역 체크 (ReinforcedManager 및 DataStorage 연동)
            if (isProtectedLocation(player, loc)) {
                return false; // 보호 구역이므로 설치 불가(빨간색)
            }

            // 3. 특정 바이옴(바다 등) 또는 제한 블록 체크
            if (block.getBiome().name().contains("OCEAN")) return false;
            if (plugin.blockListener.protectedBlocks.contains(material)) return false;
        }
        return true; // 모든 조건 통과 시 설치 가능(청록색)
    }
    // 2. [추가] 제공해주신 보호 구역 확인 로직
    private boolean isProtectedLocation(Player player, Location loc) {
        if (player.isOp()) return false;
        ConfigurationSection protectors = plugin.dataStorage.getConfig().getConfigurationSection("protectors");
        if (protectors != null) {
            for (String key : protectors.getKeys(false)) {
                String worldName = protectors.getString(key + ".world");
                if (worldName == null || !worldName.equals(loc.getWorld().getName())) continue;

                Location protectorLoc = new Location(loc.getWorld(),
                        protectors.getInt(key + ".x"), protectors.getInt(key + ".y"), protectors.getInt(key + ".z"));

                if (protectorLoc.distance(loc) <= 15) {
                    List<String> authUsers = plugin.dataStorage.getConfig().getStringList("protectors." + key + ".authorized_users");
                    if (!authUsers.contains(player.getUniqueId().toString())) return true;
                }
            }
        }

        // 2. [추가] 심층암 보호 구역 체크 (반경 30)
        for (Location center : plugin.reinforcedManager.getProtectedLocations()) {
            if (!loc.getWorld().equals(center.getWorld())) continue;
            double dx = Math.abs(loc.getX() - center.getX());
            double dz = Math.abs(loc.getZ() - center.getZ());
            if (dx <= 30 && dz <= 30) return true;
        }

        // 3. [추가] 자수정 평화 구역 체크 (반경 200)
        for (Location center : plugin.reinforcedManager.getPeaceLocations()) {
            if (!loc.getWorld().equals(center.getWorld())) continue;
            double dx = Math.abs(loc.getX() - center.getX());
            double dz = Math.abs(loc.getZ() - center.getZ());
            if (dx <= 200 && dz <= 200) return true;
        }

        return false;
    }

    // 방향에 따른 4x4 또는 1x2 좌표 리스트 생성
    private List<Location> getStructureLocations(Location start, BlockFace playerFacing, int w, int h) {
        List<Location> locs = new ArrayList<>();

        // 플레이어의 시선 방향에 따라 '오른쪽'이 어디인지 계산
        BlockFace sideStep;
        switch (playerFacing) {
            case NORTH: sideStep = BlockFace.EAST; break;
            case SOUTH: sideStep = BlockFace.WEST; break;
            case EAST:  sideStep = BlockFace.SOUTH; break;
            case WEST:  sideStep = BlockFace.NORTH; break;
            default:    sideStep = BlockFace.EAST;
        }

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                // baseLoc에서 위로 y칸, 플레이어 기준 오른쪽으로 x칸 확장
                Location l = start.clone().add(0, y, 0);
                l.add(sideStep.getModX() * x, 0, sideStep.getModZ() * x);
                locs.add(l);
            }
        }
        return locs;
    }

    public void clearHologram(Player player) {
        lastBaseLoc.remove(player.getUniqueId());
        List<BlockDisplay> entities = activeEntities.remove(player.getUniqueId());
        if (entities != null) {
            for (BlockDisplay entity : entities) {
                if (entity.isValid()) entity.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlaceAttempt(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = e.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) return;

        // 도구함 판정
        String itemName = hand.toString().toLowerCase();
        boolean isCupboard = itemName.contains("tool_cupboard");

        Block target = e.getClickedBlock().getRelative(e.getBlockFace());

        // [중요] 도구함 설치 시도 시 거리 체크
        if (isCupboard) {
            if (isInsideAnyProtector(target.getLocation())) {
                e.setCancelled(true); // 설치 취소
                player.sendMessage("§c다른 도구함의 범위(15칸)와 겹쳐서 이곳에 설치할 수 없습니다!");
                return;
            }
        }

        // 기존의 문(Door) 너비/높이 및 건축 권한 체크 로직
        String materialName = hand.getType().name();
        int width = materialName.contains("BIG_DOOR") ? 4 : 1;
        int height = 1;
        if (materialName.contains("BIG_DOOR")) height = 4;
        else if (materialName.contains("DOOR") || isCupboard) height = 2;

        List<Location> locs = getStructureLocations(target.getLocation(), player.getFacing(), width, height);

        if (!canBuildHere(player, locs, hand.getType())) {
            e.setCancelled(true);
            player.updateInventory();
        }
    }
    private boolean isInsideAnyProtector(Location loc) {
        ConfigurationSection protectors = plugin.dataStorage.getConfig().getConfigurationSection("protectors");
        if (protectors == null) return false;

        for (String key : protectors.getKeys(false)) {
            String worldName = protectors.getString(key + ".world");
            if (worldName == null || !worldName.equals(loc.getWorld().getName())) continue;

            // getInt 대신 getDouble을 사용하여 더 정확하게 비교합니다.
            double px = protectors.getDouble(key + ".x");
            double py = protectors.getDouble(key + ".y");
            double pz = protectors.getDouble(key + ".z");
            Location protectorLoc = new Location(loc.getWorld(), px, py, pz);

            // 반경 15칸 이내에 도구함 데이터가 있으면 true 반환
            if (protectorLoc.distance(loc) <= 30) {
                return true;
            }
        }
        return false;
    }
}