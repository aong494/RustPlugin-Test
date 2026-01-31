package Alpa.test;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class PlankToIronManager implements Listener {

    private final main plugin;
    // 최대 체력 설정
    private final int WOOD_MAX_HP = 50;
    private final int STONE_MAX_HP = 100;
    private final int IRON_MAX_HP = 200;

    public PlankToIronManager(main plugin) {
        this.plugin = plugin;
        // Config 기본값 설정
        if (!plugin.getConfig().contains("upgrade-settings.stone-cost")) {
            plugin.getConfig().set("upgrade-settings.stone-cost", 5);
            plugin.getConfig().set("upgrade-settings.iron-cost", 5);
            plugin.getConfig().set("plank-settings.repair-amount", 10);
            plugin.getConfig().set("stone-settings.repair-amount", 20);
            plugin.getConfig().set("upgrade-settings.repair-amount", 40); // 철 수리량
            plugin.saveConfig();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockInteract(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null) return;

        Player player = e.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // 막대기를 들고 있을 때만 작동
        if (mainHand.getType() != Material.STICK) return;

        // 1. 데이터 위치 확인 (dataStorage 사용)
        String blockKey = plugin.blockToKey(b);
        String pPath = "planks." + blockKey;
        if (!plugin.dataStorage.getConfig().contains(pPath)) return;

        // 2. 권한 체크
        if (!plugin.canAccess(player, blockKey, "planks")) {
            String errorName = plugin.dataStorage.getConfig().getString(pPath + ".display_name", "구조물");
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText("§c[" + errorName + "] 관리 권한이 없습니다."));
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);
        String typeName = b.getType().name();

        // --- 3. 데이터 로드 (이름 버그 해결) ---
        // data.yml에서 저장된 이름을 먼저 가져오고, 없으면 재질로 판단
        String displayName = plugin.dataStorage.getConfig().getString(pPath + ".display_name");
        int maxHp = plugin.dataStorage.getConfig().getInt(pPath + ".max_health", -1);

        // 4. 재질 판단 및 기본값 보정
        Material repairMat = null;
        String settingKey = "plank-settings";
        if (typeName.contains("ARMORED_DOOR")) {
            if (displayName == null) displayName = "합금 문";
            if (maxHp == -1) maxHp = 800;
        } else if (typeName.contains("BIG_DOOR")) {
            if (displayName == null) displayName = "차고 문";
            if (maxHp == -1) maxHp = 600;
        } else if (b.getType() == Material.GOLD_BLOCK) {
            if (displayName == null) displayName = "자동 터렛";
            if (maxHp == -1) maxHp = IRON_MAX_HP; // 터렛은 철급 체력(200)
            repairMat = Material.IRON_INGOT;
            settingKey = "upgrade-settings";
        }
        else if (typeName.contains("IRON")) {
            if (displayName == null) displayName = "철";
            if (maxHp == -1) maxHp = IRON_MAX_HP; // 200
            repairMat = Material.IRON_INGOT;
            settingKey = "upgrade-settings";
        }
        else if (typeName.contains("STONE_BRICK")) {
            if (displayName == null) displayName = "석재 벽돌";
            if (maxHp == -1) maxHp = STONE_MAX_HP; // 100
            repairMat = Material.COBBLESTONE;
            settingKey = "stone-settings";
        }
        else {
            if (displayName == null) displayName = "나무";
            if (maxHp == -1) maxHp = WOOD_MAX_HP; // 50
        }

        // --- 5. 상호작용 분기 처리 ---

        // 좌클릭 + 쉬프트: 업그레이드
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && player.isSneaking()) {
            handleUpgrade(player, b, typeName, pPath);
        }
        // 좌클릭: 수리 (철괴 소모 로직 포함)
        else if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            handleRepair(player, b, pPath, maxHp, repairMat, displayName, settingKey);
        }
        // 우클릭: 정보 확인 및 철거
        else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (player.isSneaking()) {
                // 터렛 모델 제거 및 아이템 드롭
                if (b.getType() == Material.GOLD_BLOCK) {
                    plugin.turretManager.removeTurret(b.getLocation());
                    plugin.turretManager.dropTurretItems(b.getLocation());
                }
                plugin.dataStorage.getConfig().set(pPath, null);
                plugin.dataStorage.saveConfig();
                b.setType(Material.AIR);
                player.sendMessage("§e[" + displayName + "] 구조물을 철거했습니다.");
                player.playSound(b.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.0f, 0.8f);
            } else {
                // 체력 확인 (dataStorage에서 읽기)
                int currentHp = plugin.dataStorage.getConfig().getInt(pPath + ".health", maxHp);
                String progressBar = plugin.getProgressBar(currentHp, maxHp);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§f[" + displayName + "] " + progressBar + " §f(" + currentHp + " / " + maxHp + ")"));
            }
        }
    }

    private void handleUpgrade(Player player, Block b, String typeName, String pKey) {
        boolean isWood = typeName.contains("_PLANKS") || (typeName.contains("_STAIRS") && !typeName.contains("STONE_BRICK"));
        boolean isStone = typeName.contains("STONE_BRICK");

        if (isWood) {
            int cost = plugin.getConfig().getInt("upgrade-settings.stone-cost", 5);
            if (hasItem(player, Material.COBBLESTONE, cost)) {
                removeItem(player, Material.COBBLESTONE, cost);
                Material nextMat = typeName.contains("STAIRS") ? Material.STONE_BRICK_STAIRS : Material.STONE_BRICKS;
                performUpgrade(b, nextMat, STONE_MAX_HP, "§7석재 벽돌", Sound.BLOCK_STONE_PLACE);
                player.sendMessage("§a[Upgrade] §7석재 벽돌로 업그레이드 완료!");
            } else {
                player.sendMessage("§c[Upgrade] 조약돌이 " + cost + "개 필요합니다.");
            }
        } else if (isStone) {
            int cost = plugin.getConfig().getInt("upgrade-settings.iron-cost", 5);
            if (hasItem(player, Material.IRON_INGOT, cost)) {
                removeItem(player, Material.IRON_INGOT, cost);
                performUpgrade(b, Material.IRON_BLOCK, IRON_MAX_HP, "§f철", Sound.BLOCK_ANVIL_USE);
                player.sendMessage("§a[Upgrade] §f철 블록으로 업그레이드 완료!");
            } else {
                player.sendMessage("§c[Upgrade] 철괴가 " + cost + "개 필요합니다.");
            }
        }
    }

    private void handleRepair(Player player, Block b, String pKey, int maxHp, Material repairMat, String displayName, String settingKey) {
        int currentHp = plugin.dataStorage.getConfig().getInt(pKey + ".health", maxHp);
        int actualMaxHp = plugin.dataStorage.getConfig().getInt(pKey + ".max_health", maxHp);
        int repairAmt = plugin.getConfig().getInt(settingKey + ".repair-amount", 10);
        int repairCost = plugin.getConfig().getInt(settingKey + ".repair-cost", 1);
        if (currentHp > (actualMaxHp - repairAmt)) {
            if (currentHp >= actualMaxHp) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§a[" + displayName + "] 체력이 가득 차 있습니다."));
            } else {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§e[" + displayName + "] 조금 더 파손되어야 수리할 수 있습니다. §f(최소 " + repairAmt + " 데미지 필요)"));
            }
            return;
        }

        // 3. 재료 확인 및 차감 로직 (기존과 동일)
        boolean hasMat = (repairMat != null) ? plugin.hasItem(player, repairMat, repairCost) : plugin.hasEnoughPlanks(player, repairCost);

        if (hasMat) {
            if (repairMat != null) plugin.removeItem(player, repairMat, repairCost);
            else plugin.removePlanks(player, repairCost);

            int newHp = Math.min(actualMaxHp, currentHp + repairAmt);
            plugin.dataStorage.getConfig().set(pKey + ".health", newHp);
            plugin.dataStorage.saveConfig();

            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText("§b[" + displayName + "] 수리 완료! " + plugin.getProgressBar(newHp, actualMaxHp) + " §f(" + newHp + " / " + actualMaxHp + ")"));

            Sound s = (b.getType() == Material.GOLD_BLOCK || actualMaxHp >= 200) ? Sound.BLOCK_ANVIL_USE : Sound.BLOCK_WOOD_PLACE;
            player.playSound(b.getLocation(), s, 1.0f, 1.2f);
        } else {
            String matName = (repairMat == Material.IRON_INGOT) ? "철괴" : (repairMat == Material.COBBLESTONE ? "조약돌" : "나무판자");
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText("§c[" + displayName + "] 수리 재료 부족 (" + matName + " " + repairCost + "개 필요)"));
        }
    }

    private void performUpgrade(Block b, Material nextMat, int newHp, String tierName, Sound sound) {
        String blockKey = plugin.blockToKey(b);
        String pPath = "planks." + blockKey;
        BlockData oldData = b.getBlockData();
        b.setType(nextMat);

        if (oldData instanceof Stairs && b.getBlockData() instanceof Stairs) {
            Stairs newStairs = (Stairs) b.getBlockData();
            Stairs oldStairs = (Stairs) oldData;
            newStairs.setFacing(oldStairs.getFacing());
            newStairs.setHalf(oldStairs.getHalf());
            newStairs.setShape(oldStairs.getShape());
            b.setBlockData(newStairs);
        }

        // dataStorage에 저장 (주인 정보는 유지됨)
        plugin.dataStorage.getConfig().set(pPath + ".health", newHp);
        plugin.dataStorage.saveConfig();

        b.getWorld().playSound(b.getLocation(), sound, 1.0f, 1.5f);
        b.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, b.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3);
    }

    private boolean hasItem(Player p, Material type, int amount) {
        int count = 0;
        for (ItemStack i : p.getInventory().getContents()) {
            if (i != null && i.getType() == type) count += i.getAmount();
        }
        return count >= amount;
    }

    private void removeItem(Player p, Material type, int amount) {
        int toRemove = amount;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == type) {
                if (item.getAmount() > toRemove) {
                    item.setAmount(item.getAmount() - toRemove);
                    break;
                } else {
                    toRemove -= item.getAmount();
                    contents[i] = null;
                }
            }
            if (toRemove <= 0) break;
        }
        p.getInventory().setContents(contents);
    }
}