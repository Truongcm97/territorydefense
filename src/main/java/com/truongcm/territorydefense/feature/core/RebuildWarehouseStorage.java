package com.truongcm.territorydefense.feature.core;

import org.bukkit.inventory.ItemStack;

public class RebuildWarehouseStorage {
    private final ItemStack[] items = new ItemStack[90];

    public ItemStack getItem(int index) {
        if (index < 0 || index >= 90) return null;
        return items[index];
    }

    public int getSize() {
        return 90;
    }

    public void setItem(int index, ItemStack item) {
        if (index >= 0 && index < 90) {
            items[index] = item;
        }
    }

    public ItemStack[] getContents() {
        return items;
    }

    public void clear() {
        for (int i = 0; i < 90; i++) {
            items[i] = null;
        }
    }
}
