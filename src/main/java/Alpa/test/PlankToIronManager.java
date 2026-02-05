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
            plugin.getConfig().set("iron-settings.repair-amount", 40);
            plugin.getConfig().set("armored-door-settings.repair-amount", 40);
            plugin.saveConfig();
        }
    }
    // 특정 아이템(모드 아이템 포함)을 가지고 있는지 확인하는 메서드
    private boolean hasCustomItem(Player p, String targetId, int amount) {
        int count = 0;
        for (ItemStack i : p.getInventory().getContents()) {
            if (i != null) {
                // 아이템의 타입을 문자열로 변환하여 확인합니다.
                if (i.getType().name().equalsIgnoreCase("STEEL_INGOT") ||
                        i.getType().name().contains("STEEL")) {
                    count += i.getAmount();
                }
            }
        }
        return count >= amount;
    }

    // 특정 아이템을 차감하는 메서드
    private void removeCustomItem(Player p, String targetId, int amount) {
        int toRemove = amount;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && (item.getType().name().contains("STEEL"))) {
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
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockInteract(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null) return;

        Player player = e.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType() != Material.STICK) return;

        // --- 수정 포인트: 마스터 블록 찾기 ---
        Block master = plugin.getMasterBlock(b);
        String blockKey = plugin.blockToKey(master);
        String pPath = "planks." + blockKey;
        String dPath = "doors." + blockKey;

        boolean isPlank = plugin.dataStorage.getConfig().contains(pPath);
        boolean isDoor = plugin.dataStorage.getConfig().contains(dPath);

        if (!isPlank && !isDoor) return; // 둘 다 아니면 종료

        // 권한 체크 (문과 일반 블록 경로 구분)
        String activePath = isDoor ? dPath : pPath;
        String activeCategory = isDoor ? "doors" : "planks";

        if (!plugin.canAccess(player, blockKey, activeCategory)) {
            String errorName = plugin.dataStorage.getConfig().getString(activePath + ".display_name", "구조물");
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText("§c[" + errorName + "] 관리 권한이 없습니다."));
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);
        String typeName = b.getType().name();

        // --- 3. 이름 및 체력 데이터 로드 ---
        String displayName = plugin.dataStorage.getConfig().getString(activePath + ".display_name");
        int maxHp = plugin.dataStorage.getConfig().getInt(activePath + ".max_health", -1);

        Material repairMat = null;
        boolean useSteel = false;
        String settingKey = "plank-settings";

        // 재질 판단 로직 (문 추가)
        if (typeName.contains("ARMORED_DOOR")) {
            if (displayName == null) displayName = "합금 문";
            if (maxHp == -1) maxHp = 800;
            useSteel = true;
            settingKey = "armored-door-settings";
        } else if (typeName.contains("BIG_DOOR") || typeName.contains("DUMMY")) {
            if (displayName == null) displayName = "차고 문";
            if (maxHp == -1) maxHp = 600;
            repairMat = Material.IRON_INGOT;
            settingKey = "iron-settings";
        } else if (typeName.contains("IRON")) {
            if (displayName == null) displayName = "철";
            if (maxHp == -1) maxHp = IRON_MAX_HP;
            repairMat = Material.IRON_INGOT;
            settingKey = "iron-settings";
        } else if (b.getType() == Material.GOLD_BLOCK) {
            if (displayName == null) displayName = "자동 터렛";
            if (maxHp == -1) maxHp = IRON_MAX_HP; // 터렛은 철급 체력(200)
            repairMat = Material.IRON_INGOT;
            settingKey = "iron-settings";
        }
        else if (typeName.contains("IRON")) {
            if (displayName == null) displayName = "철";
            if (maxHp == -1) maxHp = IRON_MAX_HP; // 200
            repairMat = Material.IRON_INGOT;
            settingKey = "iron-settings";
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
            handleRepairWithSteel(player, master, activePath, maxHp, repairMat, useSteel, displayName, settingKey);
        }
        // 우클릭: 정보 확인 및 철거
        else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (player.isSneaking()) {
                // 변수 재할당 (자료형 제거됨)
                master = plugin.getMasterBlock(b);
                if (master == null) master = b;

                // 1. 자물쇠 엔티티 제거 (UUID 기반)
                plugin.removeLockEntity(master);

                // 터렛 처리
                if (b.getType() == Material.GOLD_BLOCK) {
                    plugin.turretManager.removeTurret(b.getLocation());
                    plugin.turretManager.dropTurretItems(b.getLocation());
                }

                // 2. 데이터 삭제 (activePath를 사용하면 문/블록 자동 구분)
                plugin.dataStorage.getConfig().set(activePath, null);
                plugin.dataStorage.saveConfig();

                // 3. 블록 물리적 제거
                if (isDoor) {
                    if (typeName.contains("BIG_DOOR") || typeName.contains("DUMMY")) {
                        plugin.removeBigObject(master); // 차고 문 전용 제거 로직
                    } else {
                        master.setType(Material.AIR);
                        master.getRelative(0, 1, 0).setType(Material.AIR);
                    }
                } else {
                    b.setType(Material.AIR); // 일반 블록 제거
                }

                player.sendMessage("§e[" + (displayName != null ? displayName : "구조물") + "]을(를) 철거했습니다.");
                player.playSound(b.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.0f, 0.8f);
            } else {
                // 정보 확인
                int currentHp = plugin.dataStorage.getConfig().getInt(activePath + ".health", maxHp);
                String progressBar = plugin.getProgressBar(currentHp, maxHp);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§f[" + displayName + "] " + progressBar + " §f(" + currentHp + " / " + maxHp + ")"));
            }
        }
    }
    private void removeLockEntity(Block master) {
        int x = master.getX();
        int y = master.getY();
        int z = master.getZ();

        // 생성할 때 넣었던 태그와 동일한 형식의 문자열 생성
        String lockTag = "lock_owner_" + x + "_" + y + "_" + z;

        // 문 주변(반경 2블록 내외)에서 해당 태그를 가진 ItemDisplay만 필터링하여 제거
        master.getWorld().getNearbyEntities(master.getLocation().add(0.5, 1.0, 0.5), 2.0, 2.0, 2.0).stream()
                .filter(ent -> ent instanceof org.bukkit.entity.ItemDisplay)
                .filter(ent -> ent.getScoreboardTags().contains(lockTag)) // 정확한 좌표 태그 확인
                .forEach(org.bukkit.entity.Entity::remove);
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

    private void handleRepairWithSteel(Player player, Block b, String pKey, int maxHp, Material repairMat, boolean useSteel, String displayName, String settingKey) {
        int currentHp = plugin.dataStorage.getConfig().getInt(pKey + ".health", maxHp);
        int actualMaxHp = plugin.dataStorage.getConfig().getInt(pKey + ".max_health", maxHp);
        int repairAmt = plugin.getConfig().getInt(settingKey + ".repair-amount", 10);
        int repairCost = plugin.getConfig().getInt(settingKey + ".repair-cost", 1);

        if (currentHp >= actualMaxHp) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§a[" + displayName + "] 체력이 가득 차 있습니다."));
            return;
        }

        // 재료 확인 분기
        boolean hasMat;
        if (useSteel) {
            hasMat = hasCustomItem(player, "STEEL_INGOT", repairCost);
        } else {
            hasMat = (repairMat != null) ? plugin.hasItem(player, repairMat, repairCost) : plugin.hasEnoughPlanks(player, repairCost);
        }

        if (hasMat) {
            if (useSteel) removeCustomItem(player, "STEEL_INGOT", repairCost);
            else if (repairMat != null) plugin.removeItem(player, repairMat, repairCost);
            else plugin.removePlanks(player, repairCost);

            int newHp = Math.min(actualMaxHp, currentHp + repairAmt);
            plugin.dataStorage.getConfig().set(pKey + ".health", newHp);
            plugin.dataStorage.saveConfig();

            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText("§b[" + displayName + "] 수리 완료! " + plugin.getProgressBar(newHp, actualMaxHp)));
            player.playSound(b.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        } else {
            String matName = useSteel ? "강철 주괴" : (repairMat == Material.IRON_INGOT ? "철괴" : "나무판자");
            player.sendMessage("§c[" + displayName + "] 수리 재료 부족 (" + matName + " 필요)");
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