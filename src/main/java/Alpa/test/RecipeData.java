package Alpa.test;

import org.bukkit.inventory.ItemStack;
import java.io.Serializable;
import java.util.List;

public class RecipeData {
    public ItemStack result;      // 결과 아이템
    public List<ItemStack> ingredients; // 필요한 재료들

    public RecipeData(ItemStack result, List<ItemStack> ingredients) {
        this.result = result;
        this.ingredients = ingredients;
    }
}