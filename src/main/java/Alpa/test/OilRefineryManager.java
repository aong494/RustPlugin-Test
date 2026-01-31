package Alpa.test;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

public class OilRefineryManager implements Listener {

    private final main plugin;
    private final String GUI_TITLE = "§8정유기 (Oil Refinery)";
    private final Map<Location, Inventory> inventories = new HashMap<>();
    private final Map<Location, Double> cookProgress = new HashMap<>();
    private final Map<Location, Double> burnEnergy = new HashMap<>();
    private final Map<Location, Integer> cookTicks = new HashMap<>(); // 현재 화살표 위치 (0~200)
    private final Map<Location, Integer> burnTicks = new HashMap<>(); // 남은 에너지 (틱 단위)
    private final int TOTAL_COOK_TIME = 200;

    public OilRefineryManager(main plugin) {
        this.plugin = plugin;
        startRefineryTask();
    }

    // --- 원유 판별 ---
    private boolean isCrudeOil(ItemStack item) {
        return item != null && item.getType() == Material.SLIME_BALL &&
                item.hasItemMeta() && item.getItemMeta().getCustomModelData() == 1;
    }

    // --- [수정] 블록 상호작용: 쉬프트 클릭 시 설치 허용 ---
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;

        if (block.getType().getKey().toString().equals("minecraft:smoker")) {
            // 플레이어가 쉬프트를 누르고 있다면 창을 열지 않고 블록 설치(호퍼 등)를 허용함
            if (e.getPlayer().isSneaking()) return;

            e.setCancelled(true);
            Location loc = block.getLocation();
            Inventory inv = inventories.computeIfAbsent(loc, k ->
                    Bukkit.createInventory(null, org.bukkit.event.inventory.InventoryType.FURNACE, GUI_TITLE));
            e.getPlayer().openInventory(inv);
        }
    }

    private void startRefineryTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Location loc : inventories.keySet()) {
                    Inventory inv = inventories.get(loc);

                    // 1. 호퍼 로직 (아이템 자동 흡입 및 배출)
                    handleHopperAutomation(loc, inv);

                    // 2. 정제 로직 (수치 조절 포인트)
                    processRefining(loc, inv);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // 1틱 주기로 부드럽게 작동
    }

    private void processRefining(Location loc, Inventory inv) {
        ItemStack input = inv.getItem(0);
        ItemStack fuelSlot = inv.getItem(1);

        int currentBurn = burnTicks.getOrDefault(loc, 0);
        int currentCook = cookTicks.getOrDefault(loc, 0);

        // 1. 연료 보충 로직 (에너지가 다 떨어졌을 때만)
        if (currentBurn <= 0 && isCrudeOil(input)) {
            if (fuelSlot != null) {
                // 원목 1개당 10틱 (20개 소모 시 200틱), 석탄 1개당 20틱 (10개 소모 시 200틱)
                int energyPerItem = getEnergyPerItemTicks(fuelSlot.getType());
                if (energyPerItem > 0) {
                    currentBurn = energyPerItem;
                    fuelSlot.setAmount(fuelSlot.getAmount() - 1);
                    burnTicks.put(loc, currentBurn);
                }
            }
        }

        // 2. 정제 진행 로직
        if (currentBurn > 0 && isCrudeOil(input)) {
            currentBurn--; // 1틱마다 1씩 정직하게 소모
            currentCook++; // 1틱마다 1씩 정직하게 화살표 상승

            burnTicks.put(loc, currentBurn);
            cookTicks.put(loc, currentCook);

            // [핵심] 화살표가 끝까지 차기 직전(95% 이상)이고 에너지가 충분치 않을 때 강제 보정
            if (currentCook >= TOTAL_COOK_TIME) {
                // 완료 전 패킷 강제 동기화 (화면상에서 끝까지 차 보이게 함)
                forceUpdateAnimation(inv, TOTAL_COOK_TIME, currentBurn);

                completeRefine(inv);
                cookTicks.put(loc, 0); // 초기화
            } else {
                updateAnimation(inv, currentCook, currentBurn);
            }
        } else {
            // 중단 시에도 현재 위치 유지
            updateAnimation(inv, currentCook, Math.max(0, currentBurn));
        }
    }

    private int getEnergyPerItemTicks(Material type) {
        if (type.name().contains("LOG")) return 10; // 200 / 20 = 10
        if (type == Material.COAL || type == Material.CHARCOAL) return 20; // 200 / 10 = 20
        return 0;
    }

    // --- 호퍼 자동화 시스템 ---
    private void handleHopperAutomation(Location loc, Inventory inv) {
        if (Bukkit.getCurrentTick() % 20 != 0) return;

        Block centerBlock = loc.getBlock();

        // 1. 위쪽 호퍼: 원유(0번 슬롯) 주입
        Block top = centerBlock.getRelative(BlockFace.UP);
        if (top.getState() instanceof Hopper hopper) {
            moveItem(hopper.getInventory(), inv, 0, true);
        }

        // 2. 옆쪽 호퍼: 연료(1번 슬롯) 주입
        // Block[] sides (X) -> BlockFace[] sides (O)
        BlockFace[] sides = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : sides) {
            Block side = centerBlock.getRelative(face);
            if (side.getState() instanceof Hopper hopper) {
                moveItem(hopper.getInventory(), inv, 1, false);
            }
        }

        // 3. 아래쪽 호퍼: 결과물(2번 슬롯) 추출
        Block bottom = centerBlock.getRelative(BlockFace.DOWN);
        if (bottom.getState() instanceof Hopper hopper) {
            ItemStack result = inv.getItem(2);
            if (result != null) {
                // 호퍼 인벤토리에 아이템 추가 시도
                HashMap<Integer, ItemStack> left = hopper.getInventory().addItem(result.asQuantity(1));

                // 성공적으로 옮겼다면 정유기 슬롯에서 1개 제거
                if (left.isEmpty()) {
                    result.setAmount(result.getAmount() - 1);
                }
            }
        }
    }

    private void moveItem(Inventory from, Inventory to, int toSlot, boolean onlyOil) {
        for (int i = 0; i < from.getSize(); i++) {
            ItemStack item = from.getItem(i);
            if (item == null) continue;

            // 원유만 넣어야 하는지, 연료를 넣어야 하는지 판별
            boolean canMove = onlyOil ? isCrudeOil(item) : getEnergyPerItem(item.getType()) > 0;

            if (canMove) {
                ItemStack target = to.getItem(toSlot);
                if (target == null || (target.isSimilar(item) && target.getAmount() < 64)) {
                    if (target == null) to.setItem(toSlot, item.asQuantity(1));
                    else target.setAmount(target.getAmount() + 1);
                    item.setAmount(item.getAmount() - 1);
                    break;
                }
            }
        }
    }

    private double getEnergyPerItem(Material type) {
        if (type.name().contains("LOG")) return 100.0;
        if (type == Material.COAL || type == Material.CHARCOAL) return 200.0;
        return 0;
    }

    private void updateAnimation(Inventory inv, int cook, int burn) {
        for (org.bukkit.entity.HumanEntity entity : inv.getViewers()) {
            if (entity instanceof Player player) {
                // 2번: 현재 화살표, 3번: 화살표 최대치(TOTAL_COOK_TIME)
                player.setWindowProperty(InventoryView.Property.COOK_TIME, cook);
                player.setWindowProperty(InventoryView.Property.values()[3], TOTAL_COOK_TIME);

                // 0번: 현재 불꽃, 1번: 불꽃 최대치
                player.setWindowProperty(InventoryView.Property.BURN_TIME, burn);
                player.setWindowProperty(InventoryView.Property.values()[1], 20); // 연료 1개당 최대 에너지 기준
            }
        }
    }

    private void completeRefine(Inventory inv) {
        ItemStack input = inv.getItem(0);
        if (input != null) input.setAmount(input.getAmount() - 1);

        ItemStack fuel = new ItemStack(Material.SLIME_BALL);
        ItemMeta meta = fuel.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f저급 연료");
            fuel.setItemMeta(meta);
        }
        fuel.setAmount(3);

        ItemStack result = inv.getItem(2);
        if (result == null) inv.setItem(2, fuel);
        else result.setAmount(Math.min(64, result.getAmount() + 3));
    }
    private void forceUpdateAnimation(Inventory inv, int cook, int burn) {
        updateAnimation(inv, cook, burn);
    }
}