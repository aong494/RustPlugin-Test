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
import org.bukkit.event.inventory.InventoryOpenEvent;
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

        // 1. 덫 상자(위)를 클릭했을 때 -> 그 아래가 모드 블록인지 확인
        if (b.getType() == Material.TRAPPED_CHEST) {
            return b.getRelative(0, -1, 0).getType().name().contains("TOOL_CUPBOARD");
        }

        // 2. 모드 블록(아래)을 클릭했을 때 -> 그 위가 덫 상자인지 확인
        if (b.getType().name().contains("TOOL_CUPBOARD")) {
            return b.getRelative(0, 1, 0).getType() == Material.TRAPPED_CHEST;
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

        if (block.getType() == Material.LADDER) {
            return;
        }

        if (block.getType().name().contains("TOOL_CUPBOARD")) {
            event.setCancelled(true);

            Location bottomLoc = block.getLocation();
            Location topLoc = bottomLoc.clone().add(0, 1, 0);

            // 1. 위칸 상자 즉시 설치
            Block topBlock = topLoc.getBlock();
            topBlock.setType(Material.TRAPPED_CHEST, true);
            if (topBlock.getState() instanceof org.bukkit.block.Chest chest) {
                chest.setCustomName("도구함");
                chest.update(true, true);
            }

            // 2. 아래칸 본체 즉시 설치
            Block bottomBlock = bottomLoc.getBlock();
            Material cupboardType = block.getType(); // 타입 저장
            bottomBlock.setType(cupboardType, false);

            // 데이터 설정
            org.bukkit.block.data.BlockData data = bottomBlock.getBlockData();
            if (data instanceof org.bukkit.block.data.Bisected bisected) {
                bisected.setHalf(org.bukkit.block.data.Bisected.Half.BOTTOM);
                if (bisected instanceof org.bukkit.block.data.Directional directional) {
                    directional.setFacing(event.getPlayer().getFacing().getOppositeFace());
                }
                bottomBlock.setBlockData(bisected, false);
            }

            // 3. [핵심] 1틱 뒤에 "한 번 더" 강제로 타입을 박아넣음
            // 엔진이 지웠다면 여기서 다시 살아납니다.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (bottomLoc.getBlock().getType() == Material.AIR) {
                    bottomLoc.getBlock().setType(cupboardType, false);
                    bottomLoc.getBlock().setBlockData(data, false);
                }
                bottomLoc.getBlock().getState().update(true, true);
            }, 1L);

            handleProtectorPlace(event, bottomLoc);
        }
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProtectorInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;

        Player p = e.getPlayer();
        Block bottomPart = null;

        // [수정] 블록 이름에 따른 정확한 본체 위치 계산
        String blockName = b.getType().name();

        if (blockName.contains("TOOL_CUPBOARD")) {
            // 도구함 본체를 직접 클릭한 경우
            bottomPart = b;
        } else if (b.getType() == Material.TRAPPED_CHEST) {
            // 위쪽 상자를 클릭한 경우 -> 아래가 도구함인지 확인
            Block below = b.getRelative(0, -1, 0);
            if (below.getType().name().contains("TOOL_CUPBOARD")) {
                bottomPart = below;
            }
        }

        // 도구함 관련 블록이 아니라면 중단
        if (bottomPart == null) return;

        String key = "protectors." + plugin.blockToKey(bottomPart);

        // [핵심] DB에 데이터가 있는지 확인
        if (!plugin.dataStorage.getConfig().contains(key)) {
            p.sendActionBar("§c[오류] 등록되지 않은 도구함입니다. (Y:" + bottomPart.getY() + ")");
            return;
        }

        // 권한 부여 로직
        List<String> authUsers = plugin.dataStorage.getConfig().getStringList(key + ".authorized_users");
        String pUUID = p.getUniqueId().toString();

        if (!authUsers.contains(pUUID)) {
            authUsers.add(pUUID);
            plugin.dataStorage.getConfig().set(key + ".authorized_users", authUsers);
            plugin.dataStorage.saveConfig();
            p.sendActionBar("§a도구함 권한이 승인되었습니다!");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectorBreak(BlockBreakEvent e) {
        Block b = e.getBlock();

        // [수정] 여기서 메서드를 호출하세요!
        // 이제 복잡한 Material 체크 대신 이 메서드 하나로 해결됩니다.
        if (!isToolCupboard(b)) return;

        Location loc = b.getLocation();
        ConfigurationSection protectors = plugin.dataStorage.getConfig().getConfigurationSection("protectors");
        if (protectors == null) return;

        for (String key : protectors.getKeys(false)) {
            int x = protectors.getInt(key + ".x");
            int y = protectors.getInt(key + ".y");
            int z = protectors.getInt(key + ".z");

            // Y축은 아래 칸(본체) 기준으로 저장되어 있으므로,
            // 클릭한 블록이 위칸(상자)일 경우 y-1을 해서 비교해야 합니다.
            int targetY = (b.getType() == Material.TRAPPED_CHEST) ? loc.getBlockY() - 1 : loc.getBlockY();

            if (loc.getBlockX() == x && loc.getBlockZ() == z && targetY == y) {

                // 파괴 시 반대편 블록 제거 (상자 부수면 아래 삭제 / 아래 부수면 상자 삭제)
                Block otherPart = b.getType() == Material.TRAPPED_CHEST ? b.getRelative(0, -1, 0) : b.getRelative(0, 1, 0);
                if (isToolCupboard(otherPart)) {
                    otherPart.setType(Material.AIR);
                }

                plugin.dataStorage.getConfig().set("protectors." + key, null);
                plugin.dataStorage.saveConfig();
                e.getPlayer().sendActionBar("§e도구함이 파괴되었습니다.");
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
    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (event.getView().getTitle().contains("도구함")) {
            Player player = (Player) event.getPlayer();
            Location loc = event.getInventory().getLocation();
            if (loc == null) return;
            Location cupboardLoc = loc.clone().subtract(0, 1, 0);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.blockDecayManager.syncMaintenanceToPlayer(player, cupboardLoc);
            }, 1L);
        }
    }
    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getView().getTitle().contains("도구함")) {
            Player player = (Player) event.getWhoClicked();

            // 아이템을 옮긴 직후(1틱 뒤)에 다시 계산해서 패킷 전송
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // 현재 열려있는 인벤토리의 위치 가져오기
                Location loc = event.getInventory().getLocation();
                if (loc != null) {
                    Location cupboardLoc = loc.clone().subtract(0, 1, 0);
                    plugin.blockDecayManager.syncMaintenanceToPlayer(player, cupboardLoc);
                }
            }, 1L);
        }
    }
    // 기존 onOpen 메서드 아래에 추가하세요
    @EventHandler
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getView().getTitle().contains("도구함")) {
            Player player = (Player) event.getWhoClicked();

            // 클릭 후 인벤토리가 변할 시간을 주기 위해 1틱 뒤 실행
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location loc = event.getInventory().getLocation();
                if (loc != null) {
                    // 상자 위치의 한 칸 아래가 도구함 본체
                    Location cupboardLoc = loc.clone().subtract(0, 1, 0);
                    plugin.blockDecayManager.syncMaintenanceToPlayer(player, cupboardLoc);
                }
            }, 1L);
        }
    }
    @EventHandler
    public void onCupboardInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;

        // 클릭한 블록이 도구함인지 확인 (모드 블록 이름에 맞춰 수정)
        if (event.getClickedBlock().getType().name().contains("TOOL_CUPBOARD")) {
            Player player = event.getPlayer();
            Location cupLoc = event.getClickedBlock().getLocation();

            // [핵심] 여기서 계산기(BlockDecayManager)를 호출하여 정보를 전송합니다.
            plugin.blockDecayManager.syncMaintenanceToPlayer(player, cupLoc);
        }
    }
}