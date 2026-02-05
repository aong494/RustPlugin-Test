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

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            clearHologram(player);
            return;
        }

        // 설치될 기준 위치 (바라보는 블록의 윗칸)
        Location baseLoc = targetBlock.getLocation().add(0, 1, 0);

        // 위치 변화가 없으면 업데이트 스킵
        if (baseLoc.equals(lastBaseLoc.get(player.getUniqueId()))) return;

        clearHologram(player);
        lastBaseLoc.put(player.getUniqueId(), baseLoc);

        String name = material.name().toUpperCase();
        int width = name.contains("BIG_DOOR") ? 4 : 1;
        int height = name.contains("BIG_DOOR") ? 4 : (name.contains("DOOR") ? 2 : 1);

        // 홀로그램이 차지할 모든 좌표 리스트
        List<Location> locs = getStructureLocations(baseLoc, player.getFacing(), width, height);

        // [핵심] 모든 위치를 검사하여 하나라도 블록과 겹치면 빨간색으로 결정
        boolean canBuild = canBuildHere(player, locs, material);
        Material ghostMat = canBuild ? Material.CYAN_STAINED_GLASS : Material.RED_STAINED_GLASS;

        List<BlockDisplay> entities = new ArrayList<>();
        for (Location loc : locs) {
            BlockDisplay display = loc.getWorld().spawn(loc, BlockDisplay.class, (ent) -> {
                ent.setBlock(Bukkit.createBlockData(ghostMat));
                ent.setPersistent(false);
                ent.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (!onlinePlayer.equals(player)) {
                        onlinePlayer.hideEntity(plugin, ent);
                    }
                }
            });

            entities.add(display);
        }
        activeEntities.put(player.getUniqueId(), entities);
    }
    // 설치 가능 여부 판단 메서드
    public boolean canBuildHere(Player player, List<Location> locations, Material material) {
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
    private List<Location> getStructureLocations(Location start, BlockFace facing, int w, int h) {
        List<Location> locs = new ArrayList<>();

        // 플레이어가 바라보는 방향에 대해 '왼쪽'으로 펼쳐질지 '오른쪽'으로 펼쳐질지 결정합니다.
        // 만약 현재 오른쪽으로 펼쳐지는데 모드는 왼쪽이라면, 반대로 설정하세요.
        BlockFace sideStep;
        switch (facing) {
            case NORTH: sideStep = BlockFace.WEST; break;
            case SOUTH: sideStep = BlockFace.EAST; break;
            case EAST:  sideStep = BlockFace.NORTH; break;
            case WEST:  sideStep = BlockFace.SOUTH; break;
            default:    sideStep = BlockFace.EAST;
        }

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                // sideStep 방향으로 x만큼, 위로 y만큼 이동하며 좌표 계산
                locs.add(start.clone().add(sideStep.getModX() * x, y, sideStep.getModZ() * x));
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
        if (hand == null || !hand.getType().isBlock()) return;

        Block target = e.getClickedBlock().getRelative(e.getBlockFace());
        String name = hand.getType().name();
        int width = name.contains("BIG_DOOR") ? 4 : 1;
        int height = name.contains("BIG_DOOR") ? 4 : (name.contains("DOOR") ? 2 : 1);

        List<Location> locs = getStructureLocations(target.getLocation(), player.getFacing(), width, height);

        if (!canBuildHere(player, locs, hand.getType())) {
            e.setCancelled(true);
            player.updateInventory();
        }
    }
}