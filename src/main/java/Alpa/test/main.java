package Alpa.test;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;



public final class main extends JavaPlugin implements Listener, CommandExecutor {

    private CraftManager craftManager;
    public BedManager bedManager;
    public GroupManager groupManager;
    private final int MAX_HP = 100;
    private TemperatureManager tempManager;
    private RadiationManager radManager;
    public SpawnManager spawnManager;
    public RespawnManager respawnManager;
    public BlockRegenManager blockRegenManager;
    public InteractBlockManager interactBlockManager;
    public BlockListener blockListener;
    private final java.util.Set<String> processingLocations = new java.util.HashSet<>();
    public RaidManager raidManager;

    @Override
    public void onEnable() {
        this.groupManager = new GroupManager(this);
        this.bedManager = new BedManager(this);
        this.craftManager = new CraftManager(this);
        this.tempManager = new TemperatureManager(this);
        this.radManager = new RadiationManager(this);
        this.spawnManager = new SpawnManager(this);
        this.spawnManager.loadAllSpawns();
        this.respawnManager = new RespawnManager(this);
        this.blockRegenManager = new BlockRegenManager(this);
        this.interactBlockManager = new InteractBlockManager(this);
        this.blockListener = new BlockListener(this);
        this.raidManager = new RaidManager(this);

        addDefaultSettings();
        getServer().getPluginManager().registerEvents(this.bedManager, this);
        getServer().getPluginManager().registerEvents(new PlankHealthManager(this), this);
        getServer().getPluginManager().registerEvents(new PlankToIronManager(this), this);
        getServer().getPluginManager().registerEvents(new IronBlockManager(this), this);
        getServer().getPluginManager().registerEvents(new JoinManager(this), this);
        getServer().getPluginManager().registerEvents(new EnvironmentManager(this), this);
        getServer().getPluginManager().registerEvents(this.craftManager, this);
        getServer().getPluginManager().registerEvents(new ReinforcedManager(this), this);
        getServer().getPluginManager().registerEvents(new MobListener(this), this);
        getServer().getPluginManager().registerEvents(new RespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new InteractBlockListener(this), this);
        getServer().getPluginManager().registerEvents(this.blockListener, this);
        getServer().getPluginManager().registerEvents(new RaidListener(this), this);

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

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (this.spawnManager != null) {
                this.spawnManager.loadAllSpawns();
            }
        }, 40L);

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

    public RaidManager getRaidManager() {
        return raidManager;
    }

    @Override
    public void onDisable() {
        if (blockRegenManager != null) {
            blockRegenManager.savePending();
        }

        // ⭐ RaidManager의 cleanup을 호출하여 모든 인스턴스의 홀로그램과 태스크를 정리
        if (this.raidManager != null) {
            this.raidManager.cleanup();

            // 혹시 모를 잔해들(태그된 엔티티) 최종 청소
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof ArmorStand && entity.getScoreboardTags().contains("raid_hologram")) {
                        entity.remove();
                    }
                }
            }
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
        if (!getConfig().contains("damage-settings")) {
            getConfig().set("damage-settings.NETHERITE_AXE", 15);
            getConfig().set("damage-settings.DIAMOND_AXE", 10);
            getConfig().set("damage-settings.DEFAULT", 1);
        }
        // [추가] 판자 회복량 설정
        if (!getConfig().contains("plank-settings")) {
            getConfig().set("plank-settings.repair-amount", 10); // 판자 1개당 회복될 체력
            getConfig().set("plank-settings.repair-cost", 1);   // 수리 1회당 소모되는 판자 개수
        }
        if (!getConfig().contains("tnt-settings")) {
            getConfig().set("tnt-settings.기본", 10);
        }
        getConfig().addDefault("settings.piston-enabled", true);
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    @EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        if (this.spawnManager == null || this.spawnManager.getConfig() == null) return;
        if (!this.spawnManager.getConfig().contains("spawns")) return;

        for (String key : this.spawnManager.getConfig().getConfigurationSection("spawns").getKeys(false)) {
            String path = "spawns." + key;
            String worldName = this.spawnManager.getConfig().getString(path + ".world");

            if (!event.getWorld().getName().equals(worldName)) continue;

            double x = this.spawnManager.getConfig().getDouble(path + ".x");
            double z = this.spawnManager.getConfig().getDouble(path + ".z");

            int chunkX = (int) Math.floor(x) / 16;
            int chunkZ = (int) Math.floor(z) / 16;

            if (event.getChunk().getX() == chunkX && event.getChunk().getZ() == chunkZ) {
                // 좌표별 고유 ID 생성 (중복 실행 방지용)
                String locId = worldName + "_" + (int)x + "_" + (int)z;

                // 이미 이 좌표에 대해 소환 작업이 진행 중이라면 스킵
                if (processingLocations.contains(locId)) continue;

                Location loc = new Location(
                        event.getWorld(), x,
                        this.spawnManager.getConfig().getDouble(path + ".y"), z
                );

                // 1. 먼저 부활 대기 중인지 확인
                if (this.spawnManager.isPending(loc)) continue;

                // 2. 소환 로직 시작 (처리 목록에 추가)
                processingLocations.add(locId);

                // 1틱 뒤에 엔티티 존재 여부를 확인하고 소환
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    try {
                        // 주변 2블록 내에 이미 본체가 있는지 최종 확인
                        boolean exists = loc.getWorld().getNearbyEntities(loc, 1.5, 3.0, 1.5).stream()
                                .anyMatch(e -> e.getScoreboardTags().contains("custom_mob_base"));

                        if (!exists) {
                            int modelData = this.spawnManager.getConfig().getInt(path + ".modelData");
                            CustomMobSummoner.spawnStaticMob(this, loc, modelData);
                        }
                    } finally {
                        // 소환 작업이 끝나면 (성공하든 실패하든) 2초 후에 목록에서 제거하여 다음 로드에 대비
                        Bukkit.getScheduler().runTaskLater(this, () -> processingLocations.remove(locId), 40L);
                    }
                }, 1L);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;

        // 3. [추가] 커스텀 몹 생성 명령어
        if (label.equalsIgnoreCase("커스텀몹")) {
            if (!player.isOp()) return true;
            if (args.length < 2) {
                player.sendMessage("§c사용법: /커스텀몹 <생성|설치> <모델번호>");
                return true;
            }

            String action = args[0];
            int modelData;
            try {
                modelData = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c숫자를 입력해주세요.");
                return true;
            }

            if (action.equalsIgnoreCase("생성")) {
                // 1회성 소환
                CustomMobSummoner.spawnStaticMob(this, player.getLocation(), modelData);
                player.sendMessage("§a[System] §f" + modelData + "번 일회용 몹을 소환했습니다.");
            }
            else if (action.equalsIgnoreCase("설치")) {
                // 좌표 저장 및 지속 소환 등록
                spawnManager.addSpawnLocation(player.getLocation(), modelData);
                player.sendMessage("§b[System] §f" + modelData + "번 리스폰 몹을 설치했습니다. (좌표 저장됨)");
            }
            return true;
        }
        if (label.equalsIgnoreCase("레이드박스")) {
            if (!player.isOp()) {
                player.sendMessage("§c[System] 관리자 전용 명령어입니다.");
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("§c사용법: /레이드박스 <설치|확인|삭제> <이름>");
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

    @EventHandler
    public void onDoorRedstone(org.bukkit.event.block.BlockRedstoneEvent e) {
        Block b = e.getBlock();

        // 1. 신호가 들어가는 블록이 문인지 확인
        if (b.getType().name().contains("_DOOR")) {
            Block bottom = getBottom(b);
            String key = "doors." + blockToKey(bottom);

            // 2. 해당 문이 잠금 데이터(데이터베이스/Config)에 존재한다면
            if (getConfig().contains(key)) {
                // 3. 신호의 강도를 0으로 만들어 문이 열리는 것을 방지
                if (e.getNewCurrent() > 0) {
                    e.setNewCurrent(0);
                }
            }
        }
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

    private boolean canAccess(Player p, String locKey) {
        if (p.isOp()) return true;

        String ownerUUIDStr = getConfig().getString(locKey + ".owner");
        if (ownerUUIDStr == null) return true;

        UUID ownerUUID = UUID.fromString(ownerUUIDStr);
        if (p.getUniqueId().equals(ownerUUID)) return true;

        // OfflinePlayer를 이용해 그룹 체크 (매개변수 타입 에러 해결)
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUUID);

        // GroupManager의 getPlayerGroup이 Player 객체만 받는 경우를 대비해 처리
        String ownerGroup = null;
        if (owner.isOnline()) {
            ownerGroup = groupManager.getPlayerGroup(owner.getPlayer());
        } else {
            // OfflinePlayer 대응 메서드가 없다면 UUID로 찾는 로직이 GroupManager에 있어야 합니다.
            // 현재는 에러 방지를 위해 아래와 같이 작성합니다.
            ownerGroup = groupManager.getPlayerGroup((Player) owner); // 캐스팅 시도 또는 별도 로직 필요
        }

        String playerGroup = groupManager.getPlayerGroup(p);

        return ownerGroup != null && ownerGroup.equals(playerGroup);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Block b = e.getClickedBlock();
        if (b == null) return;

        Player player = e.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // --- [1. 좌클릭: 나무판자 수리 로직] ---
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (b.getType().name().contains("_PLANKS") && mainHand.getType() == Material.STICK) {
                String pKey = "planks." + blockToKey(b);

                if (getConfig().contains(pKey)) {
                    // 수리 로직 실행 시 이벤트 취소 (블록 파괴/타격 방지)
                    e.setCancelled(true);

                    int maxHp = 50;
                    int currentHp = getConfig().getInt(pKey + ".health", maxHp);

                    if (currentHp >= maxHp) {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§a[Plank] 이미 체력이 가득 차 있습니다."));
                        return;
                    }

                    int repairCost = getConfig().getInt("plank-settings.repair-cost", 1);
                    int repairAmt = getConfig().getInt("plank-settings.repair-amount", 10);

                    if (hasEnoughPlanks(player, repairCost)) {
                        removePlanks(player, repairCost);
                        int newHp = Math.min(maxHp, currentHp + repairAmt);
                        getConfig().set(pKey + ".health", newHp);
                        saveConfig();

                        String progressBar = getProgressBar(newHp, maxHp);
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                TextComponent.fromLegacyText("§b[Plank] 수리 완료! (-" + repairCost + "개) " + progressBar + " §f(" + newHp + " / " + maxHp + ")"));
                        player.playSound(b.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 1.2f);
                        b.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, b.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3);
                    } else {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                TextComponent.fromLegacyText("§c[Plank] 수리하려면 나무판자가 " + repairCost + "개 필요합니다!"));
                    }
                }
            }
            return; // 좌클릭 처리가 끝났으므로 종료
        }

        // --- [2. 우클릭: 문 잠금 및 상호작용 로직] ---
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // 문 관련 로직이므로 블록이 문이 아니면 여기서 종료
            if (!b.getType().name().contains("_DOOR")) return;

            Block bottom = getBottom(b);
            String key = "doors." + blockToKey(bottom);

            // 막대기 체크 (상태 확인 및 철거)
            if (mainHand.getType() == Material.STICK) {
                if (getConfig().contains(key)) {
                    e.setCancelled(true);
                    if (player.isSneaking()) {
                        if (canAccess(player, key)) {
                            getConfig().set(key, null);
                            saveConfig();
                            bottom.setType(Material.AIR);
                            bottom.getRelative(0, 1, 0).setType(Material.AIR);
                            player.sendMessage("§a[Lock] 문을 성공적으로 철거했습니다.");
                            player.playSound(bottom.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.0f, 0.8f);
                        } else {
                            player.sendMessage("§c[Lock] 타인의 문은 철거할 수 없습니다.");
                        }
                    } else {
                        int hp = getConfig().getInt(key + ".health", MAX_HP);
                        String progressBar = getProgressBar(hp, MAX_HP);
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                TextComponent.fromLegacyText("§6[Lock] 남은 체력: " + progressBar + " §f(" + hp + " / " + MAX_HP + ")"));
                    }
                }
                return;
            }

            // 잠금 설정 로직 (Blaze Rod 등)
            if (!getConfig().contains(key)) {
                if (mainHand.getType() == Material.BLAZE_ROD) {
                    mainHand.setAmount(mainHand.getAmount() - 1);
                    getConfig().set(key + ".owner", player.getUniqueId().toString());
                    getConfig().set(key + ".health", MAX_HP);
                    saveConfig();
                    String groupName = groupManager.getPlayerGroup(player);
                    player.sendMessage(groupName != null ? "§6[Lock] '" + groupName + "' 그룹 전용 문으로 잠갔습니다!" : "§6[Lock] 개인용 문으로 잠갔습니다!");
                    e.setCancelled(true);
                } else if (b.getType() == Material.IRON_DOOR) {
                    e.setCancelled(true);
                    toggleDoor(bottom);
                }
                return;
            }

            // 권한 확인 및 문 열기
            if (canAccess(player, key)) {
                if (b.getType() == Material.IRON_DOOR) {
                    e.setCancelled(true);
                    toggleDoor(bottom);
                }
            } else {
                e.setCancelled(true);
                player.sendMessage("§c[Lock] 권한이 없습니다.");
            }
        }
    }

    private boolean hasEnoughPlanks(Player player, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType().name().contains("_PLANKS")) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    private void removePlanks(Player player, int amount) {
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

        if (type.equals("door")) {
            getConfig().set("doors." + key, null);
            b.setType(Material.AIR);
            Block top = b.getRelative(0, 1, 0);
            if (top.getType().name().contains("_DOOR")) top.setType(Material.AIR);
            world.playSound(b.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.0f, 0.5f);
        } else {
            // [수정] 블록 타입 체크 후 사운드 결정
            Sound s = (b.getType() == Material.IRON_BLOCK) ? Sound.BLOCK_METAL_BREAK : Sound.BLOCK_WOOD_BREAK;
            getConfig().set("planks." + key, null);
            b.setType(Material.AIR);
            world.playSound(b.getLocation(), s, 1.0f, 0.5f);
        }
        saveConfig(); // 변경사항 저장
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Player player = e.getPlayer();
        String key = "";

        // 1. 문(Door) 처리 로직
        if (b.getType().name().contains("_DOOR")) {
            Block bottom = getBottom(b);
            key = "doors." + blockToKey(bottom);
            if (!getConfig().contains(key)) return;

            e.setCancelled(true); // 즉시 파괴 방지
            int dmg = getWeaponDamage(player);
            applyDmg(bottom, dmg, player); // 문 데미지 적용
        }

        // 2. 철 블록(IRON_BLOCK) 처리 로직 추가
        else if (b.getType() == Material.IRON_BLOCK) {
            key = "planks." + blockToKey(b); // IronBlockManager와 동일한 키 사용
            if (!getConfig().contains(key)) return;

            e.setCancelled(true); // 즉시 파괴 방지
            int dmg = getWeaponDamage(player);
            applyPlankDmg(b, dmg, player); // 판자/블록 데미지 적용 메서드 활용
        }
    }

    // 무기별 데미지 값을 가져오는 보조 메서드 (코드 중복 방지)
    private int getWeaponDamage(Player player) {
        return getConfig().getInt("damage-settings." + player.getInventory().getItemInMainHand().getType().name(),
                getConfig().getInt("damage-settings.DEFAULT", 1));
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        int dmg = getConfig().getInt("tnt-settings.기본", 10);
        if (e.getEntity() instanceof org.bukkit.entity.TNTPrimed tnt && tnt.getCustomName() != null) {
            dmg = getConfig().getInt("tnt-settings." + tnt.getCustomName(), dmg);
        }

        // BedManager는 double을 받고, Plank는 int를 받으므로 여기서 정리
        final double bedDmg = (double) dmg;
        final int plankDmg = dmg;

        e.blockList().removeIf(b -> {
            // 침대 처리
            if (b.getType().name().contains("_BED")) {
                if (this.bedManager != null) {
                    this.bedManager.applyBedExplodeDamage(b, bedDmg);
                }
                return true;
            }

            // 문 처리
            if (b.getType().name().contains("_DOOR")) {
                applyDmg(getBottom(b), plankDmg, null);
                return true;
            }

            // 나무판자 처리
            if (b.getType().name().contains("_PLANKS")) {
                String pKey = "planks." + blockToKey(b);
                if (getConfig().contains(pKey)) {
                    applyPlankDmg(b, plankDmg, null); // 367번 라인 에러 해결 (int로 전달)
                    return true;
                }
            }
            return true; // 지형 보호
        });
    }

    // 나무판자 데미지 통합 메서드 (PlankHealthManager에서도 호출 가능하도록 public)
    public void applyPlankDmg(Block b, int d, Player p) {
        String key = "planks." + blockToKey(b);

        // 1. 블록 타입에 따른 최대 체력 설정 (구분 로직)
        int maxHp = (b.getType() == Material.IRON_BLOCK) ? 200 : 50;

        // 현재 체력 가져오기 (데이터가 없으면 maxHp로 초기화)
        int currentHp = getConfig().getInt(key + ".health", maxHp);
        int newHp = currentHp - d;

        if (newHp <= 0) {
            // 파괴 처리
            getConfig().set(key, null);
            b.setType(Material.AIR);

            // 블록 타입에 따른 파괴 소리 구분
            Sound breakSound = (b.getType() == Material.IRON_BLOCK) ? Sound.BLOCK_METAL_BREAK : Sound.BLOCK_WOOD_BREAK;
            b.getWorld().playSound(b.getLocation(), breakSound, 1.0f, 1.0f);

            if (p != null) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§c[System] 블록이 완전히 파괴되었습니다!"));
            }
        } else {
            // 체력 업데이트 및 표시
            getConfig().set(key + ".health", newHp);

            if (p != null) {
                // 블록 타입에 따른 접두사 및 색상 구분
                String prefix = (b.getType() == Material.IRON_BLOCK) ? "§6[Iron]" : "§6[Plank]";
                String progressBar = getProgressBar(newHp, maxHp); // 수정된 maxHp 전달

                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(prefix + " 남은 체력 " + progressBar + " §f(" + newHp + " / " + maxHp + ")"));
            }

            // 타격 소리 구분
            Sound hitSound = (b.getType() == Material.IRON_BLOCK) ? Sound.BLOCK_METAL_HIT : Sound.BLOCK_WOOD_HIT;
            b.getWorld().playSound(b.getLocation(), hitSound, 1.0f, 0.8f);
        }
        saveConfig();
    }

    private void applyDmg(Block b, int d, Player p) {
        String key = "doors." + blockToKey(b);
        int hp = getConfig().getInt(key + ".health", MAX_HP) - d;

        if (hp <= 0) {
            getConfig().set(key, null);
            b.setType(Material.AIR);
            b.getRelative(0,1,0).setType(Material.AIR);

            if (p != null) {
                // 파괴 시 액션바 메시지
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§c[Lock] 문이 완전히 파괴되었습니다!"));
            }
        } else {
            getConfig().set(key + ".health", hp);

            if (p != null) {
                // [수정] 채팅 대신 화면 중앙 액션바에 표시
                String progressBar = getProgressBar(hp, MAX_HP);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§6[Lock] 남은 체력: " + progressBar + " §f(" + hp + " / " + MAX_HP + ")"));
            }

            // 문 타격 소리 및 효과 유지
            b.getWorld().playSound(b.getLocation(), Sound.BLOCK_METAL_HIT, 1.0f, 0.5f);
            b.getWorld().spawnParticle(Particle.CRIT, b.getLocation().add(0.5, 0.5, 0.5), 10);
        }
        saveConfig();
    }

    private void toggleDoor(Block b) {
        if (!(b.getBlockData() instanceof Door data)) return;
        boolean next = !data.isOpen();
        getServer().getScheduler().runTaskLater(this, () -> {
            update(b, next); update(b.getRelative(0,1,0), next);
            b.getWorld().playSound(b.getLocation(), next ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 1f);
        }, 1L);
    }

    private void update(Block b, boolean o) {
        if (b.getBlockData() instanceof Door d) {
            d.setOpen(o); d.setPowered(o);
            b.setBlockData(d, false);
            b.getWorld().getPlayers().forEach(p -> p.sendBlockChange(b.getLocation(), d));
        }
    }

    private Block getBottom(Block b) {
        return (b.getBlockData() instanceof Bisected bi && bi.getHalf() == Bisected.Half.TOP) ? b.getRelative(0,-1,0) : b;
    }

    public String blockToKey(Block b) { // private -> public
        Location l = b.getLocation();
        return l.getWorld().getName() + "_" + l.getBlockX() + "_" + l.getBlockY() + "_" + l.getBlockZ();
    }
    // 블럭 설치 시 보호 구역 확인
    @EventHandler(priority = EventPriority.HIGH) // PlankHealthManager보다 먼저 실행되도록 설정
    public void onBlockPlace(BlockPlaceEvent e) {
        // 덫상자 설치 시에는 보호 체크를 통과시킴
        if (e.getBlock().getType() == Material.TRAPPED_CHEST) {
            return;
        }

        if (isProtectedLocation(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true); // 이벤트를 취소함
            e.getPlayer().sendMessage("§c[Protect] 이 지역은 보호 구역입니다.");
        }
    }

    // 1. 덫상자 클릭 시 권한 부여 (방문자 등록)
    @EventHandler
    public void onProtectorInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.TRAPPED_CHEST) return;

        String key = "protectors." + blockToKey(b);
        if (!getConfig().contains(key)) return;

        Player p = e.getPlayer();
        List<String> authUsers = getConfig().getStringList(key + ".authorized_users");
        String pUUID = p.getUniqueId().toString();

        // 명단에 없으면 추가
        if (!authUsers.contains(pUUID)) {
            authUsers.add(pUUID);
            getConfig().set(key + ".authorized_users", authUsers);
            saveConfig();
            p.sendMessage("§a[Protect] 덫상자를 확인하여 이 지역의 보호 권한을 획득했습니다!");
        }
    }

    // 2. 보호 구역 확인 로직 수정 (명단 체크)
    private boolean isProtectedLocation(Player player, Location loc) {
        if (player.isOp()) return false;

        ConfigurationSection protectors = getConfig().getConfigurationSection("protectors");
        if (protectors == null) return false;

        for (String key : protectors.getKeys(false)) {
            String worldName = protectors.getString(key + ".world");
            if (worldName == null || !worldName.equals(loc.getWorld().getName())) continue;

            int x = protectors.getInt(key + ".x");
            int y = protectors.getInt(key + ".y");
            int z = protectors.getInt(key + ".z");
            Location protectorLoc = new Location(loc.getWorld(), x, y, z);

            if (protectorLoc.distance(loc) <= 15) {
                // 수정된 부분: 해당 상자의 권한 명단(authorized_users)에 포함되어 있는지 확인
                List<String> authUsers = getConfig().getStringList("protectors." + key + ".authorized_users");
                if (authUsers.contains(player.getUniqueId().toString())) {
                    return false; // 명단에 있으면 보호 구역이 아님 (설치/파괴 가능)
                }
                return true; // 명단에 없으면 보호 구역임
            }
        }
        return false;
    }

    // 2. 덫상자 파괴 및 데이터 삭제 로직 (권한 확인 추가)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectorBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() == Material.TRAPPED_CHEST) {
            String key = "protectors." + blockToKey(e.getBlock());

            // 해당 좌표에 저장된 보호 구역 데이터가 있는지 확인
            if (getConfig().contains(key)) {
                Player p = e.getPlayer();
                List<String> authUsers = getConfig().getStringList(key + ".authorized_users");

                // 권한자 혹은 OP만 파괴 가능
                if (p.isOp() || authUsers.contains(p.getUniqueId().toString())) {
                    // 핵심: 해당 덫상자 키 전체를 삭제
                    getConfig().set(key, null);
                    saveConfig();
                    p.sendMessage("§e[Protect] 보호 구역 데이터가 삭제되었습니다. 이제 보호가 해제됩니다.");
                } else {
                    // 권한 없는 사람이 부수려 하면 취소
                    e.setCancelled(true);
                    p.sendMessage("§c[Protect] 이 보호 구역을 해제할 권한이 없습니다!");
                }
            }
        }
    }

    // 3. 덫상자 설치 시 초기 명단 설정
    @EventHandler
    public void onProtectorPlace(BlockPlaceEvent e) {
        if (e.getBlock().getType() == Material.TRAPPED_CHEST) {
            Player p = e.getPlayer();
            Location newLoc = e.getBlock().getLocation();

            // 1. 겹침 검사: 기존에 설치된 덫상자들 중 반경 30블록 이내에 있는 것이 있는지 확인
            // (각 상자가 15블록씩 보호하므로, 상자 간 거리는 최소 30블록이어야 영역이 겹치지 않음)
            ConfigurationSection protectors = getConfig().getConfigurationSection("protectors");
            if (protectors != null) {
                for (String key : protectors.getKeys(false)) {
                    String worldName = protectors.getString(key + ".world");
                    if (worldName == null || !worldName.equals(newLoc.getWorld().getName())) continue;

                    int x = protectors.getInt(key + ".x");
                    int y = protectors.getInt(key + ".y");
                    int z = protectors.getInt(key + ".z");
                    Location existingLoc = new Location(newLoc.getWorld(), x, y, z);

                    // 상자 간의 직선 거리가 30블록 이하이면 겹치는 것으로 간주
                    if (existingLoc.distance(newLoc) < 30) {
                        e.setCancelled(true);
                        p.sendMessage("§c[Protect] 주변에 다른 보호 구역(덫상자)이 너무 가깝습니다! (최소 30블록 간격 필요)");
                        return;
                    }
                }
            }

            // 2. 설치 및 데이터 저장 (겹치지 않을 때만 실행됨)
            String key = "protectors." + blockToKey(e.getBlock());
            getConfig().set(key + ".world", newLoc.getWorld().getName());
            getConfig().set(key + ".x", newLoc.getBlockX());
            getConfig().set(key + ".y", newLoc.getBlockY());
            getConfig().set(key + ".z", newLoc.getBlockZ());

            // 초기 권한 명단에 설치자 본인 추가
            List<String> authUsers = new ArrayList<>();
            authUsers.add(p.getUniqueId().toString());
            getConfig().set(key + ".authorized_users", authUsers);

            saveConfig();
            p.sendMessage("§a[Protect] 덫상자 보호 구역이 설정되었습니다. (반경 15블록)");
        }
    }
    // 체력을 시각적인 바로 변환해주는 메서드
    private String getProgressBar(int current, int max) {
        int bars = 10; // 총 칸 수
        int completedBars = (int) (((double) current / max) * bars);
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < bars; i++) {
            if (i < completedBars) sb.append("§a■"); // 남은 체력 (초록색)
            else sb.append("§7■"); // 깎인 체력 (회색)
        }
        return sb.toString();
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

