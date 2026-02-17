package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class BedManager implements Listener, CommandExecutor {

    private final main plugin;
    private final int MAX_BEDS = 4;
    private final long COOLDOWN_MS = 60 * 1000;
    private final double MAX_BED_HEALTH = 10.0;
    private final HashMap<String, Double> bedHealthMap = new HashMap<>();

    public BedManager(main plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (label.equalsIgnoreCase("bedrespawn")) {
            if (args.length == 0) return true;

            // 1. 랜덤 부활 클릭 시
            if (args[0].equalsIgnoreCase("random")) {
                // "random"이라는 고정 문자열을 메타데이터에 저장
                player.setMetadata("selected_respawn_loc", new org.bukkit.metadata.FixedMetadataValue(plugin, "random"));
                return true;
            }

            // 2. 침대 좌표 클릭 시
            if (args.length < 3) return true;
            try {
                // 소수점을 버리고 정확한 블록 좌표(int)로 변환하여 오차 방지
                int x = (int) Double.parseDouble(args[0]);
                int y = (int) Double.parseDouble(args[1]);
                int z = (int) Double.parseDouble(args[2]);
                Location loc = new Location(player.getWorld(), x, y, z);

                player.setMetadata("selected_respawn_loc", new org.bukkit.metadata.FixedMetadataValue(plugin, loc));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }
    public void sendBedLocationsToClient(Player player) {
        List<String> beds = plugin.getConfig().getStringList("player_beds." + player.getUniqueId());
        String activeBed = plugin.getConfig().getString("active_bed." + player.getUniqueId());

        // 형식: "world,x,y,z|world,x,y,z|active:world,x,y,z"
        StringBuilder sb = new StringBuilder();
        for (String loc : beds) {
            if (sb.length() > 0) sb.append("|");
            sb.append(loc);
        }
        sb.append("|active:").append(activeBed != null ? activeBed : "none");

        // "rust:bed_data" 채널로 데이터 전송 (메인 클래스에서 채널 등록 필요)
        player.sendPluginMessage(plugin, "examplemod:bed_data", sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private String getProgressBar(double current, double max) {
        int bars = 10;
        int completedBars = (int) ((current / max) * bars);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            if (i < completedBars) sb.append("§a■");
            else sb.append("§7■");
        }
        return sb.toString();
    }

    // --- 침대 머리/발치 좌표 통합 보조 메서드 ---
    public Location getBedBaseLocation(Block block) {
        if (block == null || !block.getType().name().contains("_BED")) return block != null ? block.getLocation() : null;
        org.bukkit.block.data.type.Bed bedData = (org.bukkit.block.data.type.Bed) block.getBlockData();
        if (bedData.getPart() == org.bukkit.block.data.type.Bed.Part.HEAD) {
            return block.getRelative(bedData.getFacing().getOppositeFace()).getLocation();
        }
        return block.getLocation();
    }



    // --- 에러가 발생했던 인벤토리 클릭 로직 수정 ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        // 1. 아이템이 없거나 빈 칸이면 로직 수행 안함
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

        // 2. [수정 포인트] 침대 관련 GUI일 때만 클릭을 취소하도록 변경
        if (title.equals("§0양도 대상 선택") || title.equals("§0부활 지점 선택")) {
            e.setCancelled(true); // 침대 GUI 내부에서만 아이템 이동 차단
        } else {
            return; // 일반 인벤토리라면 여기서 종료 (아이템 클릭 허용)
        }

        Player p = (Player) e.getWhoClicked();

        // 3. 양도 대상 선택 GUI 로직
        if (title.equals("§0양도 대상 선택")) {
            SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
            if (meta == null) return;

            OfflinePlayer target = meta.getOwningPlayer();
            String bedLoc = "";
            if (meta.hasLore()) {
                for (String line : meta.getLore()) {
                    if (line.startsWith("§hidden:")) bedLoc = line.replace("§hidden:", "");
                }
            }
            transferBed(p, target, bedLoc);
            p.closeInventory();
        }
        // 4. 부활 지점 선택 GUI 로직
        else if (title.equals("§0부활 지점 선택")) {
            int slot = e.getRawSlot();
            List<String> beds = plugin.getConfig().getStringList("player_beds." + p.getUniqueId());
            if (slot < 0 || slot >= beds.size()) return;

            String selectedLoc = beds.get(slot);

            if (e.getClick() == ClickType.RIGHT) {
                Location loc = stringToLocation(selectedLoc);
                if (loc != null) {
                    // 주변 1칸 내의 모든 침대 블록을 아이템 드랍 없이 제거
                    removeConnectedBedBlocks(loc);
                    loc.getWorld().playSound(loc, org.bukkit.Sound.BLOCK_WOOD_BREAK, 1.0f, 0.8f);
                }
                beds.remove(slot);
                plugin.getConfig().set("player_beds." + p.getUniqueId(), beds);
                if (selectedLoc.equals(plugin.getConfig().getString("active_bed." + p.getUniqueId()))) {
                    plugin.getConfig().set("active_bed." + p.getUniqueId(), null);
                }
                plugin.saveConfig();
                syncAllBedsToClient(p);
                p.sendMessage("§e[Bed] 침대 데이터와 블록을 제거했습니다.");
            }
            else if (e.getClick() == ClickType.LEFT) {
                plugin.getConfig().set("active_bed." + p.getUniqueId(), selectedLoc);
                plugin.saveConfig();
                syncAllBedsToClient(p);
                p.sendMessage("§a[Bed] 부활 지점 설정 완료.");
                p.closeInventory();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBedInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) return;

        Block b = e.getClickedBlock();
        if (b == null || !b.getType().name().contains("_BED")) return;

        e.setCancelled(true);

        Player p = e.getPlayer();
        Location baseLoc = getBedBaseLocation(b);
        String locStr = locationToString(baseLoc);
        boolean isOwner = plugin.getConfig().getStringList("player_beds." + p.getUniqueId()).contains(locStr);

        // [핵심 수정] 맵에서 현재 체력을 가져오는 코드가 누락되어 있었습니다.
        double currentHealth = bedHealthMap.getOrDefault(locStr, MAX_BED_HEALTH);

        if (p.getInventory().getItemInMainHand().getType() == Material.STICK) {
            if (p.isSneaking()) {
                if (isOwner) {
                    removeConnectedBedBlocks(baseLoc);
                    removeBedData(locStr);
                    p.sendMessage("§a[Bed] 막대기로 자신의 침대를 철거했습니다.");
                    p.playSound(baseLoc, org.bukkit.Sound.BLOCK_WOOD_BREAK, 1.0f, 1.0f);
                } else {
                    p.sendMessage("§c[Bed] 타인의 침대는 철거할 수 없습니다.");
                }
            } else {
                // [에러 해결] 이제 currentHealth 변수를 정상적으로 사용합니다.
                String progressBar = getProgressBar(currentHealth, MAX_BED_HEALTH);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§e[Bed] 침대 체력: " + progressBar + " §f" + String.format("%.1f", currentHealth) + " / " + MAX_BED_HEALTH));
            }
            return;
        }

        if (p.isSneaking() && isOwner) {
            openGroupMemberGUI(p, locStr);
        } else {
            String ownerName = "알 수 없음";
            ConfigurationSection section = plugin.getConfig().getConfigurationSection("player_beds");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    if (plugin.getConfig().getStringList("player_beds." + key).contains(locStr)) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(key));
                        ownerName = op.getName();
                        break;
                    }
                }
            }
            p.sendMessage("§e[Bed] 주인: §b" + (ownerName != null ? ownerName : "알 수 없음"));
        }
    }

    @EventHandler
    public void onBedPlace(BlockPlaceEvent e) {
        if (!e.getBlock().getType().name().contains("_BED")) return;

        // [추가] 왼손/오른손 중복 호출 방지 (Main Hand일 때만 실행)
        if (e.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) return;

        Player p = e.getPlayer();
        List<String> beds = plugin.getConfig().getStringList("player_beds." + p.getUniqueId());

        if (beds.size() >= MAX_BEDS) {
            e.setCancelled(true);
            p.sendMessage("§c[Bed] 최대 4개까지만 설치 가능합니다.");
            return;
        }

        // [중요] 이미 통합 좌표(발치)를 가져오는 getBedBaseLocation이 있으므로
        // 해당 좌표가 이미 리스트에 있는지 검사합니다.
        Location baseLoc = getBedBaseLocation(e.getBlock());
        String locStr = locationToString(baseLoc);

        if (beds.contains(locStr)) return; // 이미 등록된 좌표라면 중단

        beds.add(locStr);
        plugin.getConfig().set("player_beds." + p.getUniqueId(), beds);

        if (beds.size() == 1) {
            plugin.getConfig().set("active_bed." + p.getUniqueId(), locStr);
        }

        plugin.saveConfig();
        bedHealthMap.put(locStr, MAX_BED_HEALTH);
        syncAllBedsToClient(p);
    }

    @EventHandler
    public void onBedBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!b.getType().name().contains("_BED")) return;

        Location baseLoc = getBedBaseLocation(b);
        String locStr = locationToString(baseLoc);

        double currentHealth = bedHealthMap.getOrDefault(locStr, MAX_BED_HEALTH);
        currentHealth -= 1.0;

        if (currentHealth > 0) {
            bedHealthMap.put(locStr, currentHealth);
            e.setCancelled(true);

            // [수정] 채팅 대신 액션바 표시
            String progressBar = getProgressBar(currentHealth, MAX_BED_HEALTH);
            e.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText("§e[Bed] 남은 체력: " + progressBar + " §f" + String.format("%.1f", currentHealth)));

            e.getPlayer().playSound(baseLoc, org.bukkit.Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.5f, 1.2f);
        } else {
            removeBedData(locStr);
            // 파괴될 때도 액션바 알림
            e.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText("§c[Bed] 침대가 파괴되었습니다!"));
        }
    }
    // 특정 위치의 침대와 연결된 2개의 블록만 정확히 제거하는 메서드
    private void removeConnectedBedBlocks(Location startLoc) {
        Block block = startLoc.getBlock();
        if (!block.getType().name().contains("_BED")) return;

        // 현재 블록의 데이터 확인 (머리인지 발치인지)
        org.bukkit.block.data.type.Bed bedData = (org.bukkit.block.data.type.Bed) block.getBlockData();
        Block otherPart;

        // 반대쪽 파트 위치 계산
        if (bedData.getPart() == org.bukkit.block.data.type.Bed.Part.FOOT) {
            otherPart = block.getRelative(bedData.getFacing());
        } else {
            otherPart = block.getRelative(bedData.getFacing().getOppositeFace());
        }

        // 1. 현재 클릭한 블록 제거
        block.setType(Material.AIR, false);

        // 2. 연결된 반대쪽 블록이 침대라면 제거
        if (otherPart.getType().name().contains("_BED")) {
            otherPart.setType(Material.AIR, false);
        }
    }
    public void applyBedExplodeDamage(Block b, double damage) {
        // 1. 통합 좌표(발치) 가져오기
        Location baseLoc = getBedBaseLocation(b);
        String locStr = locationToString(baseLoc);

        // 2. 체력 가져오기 및 차감
        double currentHealth = bedHealthMap.getOrDefault(locStr, MAX_BED_HEALTH);
        currentHealth -= damage;

        if (currentHealth > 0) {
            bedHealthMap.put(locStr, currentHealth);
        } else {
            // --- [핵심 수정: 아이템 드랍 방지 파괴 로직] ---

            // 주변 1칸 내의 모든 침대 블록(머리, 발치)을 아이템 드랍 없이 제거
            removeConnectedBedBlocks(baseLoc);
            // 데이터베이스에서 침대 정보 삭제
            removeBedData(locStr);
            // 파괴 시 소리 효과
            baseLoc.getWorld().playSound(baseLoc, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
        }
    }

    private void removeBedData(String locStr) {
        bedHealthMap.remove(locStr);
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("player_beds");
        if (section == null) return;
        for (String uuid : section.getKeys(false)) {
            List<String> beds = plugin.getConfig().getStringList("player_beds." + uuid);
            if (beds.remove(locStr)) {
                plugin.getConfig().set("player_beds." + uuid, beds);
                if (locStr.equals(plugin.getConfig().getString("active_bed." + uuid))) {
                    plugin.getConfig().set("active_bed." + uuid, null);
                }
                plugin.saveConfig();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    syncAllBedsToClient(p);
                }
                break;
            }
        }
    }

    public void openGroupMemberGUI(Player p, String bedLoc) {
        String groupName = plugin.groupManager.getPlayerGroup(p);
        if (groupName == null) {
            p.sendMessage("§c[Bed] 소속된 그룹이 없어 양도를 진행할 수 없습니다.");
            return;
        }

        List<String> memberUUIDs = plugin.groupManager.getGroupMembers(groupName);

        // --- [수정 핵심: 인벤토리 생성/오픈 코드 삭제] ---
        // Inventory gui = Bukkit.createInventory(null, 27, "§0양도 대상 선택"); <- 삭제

        // 대신 클라이언트가 알아들을 수 있는 문자열 패킷 생성
        StringBuilder sb = new StringBuilder("OPEN_TRANSFER_GUI|");
        sb.append(bedLoc).append("|");

        boolean hasMembers = false;
        for (String uuidStr : memberUUIDs) {
            if (uuidStr.equals(p.getUniqueId().toString())) continue;

            OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
            String name = (op.getName() != null) ? op.getName() : "알 수 없음";

            sb.append(uuidStr).append(":").append(name).append(",");
            hasMembers = true;
        }

        if (!hasMembers) {
            p.sendMessage("§c[Bed] 양도할 수 있는 그룹 멤버가 없습니다.");
            return;
        }

        // 클라이언트로 패킷 전송 (채널명이 일치하는지 확인하세요)
        p.sendPluginMessage(plugin, "examplemod:bed_data", sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void transferBed(Player giver, OfflinePlayer receiver, String bedLoc) {
        List<String> gBeds = plugin.getConfig().getStringList("player_beds." + giver.getUniqueId());
        List<String> rBeds = plugin.getConfig().getStringList("player_beds." + receiver.getUniqueId());
        if (rBeds.size() >= MAX_BEDS) {
            giver.sendMessage("§c[Bed] 상대방의 침대가 꽉 찼습니다.");
            return;
        }
        if (gBeds.remove(bedLoc)) {
            rBeds.add(bedLoc);
            plugin.getConfig().set("player_beds." + giver.getUniqueId(), gBeds);
            plugin.getConfig().set("player_beds." + receiver.getUniqueId(), rBeds);
            plugin.saveConfig();
            giver.sendMessage("§a[Bed] " + receiver.getName() + "님에게 양도 완료.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Location targetLoc = null;
        boolean forceRandom = false;

        // 1. 유저의 메타데이터 확인 (클릭했는지, 랜덤 눌렀는지)
        if (p.hasMetadata("selected_respawn_loc")) {
            Object metaValue = p.getMetadata("selected_respawn_loc").get(0).value();
            p.removeMetadata("selected_respawn_loc", plugin);

            if (metaValue instanceof String && metaValue.equals("random")) {
                forceRandom = true; // 랜덤 버튼 확정
            } else if (metaValue instanceof Location) {
                // 클릭한 좌표를 즉시 침대 발치(Base) 좌표로 보정
                Block b = findBedBlockNearby((Location) metaValue);
                if (b != null) {
                    targetLoc = getBedBaseLocation(b);
                }
            }
        }

        // 2. [중요] 유저가 랜덤을 선택했다면 무조건 랜덤 로직으로 점프
        if (forceRandom) {
            processRandomRespawn(e, p);
            return;
        }

        // 3. 아무것도 안 눌렀을 때만 활성 침대(Active Bed)를 가져옴
        if (targetLoc == null) {
            String activeBedStr = plugin.getConfig().getString("active_bed." + p.getUniqueId());
            if (activeBedStr != null) {
                targetLoc = stringToLocation(activeBedStr);
            }
        }

        // 4. 침대 부활 처리
        if (targetLoc != null) {
            Block bedBlock = findBedBlockNearby(targetLoc);
            if (bedBlock != null) {
                Location baseLoc = getBedBaseLocation(bedBlock);
                String cooldownKey = locationToString(baseLoc).replace(",", "_");

                long lastUse = plugin.getConfig().getLong("bed_cooldown." + cooldownKey, 0);
                long now = System.currentTimeMillis();

                if (now - lastUse >= COOLDOWN_MS) {
                    e.setRespawnLocation(baseLoc.clone().add(0.5, 1.2, 0.5));
                    plugin.getConfig().set("bed_cooldown." + cooldownKey, now);
                    plugin.saveConfig();
                    p.sendMessage("§a[Bed] 침대에서 부활했습니다.");
                    return; // 침대 부활 성공 시 종료
                } else {
                    long remain = (COOLDOWN_MS - (now - lastUse)) / 1000;
                    p.sendMessage("§c[Bed] 이 침대는 아직 충전 중입니다 (" + remain + "초 남음)");
                }
            }
        }

        // 5. 위 로직에서 return되지 못했다면(침대 없음/쿨타임/랜덤선택) 최종 랜덤 부활
        processRandomRespawn(e, p);
    }

    // 중복 코드를 줄이기 위한 헬퍼 메서드
    private void processRandomRespawn(PlayerRespawnEvent e, Player p) {
        Location randomLoc = plugin.respawnManager.getSafeRandomLocation();
        if (randomLoc != null) {
            e.setRespawnLocation(randomLoc);
            p.sendMessage("§e[System] 무작위 지점에서 부활했습니다.");
        }
    }

    // --- 주변에 침대 블록이 있는지 찾는 보조 메서드 ---
    private Block findBedBlockNearby(Location loc) {
        // 해당 좌표 및 상하좌우 1칸 검사
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block b = loc.clone().add(x, y, z).getBlock();
                    if (b.getType().name().contains("_BED")) {
                        return b;
                    }
                }
            }
        }
        return null;
    }
    public String locationToString(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
    private Location stringToLocation(String s) {
        try {
            String[] p = s.split(",");
            return new Location(Bukkit.getWorld(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
        } catch (Exception e) {
            return null;
        }
    }
    public void syncAllBedsToClient(Player player) {
        if (player == null || !player.isOnline()) return;
        List<String> beds = plugin.getConfig().getStringList("player_beds." + player.getUniqueId());

        StringBuilder sb = new StringBuilder();
        long now = System.currentTimeMillis();

        for (String loc : beds) {
            if (sb.length() > 0) sb.append("|");
            sb.append(loc);
            String cooldownKey = loc.replace(",", "_");
            long lastUse = plugin.getConfig().getLong("bed_cooldown." + cooldownKey, 0);
            if (now - lastUse >= COOLDOWN_MS) {
                sb.append(",READY");
            } else {
                sb.append(",WAIT");
            }
        }
        if (sb.length() == 0) sb.append("none");
        player.sendPluginMessage(plugin, "examplemod:bed_data", sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        // 접속 시 침대 위치 동기화
        new BukkitRunnable() {
            @Override
            public void run() {
                syncAllBedsToClient(e.getPlayer());
            }
        }.runTaskLater(plugin, 20L); // 1초 뒤 안정적으로 전송
    }

    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        // 사망 시 침대 위치 최신화하여 전송 (지도 표시용)
        syncAllBedsToClient(e.getEntity());
    }
    public Map<String, Double> getBedHealthMap() {
        return this.bedHealthMap;
    }
}