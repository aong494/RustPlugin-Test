package Alpa.test;

import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class CardKeyListener implements Listener {
    private final main plugin;
    private final CardKeyManager manager;

    public CardKeyListener(main plugin, CardKeyManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;
        Player player = event.getPlayer();

        // 1. 문 연결 모드 처리 (OP가 /카드키 연결 후 문 클릭 시)
        if (manager.pendingLinks.containsKey(player.getUniqueId())) {
            event.setCancelled(true); // 문이 열리거나 작동하지 않도록 캔슬
            Location readerLoc = manager.pendingLinks.remove(player.getUniqueId());

            manager.linkDoor(readerLoc, clickedBlock.getLocation());
            return;
        }
        String blockType = clickedBlock.getType().name().toLowerCase();

        boolean isDoomsdayDoor = blockType.contains("door_14");

        if (isDoomsdayDoor) {
            if (!player.isOp()) {
                event.setCancelled(true);
            }
            return;
        }

        // 3. 리더기 작동 로직
        Integer readerLevel = manager.getReaderLevel(clickedBlock.getLocation());
        if (readerLevel != null) {
            event.setCancelled(true);

            ItemStack item = player.getInventory().getItemInMainHand();
            int cardLevel = getCardLevel(item);

            if (cardLevel == 0) {
                TextComponent.fromLegacyText(ChatColor.RED + "카드키가 필요합니다.");
                return;
            }

            // 보안 레벨 체크 (카드 레벨 >= 리더기 레벨)
            if (cardLevel >= readerLevel) {
                Location doorLoc = manager.getLinkedDoor(clickedBlock.getLocation());
                if (doorLoc != null && openDoor(doorLoc.getBlock())) {
                    consumeItemDurability(player, item);

                    // 설정된 시간 후 자동으로 닫음
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            closeDoor(doorLoc.getBlock());
                        }
                    }.runTaskLater(plugin, manager.autoCloseSeconds * 20L); // 초 * 20틱
                } else {
                    TextComponent.fromLegacyText(ChatColor.RED + "연결된 문을 찾을 수 없거나 열 수 없습니다.");
                }
            } else {
                TextComponent.fromLegacyText(ChatColor.RED + "보안 레벨이 부족합니다. (필요: " + readerLevel + ")");
            }
        }
    }

    // 아이템을 확인하여 카드키 레벨 반환
    private int getCardLevel(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0;

        // 아이템 이름이나 Material로 체크.
        // 모드 아이템일 경우 Material 이름이 다를 수 있으므로 디스플레이 이름이나 타입을 체크해야 함.
        // 여기서는 요청하신 대로 키워드를 포함하는지 체크합니다.
        String typeName = item.getType().name().toLowerCase();

        // 만약 커스텀 아이템 이름을 쓴다면 item.getItemMeta().getDisplayName() 등을 사용해야 합니다.
        // 아래는 Material 이름 기준 예시입니다. (모드 아이템은 보통 MODID_ITEMNAME 형태)

        if (typeName.contains("red_keycard")) return 3;
        if (typeName.contains("blue_keycard")) return 2;
        if (typeName.contains("green_keycard")) return 1;

        return 0;
    }

    // 문 열기 메서드 개선
    private boolean openDoor(Block block) {
        // 1. 표준 Openable 체크
        if (block.getBlockData() instanceof Openable) {
            Openable openable = (Openable) block.getBlockData();
            if (!openable.isOpen()) {
                openable.setOpen(true);
                block.setBlockData(openable);
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.0f);
                return true;
            }
            return false;
        }

        // 2. 모드 블록(Openable이 아닐 때) 강제 처리
        String blockType = block.getType().name().toLowerCase();
        if (blockType.contains("door_14")) {
            // 모드 문은 보통 'open'이라는 상태(Property)를 가집니다.
            // BlockData를 문자열로 변환하여 상태를 강제로 수정하는 방식입니다.
            try {
                String dataString = block.getBlockData().getAsString();
                if (dataString.contains("open=false")) {
                    String openedData = dataString.replace("open=false", "open=true");
                    block.setBlockData(Bukkit.createBlockData(openedData));
                    block.getWorld().playSound(block.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.0f);
                    return true;
                }
            } catch (Exception e) {
                // 강제 변환 실패 시 로그 출력
                plugin.getLogger().warning("모드 문 열기 실패: " + e.getMessage());
            }
        }

        return false;
    }

    // 기존 closeDoor 메서드를 이 코드로 교체하세요.
    private boolean closeDoor(Block block) {
        if (block.getBlockData() instanceof Openable) {
            Openable openable = (Openable) block.getBlockData();
            if (openable.isOpen()) {
                openable.setOpen(false);
                block.setBlockData(openable);
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 1.0f);
                return true;
            }
            return false;
        }

        String blockType = block.getType().name().toLowerCase();
        if (blockType.contains("door_14")) {
            try {
                String dataString = block.getBlockData().getAsString();
                if (dataString.contains("open=true")) {
                    String closedData = dataString.replace("open=true", "open=false");
                    block.setBlockData(Bukkit.createBlockData(closedData));
                    block.getWorld().playSound(block.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 1.0f);
                    return true;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("모드 문 닫기 실패: " + e.getMessage());
            }
        }
        return false;
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;
        ItemStack item = event.getItemInHand();
        if (item == null || item.getType() == Material.AIR) return;
        String itemName = item.getType().name().toLowerCase();
        if (itemName.contains("keycard")) {
            event.setCancelled(true);
        }
    }
    private void consumeItemDurability(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        // 아이템 메타데이터가 Damageable(내구도 조절 가능)인지 확인
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable) {
            org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) item.getItemMeta();

            // 현재 내구도 수치에 1을 더함 (마인크래프트에서 Damage 수치가 올라가는 것이 내구도가 깎이는 것)
            int newDamage = damageable.getDamage() + 1;

            // 아이템의 최대 내구도를 넘었는지 확인
            if (newDamage >= item.getType().getMaxDurability() && item.getType().getMaxDurability() > 0) {
                // 내구도 다함 -> 아이템 파괴
                item.setAmount(0);
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            } else {
                // 내구도 적용
                damageable.setDamage(newDamage);
                item.setItemMeta(damageable);
            }
        }
    }
}