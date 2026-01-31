package Alpa.test;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.block.Furnace;
import org.bukkit.block.BlockState;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;

public class FurnaceListener implements Listener {

    // 배율 설정: 0.5f로 설정하면 속도가 2배 느려집니다. (1.0f가 기본)
    private final double speedMultiplier = 0.2;

    @EventHandler
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        BlockState state = event.getBlock().getState();
        // 연료가 타는 시간도 속도에 맞춰 늘려줌 (석탄 효율 유지)
        int increasedBurnTime = (int) (event.getBurnTime() / speedMultiplier);
        event.setBurnTime(increasedBurnTime);

        if (state instanceof Furnace furnace) {
            // 기본 요리 속도인 200틱을 배율로 나누어 더 길게 만듭니다.
            // 200 / 0.5 = 400틱 (20초)
            int newCookTime = (int) (200 / speedMultiplier);

            // 현재 굽고 있는 아이템의 총 소요 시간을 설정
            furnace.setCookTimeTotal(newCookTime);
            furnace.update();
        }
    }
    @EventHandler
    public void onStartSmelt(FurnaceStartSmeltEvent event) {
        // 아이템이 처음 구워지기 시작할 때 총 요리 시간을 강제로 설정
        int newCookTime = (int) (200 / speedMultiplier);
        event.setTotalCookTime(newCookTime);
    }
}
