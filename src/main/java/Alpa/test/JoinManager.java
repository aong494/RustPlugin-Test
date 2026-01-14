package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;
import org.bukkit.inventory.Recipe;

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
    }
}