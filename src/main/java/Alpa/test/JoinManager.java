package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;
import org.bukkit.inventory.Recipe;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class JoinManager implements Listener {

    private final main plugin;

    public JoinManager(main plugin) {
        this.plugin = plugin;
    }

    // 1. 새로운 레시피 발견 알림 차단 (아이템 습득 시 우측 상단 팝업 방지)
    @EventHandler
    public void onRecipeDiscover(PlayerRecipeDiscoverEvent event) {
        event.setCancelled(true);
    }

    // 2. 서버 접속 시 기존에 알고 있던 레시피를 모두 삭제
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 서버의 모든 레시피 목록을 가져와서 플레이어에게서 제거
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            // 레시피가 Keyed 인터페이스를 구현하고 있다면 (대부분의 기본/커스텀 레시피)
            if (recipe instanceof Keyed) {
                // 해당 레시피를 플레이어의 레시피 북에서 '미발견' 상태로 변경
                player.undiscoverRecipe(((Keyed) recipe).getKey());
            }
        }
        // 서버가 켜질 때 로드된 shopConfig에서 저장된 모든 상점 위치를 불러옴
        ConfigurationSection section = plugin.shopConfig.getConfigurationSection("map_locations");
        if (section != null) {
            for (String shopName : section.getKeys(false)) {
                double x = section.getDouble(shopName + ".x");
                double z = section.getDouble(shopName + ".z");
                String items = section.getString(shopName + ".items", "none");

                // [수정 핵심] 구분자를 : 에서 | 로 변경
                String payload = "SHOP_SYNC|" + (float) x + "|" + (float) z + "|" + shopName + "|" + items;

                // 클라이언트가 완전히 로딩된 후 받도록 1초(20틱) 딜레이 전송
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) { // 플레이어가 나가지 않았는지 확인
                        player.sendPluginMessage(plugin, "examplemod:shop_data", payload.getBytes(StandardCharsets.UTF_8));
                    }
                }, 20L);
            }
        }
    }
}