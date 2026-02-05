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
    public final List<Material> protectedBlocks = Arrays.asList(
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
    public final List<Material> placeRestrictedBlocks = Arrays.asList(
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
    private boolean isToolCupboard(Block b) {
        if (b == null) return false;
        String typeName = b.getType().name();

        // [수정] 모드 블록 이름 또는 덫상자 체크
        return typeName.contains("TOOL_CUPBOARD") || b.getType() == Material.TRAPPED_CHEST;
    }

    private boolean checkBlock(Block b) {
        if (b.getType() == Material.TRAPPED_CHEST) return true;
        if (b.getState() instanceof org.bukkit.block.Chest chest) {
            return chest.getCustomName() != null && chest.getCustomName().contains("도구함");
        }
        return false;
    }

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
        String blockKey = plugin.blockToKey(block);
        String plankKey = "planks." + blockKey;
        String doorKey = "doors." + plugin.blockToKey(plugin.getBottom(block));

        // [추가] 모드에서 추가한 거대 문이나 합금 문인지 확인
        boolean isModDoor = blockName.contains("big_door") ||
                blockName.contains("door_dummy") ||
                blockName.contains("armored_door");

        if (plugin.dataStorage.getConfig().contains(plankKey) ||
                plugin.dataStorage.getConfig().contains(doorKey) ||
                isModDoor) { // 모드 문이라면 여기서 중단!
            return;
        }

        // 4. 보호 블록 체크 (파괴 방지)
        Material type = block.getType();
        if (!player.isOp() && protectedBlocks.contains(type)) {
            event.setCancelled(true);
            return;
        }

        String typeName = type.name();

        // 1) 광석 파괴 시 (여기서 걸리고 있었음)
        if (typeName.contains("ORE")) {
            // 이제 위에서 isModDoor로 걸러졌기 때문에 문을 부술 땐 여기까지 내려오지 않습니다.
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
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Material originalType = block.getType();

        if (originalType.name().contains("TOOL_CUPBOARD")) {
            event.setCancelled(true); // 1. 이벤트 취소

            Location bottomLoc = block.getLocation();
            Location topLoc = bottomLoc.clone().add(0, 1, 0);

            // [수정된 체크 로직]
            Block topBlock = topLoc.getBlock();
            Material topMat = topBlock.getType();

            // 윗칸이 공기가 아니고, 풀/눈/물처럼 겹쳐 설치 가능한 블록도 아니라면 설치 중단
            if (topMat != Material.AIR && !isReplaceableMaterial(topMat)) {
                event.getPlayer().sendMessage("§c설치할 공간이 부족합니다. (위쪽이 막혀있음)");
                return;
            }

            // 2. 아래칸: 덫상자 설치 (물리 업데이트 false)
            bottomLoc.getBlock().setType(Material.TRAPPED_CHEST, false);
            if (bottomLoc.getBlock().getState() instanceof org.bukkit.block.Chest chest) {
                chest.setCustomName("도구함");
                chest.update(true, false);
            }

            // 3. 윗칸: 모드 블록 설치
            topBlock.setType(originalType, false);
            org.bukkit.block.data.BlockData data = topBlock.getBlockData();
            if (data instanceof org.bukkit.block.data.Bisected bisected) {
                bisected.setHalf(org.bukkit.block.data.Bisected.Half.TOP);
                topBlock.setBlockData(bisected, false);
            }

            handleProtectorPlace(event, bottomLoc);

            if (event.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
                event.getItemInHand().setAmount(event.getItemInHand().getAmount() - 1);
            }
        }
    }

    // [추가] Material의 대체 가능 여부를 판단하는 커스텀 메서드
    private boolean isReplaceableMaterial(Material material) {
        // 최신 서버라면 material.isReplaceable() 대신 아래와 같이 체크합니다.
        return material == Material.AIR ||
                material == Material.CAVE_AIR ||
                material == Material.VOID_AIR ||
                material == Material.VINE ||
                material == Material.WATER ||
                material == Material.LAVA;
    }
    // 2. 덫상자 전용 처리 메서드 (수정됨)
    private void handleProtectorPlace(BlockPlaceEvent e, Location loc) {
        Player p = e.getPlayer();

        // blockToKey 대신 인자로 받은 loc를 사용하여 키 생성
        String key = plugin.blockToKey(loc.getBlock());

        ConfigurationSection config = plugin.dataStorage.getConfig();

        // 데이터 저장 로직
        config.set("protectors." + key + ".world", loc.getWorld().getName());
        config.set("protectors." + key + ".x", loc.getBlockX());
        config.set("protectors." + key + ".y", loc.getBlockY());
        config.set("protectors." + key + ".z", loc.getBlockZ());
        config.set("protectors." + key + ".owner", p.getUniqueId().toString());

        List<String> auth = new ArrayList<>();
        auth.add(p.getUniqueId().toString());
        config.set("protectors." + key + ".authorized_users", auth);

        plugin.dataStorage.saveConfig();
        p.sendActionBar("§a도구함이 설치되었습니다!");
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
        if (!isToolCupboard(b)) return;

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
        // Material 이름 이용: EXAMPLEMOD_TOOL_CUPBOARD 체크
        if (!b.getType().name().contains("TOOL_CUPBOARD") && b.getType() != Material.TRAPPED_CHEST) return;

        Location loc = b.getLocation();
        ConfigurationSection protectors = plugin.dataStorage.getConfig().getConfigurationSection("protectors");
        if (protectors == null) return;

        for (String key : protectors.getKeys(false)) {
            int x = protectors.getInt(key + ".x");
            int y = protectors.getInt(key + ".y");
            int z = protectors.getInt(key + ".z");

            // 부서진 블록 좌표가 저장된 도구함 좌표와 일치하는지 확인 (y축은 2칸 높이 고려 +-1)
            if (loc.getBlockX() == x && loc.getBlockZ() == z && Math.abs(loc.getBlockY() - y) <= 1) {
                Player p = e.getPlayer();
                // 권한 체크 후 삭제
                plugin.dataStorage.getConfig().set("protectors." + key, null);
                plugin.dataStorage.saveConfig();
                p.sendActionBar("§e도구함 데이터가 정상적으로 삭제되었습니다.");
                return;
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