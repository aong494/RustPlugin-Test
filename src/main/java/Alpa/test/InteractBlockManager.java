package Alpa.test;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class InteractBlockManager {
    private final main plugin;
    private File file;
    private FileConfiguration config;
    private final Random random = new Random();

    public InteractBlockManager(main plugin) {
        this.plugin = plugin;
        setup();
    }

    public void setup() {
        // 1. 폴더 생성 확인
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // 2. 파일 객체 설정
        file = new File(plugin.getDataFolder(), "interactable_blocks.yml");

        // 3. 파일이 없으면 기본 데이터와 함께 생성
        if (!file.exists()) {
            try {
                file.createNewFile();
                config = YamlConfiguration.loadConfiguration(file);

                // 샘플 데이터 삽입 (기본적으로 다이아몬드 원석을 우클릭하면 보상이 나오도록 예시 설정)
                config.set("blocks.DIAMOND_ORE.regen_seconds", 60);
                config.set("blocks.DIAMOND_ORE.items", Arrays.asList("DIAMOND:1:100", "GOLD_INGOT:2:50"));

                config.save(file); // 초기 생성 시 저장
                plugin.getLogger().info("[System] interactable_blocks.yml 파일을 생성했습니다.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 4. 최종 로드
        config = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public List<ItemStack> getRandomDrops(String blockName) {
        List<ItemStack> drops = new ArrayList<>();
        if (config == null || !config.contains("blocks." + blockName + ".items")) return drops;

        List<String> itemList = config.getStringList("blocks." + blockName + ".items");
        NamespacedKey radKey = new NamespacedKey(plugin, "radiation_protection");
        NamespacedKey insKey = new NamespacedKey(plugin, "insulation");

        for (String entry : itemList) {
            String[] split = entry.split(":");
            // 형식: 0아이템:1개수:2확률:3로어:4방호:5보온:6인챈트:7포션설정
            if (split.length < 3) continue;

            Material mat = Material.matchMaterial(split[0]);
            if (mat == null) mat = Material.getMaterial(split[0].toUpperCase());
            if (mat == null) continue;

            if (random.nextInt(100) < Integer.parseInt(split[2])) {
                ItemStack item = new ItemStack(mat, Integer.parseInt(split[1]));
                ItemMeta meta = item.getItemMeta();

                if (meta != null) {
                    // 1. 가방(lore 리스트) 준비
                    List<String> lore = new ArrayList<>();

                    // 2. [3] 기본 로어 처리
                    if (split.length >= 4 && !split[3].equalsIgnoreCase("none") && !split[3].isEmpty()) {
                        String rawLore = split[3].replace("&", "§");
                        lore.addAll(Arrays.asList(rawLore.split("\\|")));
                    }

                    // 3. [4] 방호 레벨 처리 (PDC 저장 및 로어 추가)
                    if (split.length >= 5) {
                        try {
                            int radLvl = Integer.parseInt(split[4]);
                            if (radLvl > 0) {
                                meta.getPersistentDataContainer().set(radKey, PersistentDataType.INTEGER, radLvl);
                                lore.add("§f방호 레벨: §6" + radLvl);
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    // 4. [5] 보온 레벨 처리 (PDC 저장 및 로어 추가)
                    if (split.length >= 6) {
                        try {
                            int insLvl = Integer.parseInt(split[5]);
                            if (insLvl > 0) {
                                meta.getPersistentDataContainer().set(insKey, PersistentDataType.INTEGER, insLvl);
                                lore.add("§f보온 레벨: §e" + insLvl);
                            }
                        } catch (Exception ignored) {
                        }
                    }

                    // 5. [6] 인챈트 처리
                    if (split.length >= 7 && !split[6].equalsIgnoreCase("none") && !split[6].isEmpty()) {
                        String[] enchants = split[6].split(";");
                        for (String enchEntry : enchants) {
                            String[] enchSplit = enchEntry.split(",");
                            if (enchSplit.length == 2) {
                                // getEnchantment 보조 메서드 사용 (이전 답변 참고)
                                Enchantment ench = getEnchantment(enchSplit[0]);
                                if (ench != null) {
                                    try {
                                        int level = Integer.parseInt(enchSplit[1]);
                                        meta.addEnchant(ench, level, true);
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                        }
                    }

                    // 6. [7] 포션 효과 처리
                    if (split.length >= 8 && (mat == Material.POTION || mat == Material.SPLASH_POTION || mat == Material.LINGERING_POTION)) {
                        if (meta instanceof org.bukkit.inventory.meta.PotionMeta) {
                            org.bukkit.inventory.meta.PotionMeta potionMeta = (org.bukkit.inventory.meta.PotionMeta) meta;
                            String[] potSplit = split[7].split(",");
                            if (potSplit.length >= 3) {
                                try {
                                    org.bukkit.potion.PotionType type = org.bukkit.potion.PotionType.valueOf(potSplit[0].toUpperCase());
                                    boolean upgraded = Boolean.parseBoolean(potSplit[1]);
                                    boolean extended = Boolean.parseBoolean(potSplit[2]);
                                    potionMeta.setBasePotionData(new org.bukkit.potion.PotionData(type, extended, upgraded));
                                } catch (Exception e) {
                                    plugin.getLogger().warning("포션 설정 오류: " + split[7]);
                                }
                            }
                        }
                    }

                    // ⭐ 7. 모든 내용이 추가된 lore 리스트를 최종적으로 적용
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                drops.add(item);
            }
        }
        return drops;
    }
    private org.bukkit.enchantments.Enchantment getEnchantment(String name) {
        if (name == null || name.isEmpty()) return null;

        // 1. 최신 방식 (NamespacedKey)으로 찾기 (예: sharpness, oxygen, protection)
        org.bukkit.enchantments.Enchantment ench = org.bukkit.enchantments.Enchantment.getByKey(
                org.bukkit.NamespacedKey.minecraft(name.toLowerCase())
        );
        if (ench != null) return ench;

        // 2. 옛날 방식 (이름)으로 찾기 (예: DAMAGE_ALL, DURABILITY)
        return org.bukkit.enchantments.Enchantment.getByName(name.toUpperCase());
    }
}