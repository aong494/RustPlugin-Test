package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class BlockRegenManager {
    private final main plugin;
    private File file;
    private FileConfiguration config;
    private File pendingFile;
    private FileConfiguration pendingConfig;

    private static final Map<Material, TreeData> TREE_MAP = new HashMap<>();
    static {
        TREE_MAP.put(Material.OAK_LOG, new TreeData("a", Material.OAK_SAPLING));
        TREE_MAP.put(Material.SPRUCE_LOG, new TreeData("b", Material.SPRUCE_SAPLING));
        TREE_MAP.put(Material.BIRCH_LOG, new TreeData("c", Material.BIRCH_SAPLING));
        TREE_MAP.put(Material.JUNGLE_LOG, new TreeData("d", Material.JUNGLE_SAPLING));
        TREE_MAP.put(Material.ACACIA_LOG, new TreeData("e", Material.ACACIA_SAPLING));
        TREE_MAP.put(Material.MANGROVE_LOG, new TreeData("f", Material.MANGROVE_PROPAGULE));
        TREE_MAP.put(Material.CHERRY_LOG, new TreeData("g", Material.CHERRY_SAPLING));
    }

    public BlockRegenManager(main plugin) {
        this.plugin = plugin;
        setup();
    }

    public void setup() {
        // 기존 blocks.yml 설정
        if (file == null) file = new File(plugin.getDataFolder(), "blocks.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        config = YamlConfiguration.loadConfiguration(file);

        if (!config.contains("regen-blocks")) {
            config.set("regen-blocks.DIRT", 30);
            config.set("regen-blocks.GRASS_BLOCK", 30);
            save();
        }

        // 서버 리부팅 대비 pending_regens.yml 설정
        pendingFile = new File(plugin.getDataFolder(), "pending_regens.yml");
        if (!pendingFile.exists()) {
            try { pendingFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        pendingConfig = YamlConfiguration.loadConfiguration(pendingFile);
    }
    public Map<Material, TreeData> getTreeMap() {
        return TREE_MAP;
    }
    public int getTreeBulkDrop() {
        return config.getInt("tree-bulk-drop", 10);
    }

    public void save() {
        try { config.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    // 블록 파괴 시 기록
    public void addPendingRegen(Location loc, Material type, long respawnAt) {
        String key = locationToString(loc); // 형식: world_x_y_z
        pendingConfig.set("pending." + key + ".type", type.name());
        pendingConfig.set("pending." + key + ".time", respawnAt);
        savePending(); // 여기서 실제로 파일에 씁니다.
        // Bukkit.getLogger().info("데이터 기록됨: " + key); // 디버그용 (확인 후 삭제)
    }

    public void savePending() {
        try {
            pendingConfig.save(pendingFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public int getRegenTime(Material type) {
        return config.getInt("regen-blocks." + type.name(), -1);
    }

    public synchronized void removePendingRegen(Location loc) {
        String key = locationToString(loc);
        pendingConfig.set("pending." + key, null);
        savePending(); // 즉시 물리적 파일로 쓰기
    }

    public FileConfiguration getConfig() {
        return this.config;
    }

    public ConfigurationSection getPendingSection() {
        return pendingConfig.getConfigurationSection("pending");
    }

    private String locationToString(Location loc) {
        return loc.getWorld().getName() + "_" + loc.getBlockX() + "_" + loc.getBlockY() + "_" + loc.getBlockZ();
    }
    public void handleTreeLogic(Block block) {
        String blockName = getBlockName(block);
        ConfigurationSection treeTypes = config.getConfigurationSection("tree-settings.types");
        if (treeTypes == null) return;

        for (String key : treeTypes.getKeys(false)) {
            String logId = treeTypes.getString(key + ".log");
            List<String> stages = treeTypes.getStringList(key + ".stages");
            String saplingId = treeTypes.getString(key + ".sapling");

            int currentIndex = -1;

            // 1. 현재 블록이 원본 로그인가?
            if (blockName.equalsIgnoreCase(logId)) {
                currentIndex = -1;
            }
            // 2. 현재 블록이 리스트의 몇 단계인가?
            else {
                for (int i = 0; i < stages.size(); i++) {
                    if (stages.get(i).equalsIgnoreCase(blockName)) {
                        currentIndex = i;
                        break;
                    }
                }
            }

            // 나무를 찾았다면 로직 실행
            if (currentIndex != -1 || blockName.equalsIgnoreCase(logId)) {
                processTreeStep(block, logId, stages, saplingId, currentIndex);
                return;
            }
        }
    }

    private void processTreeStep(Block block, String logId, List<String> stages, String saplingId, int currentIndex) {
        Material logMat = Material.matchMaterial(logId);
        if (logMat == null) logMat = Material.OAK_LOG;

        // 드랍템 (해당 로그 1개)
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(logMat, 1));
        block.getWorld().playSound(block.getLocation(), "minecraft:rust.tree_bonus_damage", org.bukkit.SoundCategory.BLOCKS, 1.0f, 1.0f);
        // 마지막 단계인 경우 (리스트의 마지막 인덱스)
        if (currentIndex == stages.size() - 1) {
            triggerTreeFelling(block, stages, logId, saplingId);
            int bulk = config.getInt("tree-settings.bulk-drop-amount", 10);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(logMat, bulk - 1));
        } else {
            // 다음 단계로 교체
            String nextStage = stages.get(currentIndex + 1);
            replaceBlockLater(block, nextStage);
        }
    }

    // 26방향 연쇄 파괴 및 묘목 설치
    public void triggerTreeFelling(Block startBlock, List<String> stages, String logId, String saplingId) {
        Set<Block> toRemove = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        queue.add(startBlock);

        startBlock.getWorld().playSound(
                startBlock.getLocation(),
                "minecraft:rust.tree_fall",
                org.bukkit.SoundCategory.BLOCKS,
                1.0f,
                1.0f
        );

        while (!queue.isEmpty()) {
            Block current = queue.poll();
            if (toRemove.contains(current)) continue;

            String currentName = getBlockName(current);
            if (isRelated(currentName, logId, stages)) {
                toRemove.add(current);
                if (toRemove.size() > 700) break;

                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            if (x == 0 && y == 0 && z == 0) continue;
                            queue.add(current.getRelative(x, y, z));
                        }
                    }
                }
            }
        }

        toRemove.forEach(b -> b.setType(Material.AIR));
        Material saplingMat = Material.matchMaterial(saplingId);
        if (saplingMat != null) startBlock.setType(saplingMat);
    }
    private boolean isRelated(String name, String logId, List<String> stages) {
        if (name.equalsIgnoreCase(logId)) return true;
        String lowerName = name.toLowerCase();
        if (lowerName.contains("leaves") || lowerName.contains("cherry_leaves") || lowerName.contains("mangrove_leaves")) return true;
        for (String stage : stages) {
            if (stage.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private String getBlockName(Block block) { return block.getType().getKey().toString(); }

    private void replaceBlockLater(Block block, String materialName) {
        Material mat = Material.matchMaterial(materialName);

        if (mat != null) {
            block.setType(mat, false);
        } else {
            try {
                block.setBlockData(Bukkit.createBlockData(materialName), false);
            } catch (IllegalArgumentException e) {
                // 블록 이름을 찾을 수 없을 때만 콘솔에 경고 표시
                Bukkit.getLogger().warning("[Rust] 잘못된 블록 이름: " + materialName);
            }
        }
    }

    // 내부 데이터 클래스
    private static class TreeData {
        public final String suffix;
        public final Material sapling;
        public TreeData(String suffix, Material sapling) { this.suffix = suffix; this.sapling = sapling; }
    }
}