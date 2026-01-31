package Alpa.test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;


public final class main extends JavaPlugin implements Listener, CommandExecutor {

    private CraftManager craftManager;
    public BedManager bedManager;
    public GroupManager groupManager;
    private final int MAX_HP = 100;
    private TemperatureManager tempManager;
    private RadiationManager radManager;
    public RespawnManager respawnManager;
    public BlockRegenManager blockRegenManager;
    public InteractBlockManager interactBlockManager;
    public BlockListener blockListener;
    private final java.util.Set<String> processingLocations = new java.util.HashSet<>();
    public RaidManager raidManager;
    public RouletteManager rouletteManager;
    public RouletteListener rouletteListener;
    public DataManager dataStorage;
    private final java.util.Map<java.util.UUID, Long> breakCooldown = new java.util.HashMap<>();
    public File spawnerFile;
    public org.bukkit.configuration.file.FileConfiguration spawnerConfig;
    public ElectricManager electricManager;
    public PlankHealthManager plankHealthManager;
    public File gunDamageFile;
    public org.bukkit.configuration.file.FileConfiguration gunDamageConfig;
    public TurretManager turretManager;
    public FileConfiguration turretConfig;
    private File turretFile;
    public FileConfiguration maintenanceConfig;
    private File maintenanceFile;
    public BlueprintManager blueprintManager;

    @Override
        public void onEnable() {
            createTurretConfig();
            createGunDamageConfig();
            this.groupManager = new GroupManager(this);
            this.bedManager = new BedManager(this);
            this.craftManager = new CraftManager(this);
            this.tempManager = new TemperatureManager(this);
            this.radManager = new RadiationManager(this);
            this.respawnManager = new RespawnManager(this);
            this.blockRegenManager = new BlockRegenManager(this);
            this.interactBlockManager = new InteractBlockManager(this);
            this.blockListener = new BlockListener(this);
            this.raidManager = new RaidManager(this);
            this.rouletteManager = new RouletteManager(this);
            this.rouletteListener = new RouletteListener(this, this.rouletteManager);
            this.dataStorage = new DataManager(this);
            this.electricManager = new ElectricManager(this);
            this.spawnerFile = new File(getDataFolder(), "custom_spawners.yml");
            this.spawnerConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(spawnerFile);
            this.plankHealthManager = new PlankHealthManager(this);
            this.turretManager = new TurretManager(this);
            maintenanceFile = new File(getDataFolder(), "maintenance.yml");
            if (!maintenanceFile.exists()) saveResource("maintenance.yml", false);
            maintenanceConfig = YamlConfiguration.loadConfiguration(maintenanceFile);
            OilRefineryManager refineryManager = new OilRefineryManager(this);
            this.blueprintManager = new BlueprintManager(this);

            addDefaultSettings();
        org.bukkit.plugin.PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(this.bedManager, this);
        pm.registerEvents(this.plankHealthManager, this); // 체력 매니저 등록
        pm.registerEvents(this.craftManager, this);
        pm.registerEvents(this.electricManager, this);

        // 3. 기타 매니저 등록 (인스턴스 생성이 필요한 것들)
        pm.registerEvents(new PlankToIronManager(this), this);
        pm.registerEvents(new JoinManager(this), this);
        pm.registerEvents(new EnvironmentManager(this), this);
        pm.registerEvents(new ReinforcedManager(this), this);
        pm.registerEvents(new RespawnListener(this), this);
        pm.registerEvents(new InteractBlockListener(this), this);
        pm.registerEvents(new BlockListener(this), this);
        pm.registerEvents(new RaidListener(this), this);
        pm.registerEvents(new BlockDrops(this), this);
        pm.registerEvents(new MobListener(), this);
        pm.registerEvents(new RouletteListener(this, this.rouletteManager), this);
        pm.registerEvents(new DoorManager(this), this);
        pm.registerEvents(this, this);
        pm.registerEvents(this.turretManager, this);
        pm.registerEvents(refineryManager, this);
        pm.registerEvents(new FurnaceListener(), this);

        if (!getConfig().contains("stone-settings")) {
            getConfig().set("stone-settings.repair-amount", 20); // 석재 수리량
            getConfig().set("stone-settings.repair-cost", 1);    // 수리 비용
        }
        // 1. 플러그인 데이터 폴더 확인 및 생성
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // 2. custom_spawners.yml 파일 처리
        File spawnerFile = new File(getDataFolder(), "custom_spawners.yml");

        if (!spawnerFile.exists()) {
            try {
                // JAR 내부에서 복사하는 대신 빈 파일을 물리적으로 생성 (가장 안전함)
                spawnerFile.createNewFile();
                getLogger().info("custom_spawners.yml 파일이 없어서 새로 생성했습니다.");
            } catch (IOException e) {
                getLogger().severe("파일 생성 중 오류 발생: " + e.getMessage());
            }
        }
        if (getCommand("스포너설정") != null) getCommand("스포너설정").setExecutor(this);

        getCommand("보온설정").setExecutor(this);

        // config 설정 로직
        if (!getConfig().contains("decay-settings.interval-seconds")) {
            getConfig().set("decay-settings.interval-seconds", 10);
            saveConfig();
        }

        long intervalSeconds = getConfig().getLong("decay-settings.interval-seconds", 10L);
        long intervalTicks = intervalSeconds * 20L;

        new BlockDecayManager(this).runTaskTimer(this, intervalTicks, intervalTicks);

        Bukkit.getLogger().info("[System] 체력 감소 시스템이 " + intervalSeconds + "초 주기로 시작되었습니다.");

        // 명령어 등록
        if (getCommand("그룹") != null) getCommand("그룹").setExecutor(this);
        if (getCommand("침대") != null) getCommand("침대").setExecutor(this.bedManager);
        if (getCommand("제작") != null) {
            getCommand("제작").setExecutor((sender, command, label, args) -> {
                if (sender instanceof Player) craftManager.openMainMenu((Player) sender);
                return true;
            });
        }

        // 스코어보드 업데이트 스케줄러
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateGroupScoreboard(player);
            }
        }, 0L, 20L);
        restoreBlocksAfterReboot();
    }

    public void createTurretConfig() {
        turretFile = new File(getDataFolder(), "turret.yml");
        if (!turretFile.exists()) {
            saveResource("turret.yml", false);
        }
        turretConfig = YamlConfiguration.loadConfiguration(turretFile);
    }

    private void createGunDamageConfig() {
        gunDamageFile = new File(getDataFolder(), "gun_blockdamage.yml");
        if (!gunDamageFile.exists()) {
            // jar 안에 파일이 없다면 새로 생성
            try {
                gunDamageFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        gunDamageConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(gunDamageFile);

        if (!gunDamageConfig.contains("guns")) {
            gunDamageConfig.set("guns.jeg_assault_rifle", 15); // 콜론(:) 대신 언더바(_) 권장 (YAML 키 오류 방지)
            gunDamageConfig.set("guns.jeg_pistol", 5);
            gunDamageConfig.set("guns.jeg_sniper_rifle", 50);
            try { gunDamageConfig.save(gunDamageFile); } catch (Exception e) { e.printStackTrace(); }
        }
    }

    public RaidManager getRaidManager() {
        return raidManager;
    }

    @Override
    public void onDisable() {
        if (blockRegenManager != null) {
            blockRegenManager.savePending();
        }

        // 모든 인스턴스의 홀로그램과 태스크를 정리
        if (this.raidManager != null) {
            this.raidManager.cleanup();
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof ArmorStand && entity.getScoreboardTags().contains("raid_hologram")) {
                        if (!entity.getScoreboardTags().contains("kubejs_managed")) {
                            entity.remove();
                        }
                    }
                }
            }
        }
        if (this.electricManager != null) {
            this.electricManager.saveBlocks();
            getLogger().info("[Electric] 전력 시스템 데이터가 저장되었습니다.");
        }
        getLogger().info("플러그인이 비활성화되었습니다.");
    }

    private void updateGroupScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();

        // 그룹이 있으면 그룹명을, 없으면 기본 타이틀을 표시
        String groupName = groupManager.getPlayerGroup(player);
        String title = (groupName != null) ? "§6§l[ " + groupName + " ]" : "§b§l[ 개인 정보 ]";

        Objective obj = board.registerNewObjective("Info", "dummy", title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // 1. 체온 표시 (항상 보임)
        double temp = tempManager.getTemp(player);
        String tempColor = (temp >= 38.5) ? "§c" : (temp <= 36.0) ? "§b" : "§f";
        obj.getScore("§e체온: " + tempColor + temp + "°C").setScore(99);

        // 2. 방사능 표시 (항상 보임)
        double rad = radManager.getRad(player);
        String radColor = (rad >= 70) ? "§4" : (rad >= 40) ? "§6" : "§a";
        obj.getScore("§2방사능: " + radColor + rad + " mSv").setScore(98);

        // 구분선
        obj.getScore("§7----------------").setScore(97);

        // 3. 그룹 정보 표시 (그룹이 있을 때만 추가)
        if (groupName != null) {
            List<String> memberUUIDs = groupManager.getGroupMembers(groupName);
            int sortValue = 96;

            for (String uuidStr : memberUUIDs) {
                Player member = Bukkit.getPlayer(UUID.fromString(uuidStr));
                String displayName;
                if (member != null && member.isOnline()) {
                    displayName = "§a" + member.getName();
                } else {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
                    displayName = "§7" + (op.getName() != null ? op.getName() : "Unknown");
                }
                obj.getScore(displayName).setScore(sortValue--);
            }
        } else {
            // 그룹이 없을 때 안내 메시지 (선택 사항)
            obj.getScore("§7가입된 그룹 없음").setScore(96);
        }

        player.setScoreboard(board);
    }

    private void addDefaultSettings() {
        // [추가] 판자 회복량 설정
        if (!getConfig().contains("plank-settings")) {
            getConfig().set("plank-settings.repair-amount", 10); // 판자 1개당 회복될 체력
            getConfig().set("plank-settings.repair-cost", 1);   // 수리 1회당 소모되는 판자 개수
        }
        if (!getConfig().contains("explosion-settings")) {
            getConfig().set("explosion-settings.rocket_launcher", 50);
            getConfig().set("explosion-settings.explosive_charge", 100);
            getConfig().set("explosion-settings.grenade", 30);
        }
        getConfig().addDefault("settings.piston-enabled", true);
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;
        if (label.equalsIgnoreCase("스포너설정")) {
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
            File file = new File(getDataFolder(), "custom_spawners.json");
            Map<String, Object> config = new HashMap<>();

            // 1. 기존 데이터 읽기 (안전한 방식)
            if (file.exists() && file.length() > 0) {
                try (Reader reader = new FileReader(file)) {
                    Map<String, Object> tempConfig = new Gson().fromJson(reader, new TypeToken<Map<String, Object>>(){}.getType());
                    if (tempConfig != null) {
                        config = tempConfig;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // 2. 해당 이름의 몹 섹션이 없으면 기본 정보 생성
            if (!config.containsKey(mobName)) {
                Map<String, Object> mobInfo = new HashMap<>();
                mobInfo.put("type", "husk"); // 소문자로 변경
                mobInfo.put("hp", 40);
                mobInfo.put("speed", 0.1);
                mobInfo.put("weapon", "jeg:assault_rifle".toLowerCase()); // 소문자로 저장

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

            // 3. 데이터 캐스팅 및 좌표 추가
            Map<String, Object> mobInfo = (Map<String, Object>) config.get(mobName);
            List<Map<String, Object>> spawnList = (List<Map<String, Object>>) mobInfo.get("spawns");

            Map<String, Object> newSpawn = new HashMap<>();
            newSpawn.put("id", (mobName + "_" + (spawnList.size() + 1)).toLowerCase());
            newSpawn.put("interval", interval);
            newSpawn.put("x", Math.round(l.getX() * 100.0) / 100.0);
            newSpawn.put("y", Math.round(l.getY() * 100.0) / 100.0);
            newSpawn.put("z", Math.round(l.getZ() * 100.0) / 100.0);
            spawnList.add(newSpawn);

            // 4. 저장
            try (Writer writer = new FileWriter(file)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(config, writer);
                player.sendMessage("§a[System] §f" + mobName + " 좌표 등록 완료!");
            } catch (IOException e) { e.printStackTrace(); }
            return true;
        }
        if (label.equalsIgnoreCase("레이드박스")) {
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

            if (action.equalsIgnoreCase("설치")) {
                Location loc = player.getLocation().getBlock().getLocation();
                if (this.raidManager.getConfig().contains("raidboxes." + raidName)) {
                    this.raidManager.createInstance(loc, raidName);
                    player.sendMessage("§a[Raid] §f" + raidName + " 레이드 박스를 설치했습니다.");
                } else {
                    player.sendMessage("§c[System] raidbox.yml에 '" + raidName + "' 정보가 없습니다.");
                }
            }
            else if (action.equalsIgnoreCase("확인")) {
                org.bukkit.block.Block targetBlock = player.getTargetBlock(null, 5);
                RaidManager.RaidInstance instance = this.raidManager.getInstance(targetBlock.getLocation());

                if (instance != null) {
                    player.sendMessage("§6--- [" + instance.getTemplateName() + "] 정보 ---");
                    player.sendMessage("§f상태: §e" + instance.getState());
                    if (instance.getState() == RaidManager.RaidState.RUNNING) {
                        player.sendMessage("§c남은 시간: §f" + instance.getTimeLeft() + "초");
                    } else if (instance.getState() == RaidManager.RaidState.COOLDOWN) {
                        player.sendMessage("§b남은 쿨타임: §f" + instance.getCooldownRemain() + "초");
                    }
                } else {
                    player.sendMessage("§c[System] 바라보고 있는 위치에 설치된 레이드 박스가 없습니다.");
                }
            }
            else if (action.equalsIgnoreCase("삭제")) {
                if (this.raidManager.removeInstance(raidName)) {
                    player.sendMessage("§e[Raid] " + raidName + " 레이드 박스를 월드에서 삭제했습니다.");
                } else {
                    player.sendMessage("§c[Raid] 해당 이름으로 설치된 박스를 찾을 수 없습니다.");
                }
            }
            else if (action.equalsIgnoreCase("드랍템")) {
                if (args.length < 3) {
                    player.sendMessage("§c사용법: /레이드박스 드랍템 <박스이름> <확률(0-100)>");
                    return true;
                }
                try {
                    int chance = Integer.parseInt(args[2]);
                    this.raidManager.addHandItemToDrops(player, raidName, chance);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c[System] 확률은 숫자로 입력해주세요.");
                }
            }
            else if (action.equalsIgnoreCase("장비")) {
                if (args.length < 3) {
                    player.sendMessage("§c사용법: /레이드박스 장비 <박스이름> <부위>");
                    player.sendMessage("§7(부위: weapon, helmet, chest, legs, boots)");
                    return true;
                }
                String slot = args[2].toLowerCase();
                this.raidManager.setHandItemToEquipment(player, raidName, slot);
            }

            return true;
        }
        if (label.equalsIgnoreCase("보온설정")) {
            if (!player.isOp()) {
                player.sendMessage("§c[System] 관리자만 사용할 수 있는 명령어입니다.");
                return true;
            }

            if (args.length != 1) {
                player.sendMessage("§c[System] 사용법: /보온설정 [수치]");
                return true;
            }

            try {
                int level = Integer.parseInt(args[0]);
                setInsulationTag(player, level);
            } catch (NumberFormatException e) {
                player.sendMessage("§c[System] 숫자를 입력해주세요. (예: /보온설정 5)");
            }
            return true;
        }
        if (label.equalsIgnoreCase("방호설정")) {
            if (!player.isOp()) {
                player.sendMessage("§c[System] 관리자만 사용할 수 있는 명령어입니다.");
                return true;
            }
            if (args.length != 1) {
                player.sendMessage("§c[System] 사용법: /방호설정 [수치]");
                return true;
            }
            try {
                int level = Integer.parseInt(args[0]);
                setRadiationTag(player, level);
            } catch (NumberFormatException e) {
                player.sendMessage("§c[System] 숫자를 입력해주세요.");
            }
            return true;
        }

        if (label.equalsIgnoreCase("스폰설정")) {
            if (!player.isOp()) return true;

            respawnManager.addLocation(player.getLocation());
            player.sendMessage("§a[System] §f현재 위치를 랜덤 리스폰 지점으로 등록했습니다.");
            return true;
        }

        // 1. 인자 1개 명령어 (멤버, 나가기, 수락)
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("멤버")) {
                openGroupGUI(player);
                return true;
            }
            if (args[0].equalsIgnoreCase("나가기")) {
                if (groupManager.leaveGroup(player)) {
                    player.sendMessage("§e[Group] 그룹에서 퇴장했습니다.");
                }
                return true;
            }
            if (args[0].equalsIgnoreCase("수락")) {
                if (groupManager.acceptInvite(player)) {
                    player.sendMessage("§a[Group] 초대를 수락하여 그룹에 가입되었습니다.");
                } else {
                    player.sendMessage("§c[Group] 받은 초대가 없거나 만료되었습니다.");
                }
                return true;
            }
        }
        // 2. 도움말 출력 (인자가 부족할 때)
        if (args.length < 2) {
            String[] helpMessages = {
                    "§6--- 그룹 시스템 도움말 ---",
                    "§f/그룹 생성 [이름]: 새 그룹을 만듭니다.",
                    "§f/그룹 초대 [닉네임]: 플레이어를 초대합니다.",
                    "§f/그룹 수락: 받은 초대를 승인합니다.",
                    "§f/그룹 나가기: 그룹에서 탈퇴합니다.",
                    "§f/그룹 멤버: 그룹원 목록을 확인합니다.",
                    "§c/그룹 삭제 [이름]: 그룹을 삭제합니다."
            };
            player.sendMessage(helpMessages);
            return true;
        }

        // 3. 인자 2개 이상 명령어 처리
        String action = args[0];
        String param = args[1]; // 여기서 param으로 정의했습니다.

        if (action.equalsIgnoreCase("생성")) {
            if (groupManager.hasGroup(player)) {
                player.sendMessage("§c이미 그룹에 속해 있습니다.");
                return true;
            }
            groupManager.createGroup(param, player);
            player.sendMessage("§a그룹 '" + param + "'을(를) 생성했습니다.");
        }
        else if (action.equalsIgnoreCase("초대")) {
            String groupName = groupManager.getPlayerGroup(player);
            if (groupName == null) {
                player.sendMessage("§c소속된 그룹이 없습니다.");
                return true;
            }

            if (!groupManager.isGroupOwner(groupName, player)) {
                player.sendMessage("§c그룹장만 초대할 수 있습니다.");
                return true;
            }

            Player target = Bukkit.getPlayer(param);
            if (target == null || !target.isOnline()) {
                player.sendMessage("§c대상을 찾을 수 없거나 오프라인입니다.");
                return true;
            }

            groupManager.invitePlayer(target, groupName);
            player.sendMessage("§e" + target.getName() + "님을 초대했습니다.");
            target.sendMessage("§6[Group] " + player.getName() + "님이 '" + groupName + "' 그룹에 초대했습니다.");
            target.sendMessage("§6[Group] §f/그룹 수락§e 명령어로 가입하세요.");
        }
        else if (action.equalsIgnoreCase("삭제")) {
            // 수정됨: gName 대신 param을 사용해야 합니다.
            if (player.isOp() || groupManager.isGroupOwner(param, player)) {
                groupManager.deleteGroup(param);
                player.sendMessage("§e[Group] 그룹 '" + param + "'이(가) 삭제되었습니다.");
            } else {
                player.sendMessage("§c[Group] 그룹 삭제 권한이 없습니다 (주인만 가능).");
            }
        }
        return false;
    }
    public void saveSpawnerConfig() {
        try {
            if (spawnerConfig != null && spawnerFile != null) {
                spawnerConfig.save(spawnerFile);
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    public void setRadiationTag(Player player, int level) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == org.bukkit.Material.AIR) {
            player.sendMessage("§c[System] 손에 아이템을 들고 있어야 합니다.");
            return;
        }

        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(this, "radiation_protection");
            meta.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.INTEGER, level);

            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("§f방호 레벨= §6" + level);
            meta.setLore(lore);

            item.setItemMeta(meta);
        }

        player.sendMessage("§a[System] §f아이템에 방호 레벨 §6" + level + "§f을(를) 부여했습니다.");
    }

    public void setInsulationTag(Player player, int level) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == org.bukkit.Material.AIR) {
            player.sendMessage("§c[System] 손에 아이템을 들고 있어야 합니다.");
            return;
        }

        // editMeta 대신 getItemMeta 사용
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(this, "insulation");
            meta.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.INTEGER, level);

            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("§f보온 레벨= §e" + level);
            meta.setLore(lore);

            // 변경된 메타를 아이템에 다시 적용 (필수)
            item.setItemMeta(meta);
        }

        player.sendMessage("§a[System] §f아이템에 보온 레벨 §e" + level + "§f을(를) 부여했습니다.");
    }

    // GUI 생성 및 열기
    public void openGroupGUI(Player player) {
        String groupName = groupManager.getPlayerGroup(player);
        if (groupName == null) {
            player.sendMessage("§c소속된 그룹이 없습니다.");
            return;
        }

        List<String> memberUUIDs = groupManager.getGroupMembers(groupName);
        // 9의 배수로 크기 설정 (최대 54)
        int size = ((memberUUIDs.size() / 9) + 1) * 9;
        Inventory gui = Bukkit.createInventory(null, Math.min(size, 54), "§0그룹 멤버: " + groupName);

        for (String uuidStr : memberUUIDs) {
            UUID uuid = UUID.fromString(uuidStr);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();

            // 오프라인 플레이어 정보를 가져와 머리에 적용
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(op);
            meta.setDisplayName("§e" + (op.getName() != null ? op.getName() : "알 수 없는 유저"));

            List<String> lore = new ArrayList<>();
            lore.add("§7UUID: " + uuidStr);
            meta.setLore(lore);

            head.setItemMeta(meta);
            gui.addItem(head);
        }
        player.openInventory(gui);
    }

    public Block getBottom(Block b) {
        String typeName = b.getType().name().toUpperCase();
        // 바닐라 문/합금 문 (2칸 높이)
        if (typeName.contains("_DOOR")) {
            if (b.getBlockData() instanceof org.bukkit.block.data.type.Door door) {
                if (door.getHalf() == org.bukkit.block.data.type.Door.Half.TOP) {
                    return b.getRelative(0, -1, 0);
                }
            }
        }
        return b;
    }
    @EventHandler
    public void onDoorRedstone(org.bukkit.event.block.BlockRedstoneEvent e) {
        Block b = e.getBlock();

        if (b.getType().name().contains("_DOOR")) {
            Block bottom = getBottom(b);
            String key = "doors." + blockToKey(bottom);

            // [수정] dataStorage에 문 정보가 있는지 확인
            if (dataStorage.getConfig().contains(key)) {
                if (e.getNewCurrent() > 0) {
                    e.setNewCurrent(0);
                }
            }
        }
    }
    public Block getMasterBlock(Block b) {
        String typeName = b.getType().name().toUpperCase();

        // 2칸 높이 문 (Armored Door 등)
        if (typeName.contains("_DOOR")) {
            if (b.getBlockData() instanceof org.bukkit.block.data.type.Door door) {
                if (door.getHalf() == org.bukkit.block.data.type.Door.Half.TOP) {
                    return b.getRelative(0, -1, 0);
                }
            }
        }

        // 4x4 대형 문 (Big Door)
        if (typeName.contains("BIG_DOOR")) {
            Block master = b;
            // 같은 타입의 블록이 있는 가장 아래쪽(Y축 최하단)으로 이동
            while (master.getRelative(0, -1, 0).getType() == b.getType()) {
                master = master.getRelative(0, -1, 0);
            }
            return master;
        }
        return b;
    }

    // GUI 아이템 클릭 방지 (아이템을 꺼내가지 못하게)
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title.contains("그룹 멤버:")) {
            event.setCancelled(true);
            return;
        }
        // BedManager 인벤토리 클릭 로직은 BedManager 클래스에서 처리되므로
        // main에서는 그룹 멤버 GUI 클릭 방지만 남겨두시면 됩니다.
    }

    public boolean canAccess(Player p, String locKey, String category) {
        if (p.isOp()) return true;

        String path = category + "." + locKey;
        String ownerUUID = dataStorage.getConfig().getString(path + ".owner");

        // 주인 정보가 없으면 공용 블록으로 간주 (또는 설치 시점부터 저장되도록 관리)
        if (ownerUUID == null) return true;

        // 1. 본인인지 확인
        if (p.getUniqueId().toString().equals(ownerUUID)) return true;

        // 2. 그룹원인지 확인
        String ownerGroup = groupManager.getGroupViaUUID(ownerUUID);
        String playerGroup = groupManager.getPlayerGroup(p);

        return ownerGroup != null && ownerGroup.equals(playerGroup);
    }
    public boolean hasEnoughPlanks(Player player, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType().name().contains("_PLANKS")) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    public boolean hasItem(Player p, Material type, int amount) {
        int count = 0;
        for (ItemStack i : p.getInventory().getContents()) {
            if (i != null && i.getType() == type) count += i.getAmount();
        }
        return count >= amount;
    }

    public void removeItem(Player p, Material type, int amount) {
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

    public void removePlanks(Player player, int amount) {
        int toRemove = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType().name().contains("_PLANKS")) {
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
        player.getInventory().setContents(contents);
    }

    public void destroyBlockByKey(String key, String type) {
        String[] parts = key.split("_");
        if (parts.length < 4) return;

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return;

        int x = Integer.parseInt(parts[1]);
        int y = Integer.parseInt(parts[2]);
        int z = Integer.parseInt(parts[3]);
        Block b = world.getBlockAt(x, y, z);
        // --- [추가] 터렛 상부 모델링 제거 로직 ---
        if (b.getType() == Material.GOLD_BLOCK) {
            if (this.turretManager != null) {
                // 1. 상부 모델(종이)은 그냥 지우기 (드롭 X)
                this.turretManager.removeTurret(b.getLocation());

                // 2. 내부 GUI 아이템만 바닥에 떨구기
                this.turretManager.dropTurretItems(b.getLocation());
            }
        }
        if (type.equals("door")) {
            // [수정] dataStorage에서 삭제
            dataStorage.getConfig().set("doors." + key, null);
            b.setType(Material.AIR);
            Block top = b.getRelative(0, 1, 0);
            if (top.getType().name().contains("_DOOR")) top.setType(Material.AIR);
            world.playSound(b.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.0f, 0.5f);
        } else {
            Sound s = (b.getType() == Material.IRON_BLOCK) ? Sound.BLOCK_METAL_BREAK : Sound.BLOCK_WOOD_BREAK;
            // [수정] dataStorage에서 삭제
            dataStorage.getConfig().set("planks." + key, null);
            b.setType(Material.AIR);
            world.playSound(b.getLocation(), s, 1.0f, 0.5f);
        }
        dataStorage.saveConfig(); // 변경사항 저장
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDummyBreak(org.bukkit.event.block.BlockBreakEvent e) {
        Block b = e.getBlock();
        String typeName = b.getType().name().toUpperCase();

        if (typeName.contains("ARMORED_DOOR") || typeName.contains("BIG_DOOR")) {
            // 크리에이티브가 아니면 절대 그냥 못 부수게 함
            if (e.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
                e.setCancelled(true);
                // 여기서도 데미지를 입히고 싶다면 위 onHit의 로직을 호출하면 됩니다.
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onExplode(EntityExplodeEvent e) {
        // 1. 기본 데미지 설정 (tnt-settings.기본 값이 없으면 10)
        int dmg = getConfig().getInt("tnt-settings.기본", 10);

        if (e.getEntity() != null) {
            // 엔티티의 타입을 문자열로 가져옴 (예: JEG_ROCKET)
            String entityName = e.getEntity().getType().name().toUpperCase();

            if (entityName.contains("ROCKET")) {
                dmg = getConfig().getInt("explosion-settings.rocket_launcher", 50);
            } else if (entityName.contains("EXPLOSIVE_CHARGE")) {
                dmg = getConfig().getInt("explosion-settings.explosive_charge", 100);
            } else if (entityName.contains("GRENADE")) {
                dmg = getConfig().getInt("explosion-settings.grenade", 10);
            } else if (entityName.contains("PRIMED_TNT")) {
                dmg = getConfig().getInt("tnt-settings.기본", 10); // 바닐라 TNT 데미지
            }
        }

        final int finalDmg = dmg;
        Bukkit.getLogger().info("[Debug] 적용될 최종 데미지: " + finalDmg);

        Location center = e.getLocation();
        int radius = 5;

        // 2. 주변 블록 탐색 및 데미지 적용
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location loc = center.clone().add(x, y, z);
                    if (loc.distance(center) > radius) continue;

                    Block b = loc.getBlock();
                    Material type = b.getType();

                    if (type.name().contains("_DOOR") || type.name().contains("BIG_DOOR") || type.name().contains("ARMORED_DOOR")) {
                        // 만약 모드 블록이 마인크래프트 기본 Door 데이터를 가지고 있다면 (하단만 체크)
                        if (b.getBlockData() instanceof org.bukkit.block.data.type.Door door) {
                            if (door.getHalf() == org.bukkit.block.data.type.Door.Half.BOTTOM) {
                                String doorKey = "doors." + blockToKey(b);
                                if (dataStorage.getConfig().contains(doorKey)) {
                                    applyDmg(b, finalDmg, null);
                                }
                            }
                        } else {
                            // 모드 블록이 일반 블록 형태라면 바로 데미지 적용
                            String pKey = "planks." + blockToKey(b);
                            if (dataStorage.getConfig().contains(pKey)) {
                                applyPlankDmg(b, finalDmg, null);
                            }
                        }
                    }
                }
            }
        }

        // 3. 지형 보호
        e.blockList().clear();
        e.setYield(0.0f);
    }

    public void applyPlankDmg(Block b, int d, Player p) {
        String rawKey = blockToKey(b);
        String key = "planks." + rawKey;

        // [체크] 등록된 구조물이 아니면 종료
        if (!dataStorage.getConfig().contains(key)) {
            return;
        }

        Material type = b.getType();
        String typeName = type.name().toUpperCase();

        // 2. 실시간 재질 판별
        String correctName;
        int correctMax;

        if (typeName.contains("ARMORED_DOOR")) {
            correctName = "합금 문";
            correctMax = 800; // 설정하고 싶은 체력
        } else if (typeName.contains("BIG_DOOR")) {
            correctName = "차고 문";
            correctMax = 600;
        } else if (type == Material.GOLD_BLOCK) {
            correctName = "자동 터렛";
            correctMax = 200;
        } else if (typeName.contains("IRON_BLOCK")) {
            correctName = "철제 구조물";
            correctMax = 200;
        } else if (typeName.contains("STONE_BRICK")) {
            correctName = "석재 구조물";
            correctMax = 100;
        } else {
            correctName = "나무 구조물";
            correctMax = 50;
        }

        // 3. 정보 업데이트
        String savedName = dataStorage.getConfig().getString(key + ".display_name");
        int savedMax = dataStorage.getConfig().getInt(key + ".max_health", -1);

        if (!correctName.equals(savedName) || correctMax != savedMax) {
            dataStorage.getConfig().set(key + ".display_name", correctName);
            dataStorage.getConfig().set(key + ".max_health", correctMax);
            dataStorage.saveConfig();
        }

        // 4. 체력 계산
        int currentHp = dataStorage.getConfig().getInt(key + ".health", correctMax);
        int newHp = currentHp - d;

        // 사운드 설정
        Sound breakSound = Sound.BLOCK_WOOD_BREAK;
        Sound hitSound = Sound.BLOCK_WOOD_HIT;
        if (type == Material.GOLD_BLOCK || typeName.contains("IRON")) {
            breakSound = Sound.BLOCK_METAL_BREAK;
            hitSound = Sound.BLOCK_METAL_HIT;
        }

        // --- [수정된 판정 로직] ---
        if (newHp <= 0) {
            // 파괴되는 경우
            if (type == Material.GOLD_BLOCK) {
                this.turretManager.dropTurretItems(b.getLocation());
            }
            // 3. [핵심] 블록 제거 로직 분기
            if (typeName.contains("BIG_DOOR")) {
                // 4x4 대형 문 전체 제거
                removeBigObject(b);
            } else {
                // 일반 블록 제거
                b.setType(Material.AIR);

                // 2칸 높이 모드 문(Armored 등)일 경우 상단도 체크해서 제거
                Block top = b.getRelative(0, 1, 0);
                if (top.getType().name().contains("DOOR") || top.getType() == type) {
                    top.setType(Material.AIR);
                }
            }

            b.getWorld().playSound(b.getLocation(), breakSound, 1.0f, 1.0f);

            if (p != null) p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText("§c[System] " + correctName + "이(가) 파괴되었습니다!"));
        } else {
            // [중요!] 체력이 남아있는 경우: 블록을 없애면 안 됩니다!
            dataStorage.getConfig().set(key + ".health", newHp); // 줄어든 체력만 저장
            dataStorage.saveConfig();

            if (p != null) {
                String progressBar = getProgressBar(newHp, correctMax);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§f[" + correctName + "] " + progressBar + " §f(" + newHp + " / " + correctMax + ")"));
            }
            // 타격 사운드만 재생 (블록은 그대로 유지)
            b.getWorld().playSound(b.getLocation(), hitSound, 1.0f, 0.8f);
        }
    }
    public void applyDmg(Block b, int d, Player p) {
        String key = "doors." + blockToKey(b);
        String typeName = b.getType().name().toUpperCase();

        // 1. 최대 체력 결정 (기준점)
        int maxHp;
        if (typeName.contains("ARMORED_DOOR")) maxHp = 800;
        else if (typeName.contains("BIG_DOOR")) maxHp = 600;
        else if (typeName.contains("IRON")) maxHp = 200;
        else maxHp = 100;
        // [수정] dataStorage에서 현재 체력 가져오기
        int currentHp = dataStorage.getConfig().getInt(key + ".health", maxHp);
        int newHp = currentHp - d;

        if (newHp <= 0) {
            // [수정] dataStorage 데이터 삭제
            dataStorage.getConfig().set(key, null);
            dataStorage.saveConfig();

            b.setType(Material.AIR);
            Block top = b.getRelative(0, 1, 0);
            if (top.getType() == b.getType() || top.getType().name().contains("_DOOR")) {
                top.setType(Material.AIR);
            }

            if (p != null) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§c[System] 문이 파괴되었습니다!"));
            }
            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.5f);
        } else {
            // [수정] dataStorage 체력 업데이트
            dataStorage.getConfig().set(key + ".health", newHp);
            dataStorage.saveConfig();

            if (p != null) {
                String progressBar = getProgressBar(newHp, maxHp);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§6[Door] 체력: " + progressBar + " §f(" + newHp + " / " + maxHp + ")"));
            }
            Sound hitSound = typeName.contains("IRON") || typeName.contains("DOOR") ? Sound.BLOCK_METAL_HIT : Sound.BLOCK_WOOD_HIT;
            b.getWorld().playSound(b.getLocation(), hitSound, 1.0f, 1.2f);
            b.getWorld().spawnParticle(Particle.CRIT, b.getLocation().add(0.5, 0.5, 0.5), 5);
        }
    }
    public void removeBigObject(Block master) {
        Material type = master.getType();
        // 기준점(master)으로부터 위쪽/옆쪽으로 4x4 범위를 탐색하여 동일 재질 제거
        // (설치 방향에 따라 좌표 조정이 필요할 수 있으나, 보통은 4x4x4 범위를 훑는게 안전함)
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 4; z++) {
                    Block rel = master.getRelative(x, y, z);
                    if (rel.getType() == type) {
                        rel.setType(Material.AIR);
                    }
                    // 반대 방향(-x, -z)도 체크가 필요하다면 범위를 -2 ~ 2로 잡으셔도 됩니다.
                }
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDummyBlockBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();

        Block master = getMasterBlock(b); // 기준점 찾기 (4x4, 2칸 높이 대응)
        String masterKey = blockToKey(master);
        String typeName = b.getType().name().toUpperCase();

        String plankPath = "planks." + masterKey;
        String doorPath = "doors." + masterKey;
        boolean isModBlock = typeName.contains("ARMORED_DOOR") || typeName.contains("BIG_DOOR");

        // 데이터가 있거나 모드 블록인 경우
        if (dataStorage.getConfig().contains(plankPath) || dataStorage.getConfig().contains(doorPath) || isModBlock) {
            // 1. [중요] 실제 블록 파괴를 취소 (사라지지 않게 함)
            e.setCancelled(true);

            // 2. 데미지 적용 (한 번 부술 때마다 데미지 1 차감)
            // 만약 도구에 따라 더 많이 깎고 싶다면 여기서 도구 체크를 합니다.
            int damage = 1;
            if (dataStorage.getConfig().contains(doorPath)) {
                applyDmg(master, damage, p);
            } else {
                applyPlankDmg(master, damage, p);
            }
            // 블록이 파괴되는 시각적 효과를 주기 위해 패킷을 보내거나 소리를 재생할 수 있습니다.
            master.getWorld().playSound(master.getLocation(), Sound.BLOCK_METAL_HIT, 1.0f, 1.0f);
        }
    }
    // 체력을 시각적인 바로 변환해주는 메서드

    public String getProgressBar(int current, int max) {

        int bars = 10; // 총 칸 수

        int completedBars = (int) (((double) current / max) * bars);

        StringBuilder sb = new StringBuilder();



        for (int i = 0; i < bars; i++) {

            if (i < completedBars) sb.append("§a■"); // 남은 체력 (초록색)

            else sb.append("§7■"); // 깎인 체력 (회색)

        }

        return sb.toString();

    }

    public String blockToKey(Block b) {
        Location l = b.getLocation();
        return l.getWorld().getName() + "_" + l.getBlockX() + "_" + l.getBlockY() + "_" + l.getBlockZ();
    }
    @EventHandler
    public void onDrink(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        // 마신 아이템이 물병인지 확인
        if (item.getType() == Material.POTION) {
            org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
            // 일반 물병인지 확인 (포션 효과가 없는 기본 물병)
            if (meta != null && meta.getBasePotionData().getType() == org.bukkit.potion.PotionType.WATER) {

                // 1. 체온 회복 처리
                double currentTemp = tempManager.getTemp(player);
                double defaultTemp = getConfig().getDouble("temperature-settings.default-temp", 37.5);
                double recovery = getConfig().getDouble("temperature-settings.water-bottle-recovery", 2.0);

                // 현재 온도가 기본보다 높으면 낮추고, 낮으면 높임 (정상 체온으로 수렴)
                if (currentTemp > defaultTemp) {
                    currentTemp = Math.max(defaultTemp, currentTemp - recovery);
                } else if (currentTemp < defaultTemp) {
                    currentTemp = Math.min(defaultTemp, currentTemp + recovery);
                }

                // TempManager의 Map에 직접 업데이트 (tempManager에 setTemp 메서드 추가 권장)
                // 여기서는 예시로 tempManager 내부 Map이 public이거나 메서드가 있다고 가정
                tempManager.setTemp(player, currentTemp);

                // 2. 방사능 제거 처리
                double currentRad = radManager.getRad(player);
                double reduction = getConfig().getDouble("radiation-settings.water-bottle-reduction", 10.0);

                double newRad = Math.max(0.0, currentRad - reduction);
                radManager.setRad(player, newRad);
            }
        }
    }
    private void restoreBlocksAfterReboot() {
        ConfigurationSection section = blockRegenManager.getPendingSection();
        if (section == null) return;

        long now = System.currentTimeMillis();

        for (String key : section.getKeys(false)) {
            try {
                // 위치 정보 복원
                String[] split = key.split("_");
                org.bukkit.World world = Bukkit.getWorld(split[0]);
                int x = Integer.parseInt(split[1]);
                int y = Integer.parseInt(split[2]);
                int z = Integer.parseInt(split[3]);
                Location loc = new Location(world, x, y, z);

                Material type = Material.valueOf(section.getString(key + ".type"));
                long respawnAt = section.getLong(key + ".time");

                if (now >= respawnAt) {
                    // 이미 리젠 시간이 지난 경우 즉시 복구
                    blockListener.processRegen(loc, type);
                } else {
                    // 남은 시간만큼 기다렸다가 복구
                    long delayTicks = (respawnAt - now) / 50L; // 50ms = 1tick
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        blockListener.processRegen(loc, type);
                    }, Math.max(1, delayTicks));
                }
            } catch (Exception e) {
                getLogger().warning("리젠 블록 복구 실패: " + key);
            }
        }
    }
    @EventHandler
    public void onVineSpread(BlockSpreadEvent event) {
        // 1. 번지려고 하는 블록의 종류가 덩굴(VINE)인지 확인
        if (event.getSource().getType() == Material.VINE) {
            // 2. 이벤트 취소 (번지지 못하게 함)
            event.setCancelled(true);
        }
    }
}

