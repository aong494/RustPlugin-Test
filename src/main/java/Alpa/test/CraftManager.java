package Alpa.test;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.block.data.type.Furnace;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.*;

public class CraftManager implements Listener {

    private final main plugin;

    // --- 카테고리 식별자 ---
    private final String CAT_TOOLS = "tools";
    private final String CAT_CONSTRUCT = "construct";
    private final String CAT_CONSUMABLES = "consumables";
    private final String CAT_LV1 = "lv1";
    private final String CAT_LV2 = "lv2";
    private final String CAT_LV3 = "lv3";

    // --- GUI 제목 (하나로 통합) ---
    private final String GUI_TITLE_PREFIX = "§0제작 시스템";
    private final String REG_STEP1_TITLE = "§1[1단계] 아이템 등록 (8번 결과물)";
    private final String REG_STEP2_TITLE = "§1[2단계] 제작 시간 설정";

    // --- 플레이어별 상태 저장소 ---
    private final Map<UUID, String> playerCurrentCategory = new HashMap<>(); // 현재 보고 있는 탭
    private final Map<UUID, Integer> playerCraftAmount = new HashMap<>();    // 현재 설정한 수량
    private final Map<UUID, ItemStack> playerSelectedRecipe = new HashMap<>(); // 현재 선택한 레시피 결과물
    private final Map<UUID, String> playerSelectedPath = new HashMap<>();      // 선택한 레시피의 Config 경로 (제작용)

    // --- 기존 데이터 저장소 ---
    private final Map<UUID, ItemStack> pendingResult = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingIngredients = new HashMap<>();
    private final Map<UUID, Integer> pendingTime = new HashMap<>();
    private final Map<UUID, List<CraftingTask>> craftingQueues = new HashMap<>();

    public CraftManager(main plugin) {
        this.plugin = plugin;
        startUpdateTask();
        startFurnaceParticleTask();
    }

    // 제작 대기열 객체
    private static class CraftingTask {
        ItemStack result;
        int remainingTime;
        CraftingTask(ItemStack result, int time) {
            this.result = result;
            this.remainingTime = time;
        }
    }

    // [추가] 아이템의 한글 이름을 판별하는 메서드
    private String getItemNameKorean(ItemStack item) {
        if (item == null) return "공기";

        // 1. 아이템 메타에 수동으로 설정된 한글 이름이 있다면 최우선 반환
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }

        // 2. 주요 아이템 한글 매핑 (필요한 것을 더 추가하세요)
        Material type = item.getType();
        return switch (type) {
            case IRON_INGOT -> "철 주괴";
            case GOLD_INGOT -> "금 주괴";
            case DIAMOND -> "다이아몬드";
            case EMERALD -> "에메랄드";
            case NETHERITE_INGOT -> "네더라이트 주괴";
            case STICK -> "막대기";
            case COAL -> "석탄";
            case COPPER_INGOT -> "구리 주괴";
            case OAK_LOG -> "참나무 원목";
            case OAK_PLANKS -> "참나무 판자";
            case COBBLESTONE -> "조약돌";
            case STONE -> "돌";
            case IRON_ORE -> "철 광석";
            case GOLD_ORE -> "금 광석";
            case GLASS -> "유리";
            case BREAD -> "빵";
            case COOKED_BEEF -> "스테이크";
            // 매핑되지 않은 것은 대문자를 소문자로 바꾸고 언더바를 띄어쓰기로 변환 (예: LEATHER_BOOTS -> leather boots)
            default -> type.name().toLowerCase().replace("_", " ");
        };
    }

    // --- [1] GUI 열기 및 네비게이션 ---

    // 명령어(/제작) 진입점 수정
    public void openDefaultCrafting(Player player) {
        UUID uuid = player.getUniqueId();
        playerCurrentCategory.put(uuid, CAT_TOOLS);
        playerCraftAmount.put(uuid, 1); // 기본값 1
        playerSelectedRecipe.remove(uuid);
        syncAmountToMod(player, 1);
        openCraftingGUI(player);
    }

    // GUI 렌더링 로직 수정
    private void openCraftingGUI(Player player) {
        UUID uuid = player.getUniqueId();
        String currentCategory = playerCurrentCategory.getOrDefault(uuid, CAT_TOOLS);
        int amount = playerCraftAmount.getOrDefault(uuid, 1);
        int workbenchLevel = getNearbyWorkbenchLevel(player); // 주변 작업대 체크

        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE_PREFIX);

        loadRecipesToGUI(inv, currentCategory);
        updateBottomArea(player, inv);

        player.openInventory(inv);
    }

    // 하단 영역(45~53)만 업데이트하는 메서드 (깜빡임 방지용)
    private void updateBottomArea(Player player, Inventory inv) {
        UUID uuid = player.getUniqueId();

        // 4-1. 대기열 표시 (Slot 45~50, 6칸)
        List<CraftingTask> queue = craftingQueues.getOrDefault(uuid, new ArrayList<>());
        for (int i = 0; i < 6; i++) {
            int slot = 45 + i;
            if (i < queue.size()) {
                CraftingTask task = queue.get(i);
                ItemStack item = task.result.clone();
                ItemMeta meta = item.getItemMeta();
                List<String> lore = (meta != null && meta.hasLore()) ? meta.getLore() : new ArrayList<>();
                lore.add("§8----------------");
                lore.add(i == 0 ? "§e▶ 제작 중: " + task.remainingTime + "초" : "§7대기 중: " + task.remainingTime + "초");
                if (meta != null) { meta.setLore(lore); item.setItemMeta(meta); }
                inv.setItem(slot, item);
            }
        }

        // 4-2. 선택된 아이템 미리보기 (Slot 52)
        ItemStack selected = playerSelectedRecipe.get(uuid);
        if (selected != null) {
            ItemStack preview = selected.clone();
            ItemMeta meta = preview.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e[선택됨] §f" + getItemNameKorean(selected));
                preview.setItemMeta(meta);
            }
            inv.setItem(51, preview);
        } else {
            inv.setItem(51, createItem(Material.BARRIER, "§c선택 없음", "§7목록에서 제작할 아이템을\n§7클릭하세요."));
        }
    }

    // --- [2] 이벤트 핸들러 ---

    @EventHandler
    public void onPlayerSwapHand(PlayerSwapHandItemsEvent e) {
        e.setCancelled(true);
        openDefaultCrafting(e.getPlayer());
        e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, 0.5f, 1.5f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (!title.startsWith("§0제작 시스템")) {
            handleRegistrationClick(e); // 등록 메뉴 처리로 넘김
            return;
        }

        e.setCancelled(true);
        Player player = (Player) e.getWhoClicked();
        Inventory inv = e.getClickedInventory();
        if (inv == null || inv != e.getView().getTopInventory()) return;

        int slot = e.getRawSlot();
        UUID uuid = player.getUniqueId();

        // 1. 상단 탭 클릭 (Slot 0~5)
        if (slot >= 0 && slot <= 5) {
            int workbenchLevel = getNearbyWorkbenchLevel(player);

            // 클릭한 슬롯의 요구 레벨 체크
            boolean canAccess = true;
            if (slot == 3 && workbenchLevel < 1) canAccess = false;
            if (slot == 4 && workbenchLevel < 2) canAccess = false;
            if (slot == 5 && workbenchLevel < 3) canAccess = false;

            if (!canAccess) {
                player.sendMessage("§c[System] 해당 작업대 근처에서만 이용 가능합니다.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1f);
                return;
            }

            String newCategory = getCategoryBySlot(slot);
            if (newCategory != null) {
                playerCurrentCategory.put(uuid, newCategory);
                playerSelectedRecipe.remove(uuid);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                openCraftingGUI(player);
            }
        }
        // 2. 수량 조절 (Slot 6, 8)
        else if (slot == 6 || slot == 8) {
            int current = playerCraftAmount.getOrDefault(uuid, 1);
            if (slot == 6) current = Math.max(1, current - 1);
            if (slot == 8) current = Math.min(64, current + 1);

            playerCraftAmount.put(uuid, current);
            syncAmountToMod(player, current);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 2f);

            updateBottomArea(player, inv);
        }
        // 3. 레시피 아이템 선택 (Slot 9~44)
        else if (slot >= 9 && slot <= 44) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR) {
                // 관리자 모드: Shift+우클릭 (등록), Shift+좌클릭 (삭제)
                if (player.isOp() && e.getClick().isShiftClick()) {
                    handleAdminAction(player, slot, clicked);
                    return;
                }

                // 일반 유저: 아이템 선택
                playerSelectedRecipe.put(uuid, clicked); // 결과물 저장 (미리보기용)

                // Config 경로 찾기 (실제 제작용) - 아이템에 숨겨진 키나 메타데이터가 없으므로 Config를 역추적
                String currentCat = playerCurrentCategory.get(uuid);
                String foundPath = findRecipePath(clicked, currentCat);
                if (foundPath != null) {
                    playerSelectedPath.put(uuid, foundPath);
                }

                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f);
                updateBottomArea(player, inv); // 선택된 아이템 표시 및 버튼 활성화
            }
        }
        // 4. 제작 실행 버튼 (Slot 52)
        else if (slot == 52) {
            ItemStack selected = playerSelectedRecipe.get(uuid);
            String path = playerSelectedPath.get(uuid);

            if (selected != null && path != null) {
                int amount = playerCraftAmount.getOrDefault(uuid, 1);
                tryStartCrafting(player, selected, path, amount);
            } else {
                player.sendMessage("§c[System] 아이템을 먼저 선택해주세요.");
            }
        }
    }

    // --- [3] 제작 로직 ---

    private void tryStartCrafting(Player player, ItemStack result, String recipePath, int multiplier) {
        String category = playerCurrentCategory.get(player.getUniqueId());
        // path 구조: recipes.category.slot.uuid
        // recipePath는 "category.slot.uuid" 형태라고 가정하지 않고, findRecipePath가 반환한 값 사용

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(recipePath);
        if (sec == null) {
            player.sendMessage("§c[System] 레시피 정보를 찾을 수 없습니다.");
            return;
        }

        List<ItemStack> ingredients = (List<ItemStack>) sec.getList("ingredients");
        int baseTime = sec.getInt("time", 10);
        int totalTime = baseTime * multiplier; // (선택 사항) 시간도 배수로 늘릴지 여부. 여기선 늘림.

        // 필요 재료 계산 (기본 재료 * 배수)
        List<ItemStack> totalIngredients = new ArrayList<>();
        if (ingredients != null) {
            for (ItemStack ing : ingredients) {
                ItemStack clone = ing.clone();
                clone.setAmount(ing.getAmount() * multiplier);
                totalIngredients.add(clone);
            }
        }

        if (hasIngredients(player, totalIngredients)) {
            // 결과물 수량 계산
            ItemStack finalResult = result.clone();
            finalResult.setAmount(result.getAmount() * multiplier);

            startCraftingProcess(player, finalResult, totalIngredients, totalTime);
        } else {
            sendMissingIngredientsMessage(player, totalIngredients);
        }
    }

    private void startCraftingProcess(Player player, ItemStack result, List<ItemStack> ingredients, int time) {
        List<CraftingTask> queue = craftingQueues.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());

        // 대기열 7칸 제한
        if (queue.size() >= 7) {
            player.sendMessage("§c[System] 제작 대기열이 가득 찼습니다.");
            return;
        }

        removeIngredients(player, ingredients);
        queue.add(new CraftingTask(result, time));

        player.sendMessage("§e[System] 제작을 시작합니다. (" + getItemNameKorean(result) + " x" + result.getAmount() + ")");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.5f);

        // GUI 갱신 (보고 있다면)
        if (player.getOpenInventory().getTitle().startsWith("§0제작 시스템")) {
            updateBottomArea(player, player.getOpenInventory().getTopInventory());
        }
    }

    // --- [4] 유틸리티 및 내부 로직 ---

    // 카테고리 탭 아이콘 생성 (선택된 탭은 인챈트 효과)
    private ItemStack createTabItem(Material mat, String name, String catKey, String currentCat) {
        ItemStack item = createItem(mat, "§f" + name, "§7클릭하여 카테고리를 이동합니다.");
        if (catKey.equals(currentCat)) {
            ItemMeta meta = item.getItemMeta();
            meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.setDisplayName("§a§l[선택됨] " + name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getCategoryDisplayName(String key) {
        if (key.equals(CAT_TOOLS)) return "도구";
        if (key.equals(CAT_CONSTRUCT)) return "건축";
        if (key.equals(CAT_CONSUMABLES)) return "소모품";
        if (key.equals(CAT_LV1)) return "1레벨 작업대";
        if (key.equals(CAT_LV2)) return "2레벨 작업대";
        if (key.equals(CAT_LV3)) return "3레벨 작업대";
        return "알 수 없음";
    }

    private String getCategoryBySlot(int slot) {
        return switch (slot) {
            case 0 -> CAT_TOOLS;
            case 1 -> CAT_CONSTRUCT;
            case 2 -> CAT_CONSUMABLES;
            case 3 -> CAT_LV1;
            case 4 -> CAT_LV2;
            case 5 -> CAT_LV3;
            default -> null;
        };
    }

    private void loadRecipesToGUI(Inventory inv, String category) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("recipes." + category);
        if (sec == null) return;

        for (String slotKey : sec.getKeys(false)) {
            try {
                int slot = Integer.parseInt(slotKey);
                // GUI 영역(9~44)을 벗어나면 무시
                if (slot < 9 || slot > 44) continue;

                ConfigurationSection slotSec = sec.getConfigurationSection(slotKey);
                if (slotSec != null && !slotSec.getKeys(false).isEmpty()) {
                    String recipeId = slotSec.getKeys(false).iterator().next();
                    ItemStack result = slotSec.getItemStack(recipeId + ".result");
                    if (result != null) {
                        inv.setItem(slot, result);
                    }
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    // 아이템을 클릭했을 때 Config 상의 전체 경로를 역추적하는 메서드
    private String findRecipePath(ItemStack clicked, String category) {
        ConfigurationSection catSec = plugin.getConfig().getConfigurationSection("recipes." + category);
        if (catSec == null) return null;

        for (String slotKey : catSec.getKeys(false)) {
            ConfigurationSection slotSec = catSec.getConfigurationSection(slotKey);
            if (slotSec == null) continue;
            for (String id : slotSec.getKeys(false)) {
                ItemStack saved = slotSec.getItemStack(id + ".result");
                if (saved != null && saved.isSimilar(clicked)) {
                    return "recipes." + category + "." + slotKey + "." + id;
                }
            }
        }
        return null;
    }

    // 관리자 등록/삭제 처리
    private void handleAdminAction(Player player, int slot, ItemStack clicked) {
        String category = playerCurrentCategory.get(player.getUniqueId());

        if (player.getOpenInventory().getTopInventory().getItem(slot) == null && !clicked.getType().isAir()) {
            // 빈 슬롯이 아님 (삭제 로직) - clicked가 인벤토리 아이템인 경우
            // 로직 단순화를 위해 여기서는 "기존 아이템 클릭 -> 삭제"
            removeRecipe(clicked, category);
            player.sendMessage("§c[System] 레시피가 삭제되었습니다.");
            openCraftingGUI(player); // 새로고침
        } else {
            // 우클릭 -> 등록 창 열기
            player.setMetadata("reg_path", new org.bukkit.metadata.FixedMetadataValue(plugin, category));
            player.setMetadata("reg_slot", new org.bukkit.metadata.FixedMetadataValue(plugin, slot));
            openRegistrationMenu(player);
            player.sendMessage("§a[System] " + getCategoryDisplayName(category) + " - " + slot + "번 슬롯에 등록을 시작합니다.");
        }
    }

    // --- [5] 등록 GUI 및 기존 핸들러 (유지) ---

    public void openRegistrationMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, REG_STEP1_TITLE);
        player.openInventory(inv);
    }

    private void openTimeSettingMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, REG_STEP2_TITLE);
        int currentTime = pendingTime.getOrDefault(player.getUniqueId(), 10);
        inv.setItem(0, createItem(Material.RED_STAINED_GLASS_PANE, "§c-10초", ""));
        inv.setItem(1, createItem(Material.ORANGE_STAINED_GLASS_PANE, "§c-1초", ""));
        inv.setItem(4, createItem(Material.CLOCK, "§e현재 설정 시간: §f" + currentTime + "초", ""));
        inv.setItem(7, createItem(Material.LIME_STAINED_GLASS_PANE, "§a+1초", ""));
        inv.setItem(8, createItem(Material.GREEN_STAINED_GLASS_PANE, "§a+10초", ""));
        inv.setItem(2, createItem(Material.NETHER_STAR, "§a§l[레시피 등록 완료]", ""));
        inv.setItem(6, createItem(Material.BARRIER, "§c[등록 취소]", ""));
        player.openInventory(inv);
    }

    private void handleRegistrationClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        Player player = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();

        if (title.equals(REG_STEP1_TITLE)) {
            // 1단계: 아이템 올리기 (로직 없음, CloseEvent에서 처리)
        }
        else if (title.equals(REG_STEP2_TITLE)) {
            e.setCancelled(true);
            int currentTime = pendingTime.getOrDefault(player.getUniqueId(), 10);
            switch (slot) {
                case 0: currentTime = Math.max(1, currentTime - 10); break;
                case 1: currentTime = Math.max(1, currentTime - 1); break;
                case 7: currentTime += 1; break;
                case 8: currentTime += 10; break;
                case 2: saveFinalRecipe(player); player.closeInventory(); return;
                case 6: clearPendingData(player); player.closeInventory(); return;
                default: return;
            }
            pendingTime.put(player.getUniqueId(), currentTime);
            openTimeSettingMenu(player);
        }
    }

    // InventoryCloseEvent, startUpdateTask 등 나머지 필수 메서드는 구조에 맞춰 유지
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getView().getTitle().equals(REG_STEP1_TITLE)) {
            Inventory inv = e.getInventory();
            ItemStack result = inv.getItem(8);
            if (result == null || result.getType() == Material.AIR) {
                clearPendingData((Player) e.getPlayer()); return;
            }
            List<ItemStack> ings = new ArrayList<>();
            for (int i=0; i<8; i++) {
                ItemStack it = inv.getItem(i);
                if (it!=null && !it.getType().isAir()) ings.add(it);
            }
            if (ings.isEmpty()) return;

            Player p = (Player) e.getPlayer();
            pendingResult.put(p.getUniqueId(), result.clone());
            pendingIngredients.put(p.getUniqueId(), ings);
            pendingTime.put(p.getUniqueId(), 10);
            new BukkitRunnable() { public void run() { openTimeSettingMenu(p); } }.runTaskLater(plugin, 1L);
        }
    }

    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : new HashSet<>(craftingQueues.keySet())) {
                    List<CraftingTask> queue = craftingQueues.get(uuid);
                    if (queue == null || queue.isEmpty()) continue;

                    CraftingTask current = queue.get(0);
                    current.remainingTime--;

                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) continue;

                    if (current.remainingTime <= 0) {
                        player.getInventory().addItem(current.result);
                        player.sendMessage("§a[System] 제작 완료: " + getItemNameKorean(current.result));
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1f);
                        queue.remove(0);
                    }

                    if (player.getOpenInventory().getTitle().startsWith("§0제작 시스템")) {
                        updateBottomArea(player, player.getOpenInventory().getTopInventory());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler
    public void onBlockInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        org.bukkit.block.Block block = e.getClickedBlock();
        if (block == null) return;

        String typeName = block.getType().name();
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();

        // 1. 작업대 레벨에 따라 카테고리 문자열 결정
        String targetCategory = null;
        if (typeName.equals("JEG_SCRAP_WORKBENCH")) targetCategory = CAT_LV1;
        else if (typeName.equals("JEG_GUNMETAL_WORKBENCH")) targetCategory = CAT_LV2;
        else if (typeName.equals("JEG_GUNNITE_WORKBENCH")) targetCategory = CAT_LV3;

        if (targetCategory != null) {
            e.setCancelled(true);

            // 2. 플레이어의 현재 카테고리 상태를 해당 작업대로 강제 설정
            playerCurrentCategory.put(uuid, targetCategory);
            playerCraftAmount.putIfAbsent(uuid, 1);
            playerSelectedRecipe.remove(uuid); // 이전 선택 초기화

            // 3. 통합 GUI 열기
            openCraftingGUI(player);

            player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, 0.5f, 1.5f);
        }
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
        player.sendMessage("§c[System] 재료가 부족합니다. 필요 재료:");

        for (ItemStack item : ingredients) {
            // 1. 커스텀 이름이 있는 경우 (가장 정확함)
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                player.sendMessage(" §f- " + item.getItemMeta().getDisplayName() + " §7(" + item.getAmount() + "개)");
            }
            // 2. 일반/모드 아이템 처리
            else {
                // Deprecated 된 메서드 대신 아이템 타입의 기본 번역 키를 가져옵니다.
                // 1.20.1 Forge/Spigot 환경에서는 이 키가 가장 정확한 클라이언트 번역을 유도합니다.
                String tKey = item.getType().translationKey();

                player.spigot().sendMessage(
                        new net.md_5.bungee.api.chat.TextComponent(" §f- "),
                        new net.md_5.bungee.api.chat.TranslatableComponent(tKey),
                        new net.md_5.bungee.api.chat.TextComponent(" §7(" + item.getAmount() + "개)")
                );
            }
        }
    }

    private void startFurnaceParticleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // 서버의 모든 플레이어 주변을 탐색하는 방식이 성능상 유리합니다.
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // 플레이어 주변 10칸 이내의 로드된 청크에서 화로 탐색
                    Location loc = player.getLocation();
                    for (int x = -10; x <= 10; x++) {
                        for (int y = -5; y <= 5; y++) {
                            for (int z = -10; z <= 10; z++) {
                                org.bukkit.block.Block block = loc.clone().add(x, y, z).getBlock();

                                // 일반 화로(FURNACE)만 타겟팅 (훈연기, 용광로 제외 가능)
                                if (block.getType() == Material.FURNACE) {
                                    if (block.getBlockData() instanceof Furnace furnace) {
                                        // 화로가 불붙어 있는 상태(Lit)인지 확인
                                        if (furnace.isLit()) {
                                            spawnInnerFlame(block, furnace.getFacing());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L); // 2틱마다 실행 (부드러운 효과)
    }

    // 2. 화로 안쪽에 파티클을 소환하는 핵심 로직
    private void spawnInnerFlame(org.bukkit.block.Block block, BlockFace facing) {
        // 1. 블록의 정중앙 좌표 (0.5, 0.5, 0.5)
        Location center = block.getLocation().add(0.5, 0.5, 0.5);

        // 2. 안쪽 깊이 설정
        // facing.getDirection()은 입구 쪽을 향하는 화살표입니다.
        // 여기에 음수(-)를 곱하면 '입구 반대편(안쪽)'으로 이동합니다.
        // -0.1 ~ -0.2 정도면 화로 모델의 빈 공간 중심에 딱 위치합니다.
        double depth = -0.1;
        Vector offset = facing.getDirection().multiply(depth);
        // 3. 높이 조정 (화로 바닥보다 살짝 위)
        Location particleLoc = center.add(offset);

        // 4. 파티클 소환 (개수를 늘리고 범위를 좁혀 집중된 느낌 부여)
        block.getWorld().spawnParticle(org.bukkit.Particle.FLAME, particleLoc, 3, 0.02, 0.02, 0.02, 0.01);

        // 가끔 튀어오르는 불꽃 효과 (선택 사항)
        if (new Random().nextInt(10) == 0) {
            block.getWorld().spawnParticle(org.bukkit.Particle.LAVA, particleLoc, 1, 0, 0, 0, 0);
        }
    }
    private void removeRecipe(ItemStack result, String category) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("recipes." + category);
        if (section == null) return;
        for (String slotKey : section.getKeys(false)) {
            ConfigurationSection slotSection = section.getConfigurationSection(slotKey);
            if (slotSection == null) continue;
            for (String recipeId : slotSection.getKeys(false)) {
                ItemStack item = slotSection.getItemStack(recipeId + ".result");
                if (item != null && item.isSimilar(result)) {
                    plugin.getConfig().set("recipes." + category + "." + slotKey, null);
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
                meta.setLore(Arrays.asList(lore.split("\n")));
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
    // 주변 10블록 이내의 작업대 중 가장 높은 레벨을 반환 (0: 없음, 1: Scrap, 2: Gunmetal, 3: Gunnite)
    private int getNearbyWorkbenchLevel(Player player) {
        Location loc = player.getLocation();
        int maxLevel = 0;

        // 주변 10x10x10 탐색
        for (int x = -5; x <= 5; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -5; z <= 5; z++) {
                    Material type = loc.clone().add(x, y, z).getBlock().getType();
                    String typeName = type.name();

                    if (typeName.equals("JEG_SCRAP_WORKBENCH")) maxLevel = Math.max(maxLevel, 1);
                    else if (typeName.equals("JEG_GUNMETAL_WORKBENCH")) maxLevel = Math.max(maxLevel, 2);
                    else if (typeName.equals("JEG_GUNNITE_WORKBENCH")) maxLevel = Math.max(maxLevel, 3);

                    // 3레벨(최고)을 찾으면 더 이상 탐색할 필요 없음
                    if (maxLevel == 3) return 3;
                }
            }
        }
        return maxLevel;
    }
    // 기존의 sendAmountPacket은 삭제하거나 아래처럼 통합하세요.
    private void syncAmountToMod(Player player, int amount) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeByte(11);
        out.writeInt(amount);
        player.sendPluginMessage(plugin, "examplemod:messages", out.toByteArray());
    }
}