package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ReinforcedManager implements Listener {
    private final main plugin;
    private File file;
    private FileConfiguration data;

    // 보호 구역 (강화된 심층암)
    private final List<Location> protectedLocations = new ArrayList<>();
    // 평화 구역 (자수정 블록)
    private final List<Location> peaceLocations = new ArrayList<>();

    public ReinforcedManager(main plugin) {
        this.plugin = plugin;
        setupFile();
        loadData();
    }

    private void setupFile() {
        file = new File(plugin.getDataFolder(), "protected_blocks.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    // 파일에서 데이터 불러오기
    private void loadData() {
        // 건축 보호 구역 로드
        if (data.contains("locations")) {
            for (String s : data.getStringList("locations")) {
                Location loc = stringToLoc(s);
                if (loc != null) protectedLocations.add(loc);
            }
        }
        // 자수정 평화 구역 로드
        if (data.contains("peace_locations")) {
            for (String s : data.getStringList("peace_locations")) {
                Location loc = stringToLoc(s);
                if (loc != null) peaceLocations.add(loc);
            }
        }
    }

    private void saveData() {
        List<String> locStrings = new ArrayList<>();
        for (Location loc : protectedLocations) locStrings.add(locToString(loc));
        data.set("locations", locStrings);

        List<String> peaceStrings = new ArrayList<>();
        for (Location loc : peaceLocations) peaceStrings.add(locToString(loc));
        data.set("peace_locations", peaceStrings);

        try { data.save(file); } catch (IOException e) { e.printStackTrace(); }
    }
    private String locToString(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location stringToLoc(String s) {
        String[] parts = s.split(",");
        if (parts.length == 4) {
            return new Location(Bukkit.getWorld(parts[0]),
                    Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
        }
        return null;
    }
    @EventHandler
    public void onProtectBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() == Material.REINFORCED_DEEPSLATE) {
            protectedLocations.add(event.getBlock().getLocation());
            saveData(); // 설치 시 즉시 저장
            event.getPlayer().sendMessage("§a[보호] §f이 지역 반경 30블록이 보호됩니다.");
        }
    }

    @EventHandler
    public void onProtectBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.REINFORCED_DEEPSLATE) {
            if (protectedLocations.contains(event.getBlock().getLocation())) {
                protectedLocations.remove(event.getBlock().getLocation());
                saveData(); // 파괴 시 즉시 업데이트
                event.getPlayer().sendMessage("§c[보호] §f보호 구역이 해제되었습니다.");
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Material type = event.getBlock().getType();
        Location loc = event.getBlock().getLocation();
        Player player = event.getPlayer();

        // 1. 설치하려는 위치가 이미 보호 구역(심층암 30칸) 또는 성역(자수정 200칸)인지 확인
        if (isProtected(loc, player)) {
            event.setCancelled(true);
            player.sendMessage("§c[경고] §f이곳은 블록을 설치할 수 없습니다.");
            return;
        }

        // 2. 보호 블록 설치 로직
        if (type == Material.REINFORCED_DEEPSLATE) {
            protectedLocations.add(loc);
            saveData();
            player.sendMessage("§a[보호] §f이 지역 반경 30블록 내 건축이 보호됩니다.");
        }
        else if (type == Material.AMETHYST_BLOCK) {
            peaceLocations.add(loc);
            saveData();
            player.sendMessage("§d[중립] §f이 지역 반경 200블록 내 PVP 및 건축이 금지됩니다.");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        Location loc = event.getBlock().getLocation();
        Player player = event.getPlayer();

        // 1. 자수정 블록이나 강화된 심층암 자체를 부수려 할 때 처리
        if (type == Material.REINFORCED_DEEPSLATE) {
            // 본인이 설치한 블록이거나 OP여야만 파괴 가능하도록 하려면 추가 조건 필요
            // 현재는 누구나 부수면 리스트에서 삭제되는 구조이므로,
            // 만약 타인의 보호블록 파괴를 막으려면 여기서 isProtected 체크를 먼저 해야 합니다.
            if (isProtected(loc, player)) {
                event.setCancelled(true);
                player.sendMessage("§c[경고] §f파괴할 수 없습니다.");
                return;
            }
            if (protectedLocations.remove(loc)) {
                saveData();
                player.sendMessage("§c[보호] §f보호 구역이 해제되었습니다.");
            }
        }
        else if (type == Material.AMETHYST_BLOCK) {
            if (isProtected(loc, player)) {
                event.setCancelled(true);
                player.sendMessage("§c[경고] §f파괴할 수 없습니다.");
                return;
            }
            if (peaceLocations.remove(loc)) {
                saveData();
                player.sendMessage("§c[중립] §f평화 구역이 해제되었습니다.");
            }
        }
        // 2. 일반 블록을 보호 구역 내에서 부수려 할 때
        else if (isProtected(loc, player)) {
            event.setCancelled(true);
            player.sendMessage("§c[경고] §f이곳의 블록을 파괴할 수 없습니다.");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block == null) return;

            Material type = block.getType();
            if (isInteractable(type)) {
                if (isProtected(block.getLocation(), player)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // 상호작용 차단 대상 목록 (필요한 블록을 더 추가할 수 있습니다)
    private boolean isInteractable(Material material) {
        String name = material.name();
        return name.contains("BUTTON") ||
                name.contains("LEVER") ||
                name.contains("GATE") ||
                name.contains("BARREL") ||
                name.contains("FURNACE") ||
                name.contains("HOPPER") ||
                name.contains("DROPPER") ||
                name.contains("DISPENSER") ||
                name.contains("REPEATER") ||
                name.contains("TRAPDOOR") ||
                name.contains("COMPARATOR");

    }

    // 수정된 보호 확인 로직 (건축 보호 + 자수정 평화 구역 통합)
    private boolean isProtected(Location targetLoc, Player player) {
        if (player.isOp()) return false;

        // 1. 심층암 보호 구역 체크 (반경 30)
        for (Location center : protectedLocations) {
            if (!targetLoc.getWorld().equals(center.getWorld())) continue;
            double dx = Math.abs(targetLoc.getX() - center.getX());
            double dz = Math.abs(targetLoc.getZ() - center.getZ());
            if (dx <= 30 && dz <= 30) return true;
        }

        // 2. 자수정 성역 구역 체크 (반경 200) - 추가된 부분
        for (Location center : peaceLocations) {
            if (!targetLoc.getWorld().equals(center.getWorld())) continue;
            double dx = Math.abs(targetLoc.getX() - center.getX());
            double dz = Math.abs(targetLoc.getZ() - center.getZ());
            if (dx <= 200 && dz <= 200) return true;
        }

        return false;
    }
    // 평화 구역 확인 (X, Z 좌표만 200블록 계산)
    private boolean isInsidePeaceZone(Location loc) {
        for (Location center : peaceLocations) {
            if (!loc.getWorld().equals(center.getWorld())) continue;

            double dx = Math.abs(loc.getX() - center.getX());
            double dz = Math.abs(loc.getZ() - center.getZ());

            if (dx <= 200 && dz <= 200) return true;
        }
        return false;
    }
    // [핵심 추가] PVP 차단 이벤트
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPeaceZoneDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        // 1. 피해자가 성역 안이면 모든 엔티티에 의한 데미지 차단 (총알, 폭발물 등)
        if (isInsidePeaceZone(victim.getLocation())) {
            event.setCancelled(true);
            return;
        }

        // 2. 공격자가 플레이어(또는 투사체를 쏜 플레이어)인 경우
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj) {
            if (proj.getShooter() instanceof Player p) {
                attacker = p;
            }
        }

        // 공격자가 성역 안에서 밖으로 쏘는 행위 차단
        if (attacker != null && isInsidePeaceZone(attacker.getLocation())) {
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        // 에러 났던 부분 수정: BLOCK_EXPLOSION
        if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {

            if (isInsidePeaceZone(victim.getLocation())) {
                event.setCancelled(true);
            }
        }
    }
    @EventHandler
    public void onHeldItem(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;

        if (isInsidePeaceZone(player.getLocation())) {
            ItemStack item = player.getInventory().getItem(event.getNewSlot());
            if (isGun(item)) {
                event.setCancelled(true);
            }
        }
    }

    // 2. 성역 안으로 걸어 들어갈 때 체크 (총을 든 채 진입 방지)
    @EventHandler
    public void onMoveInPeaceZone(PlayerMoveEvent event) {
        // 성능을 위해 블록 위치가 바뀔 때만 실행
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        if (player.isOp()) return;

        if (isInsidePeaceZone(event.getTo())) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (isGun(item)) {
                // 총을 들고 있다면 빈 슬롯(0~8) 중 총이 아닌 곳으로 강제 변경
                for (int i = 0; i < 9; i++) {
                    ItemStack check = player.getInventory().getItem(i);
                    if (!isGun(check)) {
                        player.getInventory().setHeldItemSlot(i);
                        break;
                    }
                }
            }
        }
    }

    // 총기류인지 판별하는 메서드 (모드 총기의 특징적인 키워드를 넣으세요)
    private boolean isGun(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        String name = item.getType().name().toUpperCase();
        String displayName = "";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            displayName = item.getItemMeta().getDisplayName().toUpperCase();
        }
        // 모드 총기들이 주로 사용하는 키워드들
        return name.contains("GUN") ||
                name.contains("RIFLE") ||
                name.contains("PISTOL") ||
                name.contains("SHOTGUN") ||
                name.contains("SNIPER") ||
                name.contains("REVOLVER") ||
                name.contains("SMG") ||
                name.contains("SPEAR") ||
                name.contains("CANNON") ||
                name.contains("LAUNCHER") ||
                name.contains("BOW") ||
                name.contains("MK2") ||
                name.contains("FLAMETHROWER") ||
                name.contains("RAID") ||
                displayName.contains("총");
    }
}