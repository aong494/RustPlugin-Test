package Alpa.test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.*;
import java.util.*;

public class MainCommandExecutor implements CommandExecutor {
    private final main plugin;

    public MainCommandExecutor(main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;

        // 1. 스포너설정
        if (label.equalsIgnoreCase("스포너설정")) {
            return handleSpawnerSetting(player, args);
        }

        // 2. 레이드박스
        if (label.equalsIgnoreCase("레이드박스")) {
            return handleRaidBox(player, args);
        }

        // 3. 보온설정
        if (label.equalsIgnoreCase("보온설정")) {
            return handleInsulation(player, args);
        }

        // 4. 방호설정
        if (label.equalsIgnoreCase("방호설정")) {
            return handleRadiation(player, args);
        }

        // 5. 스폰설정
        if (label.equalsIgnoreCase("스폰설정")) {
            if (!player.isOp()) return true;
            plugin.respawnManager.addLocation(player.getLocation());
            player.sendMessage("§a[System] §f현재 위치를 랜덤 리스폰 지점으로 등록했습니다.");
            return true;
        }

        // 6. 상점 (새로 추가하려는 GUI)
        if (label.equalsIgnoreCase("상점")) {
            String shopName = (args.length > 0) ? args[0] : "default"; // 이름 없으면 default 상점
            plugin.shopManager.openShop(player, shopName);
            return true;
        }
        // --- 상점 등록 부분 ---
        if (label.equalsIgnoreCase("상점등록")) {
            if (args.length < 2) {
                player.sendMessage("§c[사용법] /상점등록 [지도이름] [상점ID]");
                return true;
            }

            Location loc = player.getLocation();
            String displayName = args[0]; // 지도에 뜰 이름
            String shopID = args[1];      // yml에 저장된 ID (minerals, horseshop 등)

            // 1. yml에서 아이템 데이터 파싱
            String parsedItems = getItemsByShopID(shopID);

            if (parsedItems.equals("none")) {
                player.sendMessage("§c[오류] '" + shopID + "' ID를 shop.yml에서 찾을 수 없습니다.");
                return true;
            }

            // 2. 지도 위치 정보 저장 (추후 서버 재시작 시 동기화용)
            String path = "map_locations." + displayName;
            plugin.shopConfig.set(path + ".x", (float)loc.getX());
            plugin.shopConfig.set(path + ".z", (float)loc.getZ());
            plugin.shopConfig.set(path + ".items", parsedItems); // 파싱된 아이템 목록 저장

            try {
                plugin.shopConfig.save(new File(plugin.getDataFolder(), "shop.yml"));
            } catch (IOException e) { e.printStackTrace(); }

            // 3. 패킷 전송
            String payload = "SHOP_SYNC|" + (float)loc.getX() + "|" + (float)loc.getZ() + "|" + displayName + "|" + parsedItems;
            byte[] message = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.sendPluginMessage(this.plugin, "examplemod:shop_data", message);
            }

            player.sendMessage("§a[Shop] §f'" + displayName + "' 상점이 등록되었습니다.");
            return true;
        }
        // --- 상점 삭제 부분---
        if (label.equalsIgnoreCase("상점삭제")) {
            if (args.length < 1) {
                player.sendMessage("§c[사용법] /상점삭제 [상점이름]");
                return true;
            }

            String shopName = args[0];

            // 1. 서버 파일(shop.yml)에서 데이터 삭제
            if (plugin.shopConfig.contains("map_locations." + shopName)) {
                plugin.shopConfig.set("map_locations." + shopName, null);
                try {
                    plugin.shopConfig.save(new File(plugin.getDataFolder(), "shop.yml"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // 2. 패킷 전송 (기존과 동일)
            String payload = "SHOP_REMOVE|0|0|" + shopName;
            byte[] message = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                onlinePlayer.sendPluginMessage(this.plugin, "examplemod:shop_data", message);
            }

            player.sendMessage("§e[Shop] §f'" + shopName + "' 데이터를 서버와 지도에서 제거했습니다.");
            return true;
        }
        // 7. 그룹 명령어 처리
        if (label.equalsIgnoreCase("그룹")) {
            return handleGroupCommand(player, args);
        }

        return false;
    }
    private String getItemsByShopID(String shopID) {
        ConfigurationSection itemsSection = plugin.shopConfig.getConfigurationSection("shops." + shopID + ".items");
        if (itemsSection == null) return "none";

        List<String> itemList = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            String itemKey = String.valueOf(i);
            String itemName = itemsSection.getString(itemKey + ".item");

            if (itemName != null) {
                // JEG_SCRAP 같은 경우 만약 모드 ID가 'jeg'라면 'jeg:scrap'으로 보정
                // 대문자를 소문자로 바꾸고, 콜론이 없으면 적절한 처리가 필요함
                String formattedName = itemName.toLowerCase();

                // 만약 특정 모드 아이템인데 콜론이 없다면 강제로 붙여주는 예외 처리
                if (!formattedName.contains(":") && formattedName.contains("jeg")) {
                    formattedName = "jeg:" + formattedName.replace("jeg_", "");
                }

                itemList.add(formattedName);
            } else {
                itemList.add("air");
            }
        }
        return String.join(",", itemList);
    }
    private boolean handleSpawnerSetting(Player player, String[] args) {
        if (!player.isOp()) return true;
        if (args.length < 2) {
            player.sendMessage("§c사용법: /스포너설정 <이름> <재소환시간(초)>");
            return true;
        }

        String mobName = args[0];
        int interval;
        try {
            interval = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c[System] 시간은 숫자로 입력해주세요.");
            return true;
        }

        Location l = player.getLocation();
        File file = new File(plugin.getDataFolder(), "custom_spawners.json");
        Map<String, Object> config = new HashMap<>();

        if (file.exists() && file.length() > 0) {
            try (Reader reader = new FileReader(file)) {
                Map<String, Object> tempConfig = new Gson().fromJson(reader, new TypeToken<Map<String, Object>>(){}.getType());
                if (tempConfig != null) config = tempConfig;
            } catch (IOException e) { e.printStackTrace(); }
        }

        if (!config.containsKey(mobName)) {
            Map<String, Object> mobInfo = new HashMap<>();
            mobInfo.put("type", "husk");
            mobInfo.put("hp", 40);
            mobInfo.put("speed", 0.1);
            mobInfo.put("weapon", "jeg:assault_rifle".toLowerCase());

            Map<String, String> armor = new HashMap<>();
            armor.put("helmet", "lrarmor:defender_helmet".toLowerCase());
            armor.put("chestplate", "lrarmor:defender_chestplate".toLowerCase());
            armor.put("leggings", "lrarmor:defender_leggings".toLowerCase());
            armor.put("boots", "lrarmor:defender_boots".toLowerCase());
            mobInfo.put("armor", armor);

            List<Map<String, Object>> drops = new ArrayList<>();
            Map<String, Object> drop1 = new HashMap<>();
            drop1.put("item", "jeg:rifle_ammo");
            drop1.put("chance", 100);
            drop1.put("amount", 1);
            drops.add(drop1);
            mobInfo.put("custom-drops", drops);
            mobInfo.put("spawns", new ArrayList<Map<String, Object>>());
            config.put(mobName, mobInfo);
        }

        Map<String, Object> mobInfo = (Map<String, Object>) config.get(mobName);
        List<Map<String, Object>> spawnList = (List<Map<String, Object>>) mobInfo.get("spawns");
        Map<String, Object> newSpawn = new HashMap<>();
        newSpawn.put("id", (mobName + "_" + (spawnList.size() + 1)).toLowerCase());
        newSpawn.put("interval", interval);
        newSpawn.put("x", Math.round(l.getX() * 100.0) / 100.0);
        newSpawn.put("y", Math.round(l.getY() * 100.0) / 100.0);
        newSpawn.put("z", Math.round(l.getZ() * 100.0) / 100.0);
        spawnList.add(newSpawn);

        try (Writer writer = new FileWriter(file)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(config, writer);
            player.sendMessage("§a[System] §f" + mobName + " 좌표 등록 완료!");
        } catch (IOException e) { e.printStackTrace(); }
        return true;
    }

    private boolean handleRaidBox(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage("§c[System] 관리자 전용 명령어입니다.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§c사용법: /레이드박스 <설치|확인|삭제|드랍템|장비> <이름>");
            return true;
        }

        String action = args[0];
        String raidName = args[1];

        switch (action.toLowerCase()) {
            case "설치" -> {
                Location loc = player.getLocation().getBlock().getLocation();
                if (plugin.raidManager.getConfig().contains("raidboxes." + raidName)) {
                    plugin.raidManager.createInstance(loc, raidName);
                    player.sendMessage("§a[Raid] §f" + raidName + " 레이드 박스를 설치했습니다.");
                } else {
                    player.sendMessage("§c[System] raidbox.yml에 '" + raidName + "' 정보가 없습니다.");
                }
            }
            case "확인" -> {
                org.bukkit.block.Block targetBlock = player.getTargetBlock(null, 5);
                RaidManager.RaidInstance instance = plugin.raidManager.getInstance(targetBlock.getLocation());
                if (instance != null) {
                    player.sendMessage("§6--- [" + instance.getTemplateName() + "] 정보 ---");
                    player.sendMessage("§f상태: §e" + instance.getState());
                } else {
                    player.sendMessage("§c[System] 바라보고 있는 위치에 설치된 레이드 박스가 없습니다.");
                }
            }
            case "삭제" -> {
                if (plugin.raidManager.removeInstance(raidName)) player.sendMessage("§e[Raid] 삭제 완료.");
                else player.sendMessage("§c[Raid] 찾을 수 없음.");
            }
            case "드랍템" -> {
                if (args.length < 3) return true;
                plugin.raidManager.addHandItemToDrops(player, raidName, Integer.parseInt(args[2]));
            }
            case "장비" -> {
                if (args.length < 3) return true;
                plugin.raidManager.setHandItemToEquipment(player, raidName, args[2].toLowerCase());
            }
        }
        return true;
    }

    private boolean handleGroupCommand(Player player, String[] args) {
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("멤버")) {
                plugin.openGroupGUI(player);
                return true;
            }
            if (args[0].equalsIgnoreCase("나가기")) { plugin.groupManager.leaveGroup(player); return true; }
            if (args[0].equalsIgnoreCase("수락")) { plugin.groupManager.acceptInvite(player); return true; }
        }

        if (args.length < 2) {
            player.sendMessage(new String[]{"§6--- 그룹 도움말 ---", "/그룹 생성 [이름]", "/그룹 초대 [닉네임]", "/그룹 수락", "/그룹 나가기", "/그룹 멤버", "/그룹 삭제 [이름]"});
            return true;
        }

        String action = args[0];
        String param = args[1];

        if (action.equalsIgnoreCase("생성")) {
            if (plugin.groupManager.hasGroup(player)) player.sendMessage("§c이미 그룹에 속해 있습니다.");
            else plugin.groupManager.createGroup(param, player);
        } else if (action.equalsIgnoreCase("초대")) {
            String groupName = plugin.groupManager.getPlayerGroup(player);
            if (groupName != null && plugin.groupManager.isGroupOwner(groupName, player)) {
                Player target = Bukkit.getPlayer(param);
                if (target != null) plugin.groupManager.invitePlayer(target, groupName);
            }
        } else if (action.equalsIgnoreCase("삭제")) {
            if (player.isOp() || plugin.groupManager.isGroupOwner(param, player)) plugin.groupManager.deleteGroup(param);
        }
        return true;
    }

    private boolean handleInsulation(Player player, String[] args) {
        if (!player.isOp() || args.length != 1) return true;
        try {
            int level = Integer.parseInt(args[0]);
            plugin.setInsulationTag(player, level);
        } catch (Exception e) { player.sendMessage("§c숫자를 입력하세요."); }
        return true;
    }

    private boolean handleRadiation(Player player, String[] args) {
        if (!player.isOp() || args.length != 1) return true;
        try {
            int level = Integer.parseInt(args[0]);
            plugin.setRadiationTag(player, level);
        } catch (Exception e) { player.sendMessage("§c숫자를 입력하세요."); }
        return true;
    }
}