package Alpa.test;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.scoreboard.*;

import java.io.*;
import java.util.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;


public final class main extends JavaPlugin implements Listener {

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
    public ReinforcedManager reinforcedManager;
    private File lockFile;
    private FileConfiguration lockConfig;
    public HologramManager hologramManager;
    public DoorManager doorManager;
    public BlockDecayManager blockDecayManager;
    private CardKeyManager cardKeyManager;
    public PlankToIronManager plankToIronManager;
    public JoinManager joinManager;
    public EnvironmentManager environmentManager;
    public InteractBlockListener interactBlockListener;
    public BlockDrops blockDrops;
    public MobListener mobListener;
    public FurnaceListener furnaceListener;
    public DeadManager deadManager;
    public ShopManager shopManager;
    public FileConfiguration shopConfig;
    private File shopFile;

    @Override
        public void onEnable() {
            createTurretConfig();
            createGunDamageConfig();
            createLockConfig();
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
            MainCommandExecutor mainExecutor = new MainCommandExecutor(this);
            this.blueprintManager = new BlueprintManager(this);
            this.reinforcedManager = new ReinforcedManager(this);
            this.hologramManager = new HologramManager(this);
            this.doorManager = new DoorManager(this);
            this.blockDecayManager = new BlockDecayManager(this);
            cardKeyManager = new CardKeyManager(this);
            this.plankToIronManager = new PlankToIronManager(this);
            this.joinManager = new JoinManager(this);
            this.environmentManager = new EnvironmentManager(this);
            this.interactBlockListener = new InteractBlockListener(this);
            this.blockDrops = new BlockDrops(this);
            this.mobListener = new MobListener();
            this.furnaceListener = new FurnaceListener();
            this.deadManager = new DeadManager(this);
            this.getServer().getMessenger().registerOutgoingPluginChannel(this, "examplemod:main");
            this.getServer().getMessenger().registerOutgoingPluginChannel(this, "examplemod:messages");
            this.getServer().getMessenger().registerOutgoingPluginChannel(this, "examplemod:group_data");
            this.getServer().getMessenger().registerOutgoingPluginChannel(this, "examplemod:bed_data");

            addDefaultSettings();
        org.bukkit.plugin.PluginManager pm = getServer().getPluginManager();

        pm.registerEvents(this.bedManager, this);
        pm.registerEvents(this.plankHealthManager, this); // 체력 매니저 등록
        pm.registerEvents(this.craftManager, this);
        pm.registerEvents(this.electricManager, this);

        // 3. 기타 매니저 등록 (인스턴스 생성이 필요한 것들)
        pm.registerEvents(this.plankToIronManager, this);
        pm.registerEvents(this.joinManager, this);
        pm.registerEvents(this.environmentManager, this);
        pm.registerEvents(this.reinforcedManager,this);
        pm.registerEvents(this.interactBlockListener, this);
        pm.registerEvents(this.blockListener, this);
        pm.registerEvents(new RaidListener(this), this);
        pm.registerEvents(this.blockDrops, this);
        pm.registerEvents(this.mobListener, this);
        pm.registerEvents(this.rouletteListener, this);
        pm.registerEvents(this.doorManager, this);
        pm.registerEvents(this, this);
        pm.registerEvents(this.turretManager, this);
        pm.registerEvents(refineryManager, this);
        pm.registerEvents(this.furnaceListener, this);
        pm.registerEvents(this.blueprintManager,this);
        pm.registerEvents(new CardKeyListener(this, cardKeyManager), this);
        pm.registerEvents(this.deadManager, this);

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
        // config 설정 로직
        if (!getConfig().contains("decay-settings.interval-seconds")) {
            getConfig().set("decay-settings.interval-seconds", 10);
        }

        long intervalSeconds = getConfig().getLong("decay-settings.interval-seconds", 3600L);
        this.blockDecayManager.runTaskTimer(this, intervalSeconds * 20L, intervalSeconds * 20L);

        // 1. 통합 관리자가 처리할 명령어들
        String[] mainCommands = {"스포너설정", "보온설정", "방호설정", "스폰설정", "그룹", "레이드박스", "상점", "상점등록", "상점삭제"};
        for (String cmd : mainCommands) {
            if (getCommand(cmd) != null) getCommand(cmd).setExecutor(mainExecutor);
        }

        // 2. 이미 별도 클래스가 있는 명령어들
        if (getCommand("침대") != null) getCommand("침대").setExecutor(this.bedManager);
        if (getCommand("bedrespawn") != null) getCommand("bedrespawn").setExecutor(this.bedManager);
        if (getCommand("카드키") != null) getCommand("카드키").setExecutor(new CardKeyCommand(cardKeyManager));

        if (getCommand("제작") != null) {
            getCommand("제작").setExecutor((sender, command, label, args) -> {
                if (sender instanceof Player) craftManager.openDefaultCrafting((Player) sender);
                return true;
            });
        }
        this.shopFile = new File(getDataFolder(), "shop.yml");

        // 2. 물리적 파일 존재 확인 및 기본 리소스 저장
        if (!this.shopFile.exists()) {
            this.saveResource("shop.yml", false);
        }
        this.shopConfig = YamlConfiguration.loadConfiguration(shopFile);
        this.shopManager = new ShopManager(this);
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "examplemod:shop_data");
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "examplemod:shop_data", this.shopManager);
        this.shopManager.loadAllShops();

        // 스코어보드 업데이트 스케줄러
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateGroupScoreboard(player);
            }
        }, 0L, 100L);
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            // 1. 재생성 매니저 저장
            if(this.blockRegenManager != null) this.blockRegenManager.savePending();

            // 2. 데이터 저장 (데이터가 비어있지 않을 때만 실행)
            if(this.dataStorage != null) {
                ConfigurationSection config = this.dataStorage.getConfig();
                // planks나 doors 섹션이 실제로 존재하고 비어있지 않은지 확인
                boolean hasPlanks = config.contains("planks") && !config.getConfigurationSection("planks").getKeys(false).isEmpty();
                boolean hasDoors = config.contains("doors") && !config.getConfigurationSection("doors").getKeys(false).isEmpty();

                if (hasPlanks || hasDoors) {
                    this.dataStorage.saveConfig();
                }
            }
        }, 600L, 600L);
        restoreBlocksAfterReboot();
        getCommand("카드키").setExecutor(new CardKeyCommand(cardKeyManager));
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "examplemod:main", (channel, player, message) -> {
            handleIncomingPacket(player, message);
        });
        getServer().getScheduler().runTaskLater(this, () -> {
            deadManager.restoreNPCAnimations();
        }, 60L);
    }
    public void createVoidWorld(String name) {
        WorldCreator creator = new WorldCreator(name);
        creator.generator(new VoidGenerator()); // 우리가 만든 생성기 강제 지정
        creator.generateStructures(false);
        Bukkit.createWorld(creator);
    }
    private void handleShopIncomingPacket(Player player, byte[] message) {
        try {
            String data = new String(message, "UTF-8");
            // 데이터 형식: "BUY:1:5" (액션:상품ID:수량)
            String[] parts = data.split(":");

            if (parts[0].equals("BUY")) {
                int shopId = Integer.parseInt(parts[1]);
                int amount = Integer.parseInt(parts[2]);

                // 여기서 실제 상점 거래 로직 수행
                processShopTransaction(player, shopId, amount);
            }
        } catch (Exception e) {
            getLogger().warning("상점 패킷 처리 중 오류: " + e.getMessage());
        }
    }

    private void processShopTransaction(Player player, int shopId, int amount) {
        // [예시 로직] 실제 서버의 경제 시스템이나 아이템 가격에 맞게 수정하세요.
        Material currency = Material.EMERALD; // 화폐: 에메랄드
        int pricePerOne = 10; // 개당 가격
        int totalPrice = pricePerOne * amount;

        if (player.getInventory().contains(currency, totalPrice)) {
            // 1. 비용 차감
            player.getInventory().removeItem(new ItemStack(currency, totalPrice));

            // 2. 상품 지급 (예: 1번 상품이 철괴라면)
            if (shopId == 1) {
                player.getInventory().addItem(new ItemStack(Material.IRON_INGOT, amount));
            }

            player.sendMessage("§a[Shop] §f구매가 완료되었습니다!");

            // 3. (선택) 클라이언트에 성공 패킷 전송
            sendShopResponse(player, "SUCCESS");
        } else {
            player.sendMessage("§c[Shop] §f잔액이 부족합니다.");
            sendShopResponse(player, "FAIL");
        }
    }

    // 서버에서 클라이언트로 상점 관련 알림을 보낼 때 사용
    public void sendShopResponse(Player player, String response) {
        player.sendPluginMessage(this, "examplemod:shop_data", response.getBytes());
    }
    private void handleIncomingPacket(Player player, byte[] message) {
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        byte packetId = in.readByte();

        if (packetId == 2) { // 핑 패킷 수신
            float x = in.readFloat();
            float z = in.readFloat();
            boolean isRemove = in.readBoolean();

            String groupName = groupManager.getPlayerGroup(player);
            if (groupName != null) {
                List<String> members = groupManager.getGroupMembers(groupName);
                int memberIndex = members.indexOf(player.getUniqueId().toString()) + 1;
                if (memberIndex <= 0) memberIndex = 1;

                if (isRemove) {
                    groupManager.removeGroupPing(groupName, x, z);
                } else {
                    groupManager.addGroupPing(groupName, x, z);
                }

                for (String memberUUID : members) {
                    Player member = Bukkit.getPlayer(UUID.fromString(memberUUID));
                    if (member != null && member.isOnline()) {
                        sendPingToClient(member, x, z, isRemove, memberIndex);
                    }
                }
            } else {
                sendPingToClient(player, x, z, isRemove, 1);
            }
        }
    }
    @Override
    public void onDisable() {
        if (this.hologramManager != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                this.hologramManager.removePreview(p);
            }
        }
        if (blockRegenManager != null) {
            blockRegenManager.savePending();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
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
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(this, () -> {
            String groupName = groupManager.getPlayerGroup(player);
            if (groupName != null) {
                List<String> pings = groupManager.getGroupPings(groupName);
                List<String> members = groupManager.getGroupMembers(groupName);
                int defaultIndex = members.indexOf(player.getUniqueId().toString()) + 1;
                if (defaultIndex <= 0) defaultIndex = 1;

                for (String pingStr : pings) {
                    String[] split = pingStr.split(",");
                    if (split.length >= 2) {
                        sendPingToClient(player, Float.parseFloat(split[0]), Float.parseFloat(split[1]), false, defaultIndex);
                    }
                }
            }
        }, 40L);
    }
    public void checkCitizens() {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null ||
                !Bukkit.getPluginManager().getPlugin("Citizens").isEnabled()) {
            Bukkit.getLogger().severe("Citizens 플러그인을 찾을 수 없습니다! NPC 기능이 작동하지 않습니다.");
            return;
        }
    }
    private void sendPingToClient(Player receiver, float x, float z, boolean isRemove, int memberIndex) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeByte(2);
        out.writeFloat(x);
        out.writeFloat(z);
        out.writeBoolean(isRemove);
        out.writeInt(memberIndex);
        receiver.sendPluginMessage(this, "examplemod:main", out.toByteArray());
    }
    private void sendGroupLoc(Player receiver, Player member) {
        String groupName = groupManager.getPlayerGroup(member);
        List<String> members = groupManager.getGroupMembers(groupName);
        int memberIndex = members.indexOf(member.getUniqueId().toString()) + 1;
        if (memberIndex <= 0) memberIndex = 1;

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeByte(1);
        out.writeLong(member.getUniqueId().getMostSignificantBits());
        out.writeLong(member.getUniqueId().getLeastSignificantBits());

        out.writeUTF(member.getName());
        out.writeDouble(member.getLocation().getX());
        out.writeDouble(member.getLocation().getZ());
        out.writeInt(memberIndex);

        receiver.sendPluginMessage(this, "examplemod:main", out.toByteArray());
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


    // main.java 내부 메서드 교체

    private void updateGroupScoreboard(Player player) {
        Scoreboard board = player.getScoreboard();

        // 1. 메인 보드면 개별 보드로 전환
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }

        // 2. Objective가 없으면 딱 한 번만 생성 (지우지 않음!)
        Objective obj = board.getObjective("Info");
        if (obj == null) {
            obj = board.registerNewObjective("Info", "dummy", "§6§l[ 정보 ]");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            setupPermanentLayout(board, obj); // 고정 틀 잡기
        }

        // 3. 타이틀(그룹명) 갱신 (지우지 않고 이름만 변경)
        String groupName = groupManager.getPlayerGroup(player);
        String title = (groupName != null) ? "§6§l[ " + groupName + " ]" : "§b§l[ 개인 정보 ]";
        if (!obj.getDisplayName().equals(title)) {
            obj.setDisplayName(title);
        }

        // 4. 데이터 갱신 (팀의 Suffix만 변경 - 렉 발생 0%)
        updateEntryValue(board, "temp", getTempColor(player));
        updateEntryValue(board, "rad", getRadColor(player));
    }

    // 최초 1회만 호출되는 틀 설정
    private void setupPermanentLayout(Scoreboard board, Objective obj) {
        // 체온 줄
        Team temp = board.registerNewTeam("temp");
        temp.addEntry("§e체온: "); // 이 글자를 키값으로 사용
        obj.getScore("§e체온: ").setScore(99);

        // 방사능 줄
        Team rad = board.registerNewTeam("rad");
        rad.addEntry("§2방사능: ");
        obj.getScore("§2방사능: ").setScore(98);

        // 구분선 (팀 관리 필요 없음)
        obj.getScore("§7----------------").setScore(97);
    }

    // 팀의 Suffix만 업데이트
    private void updateEntryValue(Scoreboard board, String teamName, String value) {
        Team team = board.getTeam(teamName);
        if (team != null && !team.getSuffix().equals(value)) {
            team.setSuffix(value);
        }
    }
    // 최초 1회 레이아웃 설정 (더미 엔트리 등록)
    private void setupScoreboardLayout(Scoreboard board, Objective obj) {
        String[] entries = {"§e체온: ", "§2방사능: ", "§7----------------"};
        int score = 99;

        for (String entry : entries) {
            // 각 줄을 관리할 팀 생성
            String teamName = (entry.contains("체온")) ? "temp" : (entry.contains("방사능")) ? "rad" : "line";
            Team team = board.getTeam(teamName);
            if (team == null) team = board.registerNewTeam(teamName);

            team.addEntry(entry); // 이 텍스트(엔트리)를 팀이 관리함
            obj.getScore(entry).setScore(score--);
        }
    }

    // 팀의 Prefix만 바꿔서 숫자 갱신
    private void updateTeamEntry(Scoreboard board, String teamName, String entry, String value) {
        Team team = board.getTeam(teamName);
        if (team != null) {
            team.setPrefix(""); // 초기화
            team.setSuffix(value); // 값만 변경 (패킷 매우 가벼움)
        }
    }

    // 헬퍼 메서드 (가독성용)
    private String getTempColor(Player p) {
        double temp = tempManager.getTemp(p);
        String color = (temp >= 38.5) ? "§c" : (temp <= 36.0) ? "§b" : "§f";
        return color + temp + "°C";
    }

    private String getRadColor(Player p) {
        double rad = radManager.getRad(p);
        String color = (rad >= 70) ? "§4" : (rad >= 40) ? "§6" : "§a";
        return color + rad + " mSv";
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
        if (b == null) return null;
        String typeName = b.getType().name().toUpperCase();
        if (typeName.contains("_BED")) {
            // BedManager에 있는 getBedBaseLocation 로직을 활용하거나 직접 구현
            // 침대는 보통 하단(Foot) 블록을 마스터로 잡습니다.
            if (b.getBlockData() instanceof org.bukkit.block.data.type.Bed) {
                org.bukkit.block.data.type.Bed bedData = (org.bukkit.block.data.type.Bed) b.getBlockData();
                if (bedData.getPart() == org.bukkit.block.data.type.Bed.Part.HEAD) {
                    // 머리 부분을 클릭했다면 발치(Foot) 방향의 블록을 리턴
                    return b.getRelative(bedData.getFacing().getOppositeFace());
                }
            }
            return b; // 이미 발치라면 그대로 리턴
        }
        if (typeName.contains("BIG_DOOR") || typeName.contains("DOOR_DUMMY")) {
            return findBigDoorMaster(b);
        }

        // 문자열 기반의 Half 판정 (아머드 도어 호환성 최상)
        String dataStr = b.getBlockData().getAsString().toLowerCase();
        if (dataStr.contains("half=upper") || dataStr.contains("half=top")) {
            return b.getRelative(BlockFace.DOWN);
        }

        return b;
    }
    private Block findBigDoorMaster(Block b) {
        // 1. 이미 마스터 블록인 경우 바로 반환
        if (b.getType().name().contains("BIG_DOOR") && !b.getType().name().contains("DUMMY")) {
            return b;
        }

        // 2. 더미 블록인 경우: 주변 4x4x4 범위를 뒤져서 마스터 블록을 찾음
        // (모드 로직상 마스터는 항상 왼쪽 하단 기준이므로 현재 위치 기준 -4 ~ 0 범위를 탐색)
        for (int y = -4; y <= 0; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    Block target = b.getRelative(x, y, z);
                    String targetName = target.getType().name().toUpperCase();

                    // 진짜 마스터 블록(DUMMY가 아닌 BIG_DOOR)을 찾으면 반환
                    if (targetName.contains("BIG_DOOR") && !targetName.contains("DUMMY")) {
                        return target;
                    }
                }
            }
        }
        return b; // 못 찾으면 자기 자신 반환
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
        if (b.getType() == Material.TINTED_GLASS) {
            if (this.turretManager != null) {
                // 1. 상부 모델(종이)은 그냥 지우기 (드롭 X)
                this.turretManager.removeTurret(b.getLocation());

                // 2. 내부 GUI 아이템만 바닥에 떨구기
                this.turretManager.dropTurretItems(b.getLocation());
            }
        }
        if (type.equals("door")) {
            removeLockEntity(b);
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
        // 1. 데미지 결정 (기존 로직 유지)
        int dmg = getConfig().getInt("tnt-settings.기본", 10);
        if (e.getEntity() != null) {
            String entityName = e.getEntity().getType().name().toUpperCase();
            if (entityName.contains("ROCKET")) dmg = getConfig().getInt("explosion-settings.rocket_launcher", 50);
            else if (entityName.contains("EXPLOSIVE_CHARGE")) dmg = getConfig().getInt("explosion-settings.explosive_charge", 100);
            else if (entityName.contains("GRENADE")) dmg = getConfig().getInt("explosion-settings.grenade", 10);
            else if (entityName.contains("PRIMED_TNT")) dmg = getConfig().getInt("tnt-settings.기본", 10);
        }
        final int finalDmg = dmg;

        Location center = e.getLocation();
        int radius = 5;

        // 중복 데미지 방지용 세트 (마스터 블록 좌표 저장)
        java.util.Set<String> processedBlocks = new java.util.HashSet<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = center.clone().add(x, y, z).getBlock();
                    if (b.getType() == Material.AIR) continue;

                    String typeName = b.getType().name().toUpperCase();

                    // [수정 핵심] 문 계열(더미 포함)인지 확인
                    boolean isDoor = typeName.contains("_DOOR") || typeName.contains("BIG_DOOR") || typeName.contains("DUMMY") || typeName.contains("ARMORED_DOOR");

                    if (isDoor) {
                        Block master = getMasterBlock(b);
                        String masterKey = blockToKey(master);

                        // 이미 이 폭발로 데미지를 입은 문이라면 스킵
                        if (processedBlocks.contains("door_" + masterKey)) continue;

                        if (dataStorage.getConfig().contains("doors." + masterKey)) {
                            applyDmg(master, finalDmg, null);
                            processedBlocks.add("door_" + masterKey);
                        }
                    } else {
                        // 일반 블록(Plank, 터렛 등) 처리
                        String pKey = blockToKey(b);
                        if (processedBlocks.contains("plank_" + pKey)) continue;

                        if (dataStorage.getConfig().contains("planks." + pKey)) {
                            applyPlankDmg(b, finalDmg, null);
                            processedBlocks.add("plank_" + pKey);
                        }
                    }
                }
            }
        }

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

        Block master = getMasterBlock(b);
        Material type = b.getType();
        String typeName = type.name().toUpperCase();
        boolean isDoor = typeName.contains("_DOOR") && !typeName.contains("TRAPDOOR");

        // 2. 실시간 재질 판별
        String correctName;
        int correctMax;

        if (b.getType().name().contains("_BED")) {
            return;
        }
        if (typeName.contains("ARMORED_DOOR")) {
            correctName = "합금 문";
            correctMax = 800; // 설정하고 싶은 체력
        } else if (typeName.contains("BIG_DOOR")) {
            correctName = "차고 문";
            correctMax = 600;
        } else if (typeName.contains("IRON_DOOR")) {
            correctName = "철 문";
            correctMax = 200;
        } else if (isDoor) {
            correctName = "나무 문";
            correctMax = 100;
        } else if (type == Material.TINTED_GLASS) {
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
        }

        // 4. 체력 계산
        int currentHp = dataStorage.getConfig().getInt(key + ".health", correctMax);
        int newHp = currentHp - d;

        // 사운드 설정
        Sound breakSound = Sound.BLOCK_WOOD_BREAK;
        Sound hitSound = Sound.BLOCK_WOOD_HIT;
        if (type == Material.TINTED_GLASS || typeName.contains("IRON")) {
            breakSound = Sound.BLOCK_METAL_BREAK;
            hitSound = Sound.BLOCK_METAL_HIT;
        }

        // --- [수정된 판정 로직] ---
        if (newHp <= 0) {
            removeLockEntity(master);
            // 파괴되는 경우
            if (type == Material.TINTED_GLASS) {
                // [중요] 여기서 TurretManager의 모델 제거 로직을 호출함
                this.turretManager.removeTurret(b.getLocation());
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
            reduceItemDurability(p);
        } else {
            // [중요!] 체력이 남아있는 경우: 블록을 없애면 안 됩니다!
            dataStorage.getConfig().set(key + ".health", newHp); // 줄어든 체력만 저장

            if (p != null) {
                reduceItemDurability(p);
                String progressBar = getProgressBar(newHp, correctMax);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§f[" + correctName + "] " + progressBar + " §f(" + newHp + " / " + correctMax + ")"));
            }
            // 타격 사운드만 재생 (블록은 그대로 유지)
            b.getWorld().playSound(b.getLocation(), hitSound, 1.0f, 0.8f);
        }
    }
    public void applyDmg(Block b, int d, Player p) {
        Block master = getMasterBlock(b);
        String key = "doors." + blockToKey(master);
        String typeName = master.getType().name().toUpperCase();

        // 1. 최대 체력 결정 (기준점)
        int maxHp;
        String displayName;

        if (b.getType().name().contains("_BED")) {
            return;
        }
        // 2. 조건문에 따라 값을 할당합니다.
        if (typeName.contains("ARMORED_DOOR")) {
            maxHp = 800;
            displayName = "합금 문";
        } else if (typeName.contains("BIG_DOOR")) {
            maxHp = 600;
            displayName = "차고 문";
        } else if (typeName.contains("IRON")) {
            maxHp = 200;
            displayName = "철 문";
        } else {
            maxHp = 100;
            displayName = "나무 문";
        }
        // [수정] dataStorage에서 현재 체력 가져오기
        int currentHp = dataStorage.getConfig().getInt(key + ".health", maxHp);
        int newHp = currentHp - d;

        if (newHp <= 0) {
            // [사운드] 파괴음 재생
            playModDoorSound(master.getLocation());
            removeLockEntity(master);

            // [데이터] 제거
            dataStorage.getConfig().set(key, null);

            // [블록 제거] 핵심: 차고문인지 일반 문인지 판별해서 지우기
            if (typeName.contains("BIG_DOOR")) {
                // 이미 구현하신 4x4 제거 로직 호출
                removeBigObject(master);
            } else {
                // 일반 2칸 높이 문 제거 (마스터부터 위쪽까지)
                master.setType(Material.AIR);
                Block top = master.getRelative(0, 1, 0);
                if (top.getType() == master.getType() || top.getType().name().contains("_DOOR")) {
                    top.setType(Material.AIR);
                }
            }

            if (p != null) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§c[System] " + displayName + "이(가) 파괴되었습니다!"));
            }
            master.getWorld().playSound(master.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 0.5f);
        } else {
            // [수정] dataStorage 체력 업데이트
            dataStorage.getConfig().set(key + ".health", newHp);

            // --- [소리 및 효과 로직 수정] ---
            // 1. 금속 재질(IRON, ARMORED, BIG)인 경우에만 커스텀 사운드 재생
            if (typeName.contains("IRON") || typeName.contains("ARMORED") || typeName.contains("BIG")) {
                playModDoorSound(b.getLocation());
            } else {
                // 나무 문 등 기타 재질은 기본 나무 소리만 재생
                b.getWorld().playSound(b.getLocation(), Sound.BLOCK_WOOD_HIT, 1.0f, 1.2f);
            }

            if (p != null) {
                reduceItemDurability(p);
                String progressBar = getProgressBar(newHp, maxHp);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§6[" + displayName + "] " + progressBar + " §f(" + newHp + " / " + maxHp + ")"));
            }

            b.getWorld().spawnParticle(Particle.CRIT, b.getLocation().add(0.5, 0.5, 0.5), 5);
        }
    }
    private void playModDoorSound(Location loc) {
        String soundName = "examplemod:custom_door_break";
        loc.getWorld().playSound(loc, soundName, 1.0f, 1.0f);
    }
    public void removeBigObject(Block master) {
        if (master == null) return;

        // 마스터 블록의 좌표와 타입
        Location masterLoc = master.getLocation();
        String masterTypeName = master.getType().name().toUpperCase();

        // 마스터 블록을 중심으로 전후좌우 4칸, 위로 4칸 범위를 탐색 (안전 범위)
        int radius = 4;
        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block rel = master.getRelative(x, y, z);

                    if (rel.getType() == Material.AIR) continue;

                    // [핵심] 이 블록이 현재 마스터와 연결된 블록인지 확인
                    if (isSameDoorSystem(rel, master)) {
                        rel.setType(Material.AIR);
                    }
                }
            }
        }
        // 마스터 본인도 확실히 제거
        master.setType(Material.AIR);
    }

    // 해당 블록이 이 마스터의 부속(더미)인지 확인하는 정밀 로직
    private boolean isSameDoorSystem(Block target, Block master) {
        // 1. 마스터 본인인 경우
        if (target.getLocation().equals(master.getLocation())) return true;

        // 2. 타겟이 DUMMY 블록인 경우
        if (target.getType().name().contains("DUMMY")) {
            // [중요] getMasterBlock을 호출했을 때 방금 지우려는 그 master가 나오는지 확인
            // 이 로직이 있어야 옆에 있는 다른 문의 DUMMY를 건드리지 않습니다.
            Block foundMaster = getMasterBlock(target);
            return foundMaster != null && foundMaster.getLocation().equals(master.getLocation());
        }

        return false;
    }
    // 시계 방향 계산 유틸리티
    private org.bukkit.block.BlockFace getRightFace(org.bukkit.block.BlockFace facing) {
        return switch (facing) {
            case NORTH -> org.bukkit.block.BlockFace.EAST;
            case EAST -> org.bukkit.block.BlockFace.SOUTH;
            case SOUTH -> org.bukkit.block.BlockFace.WEST;
            case WEST -> org.bukkit.block.BlockFace.NORTH;
            default -> org.bukkit.block.BlockFace.SELF;
        };
    }
    private boolean isDummyOfMaster(Block target, Block master) {
        // 1. 타겟 블록이 공기면 당연히 대상이 아님
        if (target.getType() == Material.AIR) return false;

        String targetName = target.getType().name().toUpperCase();

        // 2. 타겟 블록이 'DUMMY' 혹은 'DOOR_DUMMY' 이름을 포함하고 있는지 확인
        if (targetName.contains("DUMMY")) {
            // 3. (선택적) 더 정밀하게 하려면 마스터와 더미의 거리를 체크 (빅 도어는 보통 4칸 이내)
            double distance = target.getLocation().distance(master.getLocation());
            return distance <= 6.0; // 4x4 문이므로 대각선 거리 고려 약 6칸 이내면 같은 문으로 판단
        }

        return false;
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDummyBlockBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        String typeName = b.getType().name().toUpperCase();

        if (typeName.contains("DUMMY") || (typeName.contains("BIG_DOOR") && !b.equals(getMasterBlock(b)))) {
            e.setCancelled(true);

            Block master = getMasterBlock(b);

            long now = System.currentTimeMillis();
            if (breakCooldown.getOrDefault(p.getUniqueId(), 0L) > now) return;
            breakCooldown.put(p.getUniqueId(), now + 150);

            // 더미를 때려도 본체(master)에 데미지 전달
            applyDmg(master, 1, p);
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
        if (b == null) return "";
        return b.getWorld().getName() + "_" + b.getX() + "_" + b.getY() + "_" + b.getZ();
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
    public void reduceItemDurability(Player player) {
        if (player == null) return;
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return; // 크리에이티브 모드 제외

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;

        // 아이템의 메타데이터 확인 (내구도가 있는 아이템인지 확인)
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
            // 내구도 1 감소
            damageable.setDamage(damageable.getDamage() + 1);
            item.setItemMeta(damageable);

            // 내구도가 다 달았으면 파괴 소리와 함께 제거
            if (damageable.getDamage() >= item.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            }
        }
    }
    public void removeLockEntity(Block master) {
        String blockKey = blockToKey(master);
        FileConfiguration lockConfig = getLockConfig(); // lock.yml
        String path = "locks." + blockKey + ".uuids";

        if (lockConfig.contains(path)) {
            List<String> uuids = lockConfig.getStringList(path);
            for (String uuidStr : uuids) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Entity ent = Bukkit.getEntity(uuid);
                    if (ent != null) ent.remove();
                } catch (Exception ignored) {}
            }
            lockConfig.set("locks." + blockKey, null);
            saveLockConfig();
        }
    }
    public void createLockConfig() {
        lockFile = new File(getDataFolder(), "lock.yml");
        if (!lockFile.exists()) {
            lockFile.getParentFile().mkdirs();
            saveResource("lock.yml", false);
        }
        lockConfig = YamlConfiguration.loadConfiguration(lockFile);
    }

    public FileConfiguration getLockConfig() {
        return this.lockConfig;
    }
    public void saveLockConfig() {
        try {
            lockConfig.save(lockFile);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }
    public void sendMaintenancePacket(Player player, boolean isDecaying, String costStr) {
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(b);

            out.writeByte(6); // 패킷 ID
            out.writeBoolean(isDecaying);

            byte[] strBytes = costStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            writeVarInt(out, strBytes.length);
            out.write(strBytes);

            player.sendPluginMessage(this, "examplemod:main", b.toByteArray());
        } catch (Exception e) { e.printStackTrace(); }
    }
    private void writeVarInt(java.io.DataOutputStream out, int value) throws java.io.IOException {
        while ((value & -128) != 0) {
            out.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        out.writeByte(value);
    }
    public Location keyToLocation(String key) {
        try {
            String[] parts = key.split("_");
            if (parts.length < 4) return null;

            org.bukkit.World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;

            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);

            return new Location(world, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidGenerator();
    }
}

