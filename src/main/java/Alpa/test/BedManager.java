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
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.*;

public class BedManager implements Listener, CommandExecutor {

    private final main plugin;
    private final int MAX_BEDS = 4;
    private final long COOLDOWN_MS = 60 * 1000;
    private final double MAX_BED_HEALTH = 10.0;
    private final HashMap<String, Double> bedHealthMap = new HashMap<>();

    public BedManager(main plugin) {
        this.plugin = plugin;
        startUpdateTask();
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
    private Location getBedBaseLocation(Block block) {
        if (block == null || !block.getType().name().contains("_BED")) return block != null ? block.getLocation() : null;
        org.bukkit.block.data.type.Bed bedData = (org.bukkit.block.data.type.Bed) block.getBlockData();
        if (bedData.getPart() == org.bukkit.block.data.type.Bed.Part.HEAD) {
            return block.getRelative(bedData.getFacing().getOppositeFace()).getLocation();
        }
        return block.getLocation();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        openBedGUI(player);
        return true;
    }

    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getOpenInventory().getTitle().equals("§0부활 지점 선택")) {
                        updateBedItems(player, player.getOpenInventory().getTopInventory());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void openBedGUI(Player p) {
        Inventory gui = Bukkit.createInventory(null, 9, "§0부활 지점 선택");
        updateBedItems(p, gui);
        p.openInventory(gui);
    }

    private void updateBedItems(Player p, Inventory gui) {
        List<String> beds = plugin.getConfig().getStringList("player_beds." + p.getUniqueId());
        String activeBed = plugin.getConfig().getString("active_bed." + p.getUniqueId());
        long now = System.currentTimeMillis();

        for (int i = 0; i < 9; i++) {
            if (i < beds.size()) {
                String locStr = beds.get(i);
                long lastUse = plugin.getConfig().getLong("bed_cooldown." + locStr.replace(",", "_"), 0);
                long remain = (COOLDOWN_MS - (now - lastUse)) / 1000;

                ItemStack item = new ItemStack(remain > 0 ? Material.WHITE_BED : Material.RED_BED);
                ItemMeta meta = item.getItemMeta();
                List<String> lore = new ArrayList<>();

                if (remain > 0) {
                    meta.setDisplayName("§7침대 #" + (i + 1) + " §c(" + remain + "초)");
                    lore.add("§c재충전 중... 부활 불가");
                } else {
                    boolean isActive = locStr.equals(activeBed);
                    meta.setDisplayName((isActive ? "§a§l● " : "§e") + "침대 #" + (i + 1));
                    lore.add(isActive ? "§b▶ 현재 부활 지점" : "§f좌클릭: 부활 지점으로 설정");
                }
                lore.add("§8위치: " + locStr.replace(",", " / "));
                lore.add("§4우클릭: 데이터 삭제 및 블록 제거");
                meta.setLore(lore);
                item.setItemMeta(meta);
                gui.setItem(i, item);
            } else {
                gui.setItem(i, null);
            }
        }
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
                if (loc != null && loc.getBlock().getType().name().contains("_BED")) {
                    loc.getBlock().setType(Material.AIR);
                    loc.getWorld().playSound(loc, org.bukkit.Sound.BLOCK_WOOD_BREAK, 1.0f, 0.8f);
                }
                beds.remove(slot);
                plugin.getConfig().set("player_beds." + p.getUniqueId(), beds);
                if (selectedLoc.equals(plugin.getConfig().getString("active_bed." + p.getUniqueId()))) {
                    plugin.getConfig().set("active_bed." + p.getUniqueId(), null);
                }
                plugin.saveConfig();
                p.sendMessage("§e[Bed] 침대 데이터와 블록을 제거했습니다.");
                updateBedItems(p, e.getInventory());
            }
            else if (e.getClick() == ClickType.LEFT) {
                plugin.getConfig().set("active_bed." + p.getUniqueId(), selectedLoc);
                plugin.saveConfig();
                p.sendMessage("§a[Bed] 부활 지점 설정 완료.");
                p.closeInventory();
            }
        }
    }

    @EventHandler
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
                    baseLoc.getBlock().setType(Material.AIR);
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
        Player p = e.getPlayer();
        List<String> beds = plugin.getConfig().getStringList("player_beds." + p.getUniqueId());
        if (beds.size() >= MAX_BEDS) {
            e.setCancelled(true);
            p.sendMessage("§c[Bed] 최대 4개까지만 설치 가능합니다.");
            return;
        }
        Location baseLoc = getBedBaseLocation(e.getBlock());
        String locStr = locationToString(baseLoc);
        beds.add(locStr);
        plugin.getConfig().set("player_beds." + p.getUniqueId(), beds);
        if (beds.size() == 1) plugin.getConfig().set("active_bed." + p.getUniqueId(), locStr);
        plugin.saveConfig();
        bedHealthMap.put(locStr, MAX_BED_HEALTH);
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
            // 체력 소진 시 파괴
            b.setType(Material.AIR);
            removeBedData(locStr);
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
        Inventory gui = Bukkit.createInventory(null, 27, "§0양도 대상 선택");
        for (String uuidStr : memberUUIDs) {
            if (uuidStr.equals(p.getUniqueId().toString())) continue;
            OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(op);
            meta.setDisplayName("§e" + (op.getName() != null ? op.getName() : "알 수 없음"));
            meta.setLore(Arrays.asList("§7클릭하여 양도", "§hidden:" + bedLoc));
            head.setItemMeta(meta);
            gui.addItem(head);
        }
        p.openInventory(gui);
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

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        String activeBedLoc = plugin.getConfig().getString("active_bed." + p.getUniqueId());
        if (activeBedLoc != null) {
            long lastUse = plugin.getConfig().getLong("bed_cooldown." + activeBedLoc.replace(",", "_"), 0);
            if (System.currentTimeMillis() - lastUse < COOLDOWN_MS) {
                p.sendMessage("§c[Bed] 침대가 쿨타임 중입니다.");
            } else {
                Location loc = stringToLocation(activeBedLoc);
                if (loc != null && loc.getBlock().getType().name().contains("_BED")) {
                    e.setRespawnLocation(loc.add(0.5, 1.0, 0.5));
                    plugin.getConfig().set("bed_cooldown." + activeBedLoc.replace(",", "_"), System.currentTimeMillis());
                    plugin.saveConfig();
                }
            }
        }
    }

    private String locationToString(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location stringToLocation(String s) {
        try {
            String[] p = s.split(",");
            return new Location(Bukkit.getWorld(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
        } catch (Exception e) { return null; }
    }
}