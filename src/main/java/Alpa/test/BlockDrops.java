package Alpa.test;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Random;

public class BlockDrops implements Listener {

    private final main plugin;
    private final Random random = new Random();

    public BlockDrops(main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onIronOreBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // 1. 철광석 종류 확인
        if (block.getType() == Material.IRON_ORE || block.getType() == Material.DEEPSLATE_IRON_ORE) {
            if (player.getGameMode() != GameMode.SURVIVAL) return;

            // 2. 행운 레벨 가져오기
            ItemStack tool = player.getInventory().getItemInMainHand();
            int fortuneLevel = tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LOOT_BONUS_BLOCKS);

            // 3. 확률 설정 (현재 90%)
            if (random.nextInt(100) < 10) {
                // 4. 행운 적용 개수 계산: 기본 1개 + (0부터 행운레벨 사이의 랜덤값)
                int count = 1 + random.nextInt(fortuneLevel + 1);

                dropHighQualityIron(block, count);
            }
        }
    }

    private void dropHighQualityIron(Block block, int count) {
        String modItemId = "superbwarfare:galena";

        double x = block.getX() + 0.5;
        double y = block.getY() + 0.5;
        double z = block.getZ() + 0.5;

        // NBT 내의 Count 부분을 변수 count로 변경
        String nbt = "{Item:{id:\"" + modItemId + "\",Count:" + count + "b,tag:{display:{Name:'{\"text\":\"고품질 철 원석\",\"italic\":false,\"color\":\"white\"}'}}}}";

        String command = String.format("summon item %f %f %f %s", x, y, z, nbt);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }
}