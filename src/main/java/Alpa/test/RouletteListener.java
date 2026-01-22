package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class RouletteListener implements Listener {
    private final main plugin;
    private final RouletteManager manager;
    private final int[] MULTIPLIERS = {2, 3, 5, 8, 15};

    public RouletteListener(main plugin, RouletteManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onArcadeClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        String materialName = block.getType().name().toUpperCase();

    List<String> allowedBlocks = Arrays.asList(
        "ARCADEGAME_2"
    );

    if (allowedBlocks.stream().anyMatch(materialName::contains)) {
        event.setCancelled(true);
        openRouletteGUI(event.getPlayer());
    }
    }

    public void openRouletteGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, "§8[룰렛 배팅]");

        int[] cols = {2, 3, 4, 5, 6};
        Material[] wools = {Material.YELLOW_WOOL, Material.LIME_WOOL, Material.BLUE_WOOL, Material.PURPLE_WOOL, Material.RED_WOOL};

        for (int i = 0; i < 5; i++) {
            int col = cols[i];
            int mult = MULTIPLIERS[i]; // 고정된 배율 상수 사용

            // 현재 플레이어가 이 배율에 배팅한 총 금액 확인
            int currentBet = manager.getPlayerBetAmount(player.getUniqueId(), mult);

            gui.setItem(col, createItem(Material.BLUE_STAINED_GLASS_PANE, "§9+10개 추가", "§7클릭 시 스크랩 10개 배팅"));
            gui.setItem(col + 9, createItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§b+1개 추가", "§7클릭 시 스크랩 1개 배팅"));

            // 3줄: 배율 양털 (현재 배팅액 표시)
            gui.setItem(col + 18, createItem(wools[i], "§l" + mult + "배 배팅", "§f현재 배팅액: §e" + currentBet + " 스크랩"));

            gui.setItem(col + 27, createItem(Material.ORANGE_STAINED_GLASS_PANE, "§6-1개 빼기", "§7클릭 시 배팅액 1개 취소"));
            gui.setItem(col + 36, createItem(Material.RED_STAINED_GLASS_PANE, "§c-10개 빼기", "§7클릭 시 배팅액 10개 취소"));
        }

        player.openInventory(gui);
        updateCountdownIcon(player);
    }
    // 실시간 시계 업데이트 메서드
    public void updateCountdownIcon(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        if (inv == null || !player.getOpenInventory().getTitle().equals("§8[룰렛 배팅]")) return;

        int timeLeft = manager.getTimer();
        boolean active = manager.isCountingDown();

        ItemStack clock = new ItemStack(Material.CLOCK);
        ItemMeta meta = clock.getItemMeta();

        if (active) {
            meta.setDisplayName("§e§l시작까지: §f" + timeLeft + "초");
            meta.setLore(Arrays.asList("§7카운트다운이 진행 중입니다."));
        } else {
            meta.setDisplayName("§b§l대기 중");
            meta.setLore(Arrays.asList("§7배팅을 하면", "§710초 카운트다운이 시작됩니다."));
        }

        clock.setItemMeta(meta);
        inv.setItem(44, clock); // 45칸 중 마지막 슬롯
    }
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals("§8[룰렛 배팅]")) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 45) return;

        int col = slot % 9;
        int row = slot / 9;

        // 배율 칸(2~6열)이 아니면 무시
        if (col < 2 || col > 6) return;

        // 저장할 때도 고정된 배율 상수 사용 (col-2 인덱스 활용)
        int multiplier = MULTIPLIERS[col - 2];

        if (row == 0) { // +10
            handleBet(player, multiplier, 10, true);
        } else if (row == 1) { // +1
            handleBet(player, multiplier, 1, true);
        } else if (row == 3) { // -1
            handleBet(player, multiplier, 1, false);
        } else if (row == 4) { // -10
            handleBet(player, multiplier, 10, false);
        }
    }

    private void handleBet(Player player, int multiplier, int amount, boolean isAdd) {
        if (isAdd) {
            if (takeScrap(player, amount)) {
                manager.addBet(player, multiplier, amount);
                player.sendMessage("§e" + multiplier + "배에 " + amount + "개를 추가 배팅했습니다!");
                openRouletteGUI(player); // GUI 갱신 (배팅액 표시 업데이트)
            } else {
                player.sendMessage("§c스크랩이 부족합니다.");
            }
        } else {
            // 빼기 로직
            int currentBet = manager.getPlayerBetAmount(player.getUniqueId(), multiplier);
            int toRemove = Math.min(currentBet, amount);

            if (toRemove > 0) {
                manager.removeBet(player, multiplier, toRemove);
                giveScrap(player, toRemove);
                player.sendMessage("§6" + multiplier + "배에서 " + toRemove + "개를 뺐습니다.");
                openRouletteGUI(player); // GUI 갱신
            } else {
                player.sendMessage("§c배팅한 금액이 없습니다.");
            }
        }
    }

    // --- 유틸리티 메서드 (스크랩 처리) ---
    private boolean takeScrap(Player player, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType().name().equals("JEG_SCRAP")) count += item.getAmount();
        }
        if (count < amount) return false;

        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType().name().equals("JEG_SCRAP")) {
                if (item.getAmount() > remaining) {
                    item.setAmount(item.getAmount() - remaining);
                    break;
                } else {
                    remaining -= item.getAmount();
                    contents[i] = null;
                }
            }
            if (remaining <= 0) break;
        }
        player.getInventory().setContents(contents);
        return true;
    }

    private void giveScrap(Player player, int amount) {
        try {
            ItemStack scrap = new ItemStack(Material.valueOf("JEG_SCRAP"), amount);
            player.getInventory().addItem(scrap).values().forEach(remain ->
                    player.getWorld().dropItemNaturally(player.getLocation(), remain));
        } catch (Exception e) {
            player.sendMessage("§c아이템 지급 중 오류가 발생했습니다.");
        }
    }

    private ItemStack createItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}