package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.data.Lightable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RouletteManager {
    private final main plugin;
    private File file;
    private FileConfiguration config;

    // 게임 상태 변수
    private final Map<UUID, Map<Integer, Integer>> playerBets = new HashMap<>();
    private boolean isCountingDown = false;
    private boolean isSpinning = false;
    private int currentWinningIndex = -1;
    private int timer = 0;

    // 배율별 인덱스 설정 (총 16개 조명)
    public final int[] MULTIPLIERS = {2, 3, 5, 2, 8, 3, 2, 5, 2, 3, 8, 2, 5, 4, 2, 15};

    public int getTimer() {
        return timer;
    }
    public RouletteManager(main plugin) {
        this.plugin = plugin;
        setup();
    }
    public boolean isCountingDown() {
        return isCountingDown;
    }
    private void startCountdown() {
        isCountingDown = true;
        timer = 10;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (timer > 0) {
                    // GUI를 열고 있는 모든 플레이어에게 시계 아이콘 업데이트 요청
                    refreshAllRouletteGUIs();
                    timer--;
                } else {
                    this.cancel();
                    isCountingDown = false;
                    refreshAllRouletteGUIs(); // 0초일 때 마지막 업데이트
                    startSpin();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void refreshAllRouletteGUIs() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTitle().equals("§8[룰렛 배팅]")) {
                // 리스너의 업데이트 메서드 호출
                plugin.rouletteListener.updateCountdownIcon(p);
            }
        }
    }
    public void setup() {
        file = new File(plugin.getDataFolder(), "roulette_settings.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
                config = YamlConfiguration.loadConfiguration(file);
                // 기본 좌표 예시 (명령어로 설정하게 하거나 수동 입력)
                for (int i = 0; i < 16; i++) {
                    config.set("lamps." + i + ".world", "world");
                    config.set("lamps." + i + ".x", 0);
                    config.set("lamps." + i + ".y", 0);
                    config.set("lamps." + i + ".z", 0);
                }
                config.save(file);
            } catch (IOException e) { e.printStackTrace(); }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void addBet(Player player, int multiplier, int amount) {
        playerBets.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .merge(multiplier, amount, Integer::sum);

        if (!isCountingDown && !isSpinning) {
            startCountdown();
        }
    }

    private void startSpin() {
        isSpinning = true;
        int winningIndex = new Random().nextInt(16);
        int totalTicks = 64 + winningIndex; // 최소 3바퀴 이상 회전

        new BukkitRunnable() {
            int step = 0;
            int delay = 1;

            @Override
            public void run() {
                // 이전 조명 끄기
                setLamp(step % 16, false);
                step++;
                // 현재 조명 켜기
                setLamp(step % 16, true);

                // [효과음 추가] 배팅한 사람들에게만 딸깍 소리 재생
                playerBets.keySet().forEach(uuid -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        // 높은 음의 플링 소리로 경쾌한 느낌 제공
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                    }
                });

                if (step >= totalTicks) {
                    this.cancel();
                    finishGame(step % 16);
                    return;
                }

                // 후반부에 느려지는 효과
                if (step > totalTicks - 10) {
                    // 재귀적 스케줄러로 구현하는 것이 좋으나 단순화를 위해 고정 딜레이 증가 생략
                }
            }
        }.runTaskTimer(plugin, 0L, 2L); // 0.1초마다 회전
    }

    private void finishGame(int index) {
        int winMultiplier = MULTIPLIERS[index];
        playerBets.keySet().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
        });

        playerBets.forEach((uuid, bets) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && bets.containsKey(winMultiplier)) {
                int rewardAmount = bets.get(winMultiplier) * winMultiplier;
                giveScrap(player, rewardAmount);
                player.sendMessage("§a축하합니다! §e" + rewardAmount + "개의 스크랩을 획득했습니다.");
            }
        });

        playerBets.clear();
        isSpinning = false;
    }

    private void setLamp(int index, boolean lit) {
        String path = "lamps." + index;
        if (!config.contains(path)) return;
        Location loc = new Location(
                Bukkit.getWorld(config.getString(path + ".world")),
                config.getDouble(path + ".x"),
                config.getDouble(path + ".y"),
                config.getDouble(path + ".z")
        );
        if (loc.getBlock().getType() == Material.REDSTONE_LAMP) {
            Lightable lightable = (Lightable) loc.getBlock().getBlockData();
            lightable.setLit(lit);
            loc.getBlock().setBlockData(lightable);
        }
    }

    private void giveScrap(Player player, int amount) {
        // jeg:scrap 아이템 생성 로직 (Material 이름을 정확히 알아야 함)
        ItemStack scrap = new ItemStack(Material.valueOf("JEG_SCRAP"), amount);
        player.getInventory().addItem(scrap);
    }
    // 특정 플레이어가 특정 배율에 배팅한 금액 반환
    public int getPlayerBetAmount(UUID uuid, int multiplier) {
        if (!playerBets.containsKey(uuid)) return 0;
        return playerBets.get(uuid).getOrDefault(multiplier, 0);
    }

    // 배팅액 차감 로직
    public void removeBet(Player player, int multiplier, int amount) {
        UUID uuid = player.getUniqueId();
        if (playerBets.containsKey(uuid) && playerBets.get(uuid).containsKey(multiplier)) {
            int current = playerBets.get(uuid).get(multiplier);
            if (current <= amount) {
                playerBets.get(uuid).remove(multiplier);
            } else {
                playerBets.get(uuid).put(multiplier, current - amount);
            }
        }
    }
}