package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.*;

public class CraftManager implements Listener {

    private final main plugin;
    private final String MAIN_TITLE = "§0제작 메뉴";
    private final String TOOL_TITLE = "§0제작 - 도구";
    private final String CONSTRUCT_TITLE = "§0제작 - 건축";
    private final String CONSUMABLE_TITLE = "§0제작 - 소모템";
    private final String LV1_TITLE = "§0제작 - 1레벨 작업대";
    private final String LV2_TITLE = "§0제작 - 2레벨 작업대";
    private final String LV3_TITLE = "§0제작 - 3레벨 작업대";
    private final String REG_STEP1_TITLE = "§1[1단계] 아이템 등록 (8번 결과물)";
    private final String REG_STEP2_TITLE = "§1[2단계] 제작 시간 설정";

    // 데이터 보존을 위한 임시 저장소
    private final Map<UUID, ItemStack> pendingResult = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingIngredients = new HashMap<>();
    private final Map<UUID, Integer> pendingTime = new HashMap<>();
    private final Map<UUID, List<CraftingTask>> craftingQueues = new HashMap<>();

    public CraftManager(main plugin) {
        this.plugin = plugin;
        startUpdateTask(); // 제작 진행 스케줄러 시작
    }

    // 제작 대기열 관리 객체
    private static class CraftingTask {
        ItemStack result;
        int remainingTime;
        CraftingTask(ItemStack result, int time) {
            this.result = result;
            this.remainingTime = time;
        }
    }

    // --- GUI 열기 메서드 ---

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, MAIN_TITLE);
        inv.setItem(2, createItem(Material.DIAMOND_PICKAXE, "§e[도구]", "§f도구 제작창을 엽니다."));
        inv.setItem(4, createItem(Material.BRICKS, "§e[건축]", "§f건축 자재 제작창을 엽니다."));
        inv.setItem(6, createItem(Material.COOKED_BEEF, "§e[소모템]", "§f소모품 제작창을 엽니다."));
        player.openInventory(inv);
    }

    public void openLeveledMainMenu(Player player, int level) {
        // 블록 전용임을 구분하기 위해 타이틀에 레벨 정보를 살짝 숨기거나 태그를 달 수 있습니다.
        Inventory inv = Bukkit.createInventory(null, 18, "§0제작 메뉴 (Lv." + level + ")");

        // 첫 번째 줄: 기본 메뉴 (기존과 동일)
        inv.setItem(2, createItem(Material.DIAMOND_PICKAXE, "§e[도구]", "§f도구 제작창을 엽니다."));
        inv.setItem(4, createItem(Material.BRICKS, "§e[건축]", "§f건축 자재 제작창을 엽니다."));
        inv.setItem(6, createItem(Material.COOKED_BEEF, "§e[소모템]", "§f소모품 제작창을 엽니다."));

        // 두 번째 줄: 작업대 레벨별 버튼 (슬롯 9~17)
        if (level >= 1) {
            inv.setItem(11, createItem(Material.IRON_INGOT, "§7[1레벨 작업대]", "§f기초 부품을 제작합니다."));
        }
        if (level >= 2) {
            inv.setItem(13, createItem(Material.GOLD_INGOT, "§6[2레벨 작업대]", "§f중급 장비를 제작합니다."));
        }
        if (level >= 3) {
            inv.setItem(15, createItem(Material.NETHERITE_INGOT, "§b[3레벨 작업대]", "§f최첨단 기술을 사용합니다."));
        }

        player.openInventory(inv);
    }

    public void openCategoryMenu(Player player, String title, String configPath) {
        Inventory inv = Bukkit.createInventory(null, 54, title);
        ConfigurationSection categorySection = plugin.getConfig().getConfigurationSection("recipes." + configPath);

        if (categorySection != null) {
            for (String slotKey : categorySection.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotKey);
                    ConfigurationSection slotSection = categorySection.getConfigurationSection(slotKey);

                    if (slotSection != null && slot < 45) {
                        // 슬롯 하위에 랜덤 ID가 하나라도 있는지 확인
                        Set<String> recipeIds = slotSection.getKeys(false);
                        if (!recipeIds.isEmpty()) {
                            String firstId = recipeIds.iterator().next();
                            ItemStack result = slotSection.getItemStack(firstId + ".result");
                            if (result != null) {
                                inv.setItem(slot, result);
                            }
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        updateQueueDisplay(player, inv);
        player.openInventory(inv);
    }

    public void openRegistrationMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, REG_STEP1_TITLE);
        player.openInventory(inv);
    }

// --- GUI 오픈 메서드 수정 ---

    private void openTimeSettingMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, REG_STEP2_TITLE);
        int currentTime = pendingTime.getOrDefault(player.getUniqueId(), 10);

        inv.setItem(0, createItem(Material.RED_STAINED_GLASS_PANE, "§c-10초", ""));
        inv.setItem(1, createItem(Material.ORANGE_STAINED_GLASS_PANE, "§c-1초", ""));

        // 중앙: 현재 시간 표시
        inv.setItem(4, createItem(Material.CLOCK, "§e현재 설정 시간: §f" + currentTime + "초", ""));

        inv.setItem(7, createItem(Material.LIME_STAINED_GLASS_PANE, "§a+1초", ""));
        inv.setItem(8, createItem(Material.GREEN_STAINED_GLASS_PANE, "§a+10초", ""));

        // [추가] 슬롯 2번에 최종 등록 완료 버튼 생성
        inv.setItem(2, createItem(Material.NETHER_STAR, "§a§l[레시피 등록 완료]", "§7클릭하면 최종적으로 저장됩니다."));
        // [추가] 슬롯 6번에 취소 버튼 생성
        inv.setItem(6, createItem(Material.BARRIER, "§c[등록 취소]", "§7클릭하면 모든 설정이 삭제됩니다."));

        player.openInventory(inv);
    }

    // --- 이벤트 핸들러 ---

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        Player player = (Player) e.getWhoClicked();
        ItemStack clickedItem = e.getCurrentItem();
        int slot = e.getRawSlot();

// 1. 제작 메인 메뉴 (카테고리 선택)
        if (title.startsWith("§0제작 메뉴")) {
            if (slot >= e.getInventory().getSize()) return;
            e.setCancelled(true);

            // [추가] 현재 GUI의 레벨 파악 (타이틀에서 숫자 추출)
            int currentGuiLevel = 0;
            if (title.contains("Lv.")) {
                try {
                    // "§0제작 메뉴 (Lv.1)" 에서 "1"만 추출
                    String lvStr = title.split("Lv.")[1].replace(")", "");
                    currentGuiLevel = Integer.parseInt(lvStr);
                } catch (Exception ignored) {
                    currentGuiLevel = 0;
                }
            }

            // 기본 메뉴 (도구, 건축, 소모템) - 레벨 상관없이 접근 가능
            if (slot == 2) openCategoryMenu(player, TOOL_TITLE, "tools");
            else if (slot == 4) openCategoryMenu(player, CONSTRUCT_TITLE, "construct");
            else if (slot == 6) openCategoryMenu(player, CONSUMABLE_TITLE, "consumables");

                // [수정] 작업대 레벨별 버튼 - 현재 GUI 레벨보다 높은 레벨은 클릭 차단
            else if (slot == 11) {
                if (currentGuiLevel >= 1) openCategoryMenu(player, LV1_TITLE, "lv1");
            }
            else if (slot == 13) {
                if (currentGuiLevel >= 2) openCategoryMenu(player, LV2_TITLE, "lv2");
            }
            else if (slot == 15) {
                if (currentGuiLevel >= 3) openCategoryMenu(player, LV3_TITLE, "lv3");
            }
            return;
        }

        // 2. 통합 제작창 처리 (도구, 건축, 소모템, Lv1~3)
        if (title.contains("§0제작 -")) {
            e.setCancelled(true);
            if (slot >= 45) return; // 대기열 클릭 방지

            String configPath = getPathByTitle(title);

            // [핵심] 관리자 모드: 쉬프트 클릭으로 레시피 관리 (여기가 복구된 부분입니다)
            if (player.isOp() && e.getClick().isShiftClick()) {
                // 쉬프트 + 우클릭: 레시피 등록 창 열기
                if (e.getClick().isRightClick()) {
                    player.setMetadata("reg_path", new org.bukkit.metadata.FixedMetadataValue(plugin, configPath));
                    player.setMetadata("reg_slot", new org.bukkit.metadata.FixedMetadataValue(plugin, slot));

                    openRegistrationMenu(player); // 1단계 등록창(8번 결과물 슬롯 있는 창) 열기
                    player.sendMessage("§a[System] §f" + title + " §a의 " + slot + "번 슬롯에 레시피 등록을 시작합니다.");
                }
                // 쉬프트 + 좌클릭: 레시피 삭제
                else if (e.getClick().isLeftClick() && clickedItem != null) {
                    removeRecipe(clickedItem, configPath);
                    player.sendMessage("§c[System] 레시피가 삭제되었습니다.");
                    openCategoryMenu(player, title, configPath);
                }
                return;
            }

            // [일반 유저] 제작 시작
            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                tryStartCraftingByCategory(player, clickedItem, configPath);
            }
            return;
        }

        // 3. 레시피 등록 1단계 창 (아이템 배치)
        if (title.equals(REG_STEP1_TITLE)) {
            // 아이템을 직접 배치해야 하므로 e.setCancelled(true)를 하지 않습니다.
            return;
        }

        // 4. 레시피 등록 2단계 창 (시간 설정)
        if (title.equals(REG_STEP2_TITLE)) {
            e.setCancelled(true);
            int currentTime = pendingTime.getOrDefault(player.getUniqueId(), 10);

            switch (slot) {
                case 0: currentTime = Math.max(1, currentTime - 10); break;
                case 1: currentTime = Math.max(1, currentTime - 1); break;
                case 7: currentTime += 1; break;
                case 8: currentTime += 10; break;
                case 2: saveFinalRecipe(player); player.closeInventory(); return;
                case 6: clearPendingData(player); player.closeInventory();
                    player.sendMessage("§c[System] 등록 취소."); return;
                default: return;
            }

            pendingTime.put(player.getUniqueId(), currentTime);
            openTimeSettingMenu(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
        }
    }
    // 타이틀을 통해 config 경로를 찾아주는 편의 메서드
    private String getPathByTitle(String title) {
        if (title.equals(TOOL_TITLE)) return "tools";
        if (title.equals(CONSTRUCT_TITLE)) return "construct";
        if (title.equals(CONSUMABLE_TITLE)) return "consumables";
        if (title.equals(LV1_TITLE)) return "lv1";
        if (title.equals(LV2_TITLE)) return "lv2";
        if (title.equals(LV3_TITLE)) return "lv3";
        return "unknown";
    }

    @EventHandler
    public void onBlockInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        org.bukkit.block.Block block = e.getClickedBlock();
        if (block == null) return;

        String typeName = block.getType().name();
        Player player = e.getPlayer();

        // JEG 작업대 레벨 판별
        int level = 0;
        if (typeName.equals("JEG_SCRAP_WORKBENCH")) level = 1;
        else if (typeName.equals("JEG_GUNMETAL_WORKBENCH")) level = 2;
        else if (typeName.equals("JEG_GUNNITE_WORKBENCH")) level = 3;

        if (level > 0) {
            e.setCancelled(true); // JEG 모드 원본 창 무시
            openLeveledMainMenu(player, level);
            player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, 0.5f, 1.5f);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        String title = e.getView().getTitle();
        Player player = (Player) e.getPlayer();

        if (title.equals(REG_STEP1_TITLE)) {
            Inventory inv = e.getInventory();
            ItemStack result = inv.getItem(8);

            if (result == null || result.getType() == Material.AIR) {
                // 버튼 클릭으로 창이 바뀌는 게 아니라 진짜 ESC로 닫았을 때만 데이터 삭제
                if (player.getOpenInventory().getTitle().equals("Container")) clearPendingData(player);
                return;
            }

            List<ItemStack> ingredients = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() != Material.AIR) ingredients.add(item);
            }

            if (ingredients.isEmpty()) return;

            pendingResult.put(player.getUniqueId(), result.clone());
            pendingIngredients.put(player.getUniqueId(), ingredients);
            pendingTime.put(player.getUniqueId(), 10);

            new BukkitRunnable() {
                @Override
                public void run() { openTimeSettingMenu(player); }
            }.runTaskLater(plugin, 1L);
        }
        // REG_STEP2_TITLE에 대한 자동 저장 로직은 제거함 (완료 버튼으로 대체)
    }

    // --- 내부 비즈니스 로직 ---

    private void saveRecipe(ItemStack result, List<ItemStack> ingredients, int time) {
        String key = UUID.randomUUID().toString().substring(0, 8);
        String path = "recipes.tools." + key;
        plugin.getConfig().set(path + ".result", result);
        plugin.getConfig().set(path + ".ingredients", ingredients);
        plugin.getConfig().set(path + ".time", time);
        plugin.saveConfig();
    }

    private void tryStartCraftingByCategory(Player player, ItemStack result, String path) {
        ConfigurationSection categorySection = plugin.getConfig().getConfigurationSection("recipes." + path);
        if (categorySection == null) return;

        List<ItemStack> firstRecipeIngredients = null;

        // 1. 모든 슬롯(0, 1, 2...) 순회
        for (String slotKey : categorySection.getKeys(false)) {
            ConfigurationSection slotSection = categorySection.getConfigurationSection(slotKey);
            if (slotSection == null) continue;

            // 2. [수정] 해당 슬롯 내의 모든 랜덤 ID(UUID) 순회
            for (String recipeId : slotSection.getKeys(false)) {
                // 구조: recipes.tools.0.abcde.result
                ItemStack savedResult = slotSection.getItemStack(recipeId + ".result");

                if (savedResult != null && savedResult.isSimilar(result)) {
                    List<ItemStack> ingredients = (List<ItemStack>) slotSection.getList(recipeId + ".ingredients");
                    int time = slotSection.getInt(recipeId + ".time", 10);

                    if (firstRecipeIngredients == null) firstRecipeIngredients = ingredients;

                    if (hasIngredients(player, ingredients)) {
                        startCraftingProcess(player, result, ingredients, time);
                        return;
                    }
                }
            }
        }

        if (firstRecipeIngredients != null) {
            sendMissingIngredientsMessage(player, firstRecipeIngredients);
        } else {
            player.sendMessage("§c[System] 해당 아이템의 레시피 정보를 찾을 수 없습니다.");
        }
    }

    // [추가] 재료 부족 메시지 전송 전용 메서드
    private void sendMissingIngredientsMessage(Player player, List<ItemStack> ingredients) {
        StringBuilder missing = new StringBuilder("§c[System] 재료가 부족합니다. 필요 재료: ");
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack item = ingredients.get(i);
            String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName()
                    : item.getType().name();

            // 아이템 개수와 함께 조립
            missing.append("§f").append(name).append(" §7(").append(item.getAmount()).append("개)");
            if (i < ingredients.size() - 1) missing.append("§7, ");
        }
        player.sendMessage(missing.toString());
    }

    // [추가] 제작 프로세스 실행 메서드 (중복 제거용)
    private void startCraftingProcess(Player player, ItemStack result, List<ItemStack> ingredients, int time) {
        List<CraftingTask> queue = craftingQueues.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        if (queue.size() >= 9) {
            player.sendMessage("§c[System] 대기열이 가득 찼습니다.");
            return;
        }
        removeIngredients(player, ingredients);
        queue.add(new CraftingTask(result.clone(), time));
        player.sendMessage("§e[System] 제작을 시작합니다.");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.5f);
    }

    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : new HashSet<>(craftingQueues.keySet())) {
                    List<CraftingTask> queue = craftingQueues.get(uuid);
                    if (queue == null || queue.isEmpty()) continue;

                    // 1. 시간 감소 로직
                    CraftingTask current = queue.get(0);
                    current.remainingTime--;

                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) continue;

                    // 2. 제작 완료 처리
                    if (current.remainingTime <= 0) {
                        player.getInventory().addItem(current.result);
                        player.sendMessage("§a[System] 제작 완료: " +
                                (current.result.hasItemMeta() && current.result.getItemMeta().hasDisplayName()
                                        ? current.result.getItemMeta().getDisplayName() : current.result.getType().name()));
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1f);
                        queue.remove(0);
                    }

                    // 3. [핵심] 실시간 인벤토리 갱신
                    // 플레이어가 어떤 제작창이라도 보고 있다면 하단 대기열을 새로고침함
                    String title = player.getOpenInventory().getTitle();
                    if (title.contains("§0제작 -")) {
                        updateQueueDisplay(player, player.getOpenInventory().getTopInventory());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 20틱 = 1초마다 실행
    }

    private void updateQueueDisplay(Player player, Inventory inv) {
        List<CraftingTask> queue = craftingQueues.getOrDefault(player.getUniqueId(), new ArrayList<>());

        for (int i = 0; i < 9; i++) {
            int slot = 45 + i; // 하단 9칸 (45~53)

            if (i < queue.size()) {
                CraftingTask task = queue.get(i);
                ItemStack item = task.result.clone();
                ItemMeta meta = item.getItemMeta();

                if (meta != null) {
                    List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                    // 기존의 시간 표시가 중복되지 않도록 마지막 줄을 관리하거나 새로 추가
                    lore.add("§7---");
                    if (i == 0) {
                        lore.add("§e▶ 제작 중: §f" + task.remainingTime + "초");
                    } else {
                        lore.add("§7대기 중: §f" + task.remainingTime + "초");
                    }
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                inv.setItem(slot, item);
            } else {
                // 빈 대기열 칸
                inv.setItem(slot, createItem(Material.GRAY_STAINED_GLASS_PANE, "§8대기열 비어 있음", ""));
            }
        }
    }

    private void removeRecipe(ItemStack result, String path) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("recipes." + path);
        if (section == null) return;

        for (String slotKey : section.getKeys(false)) {
            ConfigurationSection slotSection = section.getConfigurationSection(slotKey);
            if (slotSection == null) continue;

            for (String recipeId : slotSection.getKeys(false)) {
                ItemStack item = slotSection.getItemStack(recipeId + ".result");
                if (item != null && item.isSimilar(result)) {
                    // 해당 슬롯 통째로 삭제 (또는 특정 레시피만 삭제하려면 recipeId 경로 삭제)
                    plugin.getConfig().set("recipes." + path + "." + slotKey, null);
                    plugin.saveConfig();
                    return;
                }
            }
        }
    }

    private boolean hasIngredients(Player player, List<ItemStack> ingredients) {
        for (ItemStack needed : ingredients) {
            if (!player.getInventory().containsAtLeast(needed, needed.getAmount())) return false;
        }
        return true;
    }

    private void removeIngredients(Player player, List<ItemStack> ingredients) {
        for (ItemStack needed : ingredients) player.getInventory().removeItem(needed);
    }

    private ItemStack createItem(Material m, String name, String lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) {
                List<String> lores = new ArrayList<>();
                lores.add(lore);
                meta.setLore(lores);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
    private void saveFinalRecipe(Player player) {
        UUID uuid = player.getUniqueId();
        if (pendingResult.containsKey(uuid)) {
            ItemStack result = pendingResult.remove(uuid);
            List<ItemStack> ingredients = pendingIngredients.remove(uuid);
            int time = pendingTime.remove(uuid);

            String path = "tools";
            String slotKey = "0"; // 기본값

            if (player.hasMetadata("reg_path")) {
                path = player.getMetadata("reg_path").get(0).asString();
            }

            if (player.hasMetadata("reg_slot")) {
                slotKey = String.valueOf(player.getMetadata("reg_slot").get(0).asInt());
            }

            saveRecipeSpecific(result, ingredients, time, path, slotKey);

            // 메타데이터 삭제 (다음 등록을 위해)
            player.removeMetadata("reg_path", plugin);
            player.removeMetadata("reg_slot", plugin);

            player.sendMessage("§a[System] 지정된 위치에 레시피가 추가되었습니다!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
    }

    private void saveRecipeSpecific(ItemStack result, List<ItemStack> ingredients, int time, String path, String slotKey) {
        // 슬롯번호(slotKey) 하위에 랜덤한 고유 ID를 생성하여 저장
        String recipeId = UUID.randomUUID().toString().substring(0, 5);
        String basePath = "recipes." + path + "." + slotKey + "." + recipeId;

        plugin.getConfig().set(basePath + ".result", result);
        plugin.getConfig().set(basePath + ".ingredients", ingredients);
        plugin.getConfig().set(basePath + ".time", time);
        plugin.saveConfig();
    }

    private void clearPendingData(Player player) {
        UUID uuid = player.getUniqueId();
        pendingResult.remove(uuid);
        pendingIngredients.remove(uuid);
        pendingTime.remove(uuid);
    }

    @EventHandler
    public void onPlayerSwapHand(PlayerSwapHandItemsEvent e) {
        Player player = e.getPlayer();
        e.setCancelled(true);
        openMainMenu(player);
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, 0.5f, 1.5f);
    }
}