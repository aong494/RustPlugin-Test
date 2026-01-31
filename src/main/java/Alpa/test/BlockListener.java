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
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Biome;

import java.util.ArrayList;
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

    // --- [새로 추가된 바이옴 체크 유틸리티] ---
    private boolean isOceanBiome(Location loc) {
        // 해당 위치의 바이옴 이름을 가져와 "OCEAN"이 포함되어 있는지 확인
        String biomeName = loc.getBlock().getBiome().name();
        return biomeName.contains("OCEAN");
    }

    // 블록 파괴 처리
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location loc = block.getLocation();
        String blockName = block.getType().getKey().toString();

        // 1. 바다 바이옴 체크 (가장 먼저 실행되어야 함)
        if (!player.isOp() && isOceanBiome(loc)) {
            event.setCancelled(true);
            player.sendActionBar("§c바다에서는 블록을 파괴할 수 없습니다.");
            return;
        }

        // 2. 나무 단계별 로직 처리
        // 중복 호출 방지를 위해 최상단에 있던 handleTreeLogic은 지우고 여기서만 실행합니다.
        if (isConfiguredTree(blockName)) {
            event.setDropItems(false); // 기존 드랍템 방지
            event.setCancelled(true);  // 블록이 즉시 사라지는 것 방지
            plugin.blockRegenManager.handleTreeLogic(block); // 여기서 단계 변환 및 드랍 처리
            return; // 나무 로직이 실행되었다면 여기서 끝냄 (아래 리젠 로직 실행 방지)
        }

        // 3. 기존 플랭크/문 예외 처리
        String plankKey = "planks." + plugin.blockToKey(block);
        String doorKey = "doors." + plugin.blockToKey(plugin.getBottom(block));

        if (plugin.dataStorage.getConfig().contains(plankKey) ||
                plugin.dataStorage.getConfig().contains(doorKey)) {
            return;
        }

        // 4. 보호 블록 체크 (파괴 방지)
        Material type = block.getType();
        if (!player.isOp() && protectedBlocks.contains(type)) {
            event.setCancelled(true);
            return;
        }

        String typeName = type.name();

        // 1) 광석(ORE) 또는 원석 블록(RAW_...) 파괴 시
        if (typeName.contains("ORE")) {
            loc.getWorld().playSound(loc, "minecraft:rust.bonus_hit", org.bukkit.SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
        // 2) 일반 돌(STONE, COBBLESTONE, DEEPSLATE 등) 파괴 시
        else if (type == Material.STONE || type == Material.STONE_BRICKS || type == Material.STONE_BRICK_STAIRS) {
            loc.getWorld().playSound(loc, "minecraft:rust.ore_break", org.bukkit.SoundCategory.BLOCKS, 1.0f, 1.0f);
        }

        // 5. 일반 블록 리젠 로직
        int regenTime = plugin.blockRegenManager.getRegenTime(type);
        if (regenTime > 0) {
            if (isInsideProtectorZone(loc)) {
                return;
            }
            scheduleRegen(loc, type, regenTime);
        }
    }
    private boolean isConfiguredTree(String blockName) {
        ConfigurationSection treeTypes = plugin.blockRegenManager.getConfig().getConfigurationSection("tree-settings.types");
        if (treeTypes == null) return false;

        for (String key : treeTypes.getKeys(false)) {
            if (treeTypes.getString(key + ".log").equalsIgnoreCase(blockName)) return true;
            if (treeTypes.getStringList(key + ".stages").contains(blockName)) return true;
        }
        return false;
    }

    private boolean isInsideProtectorZone(Location loc) {
        // plugin.getConfig() 대신 dataStorage.getConfig() 사용
        ConfigurationSection protectors = plugin.dataStorage.getConfig().getConfigurationSection("protectors");
        if (protectors == null) return false;

        for (String key : protectors.getKeys(false)) {
            String worldName = protectors.getString(key + ".world");
            if (worldName == null || !worldName.equals(loc.getWorld().getName())) continue;

            int x = protectors.getInt(key + ".x");
            int y = protectors.getInt(key + ".y");
            int z = protectors.getInt(key + ".z");

            Location protectorLoc = new Location(loc.getWorld(), x, y, z);
            if (protectorLoc.distance(loc) <= 15) {
                return true;
            }
        }
        return false;
    }

    // 1. 설치 로직 (중복 제거 및 거리 제한 유지)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();

        if (!player.isOp() && isOceanBiome(block.getLocation())) {
            event.setCancelled(true);
            player.sendActionBar("§c바다에서는 블록을 설치할 수 없습니다.");
            return;
        }

        Material placedType = block.getType();

        // 덫상자(보호 구역 도구)인 경우 전용 메서드로 처리
        if (placedType == Material.TRAPPED_CHEST) {
            handleProtectorPlace(event); // 이 안에서 거리 체크와 데이터 저장을 모두 수행합니다.
            return;
        }

        // 일반 블록: 보호 구역 체크
        if (isProtectedLocation(player, block.getLocation())) {
            event.setCancelled(true);
            player.sendActionBar("§c이 지역은 건설차단 구역입니다.");
            return;
        }

        // ... (이후 기존의 OP 체크 및 설치 제한 로직)
        if (player.isOp()) return;
        if (protectedBlocks.contains(placedType) || placeRestrictedBlocks.contains(placedType)) {
            event.setCancelled(true);
        }
    }

    // 2. 덫상자 전용 처리 메서드 (수정됨)
    private void handleProtectorPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Location newLoc = e.getBlock().getLocation();

        // [수정] plugin.getConfig() 대신 dataStorage.getConfig()를 사용하여 데이터 파일을 읽음
        ConfigurationSection protectors = plugin.dataStorage.getConfig().getConfigurationSection("protectors");

        if (protectors != null) {
            for (String key : protectors.getKeys(false)) {
                // 현재 설치하려는 좌표의 키값 생성
                String currentKey = plugin.blockToKey(e.getBlock());
                // 이미 저장된 데이터 중 현재 좌표와 같은 것은 스킵 (재설치 등 오류 방지)
                if (key.equals(currentKey)) continue;

                String worldName = protectors.getString(key + ".world");
                if (worldName == null || !worldName.equals(newLoc.getWorld().getName())) continue;

                int x = protectors.getInt(key + ".x");
                int y = protectors.getInt(key + ".y");
                int z = protectors.getInt(key + ".z");
                Location existingLoc = new Location(newLoc.getWorld(), x, y, z);

                // [거리 체크] 30블록 이내에 이미 다른 도구함이 있는지 확인
                if (existingLoc.distance(newLoc) < 30) {
                    e.setCancelled(true);
                    p.sendActionBar("§c주변 도구함과 너무 가깝습니다! (최소 30블록 거리 필요)");
                    return;
                }
            }
        }

        // --- 데이터 저장 (이후 로직은 동일) ---
        String key = "protectors." + plugin.blockToKey(e.getBlock());
        plugin.dataStorage.getConfig().set(key + ".world", newLoc.getWorld().getName());
        plugin.dataStorage.getConfig().set(key + ".x", newLoc.getBlockX());
        plugin.dataStorage.getConfig().set(key + ".y", newLoc.getBlockY());
        plugin.dataStorage.getConfig().set(key + ".z", newLoc.getBlockZ());
        plugin.dataStorage.getConfig().set(key + ".owner", p.getUniqueId().toString());

        List<String> authUsers = new ArrayList<>();
        authUsers.add(p.getUniqueId().toString());
        plugin.dataStorage.getConfig().set(key + ".authorized_users", authUsers);

        plugin.dataStorage.saveConfig();
        p.sendActionBar("§a건설차단 구역이 설정되었습니다. (반경 15블록)");
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
    // --- [보호 구역 확인 유틸리티] ---
    private boolean isProtectedLocation(Player player, Location loc) {
        if (player.isOp()) return false;

        ConfigurationSection protectors = plugin.dataStorage.getConfig().getConfigurationSection("protectors");
        if (protectors == null) return false;

        for (String key : protectors.getKeys(false)) {
            String worldName = protectors.getString(key + ".world");
            if (worldName == null || !worldName.equals(loc.getWorld().getName())) continue;

            int x = protectors.getInt(key + ".x");
            int y = protectors.getInt(key + ".y");
            int z = protectors.getInt(key + ".z");
            Location protectorLoc = new Location(loc.getWorld(), x, y, z);

            if (protectorLoc.distance(loc) <= 15) {
                // [중요] dataStorage에서 권한 리스트 읽기
                List<String> authUsers = plugin.dataStorage.getConfig().getStringList("protectors." + key + ".authorized_users");
                if (authUsers.contains(player.getUniqueId().toString())) {
                    return false; // 권한 있음
                }
                return true; // 권한 없음 (보호됨)
            }
        }
        return false;
    }

    @EventHandler
    public void onProtectorInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.TRAPPED_CHEST) return;

        String key = "protectors." + plugin.blockToKey(b);
        // dataStorage 확인
        if (!plugin.dataStorage.getConfig().contains(key)) return;

        Player p = e.getPlayer();
        List<String> authUsers = plugin.dataStorage.getConfig().getStringList(key + ".authorized_users");
        String pUUID = p.getUniqueId().toString();

        if (!authUsers.contains(pUUID)) {
            authUsers.add(pUUID);
            plugin.dataStorage.getConfig().set(key + ".authorized_users", authUsers);
            plugin.dataStorage.saveConfig();
            p.sendActionBar("§a이 도구함의 권한을 획득했습니다!");
        }
    }

    // 3. 덫상자 파괴 시 아이템 쏟아지기 (추가됨)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectorBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() != Material.TRAPPED_CHEST) return;

        String key = "protectors." + plugin.blockToKey(b);
        // dataStorage 확인
        if (plugin.dataStorage.getConfig().contains(key)) {
            Player p = e.getPlayer();
            List<String> authUsers = plugin.dataStorage.getConfig().getStringList(key + ".authorized_users");

            if (p.isOp() || authUsers.contains(p.getUniqueId().toString())) {
                // 아이템 드랍 로직
                if (b.getState() instanceof org.bukkit.block.Chest chest) {
                    for (ItemStack item : chest.getInventory().getContents()) {
                        if (item != null && item.getType() != Material.AIR) {
                            b.getWorld().dropItemNaturally(b.getLocation(), item);
                        }
                    }
                    chest.getInventory().clear();
                }

                // [메시지 출력 후 삭제]
                p.sendActionBar("§e도구함 데이터와 보관된 재료가 정리되었습니다.");
                plugin.dataStorage.getConfig().set(key, null);
                plugin.dataStorage.saveConfig();
            } else {
                e.setCancelled(true);
                p.sendActionBar("§c이 도구함을 해제할 권한이 없습니다!");
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        Player p = e.getPlayer();
        Location loc = e.getBlockClicked().getRelative(e.getBlockFace()).getLocation();

        if (isProtectedLocation(p, loc)) {
            e.setCancelled(true);
            p.sendActionBar("§c자신의 건설차단 구역에서만 가능합니다.");
        }
    }
}