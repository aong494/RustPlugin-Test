package Alpa.test;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class PlankHealthManager implements Listener {

    private final main plugin;
    private static final Map<String, Integer> blockHealthMap = new HashMap<>();

    public PlankHealthManager(main plugin) {
        this.plugin = plugin;
    }

    public static String locToKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    // 매개변수를 (Block, String)으로 변경합니다.
    public static void applyBulletDamage(Block block, String gunId) {
        main plugin = (main) Bukkit.getPluginManager().getPlugin("Rust");
        if (plugin == null) return;

        // 1. 데미지 계산
        String configKey = gunId.replace(":", "_");
        int finalDamage = plugin.gunDamageConfig.getInt("guns." + configKey, 1);

        // 2. [핵심 수정] 직접 계산하지 말고 main의 applyPlankDmg를 호출합니다.
        // 이렇게 해야 터렛 이름, 체력 로직이 한 곳에서 통합 관리됩니다.
        plugin.applyPlankDmg(block, finalDamage, null);
    }
    private boolean isTargetBlock(Material material) {
        if (material == null) return false;
        // 1. 터렛 (골드 블록)
        if (material == Material.GOLD_BLOCK) return true;

        String name = material.name().toUpperCase();

        if (name.contains("BIG_DOOR") || name.contains("ARMORED_DOOR")) return true;

        // 2. 철제 구조물 (철 블록 및 철 계단 - 모드나 추가 재질 대비)
        boolean isIron = name.contains("IRON_BLOCK") || (name.contains("IRON") && name.contains("_STAIRS"));

        // 3. 석재 구조물 (석재 벽돌 및 석재 계단)
        // contains("STONE_BRICK")을 사용하면 STAIRS도 한 번에 잡힙니다.
        boolean isStone = name.contains("STONE_BRICK");

        // 4. 나무 판자/계단
        boolean isWoodPlank = name.contains("_PLANKS");
        // 석재, 철, 벽돌이 아닌 모든 계단은 나무 계단으로 간주
        boolean isWoodStair = name.contains("_STAIRS") && !isStone && !isIron && !name.contains("BRICK") && !name.contains("COBBLE");

        return isIron || isStone || isWoodPlank || isWoodStair;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlankPlace(BlockPlaceEvent e) {
        Block b = e.getBlock();
        Material type = b.getType();
        if (!isTargetBlock(type)) return;

        String key = plugin.blockToKey(b);
        String path = "planks." + key;
        String typeName = type.name();

        int initialHp;
        String displayName;

        // --- [이름 및 체력 판별 로직 수정] ---
        if (typeName.contains("ARMORED_DOOR")) {
            initialHp = 800; // 무장 문 체력 설정
            displayName = "합금 문";
        } else if (typeName.contains("BIG_DOOR")) {
            initialHp = 600; // 차고 문(Big Door) 체력 설정
            displayName = "차고 문";
        } else if (type == Material.GOLD_BLOCK) {
            // 기존 터렛 로직...
            initialHp = plugin.turretConfig.getInt("settings.max_health", 200);
            displayName = plugin.turretConfig.getString("settings.display_name", "자동 터렛");
        }else if (typeName.contains("IRON")) {
            initialHp = 200;
            displayName = "철제 구조물";
        } else if (typeName.contains("STONE_BRICK")) {
            initialHp = 100;
            displayName = "석재 구조물";
        } else {
            initialHp = 50;
            displayName = "나무 구조물";
        }

        // data.yml에 저장
        plugin.dataStorage.getConfig().set(path + ".health", initialHp);
        plugin.dataStorage.getConfig().set(path + ".max_health", initialHp);
        plugin.dataStorage.getConfig().set(path + ".display_name", displayName); // 여기서 "철제", "석재" 등이 저장됨
        plugin.dataStorage.getConfig().set(path + ".owner", e.getPlayer().getUniqueId().toString());

        plugin.dataStorage.saveConfig();
    }
    // 상호작용 로직
    @EventHandler
    public void onPlankInteract(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block b = e.getClickedBlock();
        if (b == null) return;

        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        // 막대기를 들고 있을 때만 정보 표시
        if (p.getInventory().getItemInMainHand().getType() == Material.STICK) {
            String rawKey = plugin.blockToKey(b);
            String path = "planks." + rawKey;

            // 데이터가 없는 블록이면 무시
            if (!plugin.dataStorage.getConfig().contains(path)) return;

            e.setCancelled(true);

            // 1. [핵심] 실시간으로 실제 블록 재질 판별 (좌클릭 로직과 동일하게)
            Material type = b.getType();
            String typeName = type.name().toUpperCase();

            String correctName;
            int correctMax;

            if (type == Material.GOLD_BLOCK) {
                correctName = "자동 터렛";
                correctMax = 200;
            } else if (typeName.contains("IRON_BLOCK")) {
                correctName = "철제 구조물";
                correctMax = 200;
            } else if (typeName.contains("STONE_BRICKS")) {
                correctName = "석재 구조물";
                correctMax = 100;
            } else {
                correctName = "나무 구조물";
                correctMax = 50;
            }

            // 2. [중요] 파일에 저장된 정보가 '진짜 재질'과 다르면 그 자리에서 즉시 수정
            String savedName = plugin.dataStorage.getConfig().getString(path + ".display_name");
            int savedMax = plugin.dataStorage.getConfig().getInt(path + ".max_health", -1);

            if (!correctName.equals(savedName) || correctMax != savedMax) {
                plugin.dataStorage.getConfig().set(path + ".display_name", correctName);
                plugin.dataStorage.getConfig().set(path + ".max_health", correctMax);
                plugin.dataStorage.saveConfig(); // 파일에 즉시 저장
            }

            // 3. 현재 체력 가져오기
            int currentHp = plugin.dataStorage.getConfig().getInt(path + ".health", correctMax);

            // 4. 액션바 출력 (이제 우클릭만 해도 정확한 이름이 뜹니다)
            String progressBar = plugin.getProgressBar(currentHp, correctMax);
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText("§f[" + correctName + "] " + progressBar + " §e" + currentHp + "§7/§e" + correctMax));
        }
    }
    private String getProgressBar(int current, int max) {
        int bars = 10;
        int completedBars = (int) (((double) current / max) * bars);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            if (i < completedBars) sb.append("§a■");
            else sb.append("§7■");
        }
        return sb.toString();
    }

    // 3. 파괴 시 체력 차감 로직 (모두에게 적용)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlankBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!isTargetBlock(b.getType())) return;

        // dataStorage에서 키 확인
        String blockKey = plugin.blockToKey(b);
        if (!plugin.dataStorage.getConfig().contains("planks." + blockKey)) return;

        Player p = e.getPlayer();
        e.setCancelled(true); // 블록 파괴 취소 (체력 로직으로 전환)

        // 데미지 설정 (설정값은 config.yml 유지)
        String toolName = p.getInventory().getItemInMainHand().getType().name();
        int dmg = plugin.getConfig().getInt("damage-settings." + toolName,
                plugin.getConfig().getInt("damage-settings.DEFAULT", 1));

        plugin.applyPlankDmg(b, dmg, p);
    }
    // --- 권한 확인 로직 ---
    private boolean hasPermission(Player player, String configKey) {
        if (player.isOp()) return true; // 관리자는 무조건 허용

        String ownerUUID = plugin.getConfig().getString(configKey + ".owner");
        if (ownerUUID == null) return true; // 주인 정보가 없으면 공용으로 간주

        // 1. 본인인지 확인
        if (player.getUniqueId().toString().equals(ownerUUID)) return true;

        // 2. 주인의 그룹과 플레이어의 그룹이 같은지 확인
        String ownerGroup = plugin.groupManager.getGroupViaUUID(ownerUUID); // 주인의 그룹 조회
        String playerGroup = plugin.groupManager.getPlayerGroup(player);   // 나의 그룹 조회

        return ownerGroup != null && ownerGroup.equals(playerGroup);
    }
}