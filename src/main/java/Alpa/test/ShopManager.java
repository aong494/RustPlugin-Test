package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.messaging.PluginMessageListener;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ShopManager implements PluginMessageListener {
    private final main plugin;
    private final Map<String, Inventory> shopCache = new HashMap<>();

    public ShopManager(main plugin) {
        this.plugin = plugin;
    }

    public void loadAllShops() {
        shopCache.clear();
        ConfigurationSection shopsSection = plugin.shopConfig.getConfigurationSection("shops");

        if (shopsSection == null) return;

        for (String shopId : shopsSection.getKeys(false)) {
            String displayTitle = shopsSection.getString(shopId + ".title", "상점");
            String technicalTitle = displayTitle + " [shop]";

            Inventory inv = Bukkit.createInventory(null, 54, technicalTitle);

            ConfigurationSection items = shopsSection.getConfigurationSection(shopId + ".items");
            if (items != null) {
                for (String slotStr : items.getKeys(false)) {
                    int slot = Integer.parseInt(slotStr);
                    setupSlot(inv, items, slot, slotStr);
                }
            }
            shopCache.put(shopId, inv);
        }
    }

    // ShopManager.java 내 setupSlot 메서드 수정
    private void setupSlot(Inventory inv, ConfigurationSection items, int slot, String slotStr) {
        String itemStr = items.getString(slotStr + ".item", "minecraft:air");
        String priceStr = items.getString(slotStr + ".price_item", "minecraft:air");
        int priceAmount = items.getInt(slotStr + ".price_amount", 1);

        ItemStack displayItem = createModItem(itemStr, 1);
        ItemStack priceItem = createModItem(priceStr, 1); // 갯수는 무조건 1개로 표시

        if (priceItem != null) {
            ItemMeta meta = priceItem.getItemMeta();
            meta.setDisplayName("§8PRICE_VAL:" + priceAmount);
            priceItem.setItemMeta(meta);
        }

        if (displayItem != null) inv.setItem(slot, displayItem);
        if (priceItem != null) inv.setItem(slot + 9, priceItem);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("examplemod:shop_data")) return;
        try {
            String payload = new String(message, StandardCharsets.UTF_8);
            String[] parts = payload.split(":");
            if (parts.length < 3 || !parts[0].equals("BUY")) return;
            int slotIdx = Integer.parseInt(parts[1]);
            int buyAmount = Integer.parseInt(parts[2]);

            // 2. 현재 열린 인벤토리 확인 로그
            Inventory openInv = player.getOpenInventory().getTopInventory();
            String currentShopId = null;
            for (Map.Entry<String, Inventory> entry : shopCache.entrySet()) {
                if (entry.getValue().equals(openInv)) {
                    currentShopId = entry.getKey();
                    break;
                }
            }
            if (currentShopId != null) {
                processPurchase(player, currentShopId, slotIdx, buyAmount);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void processPurchase(Player player, String shopId, int slotIdx, int buyAmount) {
        ConfigurationSection itemCfg = plugin.shopConfig.getConfigurationSection("shops." + shopId + ".items." + slotIdx);
        if (itemCfg == null) return;

        int unitPrice = itemCfg.getInt("price_amount");
        int totalPrice = unitPrice * buyAmount;

        Inventory openInv = player.getOpenInventory().getTopInventory();
        ItemStack priceIcon = openInv.getItem(slotIdx + 9); // 상점창의 가격 아이콘

        if (priceIcon == null || priceIcon.getType() == Material.AIR) {
            return;
        }
        int playerHas = countTotalItems(player, priceIcon);
        if (playerHas >= totalPrice) {
            removeItems(player, priceIcon.getType(), totalPrice);

            ItemStack productIcon = openInv.getItem(slotIdx);
            if (productIcon != null) {
                ItemStack reward = productIcon.clone();
                reward.setAmount(buyAmount);
                player.getInventory().addItem(reward);
                player.sendMessage("§a[Shop] §f구매 완료!");
            }
        } else {
            player.sendMessage("§c[Shop] §f재료가 부족합니다. (보유: " + playerHas + " / 필요: " + totalPrice + ")");
        }
    }

    private int countTotalItems(Player player, ItemStack targetIcon) {
        int count = 0;
        if (targetIcon == null) return 0;
        String targetName = targetIcon.getType().name();

        for (ItemStack is : player.getInventory().getContents()) {
            if (is == null || is.getType() == Material.AIR) continue;

            String currentName = is.getType().name();

            if (currentName.equals(targetName)) {
                count += is.getAmount();
            }
        }
        return count;
    }
    // 모드 아이템 생성을 위한 보조 메서드 (하이브리드 서버용)
    private ItemStack createModItem(String itemStr, int amount) {
        Material mat = Material.matchMaterial(itemStr.toUpperCase().replace(":", "_"));
        if (mat == null) mat = Material.matchMaterial(itemStr);

        if (mat != null) {
            return new ItemStack(mat, amount);
        }
        return null;
    }

    // 아이템을 여러 슬롯에서 나누어 차감하는 보조 메서드
    private void removeItems(Player player, Material mat, int amount) {
        ItemStack[] contents = player.getInventory().getContents();
        int leftToRemove = amount;

        for (int i = 0; i < contents.length; i++) {
            ItemStack is = contents[i];
            if (is != null && is.getType() == mat) {
                int stackAmount = is.getAmount();
                if (stackAmount <= leftToRemove) {
                    leftToRemove -= stackAmount;
                    player.getInventory().setItem(i, null); // 해당 슬롯 비움
                } else {
                    is.setAmount(stackAmount - leftToRemove);
                    leftToRemove = 0;
                }
            }
            if (leftToRemove <= 0) break;
        }
    }

    public void openShop(Player player, String shopId) {
        if (shopCache.containsKey(shopId)) player.openInventory(shopCache.get(shopId));
        else player.sendMessage("§c[Shop] §f존재하지 않는 상점입니다.");
    }
}