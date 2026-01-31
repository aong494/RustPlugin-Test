package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class BlockDrops implements Listener {

    private final main plugin;
    private final Random random = new Random();

    public BlockDrops(main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        String typeName = block.getType().name();

        // --- 기존 기능: 철광석 고품질 드롭 ---
        if (block.getType() == Material.IRON_ORE || block.getType() == Material.DEEPSLATE_IRON_ORE) {
            if (player.getGameMode() != GameMode.SURVIVAL) return;

            ItemStack tool = player.getInventory().getItemInMainHand();
            int fortuneLevel = tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LOOT_BONUS_BLOCKS);

            if (random.nextInt(100) < 10) {
                int count = 1 + random.nextInt(fortuneLevel + 1);
                dropHighQualityIron(block, count);
            }
        }

        // --- 추가 기능: 나뭇잎 파괴 시 드롭 방지 ---
        if (typeName.contains("LEAVES")) {
            event.setDropItems(false);
        }
    }

    // --- 추가 기능: 폭발 지형 파괴 방지 ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent event) {
        // 파괴될 블록 리스트를 비워 지형을 보호합니다.
        event.blockList().clear();

        // 폭발 시각/청각 효과는 유지 (선택 사항)
        event.getLocation().getWorld().spawnParticle(Particle.EXPLOSION_HUGE, event.getLocation(), 1);
        event.getLocation().getWorld().playSound(event.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
    }

    // --- 추가 기능: 나뭇잎 자연 소멸 및 특정 아이템 생성 방지 ---
    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Item itemEntity) {
            ItemStack item = itemEntity.getItemStack();
            String type = item.getType().name();

            // 묘목(SAPLING), 사과(APPLE), 막대기(STICK) 생성 취소
            if (type.contains("SAPLING") || type.equals("APPLE") || type.equals("STICK")) {
                event.setCancelled(true);
            }
        }
    }

    private void dropHighQualityIron(Block block, int count) {
        String modItemId = "superbwarfare:galena";

        double x = block.getX() + 0.5;
        double y = block.getY() + 0.5;
        double z = block.getZ() + 0.5;

        // NBT 문법 (Mohist 1.20.1 호환용 중괄호 방식)
        String nbt = "{Item:{id:\"" + modItemId + "\",Count:" + count + "b,tag:{display:{Name:'{\"text\":\"고품질 철 원석\",\"italic\":false,\"color\":\"white\"}'}}}}";
        String command = String.format("summon item %f %f %f %s", x, y, z, nbt);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}