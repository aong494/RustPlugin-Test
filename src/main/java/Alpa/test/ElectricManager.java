package Alpa.test;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlastFurnace;
import org.bukkit.block.DaylightDetector;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Powerable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ElectricManager implements Listener {

    private final main plugin;
    private final File blocksFile;
    private final FileConfiguration blocksConfig;

    // 메모리에 로드된 전력 컴포넌트 목록 (Location String -> Component)
    private final Map<String, ElectricComponent> components = new HashMap<>();
    private final Set<String> activeRelays = new HashSet<>();

    // 와이어 연결 중인 플레이어 상태 (UUID -> 시작 블록 위치)
    private final Map<UUID, Location> wiringSession = new HashMap<>();
    private final Set<String> poweredTurrets = new HashSet<>();

    // 설정값 캐싱
    private int solarGen, windGen, fuelGen;
    private int smallBatCap, largeBatCap;
    private int lampCons;

    public ElectricManager(main plugin) {
        this.plugin = plugin;
        this.blocksFile = new File(plugin.getDataFolder(), "electric_blocks.yml");
        if (!blocksFile.exists()) {
            try { blocksFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        this.blocksConfig = YamlConfiguration.loadConfiguration(blocksFile);

        loadConfigValues();
        loadBlocks();
        startTickTask();
    }

    private void loadConfigValues() {
        FileConfiguration config = plugin.getConfig(); // electric.yml에서 읽어온다고 가정 (main에서 로드 필요)
        // 만약 별도 파일이라면 그 파일을 로드하세요. 여기선 편의상 config 사용.
        solarGen = config.getInt("generation.solar", 10);
        windGen = config.getInt("generation.wind", 15);
        fuelGen = config.getInt("generation.fuel", 20);
        smallBatCap = config.getInt("storage.small_battery", 1000);
        largeBatCap = config.getInt("storage.large_battery", 5000);
        lampCons = config.getInt("consumption.lamp", 5);
    }

    // --- [1] 핵심 데이터 클래스 ---
    public enum CompType { GENERATOR, BATTERY, CONSUMER, SWITCH, RELAY, TURRET }

    public class ElectricComponent {
        Location loc;
        CompType type;
        String subType; // solar, wind, fuel, small_bat, large_bat 등
        int currentPower = 0;
        int maxPower = 0;
        double currentEfficiency = 1.0;
        List<String> outputs = new ArrayList<>(); // 연결된 블록들의 좌표 키(Key)

        public ElectricComponent(Location loc, CompType type, String subType) {
            this.loc = loc;
            this.type = type;
            this.subType = subType;
            if (type == CompType.BATTERY) {
                this.maxPower = subType.equals("small_bat") ? smallBatCap : largeBatCap;
            }
        }
    }

    // --- [2] 전력 틱 (1초마다 실행 권장) ---
    private void startTickTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Set<String> poweredLamps = new HashSet<>();
                poweredTurrets.clear();
                activeRelays.clear();

                Iterator<Map.Entry<String, ElectricComponent>> it = components.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, ElectricComponent> entry = it.next();
                    ElectricComponent comp = entry.getValue();
                    Material currentMat = comp.loc.getBlock().getType();
                    if (currentMat == Material.AIR) {
                        for (ElectricComponent other : components.values()) {
                            other.outputs.remove(entry.getKey());
                        }
                        it.remove();
                        saveBlocks();
                        continue;
                    }
                }

                // 1단계: 발전기 -> 배터리 충전
                for (ElectricComponent comp : components.values()) {
                    if (comp.type == CompType.GENERATOR) {
                        int generated = 0;
                        Block b = comp.loc.getBlock();

                        // 1. 태양광 로직
                        if (comp.subType.equals("solar") && b.getType() == Material.DAYLIGHT_DETECTOR) {
                            if (b.getWorld().getTime() < 12300 && b.getLightFromSky() > 10) {
                                generated = solarGen;
                            }
                        }
                        // 2. 풍력 로직 (태양광 if문 밖으로 빼냄)
                        else if (comp.subType.equals("wind")) {
                            // 틱마다 계산하지 않음! 이미 저장된 효율 값만 사용
                            generated = (int) (windGen * comp.currentEfficiency);

                            if (comp.currentEfficiency >= 1.0) {
                                b.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, b.getLocation().add(0.5, 1.2, 0.5), 1, 0.1, 0.1, 0.1, 0.05);
                            }
                        }
                        // 3. 화력 로직
                        else if (comp.subType.equals("fuel")) {
                            if (b.getState() instanceof Furnace furnace && furnace.getBurnTime() > 0) {
                                generated = fuelGen;
                            }
                        }

                        distributePower(comp, generated);
                    }
                }

                // 2단계: 배터리 -> 출력 (기존 유지)
                for (ElectricComponent comp : components.values()) {
                    if (comp.type == CompType.BATTERY && comp.currentPower > 0) {
                        distributeFromBattery(comp, poweredLamps, poweredTurrets);
                    }
                    spawnParticles(comp);
                }

                // 3단계: 미공급 램프 끄기 (기존 유지)
                for (ElectricComponent comp : components.values()) {
                    if (comp.type == CompType.CONSUMER) {
                        if (!poweredLamps.contains(locToKey(comp.loc))) {
                            setLampState(comp.loc.getBlock(), false);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void updateNearbyWindGenerators(Location triggerLoc) {
        int checkRadius = 5;
        for (ElectricComponent comp : components.values()) {
            if (comp.type == CompType.GENERATOR && comp.subType.equals("wind")) {
                if (comp.loc.getWorld().equals(triggerLoc.getWorld()) &&
                        comp.loc.distance(triggerLoc) <= checkRadius) {
                    double newEfficiency = calculateWindEfficiency(comp.loc.getBlock());
                    comp.currentEfficiency = newEfficiency;
                }
            }
        }
    }
    // 풍력 효율 계산
    private double calculateWindEfficiency(Block center) {
        int radius = 10;
        int obstacles = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    if (center.getRelative(x, y, z).getType().isSolid()) {
                        obstacles++;
                    }
                }
            }
        }

        // 장애물 1개당 효율 3% 감소, 최소 10% 유지
        double efficiency = 1.0 - (obstacles * 0.03);
        return Math.max(0.1, efficiency);
    }



    // 발전기에서 생산된 전력을 연결된 곳으로 보냄
    private void distributePower(ElectricComponent source, int amount) {
        if (amount <= 0 || source.outputs.isEmpty()) return;

        // 발전기는 최대 1개 연결이므로 0번 인덱스만 확인
        String targetKey = source.outputs.get(0);
        ElectricComponent target = components.get(targetKey);

        if (target != null && target.type == CompType.BATTERY) {
            // 전지에 충전
            int space = target.maxPower - target.currentPower;
            int toAdd = Math.min(space, amount);
            target.currentPower += toAdd;
        } else if (target != null && target.type == CompType.SWITCH) {
            // 발전기 -> 스위치 -> ??? (직결 구조 필요 시 구현, 보통 발전기->배터리->기기)
        }
    }

    // 배터리에서 전력 소비
    private void distributeFromBattery(ElectricComponent battery, Set<String> poweredLamps, Set<String> poweredTurrets) {
        for (String outKey : battery.outputs) {
            if (battery.currentPower <= 0) break;
            ElectricComponent target = components.get(outKey);
            if (target == null) continue;

            if (target.type == CompType.CONSUMER) {
                if (consumePowerForLamp(battery, target)) poweredLamps.add(outKey);
            }
            else if (target.type == CompType.TURRET) {
                if (consumePowerForTurret(battery, target)) {
                    poweredTurrets.add(outKey);
                }
            } else if (target.type == CompType.SWITCH) {
                handleSwitch(battery, target, poweredLamps, poweredTurrets);
            } else if (target.type == CompType.RELAY) {
                // 첫 번째 중계기 호출 (depth 1 시작)
                handleRelay(battery, target, poweredLamps, poweredTurrets, 1);
            }
        }
    }
    private boolean consumePowerForTurret(ElectricComponent battery, ElectricComponent turretComp) {
        int turretCons = 5; // 나중에 turret.yml에서 읽어오도록 수정 가능
        if (battery.currentPower >= turretCons) {
            battery.currentPower -= turretCons;
            return true;
        }
        return false;
    }

    private void handleRelay(ElectricComponent sourceBat, ElectricComponent relayComp, Set<String> poweredLamps, Set<String> poweredTurrets, int depth) {
        if (depth > 5) return; // 5단계를 넘으면 중단 (무한 루프 방지)

        String relayKey = locToKey(relayComp.loc);
        activeRelays.add(relayKey); // 현재 중계기가 전력을 전달 중임을 기록

        for (String nextKey : relayComp.outputs) {
            if (sourceBat.currentPower <= 0) break;
            ElectricComponent nextTarget = components.get(nextKey);
            if (nextTarget == null) continue;

            if (nextTarget.type == CompType.CONSUMER) {
                if (consumePowerForLamp(sourceBat, nextTarget)) poweredLamps.add(nextKey);
            }
            else if (nextTarget.type == CompType.TURRET) {
                if (consumePowerForTurret(sourceBat, nextTarget)) {
                    poweredTurrets.add(nextKey);
                }
            }
            else if (nextTarget.type == CompType.SWITCH) {
                handleSwitch(sourceBat, nextTarget, poweredLamps, poweredTurrets);
            }
            else if (nextTarget.type == CompType.RELAY) {
                handleRelay(sourceBat, nextTarget, poweredLamps, poweredTurrets, depth + 1);
            }
        }
    }
    public boolean isPowered(Location loc) {
        return poweredTurrets.contains(locToKey(loc));
    }

    // 전력 소비 성공 여부를 반환하도록 수정 (boolean)
    private boolean consumePowerForLamp(ElectricComponent battery, ElectricComponent lampComp) {
        Block lampBlock = lampComp.loc.getBlock();
        if (lampBlock.getType() != Material.REDSTONE_LAMP) return false;

        if (battery.currentPower >= lampCons) {
            battery.currentPower -= lampCons;
            setLampState(lampBlock, true);
            return true; // 전력 공급 성공
        }
        return false; // 전력 부족
    }

    private void handleSwitch(ElectricComponent sourceBat, ElectricComponent switchComp, Set<String> poweredLamps, Set<String> poweredTurrets) {
        Block b = switchComp.loc.getBlock();
        if (b.getType() != Material.LEVER) return;

        // 레버 데이터 확인
        Powerable leverData = (Powerable) b.getBlockData();
        if (!leverData.isPowered()) return; // 레버가 꺼져 있으면 전력 전달 중단

        // 스위치에 연결된 모든 출력물(outputs)을 확인
        for (String nextKey : switchComp.outputs) {
            if (sourceBat.currentPower <= 0) break; // 배터리 방전 시 중단

            ElectricComponent nextTarget = components.get(nextKey);
            if (nextTarget == null) continue;

            // 1. 전등일 경우
            if (nextTarget.type == CompType.CONSUMER) {
                if (consumePowerForLamp(sourceBat, nextTarget)) {
                    poweredLamps.add(nextKey);
                }
            }
            // 2. 터렛일 경우 (추가됨)
            else if (nextTarget.type == CompType.TURRET) {
                if (consumePowerForTurret(sourceBat, nextTarget)) {
                    poweredTurrets.add(nextKey);
                }
            }
            // 3. 중계기일 경우 (추가됨)
            else if (nextTarget.type == CompType.RELAY) {
                // 스위치 다음에 중계기가 있어도 전력이 흐르도록 handleRelay 호출
                handleRelay(sourceBat, nextTarget, poweredLamps, poweredTurrets, 1);
            }
        }
    }


    private void setLampState(Block block, boolean on) {
        if (!(block.getBlockData() instanceof Lightable)) return;
        Lightable lightable = (Lightable) block.getBlockData();

        if (lightable.isLit() != on) {
            lightable.setLit(on);
            block.setBlockData(lightable);
        }
    }

    // --- [3] 전선 도구 및 연결 로직 ---

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        Block b = e.getClickedBlock();

        if (b == null) return;

        // 1. 여기서 변수 key를 한 번만 선언합니다.
        String key = locToKey(b.getLocation());

        // --- [정보 확인 로직] ---
        if (components.containsKey(key)) {
            ElectricComponent comp = components.get(key);

            if (comp.type == CompType.BATTERY) {
                // 전선 도구가 아닐 때 정보 표시
                if (item == null || !isWireTool(item)) {
                    showBatteryInfo(p, comp);
                    return;
                }
            }
        }

        // --- [전선 도구 로직] ---
        // 이미 위에서 key가 정의되었으므로 다시 String key = ... 라고 쓰지 않습니다.
        if (item == null || !isWireTool(item)) return;

        e.setCancelled(true);

        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (components.containsKey(key)) {
                ElectricComponent comp = components.get(key);

                // 1. 내가 전기를 보내는 곳(Output)들의 불을 끕니다.
                for (String outputKey : comp.outputs) {
                    ElectricComponent target = components.get(outputKey);
                    if (target != null && target.type == CompType.CONSUMER) {
                        setLampState(target.loc.getBlock(), false);
                    }
                }
                comp.outputs.clear(); // 내보내는 선 제거

                // 2. [추가] 내가 전기를 받는 곳(Input)이라면, 나를 가리키는 모든 선을 제거합니다.
                // 전등을 때려서 끄고 싶을 때 필요한 로직입니다.
                for (ElectricComponent other : components.values()) {
                    if (other.outputs.contains(key)) {
                        other.outputs.remove(key);
                        // 내가 전등이었다면 이제 공급원이 끊겼으니 즉시 끕니다.
                        if (comp.type == CompType.CONSUMER) {
                            setLampState(comp.loc.getBlock(), false);
                        }
                    }
                }

                p.sendMessage("§c[Electric] §f연결을 모두 해제했습니다.");
                saveBlocks();
            }
            return;
        }

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            handleWiring(p, b); // 내부에서 key를 다시 계산하거나 전달
        }
    }

    // 코드 가독성을 위해 도구 판별 로직을 별도 메서드로 분리 (선택 사항)
    private boolean isWireTool(ItemStack item) {
        if (item.getType() != Material.STICK) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasCustomModelData()) return false;
        return item.getItemMeta().getCustomModelData() == 1;
    }

    // 배터리 정보 출력 로직 분리
    private void showBatteryInfo(Player p, ElectricComponent comp) {
        String subTypeName = comp.subType.equals("small_bat") ? "소형 전지" : "대형 전지";
        String color = "§a";
        double ratio = (double) comp.currentPower / comp.maxPower;
        if (ratio < 0.2) color = "§c";
        else if (ratio < 0.5) color = "§e";

        p.sendActionBar("§7[" + subTypeName + "] §f전력량: " + color + comp.currentPower + " §7/ §f" + comp.maxPower + " LP");
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 2.0f);
    }

    private void handleWiring(Player p, Block targetBlock) {
        CompType type = identifyType(targetBlock);
        if (type == null) return;

        String key = locToKey(targetBlock.getLocation());
        if (!components.containsKey(key)) {
            String subType = identifySubType(targetBlock);
            components.put(key, new ElectricComponent(targetBlock.getLocation(), type, subType));
        }

        if (!wiringSession.containsKey(p.getUniqueId())) {
            wiringSession.put(p.getUniqueId(), targetBlock.getLocation());
            p.sendMessage("§e[Electric] §f시작점 선택됨. 연결할 대상 블록을 우클릭하세요.");
        } else {
            Location startLoc = wiringSession.remove(p.getUniqueId());
            Location endLoc = targetBlock.getLocation();

            // [추가] 거리 제한 체크 (15블록)
            if (!startLoc.getWorld().equals(endLoc.getWorld()) || startLoc.distance(endLoc) > 15) {
                p.sendMessage("§c§l[!] §f전선이 너무 깁니다! (최대 거리: 15블록)");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1f);
                return;
            }

            if (startLoc.equals(endLoc)) {
                p.sendMessage("§c[Electric] §f자기 자신에게는 연결할 수 없습니다.");
                return;
            }

            ElectricComponent startComp = components.get(locToKey(startLoc));

            // 발전기 출력 제한 및 연결 저장
            if (startComp.type == CompType.GENERATOR && startComp.outputs.size() >= 1) {
                p.sendMessage("§c[!] 발전기는 하나의 대상에만 연결할 수 있습니다.");
                return;
            }

            if (!startComp.outputs.contains(key)) {
                startComp.outputs.add(key);
                p.sendMessage("§a§l[✔] §f연결 성공! (거리: " + String.format("%.1f", startLoc.distance(endLoc)) + "m)");
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 2f);
                saveBlocks();
            }
        }
    }

    // 블록 타입 판별기
    private CompType identifyType(Block b) {
        // 블록의 전체 이름(ID)을 가져옵니다 (예: doomsday_decoration:generator)
        String blockId = b.getType().getKey().toString().toUpperCase();

        // 1. 모드 블록 전지 체크
        if (blockId.contains("DOOMSDAY_DECORATION:GENERATOR") ||
                blockId.contains("DOOMSDAY_DECORATION:FIXEDGENERATOR")) {
            return CompType.BATTERY;
        }

        // 2. 기본 발전기 체크
        Material m = b.getType();
        if (m == Material.GOLD_BLOCK) return CompType.TURRET;
        if (m == Material.DAYLIGHT_DETECTOR || m == Material.EMERALD_BLOCK ||
                m == Material.FURNACE || m == Material.BLAST_FURNACE) return CompType.GENERATOR;

        // 3. 기타 기기
        if (m == Material.REDSTONE_LAMP) return CompType.CONSUMER;
        if (m == Material.LEVER) return CompType.SWITCH;
        if (m == Material.TRIPWIRE_HOOK) return CompType.RELAY;

        return null;
    }

    private String identifySubType(Block b) {
        // 1. 전체 키를 가져와서 대문자로 변환 (비교의 일관성)
        String blockId = b.getType().getKey().toString().toUpperCase();
        Material m = b.getType();

        // 2. 모드 블록 구분 (더 긴 이름인 FIXEDGENERATOR를 먼저 체크하는 것이 안전합니다)
        if (blockId.contains("FIXEDGENERATOR")) {
            return "large_bat";
        }
        if (blockId.contains("GENERATOR")) {
            return "small_bat";
        }

        // 3. 기존 발전기 구분
        if (m == Material.DAYLIGHT_DETECTOR) return "solar";
        if (m == Material.EMERALD_BLOCK) return "wind";
        if (m == Material.FURNACE || m == Material.BLAST_FURNACE) return "fuel";
        if (b.getType() == Material.TRIPWIRE_HOOK) return "relay";

        return "none";
    }

    // --- [4] 기타 유틸리티 (파괴, 저장, 로드, 파티클) ---
    @EventHandler
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        Location loc = e.getBlock().getLocation();
        String key = locToKey(loc);

        // 1. 설치된 블록이 풍력 발전기인 경우 컴포넌트 등록 및 초기 효율 설정
        CompType type = identifyType(e.getBlock());
        String subType = identifySubType(e.getBlock());

        if (type == CompType.GENERATOR && "wind".equals(subType)) {
            ElectricComponent newComp = new ElectricComponent(loc, type, "wind");
            newComp.currentEfficiency = calculateWindEfficiency(e.getBlock());
            components.put(key, newComp);
            saveBlocks(); // 파일에 즉시 저장
        }
        // 이 메서드는 components에 이미 등록된 녀석들을 순회하며 효율을 깎습니다.
        new BukkitRunnable() {
            @Override
            public void run() {
                updateNearbyWindGenerators(loc);
            }
        }.runTaskLater(plugin, 1L); // 블록이 완전히 배치된 후 계산하도록 1틱 지연
    }
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Location loc = e.getBlock().getLocation();
        String key = locToKey(loc);

        if (components.containsKey(key)) {
            components.remove(key);
            saveBlocks();
            e.getPlayer().sendMessage("§c[Electric] §f장치가 파괴되었습니다.");
        }

        // 블록이 부서졌으니 주변 풍력기 효율이 올라갈 수 있음
        new BukkitRunnable() {
            @Override
            public void run() {
                updateNearbyWindGenerators(loc);
            }
        }.runTaskLater(plugin, 1L);
    }

    private void spawnParticles(ElectricComponent comp) {
        Location center = comp.loc.clone().add(0.5, 1.1, 0.5);

        // 1. 발전기: 연결된 선이 있으면 표시
        if (comp.type == CompType.GENERATOR && !comp.outputs.isEmpty()) {
            comp.loc.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, center, 2, 0.2, 0.1, 0.2, 0.02);
        }
    }

    private String locToKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location keyToLoc(String key) {
        String[] parts = key.split(",");
        return new Location(Bukkit.getWorld(parts[0]),
                Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }

    public void saveBlocks() {
        blocksConfig.set("blocks", null); // 초기화
        for (Map.Entry<String, ElectricComponent> entry : components.entrySet()) {
            String key = entry.getKey();
            ElectricComponent comp = entry.getValue();

            String path = "blocks." + key;
            blocksConfig.set(path + ".type", comp.type.toString());
            blocksConfig.set(path + ".subType", comp.subType);
            blocksConfig.set(path + ".outputs", comp.outputs);

            // 배터리의 경우 현재 전력량 저장 (서버 재시작 시 유지)
            if (comp.type == CompType.BATTERY) {
                blocksConfig.set(path + ".power", comp.currentPower);
            }
        }
        try { blocksConfig.save(blocksFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public void loadBlocks() {
        if (!blocksConfig.contains("blocks")) return;
        ConfigurationSection sec = blocksConfig.getConfigurationSection("blocks");
        for (String key : sec.getKeys(false)) {
            try {
                Location loc = keyToLoc(key);
                CompType type = CompType.valueOf(sec.getString(key + ".type"));
                String subType = sec.getString(key + ".subType");

                ElectricComponent comp = new ElectricComponent(loc, type, subType);
                comp.outputs = sec.getStringList(key + ".outputs");

                if (type == CompType.BATTERY) {
                    comp.currentPower = sec.getInt(key + ".power", 0);
                }

                components.put(key, comp);
            } catch (Exception e) {
                System.out.println("[Electric] 블록 로드 중 오류 발생: " + key);
            }
        }
    }
}