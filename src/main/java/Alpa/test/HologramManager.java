package Alpa.test;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HologramManager {
    private final main plugin;
    private final Map<UUID, ItemDisplay> previews = new HashMap<>();

    public HologramManager(main plugin) {
        this.plugin = plugin;
    }

    public void updatePreview(Player player, Block targetBlock, Location clickLoc) {
        // 이미 생성된 미리보기가 있다면 위치만 업데이트하거나 새로 생성
        ItemDisplay display = previews.computeIfAbsent(player.getUniqueId(), id -> createPreviewEntity(player, clickLoc));

        if (display == null || !display.isValid()) {
            previews.remove(player.getUniqueId());
            return;
        }

        // 문 방향 판정 및 좌표 계산 (기존 spawnLockAtPoint 로직 활용)
        Block master = plugin.getMasterBlock(targetBlock);
        if (master == null) master = targetBlock;

        BlockFace facing = getDoorFacing(master);
        Location fixedLoc = calculateFixedLocation(master, clickLoc, facing);

        // 미리보기 위치 및 회전 업데이트
        display.teleport(fixedLoc);
        applyPreviewTransformation(display, facing);
    }

    public void removePreview(Player player) {
        ItemDisplay display = previews.remove(player.getUniqueId());
        if (display != null) display.remove();
    }

    private ItemDisplay createPreviewEntity(Player player, Location loc) {
        return loc.getWorld().spawn(loc, ItemDisplay.class, ent -> {
            ItemStack item = new ItemStack(Material.BLAZE_ROD);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) meta.setCustomModelData(1);
            item.setItemMeta(meta);

            ent.setItemStack(item);
            ent.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            // 반투명 효과 (글로우 추가 가능)
            ent.setGlowing(true);
            ent.setVisualFire(false);
            // 플레이어에게만 보이게 설정하는 것은 패킷 방식이 필요하므로, 여기서는 소환 후 관리
        });
    }

    private void applyPreviewTransformation(ItemDisplay ent, BlockFace facing) {
        float yaw = switch (facing) {
            case NORTH -> 180f; case SOUTH -> 0f;
            case EAST -> 270f; case WEST -> 90f;
            default -> 0f;
        };

        // 하드코딩했던 depth 수치 적용 (북쪽 기준 예시)
        float depth = (facing == BlockFace.NORTH || facing == BlockFace.WEST) ? 0.4f : -0.4f;

        Transformation trans = ent.getTransformation();
        trans.getLeftRotation().set(new AxisAngle4f((float) Math.toRadians(yaw), 0, 1, 0));

        // X축 또는 Z축 이동 (이전 좌표 로직 적용)
        if (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) {
            trans.getTranslation().set(0, 0, depth);
        } else {
            trans.getTranslation().set(depth, 0, 0);
        }

        ent.setTransformation(trans);
    }

    // --- 기존 좌표 로직 유틸리티 ---
    private BlockFace getDoorFacing(Block b) {
        String data = b.getBlockData().getAsString();
        if (data.contains("facing=north")) return BlockFace.NORTH;
        if (data.contains("facing=south")) return BlockFace.SOUTH;
        if (data.contains("facing=east")) return BlockFace.EAST;
        if (data.contains("facing=west")) return BlockFace.WEST;
        return BlockFace.NORTH;
    }

    private Location calculateFixedLocation(Block master, Location clickLoc, BlockFace facing) {
        Location loc = clickLoc.clone();
        if (facing == BlockFace.NORTH || facing == BlockFace.SOUTH) {
            loc.setZ(master.getZ() + 0.5);
        } else {
            loc.setX(master.getX() + 0.5);
        }
        return loc;
    }
}