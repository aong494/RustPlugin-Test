package Alpa.test;

import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

public class MobListener implements Listener {
    @EventHandler
    public void onHorseSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.AbstractHorse) {
            org.bukkit.entity.AbstractHorse horse = (org.bukkit.entity.AbstractHorse) event.getEntity();
            horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        }
    }
    @EventHandler
    public void onHorseDeath(EntityDeathEvent event) {
        // 1. 죽은 엔티티가 말 종류(AbstractHorse)인지 확인
        if (event.getEntity() instanceof AbstractHorse) {
            // 2. 드랍 아이템 목록(List<ItemStack>)에서 안장을 찾아 제거
            Iterator<ItemStack> iterator = event.getDrops().iterator();

            while (iterator.hasNext()) {
                ItemStack drop = iterator.next();

                // 아이템이 안장(Saddle)인 경우 드랍 목록에서 삭제
                if (drop != null && drop.getType() == Material.SADDLE) {
                    iterator.remove();
                }
            }
        }
    }
}