package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Arrays;
import java.util.List;

public class BlockListener implements Listener {

    private final main plugin;

    public BlockListener(main plugin) {
        this.plugin = plugin;
    }

    // 1. [보호] OP가 아닐 때 파괴/설치 모두 불가능한 블록
    private final List<Material> protectedBlocks = Arrays.asList(
            Material.DIRT,
            Material.GRASS_BLOCK,
            Material.SAND,
            Material.SNOW_BLOCK,
            Material.CYAN_TERRACOTTA,
            Material.OAK_SAPLING,
            Material.SPRUCE_SAPLING,
            Material.BIRCH_SAPLING,
            Material.JUNGLE_SAPLING,
            Material.ACACIA_SAPLING,
            Material.DARK_OAK_SAPLING,
            Material.MANGROVE_PROPAGULE,
            Material.GRANITE,
            Material.DIORITE,
            Material.ANDESITE,
            Material.GRAVEL,
            Material.DEEPSLATE,
            Material.CHERRY_SAPLING
    );

    // 2. [설치 제한] OP가 아닐 때 '설치만' 불가능한 블록 (파괴는 가능)
    private final List<Material> placeRestrictedBlocks = Arrays.asList(
            Material.STONE,
            Material.COAL_ORE,
            Material.IRON_ORE,
            Material.COPPER_ORE,
            Material.GOLD_ORE,
            Material.REDSTONE_ORE,
            Material.EMERALD_ORE,
            Material.LAPIS_ORE,
            Material.DIAMOND_ORE,
            Material.ANCIENT_DEBRIS,
            Material.OAK_LOG,
            Material.SPRUCE_LOG,
            Material.BIRCH_LOG,
            Material.JUNGLE_LOG,
            Material.ACACIA_LOG,
            Material.DARK_OAK_LOG,
            Material.CHERRY_LOG,
            Material.MANGROVE_LOG,
            Material.CRIMSON_STEM,
            Material.WARPED_STEM,
            Material.CLAY
    );

    // 블록 파괴 처리
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();

        // 보호 블록 체크 (파괴 방지)
        if (!player.isOp() && protectedBlocks.contains(type)) {
            event.setCancelled(true);
            return;
        }

        // 블록 리젠 체크
        int regenTime = plugin.blockRegenManager.getRegenTime(type);
        if (regenTime > 0) {
            // ⭐ 핵심: 현재 위치가 덫상자 보호 구역 안인지 확인
            if (isInsideProtectorZone(block.getLocation())) {
                // 보호 구역 안이면 리젠을 예약하지 않음 (영구 파괴)
                return;
            }

            scheduleRegen(block.getLocation(), type, regenTime);
        }
    }

    private boolean isInsideProtectorZone(Location loc) {
        ConfigurationSection protectors = plugin.getConfig().getConfigurationSection("protectors");
        if (protectors == null) return false;

        for (String key : protectors.getKeys(false)) {
            String worldName = protectors.getString(key + ".world");
            if (worldName == null || !worldName.equals(loc.getWorld().getName())) continue;

            int x = protectors.getInt(key + ".x");
            int y = protectors.getInt(key + ".y");
            int z = protectors.getInt(key + ".z");

            Location protectorLoc = new Location(loc.getWorld(), x, y, z);

            // 덫상자와의 거리가 15블록 이하이면 리젠 금지 구역
            if (protectorLoc.distance(loc) <= 15) {
                return true;
            }
        }
        return false;
    }

    // 블록 설치 처리
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return; // OP는 통과

        Block block = event.getBlockPlaced();
        Material placedType = block.getType();
        String typeName = placedType.name(); // 블록의 이름을 문자열로 가져옴

        // 1. 보호 블록 설치 방지
        if (!player.isOp() && protectedBlocks.contains(placedType)) {
            event.setCancelled(true);
            return;
        }

        // 2. 설치 제한 블록 방지 (새로 추가된 기능)
        if (!player.isOp() && placeRestrictedBlocks.contains(placedType)) {
            event.setCancelled(true);
            return;
        }
        // 3. 모드 블록 체크 (문자열 비교)
        if (typeName.equalsIgnoreCase("JEG_BRIMSTONE_ORE") || typeName.contains("BRIMSTONE_ORE")) {
            event.setCancelled(true);
        }
    }

    // scheduleRegen 메서드만 이 내용으로 교체하세요
    private void scheduleRegen(Location loc, Material originalType, int seconds) {
        // 1. 서버 재부팅을 대비해 '현재 시간 + 대기 시간'을 밀리초로 저장
        long respawnAt = System.currentTimeMillis() + (seconds * 1000L);
        plugin.blockRegenManager.addPendingRegen(loc, originalType, respawnAt);

        // 2. 현재 실행 중인 서버용 스케줄러
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            processRegen(loc, originalType);
        }, seconds * 20L);
    }

    // 공통 리젠 로직 분리
    public void processRegen(Location loc, Material originalType) {
        if (isInsideProtectorZone(loc)) {
            plugin.blockRegenManager.removePendingRegen(loc);
            return;
        }

        Block block = loc.getBlock();
        if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR) {
            block.setType(originalType);
        }
        // 3. 리젠이 끝났으니 파일에서 제거
        plugin.blockRegenManager.removePendingRegen(loc);
    }
    @EventHandler
    public void onItemSpawn(org.bukkit.event.entity.ItemSpawnEvent event) {
        Material itemType = event.getEntity().getItemStack().getType();

        // 드랍되는 아이템이 묘목(SAPLING)인지 확인
        if (itemType.name().contains("SAPLING")) {
            // 주변에 나뭇잎이 있는지 확인하거나, 그냥 모든 묘목 드랍을 막음
            event.setCancelled(true);
        }
    }
}