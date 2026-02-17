package Alpa.test;

public class ShopSyncPacket {
    public float x, z;
    public String name;
    public String itemList; // 아이템 목록 필드 추가

    public ShopSyncPacket(float x, float z, String name, String itemList) {
        this.x = x;
        this.z = z;
        this.name = name;
        this.itemList = itemList;
    }
    public String toString() {
        return "SHOP_SYNC:" + x + ":" + z + ":" + name + ":" + itemList;
    }
}